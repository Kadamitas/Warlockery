package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*; import java.security.MessageDigest; import java.util.HexFormat; import javax.imageio.ImageIO;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart; import org.junit.jupiter.api.Test;

final class SpectralFamiliarModelTest {
    private static final Path SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/client/model/SpectralFamiliarModel.java");
    private static final Path TEXTURE=Path.of("src/main/resources/assets/warlockery/textures/entity/spectral_familiar.png");
    @Test void ownsLongEaredFoxCatHierarchyAtlasAndExtraction() throws Exception {
        assertEquals(128,SpectralFamiliarModel.TEXTURE_WIDTH); assertEquals(64,SpectralFamiliarModel.TEXTURE_HEIGHT);
        final ModelPart root=SpectralFamiliarModel.createBodyLayer().bakeRoot(); final ModelPart head=requiredChild(root,"head");
        assertAll(() -> assertFalse(requiredChild(head,"left_ear_fin").isEmpty()),() -> assertFalse(requiredChild(head,"right_ear_fin").isEmpty()),
            () -> assertFalse(requiredChild(head,"forehead_charm").isEmpty()),() -> assertFalse(requiredChild(root,"body").isEmpty()));
        for (final String leg : List.of("front_left_leg", "front_right_leg", "rear_left_leg", "rear_right_leg")) {
            final ModelPart upper = requiredChild(root, leg);
            final ModelPart lower = requiredChild(upper, leg + "_lower");
            assertAll(
                () -> assertFalse(upper.isEmpty(), leg + " upper"),
                () -> assertFalse(lower.isEmpty(), leg + " lower"),
                () -> assertFalse(requiredChild(lower, leg + "_paw").isEmpty(), leg + " paw")
            );
        }
        final ModelPart tail = requiredChild(root,"lantern_tail");
        final ModelPart middle = requiredChild(tail,"tail_middle");
        final ModelPart tip = requiredChild(middle,"tail_tip");
        assertFalse(requiredChild(tip,"tail_lantern").isEmpty());
        assertUvsWithin(root,128,64); assertOpaqueUvs(root,ImageIO.read(TEXTURE.toFile()),cube->true); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(127,63)>>>24);
        final String source=Files.readString(SOURCE); assertTrue(source.contains("extends EntityModel<SpectralFamiliarModel.State>"));
        assertTrue(source.contains("extractRenderState(final SpectralFamiliarEntity entity")); assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }
    @Test void pinsBoundsNeutralMotionSurveySilhouettesAndTexture() throws Exception {
        final SpectralFamiliarModel model=new SpectralFamiliarModel(SpectralFamiliarModel.createBodyLayer().bakeRoot());
        assertEquals(new Bounds(-6.9794025F,3.9504883F,-8F,15.561065F,24F,9.518499F),bounds(model.root())); assertEquals("21c771010d331d3433de24f361f5ea62f1e95f5ae005bc5185dcaacb67adcca1",geometrySnapshot(model.root()));
        assertEquals("78f8310016b80ab33923fb1a597a76dbb0b636a5254b6079a5e0243cb4e4dbd6",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); assertEquals("61cd514ad07c9d9f7ecc44f22c1a7c6429c0f51fc586413cae6afa7ff01fe378",imageSnapshot(softwareSnapshot(model.root(),Projection.SIDE,128,4)));
        model.root().yRot=.7853982F; assertEquals("34be21dccbb5b7d81fb8287832de2274ebffa375b246c934565d6fc146411c09",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); model.root().yRot=0;
        final SpectralFamiliarModel.State state=new SpectralFamiliarModel.State(); state.walkAnimationPos=3F; state.walkAnimationSpeed=.8F; state.ageInTicks=25F; state.yRot=-22F; state.xRot=5F; model.setupAnim(state);
        assertEquals("08818275de6e9b6b582a0ec7066858a3d201883969ec4be5dbcfbb4175ad7050",geometrySnapshot(model.root())); state.surveying=true; model.setupAnim(state); assertEquals("9792a3c72f4b9246e02d40a0923f14a2ae862924d9ef7298e5182550767599b1",geometrySnapshot(model.root()));
        assertEquals(128,ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(64,ImageIO.read(TEXTURE.toFile()).getHeight()); assertEquals("cf11618d73de90d558d52f6d034334af1966e71e066779eeb76f37d87e2abc35",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }
    @Test void surveyCrouchesAndPresentsTheLanternTailBesideItsBody() {
        final SpectralFamiliarModel neutral=new SpectralFamiliarModel(SpectralFamiliarModel.createBodyLayer().bakeRoot());
        final SpectralFamiliarModel.State neutralState=new SpectralFamiliarModel.State(); neutralState.ageInTicks=31F; neutral.setupAnim(neutralState);
        final SpectralFamiliarModel survey=new SpectralFamiliarModel(SpectralFamiliarModel.createBodyLayer().bakeRoot());
        final SpectralFamiliarModel.State surveyState=new SpectralFamiliarModel.State(); surveyState.ageInTicks=31F; surveyState.surveying=true; survey.setupAnim(surveyState);
        final ModelPart neutralHead=requiredChild(neutral.root(),"head"), surveyHead=requiredChild(survey.root(),"head");
        final ModelPart neutralBody=requiredChild(neutral.root(),"body"), surveyBody=requiredChild(survey.root(),"body");
        final ModelPart surveyTail=requiredChild(survey.root(),"lantern_tail");
        assertAll(
            () -> assertTrue(surveyHead.y>=neutralHead.y+1.25F,"survey head must lower into a crouch"),
            () -> assertTrue(surveyBody.y>=neutralBody.y+1.0F,"survey body must lower into a crouch"),
            () -> assertTrue(Math.abs(surveyTail.yRot)>=.55F,"lantern tail must sweep beside the body instead of becoming a vertical column")
        );
    }

    @Test
    void neutralQuadrupedIsBroadFromTheFrontCompactInProfileAndGroundedOnFourPaws() {
        final ModelPart root = new SpectralFamiliarModel(
            SpectralFamiliarModel.createBodyLayer().bakeRoot()
        ).root();
        final Bounds geometry = bounds(root);
        final float height = geometry.maxY() - geometry.minY();
        final float frontAspect = (geometry.maxX() - geometry.minX()) / height;
        final float sideAspect = (geometry.maxZ() - geometry.minZ()) / height;
        assertAll(
            () -> assertTrue(frontAspect >= 1.05F && frontAspect <= 1.28F,
                "finned ears, cheek width, paw stance, and curled tail must make a broad front"),
            () -> assertTrue(sideAspect >= 0.70F && sideAspect <= 0.88F,
                "the compact cat body must not become an overlong tail column in profile"),
            () -> assertEquals(24.0F, geometry.maxY(), 0.001F,
                "all four articulated paws must resolve onto the ground plane")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final SpectralFamiliarModel model=new SpectralFamiliarModel(SpectralFamiliarModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final SpectralFamiliarModel.State action=new SpectralFamiliarModel.State(); action.ageInTicks=31; action.surveying=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/spectral_familiar-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }
}
