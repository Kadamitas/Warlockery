package com.kadamitas.warlockery.client.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OccultPresentationSyncContractTest {
    private static final Path MODEL_ROOT = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model"
    );
    private static final Path ENTITY_ROOT = Path.of(
        "src/main/java/com/kadamitas/warlockery/entity"
    );

    @Test
    void vampireCourtPublishesItsAuthoritativeIntentToBothIndependentBodies() throws Exception {
        final String entity = source(ENTITY_ROOT.resolve("VampireCourtEntity.java"));
        final String vampire = source(MODEL_ROOT.resolve("VampireModel.java"));
        final String thrall = source(MODEL_ROOT.resolve("BloodThrallModel.java"));

        assertSyncedIntent(entity, "courtState.intent()", "VampireCourtRules.Intent");
        assertTrue(entity.contains("EntityDataAccessor<Byte> DATA_PRESENTATION_ROLE"));
        assertTrue(entity.contains("presentationAssaultRole()"));
        assertTrue(vampire.contains("activityFor(entity.presentationIntent())"));
        assertTrue(vampire.contains("case FEED -> Activity.FEEDING"));
        assertTrue(vampire.contains("case ASSAULT_LEAD -> Activity.ASSAULT_LEAD"));
        assertTrue(thrall.contains(
            "activityFor(entity.presentationIntent(), entity.presentationAssaultRole())"
        ));
        assertTrue(thrall.contains("role == AssaultRole.BOUND_GUARD"));
        assertTrue(thrall.contains("case WAVERING -> Activity.WAVERING"));
        assertTrue(thrall.contains("case THRESHOLD_GUARD, INTERCEPT -> Activity.BOUND_GUARD"));
        assertFalse(vampire.contains("entity.isAggressive() ? Activity.STALKING"));
        assertFalse(thrall.contains("entity.isAggressive() ? Activity.ATTACKING"));
    }

    @Test
    void lycanVillagerPublishesEverySentinelIntentWithoutChangingVillagerIdentity() throws Exception {
        final String entity = source(ENTITY_ROOT.resolve("LycanVillagerEntity.java"));
        final String model = source(MODEL_ROOT.resolve("LycanVillagerModel.java"));

        assertSyncedIntent(entity, "sentinelState.intent()", "LycanVillagerRules.Intent");
        assertTrue(model.contains("activityFor(entity.presentationIntent())"));
        assertTrue(model.contains("case MOON_WATCH -> Activity.MOON_WATCH"));
        assertTrue(model.contains("case WARNING -> Activity.WARNING"));
        assertTrue(model.contains("case DEFEND -> Activity.DEFENDING"));
        assertTrue(model.contains("state.villagerData = entity.getVillagerData()"));
        assertFalse(model.contains("entity.isTrading() ? Activity.GREETING"));
    }

    @Test
    void warlockHunterPublishesItsEvidenceDrivenIntentInsteadOfOnlyVanillaFlags() throws Exception {
        final String entity = source(ENTITY_ROOT.resolve("WerewolfHunterEntity.java"));
        final String model = source(MODEL_ROOT.resolve("WerewolfHunterModel.java"));

        assertSyncedIntent(entity, "hunterState.intent()", "WerewolfHunterRules.Intent");
        assertTrue(model.contains("activityFor(entity.presentationIntent())"));
        assertTrue(model.contains("case INVESTIGATE -> Activity.INVESTIGATING"));
        assertTrue(model.contains("case ENGAGE -> Activity.ENGAGING"));
        assertTrue(model.contains("case RETREAT -> Activity.RETREATING"));
        assertFalse(model.contains("entity.isChargingCrossbow()"));
    }

    private static void assertSyncedIntent(
        final String source,
        final String authoritativeIntent,
        final String intentType
    ) {
        assertTrue(source.contains("EntityDataAccessor<Byte> DATA_PRESENTATION_INTENT"));
        assertTrue(source.contains("EntityDataSerializers.BYTE"));
        assertTrue(source.contains("defineSynchedData"));
        assertTrue(source.contains("presentationIntent()"));
        assertTrue(source.contains(intentType));
        assertTrue(source.contains("syncPresentation(" + authoritativeIntent + ")"));
    }

    private static String source(final Path path) throws Exception {
        return Files.readString(path);
    }
}
