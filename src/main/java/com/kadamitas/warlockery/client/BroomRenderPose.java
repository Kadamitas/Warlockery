package com.kadamitas.warlockery.client;

import net.minecraft.util.Mth;

public record BroomRenderPose(
    float yawDegrees,
    float pitchDegrees,
    float rollDegrees,
    float bob,
    float scale
) {
    public static BroomRenderPose resolve(
        final float riderYaw,
        final float riderPitch,
        final float yawDelta,
        final float speed,
        final boolean gliding,
        final float age
    ) {
        final float boundedSpeed = Mth.clamp(speed, 0.0F, 1.0F);
        final float bankResponse = gliding ? 0.45F : 0.9F;
        final float bank = Mth.clamp(-Mth.wrapDegrees(yawDelta) * bankResponse, -20.0F, 20.0F);
        final float vibration = Mth.sin(age * (gliding ? 0.22F : 0.62F)) * boundedSpeed * 1.2F;
        final float bobAmplitude = (gliding ? 0.012F : 0.02F) + boundedSpeed * (gliding ? 0.012F : 0.025F);
        return new BroomRenderPose(
            180.0F - riderYaw,
            Mth.clamp(riderPitch * (gliding ? 0.62F : 0.78F), -55.0F, 55.0F),
            bank + vibration,
            Mth.sin(age * (gliding ? 0.16F : 0.3F)) * bobAmplitude,
            1.55F
        );
    }
}
