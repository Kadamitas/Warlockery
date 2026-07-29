package com.kadamitas.warlockery;

import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModCreativeTabs;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModGameTests;
import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.registry.ModFluids;
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
import com.kadamitas.warlockery.compat.neoforge.WarlockeryCapabilities;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

@Mod(Warlockery.MOD_ID)
public final class Warlockery {
    public static final String MOD_ID = "warlockery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Warlockery(final IEventBus modBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, WarlockeryConfig.SPEC);
        ModFluids.TYPES.register(modBus);
        ModFluids.REGISTRY.register(modBus);
        ModEntities.REGISTRY.register(modBus);
        ModItems.registerSpawnEggs(ModEntities.ALL);
        ModBlocks.REGISTRY.register(modBus);
        ModItems.REGISTRY.register(modBus);
        ModSounds.REGISTRY.register(modBus);
        ModBlockEntities.REGISTRY.register(modBus);
        ModCreativeTabs.REGISTRY.register(modBus);
        ModGameTests.REGISTRY.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModEntities::registerSpawnPlacements);
        modBus.addListener(WarlockeryCapabilities::register);
        ModNetwork.init(modBus);
        BrewPersistentRuntime.registerEvents();
        MagicPathRuntime.registerEvents();
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> {
            event.addListener(Identifier.fromNamespaceAndPath(MOD_ID, "rituals"), RitualManager.INSTANCE);
            event.addListener(Identifier.fromNamespaceAndPath(MOD_ID, "machine_recipes"), MachineRecipeManager.INSTANCE);
            event.addListener(Identifier.fromNamespaceAndPath(MOD_ID, "custom_brews"), CustomBrewDefinitionManager.INSTANCE);
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                RitualSessionData.get(level).tick(level);
                RitualWardData.get(level).tick(level);
                RitualEclipseData.get(level).tick(level);
                BoundStatueData.get(level).tick(level);
                CreatureWorldIntegration.tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            SupernaturalState.tick(event.getEntity());
            EquipmentSetEffects.tick(event.getEntity());
            InfernalPactEffects.tick(event.getEntity());
            FlyingBroomItem.tickFlight(event.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> SupernaturalState.handleDamage(event));
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> DollItem.handleDamage(event));
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> CreatureCombat.handleDamage(event));
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> EquipmentSetEffects.handleDamage(event));
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> FancifulCharmRuntime.handleDamage(event));
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> RitualWardData.handleDamage(event));
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Post event) -> HexRuntime.tick(event));
        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish event) -> CustomBrewRuntime.handleFinishUse(event));
        NeoForge.EVENT_BUS.addListener((LivingDropsEvent event) -> ResourceInteractionEvents.handleDrops(event));
        NeoForge.EVENT_BUS.addListener((LivingDropsEvent event) -> HexRuntime.handleDrops(event));
        NeoForge.EVENT_BUS.addListener((LivingDropsEvent event) -> PriorIncarnationRuntime.handleDrops(event));
        NeoForge.EVENT_BUS.addListener((ProjectileImpactEvent event) -> ResourceInteractionEvents.handleProjectileImpact(event));
        NeoForge.EVENT_BUS.addListener((PlayerWakeUpEvent event) -> DreamWeaverRuntime.handleWake(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> SupernaturalState.copyAfterClone(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> HexState.copyAfterClone(event));
        LOGGER.info("Loading Warlockery for Minecraft 26.2 with NeoForge");
    }

}
