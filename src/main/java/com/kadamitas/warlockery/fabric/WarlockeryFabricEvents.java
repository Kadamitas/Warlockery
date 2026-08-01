package com.kadamitas.warlockery.fabric;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.BoundStatueData;
import com.kadamitas.warlockery.block.DreamWeaverRuntime;
import com.kadamitas.warlockery.block.StatueWardData;
import com.kadamitas.warlockery.block.VoidBrambleOwnershipData;
import com.kadamitas.warlockery.brew.BrewPersistentRuntime;
import com.kadamitas.warlockery.brew.custom.CustomBrewDefinitionManager;
import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import com.kadamitas.warlockery.brew.custom.CustomBrewTriggerData;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.dream.SpiritManifestationState;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.dream.SpiritWorldState;
import com.kadamitas.warlockery.entity.CreatureCombat;
import com.kadamitas.warlockery.entity.EntRuntime;
import com.kadamitas.warlockery.fabric.event.BlockBreakContext;
import com.kadamitas.warlockery.fabric.event.BreakSpeedContext;
import com.kadamitas.warlockery.fabric.event.LivingDamageContext;
import com.kadamitas.warlockery.fabric.event.LivingDropsContext;
import com.kadamitas.warlockery.fabric.event.PlayerCloneContext;
import com.kadamitas.warlockery.fabric.event.ProjectileImpactContext;
import com.kadamitas.warlockery.fabric.event.ProjectileSelectionContext;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.item.FancifulCharmRuntime;
import com.kadamitas.warlockery.item.FlyingBroomItem;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.item.ParasyticLouseItem;
import com.kadamitas.warlockery.item.ResourceInteractionEvents;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.magic.ImpContractRuntime;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.magic.SymbolMagicRuntime;
import com.kadamitas.warlockery.ritual.HellRiftData;
import com.kadamitas.warlockery.ritual.marriage.MarriageRuntime;
import com.kadamitas.warlockery.ritual.PriorIncarnationRuntime;
import com.kadamitas.warlockery.ritual.RitualEclipseData;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import com.kadamitas.warlockery.ritual.RitualWardData;
import com.kadamitas.warlockery.ritual.hex.HexRuntime;
import com.kadamitas.warlockery.ritual.hex.HexState;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.world.VillageGuardRuntime;
import com.kadamitas.warlockery.world.WarlockVillagerFarming;
import com.kadamitas.warlockery.world.CreatureWorldIntegration;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

public final class WarlockeryFabricEvents {
    private static boolean initialized;

    private WarlockeryFabricEvents() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        WarlockeryFabricItemEvents.initialize();
        registerReloadListeners();
        ServerTickEvents.END_LEVEL_TICK.register(WarlockeryFabricEvents::tickLevel);
        ServerLivingEntityEvents.AFTER_DEATH.register(WarlockeryFabricEvents::afterDeath);
        ServerPlayerEvents.COPY_FROM.register(WarlockeryFabricEvents::copyPlayerData);
        ServerPlayerEvents.JOIN.register(FlyingBroomItem::handleLogin);
        ServerPlayerEvents.LEAVE.register(FlyingBroomItem::handleLogout);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) ->
            EquipmentSetEffects.handleEntityJoinLevel(entity, level, entity.tickCount > 0));
        EntitySleepEvents.STOP_SLEEPING.register((entity, position) -> {
            if (entity instanceof ServerPlayer player) {
                DreamWeaverRuntime.handleWake(player, position, player.getSleepTimer() < 100);
            }
        });
        PlayerBlockBreakEvents.BEFORE.register(WarlockeryFabricEvents::beforeBlockBreak);
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            final ItemStack heldItem = player.getItemInHand(hand);
            if (SupernaturalProgressionRuntime.handleInteract(serverPlayer, target, heldItem)
                || VillageGuardRuntime.handleInteract(serverPlayer, target, heldItem)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
                CustomBrewTriggerData.handleBlockUse(serverLevel, hit.getBlockPos(), serverPlayer);
            }
            return InteractionResult.PASS;
        });
    }

    private static void registerReloadListeners() {
        final ResourceLoader loader = ResourceLoader.get(PackType.SERVER_DATA);
        loader.registerReloadListener(id("rituals"), RitualManager.INSTANCE);
        loader.registerReloadListener(id("machine_recipes"), MachineRecipeManager.INSTANCE);
        loader.registerReloadListener(id("custom_brews"), CustomBrewDefinitionManager.INSTANCE);
    }

    private static Identifier id(final String path) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path);
    }

    private static void tickLevel(final ServerLevel level) {
        SpiritWorldRuntime.tickLevel(level);
        MagicPathRuntime.tickLevel(level);
        BrewPersistentRuntime.tickLevel(level);
        RitualSessionData.get(level).tick(level);
        RitualWardData.get(level).tick(level);
        RitualEclipseData.get(level).tick(level);
        HellRiftData.get(level).tick(level);
        BoundStatueData.get(level).tick(level);
        StatueWardData.get(level).tick(level);
        CreatureWorldIntegration.tick(level);
    }

    public static void dispatchLivingTick(final LivingEntity living) {
        if (!(living.level() instanceof ServerLevel)) {
            return;
        }
        BrewPersistentRuntime.tick(living);
        HexRuntime.tick(living);
        if (living instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
            WarlockVillagerFarming.handleTick(villager);
        }
        if (living instanceof ServerPlayer player) {
            SpiritWorldRuntime.tickPlayer(player);
            MagicPathRuntime.tick(player);
            SupernaturalProgressionRuntime.tick(player);
            EquipmentSetEffects.tick(player);
            InfernalPactEffects.tick(player);
            MarriageRuntime.tick(player);
        }
    }

    private static void afterDeath(final LivingEntity entity, final DamageSource source) {
        FlyingBroomItem.handleDeath(entity);
        BrewPersistentRuntime.handleDeath(entity);
        MagicPathRuntime.handleDeath(entity, source);
        SupernaturalProgressionRuntime.handleDeath(entity, source);
        SeerCovenRuntime.handleDeath(entity);
    }

    private static void copyPlayerData(
        final ServerPlayer original,
        final ServerPlayer player,
        final boolean alive
    ) {
        final PlayerCloneContext context = new PlayerCloneContext(original, player, !alive);
        SpiritWorldState.copyAfterClone(context);
        SpiritManifestationState.copyAfterClone(context);
        MagicPathState.copyAfterClone(context);
        SupernaturalProgressionRuntime.copyAfterClone(context);
        HexState.copyAfterClone(context);
        BrewPersistentRuntime.handleClone(context);
    }

    private static boolean beforeBlockBreak(
        final net.minecraft.world.level.Level level,
        final Player player,
        final net.minecraft.core.BlockPos position,
        final BlockState state,
        final net.minecraft.world.level.block.entity.BlockEntity blockEntity
    ) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        final BlockBreakContext context = new BlockBreakContext(serverLevel, serverPlayer, position, state);
        ImpContractRuntime.handleBlockBreak(context);
        if (!context.isCanceled()) {
            SupernaturalProgressionRuntime.handleBlockBreak(context);
            EntRuntime.handleLogBreak(context);
            VoidBrambleOwnershipData.handleBreak(context);
        }
        return !context.isCanceled();
    }

    public static LivingDamageContext dispatchDamage(
        final LivingEntity entity,
        final DamageSource source,
        final float amount
    ) {
        final LivingDamageContext context = new LivingDamageContext(entity, source, amount);
        SupernaturalProgressionRuntime.handleHurt(context);
        BrewPersistentRuntime.handleDamage(context);
        SpiritWorldRuntime.handleDamage(context);
        MagicPathRuntime.handleDamage(context);
        SupernaturalState.handleDamage(context);
        SupernaturalProgressionRuntime.handleDamage(context);
        CreatureCombat.handleDamage(context);
        EquipmentSetEffects.handleDamage(context);
        FancifulCharmRuntime.handleDamage(context);
        ParasyticLouseItem.handleDamage(context);
        RitualWardData.handleDamage(context);
        DollItem.handleDamage(context);
        return context;
    }

    public static void dispatchDrops(
        final LivingEntity entity,
        final DamageSource source,
        final List<ItemEntity> drops
    ) {
        final LivingDropsContext context = new LivingDropsContext(entity, source, drops);
        ResourceInteractionEvents.handleDrops(context);
        HexRuntime.handleDrops(context);
        PriorIncarnationRuntime.handleDrops(context);
        BrewPersistentRuntime.handleDrops(context);
    }

    public static boolean dispatchProjectileImpact(final Projectile projectile, final HitResult hitResult) {
        final ProjectileImpactContext context = new ProjectileImpactContext(projectile, hitResult);
        BrewPersistentRuntime.handleProjectileImpact(context);
        ResourceInteractionEvents.handleProjectileImpact(context);
        SymbolMagicRuntime.handleProjectileImpact(context);
        return context.shouldSkipEntity();
    }

    public static float dispatchBreakSpeed(final Player player, final BlockState state, final float speed) {
        final BreakSpeedContext context = new BreakSpeedContext(player, state, speed);
        SupernaturalProgressionRuntime.handleBreakSpeed(context);
        return context.getNewSpeed();
    }

    public static ItemStack dispatchProjectileSelection(final LivingEntity entity, final ItemStack projectile) {
        final ProjectileSelectionContext context = new ProjectileSelectionContext(entity, projectile);
        EquipmentSetEffects.handleGetProjectile(context);
        return context.getProjectileItemStack();
    }

    public static void dispatchFinishedItemUse(final LivingEntity entity, final ItemStack consumedItem) {
        CustomBrewRuntime.handleFinishUse(entity, consumedItem);
    }

    public static boolean blocksTeleport(final LivingEntity entity) {
        return BrewPersistentRuntime.cancelTeleport(entity);
    }
}
