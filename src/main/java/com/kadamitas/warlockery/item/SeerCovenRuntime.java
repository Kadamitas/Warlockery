package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
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

public final class SeerCovenRuntime {
    public static final int PARTICIPANT_RADIUS = 8;

    private SeerCovenRuntime() {
    }

    public static boolean isGoldenCircleCenter(final Level level, final BlockPos position) {
        return level.getBlockState(position).is(ModBlocks.ALL.get("circle").get());
    }

    public static CallResult call(final ServerLevel destination, final BlockPos center, final Player player) {
        final List<Mob> mages = loadedMagesOwnedBy(destination, player);
        for (int index = 0; index < mages.size(); index++) {
            gather(mages.get(index), destination, SeerCovenRules.gatheringPosition(center, index, mages.size()));
        }
        destination.playSound(
            null,
            center,
            mages.isEmpty() ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            0.8F,
            mages.isEmpty() ? 0.7F : 1.15F
        );
        final CallResult result = new CallResult(mages.size());
        final Component feedback = result.calledMages() > 0
            ? Component.translatable(result.feedbackKey(), result.calledMages())
            : Component.translatable(result.feedbackKey());
        player.sendSystemMessage(feedback.copy().withStyle(
            result.calledMages() > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY
        ));
        return result;
    }

    public static int countParticipants(final ServerLevel level, final BlockPos center, final int radius) {
        final AABB area = new AABB(center).inflate(radius);
        final int players = level.getEntitiesOfClass(Player.class, area, Player::isAlive).size();
        final int mages = level.getEntitiesOfClass(Mob.class, area, SeerCovenRuntime::isBoundCircleMage).size();
        return players + mages;
    }

    public static int countOwnedMages(final ServerLevel level, final Player player) {
        final CovenRosterData roster = CovenRosterData.get(level);
        loadedMagesOwnedBy(level, player).forEach(mage -> roster.register(player.getUUID(), mage.getUUID()));
        return roster.count(player.getUUID());
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

    private static List<Mob> loadedMagesOwnedBy(final ServerLevel destination, final Player player) {
        return StreamSupport.stream(destination.getServer().getAllLevels().spliterator(), false)
            .flatMap(level -> StreamSupport.stream(level.getAllEntities().spliterator(), false))
            .filter(Mob.class::isInstance)
            .map(Mob.class::cast)
            .filter(SeerCovenRuntime::isBoundCircleMage)
            .filter(mage -> CreatureBehaviorState.isOwnedBy(mage, player.getUUID()))
            .sorted(Comparator.comparing(Entity::getUUID))
            .toList();
    }

    private static void gather(final Mob mage, final ServerLevel destination, final Vec3 position) {
        mage.setTarget(null);
        mage.getNavigation().stop();
        mage.setPersistenceRequired();
        mage.teleport(new TeleportTransition(
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
