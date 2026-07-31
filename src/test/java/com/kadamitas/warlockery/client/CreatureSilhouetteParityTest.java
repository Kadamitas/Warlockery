package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

final class CreatureSilhouetteParityTest {
    @Test
    void requestedSpiritVariantsUseDedicatedNonHumanoidGeometry() {
        final ModelPart sigil = modelFor("umbral_sigil");
        assertTrue(sigil.getChild("right_arm").isEmpty());
        assertSolid(sigil, "sigil_outer_slash", "sigil_outer_backslash", "upper_right_tablet", "lower_left_tablet");
        assertTrue(solidPartCount(sigil) >= 12);

        final ModelPart watcher = modelFor("eldritch_watcher");
        assertTrue(watcher.getChild("right_arm").isEmpty());
        assertSolid(watcher, "watcher_eye", "watcher_pupil", "right_watcher_front_tentacle",
            "left_watcher_back_tentacle", "watcher_rear_eye", "watcher_rear_pupil",
            "right_watcher_lateral_eye", "right_watcher_side_eye", "left_watcher_lower_eye");
        assertTrue(solidPartCount(watcher) >= 22);

        final ModelPart poltergeist = modelFor("poltergeist");
        assertTrue(poltergeist.getChild("right_hind_leg").isEmpty());
        assertSolid(poltergeist, "debris_top", "right_debris_side", "left_debris_low", "debris_back", "debris_front");
        assertTrue(solidPartCount(poltergeist) >= 15);
    }

    @Test
    void verdantCreaturesHaveRootsCrownsAndAsymmetricBranches() {
        final ModelPart ent = modelFor("ent");
        assertSolid(ent, "ent_crown_branch", "ent_reaching_branch", "right_ent_branch_tip", "left_ent_canopy",
            "right_ent_outer_leaves", "right_ent_root_flare");

        final ModelPart mandrake = modelFor("mandrake");
        assertSolid(mandrake, "mandrake_center_leaf", "right_mandrake_leaf_fan", "left_mandrake_outer_leaf",
            "mandrake_mouth", "right_mandrake_root_arm", "left_mandrake_root_toe");

        final ModelPart dreamroot = modelFor("dreamroot");
        assertSolid(dreamroot, "dreamroot_stem", "dream_bulb", "right_outer_dream_petals",
            "right_dream_crown_spire", "left_outer_trailing_root", "right_root_fan");

        final ModelPart colossus = modelFor("bramble_colossus");
        assertSolid(colossus, "bramble_core_mass", "right_bramble_pauldrons", "left_bramble_hook_claw",
            "right_bramble_root_foot");
    }

    @Test
    void werewolfHasDigitigradeLegsBroadForearmsAndClaws() {
        final ModelPart werewolf = modelFor("werewolf");
        assertSolid(werewolf, "right_wolf_shoulder", "wolf_mane", "left_wolf_forearm", "right_wolf_claw",
            "left_wolf_hock", "right_wolf_foot");
        assertTrue(solidPartCount(werewolf) >= 18);
    }

    @Test
    void occultHumanoidsUseLayeredGarmentsAndBodyMassInsteadOfPlainBipeds() {
        assertSolid(modelFor("vampire"), "vampire_cape_mantle", "right_vampire_cape_panel",
            "left_vampire_coat_tail", "right_high_collar");
        assertSolid(modelFor("blood_thrall"), "thrall_torso_mass", "right_thrall_shoulders",
            "left_thrall_gauntlet", "right_iron_shackle");
        assertSolid(modelFor("corpse"), "corpse_back_mass", "right_corpse_shoulder", "grave_cairn",
            "burial_board");
        assertSolid(modelFor("werewolf_hunter"), "hunter_coat_mantle", "right_hunter_coat_panel",
            "left_hunter_bracer", "silver_crossbow_bow");
        assertSolid(modelFor("lycan_villager"), "village_vest", "right_village_sleeve",
            "left_village_coat_tail", "right_village_boot");
    }

    @Test
    void huntersGoblinsAndDeathHaveReadableEquipmentSilhouettes() {
        assertSolid(modelFor("thorned_pursuer"), "right_pursuer_antler_branch", "pursuer_branch_frame",
            "left_pursuer_leaf_mantle", "right_vine_whip");
        assertSolid(modelFor("hobgoblin"), "miner_cap", "work_vest", "prospector_satchel", "tail");
        assertSolid(modelFor("goblin"), "miner_cap", "work_vest", "ore_satchel", "tail");
        assertTrue(solidPartCount(modelFor("goblin")) <= 14);
        assertTrue(solidPartCount(modelFor("hobgoblin")) <= 14);
        assertSolid(modelFor("death"), "death_mantle", "right_death_robe_panel", "death_robe_hem",
            "scythe_staff", "scythe_hook");
    }

    @Test
    void bossesRetainDistinctWeaponsArmorAndLowerBodyPlans() {
        assertSolid(modelFor("demon"), "right_demon_pauldrons", "left_demon_bracer", "demon_warhammer");
        assertSolid(modelFor("emberhorn_archfiend"), "archfiend_chestplate", "right_archfiend_gauntlet",
            "archfiend_maul");
        assertSolid(modelFor("naamah"), "right_front_leg", "left_middle_hind_leg",
            "right_matriarch_crown_tine", "left_upper_blade");
        assertSolid(modelFor("abyssal_regent"), "right_wing", "left_outer_abyssal_tentacle", "tidal_staff");
        assertSolid(modelFor("ironbound_sentinel"), "sentinel_chassis", "right_sentinel_shield",
            "sentinel_hammer");
    }

    private static ModelPart modelFor(final String id) {
        final CreatureVisualProfile visual = new CreatureVisualProfile(
            0.8F,
            1.8F,
            CreatureVisualProfile.Archetype.HUMANOID
        );
        return ArcaneCreatureModel.createLayer(CreatureModelProfile.forEntity(id, visual)).bakeRoot();
    }

    private static void assertSolid(final ModelPart root, final String... names) {
        List.of(names).forEach(name -> assertFalse(root.getChild(name).isEmpty(), name));
    }

    private static long solidPartCount(final ModelPart root) {
        return root.getAllParts().stream().filter(part -> !part.isEmpty()).count();
    }
}
