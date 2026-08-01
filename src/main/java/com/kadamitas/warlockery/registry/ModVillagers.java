package com.kadamitas.warlockery.registry;

import com.google.common.collect.ImmutableSet;
import com.kadamitas.warlockery.Warlockery;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Set;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Blocks;

public final class ModVillagers {
    public static final ResourceKey<PoiType> WARLOCK_STILL_KEY = ResourceKey.create(
        Registries.POINT_OF_INTEREST_TYPE,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "warlock_still")
    );
    public static final ResourceKey<VillagerProfession> WARLOCK_KEY = ResourceKey.create(
        Registries.VILLAGER_PROFESSION,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "warlock")
    );
    public static final RegistrationHandle<PoiType> WARLOCK_STILL = RegistrationHandle.external("warlock_still");
    public static final RegistrationHandle<VillagerProfession> WARLOCK = RegistrationHandle.create(
        "warlock",
        () -> new VillagerProfession(
            Component.translatable("entity.warlockery.villager.warlock"),
            holder -> holder.is(WARLOCK_STILL_KEY),
            holder -> holder.is(WARLOCK_STILL_KEY),
            ImmutableSet.of(
                ModItems.ALL.get("seedsbelladonna").get(),
                ModItems.ALL.get("seedsmandrake").get(),
                ModItems.ALL.get("seedswormwood").get(),
                ModItems.ALL.get("seedswolfsbane").get()
            ),
            ImmutableSet.of(Blocks.FARMLAND),
            SoundEvents.VILLAGER_WORK_CLERIC,
            Int2ObjectMap.ofEntries(
                Int2ObjectMap.entry(1, tradeSet(1)),
                Int2ObjectMap.entry(2, tradeSet(2)),
                Int2ObjectMap.entry(3, tradeSet(3)),
                Int2ObjectMap.entry(4, tradeSet(4)),
                Int2ObjectMap.entry(5, tradeSet(5))
            )
        )
    );

    private ModVillagers() {
    }

    public static void register() {
        WARLOCK_STILL.bind(
            BuiltInRegistries.POINT_OF_INTEREST_TYPE,
            PoiHelper.register(
                WARLOCK_STILL.id(),
                1,
                1,
                Set.copyOf(ModBlocks.ALL.get("distilleryidle").get().getStateDefinition().getPossibleStates())
            )
        );
        WARLOCK.register(BuiltInRegistries.VILLAGER_PROFESSION);
    }

    private static ResourceKey<TradeSet> tradeSet(final int level) {
        return ResourceKey.create(
            Registries.TRADE_SET,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "warlock/level_" + level)
        );
    }
}
