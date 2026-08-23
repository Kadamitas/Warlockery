package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*; import java.security.MessageDigest; import java.util.HexFormat; import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import net.minecraft.client.model.geom.ModelPart; import org.junit.jupiter.api.Test;

final class LostSoulModelTest {
    private static final Path SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/client/model/LostSoulModel.java");
    private static final Path ENTITY_SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/entity/LostSoulEntity.java");
    private static final Path TEXTURE=Path.of("src/main/resources/assets/warlockery/textures/entity/lost_soul.png");
    @Test void ownsVulnerableWispMemoryMotesAtlasAndSyncedExtraction() throws Exception {
        assertEquals(64,LostSoulModel.TEXTURE_WIDTH); assertEquals(64,LostSoulModel.TEXTURE_HEIGHT); final ModelPart root=LostSoulModel.createBodyLayer().bakeRoot(); final ModelPart torso=requiredChild(root,"hunched_torso");
        final ModelPart memoryCluster=requiredChild(torso,"memory_mote_cluster");
        assertAll(() -> assertFalse(requiredChild(root,"drooping_head").isEmpty()),() -> assertFalse(requiredChild(torso,"heart_mote").isEmpty()),
            () -> assertFalse(memoryCluster.isEmpty()),() -> assertFalse(requiredChild(memoryCluster,"memory_mote_rose").isEmpty()),
            () -> assertFalse(requiredChild(memoryCluster,"memory_mote_moss").isEmpty()),() -> assertFalse(requiredChild(memoryCluster,"memory_mote_blue").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"right_sheltering_arm"),"right_sheltering_forearm").isEmpty()),
            () -> assertFalse(requiredChild(requiredChild(root,"left_sheltering_arm"),"left_sheltering_forearm").isEmpty()),
            () -> assertFalse(requiredChild(root,"soul_bead_near").isEmpty()),() -> assertFalse(requiredChild(root,"soul_bead_middle").isEmpty()),() -> assertFalse(requiredChild(root,"soul_bead_far").isEmpty()));
        final ModelPart tail = requiredChild(root,"ribbon_tail");
        final ModelPart middle = requiredChild(tail,"ribbon_tail_middle");
        final ModelPart tip = requiredChild(middle,"ribbon_tail_tip");
        assertFalse(requiredChild(tip,"tail_curl").isEmpty());
        assertUvsWithin(root,64,64); assertOpaqueUvs(root,ImageIO.read(TEXTURE.toFile()),cube->!cube.path().contains("memory_mote_cluster")); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(63,63)>>>24); final String source=Files.readString(SOURCE);
        assertTrue(source.contains("extends EntityModel<LostSoulModel.State>")); assertTrue(source.contains("extractRenderState(final LostSoulEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()")); assertFalse(source.contains("entity.lostSoulState()"));
        final String entitySource=Files.readString(ENTITY_SOURCE); assertTrue(entitySource.contains("DATA_PRESENTATION_PHASE")); assertTrue(entitySource.contains("defineSynchedData")); assertTrue(entitySource.contains("syncPresentation(state.phase())")); assertTrue(entitySource.contains("entityData.set(DATA_PRESENTATION_PHASE"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }
    @Test void pinsBoundsNeutralMotionShelterSilhouettesAndTexture() throws Exception {
        final LostSoulModel model=new LostSoulModel(LostSoulModel.createBodyLayer().bakeRoot()); assertEquals(new Bounds(-5.869985F,3.5275364F,-7.710188F,11F,25.59898F,3.5337868F),bounds(model.root())); assertEquals("d68921d48a7f54b51cb99fae91bc9b1e965df1e2b5db1b1e6dab02a28775905e",geometrySnapshot(model.root()));
        assertEquals("57172c565028e0ed1eeaa006f4ab2c3279da15071a52d13f39acdf6dc40937c5",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); assertEquals("8b583c0e112aa8afd0c8879675c74cab2a73e23ef52b19f6fa93c1b366e08374",imageSnapshot(softwareSnapshot(model.root(),Projection.SIDE,128,4)));
        model.root().yRot=.7853982F; assertEquals("0b901c8fe3f0abdafcc278ae61c4b7fab035b3e5880e2933a0e83471b5d64cae",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); model.root().yRot=0;
        final LostSoulModel.State state=new LostSoulModel.State(); state.walkAnimationPos=1.9F; state.walkAnimationSpeed=.5F; state.ageInTicks=41F; state.yRot=-15F; state.xRot=9F; model.setupAnim(state);
        assertEquals("24ff4c8c82777aa8d174d59da4540ee44abf81959405eb3b4dc3dcfa917dda1b",geometrySnapshot(model.root())); state.sheltering=true; model.setupAnim(state); assertEquals("ede83d71b1595e15a51035805d7d5f7c61ba460d79e4045527e034c317e957d0",geometrySnapshot(model.root()));
        assertEquals(64,ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(64,ImageIO.read(TEXTURE.toFile()).getHeight()); assertEquals("1121763ff01e1853e38d58581ca7399b031dce41cccaf304f9b44c52356be283",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test
    void neutralSoulStaysNarrowLongAndSheltersItsMemoryCluster() {
        final ModelPart root = new LostSoulModel(LostSoulModel.createBodyLayer().bakeRoot()).root();
        final double frontAspect = largestComponentAspect(root, Projection.FRONT);
        final double sideAspect = largestComponentAspect(root, Projection.SIDE);
        final ModelPart head = requiredChild(root, "drooping_head");
        final ModelPart torso = requiredChild(root, "hunched_torso");
        assertAll(
            () -> assertTrue(frontAspect >= 0.44 && frontAspect <= 0.54,
                "the hood, small torso, embrace, and long ribbon tail must form a vulnerable wisp"),
            () -> assertTrue(sideAspect >= 0.44 && sideAspect <= 0.54,
                "the same narrow soul shape must survive in profile"),
            () -> assertTrue(bounds(head).maxX() - bounds(head).minX()
                    > bounds(torso).maxX() - bounds(torso).minX(),
                "the drooping sheltering head must overhang the hunched torso")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final LostSoulModel model=new LostSoulModel(LostSoulModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final LostSoulModel.State action=new LostSoulModel.State(); action.ageInTicks=31; action.sheltering=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/lost_soul-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }

    private static double largestComponentAspect(final ModelPart root, final Projection projection) {
        final BufferedImage image = softwareSnapshot(root, projection, 256, 8);
        final boolean[] visited = new boolean[image.getWidth() * image.getHeight()];
        int largestArea = 0;
        double largestAspect = 0.0;
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
            if (area > largestArea) {
                largestArea = area;
                largestAspect = (maximumX - minimumX + 1.0) / (maximumY - minimumY + 1.0);
            }
        }
        assertTrue(largestArea > 0, "software silhouette must contain geometry");
        return largestAspect;
    }
}
