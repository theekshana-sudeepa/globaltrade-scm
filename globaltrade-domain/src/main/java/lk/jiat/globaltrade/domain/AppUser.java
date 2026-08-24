package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "ix_user_email", columnList = "email", unique = true)
})
@NamedQueries({
    @NamedQuery(name = "AppUser.findByEmail",
                query = "SELECT u FROM AppUser u WHERE u.email = :email AND u.enabled = true")
})
public class AppUser implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 120)
    private String name;

    /** PBKDF2-WithHmacSHA256 hash, never plain text. Format: iterations:saltB64:hashB64 */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "vendor_code", length = 32)
    private String vendorCode;      // set only for VENDOR_REP accounts

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 40)
    private Set<String> roles = new HashSet<>();

    public Long getId()                     { return id; }
    public String getEmail()                { return email; }
    public void setEmail(String e)          { this.email = e; }
    public String getName()                 { return name; }
    public void setName(String n)           { this.name = n; }
    public String getPasswordHash()         { return passwordHash; }
    public void setPasswordHash(String p)   { this.passwordHash = p; }
    public String getVendorCode()           { return vendorCode; }
    public void setVendorCode(String v)     { this.vendorCode = v; }
    public boolean isEnabled()              { return enabled; }
    public void setEnabled(boolean b)       { this.enabled = b; }
    public int getFailedAttempts()          { return failedAttempts; }
    public void setFailedAttempts(int f)    { this.failedAttempts = f; }
    public Instant getLockedUntil()         { return lockedUntil; }
    public void setLockedUntil(Instant i)   { this.lockedUntil = i; }
    public Set<String> getRoles()           { return roles; }
    public void setRoles(Set<String> r)     { this.roles = r; }
}
