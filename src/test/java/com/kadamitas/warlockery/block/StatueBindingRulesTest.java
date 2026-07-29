package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StatueBindingRulesTest {
    @Test
    void patronBindingReportsEachFailureAndSuccessState() {
        assertEquals("missing_binding", StatueRules.patron(false, false, false).diagnostic());
        assertEquals("missing_bound_target", StatueRules.patron(true, false, false).diagnostic());
        assertEquals("missing_offering", StatueRules.patron(true, true, false).diagnostic());
        assertFalse(StatueRules.patron(true, false, true).success());
        assertTrue(StatueRules.patron(true, true, true).success());
    }
}
