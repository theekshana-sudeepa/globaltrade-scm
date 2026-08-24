# run/ — one script per step

These exist because the four server-related IntelliJ run configurations need the
**Shell Script** plugin. If that plugin is not enabled in your IDE you get:

    Unknown run configuration type ShellScriptConfigurationType

Two ways forward — either is fine.

## Option A — enable the plugin (30 seconds, then the Run buttons work)

`IntelliJ IDEA → Settings… → Plugins → Installed →` search **Shell Script** →
tick it → **Apply** → **Restart IDE**.

The run configurations point at the scripts in this folder, so they work immediately
after the restart.

## Option B — just run the scripts from the terminal (no plugin needed)

Open the IntelliJ terminal (``Ctrl + ` ``) and run them in order:

```bash
./run/0-check-environment.sh     # verifies Java, Maven, MySQL, WildFly, ports
./run/1-start-wildfly.sh         # leave running; open a NEW tab for the rest
./run/2-configure-wildfly.sh     # datasource, timers, JASPI fix, JWT key
./run/3-deploy.sh                # builds if needed, deploys, lists deployments
./run/5-open-console.sh          # opens https://localhost:8443/globaltrade/
./run/4-tail-log.sh              # follow the server log
```

Honestly Option B is what a server wants anyway — you can see its log as it boots.

## Notes

- Every script defaults `WILDFLY_HOME` to `~/wildfly-31.0.1.Final`. Override it if
  yours lives elsewhere: `WILDFLY_HOME=/path ./run/1-start-wildfly.sh`
- Script 2 defaults the database password to `CHANGE_ME`. Override the same way:
  `DB_PASS=yourpassword ./run/2-configure-wildfly.sh`
- Script 2 must run **after** script 1 — it connects to the running server.
- The console is **https on 8443**, never http on 8080. `web.xml` declares a
  CONFIDENTIAL transport guarantee on `/api/*`.
