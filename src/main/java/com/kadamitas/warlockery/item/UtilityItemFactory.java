package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ContentFactory;
import com.kadamitas.warlockery.registry.FactoryCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.component.BundleContents;

public final class UtilityItemFactory {
    private static final FactoryCatalog<Item.Properties, Item> FACTORIES = new FactoryCatalog<>(
        "utility item",
        createFactories()
    );

    private UtilityItemFactory() {
    }

    public static Set<String> ids() {
        return FACTORIES.ids();
    }

    public static boolean supports(final String id) {
        return FACTORIES.supports(id);
    }

    public static Item create(final Item.Properties properties, final String id) {
        return FACTORIES.create(id, properties);
    }

    private static Map<String, ContentFactory<Item.Properties, Item>> createFactories() {
        final Map<String, ContentFactory<Item.Properties, Item>> factories = new LinkedHashMap<>();
        factories.put("divinerlava", properties -> new FluidDivinerItem(properties.durability(128), FluidTags.LAVA, "lava"));
        factories.put("divinerwater", properties -> new FluidDivinerItem(properties.durability(128), FluidTags.WATER, "water"));
        factories.put("boline", ShearsItem::new);
        factories.put("biomenote", BiomeNoteItem::new);
        factories.put("playercompass", PlayerCompassItem::new);
        factories.put("shelfcompass", ShelfCompassItem::new);
        factories.put("brewbag", properties -> new BrewSatchelItem(
            properties.stacksTo(1).component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
        ));
        factories.put("hornofthehunt", properties -> new SummoningFocusItem(
            properties,
            () -> ModEntities.ALL.get("thorned_pursuer").get(),
            false
        ));
        factories.put("archfiends_urn", ArchfiendsUrnItem::new);
        factories.put("ingredient_fool_skull", properties -> new SummoningFocusItem(
            properties.stacksTo(1),
            () -> ModEntities.ALL.get("pale_steed").get(),
            true
        ));
        factories.put("ingredient_soul_of_torment", properties -> new TormentSoulItem(properties.stacksTo(1).durability(64)));
        factories.put("ingredient_infernal_animus", InfernalAnimusItem::new);
        factories.put("ingredient_broom", properties -> new BroomItem(properties.durability(128)));
        factories.put("ingredient_broom_enchanted", properties -> new FlyingBroomItem(properties.durability(512)));
        factories.put("ruby_slippers", HomewardSlippersItem::new);
        factories.put("mysticbranch", ArcaneFocusItem::new);
        factories.put("sungrenade", SunGrenadeItem::new);
        factories.put("deathshand", HandOfDeathItem::new);
        factories.put("hedge_crones_hat", HedgeCroneHatItem::new);
        factories.put("spectralstone", SpectralStoneItem::new);
        factories.put("ingredient_necro_stone", NecromanticFocusItem::new);
        factories.put("ingredient_waystone", properties -> new WaystoneItem(properties, WaystoneItem.Kind.BASE));
        factories.put("ingredient_waystone_bound", properties -> new WaystoneItem(properties, WaystoneItem.Kind.POSITION));
        factories.put("ingredient_waystone_creature_bound", properties -> new WaystoneItem(properties, WaystoneItem.Kind.CREATURE));
        factories.put("ingredient_seer_stone", SeerStoneItem::new);
        factories.put("bitingbelt", BitingBeltItem::new);
        factories.put("glassgoblet", BloodGobletItem::new);
        factories.put("beast_speech_charm", properties -> new BeastSpeechCharmItem(properties, false));
        factories.put("silver_tongue_charm", properties -> new BeastSpeechCharmItem(properties, true));
        factories.put("ingredient_door_key", properties -> new RowanKeyItem(properties, 1));
        factories.put("ingredient_door_keyring", properties -> new RowanKeyItem(properties, RowanKeyItem.UNLIMITED_CAPACITY));
        factories.put("mirror", MirrorItem::new);
        factories.put("replication_staff", ReplicationStaffItem::new);
        factories.put("replication_charge", ReplicationChargeItem::new);
        factories.put("universal_antidote", UniversalAntidoteItem::new);
        factories.put("ingredient_warm_blood", WarmBloodItem::new);
        factories.put("wolftoken", ProgressionTokenItem::new);
        ManualProfile.ids().forEach(id -> factories.put(
            id,
            properties -> new ManualItem(properties, ManualProfile.find(id).orElseThrow())
        ));
        return factories;
    }
}
