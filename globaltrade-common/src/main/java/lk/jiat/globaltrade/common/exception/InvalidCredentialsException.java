package lk.jiat.globaltrade.common.exception;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = false, inherited = true)
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message, null, false, false);
    }
}
