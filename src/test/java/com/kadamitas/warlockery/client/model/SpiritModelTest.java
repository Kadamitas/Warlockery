package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*; import java.security.MessageDigest; import java.util.HexFormat; import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart; import org.junit.jupiter.api.Test;

final class SpiritModelTest {
    private static final Path SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/client/model/SpiritModel.java");
    private static final Path ENTITY_SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/entity/SpiritEntity.java");
    private static final Path TEXTURE=Path.of("src/main/resources/assets/warlockery/textures/entity/spirit.png");
    @Test void ownsProtectiveCoherentSoulHierarchyAtlasAndExtraction() throws Exception {
        assertEquals(128,SpiritModel.TEXTURE_WIDTH); assertEquals(128,SpiritModel.TEXTURE_HEIGHT); final ModelPart root=SpiritModel.createBodyLayer().bakeRoot();
        final ModelPart torso=requiredChild(root,"solid_torso"); final ModelPart sparkCore=requiredChild(torso,"golden_spirit_spark_core");
        assertAll(() -> assertFalse(requiredChild(root,"mask_head").isEmpty()),() -> assertFalse(torso.isEmpty()),
            () -> assertFalse(sparkCore.isEmpty()),() -> assertFalse(requiredChild(sparkCore,"spark_crown").isEmpty()),
            () -> assertFalse(requiredChild(sparkCore,"spark_trail").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"halo_left"),"halo_left_upper").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"halo_left"),"halo_left_lower").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"halo_right"),"halo_right_upper").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"halo_right"),"halo_right_lower").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(requiredChild(root,"right_guard_arm"),"right_guard_forearm"),"right_ward_palm").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(requiredChild(root,"left_guard_arm"),"left_guard_forearm"),"left_ward_palm").isEmpty()),
            () -> assertFalse(requiredChild(root,"vapor_tail_left").isEmpty()),() -> assertFalse(requiredChild(root,"vapor_tail_right").isEmpty()));
        assertUvsWithin(root,128,128); assertOpaqueUvs(root,ImageIO.read(TEXTURE.toFile()),
            cube -> !cube.path().contains("golden_spirit_spark_core")); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(127,127)>>>24); final String source=Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<SpiritModel.State>")); assertTrue(source.contains("extractRenderState(final SpiritEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()")); assertFalse(source.contains("entity.spiritState()"));
        final String entitySource=Files.readString(ENTITY_SOURCE); assertTrue(entitySource.contains("DATA_PRESENTATION_PHASE")); assertTrue(entitySource.contains("defineSynchedData")); assertTrue(entitySource.contains("syncPresentation(state.phase())")); assertTrue(entitySource.contains("entityData.set(DATA_PRESENTATION_PHASE"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }
    @Test void pinsBoundsNeutralMotionShieldSilhouettesAndTexture() throws Exception {
        final SpiritModel model=new SpiritModel(SpiritModel.createBodyLayer().bakeRoot()); assertEquals(new Bounds(-14.959637F,3F,-6.224773F,14.959637F,26.078669F,3F),bounds(model.root())); assertEquals("2a675bfac08736c35dbcf0a40b0fdbd4debd463db97def1162b2e9fb4e03022e",geometrySnapshot(model.root()));
        assertEquals("d0a80328182fa428878d30c53ca4886eb576f7a49c6d40711ba1cc0947f4e1cb",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); assertEquals("a49ef45ad8641a1959f94e91ce575d4812b22565215fe5e59ae75392638f42f4",imageSnapshot(softwareSnapshot(model.root(),Projection.SIDE,128,4)));
        model.root().yRot=.7853982F; assertEquals("43de8296a67b50e7ad8fa09831098c0975e98e8d49536407ab8d8f8d41801dee",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); model.root().yRot=0;
        final SpiritModel.State state=new SpiritModel.State(); state.walkAnimationPos=2.2F; state.walkAnimationSpeed=.6F; state.ageInTicks=29F; state.yRot=20F; state.xRot=-6F; model.setupAnim(state);
        assertEquals("e7fe37a7375e48f2754d274a662b5d86ae3ea65953e9ee38db005b3c16b5ea82",geometrySnapshot(model.root())); state.shielding=true; model.setupAnim(state); assertEquals("228c878e6cc941f5e1bc4086519294285629279485ee8b046207205467780717",geometrySnapshot(model.root()));
        assertEquals(128,ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(128,ImageIO.read(TEXTURE.toFile()).getHeight()); assertEquals("0848282954daa0afd1e840b0dd9a3a048600e3587e1a2d82bc3b6c1aaf0261f3",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test
    void neutralGuardianKeepsAContinuousSoulSpineAndFourLateralGaps() {
        final ModelPart root = new SpiritModel(SpiritModel.createBodyLayer().bakeRoot()).root();
        final SilhouetteStats front = silhouetteStats(root, Projection.FRONT);
        final SilhouetteStats side = silhouetteStats(root, Projection.SIDE);
        assertAll(
            () -> assertTrue(front.largestAspect() >= 0.42 && front.largestAspect() <= 0.54,
                "mask, torso, spark, and twin tails must form the narrow dominant soul spine"),
            () -> assertTrue(front.substantialComponents() >= 5,
                "paired crescents and ward palms must remain detached from the central spirit"),
            () -> assertTrue(side.largestAspect() >= 0.36 && side.largestAspect() <= 0.44,
                "front-only separation work must protect the already-passing side silhouette")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final SpiritModel model=new SpiritModel(SpiritModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final SpiritModel.State action=new SpiritModel.State(); action.ageInTicks=31; action.shielding=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/spirit-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }

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
