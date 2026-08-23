package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class BansheeModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/BansheeModel.java");
    private static final Path ENTITY_SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/entity/BansheeEntity.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/banshee.png");

    @Test
    void ownsEmbodiedWailingHierarchyAtlasAndExtraction() throws Exception {
        assertEquals(128, BansheeModel.TEXTURE_WIDTH);
        assertEquals(128, BansheeModel.TEXTURE_HEIGHT);
        final ModelPart root = BansheeModel.createBodyLayer().bakeRoot();
        final ModelPart head = requiredChild(root, "face_head");
        final ModelPart shoulderYoke = requiredChild(root, "shoulder_yoke");
        final ModelPart rightArm = requiredChild(root, "right_arm");
        final ModelPart leftArm = requiredChild(root, "left_arm");
        final ModelPart dress = requiredChild(root, "layered_dress");
        assertAll(
            () -> assertFalse(requiredChild(head, "open_mouth").isEmpty()),
            () -> assertFalse(requiredChild(head, "hair_crown").isEmpty()),
            () -> assertFalse(requiredChild(head, "hair_frame_left").isEmpty()),
            () -> assertFalse(requiredChild(head, "hair_frame_right").isEmpty()),
            () -> assertFalse(requiredChild(head, "wail_flare_left").isEmpty()),
            () -> assertFalse(requiredChild(head, "wail_flare_right").isEmpty()),
            () -> assertFalse(requiredChild(shoulderYoke, "bodice").isEmpty()),
            () -> assertFalse(requiredChild(rightArm, "right_hand").isEmpty()),
            () -> assertFalse(requiredChild(leftArm, "left_hand").isEmpty()),
            () -> assertFalse(requiredChild(dress, "upper_skirt").isEmpty()),
            () -> assertFalse(requiredChild(dress, "veil_left").isEmpty()),
            () -> assertFalse(requiredChild(dress, "veil_right").isEmpty()),
            () -> assertFalse(requiredChild(dress, "bell_lower_mass").isEmpty()),
            () -> assertTrue(rightArm.x < -3.0F && leftArm.x > 3.0F,
                "separate arms must preserve readable torso-arm negative space")
        );
        assertUvsWithin(root, 128, 128);
        assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true);
        assertEquals(0, ImageIO.read(TEXTURE.toFile()).getRGB(127, 127) >>> 24);
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<BansheeModel.State>"));
        assertTrue(source.contains("extractRenderState(final BansheeEntity entity"));
        assertTrue(source.contains("entity.presentationActivity()"));
        assertFalse(source.contains("entity.bansheeState()"));
        final String entitySource = Files.readString(ENTITY_SOURCE);
        assertTrue(entitySource.contains("DATA_ACTIVITY"));
        assertTrue(entitySource.contains("defineSynchedData"));
        assertTrue(entitySource.contains("entityData.set(DATA_ACTIVITY"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }

    @Test
    void pinsBoundsNeutralMotionWailSilhouettesAndTexture() throws Exception {
        final BansheeModel model = new BansheeModel(BansheeModel.createBodyLayer().bakeRoot());
        assertEquals(new Bounds(-7.5364585F,-8.5F,-4.25F,7.5364585F,26.5F,4.75F), bounds(model.root()));
        assertEquals("ed102274333d52c669416fc1cec9bd1cccdca1a2b2c4500405ea732be82bf8da", geometrySnapshot(model.root()));
        assertEquals("6e046b6566cb63bb8dd0315d039e18b9a1d5e57c4ea7ed6f24572943f03bb686", imageSnapshot(softwareSnapshot(model.root(), Projection.FRONT, 128, 4)));
        assertEquals("377f190dc9eba12cb402da5e6846e2354fcaea292e0b908aa28dee1dff51411c", imageSnapshot(softwareSnapshot(model.root(), Projection.SIDE, 128, 4)));
        model.root().yRot = 0.7853982F;
        assertEquals("a4ff8c402c6b3370496e4e18256d938024c317ffcfd79a7b14c64a0bf1855dae", imageSnapshot(softwareSnapshot(model.root(), Projection.FRONT, 128, 4)));
        model.root().yRot = 0.0F;
        final BansheeModel.State state = new BansheeModel.State();
        state.walkAnimationPos = 2.4F; state.walkAnimationSpeed = 0.72F; state.ageInTicks = 31.0F; state.yRot = 24.0F; state.xRot = -8.0F;
        model.setupAnim(state);
        assertEquals("ddb13cf8b930abcab9b2f7e8677bb987ce527687014aa40906ccba5cbf4b9f48", geometrySnapshot(model.root()));
        state.wailing = true; model.setupAnim(state);
        assertEquals("cc5e5d6095c7cc75cc3aee714676045e79df84e94b534c1ff732238353c2071f", geometrySnapshot(model.root()));
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getWidth());
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getHeight());
        assertEquals("1742076bcb2c86ddcebb5403a933649d400c9d2ae1c032dad88380e533539b2c", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test void writesSoftwareContactSheet() throws Exception {
        final BansheeModel model=new BansheeModel(BansheeModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics();
        g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final BansheeModel.State action=new BansheeModel.State(); action.ageInTicks=31; action.wailing=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/banshee-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile());
    }

}
