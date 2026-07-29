package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VampireInitiationRulesTest {
    @Test
    void missingMatriarchBloodProducesAnExplicitDiagnosticState() {
        assertEquals(
            VampireInitiationRules.Status.MISSING_MATRIARCH_BLOOD,
            VampireInitiationRules.assess(false, SupernaturalForm.NONE)
        );
    }

    @Test
    void incompatibleExistingTransformationIsRejected() {
        assertEquals(
            VampireInitiationRules.Status.TRANSFORMATION_BLOCKED,
            VampireInitiationRules.assess(true, SupernaturalForm.WEREWOLF)
        );
    }

    @Test
    void mortalWithMatriarchBloodIsReady() {
        assertEquals(
            VampireInitiationRules.Status.READY,
            VampireInitiationRules.assess(true, SupernaturalForm.NONE)
        );
    }

    @Test
    void onlyMatriarchBloodIsPublishedAsTheInitiationOffering() throws IOException {
        final String tag = Files.readString(Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "item",
            "creature_interactions", "vampire_initiation.json"
        ));

        assertEquals(1, tag.lines().filter(line -> line.contains("warlockery:")).count());
        assertTrue(tag.contains("warlockery:ingredient_matriarchs_blood"));
    }
}
