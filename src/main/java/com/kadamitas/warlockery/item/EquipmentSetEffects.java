package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.entity.CreatureCombat;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.magic.MagicPathState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingGetProjectileEvent;
import org.jspecify.annotations.Nullable;

public final class EquipmentSetEffects {
    private static final String STONEBROKER_QUIVER_SHOT = "WarlockeryStonebrokerQuiverShot";
    private static final ThreadLocal<Boolean> RETALIATING = ThreadLocal.withInitial(() -> false);
    private static final Identifier THORN_SPEAR_GRIP = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "thorn_spear_grip");
    private static final AttributeModifier THORN_SPEAR_STABILITY = new AttributeModifier(
        THORN_SPEAR_GRIP,
        ThornSpearRules.KNOCKBACK_RESISTANCE,
        AttributeModifier.Operation.ADD_VALUE
    );
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
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(WarlockeryTags.Items.DEATH_ROBES)) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
        }
        applyDeathHood(player, level);
        drainDeathScythe(player);
        applyThornSpearStability(player);
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
        applyPatronArmorSynergy(player, level);
        applyTwistingBand(player, level);
    }

    public static void handleGetProjectile(final LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final ItemStack projectile = event.getProjectileItemStack();
        final boolean wearingQuiver = player.getItemBySlot(EquipmentSlot.CHEST)
            .is(WarlockeryTags.Items.ARCHERY_ARMOR);
        if (!StonebrokerQuiverRules.suppliesEndlessArrow(
            wearingQuiver,
            projectile.isEmpty(),
            projectile.is(Items.ARROW)
        )) {
            return;
        }
        final ItemStack supplied = projectile.isEmpty() ? new ItemStack(Items.ARROW) : projectile.copy();
        supplied.setCount(1);
        event.setProjectileItemStack(supplied);
    }

    public static void handleEntityJoinLevel(final EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()
            || !(event.getLevel() instanceof ServerLevel)
            || !(event.getEntity() instanceof AbstractArrow arrow)
            || !(arrow.getOwner() instanceof Player shooter)
            || !shooter.getItemBySlot(EquipmentSlot.CHEST).is(WarlockeryTags.Items.ARCHERY_ARMOR)) {
            return;
        }
        arrow.getPersistentData().putBoolean(STONEBROKER_QUIVER_SHOT, true);
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(
            StonebrokerQuiverRules.PROJECTILE_VELOCITY_MULTIPLIER
        ));
    }

    public static void handleDamage(final LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        applyIncomingProtection(event);
        applyOffensiveEquipment(event);
        CreatureCombat.capDeathDamage(event);
    }

    public static boolean tryBlockHex(final LivingEntity target) {
        if (!(target instanceof Player player) || !HunterArmorRules.blocksHex(wearsCompleteHunterSet(player))) {
            return false;
        }
        ARMOR.forEach(slot -> player.getItemBySlot(slot).hurtAndBreak(1, player, slot));
        return true;
    }

    public static boolean suppressesProtectionDolls(final LivingEntity target) {
        return target instanceof Player player
            && HunterArmorRules.suppressesProtectionDolls(wearsCompleteHunterSet(player));
    }

    public static List<ItemStack> enhanceMachineOutputs(
        final ServerLevel level,
        final @Nullable Player brewer,
        final String recipeType,
        final List<ItemStack> outputs
    ) {
        if (brewer == null || !brewer.isAlive()) {
            return BrewingGarbRules.duplicate(outputs, 0);
        }
        final boolean brewOutput = outputs.stream().anyMatch(stack -> stack.is(WarlockeryTags.Items.BREWS));
        final List<Integer> chances = machineYieldChances(brewer, brewOutput, recipeType);
        final List<Integer> rolls = chances.stream().map(_ -> level.getRandom().nextInt(100)).toList();
        final int additionalCopies = BrewingGarbRules.additionalCopies(chances, rolls);
        return BrewingGarbRules.duplicate(outputs, additionalCopies);
    }

    public static List<ItemStack> enhanceNearbyMachineOutputs(
        final ServerLevel level,
        final BlockPos position,
        final String recipeType,
        final List<ItemStack> outputs
    ) {
        final boolean brewOutput = outputs.stream().anyMatch(stack -> stack.is(WarlockeryTags.Items.BREWS));
        final int additionalCopies = level.getEntitiesOfClass(
            Player.class,
            new AABB(position).inflate(8.0),
            Player::isAlive
        ).stream().mapToInt(player -> {
            final List<Integer> chances = machineYieldChances(player, brewOutput, recipeType);
            final List<Integer> rolls = chances.stream().map(_ -> level.getRandom().nextInt(100)).toList();
            return BrewingGarbRules.additionalCopies(chances, rolls);
        }).max().orElse(0);
        return BrewingGarbRules.duplicate(outputs, additionalCopies);
    }

    private static void applyIncomingProtection(final LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (tryHedgeCroneEvasion(player, event)) {
            return;
        }
        final HunterArmorRules.Resolution protection = HunterArmorRules.resolve(
            wearsFullSet(player, WarlockeryTags.Items.DAWN_HUNTER_ARMOR),
            wearsFullSet(player, WarlockeryTags.Items.SILVERED_HUNTER_ARMOR),
            wearsFullSet(player, WarlockeryTags.Items.WARLOCK_HUNTER_ARMOR),
            event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO),
            attacker != null && attacker.typeHolder().is(WarlockeryTags.EntityTypes.WEREWOLVES),
            attacker != null && attacker.typeHolder().is(WarlockeryTags.EntityTypes.VAMPIRES)
        );
        if (protection.protectedDamage()) {
            event.setAmount(event.getAmount() * protection.damageMultiplier());
        }
        if (protection.burnsAttacker() && attacker != null) {
            attacker.igniteForSeconds(4.0F);
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.BITING_ARMOR)
            && attacker != null
            && !RETALIATING.get()
            && attacker.level() instanceof ServerLevel level) {
            retaliate(level, player, attacker, 2.0F);
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            BitingBeltItem.applyHarmful(player.getItemBySlot(EquipmentSlot.LEGS), attacker);
        }
        if (attacker != null
            && player.isUsingItem()
            && player.getUseItem().is(ModItems.ALL.get("thorn_spear").get())
            && ThornSpearRules.summonsGuardWolf(player.getRandom().nextFloat())
            && player.level() instanceof ServerLevel level) {
            final Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (wolf != null) {
                wolf.setPos(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
                wolf.tame(player);
                wolf.setTarget(attacker);
                level.addFreshEntity(wolf);
            }
        }
        final int silverPieces = countArmor(player, WarlockeryTags.Items.SILVER_ARMOR);
        if (silverPieces > 0
            && attacker != null
            && !RETALIATING.get()
            && CreatureCombat.isWerewolfTarget(attacker)
            && attacker.level() instanceof ServerLevel level) {
            retaliate(level, player, attacker, silverPieces);
            ARMOR.stream()
                .filter(slot -> player.getItemBySlot(slot).is(WarlockeryTags.Items.SILVER_ARMOR))
                .forEach(slot -> player.getItemBySlot(slot).hurtAndBreak(1, player, slot));
        }
    }

    private static void retaliate(
        final ServerLevel level,
        final Player wearer,
        final LivingEntity attacker,
        final float damage
    ) {
        RETALIATING.set(true);
        try {
            attacker.hurtServer(level, attacker.damageSources().thorns(wearer), damage);
        } finally {
            RETALIATING.set(false);
        }
    }

    private static void applyOffensiveEquipment(final LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        if (attacker.getMainHandItem().isEmpty()
            && attacker.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.UNARMED_POWER_ARMOR)) {
            event.setAmount(PatronArmorRules.unarmedDamage(event.getAmount(), true, true));
            event.getEntity().push(0.0, 0.65, 0.0);
        }
        if (isStonebrokerQuiverShot(event)) {
            final LivingEntity target = event.getEntity();
            final boolean airborne = StonebrokerQuiverRules.isAirborneTarget(
                target.isFallFlying(),
                target.onGround(),
                target.isInWater(),
                target.isPassenger()
            );
            event.setAmount(event.getAmount() * StonebrokerQuiverRules.damageMultiplier(true, airborne));
            target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                StonebrokerQuiverRules.WEAKNESS_TICKS,
                StonebrokerQuiverRules.WEAKNESS_AMPLIFIER
            ));
        }
        final ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon != null && weapon.is(WarlockeryTags.Items.DEATH_WEAPONS)) {
            event.setAmount(Math.max(event.getAmount(), event.getEntity().getMaxHealth() * 0.15F));
        }
        if (weapon != null
            && weapon.is(ModItems.ALL.get("thorn_spear").get())
            && SpiritWorldRuntime.isSpiritWorld(event.getEntity().level())) {
            event.setAmount(ThornSpearRules.spiritWorldDamage(event.getAmount(), true));
        }
    }

    private static boolean wearsFullSet(final Player player, final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        return ARMOR.stream().map(player::getItemBySlot).allMatch(stack -> stack.is(tag));
    }

    public static boolean wearsCompleteHunterSet(final Player player) {
        return wearsFullSet(player, WarlockeryTags.Items.WARLOCK_HUNTER_ARMOR)
            || wearsFullSet(player, WarlockeryTags.Items.SILVERED_HUNTER_ARMOR)
            || wearsFullSet(player, WarlockeryTags.Items.DAWN_HUNTER_ARMOR);
    }

    private static int countArmor(
        final Player player,
        final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag
    ) {
        return (int) ARMOR.stream().map(player::getItemBySlot).filter(stack -> stack.is(tag)).count();
    }

    static List<Integer> machineYieldChances(final Player player, final boolean brewOutput, final String recipeType) {
        if (brewOutput) {
            final List<Integer> chances = new java.util.ArrayList<>();
            if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ALL.get("hedge_crones_hat").get())) {
                chances.addAll(List.of(25, 25));
            }
            if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ALL.get("witchhat").get())) {
                chances.add(35);
            }
            if (player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.ALL.get("witchrobe").get())) {
                chances.add(35);
            }
            final int taggedFallbacks = countArmor(player, WarlockeryTags.Items.BREWING_GARB)
                - (chances.isEmpty() ? 0 : (int) chances.stream().filter(chance -> chance == 35).count()
                    + (chances.contains(25) ? 1 : 0));
            java.util.stream.IntStream.range(0, Math.max(0, taggedFallbacks)).forEach(_ -> chances.add(20));
            return List.copyOf(chances);
        }
        return "brazier".equals(recipeType)
            && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.ALL.get("necromancerrobe").get())
            ? List.of(45)
            : List.of();
    }

    private static boolean wearsDeathSet(final Player player) {
        return List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET).stream()
            .map(player::getItemBySlot)
            .allMatch(stack -> stack.is(WarlockeryTags.Items.DEATH_DISGUISE_ARMOR));
    }

    private static void applyDeathHood(final Player wearer, final ServerLevel level) {
        if (!wearer.getItemBySlot(EquipmentSlot.HEAD).is(WarlockeryTags.Items.DEATH_HOODS)) {
            return;
        }
        if (level.isDarkOutside()) {
            wearer.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, false, false, true));
        }
        level.getEntitiesOfClass(
            LivingEntity.class,
            wearer.getBoundingBox().inflate(10.0),
            target -> target != wearer
                && target.isAlive()
                && wearer.hasLineOfSight(target)
                && GazeGeometry.faces(wearer.getLookAngle(), target.getEyePosition().subtract(wearer.getEyePosition()), 0.94)
        ).forEach(target -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1)));
    }

    private static void drainDeathScythe(final Player player) {
        if (!wearsDeathSet(player)) {
            return;
        }
        final boolean active = List.of(player.getMainHandItem(), player.getOffhandItem()).stream()
            .anyMatch(stack -> stack.getItem() instanceof HandOfDeathItem && HandOfDeathItem.isScythe(stack));
        if (active) {
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 1));
        }
    }

    private static boolean tryHedgeCroneEvasion(final Player player, final LivingDamageEvent event) {
        if (!(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HedgeCroneHatItem)
            || !HedgeCroneHatRules.shouldEvade(!MagicPathState.active(player).isEmpty(), player.getRandom().nextFloat())) {
            return false;
        }
        final var selected = MagicPathState.selected(player);
        if (selected.isEmpty() || !MagicPathState.spend(player, selected.orElseThrow(), HedgeCroneHatRules.RESERVE_COST)) {
            return false;
        }
        final boolean moved = player.randomTeleport(
            player.getX() + player.getRandom().nextIntBetweenInclusive(-8, 8),
            player.getY(),
            player.getZ() + player.getRandom().nextIntBetweenInclusive(-8, 8),
            true
        );
        if (moved) {
            event.setAmount(0.0F);
        }
        return moved;
    }

    private static void applyThornSpearStability(final Player player) {
        final var knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback == null) {
            return;
        }
        final boolean held = player.getMainHandItem().is(ModItems.ALL.get("thorn_spear").get())
            || player.getOffhandItem().is(ModItems.ALL.get("thorn_spear").get());
        if (held && !knockback.hasModifier(THORN_SPEAR_GRIP)) {
            knockback.addTransientModifier(THORN_SPEAR_STABILITY);
        } else if (!held) {
            knockback.removeModifier(THORN_SPEAR_GRIP);
        }
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

    private static void applyPatronArmorSynergy(final Player wearer, final ServerLevel level) {
        final boolean girdle = wearer.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.UNARMED_POWER_ARMOR);
        final boolean quiver = wearer.getItemBySlot(EquipmentSlot.CHEST).is(WarlockeryTags.Items.ARCHERY_ARMOR);
        if (!girdle && !quiver) {
            return;
        }
        final boolean counterpartNearby = level.getEntitiesOfClass(
            Player.class,
            new AABB(wearer.blockPosition()).inflate(12.0),
            other -> other != wearer && other.isAlive() && PatronArmorRules.sharesResistance(
                girdle,
                quiver,
                other.getItemBySlot(EquipmentSlot.LEGS).is(WarlockeryTags.Items.UNARMED_POWER_ARMOR),
                other.getItemBySlot(EquipmentSlot.CHEST).is(WarlockeryTags.Items.ARCHERY_ARMOR)
            )
        ).stream().findAny().isPresent();
        if (counterpartNearby) {
            wearer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, false, false, true));
        }
    }

    private static boolean isStonebrokerQuiverShot(final LivingDamageEvent event) {
        return event.getSource().getDirectEntity() instanceof AbstractArrow arrow
            && arrow.getPersistentData().getBooleanOr(STONEBROKER_QUIVER_SHOT, false);
    }
}
