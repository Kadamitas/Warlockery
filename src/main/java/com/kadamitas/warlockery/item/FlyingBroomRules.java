package com.kadamitas.warlockery.item;

public final class FlyingBroomRules {
    public static final float NORMAL_SPEED = 0.05F;
    public static final float SOARING_SPEED = 0.075F;

    private FlyingBroomRules() {
    }

    public static FlightDecision decide(
        final boolean activated,
        final boolean holdingBroom,
        final boolean creativeOrSpectator,
        final boolean soaring
    ) {
        if (!activated || !holdingBroom) {
            return new FlightDecision(false, creativeOrSpectator, creativeOrSpectator, NORMAL_SPEED);
        }
        return new FlightDecision(true, true, true, soaring ? SOARING_SPEED : NORMAL_SPEED);
    }

    public record FlightDecision(boolean active, boolean mayFly, boolean keepFlying, float speed) {
        public FlightDecision {
            if (speed <= 0.0F || speed > 1.0F) {
                throw new IllegalArgumentException("Flight speed must be greater than zero and at most one");
            }
        }
    }
}
