package lk.jiat.globaltrade.ejb;

import lk.jiat.globaltrade.common.exception.CarrierUnavailableException;
import lk.jiat.globaltrade.ejb.integration.CustomsGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the lock-free circuit breaker on CustomsGateway:
 * closed -> open after 5 consecutive failures -> fast-fail with
 * CarrierUnavailableException -> half-open probe recovery.
 */
class CustomsGatewayCircuitBreakerTest {

    private static final class ControllableCustomsGateway extends CustomsGateway {
        private final AtomicBoolean failNext;
        private final AtomicInteger remoteCalls = new AtomicInteger();

        ControllableCustomsGateway(long openDurationMs, AtomicBoolean failNext) {
            super(openDurationMs);
            this.failNext = failNext;
        }

        @Override
        protected String callRemoteGateway(String hsCode, String destinationCountry) {
            remoteCalls.incrementAndGet();
            if (failNext.get()) {
                throw new IllegalStateException("Simulated customs gateway outage");
            }
            return super.callRemoteGateway(hsCode, destinationCountry);
        }

        int remoteCalls() { return remoteCalls.get(); }
    }

    @Test
    @DisplayName("Successful customs submission returns formatted declaration reference")
    void successfulSubmissionReturnsReference() {
        CustomsGateway gateway = new CustomsGateway();
        String ref = gateway.submit("84713000", "DE");
        assertNotNull(ref);
        assertTrue(ref.startsWith("CD-DE-"), "expected country-prefixed reference, got: " + ref);
    }

    @Test
    @DisplayName("Circuit opens after five consecutive failures and fails fast without calling remote")
    void circuitOpensAfterThresholdAndFailsFast() {
        AtomicBoolean fail = new AtomicBoolean(true);
        ControllableCustomsGateway gateway = new ControllableCustomsGateway(60_000L, fail);

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class, () -> gateway.submit("84713000", "US"));
        }
        assertEquals(5, gateway.remoteCalls(), "expected 5 remote attempts before opening");

        // 6th call must short-circuit immediately without invoking remote gateway
        CarrierUnavailableException openEx = assertThrows(
                CarrierUnavailableException.class,
                () -> gateway.submit("84713000", "US"));
        assertEquals("CUSTOMS", openEx.getCarrierCode());
        assertEquals(5, gateway.remoteCalls(), "open circuit must not invoke remote gateway");
    }

    @Test
    @DisplayName("Half-open probe recovers circuit when downstream gateway returns to health")
    void halfOpenProbeClosesCircuitOnRecovery() throws InterruptedException {
        AtomicBoolean fail = new AtomicBoolean(true);
        ControllableCustomsGateway gateway = new ControllableCustomsGateway(25L, fail);

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class, () -> gateway.submit("84713000", "GB"));
        }
        assertThrows(CarrierUnavailableException.class, () -> gateway.submit("84713000", "GB"));

        // Wait for open window to expire and restore downstream health
        Thread.sleep(40L);
        fail.set(false);

        String ref = gateway.submit("84713000", "GB");
        assertTrue(ref.startsWith("CD-GB-"));
        assertEquals(6, gateway.remoteCalls());
    }
}
