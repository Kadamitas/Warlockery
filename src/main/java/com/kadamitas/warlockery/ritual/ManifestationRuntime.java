package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.dream.SpiritManifestationRules;
import com.kadamitas.warlockery.dream.SpiritManifestationState;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.dream.SpiritWorldState;
import com.kadamitas.warlockery.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ManifestationRuntime {
    private ManifestationRuntime() {
    }

    public static ManifestationRules.Decision diagnose(final @Nullable ServerPlayer target) {
        if (target == null) {
            return ManifestationRules.decide(false, false, false);
        }
        final long tick = target.level().getServer().getTickCount();
        return ManifestationRules.decide(
            true,
            target.isSleeping() || SpiritWorldRuntime.isDreaming(target),
            SpiritManifestationState.granted(target, tick)
        );
    }

    public static boolean manifest(
        final ServerLevel level,
        final BlockPos center,
        final ServerPlayer dreamer,
        final int duration
    ) {
        if (!diagnose(dreamer).ready()) {
            return false;
        }
        final long expiration = level.getServer().getTickCount() + Math.max(20, duration);
        SpiritManifestationState.grant(dreamer, expiration);
        dreamer.sendSystemMessage(Component.translatable(
            "message.warlockery.manifestation.granted",
            Math.max(1, duration / 20)
        ));
        return true;
    }

    public static SpiritManifestationRules.Decision portalDecision(final ServerPlayer player) {
        final long tick = player.level().getServer().getTickCount();
        final Optional<SpiritWorldState.Session> dream = SpiritWorldState.read(player);
        final boolean destination = dream
            .map(SpiritWorldState.Session::sourceDimension)
            .map(identifier -> player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, identifier)))
            .isPresent();
        return SpiritManifestationRules.enter(
            SpiritWorldRuntime.isDreaming(player),
            SpiritWorldRuntime.isSpiritWorld(player.level(), player),
            SpiritManifestationState.granted(player, tick),
            SpiritManifestationState.active(player),
            destination
        );
    }

    public static boolean canEnterPortal(final ServerPlayer player) {
        return portalDecision(player).ready();
    }

    public static boolean enterPortal(final ServerPlayer player, final BlockPos portal) {
        final SpiritManifestationRules.Decision decision = portalDecision(player);
        if (!decision.ready()) {
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.manifestation." + decision.id()
            ));
            return false;
        }
        final SpiritWorldState.Session dream = SpiritWorldState.read(player).orElseThrow();
        final ServerLevel destination = player.level().getServer().getLevel(ResourceKey.create(
            Registries.DIMENSION,
            dream.sourceDimension()
        ));
        if (destination == null) {
            return false;
        }
        destination.getChunkAt(portal);
        final Optional<Vec3> arrival = safeArrival(destination, portal, player);
        if (arrival.isEmpty()) {
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.manifestation.destination_unavailable"
            ));
            return false;
        }
        final List<ItemStackWithSlot> completeInventory = SpiritWorldState.snapshot(player.getInventory());
        final List<ItemStackWithSlot> storedInventory = completeInventory.stream()
            .filter(entry -> !isIcyNeedle(entry.stack()))
            .toList();
        final List<ItemStackWithSlot> carriedNeedles = completeInventory.stream()
            .filter(entry -> isIcyNeedle(entry.stack()))
            .toList();
        final int selectedSlot = player.getInventory().getSelectedSlot();
        SpiritManifestationState.begin(
            player,
            player.level().dimension().identifier(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot(),
            storedInventory,
            selectedSlot
        );
        player.getInventory().clearContent();
        carriedNeedles.forEach(entry -> player.getInventory().setItem(entry.slot(), entry.stack().copy()));
        player.getInventory().setChanged();
        final Vec3 target = arrival.orElseThrow();
        final boolean teleported = player.teleportTo(
            destination,
            target.x(),
            target.y(),
            target.z(),
            Set.of(),
            player.getYRot(),
            player.getXRot(),
            true
        );
        if (!teleported) {
            SpiritWorldState.restore(player.getInventory(), completeInventory, selectedSlot);
            SpiritManifestationState.finish(player);
            return false;
        }
        player.setPortalCooldown(60);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, true));
        player.sendSystemMessage(Component.translatable("message.warlockery.manifestation.entered"));
        return true;
    }

    public static boolean returnToSpiritWorld(final ServerPlayer player, final ReturnCause cause) {
        final Optional<SpiritManifestationState.ActiveManifestation> stored = SpiritManifestationState.read(player);
        if (stored.isEmpty()) {
            return false;
        }
        final SpiritManifestationState.ActiveManifestation manifestation = stored.orElseThrow();
        final ServerLevel destination = player.level().getServer().getLevel(ResourceKey.create(
            Registries.DIMENSION,
            manifestation.returnDimension()
        ));
        if (destination == null) {
            return false;
        }
        destination.getChunkAt(BlockPos.containing(
            manifestation.returnX(),
            manifestation.returnY(),
            manifestation.returnZ()
        ));
        final List<ItemStack> needles = new ArrayList<>();
        final List<ItemStack> discarded = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            (isIcyNeedle(stack) ? needles : discarded).add(stack.copy());
        }
        final boolean teleported = player.teleportTo(
            destination,
            manifestation.returnX(),
            manifestation.returnY(),
            manifestation.returnZ(),
            Set.of(),
            manifestation.returnYaw(),
            manifestation.returnPitch(),
            true
        );
        if (!teleported) {
            return false;
        }
        discarded.forEach(stack -> player.drop(stack, false));
        SpiritWorldState.restore(
            player.getInventory(),
            manifestation.storedInventory(),
            manifestation.selectedSlot()
        );
        needles.forEach(stack -> addOrDrop(player, stack));
        SpiritManifestationState.finish(player);
        player.removeEffect(MobEffects.GLOWING);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        player.setDeltaMovement(Vec3.ZERO);
        player.setPortalCooldown(60);
        player.sendSystemMessage(Component.translatable(
            "message.warlockery.manifestation.returned." + cause.id()
        ));
        return true;
    }

    public static boolean isActive(final ServerPlayer player) {
        return SpiritManifestationState.active(player);
    }

    public static void tick(final ServerLevel level) {
        final long tick = level.getServer().getTickCount();
        level.players().stream()
            .filter(SpiritManifestationState::active)
            .forEach(player -> {
                if (SpiritManifestationRules.expired(tick, SpiritManifestationState.expiration(player))) {
                    returnToSpiritWorld(player, ReturnCause.EXPIRED);
                    return;
                }
                if (player.tickCount % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, true));
                    level.sendParticles(
                        ParticleTypes.SOUL,
                        player.getX(),
                        player.getY() + 1.0,
                        player.getZ(),
                        6,
                        0.3,
                        0.6,
                        0.3,
                        0.01
                    );
                }
            });
    }

    public static int sustain(final ServerLevel level, final AABB area, final int duration) {
        final long offered = level.getServer().getTickCount() + Math.max(20, duration);
        final List<ServerPlayer> manifestations = level.players().stream()
            .filter(SpiritManifestationState::active)
            .filter(player -> area.contains(player.position()))
            .toList();
        manifestations.forEach(player -> SpiritManifestationState.grant(player, sustainedExpiration(
            SpiritManifestationState.expiration(player),
            offered
        )));
        return manifestations.size();
    }

    static long sustainedExpiration(final long current, final long offered) {
        return SpiritManifestationRules.extend(current, offered);
    }

    private static Optional<Vec3> safeArrival(
        final ServerLevel level,
        final BlockPos equivalentPortal,
        final ServerPlayer player
    ) {
        return List.of(
            equivalentPortal,
            equivalentPortal.above(),
            equivalentPortal.north(),
            equivalentPortal.south(),
            equivalentPortal.east(),
            equivalentPortal.west(),
            equivalentPortal.above(2)
        ).stream().filter(level::isInWorldBounds).filter(candidate -> level.noCollision(
            player,
            player.getBoundingBox().move(
                candidate.getX() + 0.5 - player.getX(),
                candidate.getY() - player.getY(),
                candidate.getZ() + 0.5 - player.getZ()
            )
        )).map(candidate -> Vec3.atBottomCenterOf(candidate)).findFirst();
    }

    private static boolean isIcyNeedle(final ItemStack stack) {
        return stack.is(ModItems.ALL.get("ingredient_icy_needle").get());
    }

    private static void addOrDrop(final ServerPlayer player, final ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public enum ReturnCause {
        ICY_NEEDLE("icy_needle"),
        FATAL_DAMAGE("fatal_damage"),
        EXPIRED("expired"),
        PORTAL("portal");

        private final String id;

        ReturnCause(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
