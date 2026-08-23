package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.geometrySnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.imageSnapshot;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.requiredChild;
import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class FamiliarCatModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/FamiliarCatModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/familiar_cat.png");

    @Test
    void bakesIndependentGroundedHouseholdCat() throws Exception {
        assertEquals(64, FamiliarCatModel.TEXTURE_WIDTH);
        assertEquals(32, FamiliarCatModel.TEXTURE_HEIGHT);
        assertTrue(AdultFelineModel.class.isAssignableFrom(FamiliarCatModel.class));
        assertTrue(Modifier.isPublic(FamiliarCatModel.State.class.getModifiers()));
        final ModelPart root = FamiliarCatModel.createBodyLayer().bakeRoot();
        assertFalse(requiredChild(root, "head").isEmpty());
        assertFalse(requiredChild(root, "body").isEmpty());
        for (final String leg : List.of("left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg")) {
            assertFalse(requiredChild(root, leg).isEmpty(), leg);
        }
        assertFalse(requiredChild(root, "tail1").isEmpty());
        assertFalse(requiredChild(root, "tail2").isEmpty());
        CreatureModelTestSupport.assertUvsWithin(root, 64, 32);
        final BufferedImage texture = ImageIO.read(TEXTURE.toFile());
        CreatureModelTestSupport.assertOpaqueUvs(root, texture, cube -> true);
        assertEquals(0, texture.getRGB(63, 31) >>> 24);
    }

    @Test
    void groundedBoundsAndThreeViewsArePinned() {
        final ModelPart root = FamiliarCatModel.createBodyLayer().bakeRoot();
        final CreatureModelTestSupport.Bounds bounds = CreatureModelTestSupport.bounds(root);
        assertEquals(27.1872F, bounds.maxY(), 0.001F);
        assertTrue(bounds.maxX() - bounds.minX() > 3.0F);
        assertTrue(bounds.maxZ() - bounds.minZ() > 18.0F);
        assertAll("approved Familiar Cat bake and views",
            () -> assertEquals("83992947c2dd51505d1a1fad4066140743ff2c7362198a302f4ee52018a269f0", geometrySnapshot(root)),
            () -> assertViews(root,
                "0fa1963f94e169ac3d9b1980d02631828863fc426d8b6a8320347273853d30f9",
                "8ee7dbf17758824e06adc35ffb3f138684da4e2e51d3e95bc7e3d040cca57734",
                "264177727ab4f7173b3866a74d182d37979828338277be9d3045f7928868b25a"));
    }

    @Test
    void neutralPatrolAndPouncePosesAreDistinctAndPinned() {
        final FamiliarCatModel neutral = new FamiliarCatModel(FamiliarCatModel.createBodyLayer().bakeRoot());
        neutral.setupAnim(new FamiliarCatModel.State());
        final FamiliarCatModel moving = new FamiliarCatModel(FamiliarCatModel.createBodyLayer().bakeRoot());
        final FamiliarCatModel.State walk = movingState();
        moving.setupAnim(walk);
        final FamiliarCatModel pounce = new FamiliarCatModel(FamiliarCatModel.createBodyLayer().bakeRoot());
        final FamiliarCatModel.State action = movingState();
        action.stalking = true;
        action.airborne = true;
        pounce.setupAnim(action);
        final String neutralHash = geometrySnapshot(neutral.root());
        final String movingHash = geometrySnapshot(moving.root());
        final String actionHash = geometrySnapshot(pounce.root());
        assertNotEquals(neutralHash, movingHash);
        assertNotEquals(movingHash, actionHash);
        assertAll("approved Familiar Cat poses",
            () -> assertEquals("17a59cec5c1ebd8eccf251801ee7cf85e20686048b730f9e9906a053b612eaa5", neutralHash),
            () -> assertEquals("8c8fd56f9dc94ca6dc2e86e18fda0eaf8379dec032681a96699b710ed24016f0", movingHash),
            () -> assertEquals("c8ffd224c1b51615fd64f1a78b67643a1fe2b0be1e7b62cb9fd5da40642cb2fd", actionHash));
    }

    @Test
    void sourceOwnsTheCatAndAtlasIsPinned() throws Exception {
        final String source = Files.readString(SOURCE);
        for (final String forbidden : List.of("ArcaneCreatureModel", "CreatureModelProfile", "SpectralFamiliarModel", "OwlModel", "ToadModel", "HexBatModel", "ParasyticLouseModel", "ModelHelper", "GeometryHelper", "FamilyAnimator")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("extends AdultFelineModel<FamiliarCatModel.State>"));
        assertTrue(source.contains("extractRenderState(final ArcaneMob entity"));
        assertEquals("dd4fcf3b4c5f2d7a4ba80e649468a9ca1ca5f55f65b351e9253255b2368dbaba", textureHash());
    }

    @Test
    void writesSoftwareContactSheet() throws Exception {
        writeSheet(List.of(
            view(FamiliarCatModel.createBodyLayer().bakeRoot(), CreatureModelTestSupport.Projection.FRONT),
            view(FamiliarCatModel.createBodyLayer().bakeRoot(), CreatureModelTestSupport.Projection.SIDE),
            threeQuarter(), posed(false, false, CreatureModelTestSupport.Projection.FRONT),
            posed(true, false, CreatureModelTestSupport.Projection.FRONT),
            posed(true, true, CreatureModelTestSupport.Projection.SIDE)
        ), Path.of("build/reports/visual-audit/creatures/familiar_cat-software-contact-sheet.png"));
    }

    private static FamiliarCatModel.State movingState() {
        final FamiliarCatModel.State state = new FamiliarCatModel.State();
        state.yRot = 24.0F;
        state.xRot = -8.0F;
        state.walkAnimationPos = 2.6F;
        state.walkAnimationSpeed = 0.8F;
        state.ageInTicks = 31.5F;
        return state;
    }

    private static void assertViews(final ModelPart root, final String front, final String side, final String threeQuarter) {
        final ModelPart angled = FamiliarCatModel.createBodyLayer().bakeRoot();
        angled.yRot = 0.7853982F;
        assertAll("approved Familiar Cat views",
            () -> assertEquals(front, imageSnapshot(view(root, CreatureModelTestSupport.Projection.FRONT))),
            () -> assertEquals(side, imageSnapshot(view(root, CreatureModelTestSupport.Projection.SIDE))),
            () -> assertEquals(threeQuarter, imageSnapshot(view(angled, CreatureModelTestSupport.Projection.FRONT))));
    }

    private static BufferedImage threeQuarter() {
        final ModelPart root = FamiliarCatModel.createBodyLayer().bakeRoot();
        root.yRot = 0.7853982F;
        return view(root, CreatureModelTestSupport.Projection.FRONT);
    }

    private static BufferedImage posed(
        final boolean stalking,
        final boolean airborne,
        final CreatureModelTestSupport.Projection projection
    ) {
        final FamiliarCatModel model = new FamiliarCatModel(FamiliarCatModel.createBodyLayer().bakeRoot());
        final FamiliarCatModel.State state = movingState();
        state.stalking = stalking;
        state.airborne = airborne;
        model.setupAnim(state);
        return view(model.root(), projection);
    }

    private static BufferedImage view(final ModelPart root, final CreatureModelTestSupport.Projection projection) {
        return softwareSnapshot(root, projection, 192, 10);
    }

    private static String textureHash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE)));
    }

    private static void writeSheet(final List<BufferedImage> views, final Path output) throws Exception {
        final BufferedImage sheet = new BufferedImage(576, 384, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(34, 27, 26));
            graphics.fillRect(0, 0, 576, 384);
            for (int index = 0; index < views.size(); index++) graphics.drawImage(views.get(index), index % 3 * 192, index / 3 * 192, null);
        } finally {
            graphics.dispose();
        }
        Files.createDirectories(output.getParent());
        ImageIO.write(sheet, "PNG", output.toFile());
    }
}
