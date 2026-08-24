package lk.jiat.globaltrade.ejb.service;

import jakarta.ejb.Local;

@Local
public interface AuditService {
    void record(String correlationId, String action, String principal,
                String outcome, long durationMillis, String detail);
    void recordAsync(String correlationId, String action, String principal,
                     String outcome, long durationMillis, String detail);
}
