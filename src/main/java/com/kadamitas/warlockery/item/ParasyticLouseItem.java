package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

public final class ParasyticLouseItem extends Item {
    private static final String EFFECT = "WarlockeryLouseEffect";
    private static final String DURATION = "WarlockeryLouseDuration";
    private static final String AMPLIFIER = "WarlockeryLouseAmplifier";

    public ParasyticLouseItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack louse = player.getItemInHand(hand);
        final ItemStack potion = player.getItemInHand(hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND);
        final Optional<MobEffectInstance> effect = firstEffect(potion);
        if (effect.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (storedEffect(louse).isPresent()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.louse.already_loaded"));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            storeEffect(louse, effect.orElseThrow());
            if (!player.hasInfiniteMaterials()) {
                potion.shrink(1);
                player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
            }
            player.sendOverlayMessage(Component.translatable("message.warlockery.louse.loaded"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        final Entity created = ModEntities.ALL.get("parasytic_louse").get()
            .create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (!(created instanceof Mob louse)) {
            return InteractionResult.FAIL;
        }
        final Vec3 target = Vec3.atCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        louse.snapTo(target.x, target.y, target.z);
        if (!level.noCollision(louse)) {
            louse.discard();
            return InteractionResult.FAIL;
        }
        storedEffect(context.getItemInHand()).ifPresent(effect -> CreatureBehaviorState.storeEffect(louse, effect));
        level.addFreshEntity(louse);
        if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    public static void writeFromCreature(final ItemStack stack, final Mob creature) {
        CreatureBehaviorState.storedEffect(creature).ifPresent(effect -> storeEffect(stack, new MobEffectInstance(
            BuiltInRegistries.MOB_EFFECT.get(effect.effectId()).orElseThrow(),
            effect.durationTicks(),
            effect.amplifier()
        )));
    }

    public static void handleDamage(final LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof ParasyticLouseItem)) {
                continue;
            }
            final Optional<CreatureBehaviorState.StoredEffect> stored = storedEffect(stack);
            if (stored.isEmpty()) {
                continue;
            }
            final LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
            final var effect = BuiltInRegistries.MOB_EFFECT.get(stored.orElseThrow().effectId());
            if (effect.isEmpty()) {
                clearEffect(stack);
                return;
            }
            final ParasyticLouseRules.InjectionTarget injectionTarget = ParasyticLouseRules.target(
                true,
                effect.orElseThrow().value().isBeneficial(),
                attacker != null
            );
            final LivingEntity target = switch (injectionTarget) {
                case WEARER -> player;
                case ATTACKER -> attacker;
                case NONE -> null;
            };
            if (target == null) {
                return;
            }
            target.addEffect(new MobEffectInstance(
                effect.orElseThrow(),
                stored.orElseThrow().durationTicks(),
                stored.orElseThrow().amplifier()
            ));
            clearEffect(stack);
            return;
        }
    }

    static void storeEffect(final ItemStack stack, final MobEffectInstance effect) {
        final Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putString(EFFECT, id.toString());
            data.putInt(DURATION, Math.max(20, effect.getDuration()));
            data.putInt(AMPLIFIER, Math.max(0, effect.getAmplifier()));
        });
    }

    static Optional<CreatureBehaviorState.StoredEffect> storedEffect(final ItemStack stack) {
        final var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        final Identifier id = Identifier.tryParse(data.getStringOr(EFFECT, ""));
        return id == null ? Optional.empty() : Optional.of(new CreatureBehaviorState.StoredEffect(
            id,
            Math.max(20, data.getIntOr(DURATION, 200)),
            Math.max(0, data.getIntOr(AMPLIFIER, 0))
        ));
    }

    private static void clearEffect(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.remove(EFFECT);
            data.remove(DURATION);
            data.remove(AMPLIFIER);
        });
    }

    private static Optional<MobEffectInstance> firstEffect(final ItemStack stack) {
        final PotionContents potion = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return StreamSupport.stream(potion.getAllEffects().spliterator(), false).findFirst();
    }
}
