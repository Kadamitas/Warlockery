package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.block.FetishRuntime;
import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.registry.ModSounds;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

public final class DollItem extends Item {
    private static final String HEX_ACTION = "WarlockeryHexAction";
    private static final ThreadLocal<Boolean> TRANSFERRING_DAMAGE = ThreadLocal.withInitial(() -> false);
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    );

    private final DollKind kind;

    public DollItem(final Properties properties, final DollKind kind) {
        super(properties);
        this.kind = kind;
    }

    public DollKind kind() {
        return kind;
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!player.level().isClientSide()) {
            bind(stack, player, target);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        final DollAbility ability = kind.definition().ability();
        if (DollRules.canApplyToSelf(ability)) {
            if (!level.isClientSide()) {
                bind(stack, player, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (!(ability instanceof DollAbility.ActiveHex)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            final DollHexAction next = hexAction(stack).next();
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putString(HEX_ACTION, next.id()));
            updateLore(stack, Component.literal(boundName(stack)));
            player.sendSystemMessage(Component.translatable("message.warlockery.doll.hex_mode", modeName(next)));
            return InteractionResult.SUCCESS;
        }
        final LivingEntity target = boundLiving(stack, serverLevel);
        if (target == null || target == player) {
            player.sendSystemMessage(Component.translatable("message.warlockery.doll.no_remote_target"));
            return InteractionResult.FAIL;
        }
        final DollHexAction action = hexAction(stack);
        final boolean blocked = FetishRuntime.protects(target)
            || EquipmentSetEffects.tryBlockHex(target)
            || tryBlockHex(target, player);
        if (!blocked) {
            action.apply(serverPlayer, target);
        } else {
            player.sendSystemMessage(Component.translatable("message.warlockery.doll.remote_hex_blocked"));
        }
        wear(stack, serverLevel, serverPlayer, 1);
        ModNetwork.notifyDollActivation(serverPlayer, kind.id(), 60);
        if (target instanceof ServerPlayer targetPlayer) {
            ModNetwork.notifyDollActivation(targetPlayer, kind.id(), 60);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return isBound(stack) || super.isFoil(stack);
    }

    @Override
    public void inventoryTick(
        final ItemStack stack,
        final ServerLevel level,
        final Entity owner,
        final @Nullable EquipmentSlot slot
    ) {
        if (owner instanceof ServerPlayer player
            && DollMendingSchedule.isMendingTick(level.getServer().getTickCount())) {
            tryMendBoundEquipment(stack, level, player);
        }
    }

    public static boolean tryMendBoundEquipment(
        final ItemStack doll,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        if (!(doll.getItem() instanceof DollItem item)
            || !(item.kind.definition().ability() instanceof DollAbility.Mending mending)
            || !isBoundTo(doll, player)) {
            return false;
        }
        final Optional<ItemStack> target = repairTarget(player, mending.target());
        if (target.isEmpty()) {
            return false;
        }
        final int serverTick = level.getServer().getTickCount();
        if (!DollMendingSchedule.forServer(level.getServer()).claim(player.getUUID(), mending.target(), serverTick)) {
            return false;
        }
        repairUsingDollCharge(player, doll, target.orElseThrow());
        return true;
    }

    public static void handleDamage(final LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F) {
            return;
        }
        if (!TRANSFERRING_DAMAGE.get()) {
            transferLinkedDamage(player, event);
        }
        if (event.getAmount() < player.getHealth()
            || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        findLethalGuard(player, event.getSource()).ifPresent(stack -> {
            final DollKind kind = ((DollItem) stack.getItem()).kind;
            event.setAmount(0.0F);
            player.setHealth(1.0F);
            DeathProtection.TOTEM_OF_UNDYING.applyEffects(stack.copy(), player);
            lethalBehavior(kind).recover(player, event.getSource());
            activate(player, stack, kind);
        });
    }

    public static boolean tryBlockHex(final LivingEntity target) {
        return tryBlockHex(target, null);
    }

    public static boolean tryBlockHex(
        final LivingEntity target,
        final @Nullable LivingEntity attacker
    ) {
        if (!(target instanceof ServerPlayer player)) {
            return false;
        }
        final Optional<ItemStack> guard = findBoundDoll(
            player,
            item -> item.kind.definition().ability() instanceof DollAbility.HexGuard
        );
        guard.ifPresent(stack -> {
            wear(stack, (ServerLevel) player.level(), player, 1);
            signalActivation(player, DollKind.HEX_GUARD);
            final HexGuardRules.Resolution resolution = HexGuardRules.resolve(
                true,
                attacker != null,
                attacker == target
            );
            if (resolution.retaliates()) {
                retaliate(player, attacker);
            }
        });
        return guard.isPresent();
    }

    public static CorruptionResult corruptProtectiveDolls(
        final ServerPlayer player,
        final int maximumTargets
    ) {
        if (maximumTargets < 0) {
            throw new IllegalArgumentException("Maximum corruption targets must be nonnegative");
        }
        final Optional<ItemStack> dollGuard = findBoundDoll(
            player,
            item -> item.kind.definition().ability() instanceof DollAbility.DollGuard
        );
        final List<ItemStack> protectionDolls = boundDolls(player)
            .filter(stack -> stack.getItem() instanceof DollItem item && item.isCorruptibleProtection())
            .limit(maximumTargets)
            .toList();
        final DollCorruptionRules.CorruptionPlan plan = DollCorruptionRules.plan(
            dollGuard.isPresent(),
            protectionDolls.size(),
            maximumTargets
        );
        if (plan.intercepted()) {
            final ItemStack guard = dollGuard.orElseThrow();
            wear(guard, (ServerLevel) player.level(), player, 1);
            signalActivation(player, DollKind.DOLL_GUARD);
            player.sendSystemMessage(Component.translatable("message.warlockery.doll.corruption_blocked"));
            return new CorruptionResult(CorruptionOutcome.INTERCEPTED, 0);
        }
        protectionDolls.stream().limit(plan.dollsToDamage()).forEach(stack -> wear(
            stack,
            (ServerLevel) player.level(),
            player,
            DollCorruptionRules.destructionWear(stack.isDamageableItem(), stack.getMaxDamage())
        ));
        if (plan.dollsToDamage() == 0) {
            player.sendSystemMessage(Component.translatable("message.warlockery.doll.nothing_to_corrupt"));
            return new CorruptionResult(CorruptionOutcome.NO_TARGETS, 0);
        }
        player.sendSystemMessage(Component.translatable(
            "message.warlockery.doll.protections_corrupted",
            plan.dollsToDamage()
        ));
        return new CorruptionResult(CorruptionOutcome.DAMAGED, plan.dollsToDamage());
    }

    public static boolean isBound(final ItemStack stack) {
        return SympatheticBinding.read(stack).isPresent();
    }

    public static boolean isBoundTo(final ItemStack stack, final Player player) {
        return SympatheticBinding.read(stack).filter(binding -> binding.targets(player)).isPresent();
    }

    public static DollHexAction hexAction(final ItemStack stack) {
        final String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getStringOr(HEX_ACTION, DollHexAction.PRICK.id());
        return DollHexAction.fromId(id);
    }

    private static Optional<ItemStack> findLethalGuard(final ServerPlayer player, final DamageSource source) {
        return findBoundDoll(player, item -> item.kind != DollKind.DEATH_GUARD && item.protectsAgainst(source))
            .or(() -> findBoundDoll(player, item -> item.kind == DollKind.DEATH_GUARD));
    }

    private boolean protectsAgainst(final DamageSource source) {
        return kind.definition().ability() instanceof DollAbility.LethalProtection protection
            && protection.behavior().protectsAgainst(source);
    }

    private static LethalDollBehavior lethalBehavior(final DollKind kind) {
        return ((DollAbility.LethalProtection) kind.definition().ability()).behavior();
    }

    private static Optional<ItemStack> findBoundDoll(
        final ServerPlayer player,
        final Predicate<DollItem> predicate
    ) {
        return boundDolls(player)
            .filter(stack -> stack.getItem() instanceof DollItem item && predicate.test(item))
            .findFirst();
    }

    private static java.util.stream.Stream<ItemStack> boundDolls(final ServerPlayer player) {
        final var inventory = player.getInventory();
        final var carried = IntStream.range(0, inventory.getContainerSize())
            .mapToObj(inventory::getItem)
            .filter(stack -> stack.getItem() instanceof DollItem);
        return java.util.stream.Stream.concat(
            carried,
            DollShelfBlockEntity.loadedDolls(((ServerLevel) player.level()).getServer())
        )
            .filter(stack -> isBoundTo(stack, player));
    }

    private boolean isCorruptibleProtection() {
        return kind.definition().ability() instanceof DollAbility.LethalProtection
            || kind.definition().ability() instanceof DollAbility.HexGuard;
    }

    private static void transferLinkedDamage(final ServerPlayer player, final LivingDamageEvent event) {
        findBoundDoll(
            player,
            item -> item.kind.definition().ability() instanceof DollAbility.DamageLink
        ).ifPresent(stack -> {
            final ServerPlayer target = boundPlayer(stack, (ServerLevel) player.level());
            if (target == null || target == player) {
                return;
            }
            final boolean guarded = EquipmentSetEffects.tryBlockHex(target) || tryBlockHex(target, player);
            final VampiricDollRules.TransferPlan plan = VampiricDollRules.plan(
                event.getAmount(),
                true,
                guarded
            );
            if (plan.victimDamage() <= 0.0F) {
                return;
            }
            event.setAmount(plan.protectedDamage());
            TRANSFERRING_DAMAGE.set(true);
            try {
                target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), plan.victimDamage());
            } finally {
                TRANSFERRING_DAMAGE.set(false);
            }
            wear(stack, (ServerLevel) player.level(), player, 1);
            signalActivation(player, DollKind.BLOOD_LINK);
            signalActivation(target, DollKind.BLOOD_LINK);
        });
    }

    private static boolean needsRepair(final ItemStack stack) {
        return stack.isDamageableItem() && DollRules.needsRepair(stack.getDamageValue(), stack.getMaxDamage());
    }

    private static Optional<ItemStack> repairTarget(
        final ServerPlayer player,
        final DollAbility.RepairTarget target
    ) {
        return switch (target) {
            case HELD -> Stream.of(player.getMainHandItem(), player.getOffhandItem())
                .filter(DollItem::needsRepair)
                .findFirst();
            case WORN -> ARMOR_SLOTS.stream()
                .map(player::getItemBySlot)
                .filter(DollItem::needsRepair)
                .findFirst();
        };
    }

    private static void repairUsingDollCharge(
        final ServerPlayer player,
        final ItemStack doll,
        final ItemStack target
    ) {
        target.setDamageValue(DollRules.repairedDamage(target.getDamageValue()));
        wear(doll, (ServerLevel) player.level(), player, 1);
        ModNetwork.notifyDollActivation(player, ((DollItem) doll.getItem()).kind.id(), 30);
    }

    private static void activate(final ServerPlayer player, final ItemStack activated, final DollKind kind) {
        wear(activated, (ServerLevel) player.level(), player, 1);
        signalActivation(player, kind);
    }

    private static void signalActivation(final ServerPlayer player, final DollKind kind) {
        final ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), ModSounds.DOLL_ACTIVATE.get(), SoundSource.PLAYERS, 0.7F, 1.1F);
        ModNetwork.notifyDollActivation(player, kind.id(), 80);
    }

    private static void retaliate(final ServerPlayer protectedPlayer, final LivingEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level) || !attacker.isAlive()) {
            return;
        }
        final LightningBolt lightning = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (lightning == null) {
            return;
        }
        lightning.snapTo(attacker.getX(), attacker.getY(), attacker.getZ());
        lightning.setCause(protectedPlayer);
        level.addFreshEntity(lightning);
    }

    private static void wear(
        final ItemStack stack,
        final ServerLevel level,
        final ServerPlayer player,
        final int amount
    ) {
        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(amount, level, player, _ -> { });
        } else {
            stack.shrink(amount);
        }
        DollShelfBlockEntity.markContainingShelfChanged(stack, level.getServer());
    }

    private static @Nullable ServerPlayer boundPlayer(final ItemStack stack, final ServerLevel level) {
        return SympatheticBinding.read(stack)
            .flatMap(binding -> binding.resolve(level.getServer()))
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .orElse(null);
    }

    private static @Nullable LivingEntity boundLiving(final ItemStack stack, final ServerLevel level) {
        return SympatheticBinding.read(stack).flatMap(binding -> binding.resolve(level.getServer())).orElse(null);
    }

    private void updateLore(final ItemStack stack, final Component targetName) {
        final List<Component> lines = new java.util.ArrayList<>(List.of(
            Component.translatable("tooltip.warlockery.doll.bound", targetName),
            Component.translatable(kind.descriptionKey())
        ));
        if (kind.definition().ability() instanceof DollAbility.ActiveHex) {
            lines.add(Component.translatable("tooltip.warlockery.doll.hex_mode", modeName(hexAction(stack))));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private void bind(final ItemStack stack, final Player player, final LivingEntity target) {
        SympatheticBinding.from(target).write(stack);
        updateLore(stack, target.getName());
        player.sendSystemMessage(Component.translatable("message.warlockery.doll.bound", target.getDisplayName()));
    }

    private static Component modeName(final DollHexAction action) {
        return Component.translatable("doll_action.warlockery." + action.id());
    }

    private static String boundName(final ItemStack stack) {
        return SympatheticBinding.read(stack).map(SympatheticBinding::targetName).orElse("?");
    }

    public enum CorruptionOutcome {
        INTERCEPTED,
        DAMAGED,
        NO_TARGETS
    }

    public record CorruptionResult(CorruptionOutcome outcome, int affectedDolls) {
        public CorruptionUiSignal uiSignal() {
            return switch (outcome) {
                case INTERCEPTED -> CorruptionUiSignal.DOLL_GUARD_ACTIVATION;
                case DAMAGED -> CorruptionUiSignal.INVENTORY_CHANGE;
                case NO_TARGETS -> CorruptionUiSignal.NONE;
            };
        }
    }

    public enum CorruptionUiSignal {
        DOLL_GUARD_ACTIVATION,
        INVENTORY_CHANGE,
        NONE
    }
}
