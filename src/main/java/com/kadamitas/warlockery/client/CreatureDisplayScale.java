package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Converts every dedicated rig to the approved player-relative visual height. */
final class CreatureDisplayScale {
    private static final float PLAYER_HEIGHT_BLOCKS = 1.8F;
    private static final float PLAYER_MODEL_HEIGHT = 32.0F;

    private CreatureDisplayScale() {
    }

    static float factor(final String id, final ModelPart root) {
        final CreatureVisualProfile profile = CreatureVisualProfile.forKind(ModEntities.kindFor(id));
        final float targetHeight = profile.height() / PLAYER_HEIGHT_BLOCKS * PLAYER_MODEL_HEIGHT;
        final float rawHeight = modelHeight(root);
        if (!Float.isFinite(rawHeight) || rawHeight <= 0.0F) {
            throw new IllegalStateException("Dedicated creature has no measurable geometry: " + id);
        }
        return targetHeight / rawHeight;
    }

    private static float modelHeight(final ModelPart root) {
        final float[] y = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            for (final ModelPart.Polygon polygon : cube.polygons) {
                for (final ModelPart.Vertex vertex : polygon.vertices()) {
                    final Vector3f transformed = pose.pose().transformPosition(
                        vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()
                    ).mul(16.0F);
                    y[0] = Math.min(y[0], transformed.y());
                    y[1] = Math.max(y[1], transformed.y());
                }
            }
        });
        return y[1] - y[0];
    }
}
