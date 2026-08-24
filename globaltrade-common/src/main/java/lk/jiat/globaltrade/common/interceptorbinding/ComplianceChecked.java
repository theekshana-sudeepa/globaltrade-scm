package lk.jiat.globaltrade.common.interceptorbinding;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Inherited
public @interface ComplianceChecked {
    @Nonbinding String[] regulations() default {"WCO-SAFE", "EU-UCC"};
}
