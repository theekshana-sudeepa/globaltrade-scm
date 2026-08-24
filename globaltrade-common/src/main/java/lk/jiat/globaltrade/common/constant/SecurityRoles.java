package lk.jiat.globaltrade.common.constant;

/**
 * Central definition of the supply chain security roles.
 * Referenced by @RolesAllowed and by SessionContext.isCallerInRole(...).
 */
public final class SecurityRoles {

    public static final String ADMIN                = "ADMIN";
    public static final String LOGISTICS_COORDINATOR= "LOGISTICS_COORDINATOR";
    public static final String CUSTOMS_AGENT        = "CUSTOMS_AGENT";
    public static final String WAREHOUSE_MANAGER    = "WAREHOUSE_MANAGER";
    public static final String VENDOR_REP           = "VENDOR_REP";
    public static final String CARRIER_SYSTEM       = "CARRIER_SYSTEM";

    public static final String[] ALL = {
        ADMIN, LOGISTICS_COORDINATOR, CUSTOMS_AGENT,
        WAREHOUSE_MANAGER, VENDOR_REP, CARRIER_SYSTEM
    };

    private SecurityRoles() { }
}
