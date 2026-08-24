package lk.jiat.globaltrade.ejb.service;

import jakarta.ejb.Local;
import lk.jiat.globaltrade.common.exception.VendorValidationException;
import lk.jiat.globaltrade.domain.Vendor;

import java.util.List;

@Local
public interface VendorService {
    Vendor register(Vendor vendor) throws VendorValidationException;
    Vendor update(Vendor vendor) throws VendorValidationException;
    List<Vendor> listActive();
    int evaluateAllDue(int lookbackDays);
}
