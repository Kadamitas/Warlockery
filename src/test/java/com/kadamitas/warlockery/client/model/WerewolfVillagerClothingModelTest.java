package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class WerewolfVillagerClothingModelTest {
    private static final Path SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/model/WerewolfVillagerClothingModel.java"
    );
    private static final Path LAYER_SOURCE = Path.of(
        "src/main/java/com/kadamitas/warlockery/client/NativeVillagerClothingLayer.java"
    );

    @Test
    void ownsAProfessionTextureCompatibleWerewolfClothingMesh() {
        final ModelPart root = WerewolfVillagerClothingModel.createBodyLayer(false).bakeRoot();
        for (final String part : List.of(
            "profession_hat", "villager_coat", "left_sleeve", "right_sleeve",
            "left_trouser", "right_trouser"
        )) {
            assertFalse(requiredChild(root, part).isEmpty(), part);
        }
        assertFalse(requiredChild(requiredChild(root, "left_sleeve"), "left_forearm_sleeve").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_sleeve"), "right_forearm_sleeve").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "left_trouser"), "left_shin_trouser").isEmpty());
        assertFalse(requiredChild(requiredChild(root, "right_trouser"), "right_shin_trouser").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, 64, 64);

        final ModelPart noHat = WerewolfVillagerClothingModel.createBodyLayer(true).bakeRoot();
        assertTrue(requiredChild(noHat, "profession_hat").isEmpty());
        assertFalse(requiredChild(noHat, "villager_coat").isEmpty());
    }

    @Test
    void usesTheNativeVillagerProfessionLayerWithoutSharingCreatureGeometry() throws Exception {
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<WerewolfModel.State>"));
        assertTrue(source.contains("implements VillagerLikeModel<WerewolfModel.State>"));
        assertTrue(source.contains("TEXTURE_WIDTH = 64"));
        assertTrue(source.contains("TEXTURE_HEIGHT = 64"));
        for (final String forbidden : List.of(
            "LycanVillagerModel", "ArcaneCreatureModel", "CreatureModelProfile",
            "GeometryHelper", "ModelHelper", "WarlockeryModel"
        )) {
            assertFalse(source.contains(forbidden), forbidden);
        }

        final String layerSource = Files.readString(LAYER_SOURCE);
        assertTrue(layerSource.contains("VillagerProfessionLayer"));
        assertTrue(layerSource.contains("\"villager\""));
        assertTrue(layerSource.contains("state.getVillagerData() == null"));
        for (final String forbidden : List.of(
            "ModelPart", "CubeListBuilder", "LayerDefinition", "MeshDefinition",
            "WerewolfVillagerClothingModel", "LycanVillagerModel"
        )) {
            assertFalse(layerSource.contains(forbidden), forbidden);
        }
    }

    @Test
    void professionClothingFollowsTheWerewolfAggressiveAndPouncePose() {
        final WerewolfVillagerClothingModel model = new WerewolfVillagerClothingModel(
            WerewolfVillagerClothingModel.createBodyLayer(false).bakeRoot()
        );
        final WerewolfModel.State aggressive = new WerewolfModel.State();
        aggressive.aggressive = true;
        model.setupAnim(aggressive);
        assertEquals(-0.18F, requiredChild(model.root(), "profession_hat").xRot, 0.0001F);

        final WerewolfModel.State pouncing = new WerewolfModel.State();
        pouncing.pouncing = true;
        model.setupAnim(pouncing);
        assertEquals(0.46F, requiredChild(model.root(), "villager_coat").xRot, 0.0001F);
        assertEquals(-1.0F, requiredChild(model.root(), "left_sleeve").xRot, 0.0001F);
        assertEquals(-1.0F, requiredChild(model.root(), "right_sleeve").xRot, 0.0001F);
        assertEquals(
            -0.34F,
            requiredChild(requiredChild(model.root(), "left_sleeve"), "left_forearm_sleeve").xRot,
            0.0001F
        );
        assertEquals(
            -0.34F,
            requiredChild(requiredChild(model.root(), "right_sleeve"), "right_forearm_sleeve").xRot,
            0.0001F
        );
        assertEquals(0.62F, requiredChild(model.root(), "left_trouser").xRot, 0.0001F);
        assertEquals(0.62F, requiredChild(model.root(), "right_trouser").xRot, 0.0001F);
    }
}
