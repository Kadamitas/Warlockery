package com.kadamitas.warlockery.client.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DedicatedCreatureModelPresentationConsumptionTest {
    private static final Path MODEL_ROOT = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model"
    );
    private static final List<Branch> BRANCHES = List.of(
        branch("AbyssalRegentModel", "presentationIntent", "presentationPhaseState"),
        branch("EmberhornArchfiendModel", "presentationIntent"),
        branch("BrambleColossusModel", "presentationPosted", "presentationNerve",
            "presentationLeg", "presentationPhase"),
        branch("WerewolfModel", "presentationHunger", "presentationFear", "presentationAction"),
        branch("FeralLycanModel", "presentationHunger", "presentationFear", "presentationAction"),
        branch("GoblinModel", "presentationIntent", "presentationAssaultMember",
            "presentationAssaultLeader", "presentationAssaultWave"),
        branch("HobgoblinModel", "presentationMode"),
        branch("StonebrokerModel", "presentationAction"),
        branch("ForgewardenModel", "presentationAction"),
        branch("HellhoundModel", "presentationBound", "presentationWarning",
            "presentationBiting", "presentationRetreating"),
        branch("IllusionCreeperModel", "presentationPhase"),
        branch("IllusionSpiderModel", "presentationPhase"),
        branch("IllusionZombieModel", "presentationPhase", "presentationAcceptedHits"),
        branch("ImpModel", "presentationAction"),
        branch("IronboundSentinelModel", "presentationCharged", "presentationPhase"),
        branch("NaamahModel", "presentationAction", "presentationPhase", "presentationGazeMending"),
        branch("PaleSteedModel", "presentationGait", "presentationBond", "presentationFatigue",
            "presentationBalking", "presentationResting"),
        branch("NightmareModel", "presentationGait", "presentationBond", "presentationFatigue",
            "presentationBalking", "presentationResting", "presentationWarning"),
        branch("ParasyticLouseModel", "presentationPhase", "presentationNourishment"),
        branch("SpectralFamiliarModel", "presentationPhase"),
        branch("StormSimianModel", "presentationCharge", "presentationHasGrip"),
        branch("ThornedPursuerModel", "presentationPhase", "presentationSnareCooldownRemaining"),
        branch("UmbralSigilModel", "presentationPhase")
    );
    private static final List<String> SERVER_RECORD_READS = List.of(
        "hierarchyState()", "colossusState()", "colossusTransient()", "packState()",
        "goblinEnclaveState()", "journeyState()", "goblinPatronState()", "lifeState()",
        "mimicCore()", "sentinelState()", "courtState()", "regenerationSuppressedUntil()",
        "steedState()", "tenancy()", "louseState()", "stormSimianState()",
        "pursuerRuntime()", "pursuerState()", "sigilState()"
    );

    @Test
    void everyDedicatedModelConsumesOnlyItsSynchronizedPresentationSurface() throws Exception {
        assertTrue(BRANCHES.size() >= 23);
        for (final Branch branch : BRANCHES) {
            final String source = Files.readString(MODEL_ROOT.resolve(branch.model() + ".java"));
            final String extractor = extractor(source, branch.model());
            for (final String getter : branch.getters()) {
                assertTrue(
                    extractor.contains("entity." + getter + "()"),
                    branch.model() + " must consume " + getter
                );
            }
            for (final String forbidden : SERVER_RECORD_READS) {
                assertFalse(
                    extractor.contains("entity." + forbidden),
                    branch.model() + " reads server-only state through " + forbidden
                );
            }
        }
    }

    private static String extractor(final String source, final String model) {
        final int start = source.indexOf("extractRenderState");
        assertTrue(start >= 0, model + " needs a concrete extractor");
        int end = source.indexOf("public static final class State", start);
        if (end < 0) end = source.indexOf("public static class State", start);
        assertTrue(end > start, model + " extractor boundary is missing");
        return source.substring(start, end);
    }

    private static Branch branch(final String model, final String... getters) {
        return new Branch(model, List.of(getters));
    }

    private record Branch(String model, List<String> getters) {
    }
}
