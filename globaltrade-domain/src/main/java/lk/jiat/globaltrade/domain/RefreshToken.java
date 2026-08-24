package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * Server-side refresh token. This is what makes a stateless JWT revocable:
 * deleting the row terminates the session even though the access token itself
 * cannot be recalled (it simply expires within 5 minutes).
 */
@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "ix_rt_token",    columnList = "token", unique = true),
        @Index(name = "ix_rt_username", columnList = "username"),
        @Index(name = "ix_rt_expiry",   columnList = "expiry_at")
})
@NamedQueries({
    @NamedQuery(name = "RefreshToken.findValid",
                query = "SELECT r FROM RefreshToken r WHERE r.token = :token AND r.expiryAt > :now"),
    @NamedQuery(name = "RefreshToken.deleteByToken",
                query = "DELETE FROM RefreshToken r WHERE r.token = :token"),
    @NamedQuery(name = "RefreshToken.deleteExpired",
                query = "DELETE FROM RefreshToken r WHERE r.expiryAt < :now"),
    @NamedQuery(name = "RefreshToken.deleteAllForUser",
                query = "DELETE FROM RefreshToken r WHERE r.username = :username")
})
public class RefreshToken implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @Column(nullable = false, length = 160)
    private String username;

    @Column(name = "expiry_at", nullable = false)
    private Instant expiryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() { }

    public RefreshToken(String token, String username, Instant expiryAt) {
        this.token = token;
        this.username = username;
        this.expiryAt = expiryAt;
    }

    public Long getId()          { return id; }
    public String getToken()     { return token; }
    public String getUsername()  { return username; }
    public Instant getExpiryAt() { return expiryAt; }
    public Instant getCreatedAt(){ return createdAt; }
}
