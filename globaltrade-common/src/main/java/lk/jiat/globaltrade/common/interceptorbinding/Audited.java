package lk.jiat.globaltrade.common.interceptorbinding;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Type-safe CDI binding for the logistics audit-trail interceptor. */
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Inherited
public @interface Audited {
    /** Business action recorded in the audit trail, e.g. "SHIPMENT_STATUS_UPDATE". */
    @Nonbinding String value() default "";
}
