package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LycanVillagerEntity extends Villager implements ArcaneCreature {
    public static final int SIGNATURE_OFFER_COUNT = 3;
    public static final int MAX_OFFERS = 64;
    private LycanVillagerState sentinelState;
    private static final String STATE_KEY = "WarlockeryLycanSentinel";
    private final Map<UUID, Long> tradeCooldowns = new HashMap<>();

    public LycanVillagerEntity(final EntityType<? extends Villager> type, final Level level) {
        super(type, level);
        sentinelState = LycanVillagerState.fresh(getUUID(), level.getGameTime());
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.05D, true) {
            @Override public boolean canUse() { return sentinelState != null
                && sentinelState.intent() == LycanVillagerRules.Intent.DEFEND && super.canUse(); }
            @Override public boolean canContinueToUse() { return sentinelState != null
                && sentinelState.intent() == LycanVillagerRules.Intent.DEFEND && super.canContinueToUse(); }
        });
    }

    static java.util.List<MerchantOffer> signatureOffers() {
        return java.util.List.of(new MerchantOffer(
            new ItemCost(Items.EMERALD, 4),
            new ItemStack(ModItems.ALL.get("ingredient_silverdust").get(), 2),
            12,
            2,
            0.05F
        ), new MerchantOffer(
            new ItemCost(Items.EMERALD, 7),
            new ItemStack(ModItems.ALL.get("ingredient_wolfsbane").get(), 3),
            8,
            4,
            0.05F
        ), new MerchantOffer(
            new ItemCost(ModItems.ALL.get("raw_silver").get(), 3),
            new ItemStack(Items.EMERALD, 1),
            12,
            3,
            0.05F
        ));
    }

    @Override
    public MerchantOffers getOffers() {
        final MerchantOffers offers = super.getOffers();
        reconcileSignatureOffers(offers);
        return offers;
    }

    static void reconcileSignatureOffers(final MerchantOffers offers) {
        for (MerchantOffer signature : signatureOffers()) {
            boolean found = false;
            for (int index = 0; index < offers.size(); index++) {
                final MerchantOffer existing = offers.get(index);
                if (sameOffer(existing, signature)) {
                    if (!found) found = true;
                    else offers.remove(index--);
                }
            }
            if (!found && offers.size() < MAX_OFFERS) offers.add(signature);
        }
    }

    static boolean sameOffer(final MerchantOffer left, final MerchantOffer right) {
        return left.getItemCostA().item().equals(right.getItemCostA().item())
            && left.getItemCostA().count() == right.getItemCostA().count()
            && left.getItemCostB().map(cost -> cost.item()).equals(right.getItemCostB().map(cost -> cost.item()))
            && left.getItemCostB().map(cost -> cost.count()).equals(right.getItemCostB().map(cost -> cost.count()))
            && ItemStack.isSameItemSameComponents(left.getResult(), right.getResult())
            && left.getResult().getCount() == right.getResult().getCount();
    }

    public static boolean canTrade(final SupernaturalForm form) {
        return form == SupernaturalForm.WEREWOLF;
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.LYCAN_VILLAGER;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        if (sentinelState.intent() != LycanVillagerRules.Intent.ROUTINE) {
            getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            getBrain().eraseMemory(MemoryModuleType.PATH);
            getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
        LycanVillagerRuntime.tick(this, level);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final float before = getHealth() + getAbsorptionAmount();
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && getHealth() + getAbsorptionAmount() < before && source.getEntity() instanceof LivingEntity attacker
            && LycanVillagerRuntime.admitsTarget(this, attacker)) {
            final long now = level.getGameTime();
            LycanVillagerRuntime.rememberDirectEvidence(this, now);
            final boolean engagedSameAggressor = sentinelState.recentAggressor()
                .filter(attacker.getUUID()::equals).isPresent()
                && (sentinelState.intent() == LycanVillagerRules.Intent.WARNING
                    || sentinelState.intent() == LycanVillagerRules.Intent.INTERCEPT
                    || sentinelState.intent() == LycanVillagerRules.Intent.DEFEND);
            if (engagedSameAggressor) {
                sentinelState = sentinelState.withCombat(attacker.getUUID(),
                    sentinelState.protectedResident().orElse(null), sentinelState.intent(),
                    sentinelState.warningDeadline(), now + LycanVillagerRules.PURSUIT_TICKS);
            } else {
                LycanVillagerRuntime.beginWarning(this, level, attacker);
                sentinelState = sentinelState.withCombat(attacker.getUUID(), null, LycanVillagerRules.Intent.WARNING,
                    now + LycanVillagerRules.WARNING_TICKS,
                    now + LycanVillagerRules.PURSUIT_TICKS);
            }
        }
        return hurt;
    }

    @Override
    public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (!canTrade(SupernaturalState.getForm(player))) {
            if (!level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.warlockery.lycan_villager.werewolf_only"));
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void notifyTrade(final MerchantOffer offer) {
        final Player trader = getTradingPlayer();
        super.notifyTrade(offer);
        final long now = level().getGameTime();
        tradeCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > LycanVillagerRules.FAMILIARITY_DECAY_TICKS);
        if (trader != null && canTrade(SupernaturalState.getForm(trader)) && offerIsCurrent(offer)
            && now - tradeCooldowns.getOrDefault(trader.getUUID(), Long.MIN_VALUE / 2)
                >= LycanVillagerRules.TRADE_FAMILIARITY_COOLDOWN_TICKS) {
            sentinelState = sentinelState.observe(trader.getUUID(), LycanVillagerRules.RelationshipSource.PLAYER,
                LycanVillagerRules.TRADE_FAMILIARITY_POINTS, now);
            tradeCooldowns.put(trader.getUUID(), now);
            if (tradeCooldowns.size() > LycanVillagerRules.TRADE_COOLDOWN_CAP) {
                tradeCooldowns.entrySet().stream().min(java.util.Map.Entry.comparingByValue())
                    .ifPresent(entry -> tradeCooldowns.remove(entry.getKey()));
            }
        }
        reconcileSignatureOffers(super.getOffers());
    }

    private boolean offerIsCurrent(final MerchantOffer offer) {
        return super.getOffers().stream().anyMatch(existing -> existing == offer);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        final CompoundTag tag = sentinelState.write();
        final CompoundTag cooldowns = new CompoundTag();
        tradeCooldowns.forEach((id, time) -> cooldowns.putLong(id.toString(), time));
        tag.put("TradeCooldowns", cooldowns);
        output.store(STATE_KEY, CompoundTag.CODEC, tag);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final long now = level().getGameTime();
        tradeCooldowns.clear();
        input.read(STATE_KEY, CompoundTag.CODEC).ifPresentOrElse(tag -> {
            sentinelState = LycanVillagerState.read(tag, getUUID(), now);
            final CompoundTag cooldowns = tag.getCompoundOrEmpty("TradeCooldowns");
            for (final String key : cooldowns.keySet()) {
                try {
                    final long time = cooldowns.getLongOr(key, Long.MIN_VALUE);
                    if (time <= now && now - time <= LycanVillagerRules.FAMILIARITY_DECAY_TICKS) {
                        tradeCooldowns.put(UUID.fromString(key), time);
                    }
                } catch (RuntimeException ignored) { }
            }
        }, () -> sentinelState = LycanVillagerState.fresh(getUUID(), now));
        reconcileSignatureOffers(super.getOffers());
        LycanVillagerRuntime.cancel(this);
    }

    @Override
    public void stopTrading() {
        super.stopTrading();
        LycanVillagerRuntime.cancel(this);
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!level().isClientSide() && sentinelState != null) LycanVillagerRuntime.cancel(this);
        super.remove(reason);
    }

    @Override
    public void setVillagerData(final net.minecraft.world.entity.npc.villager.VillagerData data) {
        if (!level().isClientSide() && sentinelState != null) LycanVillagerRuntime.cancel(this);
        super.setVillagerData(data);
    }

    LycanVillagerState sentinelState() {
        return sentinelState;
    }

    void setSentinelState(final LycanVillagerState value) { sentinelState = value; }
}
