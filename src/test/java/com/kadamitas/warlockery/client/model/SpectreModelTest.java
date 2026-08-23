package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*; import java.security.MessageDigest; import java.util.HexFormat; import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart; import org.junit.jupiter.api.Test;

final class SpectreModelTest {
    private static final Path SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/client/model/SpectreModel.java");
    private static final Path ENTITY_SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/entity/SpectreEntity.java");
    private static final Path TEXTURE=Path.of("src/main/resources/assets/warlockery/textures/entity/spectre.png");
    @Test void ownsNonAttackingDreadManifestationHierarchyAtlasAndSyncedExtraction() throws Exception {
        assertEquals(128,SpectreModel.TEXTURE_WIDTH); assertEquals(128,SpectreModel.TEXTURE_HEIGHT);
        final ModelPart root=SpectreModel.createBodyLayer().bakeRoot(); final ModelPart cowl=requiredChild(root,"empty_cowl");
        assertAll(() -> assertFalse(requiredChild(cowl,"obscured_inner_face").isEmpty()),() -> assertFalse(requiredChild(root,"hooked_mantle").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"hooked_mantle"),"mantle_left_hook").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"hooked_mantle"),"mantle_right_hook").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"hooked_mantle"),"mantle_back_layer").isEmpty()),
            () -> assertFalse(requiredChild(root,"manifestation_shroud").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(requiredChild(root,"right_reaching_arm"),"right_reaching_forearm"),"right_cold_touch_tip").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(requiredChild(root,"left_reaching_arm"),"left_reaching_forearm"),"left_cold_touch_tip").isEmpty()),
            () -> assertFalse(requiredChild(root,"fork_tail_left").isEmpty()),() -> assertFalse(requiredChild(root,"fork_tail_right").isEmpty()));
        assertUvsWithin(root,128,128); assertOpaqueUvs(root,ImageIO.read(TEXTURE.toFile()),
            cube -> !cube.path().endsWith("cold_touch_tip")
                && !cube.path().equals("/empty_cowl/obscured_inner_face")
                && !cube.path().startsWith("/fork_tail_")
                && !cube.path().equals("/manifestation_shroud")); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(127,127)>>>24);
        final String source=Files.readString(SOURCE); assertTrue(source.contains("extends EntityModel<SpectreModel.State>")); assertTrue(source.contains("extractRenderState(final SpectreEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()")); assertFalse(source.contains("entity.spectreState()"));
        assertTrue(source.contains("state.manifesting")); assertFalse(source.contains("lunging"));
        final String entitySource=Files.readString(ENTITY_SOURCE); assertTrue(entitySource.contains("DATA_PRESENTATION_PHASE")); assertTrue(entitySource.contains("defineSynchedData")); assertTrue(entitySource.contains("syncPresentation(state.phase())")); assertTrue(entitySource.contains("entityData.set(DATA_PRESENTATION_PHASE"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }
    @Test void pinsBoundsNeutralMotionManifestationSilhouettesAndTexture() throws Exception {
        final SpectreModel model=new SpectreModel(SpectreModel.createBodyLayer().bakeRoot()); assertEquals(new Bounds(-10.586335F,.5642996F,-10.295918F,10.586335F,24.780731F,4.520411F),bounds(model.root())); assertEquals("f10c6439e5995c6e5529c9eaddcf031a8afd7de40c71a36ac7014343a1ddd961",geometrySnapshot(model.root()));
        assertEquals("0103a901f3d0517248d8fdef7b39ca113a9da79739bf035f414bbbe5e450aaeb",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); assertEquals("c2ae9e6420f674937c71a91be89223a83a6e663ed4c03c08ff4f83f5cf1f1542",imageSnapshot(softwareSnapshot(model.root(),Projection.SIDE,128,4)));
        model.root().yRot=.7853982F; assertEquals("ee317cbb4f966e8b0c83203624d3293c785c37bc63c76f9e63a4279e5218c60c",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); model.root().yRot=0;
        final SpectreModel.State state=new SpectreModel.State(); state.walkAnimationPos=2.6F; state.walkAnimationSpeed=.75F; state.ageInTicks=33F; state.yRot=-27F; state.xRot=11F; model.setupAnim(state);
        assertEquals("c2039fdd3065e5b311c9fa45305e445b61a436e3533c14e0df75140b67eb4104",geometrySnapshot(model.root())); SpectreModel.State.class.getField("manifesting").setBoolean(state,true); model.setupAnim(state); assertEquals("123ff9511159901c72055990885a1c9b15418fcf0b8ef263def900b02401fc2c",geometrySnapshot(model.root()));
        assertEquals(128,ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(128,ImageIO.read(TEXTURE.toFile()).getHeight()); assertEquals("1b568d9b7534bb3b2b1eeebe690a73242aca2285ff3c5fc1b5a1de1f7ea372aa",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test
    void neutralCowlMantleAndReachingArmsKeepBroadFrontAndDeepProfile() {
        final ModelPart root = new SpectreModel(SpectreModel.createBodyLayer().bakeRoot()).root();
        final Bounds geometry = bounds(root);
        final float height = geometry.maxY() - geometry.minY();
        final float frontAspect = (geometry.maxX() - geometry.minX()) / height;
        final float sideAspect = (geometry.maxZ() - geometry.minZ()) / height;
        final ModelPart cowl = requiredChild(root, "empty_cowl");
        final ModelPart mantle = requiredChild(root, "hooked_mantle");
        assertAll(
            () -> assertTrue(frontAspect >= 0.80F && frontAspect <= 0.94F,
                "hooked mantle and long arms must form the concept's broad upper silhouette"),
            () -> assertTrue(sideAspect >= 0.55F && sideAspect <= 0.70F,
                "the cowl, mantle layers, and reaching arms must not flatten in profile"),
            () -> assertTrue(bounds(mantle).maxX() - bounds(mantle).minX()
                    > bounds(cowl).maxX() - bounds(cowl).minX(),
                "the mantle must hook beyond the empty cowl")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final SpectreModel model=new SpectreModel(SpectreModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final SpectreModel.State action=new SpectreModel.State(); action.ageInTicks=31; SpectreModel.State.class.getField("manifesting").setBoolean(action,true); model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/spectre-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }
}
