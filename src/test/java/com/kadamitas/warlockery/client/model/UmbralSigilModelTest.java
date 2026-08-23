package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class UmbralSigilModelTest {
    private static final Path SOURCE = Path.of("src/main/java/com/kadamitas/warlockery/client/model/UmbralSigilModel.java");
    private static final Path TEXTURE = Path.of("src/main/resources/assets/warlockery/textures/entity/umbral_sigil.png");

    @Test
    void ownsBrokenRuneRingHierarchyAtlasAndExtraction() throws Exception {
        assertEquals(128, UmbralSigilModel.TEXTURE_WIDTH); assertEquals(128, UmbralSigilModel.TEXTURE_HEIGHT);
        final ModelPart root = UmbralSigilModel.createBodyLayer().bakeRoot();
        assertAll(
            () -> assertFalse(requiredChild(root, "faceted_core").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root, "inner_ring"), "inner_arc_north").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root, "outer_ring"), "outer_rune_upper_left").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root, "outer_ring"), "outer_rune_upper_center").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root, "outer_ring"), "outer_rune_upper_right").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root, "outer_ring"), "outer_rune_lower_left").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root, "outer_ring"), "outer_rune_lower_center").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root, "outer_ring"), "outer_rune_lower_right").isEmpty()),
            () -> assertFalse(requiredChild(root, "left_prong").isEmpty()),
            () -> assertFalse(requiredChild(root, "center_prong").isEmpty()),
            () -> assertFalse(requiredChild(root, "right_prong").isEmpty())
        );
        assertUvsWithin(root, 128, 128); assertOpaqueUvs(root, ImageIO.read(TEXTURE.toFile()), cube -> true); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(127,127)>>>24);
        final String source = Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<UmbralSigilModel.State>"));
        assertTrue(source.contains("extractRenderState(final UmbralSigilEntity entity"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }

    @Test
    void pinsBoundsNeutralMotionSealSilhouettesAndTexture() throws Exception {
        final UmbralSigilModel model = new UmbralSigilModel(UmbralSigilModel.createBodyLayer().bakeRoot());
        assertEquals(new Bounds(-14.699962F,-5.066478F,-10F,14.699962F,35F,10F), bounds(model.root())); assertEquals("864461f4d284b5f762dec21b77b58ef8f5d872ffa79e9ec329615499b54bddf2", geometrySnapshot(model.root()));
        assertEquals("c0803f3552c1bcff4b8f689d68d28a19cfc73756e804ea750ce41347e4e51cd6", imageSnapshot(softwareSnapshot(model.root(), Projection.FRONT, 128, 4)));
        assertEquals("bc76344d7d1d3c72c258aed26b417edd552d99ac4883e5bffc4df6d6a74d4359", imageSnapshot(softwareSnapshot(model.root(), Projection.SIDE, 128, 4)));
        model.root().yRot = 0.7853982F; assertEquals("b3a0451a375b7eed8acdc83835fce8c22288aaf99ff4991de65ce67f279f3c67", imageSnapshot(softwareSnapshot(model.root(), Projection.FRONT, 128, 4))); model.root().yRot = 0;
        final UmbralSigilModel.State state = new UmbralSigilModel.State();
        state.walkAnimationPos=2.1F; state.walkAnimationSpeed=.66F; state.ageInTicks=27F; state.yRot=-18F; state.xRot=6F; model.setupAnim(state);
        assertEquals("3f7f4f4e578a48f67b3070d7c9010ac6ad8eb476b125c00e67e6e405df77ad3f", geometrySnapshot(model.root())); state.sealing=true; model.setupAnim(state); assertEquals("4ec379a3dae232171650f95d4ae5ebd8aa11cf42d7559d929037e9042ff3808c", geometrySnapshot(model.root()));
        assertEquals(128, ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(128, ImageIO.read(TEXTURE.toFile()).getHeight());
        assertEquals("06319194101b835f1ce94c5904f2eaa50c00a0cd3672c33e87fbe69a0c1566bf", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test
    void neutralFragmentsStayPortraitAndCarryRealProfileDepth() {
        final ModelPart root = new UmbralSigilModel(
            UmbralSigilModel.createBodyLayer().bakeRoot()
        ).root();
        final SilhouetteStats front = silhouetteStats(root, Projection.FRONT);
        final SilhouetteStats side = silhouetteStats(root, Projection.SIDE);
        assertAll(
            () -> assertTrue(front.largestAspect() >= 0.56 && front.largestAspect() <= 0.67,
                "dominant front rune fragment must track the portrait concept"),
            () -> assertTrue(side.largestAspect() >= 0.50 && side.largestAspect() <= 0.62,
                "runes and crystal must retain authored depth in profile"),
            () -> assertTrue(front.substantialComponents() >= 5,
                "broken rings, core, and hanging prongs must preserve visible air gaps")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final UmbralSigilModel model=new UmbralSigilModel(UmbralSigilModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final UmbralSigilModel.State action=new UmbralSigilModel.State(); action.ageInTicks=31; action.sealing=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/umbral_sigil-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }

    private static SilhouetteStats silhouetteStats(final ModelPart root, final Projection projection) {
        final BufferedImage image = softwareSnapshot(root, projection, 256, 8);
        final boolean[] visited = new boolean[image.getWidth() * image.getHeight()];
        final List<Component> components = new ArrayList<>();
        for (int start = 0; start < visited.length; start++) {
            if (visited[start] || (image.getRGB(start % image.getWidth(), start / image.getWidth()) >>> 24) == 0) {
                continue;
            }
            final ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(start);
            visited[start] = true;
            int area = 0;
            int minimumX = image.getWidth();
            int minimumY = image.getHeight();
            int maximumX = -1;
            int maximumY = -1;
            while (!pending.isEmpty()) {
                final int index = pending.removeFirst();
                final int x = index % image.getWidth();
                final int y = index / image.getWidth();
                area++;
                minimumX = Math.min(minimumX, x);
                minimumY = Math.min(minimumY, y);
                maximumX = Math.max(maximumX, x);
                maximumY = Math.max(maximumY, y);
                for (int deltaY = -1; deltaY <= 1; deltaY++) {
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        final int nextX = x + deltaX;
                        final int nextY = y + deltaY;
                        if (nextX < 0 || nextY < 0 || nextX >= image.getWidth() || nextY >= image.getHeight()) {
                            continue;
                        }
                        final int next = nextY * image.getWidth() + nextX;
                        if (!visited[next] && (image.getRGB(nextX, nextY) >>> 24) != 0) {
                            visited[next] = true;
                            pending.addLast(next);
                        }
                    }
                }
            }
            components.add(new Component(area, minimumX, minimumY, maximumX, maximumY));
        }
        final Component largest = components.stream()
            .max(java.util.Comparator.comparingInt(Component::area))
            .orElseThrow();
        final long substantial = components.stream()
            .filter(component -> component.area() >= largest.area() * 0.04)
            .count();
        return new SilhouetteStats(largest.aspect(), substantial);
    }

    private record Component(int area, int minimumX, int minimumY, int maximumX, int maximumY) {
        double aspect() {
            return (maximumX - minimumX + 1.0) / (maximumY - minimumY + 1.0);
        }
    }

    private record SilhouetteStats(double largestAspect, long substantialComponents) {
    }
}
