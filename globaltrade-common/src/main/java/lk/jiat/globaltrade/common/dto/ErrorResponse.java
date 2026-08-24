package lk.jiat.globaltrade.common.dto;

import java.io.Serializable;

/**
 * Uniform error envelope. Note that no stack trace and no internal message
 * ever reaches the client - only a correlation id the support team can grep.
 */
public record ErrorResponse(String errorCode,
                            String message,
                            int status,
                            String correlationId,
                            long timestamp) implements Serializable {

    public static ErrorResponse of(String errorCode, String message, int status, String correlationId) {
        return new ErrorResponse(errorCode, message, status, correlationId, System.currentTimeMillis());
    }
}
