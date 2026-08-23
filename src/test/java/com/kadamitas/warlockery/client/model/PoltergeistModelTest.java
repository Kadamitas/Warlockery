package com.kadamitas.warlockery.client.model;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*; import java.security.MessageDigest; import java.util.HexFormat; import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart; import org.junit.jupiter.api.Test;

final class PoltergeistModelTest {
    private static final Path SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/client/model/PoltergeistModel.java");
    private static final Path ENTITY_SOURCE=Path.of("src/main/java/com/kadamitas/warlockery/entity/PoltergeistEntity.java");
    private static final Path TEXTURE=Path.of("src/main/resources/assets/warlockery/textures/entity/poltergeist.png");
    @Test void ownsEmbodiedApparitionForceHandsPropsAtlasAndSyncedExtraction() throws Exception {
        assertEquals(128,PoltergeistModel.TEXTURE_WIDTH); assertEquals(128,PoltergeistModel.TEXTURE_HEIGHT);
        final ModelPart root=PoltergeistModel.createBodyLayer().bakeRoot(); final ModelPart orbit=requiredChild(root,"object_orbit");
        final ModelPart apparitionHead=requiredChild(root,"apparition_head");
        assertAll(() -> assertFalse(requiredChild(apparitionHead,"hollow_face").isEmpty()),
            () -> assertFalse(requiredChild(root,"shoulder_arch").isEmpty()),
            () -> assertFalse(requiredChild(root,"ectoplasm_torso").isEmpty()),
            () -> assertFalse(requiredChild(root,"inner_right_spectral_arm").isEmpty()),
            () -> assertFalse(requiredChild(root,"inner_left_spectral_arm").isEmpty()),
            () -> assertFalse(requiredChild(root,"left_force_hand").isEmpty()),
            () -> assertFalse(requiredChild(root,"right_force_hand").isEmpty()),() -> assertFalse(requiredChild(root,"spiral_tail").isEmpty()),
            () -> assertFalse(requiredChild(orbit,"chair").isEmpty()),() -> assertFalse(requiredChild(orbit,"book").isEmpty()),
            () -> assertFalse(requiredChild(orbit,"bottle").isEmpty()),() -> assertFalse(requiredChild(orbit,"pebble").isEmpty()));
        for (final String side : List.of("left", "right")) {
            final ModelPart hand = requiredChild(root, side + "_force_hand");
            for (final String digit : List.of("thumb", "index", "middle", "ring", "little")) {
                assertFalse(requiredChild(hand, side + "_force_" + digit).isEmpty(), side + " " + digit);
            }
        }
        assertUvsWithin(root,128,128); assertOpaqueUvs(root,ImageIO.read(TEXTURE.toFile()),
            cube -> !cube.path().equals("/spiral_tail")
                && !cube.path().equals("/apparition_head")
                && !cube.path().contains("spectral_arm")
                && !cube.path().startsWith("/object_orbit/")
                && !cube.path().contains("force_hand")
                && !cube.path().equals("/ectoplasm_torso")); assertEquals(0,ImageIO.read(TEXTURE.toFile()).getRGB(127,127)>>>24);
        final String source=Files.readString(SOURCE); assertTrue(source.contains("extends EntityModel<PoltergeistModel.State>"));
        assertTrue(source.contains("extractRenderState(final PoltergeistEntity entity"));
        assertTrue(source.contains("entity.presentationPhase()")); assertFalse(source.contains("entity.poltergeistState()"));
        final String entitySource=Files.readString(ENTITY_SOURCE); assertTrue(entitySource.contains("DATA_PRESENTATION_PHASE")); assertTrue(entitySource.contains("defineSynchedData")); assertTrue(entitySource.contains("syncPresentation(state.phase())")); assertTrue(entitySource.contains("entityData.set(DATA_PRESENTATION_PHASE"));
        assertFalse(source.matches("(?s).*(ArcaneCreatureModel|CreatureModelProfile|AnimationHelper|GeometryHelper|ModelHelper).*"));
    }
    @Test void pinsBoundsNeutralMotionFlingSilhouettesAndTexture() throws Exception {
        final PoltergeistModel model=new PoltergeistModel(PoltergeistModel.createBodyLayer().bakeRoot());
        assertEquals(new Bounds(-17.667013F,.8194032F,-4.5614476F,17.667013F,23.963757F,3.5F),bounds(model.root())); assertEquals("72f63b43a9916f5f8debfb24bc04d93d2dd0c4815b94f5e12e13bf8239060bdf",geometrySnapshot(model.root()));
        assertEquals("e3aeebf86df35cb43a1a9493c1e02c4c3b99035e8a942e66dc18f0d3eec8596b",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); assertEquals("117b9e8e4d59c8515f32069e03109737574103496acadf23080d0d59fe5e8a1b",imageSnapshot(softwareSnapshot(model.root(),Projection.SIDE,128,4)));
        model.root().yRot=.7853982F; assertEquals("4ec0c0be4518167bc65accaa8cc6000d133f72bccc1c8717af767b53a46f3ec4",imageSnapshot(softwareSnapshot(model.root(),Projection.FRONT,128,4))); model.root().yRot=0;
        final PoltergeistModel.State state=new PoltergeistModel.State(); state.walkAnimationPos=2.3F; state.walkAnimationSpeed=.7F; state.ageInTicks=36F; state.yRot=17F; state.xRot=-4F; model.setupAnim(state);
        assertEquals("a1ef4d1df87da887cc07ae3d60db1539ecf201b19c142fb9d22322899d537cde",geometrySnapshot(model.root())); state.flinging=true; model.setupAnim(state); assertEquals("d4307fa71919a454b30ef712cbdf4560a22a748d98a1e1f93f641c0c88e4ffd0",geometrySnapshot(model.root()));
        assertEquals(128,ImageIO.read(TEXTURE.toFile()).getWidth()); assertEquals(128,ImageIO.read(TEXTURE.toFile()).getHeight()); assertEquals("5108456cb92cc9a95c556ad99213384ddedf5204c6632ea42016704a3246ed74",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEXTURE))));
    }

    @Test
    void neutralApparitionRemainsDominantWhileHandsAndPropsStayDetached() {
        final ModelPart root = new PoltergeistModel(
            PoltergeistModel.createBodyLayer().bakeRoot()
        ).root();
        final SilhouetteStats front = silhouetteStats(root, Projection.FRONT);
        final SilhouetteStats side = silhouetteStats(root, Projection.SIDE);
        assertAll(
            () -> assertTrue(front.largestAspect() >= 0.62 && front.largestAspect() <= 0.76,
                "the tall central apparition must remain the dominant front component"),
            () -> assertTrue(front.substantialComponents() >= 3,
                "the two force hands must retain negative space from the apparition and props"),
            () -> assertTrue(side.largestAspect() >= 0.27 && side.largestAspect() <= 0.36,
                "front-only repairs must protect the already-passing narrow profile")
        );
    }

    @Test void writesSoftwareContactSheet() throws Exception { final PoltergeistModel model=new PoltergeistModel(PoltergeistModel.createBodyLayer().bakeRoot()); final java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(384,256,java.awt.image.BufferedImage.TYPE_INT_ARGB); final java.awt.Graphics2D g=sheet.createGraphics(); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,0,null); g.drawImage(softwareSnapshot(model.root(),Projection.SIDE,128,4),128,0,null); model.root().yRot=.7853982F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,0,null); model.root().yRot=3.1415927F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),0,128,null); model.root().yRot=-1.5707964F; g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),128,128,null); final PoltergeistModel.State action=new PoltergeistModel.State(); action.ageInTicks=31; action.flinging=true; model.setupAnim(action); g.drawImage(softwareSnapshot(model.root(),Projection.FRONT,128,4),256,128,null); g.dispose(); final Path out=Path.of("build/reports/visual-audit/creatures/poltergeist-software-contact-sheet.png"); Files.createDirectories(out.getParent()); ImageIO.write(sheet,"PNG",out.toFile()); }

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
