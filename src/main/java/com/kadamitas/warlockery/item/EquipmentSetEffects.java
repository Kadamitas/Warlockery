package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class EquipmentSetEffects {
    private static final List<EquipmentSlot> ARMOR = List.of(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    );

    private EquipmentSetEffects() {
    }

    public static void tick(final Player player) {
        if (!(player.level() instanceof ServerLevel level) || player.tickCount % 20 != 0) {
            return;
        }
        if (wearsDeathSet(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
        }
        if (player.getItemBySlot(EquipmentSlot.FEET).is(WarlockeryTags.Items.POISON_REDIRECTING_FOOTWEAR)
            && player.hasEffect(MobEffects.POISON)) {
            player.removeEffect(MobEffects.POISON);
            growNearbyPlants(level, player.blockPosition());
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.BARK_ARMOR)
            && level.getBlockState(player.blockPosition().below()).is(WarlockeryTags.Blocks.LIVING_GROUND)) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 40, 1, false, false, true));
        }
        final ItemStack legwear = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legwear.getItem() instanceof BitingBeltItem) {
            BitingBeltItem.applyHelpful(legwear, player);
        }
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(WarlockeryTags.Items.NECROMANCER_GARB)) {
            level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(16), mob ->
                mob.getTarget() == player && mob.typeHolder().is(EntityTypeTags.UNDEAD)
            ).forEach(mob -> mob.setTarget(null));
        }
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(WarlockeryTags.Items.SOUND_DAMPENING_ARMOR)) {
            player.removeEffect(MobEffects.NAUSEA);
            player.removeEffect(MobEffects.BLINDNESS);
        }
        if (ARMOR.stream().map(player::getItemBySlot).anyMatch(stack -> stack.is(WarlockeryTags.Items.BREWING_GARB))) {
            level.getEntitiesOfClass(Creeper.class, new AABB(player.blockPosition()).inflate(12), creeper ->
                creeper.getTarget() == player
            ).forEach(creeper -> creeper.setTarget(null));
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.ARCHERY_ARMOR)
            && !inventoryContains(player, Items.ARROW)) {
            player.getInventory().add(new ItemStack(Items.ARROW));
        }
        applyTwistingBand(player, level);
    }

    public static void handleDamage(final LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        applyIncomingProtection(event);
        applyOffensiveEquipment(event);
    }

    public static boolean tryBlockHex(final LivingEntity target) {
        if (!(target instanceof Player player) || !HunterArmorRules.blocksHex(
            wearsFullSet(player, WarlockeryTags.Items.WARLOCK_HUNTER_ARMOR)
                || wearsFullSet(player, WarlockeryTags.Items.SILVERED_HUNTER_ARMOR)
                || wearsFullSet(player, WarlockeryTags.Items.DAWN_HUNTER_ARMOR)
        )) {
            return false;
        }
        ARMOR.forEach(slot -> player.getItemBySlot(slot).hurtAndBreak(1, player, slot));
        return true;
    }

    public static List<ItemStack> enhanceMachineOutputs(
        final ServerLevel level,
        final BlockPos position,
        final String recipeType,
        final List<ItemStack> outputs
    ) {
        final boolean brewOutput = outputs.stream().anyMatch(stack -> stack.is(WarlockeryTags.Items.BREWS));
        final List<Player> nearby = level.getEntitiesOfClass(
            Player.class,
            new AABB(position).inflate(8.0),
            Player::isAlive
        );
        final int pieces = nearby.stream().mapToInt(player -> brewOutput
            ? countArmor(player, WarlockeryTags.Items.BREWING_GARB)
            : "brazier".equals(recipeType)
                ? countArmor(player, WarlockeryTags.Items.NECROMANCER_GARB)
                : 0
        ).max().orElse(0);
        return BrewingGarbRules.duplicate(outputs, BrewingGarbRules.duplicates(pieces, level.getRandom().nextInt(100)));
    }

    private static void applyIncomingProtection(final LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        final HunterArmorRules.Resolution protection = HunterArmorRules.resolve(
            wearsFullSet(player, WarlockeryTags.Items.DAWN_HUNTER_ARMOR),
            wearsFullSet(player, WarlockeryTags.Items.SILVERED_HUNTER_ARMOR),
            wearsFullSet(player, WarlockeryTags.Items.WARLOCK_HUNTER_ARMOR),
            event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO),
            attacker != null && attacker.typeHolder().is(WarlockeryTags.EntityTypes.WEREWOLVES),
            attacker != null && attacker.typeHolder().is(WarlockeryTags.EntityTypes.VAMPIRES)
        );
        if (protection.protectedDamage()) {
            event.setNewDamage(event.getNewDamage() * protection.damageMultiplier());
        }
        if (protection.burnsAttacker() && attacker != null) {
            attacker.igniteForSeconds(4.0F);
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.BITING_ARMOR)
            && attacker != null
            && attacker.level() instanceof ServerLevel level) {
            attacker.hurtServer(level, attacker.damageSources().thorns(player), 2.0F);
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            BitingBeltItem.applyHarmful(player.getItemBySlot(EquipmentSlot.LEGS), attacker);
        }
    }

    private static void applyOffensiveEquipment(final LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        if (attacker.getMainHandItem().isEmpty()
            && attacker.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.UNARMED_POWER_ARMOR)) {
            event.setNewDamage(event.getNewDamage() * 1.75F);
            event.getEntity().push(0.0, 0.65, 0.0);
        }
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)
            && attacker.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.ARCHERY_ARMOR)) {
            event.setNewDamage(event.getNewDamage() * (event.getEntity().isFallFlying() ? 1.75F : 1.25F));
        }
        final ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon != null && weapon.is(WarlockeryTags.Items.DEATH_WEAPONS)) {
            event.setNewDamage(event.getNewDamage() + event.getEntity().getMaxHealth() * 0.1F);
        }
    }

    private static boolean wearsFullSet(final Player player, final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        return ARMOR.stream().map(player::getItemBySlot).allMatch(stack -> stack.is(tag));
    }

    private static int countArmor(
        final Player player,
        final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag
    ) {
        return (int) ARMOR.stream().map(player::getItemBySlot).filter(stack -> stack.is(tag)).count();
    }

    private static boolean wearsDeathSet(final Player player) {
        return List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET).stream()
            .map(player::getItemBySlot)
            .allMatch(stack -> stack.is(WarlockeryTags.Items.DEATH_DISGUISE_ARMOR));
    }

    private static void growNearbyPlants(final ServerLevel level, final BlockPos center) {
        BlockPos.betweenClosedStream(center.offset(-3, -1, -3), center.offset(3, 2, 3))
            .filter(pos -> level.getBlockState(pos).getBlock() instanceof BonemealableBlock)
            .limit(16)
            .forEach(pos -> {
                final var state = level.getBlockState(pos);
                final BonemealableBlock growable = (BonemealableBlock) state.getBlock();
                if (growable.isValidBonemealTarget(level, pos, state)
                    && growable.isBonemealSuccess(level, level.getRandom(), pos, state)) {
                    growable.performBonemeal(level, level.getRandom(), pos, state);
                }
            });
    }

    private static void applyTwistingBand(final Player wearer, final ServerLevel level) {
        if (!wearer.getItemBySlot(EquipmentSlot.HEAD).is(WarlockeryTags.Items.TWISTING_BANDS)) {
            return;
        }
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(wearer.blockPosition()).inflate(12),
            target -> target != wearer && target.isAlive() && wearer.hasLineOfSight(target)
                && GazeGeometry.faces(
                    wearer.getLookAngle(),
                    target.getEyePosition().subtract(wearer.getEyePosition()),
                    0.92
                )
        ).forEach(target -> {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
        });
        level.getEntitiesOfClass(
            ServerPlayer.class,
            new AABB(wearer.blockPosition()).inflate(12),
            observer -> observer != wearer && observer.hasLineOfSight(wearer)
                && GazeGeometry.faces(
                    observer.getLookAngle(),
                    wearer.getEyePosition().subtract(observer.getEyePosition()),
                    0.96
                )
        ).forEach(observer -> {
            observer.setYRot(observer.getYRot() + 150.0F);
            observer.setYHeadRot(observer.getYRot());
            observer.getFoodData().setFoodLevel(Math.max(0, observer.getFoodData().getFoodLevel() - 1));
            observer.getFoodData().setSaturation(Math.max(0.0F, observer.getFoodData().getSaturationLevel() - 0.5F));
        });
    }

    private static boolean inventoryContains(final Player player, final net.minecraft.world.item.Item item) {
        return java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
            .mapToObj(player.getInventory()::getItem)
            .anyMatch(stack -> stack.is(item));
    }
}
