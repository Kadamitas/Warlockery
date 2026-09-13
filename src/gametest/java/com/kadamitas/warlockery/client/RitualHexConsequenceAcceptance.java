package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.ritual.hex.HexEntityMarkers;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Follow-up to a completed native ritual cast. Only trigger fixtures are staged; hex ticks remain natural. */
public final class RitualHexConsequenceAcceptance {
    private final ClientGameTestContext context;
    private final TestSingleplayerContext world;
    private final UUID targetId;
    private final HexKind kind;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private BlockPos bay;

    private RitualHexConsequenceAcceptance(final ClientGameTestContext context, final TestSingleplayerContext world,
        final UUID target, final HexKind kind) {
        this.context = context; this.world = world; this.targetId = target; this.kind = kind;
    }

    public static Map<String, Object> verify(final ClientGameTestContext context, final TestSingleplayerContext world,
        final UUID target, final String ritualId) {
        final String id = ritualId.replaceFirst("^warlockery:", "");
        final HexKind kind = switch (id) {
            case "hex_heat_metal" -> HexKind.HEAT_METAL;
            case "hex_insanity" -> HexKind.INSANITY;
            case "hex_misfortune" -> HexKind.MISFORTUNE;
            case "hex_nightmare" -> HexKind.WAKING_NIGHTMARE;
            case "hex_overheating" -> HexKind.OVERHEATING;
            case "hex_sinking" -> HexKind.SINKING;
            default -> throw new IllegalArgumentException("No persistent-hex consequence scenario for " + ritualId);
        };
        final var trial = new RitualHexConsequenceAcceptance(context, world, target, kind);
        trial.report.put("ritual", id); trial.report.put("target_uuid", target.toString());
        trial.report.put("status", "RUNNING");
        check(trial.value(player -> trial.target(player).isAlive() && HexState.isActive(trial.target(player), kind)),
            "Follow-up requires the actual living target already hexed by the native ritual");
        trial.stageBay();
        switch (kind) {
            case HEAT_METAL -> trial.metal();
            case OVERHEATING -> trial.overheating();
            case SINKING -> trial.sinking();
            case INSANITY, WAKING_NIGHTMARE -> trial.threats();
            case MISFORTUNE -> trial.misfortune();
        }
        trial.report.put("status", "PASSED");
        trial.report.put("target_health_after", trial.value(player -> trial.target(player).getHealth()));
        trial.report.put("not_run", List.of("full hex expiry and cure aftermath", "all alternative biome/fluid/metal tags",
            "hallucination combat damage, loot and maximum population", "nightmare sleeping pause", "misfortune outcome distribution"));
        trial.report.put("fixture", "Runs only after a native cast. A separate supported arena, leather sun protection, equipment, biome and water are staged triggers; target/camera teleport only positions the trial before observation. No hex/effect, damage, desired velocity, spawn marker or tickCount is injected. Normal world ticks produce all checked consequences.");
        trial.lookAtTarget();
        return Map.copyOf(trial.report);
    }

    private void stageBay() {
        server(player -> {
            final LivingEntity target = target(player);
            bay = new BlockPos(40, target.blockPosition().getY(), 0);
            for (Entity entity : player.level().getEntities((Entity) null, new AABB(bay).inflate(24),
                entity -> !(entity instanceof net.minecraft.world.entity.player.Player) && !entity.getUUID().equals(targetId)))
                entity.discard();
            for (BlockPos pos : BlockPos.betweenClosed(bay.offset(-12, -1, -12), bay.offset(12, 8, 12)))
                player.level().setBlockAndUpdate(pos, pos.getY() == bay.getY() - 1 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            target.teleportTo(bay.getX() + .5, bay.getY(), bay.getZ() + .5);
            target.setDeltaMovement(Vec3.ZERO); target.clearFire();
            if (target instanceof Mob mob) { mob.setNoAi(true); mob.setTarget(null); }
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) target.setItemSlot(slot, ItemStack.EMPTY);
            target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            player.teleportTo(bay.getX() + .5, bay.getY(), bay.getZ() - 7.5);
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets(); world.getConnection().waitForChunksRender();
        context.waitTicks(3);
    }

    private void metal() {
        final float dryHealth = value(player -> target(player).getHealth());
        context.waitTicks(45);
        check(value(player -> !target(player).isOnFire() && target(player).getHealth() == dryHealth),
            "Active Heat Metal has no burning consequence without tagged metal");
        server(player -> target(player).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
        final float before = value(player -> target(player).getHealth());
        await(player -> target(player).isOnFire() && target(player).getHealth() < before, 60,
            "Normal Heat Metal ticks ignite and actually damage the metal wearer");
        report.put("metal_free_health", dryHealth); report.put("metal_equipped_health_before", before);
        report.put("burning_health_after", value(player -> target(player).getHealth()));
    }

    private void overheating() {
        server(player -> {
            final var biome = player.level().registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:desert")));
            net.minecraft.server.commands.FillBiomeCommand.fill(player.level(), bay.offset(-8, -4, -8), bay.offset(8, 8, 8), biome);
            player.level().getServer().getCommands().performPrefixedCommand(player.level().getServer().createCommandSourceStack(), "weather clear");
        });
        check(value(player -> player.level().getBiome(target(player).blockPosition()).value().getBaseTemperature() >= 1.0F
            && !target(player).isInWaterOrRain() && !target(player).hasEffect(MobEffects.FIRE_RESISTANCE)),
            "Actual hot, dry, unprotected trigger exists");
        final float before = value(player -> target(player).getHealth());
        await(player -> target(player).isOnFire() && target(player).getHealth() < before, 60,
            "Normal Overheating ticks ignite and damage the cursed target in the hot biome");
        report.put("hot_dry_health_before", before); report.put("hot_dry_health_after", value(player -> target(player).getHealth()));
        server(player -> {
            player.level().setBlockAndUpdate(bay, Blocks.WATER.defaultBlockState());
            target(player).clearFire();
        });
        await(player -> target(player).isInWaterOrRain(), 20, "Target actually becomes wet in placed water");
        final float wetHealth = value(player -> target(player).getHealth());
        context.waitTicks(45);
        check(value(player -> target(player).isAlive() && target(player).getHealth() == wetHealth && !target(player).isOnFire()),
            "Actual water contact stops subsequent heat damage while the hex remains active");
        report.put("wet_health_before", wetHealth); report.put("wet_health_after", value(player -> target(player).getHealth()));
    }

    private void sinking() {
        final double dryY = value(player -> target(player).getY());
        context.waitTicks(25);
        check(Math.abs(value(player -> target(player).getY()) - dryY) < .05, "Sinking does not pull its target through solid dry ground");
        final UUID controlId = value(player -> {
            final LivingEntity target = target(player);
            check(target instanceof Mob, "Matched swimmer scenario requires the ritual's staged mob");
            pool(player, bay); pool(player, bay.offset(8, 0, 0));
            final Mob control = (Mob) target.getType().create(player.level(), EntitySpawnReason.COMMAND);
            check(control != null, "Matching untreated species exists");
            control.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            control.snapTo(bay.getX() + 8.5, bay.getY(), bay.getZ() + .5);
            control.setNoAi(false); control.setPersistenceRequired();
            player.level().addFreshEntity(control);
            check(!HexState.isActive(control, HexKind.SINKING), "Control has no sinking hex");
            ((Mob) target).setNoAi(false);
            target.teleportTo(bay.getX() + .5, bay.getY(), bay.getZ() + .5); target.setDeltaMovement(Vec3.ZERO);
            return control.getUUID();
        });
        await(player -> target(player).getY() < player.level().getEntity(controlId).getY() - .5, 100,
            "Normally ticking cursed swimmer sinks measurably below an untreated same-species swimmer in matched pools");
        report.put("dry_ground_y", dryY); report.put("cursed_swimmer_y", value(player -> target(player).getY()));
        report.put("untreated_swimmer_y", value(player -> player.level().getEntity(controlId).getY()));
        report.put("untreated_swimmer_uuid", controlId.toString());
        report.put("target_swimming", value(player -> target(player).isSwimming()));
    }

    private void threats() {
        check(value(player -> !target(player).isSleeping()), "The target is awake for its hallucination trigger");
        await(player -> !matchingThreats(player).isEmpty(), kind == HexKind.INSANITY ? 450 : 250,
            "Natural hex ticks create an actual temporary hostile creature in clear space");
        final UUID threatId = value(player -> matchingThreats(player).getFirst().getUUID());
        final Map<String, Object> details = value(player -> {
            final Entity threat = player.level().getEntity(threatId);
            final var marker = HexEntityMarkers.threat(threat).orElseThrow();
            check(marker.kind() == kind && marker.targetId().equals(targetId), "Actual spawned threat belongs to this hex and victim");
            check(marker.expiration() > player.level().getGameTime(), "Spawned creature has a future temporary expiry");
            check(threat instanceof Mob mob && mob.getTarget() == target(player), "Spawned hostile mob targets the cursed victim");
            return Map.<String, Object>of("uuid", threatId.toString(), "type", BuiltInRegistries.ENTITY_TYPE.getKey(threat.getType()).toString(),
                "target_uuid", marker.targetId().toString(), "expiration", marker.expiration(), "observed_at", player.level().getGameTime());
        });
        context.waitFor(client -> {
            for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(threatId)) return true;
            return false;
        });
        report.put("spawned_hostile", details);
    }

    private List<Entity> matchingThreats(final ServerPlayer player) {
        return player.level().getEntities((Entity) null, new AABB(bay).inflate(24), entity ->
            HexEntityMarkers.threat(entity).filter(marker -> marker.kind() == kind && marker.targetId().equals(targetId)).isPresent());
    }

    private void misfortune() {
        await(player -> !misfortuneEffects(target(player)).isEmpty(), 220,
            "Natural Misfortune ticks produce a secondary mishap beyond the permanent Unluck marker");
        report.put("secondary_mishaps", value(player -> misfortuneEffects(target(player))));
    }
    private static List<String> misfortuneEffects(final LivingEntity target) {
        final List<String> result = new ArrayList<>();
        for (var effect : List.of(MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.HUNGER, MobEffects.MINING_FATIGUE, MobEffects.BLINDNESS))
            if (target.hasEffect(effect)) result.add(effect.unwrapKey().orElseThrow().identifier() + ":" + target.getEffect(effect).getAmplifier()
                + ":" + target.getEffect(effect).getDuration());
        return List.copyOf(result);
    }

    private static void pool(final ServerPlayer player, final BlockPos surface) {
        for (BlockPos pos : BlockPos.betweenClosed(surface.offset(-2, -6, -2), surface.offset(2, 0, 2))) {
            final boolean wall = pos.getX() == surface.getX() - 2 || pos.getX() == surface.getX() + 2
                || pos.getZ() == surface.getZ() - 2 || pos.getZ() == surface.getZ() + 2 || pos.getY() == surface.getY() - 6;
            player.level().setBlockAndUpdate(pos, wall ? Blocks.BEDROCK.defaultBlockState() : Blocks.WATER.defaultBlockState());
        }
    }
    private void lookAtTarget() {
        world.getConnection().waitForClientboundPackets();
        final Vec3 point = value(player -> target(player).getBoundingBox().getCenter());
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        });
        context.waitTicks(2);
    }
    private LivingEntity target(final ServerPlayer player) {
        final Entity target = player.level().getEntity(targetId);
        check(target instanceof LivingEntity, "The native ritual's original target remains loaded");
        return (LivingEntity) target;
    }
    private void await(final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int i = 0; i < ticks && !value(condition::test); i++) context.waitTicks(1);
        check(value(condition::test), message);
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T value(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
