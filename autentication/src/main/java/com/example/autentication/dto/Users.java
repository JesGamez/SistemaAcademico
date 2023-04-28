package com.example.autentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.util.*;
import java.util.function.Predicate;

@ToString
@Table("USERS")
@AllArgsConstructor
public class Users {

    @Getter
    @Id private Long id;
    @Getter @Setter
    private String firstName;
    @Getter @Setter
    private String lastName;
    @Getter @Setter
    private String email;
    @Getter @Setter
    private String password;
    @Getter @Setter
    private String tfaSecret;
    @MappedCollection private final Set<Token> tokens = new HashSet<>();
    @MappedCollection private final Set<PasswordRecovery> passwordRecoveries = new HashSet<>();

    public static Users of(String firstName, String lastName, String email, String password) {
        return new Users(null, firstName, lastName, email, password, "", Collections.emptyList(), Collections.emptyList());
    }

    @PersistenceCreator
    private Users(Long id, String firstName, String lastName, String email, String password, String tfaSecret, Collection<Token> tokens, Collection<PasswordRecovery> passwordRecoveries) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.tfaSecret = tfaSecret;
        this.tokens.addAll(tokens);
        this.passwordRecoveries.addAll(passwordRecoveries);
    }
    public void addToken(Token token) {
        this.tokens.add(token);
    }

    public Boolean removeToken(Token token) {
        return this.tokens.remove(token);
    }

    public Boolean removeTokenIf(Predicate<? super Token> predicate) {
        return this.tokens.removeIf(predicate);
    }

    public void addPasswordRecovery(PasswordRecovery passwordRecovery) {
        this.passwordRecoveries.add(passwordRecovery);
    }

    public Boolean removePasswordRecovery(PasswordRecovery passwordRecovery) {
        return this.passwordRecoveries.remove(passwordRecovery);
    }

    public Boolean removePasswordRecoveryIf(Predicate<? super PasswordRecovery> predicate) {
        return this.passwordRecoveries.removeIf(predicate);
    }

}
