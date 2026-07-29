package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.brew.BrewTargeting.Target;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexRuntime;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.IPlantable;
import org.jspecify.annotations.Nullable;

public final class BrewRuntime {
    private static final int MAX_AREA_BLOCKS = 2_048;
    private static final int MAX_CONNECTED_BLOCKS = 256;
    private static final Consumer<LivingEntity> NO_CONFIGURATION = entity -> {
    };

    private BrewRuntime() {
    }

    public static ImpactResult handleImpact(
        final ServerLevel level,
        final BrewKind kind,
        final Vec3 center,
        final @Nullable Entity directSource,
        final @Nullable Entity owner
    ) {
        final ImpactContext context = new ImpactContext(
            level,
            center,
            kind.radius(),
            kind.potency(),
            directSource,
            owner
        );
        return kind.behaviors().stream()
            .map(behavior -> apply(behavior, context))
            .reduce(ImpactResult.ZERO, ImpactResult::plus);
    }

    private static ImpactResult apply(final BrewBehavior behavior, final ImpactContext context) {
        return switch (behavior) {
            case GROW -> grow(context);
            case EXTINGUISH -> extinguish(context);
            case FREEZE -> freeze(context);
            case PLACE_WEB -> placeWebs(context);
            case IGNITE -> ignite(context);
            case EXPLODE -> explode(context);
            case PUSH -> moveRadially(context, false);
            case PULL -> moveRadially(context, true);
            case LIFT -> lift(context);
            case ATTRACT_ANIMALS -> attractAnimals(context);
            case REPEL_ANIMALS -> repelAnimals(context);
            case FELL_LOGS -> fellLogs(context);
            case PRUNE_LEAVES -> pruneLeaves(context);
            case HARVEST_CROPS -> harvestCrops(context);
            case TILL_SOIL -> tillSoil(context);
            case REVEAL -> reveal(context);
            case REMOVE_BENEFICIAL -> removeEffects(context, true);
            case REMOVE_HARMFUL -> removeEffects(context, false);
            case REMOVE_NAUSEA -> removeNausea(context);
            case HARM_WEREWOLVES -> harmTarget(context, Target.WEREWOLF, 10.0F);
            case WEAKEN_VAMPIRES -> weakenVampires(context);
            case HARM_DEMONS -> harmTarget(context, Target.DEMON, 12.0F);
            case SUMMON_BATS -> summonBats(context);
            case BLIGHT -> blight(context);
            case ERODE -> erode(context);
            case FEAR -> fear(context);
            case PULL_TO_OWNER -> pullToOwner(context);
            case PLACE_LILIES -> placeLilies(context);
            case ICE_SHELL -> iceShell(context);
            case PLACE_SNOW -> placeSnow(context);
            case HARM_INSECTS -> harmEntityTag(context, EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS, 12.0F);
            case LEVEL_LAND -> levelLand(context);
            case BREED_ANIMALS -> breedAnimals(context);
            case PULVERIZE_ROCK -> pulverizeRock(context);
            case RAISE_LAND -> raiseLand(context);
            case BUFF_UNDEAD -> buffUndead(context);
            case SPREAD_HARMFUL -> spreadHarmful(context);
            case STEAL_BENEFICIAL -> stealBeneficial(context);
            case PLACE_THORNS -> placeThorns(context);
            case RANDOM_TELEPORT -> randomTeleport(context);
            case TRANSPOSE_ORES -> transposeOres(context);
            case HARM_UNDEAD -> harmEntityTag(context, EntityTypeTags.SENSITIVE_TO_SMITE, 12.0F);
            case CURSE_UNDEAD -> curseUndead(context);
            case PLACE_VINES -> placeVines(context);
            case DISSIPATE_GAS -> dissipateGas(context);
            case DRAIN_RESERVES -> drainReserves(context);
            case EXTEND_EFFECTS -> extendEffects(context);
            case PLACE_WATER -> placeWater(context);
            case DARKNESS_PREY -> darknessPrey(context);
            case MOONLIGHT -> moonlight(context);
            case PART_WATER -> partWater(context);
            case PART_LAVA -> partLava(context);
            case PLANT_DROPS -> plantDrops(context);
            case SUMMON_POISON_TOADS -> summonPoisonToads(context);
            case RAISE_DEAD -> raiseDead(context);
            case APPLY_ABSORB_MAGIC -> applyMarker(context, BrewMarkerKind.ABSORB_MAGIC);
            case APPLY_ATTRACT_ARROWS -> applyMarker(context, BrewMarkerKind.ATTRACT_ARROWS);
            case BOTTLE_YIELD -> bottleYield(context);
            case APPLY_GAS_IMMUNITY -> applyMarker(context, BrewMarkerKind.BREW_GAS_IMMUNITY);
            case APPLY_ENDER_INHIBITION -> applyMarker(context, BrewMarkerKind.ENDER_INHIBITION);
            case APPLY_ILL_FITTING -> applyMarker(context, BrewMarkerKind.ILL_FITTING);
            case APPLY_INSANITY -> applyHex(context, HexKind.INSANITY);
            case APPLY_KEEP_EFFECTS -> applyMarker(
                context, BrewMarkerKind.KEEP_EFFECTS, Player.class::isInstance
            );
            case APPLY_KEEP_INVENTORY -> applyMarker(
                context, BrewMarkerKind.KEEP_INVENTORY, Player.class::isInstance
            );
            case APPLY_NIGHTMARE -> applyHex(context, HexKind.WAKING_NIGHTMARE);
            case APPLY_POISON_WEAPON -> applyMarker(context, BrewMarkerKind.POISON_WEAPON);
            case APPLY_REFLECT_ARROWS -> applyMarker(context, BrewMarkerKind.REFLECT_ARROWS);
            case APPLY_REFLECT_DAMAGE -> applyMarker(context, BrewMarkerKind.REFLECT_DAMAGE);
            case APPLY_REINCARNATE -> applyMarker(
                context, BrewMarkerKind.REINCARNATE, Animal.class::isInstance
            );
            case APPLY_REPEL_ATTACKER -> applyMarker(context, BrewMarkerKind.REPEL_ATTACKER);
            case APPLY_RESIZING -> applyMarker(context, BrewMarkerKind.RESIZING);
            case SHIFT_SEASONS -> shiftSeasons(context);
            case SUMMON_ABYSSAL_REGENT -> summonLeonardShade(context);
            case APPLY_TINT_SKIN -> applyTint(context);
            case APPLY_WEREWOLF_LOCK -> applyWerewolfLock(context);
            case APPLY_DISEASE -> applyMarker(context, BrewMarkerKind.DISEASE);
            case APPLY_INFECTION -> applyMarker(context, BrewMarkerKind.INFECTION);
            case APPLY_SINKING -> applyMarker(context, BrewMarkerKind.SINKING);
            case APPLY_SUNLIGHT_CURSE -> applyMarker(
                context,
                BrewMarkerKind.SUNLIGHT_CURSE,
                entity -> entity.typeHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)
            );
            case APPLY_VOLATILITY -> applyMarker(context, BrewMarkerKind.VOLATILITY);
            case SUMMON_OWLS -> summonOwls(context);
            case APPLY_CURSED_LEAPING -> applyMarker(context, BrewMarkerKind.CURSED_LEAPING);
            case APPLY_OVERHEATING -> applyMarker(context, BrewMarkerKind.OVERHEATING);
            case APPLY_SLEEPING -> applyMarker(context, BrewMarkerKind.SLEEPING);
            case APPLY_SNOW_TRAIL -> applyMarker(context, BrewMarkerKind.SNOW_TRAIL);
            case SPROUT_BRANCHES -> sproutBranches(context);
            case SUBSTITUTE_BLOCKS -> substituteBlocks(context);
            case APPLY_DEPTHS -> applyMarker(context, BrewMarkerKind.DEPTHS);
            case APPLY_GROTESQUE -> applyMarker(context, BrewMarkerKind.GROTESQUE);
            case SOLIDIFY_STONE -> solidify(context, Blocks.STONE.defaultBlockState());
            case SOLIDIFY_DIRT -> solidify(context, Blocks.DIRT.defaultBlockState());
            case SOLIDIFY_SAND -> solidify(context, Blocks.SAND.defaultBlockState());
            case SOLIDIFY_SANDSTONE -> solidify(context, Blocks.SANDSTONE.defaultBlockState());
            case SOLIDIFY_EROSION -> erodeHollowTears(context);
        };
    }

    private static ImpactResult grow(final ImpactContext context) {
        final int changed = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) -> {
            if (!(state.getBlock() instanceof BonemealableBlock growable)) {
                return false;
            }
            if (!BrewRules.canGrow(
                true,
                growable.isValidBonemealTarget(context.level(), pos, state),
                growable.isBonemealSuccess(context.level(), context.level().getRandom(), pos, state)
            )) {
                return false;
            }
            growable.performBonemeal(context.level(), context.level().getRandom(), pos, state);
            return true;
        });
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult extinguish(final ImpactContext context) {
        final int entities = (int) living(context).stream().filter(Entity::isOnFire).peek(Entity::clearFire).count();
        final int blocks = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) -> {
            final boolean fire = state.is(BlockTags.FIRE);
            final boolean extinguishable = state.is(BlockTags.CAMPFIRES)
                || state.is(BlockTags.CANDLES)
                || state.is(BlockTags.CANDLE_CAKES);
            final boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
            if (!BrewRules.shouldExtinguish(fire, extinguishable, lit)) {
                return false;
            }
            if (fire) {
                return context.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            return context.level().setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, false));
        });
        return new ImpactResult(entities, blocks, 0);
    }

    private static ImpactResult freeze(final ImpactContext context) {
        final int entities = (int) living(context).stream().peek(entity -> {
            entity.clearFire();
            entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), entity.getTicksRequiredToFreeze() + 100));
        }).count();
        final int blocks = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) -> {
            if (!BrewRules.shouldFreeze(
                state.canBeReplaced(),
                state.getFluidState().is(FluidTags.WATER),
                state.getFluidState().isSource()
            )) {
                return false;
            }
            return context.level().setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        });
        return new ImpactResult(entities, blocks, 0);
    }

    private static ImpactResult placeWebs(final ImpactContext context) {
        final int limit = Math.clamp((int) Math.ceil(context.radius() * context.radius() * 2.0F), 1, 96);
        final int changed = mutateArea(context, limit, (pos, state) -> {
            if (!BrewRules.canPlaceOnSurface(
                state.canBeReplaced(),
                context.level().getBlockState(pos.below()).isFaceSturdy(
                    context.level(), pos.below(), net.minecraft.core.Direction.UP
                )
            )) {
                return false;
            }
            return context.level().setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
        });
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult ignite(final ImpactContext context) {
        final int entities = (int) living(context).stream().peek(entity ->
            entity.igniteForSeconds(6.0F * context.potency())
        ).count();
        final int limit = Math.clamp((int) Math.ceil(context.radius() * context.radius()), 1, 64);
        final int blocks = mutateArea(context, limit, (pos, state) -> {
            if (!state.canBeReplaced()) {
                return false;
            }
            final BlockState fire = BaseFireBlock.getState(context.level(), pos);
            return fire.canSurvive(context.level(), pos) && context.level().setBlockAndUpdate(pos, fire);
        });
        return new ImpactResult(entities, blocks, 0);
    }

    private static ImpactResult explode(final ImpactContext context) {
        context.level().explode(
            context.directSource(),
            context.center().x,
            context.center().y,
            context.center().z,
            Math.clamp(context.potency() * 2.0F, 1.0F, 6.0F),
            false,
            Level.ExplosionInteraction.MOB
        );
        return ImpactResult.event();
    }

    private static ImpactResult moveRadially(final ImpactContext context, final boolean inward) {
        final List<LivingEntity> entities = living(context);
        entities.forEach(entity -> {
            entity.addDeltaMovement(BrewPhysics.radialVelocity(
                context.center(), entity.position(), context.potency(), inward
            ));
            entity.hurtMarked = true;
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult lift(final ImpactContext context) {
        final List<LivingEntity> entities = living(context);
        entities.forEach(entity -> {
            entity.addDeltaMovement(new Vec3(0.0, Math.clamp(context.potency(), 0.25F, 2.0F), 0.0));
            entity.resetFallDistance();
            entity.hurtMarked = true;
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult attractAnimals(final ImpactContext context) {
        final List<Animal> animals = animals(context);
        animals.forEach(animal -> animal.getNavigation().moveTo(
            context.center().x, context.center().y, context.center().z, 1.0 + context.potency() * 0.25
        ));
        return ImpactResult.entities(animals.size());
    }

    private static ImpactResult repelAnimals(final ImpactContext context) {
        final List<Animal> animals = animals(context);
        animals.forEach(animal -> {
            animal.getNavigation().stop();
            animal.addDeltaMovement(BrewPhysics.radialVelocity(
                context.center(), animal.position(), context.potency(), false
            ));
            animal.hurtMarked = true;
        });
        return ImpactResult.entities(animals.size());
    }

    private static ImpactResult fellLogs(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final Optional<BlockPos> origin = BrewArea.sphere(center, (int) Math.ceil(context.radius()))
            .filter(pos -> context.level().getBlockState(pos).is(BlockTags.LOGS))
            .min(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        if (origin.isEmpty()) {
            return ImpactResult.ZERO;
        }
        final int limit = Math.clamp((int) (64 * context.potency()), 16, MAX_CONNECTED_BLOCKS);
        final List<BlockPos> logs = BrewArea.connected(
            origin.orElseThrow(), limit, pos -> context.level().getBlockState(pos).is(BlockTags.LOGS)
        );
        final int changed = (int) logs.stream().filter(pos -> context.level().destroyBlock(
            pos, true, context.owner(), MAX_CONNECTED_BLOCKS
        )).count();
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult pruneLeaves(final ImpactContext context) {
        final int changed = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) ->
            state.is(BlockTags.LEAVES)
                && context.level().destroyBlock(pos, true, context.owner(), MAX_CONNECTED_BLOCKS)
        );
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult harvestCrops(final ImpactContext context) {
        final int changed = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) -> {
            final boolean crop = state.is(BlockTags.CROPS);
            if (!crop) {
                return false;
            }
            final Optional<IntegerProperty> age = ageProperty(state);
            final boolean mature = age.map(property -> {
                final List<Integer> values = property.getPossibleValues();
                return state.getValue(property).equals(values.getLast());
            }).orElse(true);
            if (!BrewRules.shouldHarvest(crop, age.isPresent(), mature)) {
                return false;
            }
            if (age.isEmpty()) {
                return context.level().destroyBlock(pos, true, context.owner(), MAX_CONNECTED_BLOCKS);
            }
            final IntegerProperty property = age.orElseThrow();
            final List<Integer> values = property.getPossibleValues();
            Block.dropResources(
                state,
                context.level(),
                pos,
                context.level().getBlockEntity(pos),
                context.owner(),
                ItemStack.EMPTY
            );
            return context.level().setBlockAndUpdate(pos, state.setValue(property, values.getFirst()));
        });
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult tillSoil(final ImpactContext context) {
        final int changed = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) ->
            BrewRules.shouldTill(state.is(BlockTags.DIRT), context.level().isEmptyBlock(pos.above()))
                && context.level().setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState())
        );
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult reveal(final ImpactContext context) {
        final int changed = (int) living(context).stream().filter(entity ->
            entity.isInvisible() || entity.hasEffect(MobEffects.INVISIBILITY)
        ).peek(entity -> {
            entity.setInvisible(false);
            entity.removeEffect(MobEffects.INVISIBILITY);
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300));
        }).count();
        return ImpactResult.entities(changed);
    }

    private static ImpactResult removeEffects(final ImpactContext context, final boolean beneficial) {
        final int changed = (int) living(context).stream().filter(entity -> {
            final List<MobEffectInstance> effects = List.copyOf(entity.getActiveEffects());
            final List<MobEffectInstance> removed = effects.stream()
                .filter(effect -> BrewRules.shouldRemoveEffect(
                    effect.getEffect().value().isBeneficial(), beneficial
                ))
                .toList();
            removed.forEach(effect -> entity.removeEffect(effect.getEffect()));
            return !removed.isEmpty();
        }).count();
        return ImpactResult.entities(changed);
    }

    private static ImpactResult removeNausea(final ImpactContext context) {
        final int changed = (int) living(context).stream().filter(entity -> entity.removeEffect(MobEffects.NAUSEA)).count();
        return ImpactResult.entities(changed);
    }

    private static ImpactResult harmTarget(
        final ImpactContext context,
        final Target target,
        final float baseDamage
    ) {
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> BrewTargeting.matches(entity, target))
            .toList();
        entities.forEach(entity -> entity.hurtServer(
            context.level(),
            context.directSource() != null && context.owner() != null
                ? context.level().damageSources().indirectMagic(context.directSource(), context.owner())
                : context.level().damageSources().magic(),
            baseDamage * context.potency()
        ));
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult weakenVampires(final ImpactContext context) {
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> BrewTargeting.matches(entity, Target.VAMPIRE))
            .toList();
        entities.forEach(entity -> entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1_200, 2)));
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult summonBats(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final int requested = Math.clamp((int) Math.ceil(context.potency() * 4.0F), 2, 12);
        int spawned = 0;
        for (int index = 0; index < requested; index++) {
            final BlockPos position = center.offset(index % 3 - 1, 1 + index / 6, index % 2 * 2 - 1);
            if (EntityTypes.BAT.spawn(context.level(), position, EntitySpawnReason.EVENT) != null) {
                spawned++;
            }
        }
        return new ImpactResult(spawned, 0, spawned > 0 ? 1 : 0);
    }

    private static ImpactResult blight(final ImpactContext context) {
        final int entities = (int) living(context).stream()
            .filter(entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.BLIGHT_VICTIMS))
            .peek(entity -> {
                entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 0));
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
            })
            .count();
        final int blocks = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) -> {
            if (state.is(WarlockeryTags.Blocks.BLIGHT_VEGETATION)) {
                return context.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            if (state.is(WarlockeryTags.Blocks.BLIGHT_SOILS)) {
                return context.level().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            }
            return false;
        });
        return new ImpactResult(entities, blocks, 0);
    }

    private static ImpactResult erode(final ImpactContext context) {
        final List<LivingEntity> entities = living(context);
        entities.forEach(entity -> {
            entity.hurtServer(
                context.level(),
                context.level().damageSources().magic(),
                4.0F * context.potency()
            );
            BrewMarkerState.apply(entity, BrewMarkerKind.EROSION);
            EquipmentSlot.VALUES.stream()
                .map(slot -> java.util.Map.entry(slot, entity.getItemBySlot(slot)))
                .filter(entry -> entry.getValue().isDamageableItem())
                .forEach(entry -> entry.getValue().hurtAndBreak(2, entity, entry.getKey()));
        });
        final int blocks = mutateArea(context, Math.clamp((int) (24 * context.potency()), 8, 96), (pos, state) ->
            state.is(WarlockeryTags.Blocks.BREW_ERODIBLE)
                && context.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
        );
        final BlockPos fluidPosition = BlockPos.containing(context.center());
        final boolean fluidPlaced = context.level().getBlockState(fluidPosition).canBeReplaced()
            && context.level().setBlockAndUpdate(fluidPosition, ModFluids.EROSION_SOURCE.get().defaultFluidState().createLegacyBlock());
        return new ImpactResult(entities.size(), blocks + (fluidPlaced ? 1 : 0), fluidPlaced ? 1 : 0);
    }

    private static ImpactResult fear(final ImpactContext context) {
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> entity != context.owner())
            .toList();
        entities.forEach(entity -> {
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            BrewMarkerState.setOrigin(entity, BrewMarkerKind.FEAR, BlockPos.containing(context.center()));
            entity.addDeltaMovement(BrewPhysics.radialVelocity(
                context.center(), entity.position(), context.potency(), false
            ));
            entity.hurtMarked = true;
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult pullToOwner(final ImpactContext context) {
        final Vec3 destination = context.owner() == null ? context.center() : context.owner().position();
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> entity != context.owner())
            .toList();
        entities.forEach(entity -> {
            entity.addDeltaMovement(BrewPhysics.radialVelocity(
                destination, entity.position(), context.potency(), true
            ));
            entity.hurtMarked = true;
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult placeLilies(final ImpactContext context) {
        final int blocks = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) -> {
            final BlockPos above = pos.above();
            final BlockState lily = Blocks.LILY_PAD.defaultBlockState();
            if (!BrewRules.shouldPlaceLily(
                state.getFluidState().is(FluidTags.WATER),
                state.getFluidState().isSource(),
                context.level().getBlockState(above).canBeReplaced(),
                lily.canSurvive(context.level(), above)
            )) {
                return false;
            }
            return context.level().setBlockAndUpdate(above, lily);
        });
        return ImpactResult.blocks(blocks);
    }

    private static ImpactResult iceShell(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final int radius = Math.clamp((int) Math.ceil(context.radius()), 2, 8);
        final int inner = Math.max(1, radius - 1);
        final long outerSquared = (long) radius * radius;
        final long innerSquared = (long) inner * inner;
        final List<BlockPos> shell = BrewArea.sphere(center, radius)
            .filter(pos -> {
                final double distance = pos.distSqr(center);
                return distance <= outerSquared && distance >= innerSquared;
            })
            .limit(768)
            .toList();
        final int changed = (int) shell.stream().filter(pos -> {
            final BlockState state = context.level().getBlockState(pos);
            return (state.canBeReplaced() || state.getFluidState().is(FluidTags.WATER))
                && context.level().setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
        }).count();
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult placeSnow(final ImpactContext context) {
        final int blocks = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) -> {
            final BlockState snow = Blocks.SNOW.defaultBlockState();
            return state.canBeReplaced()
                && snow.canSurvive(context.level(), pos)
                && context.level().setBlockAndUpdate(pos, snow);
        });
        return ImpactResult.blocks(blocks);
    }

    private static ImpactResult harmEntityTag(
        final ImpactContext context,
        final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag,
        final float damage
    ) {
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> entity.typeHolder().is(tag))
            .toList();
        entities.forEach(entity -> entity.hurtServer(
            context.level(), context.level().damageSources().magic(), damage * context.potency()
        ));
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult levelLand(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final int radius = Math.clamp((int) Math.ceil(context.radius()), 1, 8);
        final int targetY = center.getY() - 1;
        int changed = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                final Optional<BlockPos> surface = findSurface(context, center.offset(x, 0, z), radius);
                if (surface.isEmpty()) {
                    continue;
                }
                final BlockPos top = surface.orElseThrow();
                final BlockState fill = context.level().getBlockState(top);
                if (top.getY() > targetY) {
                    for (int y = top.getY(); y > targetY; y--) {
                        final BlockPos pos = new BlockPos(top.getX(), y, top.getZ());
                        if (!context.level().getBlockState(pos).is(WarlockeryTags.Blocks.BREW_LEVELABLE_TERRAIN)) {
                            break;
                        }
                        if (context.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) {
                            changed++;
                        }
                    }
                } else if (top.getY() < targetY) {
                    for (int y = top.getY() + 1; y <= targetY; y++) {
                        final BlockPos pos = new BlockPos(top.getX(), y, top.getZ());
                        if (!context.level().getBlockState(pos).canBeReplaced()) {
                            break;
                        }
                        if (context.level().setBlockAndUpdate(pos, fill)) {
                            changed++;
                        }
                    }
                }
            }
        }
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult breedAnimals(final ImpactContext context) {
        final Player owner = context.owner() instanceof Player player ? player : null;
        final List<Animal> candidates = animals(context).stream()
            .filter(animal -> animal.getAge() == 0 && animal.canFallInLove())
            .toList();
        candidates.forEach(animal -> {
            animal.setInLove(owner);
            animal.heal(2.0F);
        });
        return ImpactResult.entities(candidates.size());
    }

    private static ImpactResult pulverizeRock(final ImpactContext context) {
        final int changed = mutateArea(context, Math.clamp((int) (32 * context.potency()), 8, 128), (pos, state) -> {
            if (!state.is(WarlockeryTags.Blocks.BREW_PULVERIZABLE_ROCK)) {
                return false;
            }
            return context.level().setBlockAndUpdate(pos, pulverized(state));
        });
        return ImpactResult.blocks(changed);
    }

    private static BlockState pulverized(final BlockState state) {
        if (state.is(Blocks.STONE)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (state.is(Blocks.DEEPSLATE)) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (state.is(Blocks.SANDSTONE)) {
            return Blocks.SAND.defaultBlockState();
        }
        if (state.is(Blocks.RED_SANDSTONE)) {
            return Blocks.RED_SAND.defaultBlockState();
        }
        return Blocks.GRAVEL.defaultBlockState();
    }

    private static ImpactResult raiseLand(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final int radius = Math.clamp((int) Math.ceil(context.radius()), 1, 7);
        int changed = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                final Optional<BlockPos> surface = findSurface(context, center.offset(x, 0, z), radius);
                if (surface.isEmpty()) {
                    continue;
                }
                final BlockPos top = surface.orElseThrow();
                final BlockPos above = top.above();
                if (context.level().getBlockState(above).canBeReplaced()
                    && context.level().setBlockAndUpdate(above, context.level().getBlockState(top))) {
                    changed++;
                }
            }
        }
        return ImpactResult.blocks(changed);
    }

    private static Optional<BlockPos> findSurface(
        final ImpactContext context,
        final BlockPos column,
        final int radius
    ) {
        for (int y = column.getY() + radius; y >= column.getY() - radius; y--) {
            final BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (context.level().getBlockState(pos).is(WarlockeryTags.Blocks.BREW_LEVELABLE_TERRAIN)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private static ImpactResult buffUndead(final ImpactContext context) {
        if (!(context.owner() instanceof LivingEntity owner)) {
            return ImpactResult.ZERO;
        }
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> entity.typeHolder().is(EntityTypeTags.UNDEAD))
            .filter(entity -> entity == owner || CreatureBehaviorState.isOwnedBy(entity, owner.getUUID()))
            .toList();
        entities.forEach(entity -> {
            entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1_200, 1));
            entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1_200, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0));
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult spreadHarmful(final ImpactContext context) {
        final List<LivingEntity> entities = living(context);
        final Optional<LivingEntity> source = entities.stream()
            .filter(entity -> entity.getActiveEffects().stream().anyMatch(effect -> !effect.getEffect().value().isBeneficial()))
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(context.center())));
        if (source.isEmpty()) {
            return ImpactResult.ZERO;
        }
        final List<MobEffectInstance> harmful = source.orElseThrow().getActiveEffects().stream()
            .filter(effect -> !effect.getEffect().value().isBeneficial())
            .map(MobEffectInstance::new)
            .toList();
        final List<LivingEntity> recipients = entities.stream().filter(entity -> entity != source.orElseThrow()).toList();
        recipients.forEach(entity -> harmful.forEach(effect -> entity.addEffect(new MobEffectInstance(effect))));
        return ImpactResult.entities(recipients.size());
    }

    private static ImpactResult stealBeneficial(final ImpactContext context) {
        if (!(context.owner() instanceof LivingEntity owner)) {
            return ImpactResult.ZERO;
        }
        int affected = 0;
        for (LivingEntity entity : living(context)) {
            if (entity == owner) {
                continue;
            }
            final List<MobEffectInstance> stolen = entity.getActiveEffects().stream()
                .filter(effect -> effect.getEffect().value().isBeneficial())
                .map(MobEffectInstance::new)
                .toList();
            if (stolen.isEmpty()) {
                continue;
            }
            stolen.forEach(effect -> {
                entity.removeEffect(effect.getEffect());
                owner.addEffect(new MobEffectInstance(effect));
            });
            affected++;
        }
        return ImpactResult.entities(affected);
    }

    private static ImpactResult placeThorns(final ImpactContext context) {
        final int changed = mutateArea(context, 96, (pos, state) -> {
            if (!state.canBeReplaced()) {
                return false;
            }
            final BlockState support = context.level().getBlockState(pos.below());
            if (!support.is(WarlockeryTags.Blocks.BREW_THORN_SUPPORTS)) {
                return false;
            }
            final BlockState thorn = support.is(BlockTags.SAND)
                ? Blocks.CACTUS.defaultBlockState()
                : Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3);
            return thorn.canSurvive(context.level(), pos) && context.level().setBlockAndUpdate(pos, thorn);
        });
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult randomTeleport(final ImpactContext context) {
        final List<LivingEntity> entities = living(context);
        final double range = Math.clamp(context.radius() * 4.0, 8.0, 48.0);
        int moved = 0;
        for (LivingEntity entity : entities) {
            final double x = entity.getX() + (context.level().getRandom().nextDouble() - 0.5) * range * 2.0;
            final double y = entity.getY() + context.level().getRandom().nextInt(17) - 8;
            final double z = entity.getZ() + (context.level().getRandom().nextDouble() - 0.5) * range * 2.0;
            if (entity.randomTeleport(x, y, z, true)) {
                moved++;
            }
        }
        return ImpactResult.entities(moved);
    }

    private static ImpactResult transposeOres(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final List<BlockPos> ores = BrewArea.sphere(center, Math.clamp((int) Math.ceil(context.radius()), 1, 12))
            .filter(pos -> context.level().getBlockState(pos).is(WarlockeryTags.Blocks.BREW_TRANSPOSABLE_ORES))
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .limit(32)
            .toList();
        final List<BlockPos> destinations = BrewArea.sphere(center, 2)
            .filter(pos -> context.level().getBlockState(pos).canBeReplaced())
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .limit(ores.size())
            .toList();
        int moved = 0;
        for (int index = 0; index < Math.min(ores.size(), destinations.size()); index++) {
            final BlockPos source = ores.get(index);
            final BlockPos destination = destinations.get(index);
            final BlockState ore = context.level().getBlockState(source);
            final BlockState replacement = source.getY() < 0
                ? Blocks.DEEPSLATE.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
            if (context.level().setBlockAndUpdate(source, replacement)
                && context.level().setBlockAndUpdate(destination, ore)) {
                moved++;
            }
        }
        return ImpactResult.blocks(moved * 2);
    }

    private static ImpactResult curseUndead(final ImpactContext context) {
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> entity.typeHolder().is(EntityTypeTags.UNDEAD))
            .toList();
        entities.forEach(entity -> {
            entity.igniteForSeconds(30.0F * context.potency());
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1_200, 1));
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult placeVines(final ImpactContext context) {
        final List<Direction> directions = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        final int changed = mutateArea(context, 128, (pos, state) -> {
            if (!state.canBeReplaced()) {
                return false;
            }
            return directions.stream().filter(direction -> VineBlock.isAcceptableNeighbour(
                context.level(), pos.relative(direction), direction
            )).findFirst().map(direction -> context.level().setBlockAndUpdate(
                pos,
                Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(direction), true)
            )).orElse(false);
        });
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult dissipateGas(final ImpactContext context) {
        final int blocks = mutateArea(context, MAX_AREA_BLOCKS, (pos, state) ->
            state.is(BrewCompatibilityTags.Blocks.GASES)
                && context.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
        );
        final List<LivingEntity> spectral = living(context).stream()
            .filter(entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.SPECTRAL))
            .toList();
        spectral.forEach(entity -> entity.hurtServer(
            context.level(), context.level().damageSources().magic(), 12.0F * context.potency()
        ));
        return new ImpactResult(spectral.size(), blocks, blocks > 0 ? 1 : 0);
    }

    private static ImpactResult drainReserves(final ImpactContext context) {
        int affected = 0;
        for (LivingEntity entity : living(context)) {
            final List<MobEffectInstance> beneficial = entity.getActiveEffects().stream()
                .filter(effect -> effect.getEffect().value().isBeneficial())
                .toList();
            beneficial.forEach(effect -> entity.removeEffect(effect.getEffect()));
            final Set<ItemStack> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
            EquipmentSlot.VALUES.stream().map(entity::getItemBySlot).forEach(stacks::add);
            if (entity instanceof Player player) {
                stacks.addAll(player.getInventory().getNonEquipmentItems());
            }
            final int drained = stacks.stream().mapToInt(stack -> stack
                .getCapability(ForgeCapabilities.ENERGY)
                .map(storage -> storage.extractEnergy(50_000, false))
                .orElse(0)
            ).sum();
            if (!beneficial.isEmpty() || drained > 0) {
                affected++;
            }
        }
        return ImpactResult.entities(affected);
    }

    private static ImpactResult extendEffects(final ImpactContext context) {
        int affected = 0;
        for (LivingEntity entity : living(context)) {
            final List<MobEffectInstance> extendable = entity.getActiveEffects().stream()
                .filter(effect -> !effect.isInfiniteDuration())
                .toList();
            extendable.forEach(effect -> entity.addEffect(new MobEffectInstance(
                effect.getEffect(),
                BrewRules.extendedDuration(effect.getDuration()),
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.isVisible(),
                effect.showIcon()
            )));
            if (!extendable.isEmpty()) {
                affected++;
            }
        }
        return ImpactResult.entities(affected);
    }

    private static ImpactResult placeWater(final ImpactContext context) {
        final int limit = Math.clamp((int) (16 * context.potency()), 4, 48);
        final int changed = mutateArea(context, limit, (pos, state) ->
            state.canBeReplaced()
                && state.getFluidState().isEmpty()
                && context.level().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState())
        );
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult darknessPrey(final ImpactContext context) {
        final List<LivingEntity> allTargets = living(context);
        allTargets.forEach(entity -> BrewMarkerState.apply(entity, BrewMarkerKind.GRUES_PREY));
        final List<LivingEntity> entities = allTargets.stream().filter(entity -> BrewRules.isDarkEnoughForGrue(
            context.level().getMaxLocalRawBrightness(entity.blockPosition())
        )).toList();
        entities.forEach(entity -> {
            entity.hurtServer(
                context.level(), context.level().damageSources().magic(), 8.0F * context.potency()
            );
            entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 600, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 0));
        });
        return ImpactResult.entities(allTargets.size());
    }

    private static ImpactResult moonlight(final ImpactContext context) {
        final List<LivingEntity> entities = living(context).stream()
            .filter(entity -> BrewRules.isMoonlit(
                context.level().canSeeSky(entity.blockPosition()),
                context.level().getMoonBrightness(entity.blockPosition())
            ))
            .toList();
        entities.forEach(entity -> {
            final int amplifier = context.level().getMoonBrightness(entity.blockPosition()) > 0.75F ? 1 : 0;
            entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2_400, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.LUCK, 2_400, amplifier));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, amplifier));
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult partWater(final ImpactContext context) {
        final int changed = mutateArea(context, 128, (pos, state) -> {
            final boolean water = state.getFluidState().is(FluidTags.WATER);
            final boolean surface = context.level().getBlockState(pos.above()).canBeReplaced();
            return BrewRules.canPartFluid(water, state.getFluidState().isSource(), surface)
                && context.level().setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
        });
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult partLava(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final List<BlockPos> positions = BrewArea.sphere(center, (int) Math.ceil(context.radius()))
            .filter(pos -> {
                final BlockState state = context.level().getBlockState(pos);
                return BrewRules.canPartFluid(
                    state.getFluidState().is(FluidTags.LAVA),
                    state.getFluidState().isSource(),
                    context.level().getBlockState(pos.above()).canBeReplaced()
                );
            })
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .limit(96)
            .toList();
        final int changed = BrewWorldData.get(context.level()).replaceTemporarily(
            context.level(),
            positions,
            Blocks.BASALT.defaultBlockState(),
            context.level().getGameTime() + 400,
            96
        );
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult plantDrops(final ImpactContext context) {
        final AABB area = AABB.ofSize(
            context.center(), context.radius() * 2.0, context.radius(), context.radius() * 2.0
        );
        final BlockPos center = BlockPos.containing(context.center());
        final List<ItemEntity> drops = context.level().getEntitiesOfClass(
            ItemEntity.class,
            area,
            item -> item.isAlive() && item.getItem().getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof IPlantable
        );
        int planted = 0;
        for (ItemEntity drop : drops) {
            final BlockItem blockItem = (BlockItem) drop.getItem().getItem();
            final IPlantable plantable = (IPlantable) blockItem.getBlock();
            final Optional<BlockPos> ground = BrewArea.sphere(center, (int) Math.ceil(context.radius()))
                .filter(pos -> context.level().getBlockState(pos).canSustainPlant(
                    context.level(), pos, Direction.UP, plantable
                ))
                .filter(pos -> context.level().getBlockState(pos.above()).canBeReplaced())
                .filter(pos -> plantable.getPlant(context.level(), pos.above()).canSurvive(
                    context.level(), pos.above()
                ))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(drop.blockPosition())));
            if (ground.isEmpty()) {
                continue;
            }
            final BlockPos position = ground.orElseThrow().above();
            if (!context.level().setBlockAndUpdate(position, plantable.getPlant(context.level(), position))) {
                continue;
            }
            drop.getItem().shrink(1);
            if (drop.getItem().isEmpty()) {
                drop.discard();
            }
            planted++;
        }
        return new ImpactResult(planted, planted, 0);
    }

    private static ImpactResult summonPoisonToads(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        int spawned = 0;
        for (int index = 0; index < 4; index++) {
            final BlockPos position = center.offset(index % 2 * 2 - 1, 1, index / 2 * 2 - 1);
            if (ModEntities.ALL.get("toad").get().spawn(context.level(), position, EntitySpawnReason.EVENT) != null) {
                spawned++;
            }
        }
        final List<LivingEntity> targets = living(context).stream()
            .filter(entity -> entity != context.owner())
            .filter(entity -> !entity.typeHolder().is(WarlockeryTags.EntityTypes.HEX_TOADS))
            .toList();
        targets.forEach(entity -> entity.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1)));
        return new ImpactResult(targets.size() + spawned, 0, spawned > 0 ? 1 : 0);
    }

    private static ImpactResult raiseDead(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final Optional<LivingEntity> target = living(context).stream()
            .filter(entity -> entity != context.owner())
            .filter(entity -> !entity.typeHolder().is(EntityTypeTags.UNDEAD))
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(context.center())));
        int spawned = 0;
        for (int index = 0; index < 3; index++) {
            final BlockPos position = center.offset(index - 1, 1, index % 2 * 2 - 1);
            final Entity corpse = ModEntities.ALL.get("corpse").get().spawn(
                context.level(), position, EntitySpawnReason.EVENT
            );
            if (!(corpse instanceof Mob mob)) {
                continue;
            }
            mob.setPersistenceRequired();
            if (context.owner() instanceof LivingEntity owner) {
                CreatureBehaviorState.bind(mob, owner.getUUID());
            }
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1_200, 1));
            mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1_200, 0));
            target.ifPresent(mob::setTarget);
            spawned++;
        }
        return new ImpactResult(spawned, 0, spawned > 0 ? 1 : 0);
    }

    private static ImpactResult summonOwls(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final Optional<LivingEntity> target = living(context).stream()
            .filter(entity -> entity != context.owner())
            .filter(entity -> isBodegaTarget(
                entity.typeHolder().is(BrewCompatibilityTags.EntityTypes.BODEGA_TARGETS),
                entity instanceof Enemy,
                entity instanceof Mob mob && mob.getTarget() == context.owner(),
                context.owner() != null && CreatureBehaviorState.isOwnedBy(entity, context.owner().getUUID())
            ))
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(context.center())));
        int spawned = 0;
        for (int index = 0; index < 6; index++) {
            final BlockPos position = center.offset(index % 3 - 1, 1 + index / 3, index % 2 * 2 - 1);
            final Entity owl = ModEntities.ALL.get("owl").get().spawn(
                context.level(), position, EntitySpawnReason.EVENT
            );
            if (!(owl instanceof Mob mob)) {
                continue;
            }
            mob.setPersistenceRequired();
            if (context.owner() instanceof LivingEntity owner) {
                CreatureBehaviorState.bind(mob, owner.getUUID());
            }
            target.ifPresent(mob::setTarget);
            spawned++;
        }
        return new ImpactResult(spawned, 0, spawned > 0 ? 1 : 0);
    }

    static boolean isBodegaTarget(
        final boolean tagged,
        final boolean enemy,
        final boolean attackingOwner,
        final boolean ownerBound
    ) {
        return !ownerBound && (tagged || enemy || attackingOwner);
    }

    private static ImpactResult sproutBranches(final ImpactContext context) {
        final BlockState branch = BuiltInRegistries.BLOCK.getRandomElementOf(
            BrewCompatibilityTags.Blocks.SPROUTING_BRANCHES,
            context.level().getRandom()
        ).map(holder -> holder.value().defaultBlockState()).orElseGet(() ->
            ModBlocks.ALL.get("hex_log").get().defaultBlockState()
        );
        final Vec3 look = context.owner() instanceof LivingEntity owner ? owner.getLookAngle() : new Vec3(1.0, 0.0, 0.0);
        final int dx = Math.abs(look.x) >= Math.abs(look.z) ? (look.x < 0.0 ? -1 : 1) : 0;
        final int dz = dx == 0 ? (look.z < 0.0 ? -1 : 1) : 0;
        final BlockPos center = BlockPos.containing(context.center());
        final List<BlockPos> path = java.util.stream.IntStream.range(0, 16)
            .mapToObj(step -> center.offset(dx * step, step / 4, dz * step))
            .filter(context.level()::isLoaded)
            .filter(pos -> context.level().getBlockEntity(pos) == null)
            .filter(pos -> context.level().getBlockState(pos).canBeReplaced())
            .toList();
        final int changed = (int) path.stream()
            .filter(pos -> context.level().setBlockAndUpdate(pos, branch))
            .count();
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult substituteBlocks(final ImpactContext context) {
        final AABB area = AABB.ofSize(
            context.center(), context.radius() * 2.0, context.radius() * 1.5, context.radius() * 2.0
        );
        final Optional<ItemEntity> offered = context.level().getEntitiesOfClass(
            ItemEntity.class,
            area,
            item -> item.isAlive() && item.getItem().getItem() instanceof BlockItem
        ).stream().min(Comparator.comparingDouble(item -> item.distanceToSqr(context.center())));
        if (offered.isEmpty()) {
            return ImpactResult.ZERO;
        }
        final ItemEntity drop = offered.orElseThrow();
        final BlockItem blockItem = (BlockItem) drop.getItem().getItem();
        final BlockState replacement = blockItem.getBlock().defaultBlockState();
        final int limit = Math.min(64, drop.getItem().getCount());
        final int changed = mutateArea(context, limit, (pos, state) ->
            context.level().getBlockEntity(pos) == null
                && state.is(BrewCompatibilityTags.Blocks.SUBSTITUTABLE)
                && !state.is(replacement.getBlock())
                && replacement.canSurvive(context.level(), pos)
                && context.level().setBlockAndUpdate(pos, replacement)
        );
        drop.getItem().shrink(changed);
        if (drop.getItem().isEmpty()) {
            drop.discard();
        }
        return new ImpactResult(changed > 0 ? 1 : 0, changed, changed > 0 ? 1 : 0);
    }

    private static ImpactResult solidify(final ImpactContext context, final BlockState solid) {
        final int changed = mutateArea(context, 128, (pos, state) ->
            state.getFluidState().is(WarlockeryTags.Fluids.HOLLOW_TEARS)
                && state.getFluidState().isSource()
                && context.level().setBlockAndUpdate(pos, solid)
        );
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult erodeHollowTears(final ImpactContext context) {
        final BlockPos center = BlockPos.containing(context.center());
        final List<BlockPos> sources = BrewArea.sphere(center, (int) Math.ceil(context.radius()))
            .filter(pos -> context.level().getFluidState(pos).is(WarlockeryTags.Fluids.HOLLOW_TEARS))
            .filter(pos -> context.level().getFluidState(pos).isSource())
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .limit(16)
            .toList();
        final List<BlockPos> removable = sources.stream()
            .flatMap(source -> java.util.stream.IntStream.range(0, 32).mapToObj(source::below))
            .distinct()
            .filter(context.level()::isLoaded)
            .filter(pos -> context.level().getBlockEntity(pos) == null)
            .filter(pos -> {
                final BlockState state = context.level().getBlockState(pos);
                return !state.isAir() && state.getDestroySpeed(context.level(), pos) >= 0.0F;
            })
            .limit(128)
            .toList();
        final int changed = (int) removable.stream()
            .filter(pos -> context.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()))
            .count();
        return ImpactResult.blocks(changed);
    }

    private static ImpactResult applyMarker(final ImpactContext context, final BrewMarkerKind kind) {
        return applyMarker(context, kind, entity -> true);
    }

    private static ImpactResult applyMarker(
        final ImpactContext context,
        final BrewMarkerKind kind,
        final Predicate<LivingEntity> filter
    ) {
        return applyMarker(context, kind, filter, NO_CONFIGURATION);
    }

    private static ImpactResult applyMarker(
        final ImpactContext context,
        final BrewMarkerKind kind,
        final Predicate<LivingEntity> filter,
        final Consumer<LivingEntity> configured
    ) {
        final List<LivingEntity> entities = living(context).stream().filter(filter).toList();
        entities.forEach(entity -> {
            BrewMarkerState.apply(entity, kind);
            configured.accept(entity);
        });
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult applyHex(final ImpactContext context, final HexKind kind) {
        final List<LivingEntity> entities = living(context);
        entities.forEach(entity -> HexRuntime.apply(entity, kind, 2_400));
        return ImpactResult.entities(entities.size());
    }

    private static ImpactResult applyWerewolfLock(final ImpactContext context) {
        return applyMarker(
            context,
            BrewMarkerKind.WEREWOLF_LOCK,
            Player.class::isInstance,
            entity -> BrewMarkerState.lockCurrentForm((Player) entity)
        );
    }

    private static ImpactResult applyTint(final ImpactContext context) {
        return applyMarker(
            context,
            BrewMarkerKind.TINT_SKIN,
            entity -> true,
            entity -> BrewMarkerState.setColor(entity, BrewMarkerKind.TINT_SKIN, 0x7FBAB4)
        );
    }

    private static ImpactResult bottleYield(final ImpactContext context) {
        final int count = Math.clamp((int) Math.ceil(context.potency() * 3.0F), 1, 8);
        final ItemEntity bottles = new ItemEntity(
            context.level(),
            context.center().x,
            context.center().y + 0.25,
            context.center().z,
            new ItemStack(Items.GLASS_BOTTLE, count)
        );
        return context.level().addFreshEntity(bottles)
            ? new ImpactResult(0, 0, 1)
            : ImpactResult.ZERO;
    }

    private static ImpactResult shiftSeasons(final ImpactContext context) {
        final int season = BrewMarkerRules.season(context.level().getOverworldClockTime());
        final ImpactResult landscape = switch (season) {
            case 0 -> grow(context);
            case 1 -> grow(context).plus(extinguish(context));
            case 2 -> harvestCrops(context).plus(pruneLeaves(context));
            default -> freeze(context).plus(placeSnow(context));
        };
        final BlockPos center = BlockPos.containing(context.center());
        final int radius = Math.clamp((int) Math.ceil(context.radius()), 1, 8);
        final boolean biomeChanged = context.level().registryAccess().lookupOrThrow(Registries.BIOME)
            .getRandomElementOf(BrewCompatibilityTags.Biomes.season(season), context.level().getRandom())
            .map(biome -> {
                FillBiomeCommand.fill(
                    context.level(),
                    center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius),
                    biome
                );
                return true;
            })
            .orElse(false);
        return biomeChanged ? landscape.plus(ImpactResult.event()) : landscape;
    }

    private static ImpactResult summonLeonardShade(final ImpactContext context) {
        final BlockPos position = BlockPos.containing(context.center()).above();
        final Entity summoned = ModEntities.ALL.get("emberhorn_archfiend").get().spawn(
            context.level(), position, EntitySpawnReason.EVENT
        );
        if (!(summoned instanceof Mob mob)) {
            return ImpactResult.ZERO;
        }
        living(context).stream()
            .filter(entity -> entity != context.owner() && entity != mob)
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(context.center())))
            .ifPresent(mob::setTarget);
        mob.setPersistenceRequired();
        return new ImpactResult(1, 0, 1);
    }

    private static int mutateArea(
        final ImpactContext context,
        final int limit,
        final BlockMutation mutation
    ) {
        final BlockPos center = BlockPos.containing(context.center());
        final List<BlockPos> positions = BrewArea.sphere(center, (int) Math.ceil(context.radius()))
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
            .limit(MAX_AREA_BLOCKS)
            .toList();
        int changed = 0;
        for (BlockPos pos : positions) {
            if (changed >= Math.min(limit, MAX_AREA_BLOCKS)) {
                break;
            }
            if (mutation.apply(pos, context.level().getBlockState(pos))) {
                changed++;
            }
        }
        return changed;
    }

    private static List<LivingEntity> living(final ImpactContext context) {
        final AABB area = AABB.ofSize(
            context.center(), context.radius() * 2.0, context.radius() * 1.5, context.radius() * 2.0
        );
        final double radiusSquared = context.radius() * context.radius();
        return context.level().getEntitiesOfClass(
            LivingEntity.class,
            area,
            entity -> entity.isAlive() && entity.distanceToSqr(context.center()) <= radiusSquared
        );
    }

    private static List<Animal> animals(final ImpactContext context) {
        final AABB area = AABB.ofSize(
            context.center(), context.radius() * 2.0, context.radius() * 1.5, context.radius() * 2.0
        );
        final double radiusSquared = context.radius() * context.radius();
        return context.level().getEntitiesOfClass(
            Animal.class,
            area,
            animal -> animal.isAlive() && animal.distanceToSqr(context.center()) <= radiusSquared
        );
    }

    private static Optional<IntegerProperty> ageProperty(final BlockState state) {
        return state.getProperties().stream()
            .filter(IntegerProperty.class::isInstance)
            .map(IntegerProperty.class::cast)
            .filter(property -> property.getName().equals("age"))
            .findFirst();
    }

    @FunctionalInterface
    private interface BlockMutation {
        boolean apply(BlockPos pos, BlockState state);
    }

    private record ImpactContext(
        ServerLevel level,
        Vec3 center,
        float radius,
        float potency,
        @Nullable Entity directSource,
        @Nullable Entity owner
    ) {
    }

    public record ImpactResult(int affectedEntities, int changedBlocks, int emittedEvents) {
        public static final ImpactResult ZERO = new ImpactResult(0, 0, 0);

        public ImpactResult {
            if (affectedEntities < 0 || changedBlocks < 0 || emittedEvents < 0) {
                throw new IllegalArgumentException("Impact result counts cannot be negative");
            }
        }

        public ImpactResult plus(final ImpactResult other) {
            return new ImpactResult(
                affectedEntities + other.affectedEntities,
                changedBlocks + other.changedBlocks,
                emittedEvents + other.emittedEvents
            );
        }

        public static ImpactResult entities(final int count) {
            return new ImpactResult(count, 0, 0);
        }

        public static ImpactResult blocks(final int count) {
            return new ImpactResult(0, count, 0);
        }

        public static ImpactResult event() {
            return new ImpactResult(0, 0, 1);
        }
    }
}
