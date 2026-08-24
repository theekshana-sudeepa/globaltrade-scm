package lk.jiat.globaltrade.ejb.service;

import jakarta.ejb.Local;
import lk.jiat.globaltrade.common.exception.CustomsComplianceException;
import lk.jiat.globaltrade.domain.CustomsDeclaration;

import java.util.List;

@Local
public interface CustomsDeclarationService {
    CustomsDeclaration fileDeclaration(CustomsDeclaration declaration) throws CustomsComplianceException;
    List<CustomsDeclaration> findDueBefore(java.time.Instant cutoff);
    void markOverdue(Long declarationId);
}
