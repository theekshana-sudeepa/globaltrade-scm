package lk.jiat.globaltrade.web.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2-WithHmacSHA256 password hashing.
 *
 * Replaces the plain-text comparison found in the reference teaching project.
 * Plain-text (or unsalted SHA-256) storage means one database dump compromises
 * every vendor and customs account across 50 countries. PBKDF2 with 210,000
 * iterations makes an offline dictionary attack economically infeasible, and
 * MessageDigest.isEqual gives a constant-time comparison so an attacker cannot
 * time their way to the correct hash prefix.
 *
 * Stored format: iterations:saltBase64:hashBase64
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS   = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static String hash(String plainPassword) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            byte[] key = derive(plainPassword.toCharArray(), salt, ITERATIONS);
            return ITERATIONS + ":"
                 + Base64.getEncoder().encodeToString(salt) + ":"
                 + Base64.getEncoder().encodeToString(key);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    public static boolean verify(String plainPassword, String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 3) return false;
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt     = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual   = derive(plainPassword.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);   // constant time
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                                   .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
