package com.example.autentication.service;

import com.example.autentication.dto.PasswordRecovery;
import com.example.autentication.dto.Token;
import com.example.autentication.dto.Users;
import com.example.autentication.error.*;
import com.example.autentication.repository.UserRepo;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import dev.samstevens.totp.code.CodeVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final String accessTokenSecret;
    private final String refreshTokenSecret;

    private final MailService mailService;
    private final Long accessTokenValidity;
    private final Long refreshTokenValidity;
    private final GoogleIdTokenVerifier googleVerifier;
    private final CodeVerifier codeVerifier;

    @Autowired
    public AuthService(
            UserRepo userRepo,
            PasswordEncoder passwordEncoder,
            @Value("${application.security.access-token-secret}") String accessTokenSecret,
            @Value("${application.security.refresh-token-secret}") String refreshTokenSecret,
            MailService mailService,
            @Value("${application.security.access-token-validity}") Long accessTokenValidity,
            @Value("${application.security.refresh-token-validity}") Long refreshTokenValidity,
            GoogleIdTokenVerifier googleVerifier,
            CodeVerifier codeVerifier) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenSecret = accessTokenSecret;
        this.refreshTokenSecret = refreshTokenSecret;
        this.mailService = mailService;
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
        this.googleVerifier = googleVerifier;
        this.codeVerifier = codeVerifier;
    }

    public Users register(String firstname, String lastName, String email, String password, String passwordConfirm){
        if (!Objects.equals(password, passwordConfirm))
            throw new PasswordsDoNotMatchError();

        Users user;
        try {
            user = userRepo.save(Users.of(firstname, lastName, email, passwordEncoder.encode(password)));
        }catch (DbActionExecutionException exception){
            throw new EmailAlreadyExistsError();
        }
        return user;
    }

    public Login login(String email, String password){
        // buscar usuario por email
        var user = userRepo.findByEmail(email)
                .orElseThrow(InvalidCredentialsError::new);

        // si la contrasenia es diferente
        if(!passwordEncoder.matches(password, user.getPassword()))
            throw new InvalidCredentialsError();
            //throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID CREDENTIALS");

        var login = Login.of(
                user.getId(),
                accessTokenSecret, accessTokenValidity,
                refreshTokenSecret, refreshTokenValidity,
                Objects.equals(user.getTfaSecret(), "")
        );
        var refreshJwt = login.getRefreshToken();

        user.addToken(new Token(refreshJwt.getToken(), refreshJwt.getIssueAt(), refreshJwt.getExpiration()));
        userRepo.save(user);

        return login;
    }

    public Users getUserFromToken(String token){
        return userRepo.findById(Jwt.from(token, accessTokenSecret).getUserId())
                .orElseThrow(UserNotFoundError::new);
    }

    public Login refreshAccess(String refreshToken){
        var refreshJwt = Jwt.from(refreshToken, refreshTokenSecret);
        userRepo.findByIdAndTokensRefreshTokenAndTokensExpiredAtGreaterThan(refreshJwt.getUserId(),refreshJwt.getToken(),refreshJwt.getExpiration())
                .orElseThrow(UnauthenticatedError::new);

        return Login.of(refreshJwt.getUserId(), accessTokenSecret, accessTokenValidity, refreshJwt, false);
    }

    public Boolean logout(String refreshToken){
        var refreshJwt = Jwt.from(refreshToken, refreshTokenSecret);

        var user = userRepo.findById(refreshJwt.getUserId())
                .orElseThrow(UnauthenticatedError::new);

        var tokenIsRemoved = user.removeTokenIf(token -> Objects.equals(token.refreshToken(), refreshToken));

        if (tokenIsRemoved)
            userRepo.save(user);

        return tokenIsRemoved;
    }

    public void forgot(String email, String originUrl) {
        var token = UUID.randomUUID().toString().replace("-","");
        var user = userRepo.findByEmail(email)
                .orElseThrow(UserNotFoundError:: new);

        user.addPasswordRecovery(new PasswordRecovery(token));
        mailService.sendForgotMessage(email, token, originUrl);
        userRepo.save(user);

    }

    public Boolean reset(String password, String passwordConfirm, String token) {
        if (!Objects.equals(password, passwordConfirm)) {
            throw new PasswordsDoNotMatchError();
        }

        var user = userRepo.findByPasswordRecoveriesToken(token)
                .orElseThrow(InvalidLinkError::new);

        var passwordRecoveryIsRemoved = user.removePasswordRecoveryIf(passwordRecovery ->
                Objects.equals(passwordRecovery.token(), token));

        if (passwordRecoveryIsRemoved) {
            user.setPassword(passwordEncoder.encode(password));
            userRepo.save(user);
        }

        return passwordRecoveryIsRemoved;
    }

    public Login twoFactorLogin(Long id, String secret, String code, String refreshToken) {
        var user = userRepo.findById(id)
                .orElseThrow(InvalidCredentialsError::new);

        var tfaSecret = !Objects.equals(user.getTfaSecret(), "") ? user.getTfaSecret() : secret;

        if (!codeVerifier.isValidCode(tfaSecret, code)) {
            throw new InvalidCredentialsError();
        }

        if (Objects.equals(user.getTfaSecret(), "")) {
            user.setTfaSecret(secret);
            userRepo.save(user);
        }

        var refreshJwt = Jwt.from(refreshToken, refreshTokenSecret);

        userRepo.findByIdAndTokensRefreshTokenAndTokensExpiredAtGreaterThan(refreshJwt.getUserId(), refreshJwt.getToken(), refreshJwt.getExpiration())
                .orElseThrow(UnauthenticatedError::new);

        return Login.of(
                refreshJwt.getUserId(),
                accessTokenSecret,
                accessTokenValidity,
                refreshJwt,
                false
        );
    }

    public Login googleOAuth2Login(String idTokenString) throws GeneralSecurityException, IOException {
        GoogleIdToken idToken = googleVerifier.verify(idTokenString);


        if (idToken == null) {
            throw new InvalidCredentialsError();
        }

        Payload payload = idToken.getPayload();
        String email = payload.getEmail();

        Users user;
        var optionalUser = userRepo.findByEmail(email);
        if (optionalUser.isEmpty()) {
            String givenName = (String) payload.get("given_name");
            String familyName = (String) payload.get("family_name");
            final String password = "";
            try {
                if (familyName == null) {
                    familyName = "";
                }
                user = userRepo.save(Users.of(givenName, familyName, email, password));
            } catch (DbActionExecutionException exception) {
                throw new EmailAlreadyExistsError();
            }
        } else {
            user = optionalUser.get();
        }


        var login = Login.of(
                user.getId(),
                accessTokenSecret, accessTokenValidity,
                refreshTokenSecret, refreshTokenValidity,
                false
        );
        var refreshJwt = login.getRefreshToken();

        user.addToken(new Token(refreshJwt.getToken(), refreshJwt.getIssueAt(), refreshJwt.getExpiration()));
        userRepo.save(user);

        return login;
    }
}
