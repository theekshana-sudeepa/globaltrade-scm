package lk.jiat.globaltrade.ejb;

import lk.jiat.globaltrade.common.exception.ShipmentNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PERFORMANCE TEST - the empirical evidence behind the "optimizing performance
 * through exception handling" claim (LO6). Produces the numbers quoted in the
 * Critical Analysis and Test Report.
 *
 * Compares three strategies for the same negative outcome:
 *   A. exception WITH a captured stack trace   (the naive default)
 *   B. exception WITHOUT a stack trace         (our SupplyChainException design)
 *   C. no exception at all - a null/Optional return
 */
class ExceptionPerformanceTest {

    private static final int WARMUP     = 20_000;
    private static final int ITERATIONS = 200_000;

    static class WithStackTrace extends Exception {
        WithStackTrace(String m) { super(m); }             // captures the stack
    }

    @Test
    @DisplayName("Suppressing stack traces makes business exceptions dramatically cheaper")
    void stackTraceSuppressionIsFaster() {
        for (int i = 0; i < WARMUP; i++) { throwWith(); throwWithout(); returnNull(); }

        long a = time(this::throwWith);
        long b = time(this::throwWithout);
        long c = time(this::returnNull);

        System.out.printf("A. throw WITH stack trace     : %,10d ns total  %8.3f us/op%n",
                          a, a / (double) ITERATIONS / 1000);
        System.out.printf("B. throw WITHOUT stack trace  : %,10d ns total  %8.3f us/op%n",
                          b, b / (double) ITERATIONS / 1000);
        System.out.printf("C. return null (no exception) : %,10d ns total  %8.3f us/op%n",
                          c, c / (double) ITERATIONS / 1000);
        System.out.printf("B is %.1fx faster than A; C is %.1fx faster than A%n",
                          a / (double) b, a / (double) c);

        assertTrue(b < a, "stack-trace-free exception should be cheaper than the default");
        assertTrue(c < b, "returning a value should be cheaper than throwing at all");
    }

    @Test
    @DisplayName("ShipmentNotFoundException carries no stack trace by design")
    void applicationExceptionHasNoStackTrace() {
        ShipmentNotFoundException e = new ShipmentNotFoundException("GT-LKGB-TEST");
        assertEquals(0, e.getStackTrace().length,
                "expected a stack-trace-free exception on the hot lookup-miss path");
        assertEquals("SCM-404-SHIPMENT", e.getErrorCode());
    }

    // ------------------------------------------------------------------
    private long time(Runnable r) {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) r.run();
        return System.nanoTime() - start;
    }

    private void throwWith() {
        try { throw new WithStackTrace("miss"); }
        catch (WithStackTrace ignored) { }
    }

    private void throwWithout() {
        try { throw new ShipmentNotFoundException("GT-XX"); }
        catch (ShipmentNotFoundException ignored) { }
    }

    private Object returnNull() { return null; }
}
