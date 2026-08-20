package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CircleMageEntity;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

public final class SeerCovenRuntime {
    public static final int PARTICIPANT_RADIUS = 8;

    private SeerCovenRuntime() {
    }

    public static boolean isGoldenCircleCenter(final Level level, final BlockPos position) {
        return level.getBlockState(position).is(ModBlocks.ALL.get("circle").get());
    }

    public static CallResult call(final ServerLevel destination, final BlockPos center, final Player player) {
        final List<Mob> mages = loadedMagesOwnedBy(destination, player);
        int arrivedCount = 0;
        for (int index = 0; index < mages.size(); index++) {
            // Entity.teleport returns the possibly new instance: a cross-dimension move replaces
            // the entity, so the recall must be applied to the entity that actually arrived,
            // never to the discarded original.
            final Entity gathered = gather(
                mages.get(index), destination, SeerCovenRules.gatheringPosition(center, index, mages.size())
            );
            // The dedicated runtime only cancels its own stale action, path, report, and session
            // state. The exact ring position, feedback, and participant result stay unchanged.
            if (gathered == null) {
                // The teleport produced no entity, so nothing arrived at that ring position and it
                // must not be reported to the player as a called Mage.
                continue;
            }
            arrivedCount++;
            if (gathered instanceof CircleMageEntity dedicated) {
                dedicated.onSeerRecall(destination, center);
            }
        }
        destination.playSound(
            null,
            center,
            arrivedCount == 0 ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            0.8F,
            arrivedCount == 0 ? 0.7F : 1.15F
        );
        final CallResult result = new CallResult(arrivedCount);
        final Component feedback = result.calledMages() > 0
            ? Component.translatable(result.feedbackKey(), result.calledMages())
            : Component.translatable(result.feedbackKey());
        player.sendSystemMessage(feedback.copy().withStyle(
            result.calledMages() > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY
        ));
        return result;
    }

    /**
     * The participants a rite cast by {@code caster} may draw on: every living player standing in the circle,
     * plus that caster's own Circle Mages.
     *
     * <p>Players are counted whoever they are, because gathering people is the point of a coven rite. Mages
     * are attributed strictly, because borrowing a coven is not. One query per kind, with the ownership test
     * folded into the entity filter, since site inspection runs this once per visible ritual on every screen
     * open.</p>
     */
    public static int countParticipants(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final @Nullable UUID caster
    ) {
        final AABB area = new AABB(center).inflate(radius);
        final int players = level.getEntitiesOfClass(Player.class, area, Player::isAlive).size();
        final Optional<UUID> owner = Optional.ofNullable(caster);
        final int mages = level.getEntitiesOfClass(
            Mob.class, area, mage -> answersTo(mage, owner)
        ).size();
        return players + SeerCovenRules.cappedCoven(mages);
    }

    private static boolean answersTo(final Entity entity, final Optional<UUID> caster) {
        final CreatureKind kind = entity instanceof ArcaneCreature creature ? creature.creatureKind() : null;
        return entity.isAlive()
            && SeerCovenRules.countsForCaster(kind, CreatureBehaviorState.owner(entity), caster);
    }

    /**
     * Reads the capped roster directly. The previous all-loaded-entity scan across every level is
     * gone: a loaded bound Mage idempotently self-repairs its own membership once after load
     * through {@code CircleMageRuntime}, so counting never traverses the world.
     */
    public static int countOwnedMages(final ServerLevel level, final Player player) {
        return CovenRosterData.get(level).count(player.getUUID());
    }

    public static void register(final ServerLevel level, final Player owner, final Mob mage) {
        CovenRosterData.get(level).register(owner.getUUID(), mage.getUUID());
    }

    public static void handleDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof ArcaneCreature creature
            && creature.creatureKind() == CreatureKind.CIRCLE_MAGE
            && event.getEntity().level() instanceof ServerLevel level) {
            CovenRosterData.get(level).unregister(event.getEntity().getUUID());
        }
    }

    public static boolean isBoundCircleMage(final Entity entity) {
        final CreatureKind kind = entity instanceof ArcaneCreature creature ? creature.creatureKind() : null;
        return entity.isAlive()
            && SeerCovenRules.isCircleMageParticipant(kind, CreatureBehaviorState.owner(entity).isPresent());
    }

    /**
     * At most six roster UUIDs are considered. Each is resolved by direct entity lookup in the
     * already-loaded server levels and validated for exact type, owner, and alive status, then the
     * accepted loaded set is UUID-sorted exactly as before. No level is traversed entity by entity,
     * no chunk is forced, and an unrostered unbound Mage is never discovered. Unloaded roster
     * members remain uncalled, exactly as today.
     */
    private static List<Mob> loadedMagesOwnedBy(final ServerLevel destination, final Player player) {
        final List<UUID> members = CovenRosterData.get(destination).members(player.getUUID());
        final List<Mob> loaded = new ArrayList<>();
        for (final UUID member : members) {
            for (final ServerLevel level : destination.getServer().getAllLevels()) {
                final Entity resolved = level.getEntity(member);
                if (resolved instanceof Mob mage
                    && isBoundCircleMage(mage)
                    && CreatureBehaviorState.isOwnedBy(mage, player.getUUID())) {
                    loaded.add(mage);
                    break;
                }
            }
        }
        loaded.sort(Comparator.comparing(Entity::getUUID));
        return List.copyOf(loaded);
    }

    /**
     * Returns the entity that actually arrived, or null when the teleport produced none. Across
     * dimensions {@code Entity.teleport} replaces the instance, so callers must use the returned
     * entity and never the original.
     */
    public static @org.jspecify.annotations.Nullable Entity gatherForRecall(
        final Mob mage,
        final ServerLevel destination,
        final Vec3 position
    ) {
        return gather(mage, destination, position);
    }

    private static @org.jspecify.annotations.Nullable Entity gather(
        final Mob mage,
        final ServerLevel destination,
        final Vec3 position
    ) {
        mage.setTarget(null);
        mage.getNavigation().stop();
        mage.setPersistenceRequired();
        final Entity arrived = mage.teleport(new TeleportTransition(
            destination,
            position,
            Vec3.ZERO,
            mage.getYRot(),
            mage.getXRot(),
            TeleportTransition.DO_NOTHING
        ));
        destination.sendParticles(
            ParticleTypes.ENCHANT,
            position.x(),
            position.y() + 0.6,
            position.z(),
            10,
            0.35,
            0.5,
            0.35,
            0.02
        );
        // Deliberately propagated: the javadoc forbids callers from using the original, so
        // silently substituting it here would hand back the very instance that must not be used.
        return arrived;
    }

    public record CallResult(int calledMages) {
        public CallResult {
            if (calledMages < 0) {
                throw new IllegalArgumentException("Called mage count cannot be negative");
            }
        }

        public String feedbackKey() {
            return SeerCovenRules.feedbackKey(calledMages);
        }
    }
}
