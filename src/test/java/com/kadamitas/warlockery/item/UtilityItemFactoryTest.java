package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class UtilityItemFactoryTest {
    @Test
    void catalogUsesDedicatedModernUtilityImplementations() {
        final Set<String> base = Set.of(
            "divinerlava",
            "divinerwater",
            "biomenote",
            "playercompass",
            "shelfcompass",
            "brewbag",
            "hornofthehunt",
            "archfiends_urn",
            "ingredient_fool_skull",
            "ingredient_soul_of_torment",
            "ingredient_infernal_animus",
            "ingredient_broom",
            "ingredient_broom_enchanted",
            "ruby_slippers",
            "mysticbranch",
            "sungrenade",
            "spectralstone",
            "ingredient_necro_stone",
            "ingredient_waystone",
            "ingredient_waystone_bound",
            "ingredient_waystone_creature_bound",
            "ingredient_seer_stone",
            "bitingbelt",
            "glassgoblet",
            "beast_speech_charm",
            "silver_tongue_charm",
            "ingredient_door_key",
            "ingredient_door_keyring",
            "mirror",
            "replication_staff",
            "replication_charge",
            "wolftoken",
            "hedge_crones_hat",
            "ingredient_warm_blood",
            "universal_antidote",
            "boline",
            "deathshand"
        );
        final Set<String> expected = java.util.stream.Stream.concat(base.stream(), ManualProfile.ids().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(expected, UtilityItemFactory.ids());
    }

    @Test
    void supportLookupIsExplicit() {
        assertTrue(UtilityItemFactory.supports("playercompass"));
        assertTrue(UtilityItemFactory.supports("mirror"));
        assertFalse(UtilityItemFactory.supports("missing_utility"));
        assertThrows(IllegalArgumentException.class, () ->
            UtilityItemFactory.create(new net.minecraft.world.item.Item.Properties(), "missing_utility")
        );
    }
}
