package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import com.kadamitas.warlockery.item.BeastSpeechCharmItem;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.item.WaystoneState;
import com.kadamitas.warlockery.item.ResourceCompatibilityTags;
import com.kadamitas.warlockery.item.ParasyticLouseItem;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.magic.ImpContractRuntime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class CreatureBehaviorRuntime {
    private static final ThreadLocal<Boolean> APPLYING_THORNS = ThreadLocal.withInitial(() -> false);
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    );
    private static final List<HexPulse> HEDGE_CRONE_PULSES = List.of(
        new HexPulse(MobEffects.POISON, 120, 0),
        new HexPulse(MobEffects.SLOWNESS, 160, 1),
        new HexPulse(MobEffects.WEAKNESS, 160, 1),
        new HexPulse(MobEffects.BLINDNESS, 80, 0)
    );

    private CreatureBehaviorRuntime() {
    }

    public static void tick(
        final Mob creature,
        final ServerLevel level,
        final CreatureBehaviorProfile profile
    ) {
        tickBoundCompanion(creature, level, profile);
        if (!CreatureBehaviorRules.shouldPulse(
            creature.tickCount,
            creature.getId(),
            profile.pulseIntervalTicks()
        )) {
            return;
        }
        switch (profile.kind()) {
            case HEDGE_CRONE -> tickHedgeCrone(creature);
            case DEATH -> creature.heal(1.0F);
            case FORGEWARDEN -> tickGoblinAura(creature, level, true);
            case THORNED_PURSUER -> tickThornedPursuer(creature, level);
            case ABYSSAL_REGENT -> InfernalHierarchyRuntime.tickAbyssalTorment(creature, level);
            case SPECTRE -> pulseFear(creature);
            case MANDRAKE -> pulseScreech(creature);
            case DREAMROOT, BRAMBLE_COLOSSUS -> tickRootedDrain(creature, level);
            case GLASS_DOPPELGANGER -> tickReflection(creature, level);
            case STONEBROKER -> tickStonebroker(creature, level);
            case LOUSE -> tickLouse(creature, level);
            case POLTERGEIST -> tickPoltergeist(creature, level);
            case EMBERHORN_ARCHFIEND -> InfernalHierarchyRuntime.tickCauldronAura(creature, level);
            case FAMILIAR -> tickOreGuidance(creature, level);
            case VAMPIRE, BLOOD_THRALL, NAAMAH -> tickSunlightWeakness(creature, level);
            default -> {
            }
        }
    }

    public static InteractionResult interact(
        final Mob creature,
        final Player player,
        final InteractionHand hand,
        final CreatureBehaviorProfile profile
    ) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        final ItemStack held = player.getItemInHand(hand);
        return switch (profile.kind()) {
            case BANSHEE -> empowerBanshee(creature, player, held);
            case PALE_STEED, NIGHTMARE -> interactMount(creature, player, held, profile);
            case CIRCLE_MAGE -> recruitCircleMage(creature, level, player, held, profile);
            case DEMON -> barterWithDemon(creature, player, held, profile);
            case HELLHOUND -> cureHellhound(creature, level, player, held);
            case CAT, LOST_SOUL, SPIRIT, TOAD -> bindCompanion(creature, player, held, profile);
            case OWL -> interactOwl(creature, level, player, hand, held, profile);
            case GOBLIN, HOBGOBLIN -> bindCompanion(creature, player, held, profile);
            case IMP -> ImpContractRuntime.interact(creature, player, held, profile);
            case STORM_SIMIAN -> interactStormSimian(creature, level, player, held, profile);
            case BRAMBLE_COLOSSUS -> interactTreefyd(creature, player, held, profile);
            case FORGEWARDEN, DREAMROOT, STONEBROKER ->
                empowerWithHeart(creature, player, held, profile);
            case NAAMAH -> interactNaamah(creature, player, hand, profile);
            case LOUSE -> captureEffect(creature, player, held);
            case FAMILIAR -> interactSpectralFamiliar(creature, player, held, profile);
            default -> InteractionResult.PASS;
        };
    }

    public static boolean canAttack(
        final Mob creature,
        final LivingEntity target,
        final CreatureBehaviorProfile profile
    ) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(creature);
        if (owner.filter(target.getUUID()::equals).isPresent()) {
            return false;
        }
        if (target instanceof Mob other
            && owner.isPresent()
            && CreatureBehaviorState.owner(other).equals(owner)) {
            return false;
        }
        if (profile.has(Feature.DEATH_DISGUISE)
            && target instanceof Player player
            && DeathImpersonationRules.isComplete(player)) {
            return false;
        }
        if (profile.kind() == CreatureKind.DEMON
            && target instanceof Player player
            && BeastSpeechCharmItem.pacifiesDemon(player, creature)) {
            return false;
        }
        if (profile.kind() == CreatureKind.BRAMBLE_COLOSSUS && !TreefydRules.canAttack(
            CreatureBehaviorState.isOwnedBy(creature, target.getUUID()),
            TreefydState.isAllowed(creature, target.getUUID()),
            target instanceof ArcaneCreature arcane && arcane.creatureKind() == CreatureKind.BRAMBLE_COLOSSUS
        )) {
            return false;
        }
        if (creature instanceof InfernalHierarchyEntity hierarchy) {
            return InfernalHierarchyRuntime.restraintAllows(hierarchy, target);
        }
        return !profile.has(Feature.PASSIVE_UNTIL_HURT) || creature.getLastHurtByMob() == target;
    }

    public static void afterAttack(
        final Mob creature,
        final ServerLevel level,
        final Entity target,
        final CreatureBehaviorProfile profile
    ) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        if (profile.has(Feature.FIRE_MELEE)) {
            living.igniteForSeconds(4.0F);
        }
        if (profile.kind() == CreatureKind.FORGEWARDEN) {
            final float bonus = GoblinBossRules.pairedAttackBonus(nearestPatronDistanceSquared(creature, level));
            if (bonus > 0.0F) {
                living.push(0.0, 0.45, 0.0);
            }
        }
        if (profile.has(Feature.BLOOD_DRAIN)) {
            creature.heal(3.0F + CreatureBehaviorState.empowerment(creature));
            living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 1));
        }
        if (profile.has(Feature.SOUL_REAP)) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
        }
        if (profile.has(Feature.TORMENT_BANISHMENT)) {
            final double x = living.getX() + level.getRandom().nextIntBetweenInclusive(-8, 8);
            final double z = living.getZ() + level.getRandom().nextIntBetweenInclusive(-8, 8);
            // The Darkness rider applies only after the displacement actually succeeded. randomTeleport
            // validates the destination footprint itself and returns false while leaving the victim at
            // the original position, so a rejected displacement adds no rider at all.
            if (living.randomTeleport(x, living.getY(), z, true)) {
                living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
            }
        }
        if (profile.has(Feature.ROOTED_DRAIN)) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            creature.heal(2.0F);
        }
        if (profile.kind() == CreatureKind.LOUSE) {
            injectStoredEffect(creature, living);
        }
        if (profile.has(Feature.SAFE_BLAST)) {
            MinedrakeCombat.detonate(creature, level);
        }
    }

    public static float attackDamageBonus(
        final Mob creature,
        final ServerLevel level,
        final CreatureBehaviorProfile profile
    ) {
        return profile.kind() == CreatureKind.FORGEWARDEN
            ? GoblinBossRules.pairedAttackBonus(nearestPatronDistanceSquared(creature, level))
            : 0.0F;
    }

    public static void afterHurt(
        final Mob creature,
        final ServerLevel level,
        final DamageSource source,
        final float amount,
        final CreatureBehaviorProfile profile
    ) {
        if (profile.has(Feature.THORN_RETALIATION)
            && !APPLYING_THORNS.get()
            && source.getEntity() instanceof LivingEntity attacker
            && attacker != creature) {
            APPLYING_THORNS.set(true);
            try {
                attacker.hurtServer(
                    level,
                    creature.damageSources().thorns(creature),
                    Math.min(6.0F, 2.0F + amount * 0.25F)
                );
            } finally {
                APPLYING_THORNS.set(false);
            }
        }
        if (profile.has(Feature.PHASED) && amount >= 2.0F) {
            creature.randomTeleport(
                creature.getX() + level.getRandom().nextIntBetweenInclusive(-4, 4),
                creature.getY() + level.getRandom().nextIntBetweenInclusive(-2, 2),
                creature.getZ() + level.getRandom().nextIntBetweenInclusive(-4, 4),
                true
            );
        }
        if (profile.has(Feature.SAFE_BLAST)) {
            MinedrakeCombat.detonate(creature, level);
        }
    }

    private static InteractionResult empowerBanshee(
        final Mob creature,
        final Player player,
        final ItemStack held
    ) {
        if (!held.is(CreatureBehaviorTags.Items.BANSHEE_EMPOWERMENT)) {
            return InteractionResult.PASS;
        }
        final CreatureBehaviorState.EmpowermentResult result = CreatureBehaviorState.empower(creature, 1);
        if (!result.changed()) {
            send(player, "message.warlockery.creature.empowerment_full", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        increaseAttributes(creature, 6.0, 0.0);
        consumeOne(player, held);
        send(player, "message.warlockery.creature.empowered", creature.getDisplayName(), result.after());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactMount(
        final Mob creature,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        final Optional<UUID> owner = CreatureBehaviorState.owner(creature);
        if (owner.isEmpty()) {
            return bindCompanion(creature, player, held, profile);
        }
        if (!CreatureBehaviorRules.canMount(owner, player.getUUID())) {
            send(player, "message.warlockery.creature.bound_elsewhere", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        if (!held.isEmpty() && profile.offering().stream().noneMatch(held::is)) {
            return InteractionResult.PASS;
        }
        return player.startRiding(creature) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static InteractionResult recruitCircleMage(
        final Mob creature,
        final ServerLevel level,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (profile.offering().stream().noneMatch(held::is)) {
            return InteractionResult.PASS;
        }
        if (!CreatureBehaviorState.isOwnedBy(creature, player.getUUID())
            && !FamiliarBondRules.canRecruitCovenMage(SeerCovenRuntime.countOwnedMages(level, player))) {
            send(player, "message.warlockery.creature.coven_full");
            return InteractionResult.FAIL;
        }
        final boolean familiarPresent = level.getEntitiesOfClass(
            Mob.class,
            player.getBoundingBox().inflate(12.0),
            familiar -> familiar.typeHolder().is(CreatureBehaviorTags.EntityTypes.FAMILIARS)
                && CreatureBehaviorState.isOwnedBy(familiar, player.getUUID())
        ).stream().findAny().isPresent();
        if (!CreatureBehaviorRules.canRecruit(
            CreatureBehaviorState.owner(creature),
            player.getUUID(),
            true,
            familiarPresent
        )) {
            send(player, "message.warlockery.creature.familiar_required", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        final InteractionResult result = finishBinding(creature, player, held);
        if (CreatureBehaviorState.isOwnedBy(creature, player.getUUID())) {
            SeerCovenRuntime.register(level, player, creature);
        }
        return result;
    }

    private static InteractionResult barterWithDemon(
        final Mob creature,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (profile.offering().stream().noneMatch(held::is)) {
            return InteractionResult.PASS;
        }
        if (!CreatureBehaviorRules.canBind(CreatureBehaviorState.owner(creature), player.getUUID(), true)) {
            send(player, "message.warlockery.creature.bound_elsewhere", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        CreatureBehaviorState.bind(creature, player.getUUID());
        creature.setTarget(null);
        creature.setPersistenceRequired();
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1_200, 0));
        consumeOne(player, held);
        send(player, "message.warlockery.creature.bartered", creature.getDisplayName());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult bindCompanion(
        final Mob creature,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (profile.offering().stream().noneMatch(held::is)) {
            return InteractionResult.PASS;
        }
        if (!CreatureBehaviorRules.canBind(CreatureBehaviorState.owner(creature), player.getUUID(), true)) {
            send(player, "message.warlockery.creature.bound_elsewhere", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        return finishBinding(creature, player, held);
    }

    private static InteractionResult finishBinding(
        final Mob creature,
        final Player player,
        final ItemStack held
    ) {
        CreatureBehaviorState.bind(creature, player.getUUID());
        creature.setTarget(null);
        creature.setPersistenceRequired();
        consumeOne(player, held);
        send(player, "message.warlockery.creature.bound", creature.getDisplayName());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactStormSimian(
        final Mob creature,
        final ServerLevel level,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (!held.is(CreatureBehaviorTags.Items.BOUND_WAYSTONES)) {
            return bindCompanion(creature, player, held, profile);
        }
        if (!CreatureBehaviorState.isOwnedBy(creature, player.getUUID())) {
            send(player, "message.warlockery.creature.owner_required", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        final var data = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        final String dimension = data.getStringOr("WarlockeryDimension", "");
        if (!dimension.equals(level.dimension().identifier().toString())
            || !data.contains("WarlockeryWaystonePos")) {
            send(player, "message.warlockery.creature.travel_failed", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        final BlockPos destination = BlockPos.of(data.getLongOr("WarlockeryWaystonePos", BlockPos.ZERO.asLong()));
        if (!level.isLoaded(destination)) {
            send(player, "message.warlockery.creature.travel_failed", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        player.teleportTo(destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5);
        creature.teleportTo(destination.getX() + 1.5, destination.getY() + 1.0, destination.getZ() + 0.5);
        send(player, "message.warlockery.creature.travel_succeeded", creature.getDisplayName());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactOwl(
        final Mob creature,
        final ServerLevel level,
        final Player player,
        final InteractionHand hand,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (!held.is(CreatureBehaviorTags.Items.BOUND_WAYSTONES)) {
            return bindCompanion(creature, player, held, profile);
        }
        final InteractionHand cargoHand = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
        final ItemStack cargo = player.getItemInHand(cargoHand);
        final Optional<DeliveryTarget> target = deliveryTarget(level, held);
        final FamiliarDeliveryRules.Diagnostic diagnostic = FamiliarDeliveryRules.diagnose(
            CreatureBehaviorState.isOwnedBy(creature, player.getUUID()),
            WaystoneState.read(held).isPresent() || SympatheticBinding.read(held).isPresent(),
            !cargo.isEmpty(),
            target.isPresent()
        );
        if (diagnostic != FamiliarDeliveryRules.Diagnostic.READY) {
            send(player, "message.warlockery.creature.owl_delivery." + diagnostic.name().toLowerCase(java.util.Locale.ROOT));
            return InteractionResult.FAIL;
        }
        final DeliveryTarget destination = target.orElseThrow();
        final ItemStack parcel = cargo.copyWithCount(1);
        if (!player.hasInfiniteMaterials()) {
            cargo.shrink(1);
        }
        final ItemEntity delivered = new ItemEntity(
            destination.level(),
            destination.position().x,
            destination.position().y + 0.5,
            destination.position().z,
            parcel
        );
        delivered.setDefaultPickUpDelay();
        destination.level().addFreshEntity(delivered);
        creature.teleport(new TeleportTransition(
            destination.level(),
            destination.position().add(0.0, 0.5, 0.0),
            Vec3.ZERO,
            creature.getYRot(),
            creature.getXRot(),
            TeleportTransition.DO_NOTHING
        ));
        send(player, "message.warlockery.creature.owl_delivery.ready", parcel.getHoverName());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult cureHellhound(
        final Mob creature,
        final ServerLevel level,
        final Player player,
        final ItemStack held
    ) {
        return HellhoundCureRuntime.cure(creature, level, player, held);
    }

    private static Optional<DeliveryTarget> deliveryTarget(final ServerLevel current, final ItemStack waystone) {
        final Optional<SympatheticBinding> binding = SympatheticBinding.read(waystone);
        if (binding.isPresent()) {
            return binding.orElseThrow().resolve(current.getServer())
                .filter(entity -> entity.level() instanceof ServerLevel)
                .map(entity -> new DeliveryTarget((ServerLevel) entity.level(), entity.position()));
        }
        return WaystoneState.read(waystone).flatMap(location -> {
            final ServerLevel destination = current.getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION,
                location.dimension()
            ));
            if (destination == null || !destination.isLoaded(location.position())) {
                return Optional.empty();
            }
            return Optional.of(new DeliveryTarget(destination, Vec3.atCenterOf(location.position())));
        });
    }

    private static InteractionResult empowerWithHeart(
        final Mob creature,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (profile.offering().stream().noneMatch(held::is)) {
            return InteractionResult.PASS;
        }
        final CreatureBehaviorState.EmpowermentResult result = CreatureBehaviorState.empower(creature, 1);
        if (!result.changed()) {
            send(player, "message.warlockery.creature.empowerment_full", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        increaseAttributes(creature, 4.0, 1.0);
        consumeOne(player, held);
        send(player, "message.warlockery.creature.empowered", creature.getDisplayName(), result.after());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactTreefyd(
        final Mob creature,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (!CreatureBehaviorState.isOwnedBy(creature, player.getUUID())) {
            send(player, "message.warlockery.creature.owner_required", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        final Optional<SympatheticBinding> binding = SympatheticBinding.read(held);
        if (binding.isPresent()) {
            final boolean allowed = TreefydState.toggleAllowed(creature, binding.orElseThrow());
            send(player, allowed
                ? "message.warlockery.creature.treefyd.allowed"
                : "message.warlockery.creature.treefyd.removed", binding.orElseThrow().targetName());
            return InteractionResult.SUCCESS;
        }
        if (held.is(ResourceCompatibilityTags.Items.SAFE_MAGICAL_PLANT_TOOLS)) {
            final boolean wandering = TreefydState.toggleWandering(creature);
            creature.setNoAi(!wandering);
            send(player, wandering
                ? "message.warlockery.creature.treefyd.wandering"
                : "message.warlockery.creature.treefyd.guardian");
            return InteractionResult.SUCCESS;
        }
        return empowerWithHeart(creature, player, held, profile);
    }

    private static InteractionResult initiateVampire(
        final Player player,
        final InteractionHand hand,
        final CreatureBehaviorProfile profile
    ) {
        final boolean mainHandOffering = profile.offering().stream().anyMatch(player.getMainHandItem()::is);
        final boolean offHandOffering = profile.offering().stream().anyMatch(player.getOffhandItem()::is);
        if (hand != VampireInitiationRules.preferredHand(mainHandOffering, offHandOffering)) {
            return InteractionResult.PASS;
        }
        final ItemStack held = player.getItemInHand(hand);
        return switch (VampireInitiationRules.assess(
            profile.offering().stream().anyMatch(held::is),
            SupernaturalState.getForm(player)
        )) {
            case MISSING_MATRIARCH_BLOOD -> {
                send(player, "message.warlockery.creature.vampire_missing_blood", player.getDisplayName());
                yield InteractionResult.CONSUME;
            }
            case TRANSFORMATION_BLOCKED -> {
                send(player, "message.warlockery.creature.transformation_blocked", player.getDisplayName());
                yield InteractionResult.CONSUME;
            }
            case READY -> {
                com.kadamitas.warlockery.transformation.SupernaturalAdvancement.beginVampire(player);
                consumeOne(player, held);
                send(player, "message.warlockery.creature.vampire_initiated", player.getDisplayName());
                yield InteractionResult.SUCCESS;
            }
        };
    }

    private static InteractionResult interactNaamah(
        final Mob creature,
        final Player player,
        final InteractionHand hand,
        final CreatureBehaviorProfile profile
    ) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && com.kadamitas.warlockery.transformation.SupernaturalProgression.level(
                player,
                com.kadamitas.warlockery.transformation.SupernaturalProgression.Path.VAMPIRE
            ) == 6) {
            com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime
                .recordNaamahAudience(serverPlayer);
            creature.getPersistentData().putString(
                com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime.NAAMAH_TRIAL_OWNER,
                player.getStringUUID()
            );
            final ItemStack offering = player.getItemInHand(hand);
            if (offering.is(Items.POPPY)) {
                if (com.kadamitas.warlockery.transformation.SupernaturalProgression.counter(
                    player,
                    com.kadamitas.warlockery.transformation.SupernaturalProgression.Path.VAMPIRE,
                    com.kadamitas.warlockery.transformation.VampireProgressionRules.Metric.NAAMAH_DEFEATED
                ) == 0) {
                    send(player, "message.warlockery.vampire_progression.naamah_must_be_defeated");
                    return InteractionResult.CONSUME;
                }
                com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime
                    .recordPoppyOffering(serverPlayer);
                consumeOne(player, offering);
                send(player, "message.warlockery.creature.naamah_poppy_accepted", player.getDisplayName());
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.CONSUME;
        }
        return initiateVampire(player, hand, profile);
    }

    private static InteractionResult captureEffect(
        final Mob creature,
        final Player player,
        final ItemStack held
    ) {
        final PotionContents potion = held.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        final Optional<MobEffectInstance> effect = StreamSupport.stream(potion.getAllEffects().spliterator(), false)
            .findFirst();
        if (effect.isEmpty()) {
            if (!held.isEmpty()) {
                return InteractionResult.PASS;
            }
            final ItemStack capturedLouse = new ItemStack(ModItems.ALL.get("louse").get());
            ParasyticLouseItem.writeFromCreature(capturedLouse, creature);
            if (!player.getInventory().add(capturedLouse)) {
                player.drop(capturedLouse, false);
            }
            creature.discard();
            send(player, "message.warlockery.louse.captured");
            return InteractionResult.SUCCESS;
        }
        final MobEffectInstance captured = effect.orElseThrow();
        final Identifier effectId = BuiltInRegistries.MOB_EFFECT.getKey(captured.getEffect().value());
        CreatureBehaviorState.bind(creature, player.getUUID());
        CreatureBehaviorState.storeEffect(creature, new CreatureBehaviorState.StoredEffect(
            effectId,
            Math.max(20, captured.getDuration()),
            Math.max(0, captured.getAmplifier())
        ));
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        send(player, "message.warlockery.creature.effect_stored", creature.getDisplayName());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult interactSpectralFamiliar(
        final Mob creature,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (!held.is(CreatureBehaviorTags.Items.SPECTRAL_ORE_SAMPLES)) {
            return bindCompanion(creature, player, held, profile);
        }
        if (!CreatureBehaviorState.isOwnedBy(creature, player.getUUID())
            || !(held.getItem() instanceof BlockItem blockItem)) {
            send(player, "message.warlockery.creature.owner_required", creature.getDisplayName());
            return InteractionResult.FAIL;
        }
        CreatureBehaviorState.setSampleBlock(creature, BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
        consumeOne(player, held);
        send(player, "message.warlockery.creature.ore_sampled", creature.getDisplayName());
        return InteractionResult.SUCCESS;
    }

    private static void tickBoundCompanion(
        final Mob creature,
        final ServerLevel level,
        final CreatureBehaviorProfile profile
    ) {
        if (!profile.has(Feature.OWNER_AURA) && !profile.has(Feature.PROTECT_OWNER)
            && !profile.has(Feature.BROOM_AURA) && !profile.has(Feature.AMPHIBIOUS_AURA)) {
            return;
        }
        CreatureBehaviorState.owner(creature)
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(LivingEntity::isAlive)
            .ifPresent(owner -> {
                if (profile.has(Feature.PROTECT_OWNER)) {
                    final LivingEntity attacker = owner.getLastHurtByMob();
                    if (attacker != null && attacker.isAlive() && creature.canAttack(attacker)) {
                        creature.setTarget(attacker);
                    }
                }
                if (owner.getVehicle() != creature) {
                    followOwner(creature, owner);
                }
                if (creature.tickCount % 20 == 0) {
                    applyOwnerAura(creature, owner, profile.kind());
                }
            });
    }

    private static void followOwner(final Mob creature, final LivingEntity owner) {
        final double distance = creature.distanceToSqr(owner);
        if (distance >= CreatureBehaviorRules.OWNER_TELEPORT_DISTANCE_SQUARED) {
            creature.teleportTo(owner.getX() + 1.0, owner.getY(), owner.getZ() + 1.0);
        } else if (distance >= CreatureBehaviorRules.OWNER_FOLLOW_DISTANCE_SQUARED) {
            creature.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.1);
        }
    }

    private static void applyOwnerAura(
        final Mob creature,
        final LivingEntity owner,
        final CreatureKind kind
    ) {
        switch (kind) {
            case CAT -> owner.addEffect(new MobEffectInstance(MobEffects.LUCK, 60, 0, true, false));
            case CIRCLE_MAGE -> owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            case DEMON, IMP -> owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
            case STORM_SIMIAN -> owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
            case LOST_SOUL, SPIRIT -> owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
            case PALE_STEED -> creature.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false));
            case NIGHTMARE -> {
                creature.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false));
                owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
            }
            case OWL -> {
                if (owner instanceof Player player && inventoryContains(player, CreatureBehaviorTags.Items.BROOMS)) {
                    owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
                    owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false));
                }
            }
            case FAMILIAR -> owner.addEffect(new MobEffectInstance(MobEffects.HASTE, 60, 0, true, false));
            case TOAD -> {
                owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
                owner.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 0, true, false));
            }
            default -> {
            }
        }
    }

    private static void tickHedgeCrone(final Mob creature) {
        final LivingEntity target = creature.getTarget();
        if (target == null || creature.distanceToSqr(target) < 9.0 || creature.distanceToSqr(target) > 196.0) {
            return;
        }
        final HexPulse pulse = HEDGE_CRONE_PULSES.get(Math.floorMod(
            creature.tickCount / 80 + creature.getId(),
            HEDGE_CRONE_PULSES.size()
        ));
        target.addEffect(new MobEffectInstance(pulse.effect(), pulse.durationTicks(), pulse.amplifier()));
    }

    @SafeVarargs
    private static void pulseEffects(
        final Mob creature,
        final double radius,
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>... effects
    ) {
        creature.level().getEntitiesOfClass(
            Player.class,
            creature.getBoundingBox().inflate(radius),
            player -> player.isAlive() && creature.canAttack(player)
        ).forEach(player -> List.of(effects).forEach(effect ->
            player.addEffect(new MobEffectInstance(effect, 120, 0))
        ));
    }

    private static void pulseScreech(final Mob creature) {
        pulseEffects(creature, 10.0, MobEffects.NAUSEA, MobEffects.SLOWNESS);
    }

    private static void pulseFear(final Mob creature) {
        pulseEffects(creature, 10.0, MobEffects.DARKNESS, MobEffects.WEAKNESS);
    }

    private static void tickGoblinAura(
        final Mob creature,
        final ServerLevel level,
        final boolean forgeAura
    ) {
        level.getEntitiesOfClass(
            LivingEntity.class,
            creature.getBoundingBox().inflate(12.0),
            entity -> entity.typeHolder().is(CreatureBehaviorTags.EntityTypes.GOBLINS)
        ).forEach(entity -> {
            entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0));
            entity.addEffect(new MobEffectInstance(forgeAura ? MobEffects.FIRE_RESISTANCE : MobEffects.HASTE, 100, 0));
        });
    }

    private static void tickStonebroker(final Mob creature, final ServerLevel level) {
        tickGoblinAura(creature, level, false);
        nearestPatron(creature, level, 32.0)
            .filter(counterpart -> creature.distanceToSqr(counterpart) > 36.0)
            .ifPresent(counterpart -> creature.getNavigation().moveTo(counterpart, 1.1));
        final LivingEntity target = creature.getTarget();
        if (target != null
            && creature.distanceToSqr(target) <= 256.0
            && creature.getSensing().hasLineOfSight(target)) {
            fireArrow(creature, target, level, 6.0, 1.8F, 3.0F, 1.15F);
        }
    }

    private static double nearestPatronDistanceSquared(final Mob creature, final ServerLevel level) {
        return nearestPatron(creature, level, 16.0)
            .map(creature::distanceToSqr)
            .orElse(Double.POSITIVE_INFINITY);
    }

    private static java.util.Optional<LivingEntity> nearestPatron(
        final Mob creature,
        final ServerLevel level,
        final double radius
    ) {
        final var counterpart = GoblinBossRules.counterpart(
            creature instanceof ArcaneCreature arcane ? arcane.creatureKind() : null
        );
        if (counterpart.isEmpty()) {
            return java.util.Optional.empty();
        }
        return level.getEntitiesOfClass(
            LivingEntity.class,
            creature.getBoundingBox().inflate(radius),
            candidate -> candidate != creature
                && candidate instanceof ArcaneCreature arcane
                && arcane.creatureKind() == counterpart.orElseThrow()
        ).stream().min(java.util.Comparator.comparingDouble(creature::distanceToSqr));
    }

    private static void tickThornedPursuer(final Mob creature, final ServerLevel level) {
        final LivingEntity target = creature.getTarget();
        final double distanceSquared = target == null ? 0.0 : creature.distanceToSqr(target);
        if (target != null && CreatureBehaviorRules.shouldUseRangedAttack(
            distanceSquared,
            creature.getSensing().hasLineOfSight(target)
        )) {
            fireArrow(creature, target, level, 5.0, 1.6F, 5.0F, 0.8F);
        } else if (target != null && distanceSquared > 196.0) {
            final Vec3 direction = target.position().subtract(creature.position()).normalize().scale(-2.0);
            creature.randomTeleport(
                target.getX() + direction.x,
                target.getY(),
                target.getZ() + direction.z,
                true
            );
        }
        final List<Wolf> wolves = level.getEntitiesOfClass(Wolf.class, creature.getBoundingBox().inflate(24.0));
        if (!CreatureBehaviorRules.shouldSummonWolves(
            creature.getHealth(),
            creature.getMaxHealth(),
            wolves.size(),
            creature.tickCount
        )) {
            return;
        }
        final int count = Math.min(2, 4 - wolves.size());
        for (int index = 0; index < count; index++) {
            final BlockPos position = creature.blockPosition().offset(index == 0 ? -2 : 2, 0, 1);
            final Wolf wolf = EntityTypes.WOLF.spawn(level, position, EntitySpawnReason.EVENT);
            if (wolf != null) {
                wolf.setTarget(target);
                wolf.setPersistenceRequired();
            }
        }
    }

    private static void fireArrow(
        final Mob creature,
        final LivingEntity target,
        final ServerLevel level,
        final double damage,
        final float velocity,
        final float inaccuracy,
        final float pitch
    ) {
        final ItemStack projectileStack = new ItemStack(Items.ARROW);
        final Arrow thorn = new Arrow(level, creature, projectileStack, null);
        thorn.setBaseDamage(damage);
        final double x = target.getX() - creature.getX();
        final double z = target.getZ() - creature.getZ();
        final double arc = Math.sqrt(x * x + z * z) * 0.12;
        Projectile.spawnProjectile(thorn, level, projectileStack, projectile -> projectile.shoot(
            x,
            target.getEyeY() - projectile.getY() + arc,
            z,
            velocity,
            inaccuracy
        ));
        creature.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, pitch);
    }

    private static void tickRootedDrain(final Mob creature, final ServerLevel level) {
        if (level.getBlockState(creature.blockPosition().below()).is(CreatureBehaviorTags.Blocks.LIVING_GROUND)) {
            creature.heal(1.0F + CreatureBehaviorState.empowerment(creature) * 0.5F);
        }
        final LivingEntity target = creature.getTarget();
        if (target != null && creature.distanceToSqr(target) <= 36.0) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        }
    }

    private static void tickLouse(final Mob creature, final ServerLevel level) {
        final Optional<LivingEntity> owner = CreatureBehaviorState.owner(creature)
            .map(level::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(LivingEntity::isAlive);
        final Optional<CreatureBehaviorState.StoredEffect> stored = CreatureBehaviorState.storedEffect(creature);
        final LivingEntity attacker = owner.map(LivingEntity::getLastHurtByMob).orElse(null);
        final boolean armor = owner.filter(Player.class::isInstance)
            .map(Player.class::cast)
            .filter(player -> armorContains(player, CreatureBehaviorTags.Items.LOUSE_REDIRECTING_ARMOR))
            .isPresent();
        if (!CreatureBehaviorRules.canRedirectEffect(
            owner.isPresent(),
            armor,
            attacker != null && attacker.isAlive(),
            stored.isPresent()
        )) {
            return;
        }
        final CreatureBehaviorState.StoredEffect effect = stored.orElseThrow();
        BuiltInRegistries.MOB_EFFECT.get(effect.effectId()).ifPresent(holder -> attacker.addEffect(
            new MobEffectInstance(holder, Math.min(600, effect.durationTicks()), effect.amplifier())
        ));
        CreatureBehaviorState.clearStoredEffect(creature);
    }

    private static void injectStoredEffect(final Mob creature, final LivingEntity bitten) {
        final Optional<CreatureBehaviorState.StoredEffect> stored = CreatureBehaviorState.storedEffect(creature);
        if (stored.isEmpty()) {
            return;
        }
        BuiltInRegistries.MOB_EFFECT.get(stored.orElseThrow().effectId()).ifPresent(effect -> bitten.addEffect(
            new MobEffectInstance(
                effect,
                stored.orElseThrow().durationTicks(),
                stored.orElseThrow().amplifier()
            )
        ));
        CreatureBehaviorState.clearStoredEffect(creature);
    }

    private static void tickPoltergeist(final Mob creature, final ServerLevel level) {
        level.getEntitiesOfClass(
            Player.class,
            creature.getBoundingBox().inflate(8.0),
            player -> player.isAlive() && creature.canAttack(player)
        ).forEach(player -> player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0)));
        level.getEntitiesOfClass(ItemEntity.class, creature.getBoundingBox().inflate(6.0)).forEach(item -> {
            final Vec3 push = item.position().subtract(creature.position()).normalize().scale(0.25).add(0.0, 0.2, 0.0);
            item.setDeltaMovement(push);
            item.hurtMarked = true;
        });
    }

    private static void tickOreGuidance(final Mob creature, final ServerLevel level) {
        final Optional<Identifier> sample = CreatureBehaviorState.sampleBlock(creature);
        if (sample.isEmpty()) {
            return;
        }
        BuiltInRegistries.BLOCK.get(sample.orElseThrow()).ifPresent(block -> BlockPos.betweenClosedStream(
                creature.blockPosition().offset(-12, -8, -12),
                creature.blockPosition().offset(12, 8, 12)
            )
            .filter(position -> {
                final var state = level.getBlockState(position);
                return state.is(block.value()) && state.is(CreatureBehaviorTags.Blocks.SPECTRAL_ORES);
            })
            .min(Comparator.comparingDouble(position -> creature.distanceToSqr(Vec3.atCenterOf(position))))
            .ifPresent(position -> {
                creature.getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 1.2);
                creature.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, true, false));
            }));
    }

    private static void tickReflection(final Mob creature, final ServerLevel level) {
        final String targetId = creature.getPersistentData().getStringOr("WarlockeryReflectedTarget", "");
        if (targetId.isBlank()) {
            return;
        }
        final LivingEntity target;
        try {
            target = level.getEntity(UUID.fromString(targetId)) instanceof LivingEntity living ? living : null;
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        List.of(
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        ).forEach(slot -> {
            creature.setItemSlot(slot, target.getItemBySlot(slot).copy());
            creature.setDropChance(slot, 0.0F);
        });
        creature.setHealth(Math.min(creature.getMaxHealth(), target.getHealth()));
        if (target.hasEffect(MobEffects.SPEED)) {
            creature.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false));
        }
        if (target.hasEffect(MobEffects.STRENGTH)) {
            creature.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, true, false));
        }
    }

    private static void tickSunlightWeakness(final Mob creature, final ServerLevel level) {
        final long dayTime = level.getOverworldClockTime() % 24_000L;
        final boolean daylight = dayTime < 13_000L || dayTime > 23_000L;
        if (CreatureBehaviorRules.shouldBurnInSun(
            daylight,
            level.canSeeSky(creature.blockPosition()),
            creature.hasEffect(MobEffects.FIRE_RESISTANCE)
        )) {
            creature.igniteForSeconds(3.0F);
        }
    }

    private static void increaseAttributes(
        final Mob creature,
        final double healthIncrease,
        final double attackIncrease
    ) {
        final AttributeInstance health = creature.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() + healthIncrease);
            creature.heal((float) healthIncrease);
        }
        final AttributeInstance attack = creature.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attackIncrease > 0.0) {
            attack.setBaseValue(attack.getBaseValue() + attackIncrease);
        }
    }

    private static boolean inventoryContains(
        final Player player,
        final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag
    ) {
        final var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean armorContains(
        final LivingEntity entity,
        final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag
    ) {
        return ARMOR_SLOTS.stream().map(entity::getItemBySlot).anyMatch(stack -> stack.is(tag));
    }

    private static void consumeOne(final Player player, final ItemStack stack) {
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }

    private static void send(final Player player, final String key, final Object... arguments) {
        player.sendSystemMessage(Component.translatable(key, arguments));
    }

    private record HexPulse(
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        int durationTicks,
        int amplifier
    ) {
    }

    private record DeliveryTarget(ServerLevel level, Vec3 position) {
    }
}
