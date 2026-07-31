package com.kadamitas.warlockery.item;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FlyingBroomRules {
    public static final double THRUST = 0.19D;
    public static final double MAX_SPEED = 1.65D;
    public static final double NORMAL_TORQUE = 0.08D;
    public static final double SOARING_TORQUE = 0.22D;
    public static final double GLIDE_DESCENT = -0.065D;
    public static final long CONTROL_TIMEOUT_TICKS = 10L;
    private static final double POWERED_DRAG = 0.985D;
    private static final double GLIDE_DRAG = 0.995D;
    private static final double STRAFE_SCALE = 0.72D;
    private static final double REVERSE_SCALE = 0.5D;
    private static final double LAUNCH_BOOST = 0.85D;
    private static final double MAX_CONTROL_PITCH = 75.0D;
    private static final double COLLISION_EPSILON = 1.0E-6D;

    private FlyingBroomRules() {
    }

    public static FlightDecision decide(
        final boolean hasRider,
        final boolean holdingBroom,
        final boolean gliding,
        final boolean soaring
    ) {
        if (!hasRider || !holdingBroom) {
            return new FlightDecision(false, false, NORMAL_TORQUE, 0.0D, MAX_SPEED);
        }
        return new FlightDecision(
            true,
            gliding,
            soaring ? SOARING_TORQUE : NORMAL_TORQUE,
            gliding ? 0.0D : THRUST,
            MAX_SPEED
        );
    }

    public static Vec3 nextVelocity(
        final Vec3 current,
        final float vehicleYaw,
        final ControlInput input,
        final FlightDecision decision
    ) {
        return nextVelocity(current, vehicleYaw, 0.0F, input, decision);
    }

    public static Vec3 nextVelocity(
        final Vec3 current,
        final float vehicleYaw,
        final float riderPitch,
        final ControlInput input,
        final FlightDecision decision
    ) {
        if (!decision.active()) {
            return Vec3.ZERO;
        }
        final Vec3 forward = horizontalForward(vehicleYaw);
        final Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        final Vec3 requested = requestedDirection(forward, right, riderPitch, input);
        if (decision.gliding()) {
            return glide(current, requested, decision);
        }
        return powered(current, requested, input, decision);
    }

    public static float nextYaw(final float currentYaw, final float riderYaw, final double torque) {
        return currentYaw + Mth.wrapDegrees(riderYaw - currentYaw) * (float) Math.clamp(torque, 0.0D, 1.0D);
    }

    private static Vec3 powered(
        final Vec3 current,
        final Vec3 requested,
        final ControlInput input,
        final FlightDecision decision
    ) {
        if (requested.lengthSqr() < 1.0E-8D) {
            return clampLength(current.scale(POWERED_DRAG), decision.maxSpeed());
        }
        final Vec3 direction = requested.normalize();
        final double currentSpeed = current.length();
        final double throttle = Math.min(1.0D, requested.length());
        final double reverse = input.forward() < 0.0D ? REVERSE_SCALE : 1.0D;
        final double launch = 1.0D + LAUNCH_BOOST * (1.0D - Math.min(currentSpeed / decision.maxSpeed(), 1.0D));
        final Vec3 redirected = current.lengthSqr() < 1.0E-8D
            ? current
            : current.lerp(direction.scale(currentSpeed), decision.torque());
        return clampLength(
            redirected.scale(POWERED_DRAG)
                .add(direction.scale(decision.thrust() * throttle * reverse * launch)),
            decision.maxSpeed()
        );
    }

    public static Vec3 retainUnblockedVelocity(final Vec3 requested, final Vec3 travelled) {
        return new Vec3(
            retainedAxis(requested.x, travelled.x),
            retainedAxis(requested.y, travelled.y),
            retainedAxis(requested.z, travelled.z)
        );
    }

    public static boolean controlsAreFresh(final long currentTick, final long lastControlTick) {
        if (lastControlTick == Long.MIN_VALUE) {
            return false;
        }
        final long age = currentTick - lastControlTick;
        return age >= 0L && age <= CONTROL_TIMEOUT_TICKS;
    }

    private static Vec3 glide(
        final Vec3 current,
        final Vec3 requested,
        final FlightDecision decision
    ) {
        final Vec3 horizontal = new Vec3(current.x, 0.0D, current.z);
        final double speed = horizontal.length();
        final Vec3 target = requested.lengthSqr() < 1.0E-8D
            ? horizontal
            : requested.normalize().scale(speed);
        final Vec3 steered = horizontal.lerp(target, decision.torque()).scale(GLIDE_DRAG);
        final double descent = Math.clamp(current.y * 0.82D - 0.02D, GLIDE_DESCENT, 0.0D);
        return clampLength(new Vec3(steered.x, descent, steered.z), decision.maxSpeed());
    }

    private static Vec3 requestedDirection(
        final Vec3 forward,
        final Vec3 right,
        final float riderPitch,
        final ControlInput input
    ) {
        final double pitch = Math.toRadians(Math.clamp(riderPitch, -MAX_CONTROL_PITCH, MAX_CONTROL_PITCH));
        final double forwardScale = input.forward() * Math.cos(pitch);
        final double verticalScale = -input.forward() * Math.sin(pitch) + (input.ascend() ? 1.0D : 0.0D);
        return forward.scale(forwardScale)
            .add(right.scale(input.strafe() * STRAFE_SCALE))
            .add(0.0D, verticalScale, 0.0D);
    }

    private static Vec3 horizontalForward(final float yaw) {
        final double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static Vec3 clampLength(final Vec3 velocity, final double maximum) {
        final double lengthSquared = velocity.lengthSqr();
        return lengthSquared > maximum * maximum ? velocity.scale(maximum / Math.sqrt(lengthSquared)) : velocity;
    }

    private static double retainedAxis(final double requested, final double travelled) {
        return Math.abs(requested - travelled) > COLLISION_EPSILON ? 0.0D : requested;
    }

    public record FlightDecision(
        boolean active,
        boolean gliding,
        double torque,
        double thrust,
        double maxSpeed
    ) {
        public FlightDecision {
            if (!Double.isFinite(torque) || torque < 0.0D || torque > 1.0D) {
                throw new IllegalArgumentException("Broom torque must be between zero and one");
            }
            if (!Double.isFinite(thrust) || !Double.isFinite(maxSpeed) || thrust < 0.0D || maxSpeed <= 0.0D) {
                throw new IllegalArgumentException("Broom thrust and maximum speed must be safe positive values");
            }
        }
    }

    public record ControlInput(double strafe, double forward, boolean ascend) {
        public static final ControlInput IDLE = new ControlInput(0.0D, 0.0D, false);

        public ControlInput {
            if (!Double.isFinite(strafe) || !Double.isFinite(forward)) {
                throw new IllegalArgumentException("Broom controls must be finite");
            }
            strafe = Math.clamp(strafe, -1.0D, 1.0D);
            forward = Math.clamp(forward, -1.0D, 1.0D);
        }
    }
}
