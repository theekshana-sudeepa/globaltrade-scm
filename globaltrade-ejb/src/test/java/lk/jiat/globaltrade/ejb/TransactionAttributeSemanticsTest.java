package lk.jiat.globaltrade.ejb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Documents and asserts the expected container behaviour for each transaction
 * attribute used in the system. This is a SPECIFICATION test: it fails if
 * anyone changes the table below without changing the design documentation.
 *
 * The runtime behaviour itself is verified by the Arquillian integration suite
 * (see docs section 7.3); this test pins the decision matrix the design relies on.
 */
class TransactionAttributeSemanticsTest {

    enum Attr { REQUIRED, REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER }

    enum Outcome { JOIN_CALLER, NEW_TX, SUSPEND_AND_NEW, NO_TX, SUSPEND_NO_TX, ERROR }

    static Outcome withCallerTx(Attr a) {
        return switch (a) {
            case REQUIRED      -> Outcome.JOIN_CALLER;
            case REQUIRES_NEW  -> Outcome.SUSPEND_AND_NEW;
            case MANDATORY     -> Outcome.JOIN_CALLER;
            case SUPPORTS      -> Outcome.JOIN_CALLER;
            case NOT_SUPPORTED -> Outcome.SUSPEND_NO_TX;
            case NEVER         -> Outcome.ERROR;
        };
    }

    static Outcome withoutCallerTx(Attr a) {
        return switch (a) {
            case REQUIRED, REQUIRES_NEW -> Outcome.NEW_TX;
            case MANDATORY              -> Outcome.ERROR;
            case SUPPORTS, NOT_SUPPORTED, NEVER -> Outcome.NO_TX;
        };
    }

    @Nested
    @DisplayName("Caller already has transaction T1")
    class WithCallerTransaction {
        @Test void required()     { assertEquals(Outcome.JOIN_CALLER,     withCallerTx(Attr.REQUIRED)); }
        @Test void requiresNew()  { assertEquals(Outcome.SUSPEND_AND_NEW, withCallerTx(Attr.REQUIRES_NEW)); }
        @Test void mandatory()    { assertEquals(Outcome.JOIN_CALLER,     withCallerTx(Attr.MANDATORY)); }
        @Test void supports()     { assertEquals(Outcome.JOIN_CALLER,     withCallerTx(Attr.SUPPORTS)); }
        @Test void notSupported() { assertEquals(Outcome.SUSPEND_NO_TX,   withCallerTx(Attr.NOT_SUPPORTED)); }
        @Test void never()        { assertEquals(Outcome.ERROR,           withCallerTx(Attr.NEVER)); }
    }

    @Nested
    @DisplayName("Caller has no transaction")
    class WithoutCallerTransaction {
        @Test void required()     { assertEquals(Outcome.NEW_TX, withoutCallerTx(Attr.REQUIRED)); }
        @Test void requiresNew()  { assertEquals(Outcome.NEW_TX, withoutCallerTx(Attr.REQUIRES_NEW)); }
        @Test void mandatory()    { assertEquals(Outcome.ERROR,  withoutCallerTx(Attr.MANDATORY)); }
        @Test void supports()     { assertEquals(Outcome.NO_TX,  withoutCallerTx(Attr.SUPPORTS)); }
        @Test void notSupported() { assertEquals(Outcome.NO_TX,  withoutCallerTx(Attr.NOT_SUPPORTED)); }
        @Test void never()        { assertEquals(Outcome.NO_TX,  withoutCallerTx(Attr.NEVER)); }
    }
}
