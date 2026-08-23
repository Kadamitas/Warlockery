package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class EldritchWatcherModelTest {
    private static final Path SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/client/model/EldritchWatcherModel.java");
    private static final Path ENTITY_SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/entity/EldritchWatcherEntity.java");
    private static final Path TEXTURE=Path.of("src/main/resources/assets/warlockery/textures/entity/eldritch_watcher.png");

    @Test void ownsAlienObservingHierarchyAtlasAndExtraction() throws Exception {
        assertEquals(128,EldritchWatcherModel.TEXTURE_WIDTH); assertEquals(64,EldritchWatcherModel.TEXTURE_HEIGHT);
        final ModelPart root=EldritchWatcherModel.createBodyLayer().bakeRoot(); final ModelPart lens=requiredChild(root,"lens_head");
        assertAll(() -> assertFalse(requiredChild(lens,"central_eye").isEmpty()), () -> assertFalse(requiredChild(lens,"left_eye_cluster").isEmpty()),
            () -> assertFalse(requiredChild(lens,"right_eye_cluster").isEmpty()), () -> assertFalse(requiredChild(root,"suspended_thorax").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"rear_fins"),"rear_fin_upper").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"rear_fins"),"rear_fin_center").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"rear_fins"),"rear_fin_lower").isEmpty()));
        for (final String tendril : List.of("tendril_front", "tendril_left", "tendril_right", "tendril_rear")) {
            final ModelPart upper = requiredChild(root, tendril);
            final ModelPart lower = requiredChild(upper, tendril + "_lower");
            assertAll(
                () -> assertFalse(upper.isEmpty(), tendril + " upper"),
                () -> assertFalse(lower.isEmpty(), tendril + " lower"),
                () -> assertFalse(requiredChild(lower, tendril + "_terminal").isEmpty(), tendril + " terminal")
            );
        }
        assertUvsWithin(root,128,64); assertOpaqueUvs(root,ImageIO.read(TEXTURE.toFile()),cube->true); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(127,63)>>>24);
        final String source=Files.readString(SOURCE); assertTrue(source.contains("extends EntityModel<EldritchWatcherModel.State>"));
        assertTrue(source.contains("extractRenderState(final EldritchWatcherEntity entity"));
        assertTrue(source.contains("entity.presentationMode()")); assertFalse(source.contains("entity.watcherState()"));
        final String entitySource=Files.readString(ENTITY_SOURCE); assertTrue(entitySource.contains("DATA_PRESENTATION_MODE")); assertTrue(entitySource.contains("defineSynchedData")); assertTrue(entitySource.contains("syncPresentation(watcherState.mode())")); assertTrue(entitySource.contains("entityData.set(DATA_PRESENTATION_MODE"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }

    @Test void pinsBoundsNeutralMotionFocusSilhouettesAndTexture() throws Exception {
        final EldritchWatcherModel model=new EldritchWatcherModel(EldritchWatcherModel.createBodyLayer().bakeRoot());
        assertEquals(new Bounds(-10.378038F,1F,-8.382934F,10.378038F,23.140844F,8.382934F),bounds(model.root())); assertEquals("424ac757c6c02476f659878b8d69d92a94a1db1587aec0bb13506f9cba24feff",geometrySnapshot(model.root()));
        assertEquals("ac2f40fa7c49809877607acaf9f97eced814abbf7d42cedc5f57b6dd2ea94df7",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); assertEquals("0d23bb72f65db5ec7fe5fde0d00e0f7289591ff1924bca551e588bef85fd1469",imageSnapshot(softwareSnapshot(model.root(),Projection.SIDE,128,4)));
        model.root().yRot=.7853982F; assertEquals("cc275498dde371e2afc202f9c16cd2026953c4b59496ff97f57e5be95adc5484",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); model.root().yRot=0;
        final EldritchWatcherModel.State state=new EldritchWatcherModel.State(); state.walkAnimationPos=1.8F; state.walkAnimationSpeed=.5F; state.ageInTicks=40F; state.yRot=31F; state.xRot=-10F; model.setupAnim(state);
        assertEquals("5ffa134d8c5cc8dd3c8388a76fd570dfcffe941ac840c32e6b812e5050e1d181",geometrySnapshot(model.root())); state.focusing=true; model.setupAnim(state); assertEquals("5694012756fe36d0edec76fef0e20143fac6fd845942a9b229a25f11c76b9232",geometrySnapshot(model.root()));
        assertEquals(128,ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(64,ImageIO.read(TEXTURE.toFile()).getHeight()); assertEquals("3a4b9c93d1b505eb96c4da1449d61579c2195bed4a5d95da944c6869f462e001",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test
    void neutralLensAndJointedTendrilsKeepTheTurnaroundProportions() {
        final ModelPart root = new EldritchWatcherModel(
            EldritchWatcherModel.createBodyLayer().bakeRoot()
        ).root();
        final Bounds geometry = bounds(root);
        final float height = geometry.maxY() - geometry.minY();
        final float frontAspect = (geometry.maxX() - geometry.minX()) / height;
        final float sideAspect = (geometry.maxZ() - geometry.minZ()) / height;
        final ModelPart lens = requiredChild(root, "lens_head");
        final ModelPart thorax = requiredChild(root, "suspended_thorax");
        assertAll(
            () -> assertTrue(frontAspect >= 0.88F && frontAspect <= 1.02F,
                "the eye pod and splayed tendrils must read almost square from the front"),
            () -> assertTrue(sideAspect >= 0.68F && sideAspect <= 0.80F,
                "the rear lens barrel and tendrils must retain profile depth"),
            () -> assertTrue(bounds(lens).maxX() - bounds(lens).minX()
                    > (bounds(thorax).maxX() - bounds(thorax).minX()) * 2.5F,
                "the observing lens must dominate its narrow suspended collar")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final EldritchWatcherModel model=new EldritchWatcherModel(EldritchWatcherModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final EldritchWatcherModel.State action=new EldritchWatcherModel.State(); action.ageInTicks=31; action.focusing=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/eldritch_watcher-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }
}
