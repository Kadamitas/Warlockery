package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.MimicryRules.Species;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MimicryStateTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theDurableRecordIsFixedCardinalityAndHoldsOnlyASchemaASpeciesAndTwoCounters() {
        assertEquals(4, MimicryState.class.getRecordComponents().length);
        final CompoundTag tag = MimicryState.empty(Species.HOLLOW_FUSE).write();
        assertEquals(4, tag.size(), "the encoded record must carry nothing beyond its four fields");
        assertFalse(tag.contains("Phase"));
        assertFalse(tag.contains("Subject"));
        assertFalse(tag.contains("Observer"));
        assertFalse(tag.contains("Recognition"));
    }

    @Test
    void everySpeciesRoundTripsItsOwnCounters() {
        for (final Species species : Species.values()) {
            final MimicryState written = MimicryState.empty(species)
                .withPrimaryCooldown(species.primaryCooldownTicks())
                .withEpisodeCooldown(MimicryState.EPISODE_COOLDOWN_TICKS);
            final MimicryState read = MimicryState.read(written.write(), species);
            assertEquals(written, read, species.name());
        }
    }

    @Test
    void aPayloadWrittenByAnotherSpeciesIsDiscardedWholeRatherThanPartlyAdopted() {
        final CompoundTag foreign = MimicryState.empty(Species.HOLLOW_DECOY)
            .withPrimaryCooldown(700)
            .write();
        final MimicryState read = MimicryState.read(foreign, Species.HOLLOW_FUSE);
        assertEquals(MimicryState.empty(Species.HOLLOW_FUSE), read);
    }

    @Test
    void anUnknownSchemaDiscardsOnlyTheMimicrySemanticsAndDefaultsSafely() {
        final CompoundTag future = MimicryState.empty(Species.PRESENTED_LIKENESS).write();
        future.putInt("SchemaVersion", MimicryRules.STATE_SCHEMA_VERSION + 41);
        assertEquals(
            MimicryState.empty(Species.PRESENTED_LIKENESS),
            MimicryState.read(future, Species.PRESENTED_LIKENESS)
        );
    }

    @Test
    void aMissingMalformedNegativeOrOverflowingRecordDefaultsAndClampsIndependently() {
        assertEquals(
            MimicryState.empty(Species.THRESHOLD_WEAVER),
            MimicryState.read(null, Species.THRESHOLD_WEAVER)
        );
        assertEquals(
            MimicryState.empty(Species.THRESHOLD_WEAVER),
            MimicryState.read(new CompoundTag(), Species.THRESHOLD_WEAVER)
        );
        assertEquals(0, new MimicryState(1, Species.THRESHOLD_WEAVER, -900, -1).primaryCooldown());
        assertEquals(
            Species.THRESHOLD_WEAVER.primaryCooldownTicks(),
            new MimicryState(1, Species.THRESHOLD_WEAVER, Integer.MAX_VALUE, 0).primaryCooldown()
        );
        assertEquals(
            MimicryState.EPISODE_COOLDOWN_TICKS,
            new MimicryState(1, Species.THRESHOLD_WEAVER, 0, Integer.MAX_VALUE).episodeCooldown()
        );
    }

    @Test
    void eachSpeciesClampsToItsOwnDeclaredPrimaryCooldownAndNotAToShared0ne() {
        assertEquals(600, Species.HOLLOW_FUSE.primaryCooldownTicks());
        assertEquals(400, Species.THRESHOLD_WEAVER.primaryCooldownTicks());
        assertEquals(800, Species.HOLLOW_DECOY.primaryCooldownTicks());
        assertEquals(1_200, Species.PRESENTED_LIKENESS.primaryCooldownTicks());
        assertEquals(600, new MimicryState(1, Species.HOLLOW_FUSE, 5_000, 0).primaryCooldown());
        assertEquals(1_200, new MimicryState(1, Species.PRESENTED_LIKENESS, 5_000, 0).primaryCooldown());
    }

    /**
     * The reconcile classification. A zero counter here means <em>due</em> and nothing else. There
     * is no arm of the form "if the timer reached zero, zero its dependents", because that is the
     * canonical-constructor defect where the constructor decides a phase ended and the tick branch
     * that owned ending it, and arming its cooldown, never runs.
     */
    @Test
    void zeroingOneCounterNeverZeroesTheOther() {
        final MimicryState state = new MimicryState(1, Species.HOLLOW_DECOY, 0, 150);
        assertEquals(0, state.primaryCooldown());
        assertEquals(150, state.episodeCooldown(), "a due primary counter must not clear the episode one");
        final MimicryState other = new MimicryState(1, Species.HOLLOW_DECOY, 500, 0);
        assertEquals(500, other.primaryCooldown());
        assertEquals(0, other.episodeCooldown());
    }

    @Test
    void loadedTicksDecrementBothCountersAndNeverGoNegativeOrCatchUp() {
        MimicryState state = new MimicryState(1, Species.HOLLOW_FUSE, 2, 1);
        state = state.tickLoaded();
        assertEquals(1, state.primaryCooldown());
        assertEquals(0, state.episodeCooldown());
        state = state.tickLoaded().tickLoaded().tickLoaded();
        assertEquals(0, state.primaryCooldown());
        assertEquals(0, state.episodeCooldown());
        assertTrue(state.episodeAllowed());
    }

    @Test
    void aFreshRecordIsImmediatelyAllowedToStartAnEpisode() {
        for (final Species species : Species.values()) {
            assertTrue(MimicryState.empty(species).episodeAllowed(), species.name());
            assertFalse(
                MimicryState.empty(species).withPrimaryCooldown(1).episodeAllowed(),
                species.name()
            );
        }
    }

    @Test
    void theEncodedRecordStaysWellUnderItsDeclaredRepresentativeCeiling() {
        for (final Species species : Species.values()) {
            final int size = MimicryState.empty(species)
                .withPrimaryCooldown(species.primaryCooldownTicks())
                .write()
                .toString()
                .length();
            assertTrue(size <= MimicryRules.MAX_STATE_BYTES, species + " encoded to " + size);
        }
    }
}


