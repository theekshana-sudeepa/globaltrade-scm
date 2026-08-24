package lk.jiat.globaltrade.web.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;
import lk.jiat.globaltrade.web.service.UserAccountService;

import java.util.Set;

/**
 * Custom IdentityStore - the JAAS-equivalent login module for Jakarta Security.
 * Validates the credential and returns the caller principal plus the caller's
 * groups (which become the roles @RolesAllowed and isCallerInRole() see).
 */
@ApplicationScoped
public class SupplyChainIdentityStore implements IdentityStore {

    @Inject
    private UserAccountService userAccountService;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (credential instanceof UsernamePasswordCredential upc) {
            if (userAccountService.isLockedOut(upc.getCaller())) {
                return CredentialValidationResult.INVALID_RESULT;
            }
            if (userAccountService.validate(upc.getCaller(), upc.getPasswordAsString())) {
                Set<String> roles = userAccountService.rolesOf(upc.getCaller());
                userAccountService.recordSuccess(upc.getCaller());
                return new CredentialValidationResult(upc.getCaller(), roles);
            }
            userAccountService.recordFailure(upc.getCaller());
        }
        return CredentialValidationResult.INVALID_RESULT;
    }

    @Override
    public Set<ValidationType> validationTypes() {
        return Set.of(ValidationType.VALIDATE, ValidationType.PROVIDE_GROUPS);
    }
}
