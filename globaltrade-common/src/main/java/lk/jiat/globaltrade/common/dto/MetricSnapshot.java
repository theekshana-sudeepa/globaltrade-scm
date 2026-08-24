package lk.jiat.globaltrade.common.dto;

import java.io.Serializable;

/** One row of the performance-monitoring endpoint. */
public record MetricSnapshot(String method,
                             long invocations,
                             long totalNanos,
                             long maxNanos,
                             long failures) implements Serializable {

    public double averageMillis() {
        return invocations == 0 ? 0d : (totalNanos / (double) invocations) / 1_000_000d;
    }
    public double maxMillis() { return maxNanos / 1_000_000d; }
}
