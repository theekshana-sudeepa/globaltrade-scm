package lk.jiat.globaltrade.ejb.interceptor;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lk.jiat.globaltrade.common.exception.VendorValidationException;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.domain.Vendor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * INTERCEPTOR 2 of 5.
 *
 * Validates - and NORMALISES - vendor arguments before the business method sees
 * them. This demonstrates InvocationContext.setParameters(): country codes are
 * upper-cased and vendor codes trimmed in flight, so the business bean never has
 * to defend against formatting variation from 50 different national portals.
 */
@Audited
@Interceptor
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 100)
public class VendorDataValidationInterceptor {

    private static final Pattern ISO2      = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern VENDOR_CD = Pattern.compile("^[A-Z0-9\\-]{3,32}$");
    private static final Pattern EMAIL     = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    @AroundInvoke
    public Object validate(InvocationContext ctx) throws Exception {
        Object[] params = ctx.getParameters();
        boolean mutated = false;
        List<String> violations = new ArrayList<>();

        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Vendor v) {
                if (v.getVendorCode() != null) {
                    String normalised = v.getVendorCode().trim().toUpperCase();
                    if (!normalised.equals(v.getVendorCode())) { v.setVendorCode(normalised); mutated = true; }
                    if (!VENDOR_CD.matcher(normalised).matches())
                        violations.add("vendorCode '" + normalised + "' must match " + VENDOR_CD.pattern());
                } else {
                    violations.add("vendorCode is required");
                }

                if (v.getCountryCode() != null) {
                    String cc = v.getCountryCode().trim().toUpperCase();
                    if (!cc.equals(v.getCountryCode())) { v.setCountryCode(cc); mutated = true; }
                    if (!ISO2.matcher(cc).matches())
                        violations.add("countryCode '" + cc + "' is not a valid ISO-3166 alpha-2 code");
                } else {
                    violations.add("countryCode is required");
                }

                if (v.getContactEmail() != null && !EMAIL.matcher(v.getContactEmail()).matches())
                    violations.add("contactEmail '" + v.getContactEmail() + "' is malformed");

                if (v.getName() == null || v.getName().isBlank())
                    violations.add("vendor name is required");
            }
        }

        if (!violations.isEmpty()) {
            // Application exception: fails fast BEFORE the transaction does any work.
            throw new VendorValidationException(violations);
        }
        if (mutated) ctx.setParameters(params);   // hand the normalised arguments onward
        return ctx.proceed();
    }
}
