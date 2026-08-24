package lk.jiat.globaltrade.ejb.interceptor;

/** Keys shared between interceptors through InvocationContext.getContextData(). */
public final class CorrelationContext {
    public static final String CORRELATION_ID = "gt.correlationId";
    public static final String START_NANOS    = "gt.startNanos";
    public static final String PRINCIPAL      = "gt.principal";
    private CorrelationContext() { }
}
