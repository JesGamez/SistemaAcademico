package com.example.autentication.repository;

import com.example.autentication.dto.Users;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepo extends CrudRepository<Users,Long> {
    //select * from user u where u.email = :email
    Optional<Users> findByEmail(String email);
    @Query("""
            SELECT u.id, u.first_name, u.last_name, u.email, u.password FROM usr u INNER JOIN token t ON u.id = t.user
            WHERE u.id = :id AND t.refresh_token = :refreshToken AND t.expired_at >= :expiredAt
    """)
    Optional<Users> findByIdAndTokensRefreshTokenAndTokensExpiredAtGreaterThan(Long id, String refreshToken, LocalDateTime expiredAt);
    @Query("""
            SELECT u.id, u.first_name, u.last_name, u.email, u.password FROM usr u INNER JOIN password_recovery pr ON u.id = pr.user
            WHERE pr.token = :token
    """)
    Optional<Users> findByPasswordRecoveriesToken(String token);
}
