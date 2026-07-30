package com.kadamitas.warlockery;

import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModCreativeTabs;
import com.kadamitas.warlockery.registry.ModChunkTickets;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModGameTests;
import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModEffects;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.brew.BrewPersistentRuntime;
import com.kadamitas.warlockery.brew.custom.CustomBrewDefinitionManager;
import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.item.ResourceInteractionEvents;
import com.kadamitas.warlockery.item.FlyingBroomItem;
import com.kadamitas.warlockery.item.FancifulCharmRuntime;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.block.DreamWeaverRuntime;
import com.kadamitas.warlockery.block.BoundStatueData;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import com.kadamitas.warlockery.ritual.RitualEclipseData;
import com.kadamitas.warlockery.ritual.RitualWardData;
import com.kadamitas.warlockery.ritual.PriorIncarnationRuntime;
import com.kadamitas.warlockery.ritual.hex.HexRuntime;
import com.kadamitas.warlockery.ritual.hex.HexState;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.entity.CreatureCombat;
import com.kadamitas.warlockery.world.CreatureWorldIntegration;
import com.kadamitas.warlockery.config.WarlockeryConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Warlockery.MOD_ID)
public final class Warlockery {
    public static final String MOD_ID = "warlockery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Warlockery(final FMLJavaModLoadingContext context) {
        final var modBus = context.getModBusGroup();
        context.registerConfig(ModConfig.Type.SERVER, WarlockeryConfig.SPEC);
        ModFluids.TYPES.register(modBus);
        ModFluids.REGISTRY.register(modBus);
        ModEntities.REGISTRY.register(modBus);
        ModEffects.REGISTRY.register(modBus);
        ModItems.registerSpawnEggs(ModEntities.ALL);
        ModBlocks.REGISTRY.register(modBus);
        ModItems.REGISTRY.register(modBus);
        ModSounds.REGISTRY.register(modBus);
        ModBlockEntities.REGISTRY.register(modBus);
        ModChunkTickets.REGISTRY.register(modBus);
        ModCreativeTabs.REGISTRY.register(modBus);
        ModGameTests.REGISTRY.register(modBus);
        EntityAttributeCreationEvent.BUS.addListener(ModEntities::registerAttributes);
        SpawnPlacementRegisterEvent.BUS.addListener(ModEntities::registerSpawnPlacements);
        ModNetwork.init();
        BrewPersistentRuntime.registerEvents();
        MagicPathRuntime.registerEvents();
        AddReloadListenerEvent.BUS.addListener(event -> {
            event.addListener(RitualManager.INSTANCE);
            event.addListener(MachineRecipeManager.INSTANCE);
            event.addListener(CustomBrewDefinitionManager.INSTANCE);
        });
        TickEvent.LevelTickEvent.Post.BUS.addListener(event -> {
            if (event.level() instanceof ServerLevel level) {
                RitualSessionData.get(level).tick(level);
                RitualWardData.get(level).tick(level);
                RitualEclipseData.get(level).tick(level);
                BoundStatueData.get(level).tick(level);
                CreatureWorldIntegration.tick(level);
            }
        });
        TickEvent.PlayerTickEvent.Post.BUS.addListener(event -> {
            SupernaturalState.tick(event.player());
            EquipmentSetEffects.tick(event.player());
            InfernalPactEffects.tick(event.player());
            FlyingBroomItem.tickFlight(event.player());
        });
        LivingDamageEvent.BUS.addListener(SupernaturalState::handleDamage);
        LivingDamageEvent.BUS.addListener(DollItem::handleDamage);
        LivingDamageEvent.BUS.addListener(CreatureCombat::handleDamage);
        LivingDamageEvent.BUS.addListener(EquipmentSetEffects::handleDamage);
        LivingDamageEvent.BUS.addListener(FancifulCharmRuntime::handleDamage);
        LivingDamageEvent.BUS.addListener(RitualWardData::handleDamage);
        LivingEvent.LivingTickEvent.BUS.addListener(HexRuntime::tick);
        LivingEntityUseItemEvent.Finish.BUS.addListener(CustomBrewRuntime::handleFinishUse);
        LivingDropsEvent.BUS.addListener(ResourceInteractionEvents::handleDrops);
        LivingDropsEvent.BUS.addListener(HexRuntime::handleDrops);
        LivingDropsEvent.BUS.addListener(PriorIncarnationRuntime::handleDrops);
        ProjectileImpactEvent.BUS.addListener(ResourceInteractionEvents::handleProjectileImpact);
        PlayerWakeUpEvent.BUS.addListener(DreamWeaverRuntime::handleWake);
        PlayerEvent.Clone.BUS.addListener(SupernaturalState::copyAfterClone);
        PlayerEvent.Clone.BUS.addListener(HexState::copyAfterClone);
        LOGGER.info("Loading Warlockery for Minecraft 26.2");
    }

}
