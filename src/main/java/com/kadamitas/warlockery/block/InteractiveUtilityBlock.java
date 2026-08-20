package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.item.MirrorState;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.item.UtilityDecision;
import com.kadamitas.warlockery.item.WaystoneState;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.ManifestationRuntime;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.stats.Stats;
import org.jspecify.annotations.Nullable;

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
            case COFFIN -> useCoffin(level, pos, player, stack);
            case LEECH_CHEST -> sampleVictim(level, pos, player, stack);
            case MIRROR -> bindMirror(level, pos, player, stack);
            case SPIRIT_PORTAL -> travel(level, pos, player, stack);
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
            case COFFIN -> useCoffin(level, pos, player, ItemStack.EMPTY);
            case GARLIC_WARD -> inspectGarlic(level, player);
            case DISEASE -> show(player, "disease", UtilityDecision.failure("hazardous"));
            case PIT_SOIL -> show(player, "pit_soil", UtilityDecision.failure("unstable"));
            case SHADED_GLASS -> show(player, "shaded_glass", UtilityDecision.success(
                level.hasNeighborSignal(pos) ? "opaque" : "transparent"
            ));
            case BLOOD_CRUCIBLE -> show(player, "blood_crucible", UtilityDeviceRules.bloodCrucible(
                SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE, false
            ));
            case LEECH_CHEST -> rememberLeechVisitor(level, pos, player);
            case MIRROR -> useMirror(level, pos, player);
            case SPIRIT_PORTAL -> useSpiritPortal(pos, player);
            case TRENT_EFFIGY -> show(player, "trent_effigy", UtilityDeviceRules.trentEffigy(false));
            case WOLF_ALTAR -> advanceWolf(level, player, ItemStack.EMPTY);
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
        if (profile == UtilityDeviceProfile.SPIRIT_PORTAL
            && entity instanceof ServerPlayer player
            && !player.isOnPortalCooldown()
            && ManifestationRuntime.canEnterPortal(player)) {
            ManifestationRuntime.enterPortal(player, pos);
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
        if (!level.isClientSide()
            && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime
                .chargeBloodPower(serverPlayer, stack)) {
            return InteractionResult.SUCCESS;
        }
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

    private static InteractionResult useCoffin(
        final Level level,
        final BlockPos pos,
        final Player player,
        final ItemStack stack
    ) {
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
        if (decision.success() && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.setRespawnPosition(new ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                    serverLevel.dimension(),
                    pos,
                    player.getYRot(),
                    player.getXRot()
                ),
                false
            ), true);
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
        final LivingEntity nearby = level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(pos).inflate(5.0),
            target -> target != player && target.isAlive()
        ).stream().min(Comparator.comparingDouble(target -> target.distanceToSqr(Vec3.atCenterOf(pos)))).orElse(null);
        final Optional<SympatheticBinding> remembered;
        if (level instanceof ServerLevel serverLevel) {
            final LeechChestMemory memory = LeechChestMemory.get(serverLevel);
            memory.remember(pos, SympatheticBinding.from(player));
            if (nearby != null) {
                memory.remember(pos, SympatheticBinding.from(nearby));
            }
            remembered = nearby == null
                ? memory.mostRecentOther(pos, player.getUUID())
                : Optional.of(SympatheticBinding.from(nearby));
        } else {
            remembered = Optional.empty();
        }
        final UtilityDecision decision = UtilityDeviceRules.leechChest(vial, remembered.isPresent());
        if (decision.success() && level instanceof ServerLevel serverLevel) {
            remembered.orElseThrow().write(stack);
            if (stack.getItem() instanceof com.kadamitas.warlockery.item.BloodGobletItem) {
                com.kadamitas.warlockery.item.BloodGobletState.setFull(stack, true);
            }
            if (nearby != null) {
                nearby.hurtServer(serverLevel, nearby.damageSources().magic(), 1.0F);
            }
        }
        return show(player, "leech_chest", decision);
    }

    private static InteractionResult rememberLeechVisitor(
        final Level level,
        final BlockPos pos,
        final Player player
    ) {
        if (level instanceof ServerLevel serverLevel) {
            LeechChestMemory.get(serverLevel).remember(pos, SympatheticBinding.from(player));
        }
        return show(player, "leech_chest", UtilityDecision.success("remembered"));
    }

    @Override
    public void setPlacedBy(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final LivingEntity placer,
        final ItemStack stack
    ) {
        if (profile != UtilityDeviceProfile.LEECH_CHEST || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final LeechChestMemory memory = LeechChestMemory.get(serverLevel);
        memory.clear(pos);
        LeechChestMemory.readPortable(stack).forEach(binding -> memory.remember(pos, binding));
    }

    @Override
    public void playerDestroy(
        final Level level,
        final Player player,
        final BlockPos pos,
        final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final ItemStack destroyedWith
    ) {
        if (profile != UtilityDeviceProfile.LEECH_CHEST || !(level instanceof ServerLevel serverLevel)) {
            super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
            return;
        }
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        final LeechChestMemory memory = LeechChestMemory.get(serverLevel);
        if (!player.hasInfiniteMaterials()) {
            final ItemStack dropped = new ItemStack(this);
            LeechChestMemory.writePortable(dropped, memory.samples(pos));
            Block.popResource(level, pos, dropped);
        }
        memory.clear(pos);
    }

    private static InteractionResult bindMirror(
        final Level level,
        final BlockPos pos,
        final Player player,
        final ItemStack stack
    ) {
        if (stack.is(ModItems.ALL.get("ingredient_quartz_sphere").get())) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.SUCCESS;
            }
            if (!AltarPowerNetwork.consume(serverLevel, pos, MagicMirrorRules.REPLICATION_POWER)) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.mirror.needs_power")
                    .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            consume(player, stack);
            final ItemStack charge = new ItemStack(ModItems.ALL.get("replication_charge").get());
            if (!player.getInventory().add(charge)) {
                Block.popResource(level, pos, charge);
            }
            player.sendOverlayMessage(Component.translatable("message.warlockery.mirror.replication_captured")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            return InteractionResult.SUCCESS;
        }
        final Optional<SympatheticBinding> binding = SympatheticBinding.read(stack);
        if (binding.isPresent()) {
            if (!level.isClientSide()) {
                final SympatheticBinding target = binding.orElseThrow();
                if (target.targetId().equals(player.getUUID())) {
                    player.getPersistentData().remove("WarlockeryMirrorMasquerade");
                    player.setCustomName(null);
                    player.sendOverlayMessage(Component.translatable("message.warlockery.mirror.masquerade_restored")
                        .withStyle(ChatFormatting.GRAY));
                } else {
                    player.getPersistentData().putString("WarlockeryMirrorMasquerade", target.targetName());
                    player.setCustomName(Component.literal(target.targetName()));
                    player.sendOverlayMessage(Component.translatable(
                        "message.warlockery.mirror.masquerade_applied",
                        target.targetName()
                    )
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                consume(player, stack);
            }
            return InteractionResult.SUCCESS;
        }
        final boolean mirror = stack.is(WarlockeryTags.Items.MIRROR_TOOLS);
        final UtilityDecision decision = UtilityDeviceRules.mirror(mirror);
        if (decision.success() && !level.isClientSide()) {
            new MirrorState(level.dimension().identifier(), pos.above()).write(stack);
        }
        return show(player, "mirror", decision);
    }

    private static InteractionResult useMirror(final Level level, final BlockPos pos, final Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        final List<String> visitors = MagicMirrorMemory.get(serverLevel).record(pos, player.getName().getString());
        if (player.isShiftKeyDown()) {
            final Optional<BlockPos> paired = pairedMirror(serverLevel, pos);
            if (paired.isPresent()) {
                final BlockPos destination = paired.orElseThrow().above();
                serverPlayer.teleportTo(
                    destination.getX() + 0.5,
                    destination.getY(),
                    destination.getZ() + 0.5
                );
                player.sendOverlayMessage(Component.translatable("message.warlockery.mirror.paired_travel")
                    .withStyle(ChatFormatting.AQUA));
                return InteractionResult.SUCCESS;
            }
            return summonReflection(serverLevel, pos, serverPlayer);
        }
        final LivingEntity fairest = serverLevel.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(pos).inflate(32.0),
            LivingEntity::isAlive
        ).stream().max(Comparator.comparingDouble(entity -> MagicMirrorRules.fairnessScore(
            entity.getHealth(),
            entity.getMaxHealth(),
            entity.getAbsorptionAmount(),
            entity.getArmorValue()
        ))).orElse(player);
        final String direction = MagicMirrorRules.direction(fairest.getX() - pos.getX(), fairest.getZ() - pos.getZ());
        player.sendSystemMessage(Component.translatable(
            "message.warlockery.mirror.fairest",
            fairest.getName(),
            Component.translatable("message.warlockery.spirit_locator.direction." + direction)
        ).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (visitors.size() > 1) {
            player.sendSystemMessage(Component.translatable(
                "message.warlockery.mirror.remembers",
                String.join(", ", visitors.subList(1, visitors.size()))
            )
                .withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    private static Optional<BlockPos> pairedMirror(final ServerLevel level, final BlockPos origin) {
        return BlockPos.betweenClosedStream(
            origin.offset(-MagicMirrorRules.MAX_PAIR_DISTANCE, -MagicMirrorRules.MAX_PAIR_DISTANCE, -MagicMirrorRules.MAX_PAIR_DISTANCE),
            origin.offset(MagicMirrorRules.MAX_PAIR_DISTANCE, MagicMirrorRules.MAX_PAIR_DISTANCE, MagicMirrorRules.MAX_PAIR_DISTANCE)
        ).filter(pos -> !pos.equals(origin) && level.isLoaded(pos))
            .filter(pos -> level.getBlockState(pos).getBlock() instanceof InteractiveUtilityBlock mirror
                && mirror.profile() == UtilityDeviceProfile.MIRROR)
            .filter(pos -> MagicMirrorRules.canPair(
                Math.sqrt(pos.distSqr(origin)),
                true,
                level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty()
            ))
            .min(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
            .map(BlockPos::immutable);
    }

    private static InteractionResult summonReflection(
        final ServerLevel level,
        final BlockPos pos,
        final ServerPlayer player
    ) {
        final Entity created = ModEntities.ALL.get("glass_doppelganger").get().create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof net.minecraft.world.entity.Mob reflection)) {
            return InteractionResult.FAIL;
        }
        reflection.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        if (!level.noCollision(reflection)) {
            return InteractionResult.FAIL;
        }
        reflection.setCustomName(Component.translatable(
            "entity.warlockery.reflection_of",
            player.getName()
        ));
        reflection.setTarget(player);
        reflection.getPersistentData().putString(
            "WarlockeryReflectedTarget", player.getStringUUID()
        );
        reflection.setPersistenceRequired();
        level.addFreshEntity(reflection);
        player.sendOverlayMessage(Component.translatable("message.warlockery.mirror.reflection_emerged")
            .withStyle(ChatFormatting.RED));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult travel(
        final Level level,
        final BlockPos pos,
        final Player player,
        final ItemStack stack
    ) {
        if (player instanceof ServerPlayer serverPlayer && SpiritWorldRuntime.isDreaming(serverPlayer)) {
            return useSpiritPortal(pos, serverPlayer);
        }
        final var destination = WaystoneState.read(stack);
        final UtilityDecision decision = UtilityDeviceRules.spiritPortal(destination.isPresent());
        if (decision.success() && player instanceof ServerPlayer serverPlayer) {
            final var target = destination.orElseThrow();
            MagicPathRuntime.teleportToBoundPosition(serverPlayer, target.dimension(), target.position());
        }
        return show(player, "spirit_portal", decision);
    }

    private static InteractionResult useSpiritPortal(final BlockPos pos, final Player player) {
        if (player instanceof ServerPlayer serverPlayer && SpiritWorldRuntime.isDreaming(serverPlayer)) {
            final boolean success = ManifestationRuntime.isActive(serverPlayer)
                ? ManifestationRuntime.returnToSpiritWorld(serverPlayer, ManifestationRuntime.ReturnCause.PORTAL)
                : ManifestationRuntime.enterPortal(serverPlayer, pos);
            return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return show(player, "spirit_portal", UtilityDeviceRules.spiritPortal(false));
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
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final var result = com.kadamitas.warlockery.transformation.SupernaturalAdvancement.useWolfAltar(
            (net.minecraft.server.level.ServerPlayer) player,
            stack
        );
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(result.messageKey())
            .withStyle(result.accepted() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return result.accepted() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
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
