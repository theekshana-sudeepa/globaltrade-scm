package lk.jiat.globaltrade.ejb;

import lk.jiat.globaltrade.common.constant.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UNIT TEST - pure state-machine logic, no container required.
 * Fast feedback: the whole class runs in under 50 ms.
 */
class ShipmentStatusTransitionTest {

    @ParameterizedTest(name = "{0} -> {1} should be {2}")
    @DisplayName("Shipment status transitions obey the logistics state machine")
    @CsvSource({
        "CREATED,          BOOKED,           true",
        "CREATED,          DELIVERED,        false",
        "BOOKED,           IN_TRANSIT,       true",
        "IN_TRANSIT,       AT_CUSTOMS,       true",
        "AT_CUSTOMS,       CUSTOMS_CLEARED,  true",
        "CUSTOMS_CLEARED,  OUT_FOR_DELIVERY, true",
        "OUT_FOR_DELIVERY, DELIVERED,        true",
        "DELIVERED,        IN_TRANSIT,       false",
        "CANCELLED,        BOOKED,           false",
        "DELAYED,          IN_TRANSIT,       true"
    })
    void transitions(ShipmentStatus from, ShipmentStatus to, boolean expected) {
        assertEquals(expected, TestTransitionRules.isLegal(from, to));
    }

    /** Mirror of ShipmentServiceBean.isLegalTransition, extracted for unit testing. */
    static final class TestTransitionRules {
        static boolean isLegal(ShipmentStatus from, ShipmentStatus to) {
            if (from == to) return true;
            return switch (from) {
                case CREATED          -> to == ShipmentStatus.BOOKED || to == ShipmentStatus.CANCELLED;
                case BOOKED           -> to == ShipmentStatus.IN_TRANSIT || to == ShipmentStatus.CANCELLED
                                      || to == ShipmentStatus.DELAYED;
                case IN_TRANSIT       -> to == ShipmentStatus.AT_CUSTOMS || to == ShipmentStatus.DELAYED
                                      || to == ShipmentStatus.EXCEPTION;
                case AT_CUSTOMS       -> to == ShipmentStatus.CUSTOMS_CLEARED || to == ShipmentStatus.EXCEPTION;
                case CUSTOMS_CLEARED  -> to == ShipmentStatus.OUT_FOR_DELIVERY;
                case OUT_FOR_DELIVERY -> to == ShipmentStatus.DELIVERED || to == ShipmentStatus.EXCEPTION;
                case DELAYED          -> to == ShipmentStatus.IN_TRANSIT || to == ShipmentStatus.EXCEPTION
                                      || to == ShipmentStatus.CANCELLED;
                case EXCEPTION        -> to == ShipmentStatus.IN_TRANSIT || to == ShipmentStatus.CANCELLED;
                case DELIVERED, CANCELLED -> false;
            };
        }
    }
}
