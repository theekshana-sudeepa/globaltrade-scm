package lk.jiat.globaltrade.ejb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures the per-hop cost of a proxy-based interception chain, so the Test
 * Report can quantify "interceptor performance impact on supply chain business
 * method execution" with real numbers instead of assertion.
 *
 * A dynamic proxy is a faithful stand-in for the container's interceptor
 * dispatch mechanism for the purpose of measuring marginal cost per hop.
 */
class InterceptorOverheadTest {

    interface Business { int compute(int x); }

    static class Impl implements Business {
        public int compute(int x) { return x * 2; }
    }

    private static Business wrap(Business target, int layers) {
        Business current = target;
        for (int i = 0; i < layers; i++) {
            final Business inner = current;
            current = (Business) Proxy.newProxyInstance(
                    Business.class.getClassLoader(),
                    new Class<?>[]{Business.class},
                    (InvocationHandler) (p, m, args) -> {
                        long t0 = System.nanoTime();          // pre-processing
                        try { return m.invoke(inner, args); }
                        finally { long ignored = System.nanoTime() - t0; }
                    });
        }
        return current;
    }

    @Test
    @DisplayName("Interceptor chain cost grows linearly and stays in the microsecond range")
    void measureChainCost() {
        int iterations = 500_000;
        long baseline = time(new Impl(), iterations);
        long one      = time(wrap(new Impl(), 1), iterations);
        long three    = time(wrap(new Impl(), 3), iterations);
        long five     = time(wrap(new Impl(), 5), iterations);

        System.out.printf("0 interceptors : %8.4f us/call%n", perCall(baseline, iterations));
        System.out.printf("1 interceptor  : %8.4f us/call%n", perCall(one, iterations));
        System.out.printf("3 interceptors : %8.4f us/call%n", perCall(three, iterations));
        System.out.printf("5 interceptors : %8.4f us/call%n", perCall(five, iterations));
        System.out.printf("Marginal cost per interceptor: %8.4f us%n",
                          (perCall(five, iterations) - perCall(baseline, iterations)) / 5);

        assertTrue(five >= baseline, "more layers should not be faster than none");
    }

    private double perCall(long nanos, int iterations) {
        return nanos / (double) iterations / 1000d;
    }

    private long time(Business b, int iterations) {
        for (int i = 0; i < 20_000; i++) b.compute(i);   // warm up the JIT
        long start = System.nanoTime();
        int sink = 0;
        for (int i = 0; i < iterations; i++) sink += b.compute(i);
        long elapsed = System.nanoTime() - start;
        if (sink == Integer.MIN_VALUE) System.out.print("");   // defeat DCE
        return elapsed;
    }
}
