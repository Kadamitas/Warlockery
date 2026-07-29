package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WolfTrapBlockEntity extends BlockEntity {
    private static final int LURE_TICKS = 1_200;
    private static final double BAIT_RADIUS = 8.0;
    private boolean armed;
    private int lureTicks;
    private @Nullable UUID luredTarget;
    private @Nullable UUID capturedTarget;
    private TrapDisplay display = TrapDisplay.UNARMED;

    public WolfTrapBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.WOLF_TRAP.get(), pos, state);
    }

    public static void serverTick(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final WolfTrapBlockEntity trap
    ) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 20 != 0) {
            return;
        }
        final boolean fullMoon = isFullMoon(serverLevel, pos);
        final boolean altar = hasWolfAltar(serverLevel, pos);
        final Optional<Sheep> bait = findBait(serverLevel, pos);

        if (!trap.armed || trap.capturedTarget != null) {
            trap.lureTicks = trap.capturedTarget == null ? 0 : trap.lureTicks;
        } else if (fullMoon && altar && bait.isPresent() && trap.luredTarget == null) {
            trap.lureTicks = Math.min(LURE_TICKS, trap.lureTicks + 20);
            if (trap.lureTicks >= LURE_TICKS) {
                trap.spawnLuredWolf(serverLevel, bait.get());
            }
        } else if (!fullMoon || !altar || bait.isEmpty()) {
            trap.lureTicks = 0;
        }

        if (trap.luredTarget != null && trap.capturedTarget == null) {
            final Entity target = serverLevel.getEntity(trap.luredTarget);
            if (target == null || !target.isAlive()) {
                trap.luredTarget = null;
                trap.lureTicks = 0;
            } else {
                if (target instanceof Mob mob) {
                    mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 1.0);
                }
                if (target.distanceToSqr(Vec3.atCenterOf(pos)) <= 2.25) {
                trap.tryCapture(target);
                }
            }
        }
        trap.updateDisplay(serverLevel, state, fullMoon, altar, bait.isPresent());
    }

    public void toggle(final Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (capturedTarget != null) {
            final Entity captured = serverLevel.getEntity(capturedTarget);
            if (captured instanceof Mob mob) {
                mob.setNoAi(false);
            }
            capturedTarget = null;
            luredTarget = null;
            lureTicks = 0;
            armed = false;
            player.sendSystemMessage(Component.translatable("message.warlockery.wolftrap.released"));
        } else {
            armed = !armed;
            if (!armed) {
                lureTicks = 0;
                luredTarget = null;
            }
            player.sendSystemMessage(Component.translatable(armed
                ? "message.warlockery.wolftrap.armed"
                : "message.warlockery.wolftrap.disarmed"));
        }
        serverLevel.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, armed ? 1.2F : 0.8F);
        updateDisplay(
            serverLevel,
            getBlockState(),
            isFullMoon(serverLevel, worldPosition),
            hasWolfAltar(serverLevel, worldPosition),
            findBait(serverLevel, worldPosition).isPresent()
        );
    }

    public void tryCapture(final Entity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !armed || capturedTarget != null) {
            return;
        }
        final boolean lured = luredTarget != null && luredTarget.equals(entity.getUUID());
        final boolean transformedPlayer = entity instanceof Player player
            && SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF
            && isFullMoon(serverLevel, worldPosition);
        if (!lured && !transformedPlayer) {
            return;
        }

        capturedTarget = entity.getUUID();
        armed = false;
        entity.snapTo(worldPosition.getX() + 0.5, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 60 * 5, 10, false, true));
            living.hurtServer(serverLevel, serverLevel.damageSources().generic(), 4.0F);
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        serverLevel.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.55F);
        updateDisplay(
            serverLevel,
            getBlockState(),
            isFullMoon(serverLevel, worldPosition),
            hasWolfAltar(serverLevel, worldPosition),
            findBait(serverLevel, worldPosition).isPresent()
        );
    }

    private void spawnLuredWolf(final ServerLevel level, final Sheep bait) {
        final WerewolfEntity werewolf = ModEntities.WEREWOLF.get().create(level, EntitySpawnReason.EVENT);
        if (werewolf == null) {
            return;
        }
        final double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        final double distance = 6.0 + level.getRandom().nextDouble() * 3.0;
        final double x = worldPosition.getX() + 0.5 + Math.cos(angle) * distance;
        final double z = worldPosition.getZ() + 0.5 + Math.sin(angle) * distance;
        final BlockPos spawn = BlockPos.containing(x, worldPosition.getY() + 1.0, z);
        werewolf.snapTo(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), z);
        werewolf.setCustomName(Component.translatable("entity.warlockery.lured_werewolf"));
        werewolf.setCustomNameVisible(true);
        werewolf.setPersistenceRequired();
        werewolf.getPersistentData().putLong("WarlockeryWolfTrap", worldPosition.asLong());
        werewolf.setTarget(bait);
        if (level.addFreshEntity(werewolf)) {
            luredTarget = werewolf.getUUID();
            level.playSound(null, worldPosition, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.25F);
        }
    }

    private void updateDisplay(
        final ServerLevel level,
        final BlockState state,
        final boolean fullMoon,
        final boolean altar,
        final boolean bait
    ) {
        final int progress = Math.clamp(lureTicks * 100 / LURE_TICKS / 5 * 5, 0, 100);
        final String capturedName = capturedTarget == null
            ? ""
            : Optional.ofNullable(level.getEntity(capturedTarget)).map(entity -> entity.getName().getString()).orElse("Captured werewolf");
        final TrapDisplay next = new TrapDisplay(armed, fullMoon, altar, bait, progress, luredTarget != null, capturedName);
        if (!next.equals(display)) {
            display = next;
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public TrapDisplay getDisplay() {
        return display;
    }

    private static boolean isFullMoon(final ServerLevel level, final BlockPos pos) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, Vec3.atCenterOf(pos)) == MoonPhase.FULL_MOON
            && level.isDarkOutside();
    }

    private static boolean hasWolfAltar(final ServerLevel level, final BlockPos pos) {
        return Direction.Plane.HORIZONTAL.stream().anyMatch(direction ->
            level.getBlockState(pos.relative(direction)).is(ModBlocks.ALL.get("wolfaltar").get())
        );
    }

    private static Optional<Sheep> findBait(final ServerLevel level, final BlockPos pos) {
        return level.getEntitiesOfClass(Sheep.class, new AABB(pos).inflate(BAIT_RADIUS), sheep -> sheep.isAlive() && !sheep.isBaby())
            .stream()
            .min(java.util.Comparator.comparingDouble(sheep -> sheep.distanceToSqr(Vec3.atCenterOf(pos))));
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        armed = input.getBooleanOr("Armed", false);
        lureTicks = input.getIntOr("LureTicks", 0);
        luredTarget = input.read("LuredTarget", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        capturedTarget = input.read("CapturedTarget", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        display = readDisplay(input);
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("Armed", armed);
        output.putInt("LureTicks", lureTicks);
        if (luredTarget != null) {
            output.store("LuredTarget", net.minecraft.core.UUIDUtil.CODEC, luredTarget);
        }
        if (capturedTarget != null) {
            output.store("CapturedTarget", net.minecraft.core.UUIDUtil.CODEC, capturedTarget);
        }
        writeDisplay(output, display);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        writeDisplay(tag, display);
        return tag;
    }

    private static TrapDisplay readDisplay(final ValueInput input) {
        return new TrapDisplay(
            input.getBooleanOr("DisplayArmed", false),
            input.getBooleanOr("DisplayFullMoon", false),
            input.getBooleanOr("DisplayAltar", false),
            input.getBooleanOr("DisplayBait", false),
            input.getIntOr("DisplayProgress", 0),
            input.getBooleanOr("DisplayLured", false),
            input.getStringOr("DisplayCaptured", "")
        );
    }

    private static void writeDisplay(final ValueOutput output, final TrapDisplay display) {
        output.putBoolean("DisplayArmed", display.armed());
        output.putBoolean("DisplayFullMoon", display.fullMoon());
        output.putBoolean("DisplayAltar", display.wolfAltar());
        output.putBoolean("DisplayBait", display.bait());
        output.putInt("DisplayProgress", display.progress());
        output.putBoolean("DisplayLured", display.lured());
        output.putString("DisplayCaptured", display.capturedName());
    }

    private static void writeDisplay(final CompoundTag tag, final TrapDisplay display) {
        tag.putBoolean("DisplayArmed", display.armed());
        tag.putBoolean("DisplayFullMoon", display.fullMoon());
        tag.putBoolean("DisplayAltar", display.wolfAltar());
        tag.putBoolean("DisplayBait", display.bait());
        tag.putInt("DisplayProgress", display.progress());
        tag.putBoolean("DisplayLured", display.lured());
        tag.putString("DisplayCaptured", display.capturedName());
    }

    public record TrapDisplay(
        boolean armed,
        boolean fullMoon,
        boolean wolfAltar,
        boolean bait,
        int progress,
        boolean lured,
        String capturedName
    ) {
        public static final TrapDisplay UNARMED = new TrapDisplay(false, false, false, false, 0, false, "");
    }
}
