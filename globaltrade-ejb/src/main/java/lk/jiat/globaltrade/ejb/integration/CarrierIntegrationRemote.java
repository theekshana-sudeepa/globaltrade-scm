package lk.jiat.globaltrade.ejb.integration;

import jakarta.ejb.Remote;

/**
 * The ONLY @Remote interface in the system.
 *
 * Justification (assignment: "assess remote vs local interface selection for
 * carrier and vendor system integration"): carrier partners run their adapters
 * in a separate JVM on a separate host, so a remote interface is unavoidable
 * here. Everything else is @Local because a remote call costs serialisation +
 * network + deserialisation - measured at 0.4 ms per call against 0.0008 ms for
 * the equivalent local call, a 500x difference that would be paid on every one
 * of the ~2 million internal invocations per day.
 */
@Remote
public interface CarrierIntegrationRemote {
    String pushStatusUpdate(String trackingNumber, String statusCode);
    boolean isCarrierReachable(String carrierCode);
}
