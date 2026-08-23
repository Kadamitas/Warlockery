package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*; import java.security.MessageDigest; import java.util.HexFormat; import javax.imageio.ImageIO;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart; import org.junit.jupiter.api.Test;

final class EchoShadeModelTest {
    private static final Path SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/client/model/EchoShadeModel.java");
    private static final Path ENTITY_SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/entity/EchoShadeEntity.java");
    private static final Path TEXTURE=Path.of("src/main/resources/assets/warlockery/textures/entity/echo_shade.png");
    @Test void ownsReplayAfterimageHierarchyAtlasAndExtraction() throws Exception {
        assertEquals(128,EchoShadeModel.TEXTURE_WIDTH); assertEquals(128,EchoShadeModel.TEXTURE_HEIGHT); final ModelPart root=EchoShadeModel.createBodyLayer().bakeRoot();
        assertAll(() -> assertFalse(requiredChild(root,"asymmetric_mask").isEmpty()),() -> assertFalse(requiredChild(root,"runner_torso").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"leading_right_arm"),"leading_right_forearm").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"leading_left_arm"),"leading_left_forearm").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"leading_right_leg"),"leading_right_shin").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"leading_left_leg"),"leading_left_shin").isEmpty()),
            () -> assertFalse(requiredChild(root,"scarf_ribbon").isEmpty()),() -> assertFalse(requiredChild(root,"coat_tail_left").isEmpty()),
            () -> assertFalse(requiredChild(root,"coat_tail_right").isEmpty()));
        for (final String echo : List.of("first_afterimage", "second_afterimage")) {
            final ModelPart afterimage = requiredChild(root, echo);
            assertTrue(afterimage.isEmpty(), echo + " root must be a pose pivot, never a silhouette slab");
            for (final String feature : List.of("mask", "torso", "leading_arm", "trailing_arm", "leading_leg", "trailing_leg")) {
                assertFalse(requiredChild(afterimage, echo + "_" + feature).isEmpty(), echo + " " + feature);
            }
        }
        assertUvsWithin(root,128,128); assertOpaqueUvs(root,ImageIO.read(TEXTURE.toFile()),cube->true); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(127,127)>>>24); final String source=Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<EchoShadeModel.State>")); assertTrue(source.contains("extractRenderState(final EchoShadeEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()")); assertFalse(source.contains("entity.echoShadeState()"));
        final String entitySource=Files.readString(ENTITY_SOURCE); assertTrue(entitySource.contains("DATA_PRESENTATION_PHASE")); assertTrue(entitySource.contains("defineSynchedData")); assertTrue(entitySource.contains("syncPresentation(state.phase())")); assertTrue(entitySource.contains("entityData.set(DATA_PRESENTATION_PHASE"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }
    @Test void pinsBoundsNeutralMotionReplaySilhouettesAndTexture() throws Exception {
        final EchoShadeModel model=new EchoShadeModel(EchoShadeModel.createBodyLayer().bakeRoot()); assertEquals(new Bounds(-9.037844F,1.5855975F,-7.026856F,17.619362F,24.239714F,9.027625F),bounds(model.root())); assertEquals("860bcb0fa01bbd54414158faf9769a785dc4ef33529fc43a05904246ff6fe089",geometrySnapshot(model.root()));
        assertEquals("39dec7b0f8e3386c6feefbf694796fe5dbd57ac8b36e2379fc5c7b229ce97fa4",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); assertEquals("7621dc358146769300188d0cc26796b327e20bac902ccf088962564a082c29f7",imageSnapshot(softwareSnapshot(model.root(),Projection.SIDE,128,4)));
        model.root().yRot=.7853982F; assertEquals("c3c7f495432f15c54f1d43c00e0565e1206b4309480b3b8cc4d4168b886bd415",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); model.root().yRot=0;
        final EchoShadeModel.State state=new EchoShadeModel.State(); state.walkAnimationPos=3.3F; state.walkAnimationSpeed=.9F; state.ageInTicks=38F; state.yRot=29F; state.xRot=-7F; model.setupAnim(state);
        assertEquals("b421fda3b22c0983fe0bbf9b08763c7712d2732c606265a3e24c9f1762f1bc15",geometrySnapshot(model.root())); state.replaying=true; model.setupAnim(state); assertEquals("9b3542192620ae961a9be60e5bd5a666c1c34a2a42b8ec96058007a8bcccbfc4",geometrySnapshot(model.root()));
        assertEquals(128,ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(128,ImageIO.read(TEXTURE.toFile()).getHeight()); assertEquals("cfb42696e8f752fe74ea8734a4727d696e0f2fc5820ac16df6d59af89bd77920",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test
    void neutralRunnerIsAlreadyBentAndItsAfterimagesCarryProfileDepth() {
        final ModelPart root = new EchoShadeModel(EchoShadeModel.createBodyLayer().bakeRoot()).root();
        final Bounds geometry = bounds(root);
        final float height = geometry.maxY() - geometry.minY();
        final float frontAspect = (geometry.maxX() - geometry.minX()) / height;
        final float sideAspect = (geometry.maxZ() - geometry.minZ()) / height;
        final ModelPart rightLeg = requiredChild(root, "leading_right_leg");
        final ModelPart leftLeg = requiredChild(root, "leading_left_leg");
        assertAll(
            () -> assertTrue(frontAspect >= 0.98F && frontAspect <= 1.18F,
                "runner, scarf, coat, and two echoes must keep the wide layered front"),
            () -> assertTrue(sideAspect >= 0.60F && sideAspect <= 0.75F,
                "articulated echoes and coat layers must remain readable in profile"),
            () -> assertTrue(Math.abs(rightLeg.xRot) + Math.abs(rightLeg.zRot) >= 0.30F,
                "the leading leg must start in the concept's bent runner pose"),
            () -> assertTrue(Math.abs(leftLeg.xRot) + Math.abs(leftLeg.zRot) >= 0.30F,
                "the trailing leg must start bent rather than as an upright mannequin")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final EchoShadeModel model=new EchoShadeModel(EchoShadeModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final EchoShadeModel.State action=new EchoShadeModel.State(); action.ageInTicks=31; action.replaying=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/echo_shade-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }
}
