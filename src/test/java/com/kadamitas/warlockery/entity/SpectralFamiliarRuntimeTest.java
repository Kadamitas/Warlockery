package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.kadamitas.warlockery.entity.AnimalFamiliarRules.SearchOutcome;
import java.lang.reflect.RecordComponent;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SpectralFamiliarRuntimeTest {
    private static final java.util.UUID IDENTITY = new java.util.UUID(0, 57005);

    @Test
    void thePersistedCrossFamilyContractOnSearchOutcomeIsFrozenInNameAndInMeaning() {
        final RecordComponent[] components = SearchOutcome.class.getRecordComponents();
        assertEquals(2, components.length,
            "the on-disk format of this family's survey field is exactly two primitives; a third "
                + "component would be persisted by nobody and read back as a default");
        assertEquals("nextDueAt", components[0].getName());
        assertEquals(long.class, components[0].getType());
        assertEquals("consecutiveFailures", components[1].getName());
        assertEquals(int.class, components[1].getType());

        // The MEANING, which is the half a compiler cannot check.
        assertEquals(1, AnimalFamiliarRules.recordSearch(0L, 200, false, 0).consecutiveFailures(),
            "one fruitless search is one failure");
        assertEquals(3, AnimalFamiliarRules.recordSearch(0L, 200, false, 2).consecutiveFailures(),
            "and each further fruitless search adds exactly one to the count it is handed");
        assertEquals(0, AnimalFamiliarRules.recordSearch(0L, 200, true, 2).consecutiveFailures(),
            "ANY success resets the count to zero: it is a run of CONSECUTIVE failures and not a "
                + "rolling window, and this family's persisted SurveyFailures means exactly that");
        assertEquals(AnimalFamiliarRules.MAX_ROUTE_FAILURES,
            AnimalFamiliarRules.recordSearch(0L, 200, false, 99).consecutiveFailures(),
            "and the count saturates at the shared maximum rather than growing without bound");

        // The two primitives really are the two accessors, and the failure count really does cross
        // the reload seam carrying that meaning with it.
        final CompoundTag tag = SpectralFamiliarState.empty(IDENTITY, 1_000L)
            .withSurvey(new SearchOutcome(4_242L, 2), 0L)
            .write();
        assertEquals(4_242L, tag.getLongOr("NextSurveyAt", -1L),
            "nextDueAt is persisted under NextSurveyAt");
        assertEquals(2, tag.getIntOr("SurveyFailures", -1),
            "consecutiveFailures is persisted under SurveyFailures");
        assertEquals(2,
            SpectralFamiliarState.read(tag, IDENTITY, 1_000L).survey().consecutiveFailures(),
            "and it is the one half of the outcome that survives a reload, so its meaning is a "
                + "save-compatibility decision rather than a cosmetic one");
    }
}
