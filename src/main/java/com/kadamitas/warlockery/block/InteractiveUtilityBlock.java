package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.MirrorState;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.item.UtilityDecision;
import com.kadamitas.warlockery.item.WaystoneState;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class InteractiveUtilityBlock extends Block {
    private static final int WARD_INTERVAL = 20;
    private final UtilityDeviceProfile profile;

    public InteractiveUtilityBlock(final BlockBehaviour.Properties properties, final UtilityDeviceProfile profile) {
        super(properties);
        this.profile = profile;
    }

    public UtilityDeviceProfile profile() {
        return profile;
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack stack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hit
    ) {
        return switch (profile) {
            case BLOOD_CRUCIBLE -> feedCrucible(level, player, stack);
            case COFFIN -> useCoffin(level, player, stack);
            case LEECH_CHEST -> sampleVictim(level, pos, player, stack);
            case MIRROR -> bindMirror(level, pos, player, stack);
            case SPIRIT_PORTAL -> travel(level, player, stack);
            case TRENT_EFFIGY -> awakenEnt(level, pos, player, stack);
            case WOLF_ALTAR -> advanceWolf(level, player, stack);
            case DISEASE, GARLIC_WARD, PIT_SOIL, SHADED_GLASS -> useWithoutItem(state, level, pos, player, hit);
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hit
    ) {
        return switch (profile) {
            case COFFIN -> useCoffin(level, player, ItemStack.EMPTY);
            case GARLIC_WARD -> inspectGarlic(level, player);
            case DISEASE -> show(player, "disease", UtilityDecision.failure("hazardous"));
            case PIT_SOIL -> show(player, "pit_soil", UtilityDecision.failure("unstable"));
            case SHADED_GLASS -> show(player, "shaded_glass", UtilityDecision.success(
                level.hasNeighborSignal(pos) ? "opaque" : "transparent"
            ));
            case BLOOD_CRUCIBLE -> show(player, "blood_crucible", UtilityDeviceRules.bloodCrucible(
                SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE, false
            ));
            case LEECH_CHEST -> show(player, "leech_chest", UtilityDeviceRules.leechChest(false, false));
            case MIRROR -> show(player, "mirror", UtilityDeviceRules.mirror(false));
            case SPIRIT_PORTAL -> show(player, "spirit_portal", UtilityDeviceRules.spiritPortal(false));
            case TRENT_EFFIGY -> show(player, "trent_effigy", UtilityDeviceRules.trentEffigy(false));
            case WOLF_ALTAR -> show(player, "wolf_altar", UtilityDeviceRules.wolfAltar(
                player.getItemBySlot(EquipmentSlot.HEAD).is(WarlockeryTags.Items.WOLF_ALTAR_HEADS),
                false,
                moonlit(level),
                SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF)
            ));
        };
    }

    @Override
    protected void onPlace(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final BlockState oldState,
        final boolean movedByPiston
    ) {
        if (profile == UtilityDeviceProfile.GARLIC_WARD && !level.isClientSide()) {
            level.scheduleTick(pos, this, WARD_INTERVAL);
        }
    }

    @Override
    protected void tick(
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final RandomSource random
    ) {
        if (profile != UtilityDeviceProfile.GARLIC_WARD) {
            return;
        }
        level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(6.0), InteractiveUtilityBlock::isVampire)
            .forEach(target -> {
                final Vec3 away = target.position().subtract(Vec3.atCenterOf(pos));
                final Vec3 push = away.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
                target.push(push.x * 0.55, 0.2, push.z * 0.55);
                target.igniteForSeconds(2.0F);
            });
        level.scheduleTick(pos, this, WARD_INTERVAL);
    }

    @Override
    protected void entityInside(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Entity entity,
        final InsideBlockEffectApplier effects,
        final boolean precise
    ) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (profile == UtilityDeviceProfile.DISEASE
            && UtilityDeviceRules.harmsDisease(true, entity.typeHolder().is(WarlockeryTags.EntityTypes.DISEASE_IMMUNE))) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 0));
        }
        if (profile == UtilityDeviceProfile.PIT_SOIL
            && UtilityDeviceRules.trapsInPit(true, entity.isShiftKeyDown())) {
            entity.makeStuckInBlock(state, new Vec3(0.35, 0.1, 0.35));
        }
    }

    @Override
    protected void neighborChanged(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Block neighbor,
        final net.minecraft.world.level.redstone.Orientation orientation,
        final boolean movedByPiston
    ) {
        if (profile != UtilityDeviceProfile.SHADED_GLASS || level.isClientSide()) {
            return;
        }
        final boolean active = level.hasNeighborSignal(pos);
        final Block target = com.kadamitas.warlockery.registry.ModBlocks.ALL.get(
            active ? "shadedglass_active" : "shadedglass"
        ).get();
        if (state.getBlock() != target) {
            level.setBlockAndUpdate(pos, target.defaultBlockState());
        }
    }

    private static InteractionResult feedCrucible(final Level level, final Player player, final ItemStack stack) {
        final boolean vampire = SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE;
        final boolean blood = stack.is(WarlockeryTags.Items.BLOOD_SOURCES)
            && (!(stack.getItem() instanceof com.kadamitas.warlockery.item.BloodGobletItem)
                || com.kadamitas.warlockery.item.BloodGobletState.isFull(stack));
        final UtilityDecision decision = UtilityDeviceRules.bloodCrucible(vampire, blood);
        if (decision.success() && !level.isClientSide()) {
            player.heal(8.0F);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            SupernaturalState.addReserve(player, 25);
            if (stack.getItem() instanceof com.kadamitas.warlockery.item.BloodGobletItem) {
                com.kadamitas.warlockery.item.BloodGobletState.setFull(stack, false);
            } else {
                consume(player, stack);
            }
        }
        return show(player, "blood_crucible", decision);
    }

    private static InteractionResult useCoffin(final Level level, final Player player, final ItemStack stack) {
        if (!stack.isEmpty() && stack.is(WarlockeryTags.Items.SYMPATHETIC_CONTAINERS)) {
            if (!level.isClientSide()) {
                SympatheticBinding.from(player).write(stack);
                if (stack.getItem() instanceof com.kadamitas.warlockery.item.BloodGobletItem) {
                    com.kadamitas.warlockery.item.BloodGobletState.setFull(stack, true);
                }
            }
            return show(player, "coffin", UtilityDecision.success("sampled"));
        }
        final boolean vampire = SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE;
        final long clock = level.getOverworldClockTime();
        final long daytime = Math.floorMod(clock, 24_000L);
        final UtilityDecision decision = UtilityDeviceRules.coffin(vampire, daytime < 13_000L || daytime > 23_000L);
        if (decision.success() && level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getCommands().performPrefixedCommand(
                serverLevel.getServer().createCommandSourceStack(), "time set night"
            );
            player.heal(6.0F);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        }
        return show(player, "coffin", decision);
    }

    private static InteractionResult sampleVictim(
        final Level level,
        final BlockPos pos,
        final Player player,
        final ItemStack stack
    ) {
        final boolean vial = stack.is(WarlockeryTags.Items.SYMPATHETIC_CONTAINERS);
        final LivingEntity victim = level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(pos).inflate(5.0),
            target -> target != player && target.isAlive()
        ).stream().min(Comparator.comparingDouble(target -> target.distanceToSqr(Vec3.atCenterOf(pos)))).orElse(null);
        final UtilityDecision decision = UtilityDeviceRules.leechChest(vial, victim != null);
        if (decision.success() && level instanceof ServerLevel serverLevel && victim != null) {
            SympatheticBinding.from(victim).write(stack);
            if (stack.getItem() instanceof com.kadamitas.warlockery.item.BloodGobletItem) {
                com.kadamitas.warlockery.item.BloodGobletState.setFull(stack, true);
            }
            victim.hurtServer(serverLevel, victim.damageSources().magic(), 1.0F);
        }
        return show(player, "leech_chest", decision);
    }

    private static InteractionResult bindMirror(
        final Level level,
        final BlockPos pos,
        final Player player,
        final ItemStack stack
    ) {
        final boolean mirror = stack.is(WarlockeryTags.Items.MIRROR_TOOLS);
        final UtilityDecision decision = UtilityDeviceRules.mirror(mirror);
        if (decision.success() && !level.isClientSide()) {
            new MirrorState(level.dimension().identifier(), pos.above()).write(stack);
        }
        return show(player, "mirror", decision);
    }

    private static InteractionResult travel(final Level level, final Player player, final ItemStack stack) {
        final var destination = WaystoneState.read(stack);
        final UtilityDecision decision = UtilityDeviceRules.spiritPortal(destination.isPresent());
        if (decision.success() && player instanceof ServerPlayer serverPlayer) {
            final var target = destination.orElseThrow();
            MagicPathRuntime.teleportToBoundPosition(serverPlayer, target.dimension(), target.position());
        }
        return show(player, "spirit_portal", decision);
    }

    private static InteractionResult awakenEnt(
        final Level level,
        final BlockPos pos,
        final Player player,
        final ItemStack stack
    ) {
        final boolean offering = stack.is(WarlockeryTags.Items.TRENT_OFFERINGS);
        final UtilityDecision decision = UtilityDeviceRules.trentEffigy(offering);
        if (decision.success() && level instanceof ServerLevel serverLevel) {
            ModEntities.ENT.get().spawn(serverLevel, pos.above(), EntitySpawnReason.EVENT);
            consume(player, stack);
        }
        return show(player, "trent_effigy", decision);
    }

    private static InteractionResult advanceWolf(final Level level, final Player player, final ItemStack stack) {
        final boolean head = player.getItemBySlot(EquipmentSlot.HEAD).is(WarlockeryTags.Items.WOLF_ALTAR_HEADS);
        final int current = SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF);
        final UtilityDecision decision = UtilityDeviceRules.wolfAltar(
            head,
            stack.is(WarlockeryTags.Items.WOLF_ALTAR_OFFERINGS),
            moonlit(level),
            current
        );
        if (decision.success() && !level.isClientSide()) {
            WolfAltarRuntime.completeTrial(player, stack);
        }
        return show(player, "wolf_altar", decision);
    }

    private static InteractionResult inspectGarlic(final Level level, final Player player) {
        if (isVampire(player)) {
            if (level instanceof ServerLevel serverLevel) {
                player.hurtServer(serverLevel, player.damageSources().onFire(), 4.0F);
                player.igniteForSeconds(4.0F);
            }
            return show(player, "garlic_ward", UtilityDeviceRules.garlicWard(true));
        }
        return show(player, "garlic_ward", UtilityDeviceRules.garlicWard(false));
    }

    private static boolean isVampire(final LivingEntity entity) {
        return entity.typeHolder().is(WarlockeryTags.EntityTypes.VAMPIRES)
            || entity instanceof Player player && SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE;
    }

    private static boolean moonlit(final Level level) {
        final long time = level.getOverworldClockTime() % 24_000L;
        return time >= 13_000L && time <= 23_000L
            && Math.floorMod(level.getOverworldClockTime() / 24_000L, 8L) == 0L;
    }

    private static void consume(final Player player, final ItemStack stack) {
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }

    private static InteractionResult show(
        final Player player,
        final String family,
        final UtilityDecision decision
    ) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(decision.messageKey(family))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return decision.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}
