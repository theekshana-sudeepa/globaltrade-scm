package lk.jiat.globaltrade.web.config;

import jakarta.annotation.security.DeclareRoles;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import lk.jiat.globaltrade.common.constant.SecurityRoles;

@ApplicationPath("/api")
@DeclareRoles({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR,
               SecurityRoles.CUSTOMS_AGENT, SecurityRoles.WAREHOUSE_MANAGER,
               SecurityRoles.VENDOR_REP, SecurityRoles.CARRIER_SYSTEM})
public class RestApplication extends Application { }
