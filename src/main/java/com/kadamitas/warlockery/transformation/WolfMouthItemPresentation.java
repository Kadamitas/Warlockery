package com.kadamitas.warlockery.transformation;

import java.util.Objects;
import java.util.Optional;

public record WolfMouthItemPresentation(
    Mode mode,
    Optional<Hand> mouthHand,
    Optional<Hand> suppressedVanillaHand,
    MouthPose pose
) {
    private static final MouthPose MOUTH_POSE = new MouthPose(
        0.0F,
        0.27F,
        -0.5F,
        90.0F,
        0.0F,
        90.0F,
        0.75F,
        DisplayContext.GROUND
    );

    public WolfMouthItemPresentation {
        Objects.requireNonNull(mode, "mode");
        mouthHand = Objects.requireNonNull(mouthHand, "mouthHand");
        suppressedVanillaHand = Objects.requireNonNull(suppressedVanillaHand, "suppressedVanillaHand");
        Objects.requireNonNull(pose, "pose");
        final boolean mouthMode = mode == Mode.MOUTH;
        if (mouthMode != mouthHand.isPresent()
            || mouthMode != suppressedVanillaHand.isPresent()
            || mouthMode && !mouthHand.equals(suppressedVanillaHand)) {
            throw new IllegalArgumentException("Mouth presentation must select and suppress the same hand");
        }
    }

    public static WolfMouthItemPresentation resolve(
        final SupernaturalForm form,
        final WerewolfShape shape,
        final boolean mainHandEmpty,
        final boolean offHandEmpty
    ) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(shape, "shape");
        if (form != SupernaturalForm.WEREWOLF || shape != WerewolfShape.WOLF) {
            return inactive(Mode.VANILLA);
        }
        if (!mainHandEmpty) {
            return mouth(Hand.MAIN);
        }
        if (!offHandEmpty) {
            return mouth(Hand.OFFHAND);
        }
        return inactive(Mode.NONE);
    }

    public boolean presentsAtMouth() {
        return mode == Mode.MOUTH;
    }

    public boolean usesVanillaHandPresentation() {
        return mode == Mode.VANILLA;
    }

    private static WolfMouthItemPresentation mouth(final Hand hand) {
        final Optional<Hand> selected = Optional.of(hand);
        return new WolfMouthItemPresentation(Mode.MOUTH, selected, selected, MOUTH_POSE);
    }

    private static WolfMouthItemPresentation inactive(final Mode mode) {
        return new WolfMouthItemPresentation(mode, Optional.empty(), Optional.empty(), MOUTH_POSE);
    }

    public enum Mode {
        VANILLA,
        MOUTH,
        NONE
    }

    public enum Hand {
        MAIN,
        OFFHAND
    }

    public enum DisplayContext {
        GROUND
    }

    public record MouthPose(
        float translateX,
        float translateY,
        float translateZ,
        float rotateXDegrees,
        float rotateYDegrees,
        float rotateZDegrees,
        float scale,
        DisplayContext displayContext
    ) {
        public MouthPose {
            Objects.requireNonNull(displayContext, "displayContext");
            if (!Float.isFinite(translateX)
                || !Float.isFinite(translateY)
                || !Float.isFinite(translateZ)
                || !Float.isFinite(rotateXDegrees)
                || !Float.isFinite(rotateYDegrees)
                || !Float.isFinite(rotateZDegrees)
                || !Float.isFinite(scale)
                || scale <= 0.0F) {
                throw new IllegalArgumentException("Mouth pose values must be finite with a positive scale");
            }
        }
    }
}
