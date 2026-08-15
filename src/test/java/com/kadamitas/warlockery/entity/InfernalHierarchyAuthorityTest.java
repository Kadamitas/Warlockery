package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.InfernalHierarchyRules.AuthorityClass;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InfernalHierarchyAuthorityTest {
    private static final UUID DIRECT = new UUID(10L, 1L);
    private static final UUID ANIMUS = new UUID(10L, 2L);
    private static final UUID STRANGER = new UUID(10L, 3L);

    @Test
    void matchingLegacyKeysResolveToThatPlayer() {
        assertEquals(DIRECT, InfernalHierarchyRules.effectiveOwner(
            Optional.of(DIRECT), Optional.of(DIRECT)).orElseThrow());
    }

    @Test
    void conflictingLegacyKeysResolveToTheDirectBargainOwner() {
        assertEquals(DIRECT, InfernalHierarchyRules.effectiveOwner(
            Optional.of(DIRECT), Optional.of(ANIMUS)).orElseThrow());
        assertFalse(InfernalHierarchyRules.commandAccepted(
            ANIMUS, Optional.of(DIRECT), Optional.of(ANIMUS)),
            "commands from the conflicting Animus key are rejected");
        assertTrue(InfernalHierarchyRules.commandAccepted(
            DIRECT, Optional.of(DIRECT), Optional.of(ANIMUS)));
    }

    @Test
    void absentOwnersRemainAuthoritativeAndStrangersAreRefused() {
        assertTrue(InfernalHierarchyRules.effectiveOwner(
            Optional.of(DIRECT), Optional.empty()).isPresent(),
            "an unloaded or logged-out owner stays the pact authority");
        assertFalse(InfernalHierarchyRules.commandAccepted(
            STRANGER, Optional.of(DIRECT), Optional.empty()));
        assertFalse(InfernalHierarchyRules.commandAccepted(
            STRANGER, Optional.empty(), Optional.empty()),
            "an unowned Demon accepts commands from nobody");
    }

    @Test
    void hazardAlwaysOutranksEveryPactAndOrder() {
        assertEquals(AuthorityClass.HAZARD,
            InfernalHierarchyRules.resolveAuthority(true, true, true, true, true, true, true));
    }

    @Test
    void courtOrdersOutrankSquadOrdersAndBothOutrankSelfDefense() {
        assertEquals(AuthorityClass.REGENT_ORDER,
            InfernalHierarchyRules.resolveAuthority(false, false, false, false, true, true, true));
        assertEquals(AuthorityClass.ARCHFIEND_ORDER,
            InfernalHierarchyRules.resolveAuthority(false, false, false, false, false, true, true));
        assertEquals(AuthorityClass.SELF_DEFENSE,
            InfernalHierarchyRules.resolveAuthority(false, false, false, false, false, false, true));
    }
}
