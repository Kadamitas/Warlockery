package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.HandOfDeathItem;
import com.kadamitas.warlockery.item.HedgeCroneHatRules;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Secondary equipment behaviors beyond the primary equipment slices: Hunter set hex rejection,
 * lycanthropy prevention, protection-doll suppression and werewolf damage bonus; silver piece scaling;
 * Hedge Crone reserve evasion; girdle/quiver pairing; Death's Hand disguise branches; earmuff Nausea;
 * Seeping Shoes water movement. Only prerequisites are staged; native input and ordinary ticks produce outcomes.
 */
public final class EquipmentSecondaryAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final AABB AREA = new AABB(-20, 96, -20, 21, 114, 21);
    private static final Vec3 START = new Vec3(0.5, 100, -3.5);
    private static final List<String> HUNTER_BASE = List.of("werewolf_hunter_hat", "werewolf_hunter_coat", "werewolf_hunter_leggings", "werewolf_hunter_boots");
    private static final List<String> HUNTER_SILVERED = List.of("werewolf_hunter_hat_silvered", "werewolf_hunter_coat_silvered", "werewolf_hunter_leggings_silvered", "werewolf_hunter_boots_silvered");
    private static final List<String> SILVER = List.of("silverhelm", "silverchestplate", "silverleggings", "silverboots");
    private static final List<Scenario> CASES = List.of(
        new Scenario("hunter_hex_rejection", List.of("werewolf_hunter_hat", "hexing_doll"),
            "A complete base Hunter set on the bound victim intercepts a native remote hexing-doll prick; every worn piece pays one durability; the unarmored control is hurt",
            List.of("Rite of Blindness interception", "silvered and dawn set variants", "acquisition")),
        new Scenario("hunter_lycanthropy_prevention", List.of("werewolf_hunter_hat"),
            "A single hunter piece on the victim blocks the curse spread by a transformed level-ten werewolf player's native melee attacks; the unarmored control is cursed",
            List.of("villager victims", "cure of an existing curse", "acquisition")),
        new Scenario("hunter_doll_suppression", List.of("werewolf_hunter_hat", "tool_mending_doll"),
            "A natively self-bound mending doll repairs equipment for the control but stops helping once the complete Hunter set is worn",
            List.of("lethal-guard suppression", "hex-guard suppression", "doll-guard corruption suppression")),
        new Scenario("hunter_silvered_werewolf_bonus", List.of("werewolf_hunter_hat_silvered"),
            "A real werewolf strike on the complete Silvered set is reduced compared with the base set control and ignites the attacker",
            List.of("dawn set vampire branch", "burn duration", "acquisition")),
        new Scenario("silver_multi_piece_scaling", List.of("silverhelm", "silverchestplate", "silverleggings", "silverboots"),
            "Silver retaliation against a real werewolf hit grows with one, two and four worn pieces and each worn piece pays durability",
            List.of("three-piece configuration", "non-werewolf control beyond the primary slice", "acquisition")),
        new Scenario("hedge_hat_reserve_evasion", List.of("hedge_crones_hat"),
            "Without an infusion every native Harming drink lands; with an active path a damaging hit eventually spends eight reserve and a successful landing cancels that hit",
            List.of("zero-reserve refusal", "landing failure branch", "creeper target clearing", "acquisition")),
        new Scenario("hedge_hat_processing_yield", List.of("hedge_crones_hat"),
            "Two independent one-in-four extra brew copies when a kettle brewed by the wearer finishes",
            List.of("requires the native kettle brewing walkthrough fixture; not executed in this suite")),
        new Scenario("girdle_quiver_synergy", List.of("forgewardens_girdle", "stonebrokers_quiver"),
            "A natively worn girdle grants Resistance I only while another player wearing the quiver is within twelve blocks",
            List.of("quiver wearer as the rendered player", "exact twelve-block boundary", "acquisition")),
        new Scenario("deaths_hand_advanced_disguise", List.of("deathshand", "deathscowl"),
            "Overlay messages accompany the scythe toggle; hunger drain stops when toggled back or when a piece is natively removed; the incomplete outfit refuses",
            List.of("glint rendering", "death impersonation encounters", "acquisition")),
        new Scenario("earmuffs_nausea", List.of("earmuffs"),
            "Native pufferfish Nausea persists for the control and is cleared after natively equipping Earmuffs",
            List.of("ritual hexes", "other effects", "acquisition")),
        new Scenario("seeping_shoes_depth_strider", List.of("seepingshoes"),
            "Native walking through a water trench covers more distance in Seeping Shoes than in plain leather boots",
            List.of("swimming", "acquisition")));
    private final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;
    private UUID fixture;
    private volatile DamageWatch damageWatch;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("equipment-secondary-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final DamageWatch watch = damageWatch;
                if (watch != null && watch.player.level().getServer() == server) watch.tick();
            });
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            final String requested = System.getProperty("warlockery.equipmentSecondaryCases", "").trim();
            final Set<String> selected = requested.isEmpty() ? Set.of()
                : Set.copyOf(Arrays.stream(requested.split(",")).map(String::trim).toList());
            for (Scenario scenario : CASES) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", "NOT_RUN");
                row.put("items", scenario.items());
                row.put("contract", scenario.contract());
                row.put("not_run", scenario.notRun());
                rows.put(scenario.id(), row);
            }
            check(rows.keySet().containsAll(selected), "Unknown equipmentSecondaryCases selection " + selected);
            write(false);
            for (Scenario scenario : CASES) {
                if (!selected.isEmpty() && !selected.contains(scenario.id())) continue;
                if (scenario.id().equals("hedge_hat_processing_yield")) {
                    rows.get(scenario.id()).put("reason", "Native kettle brewing is a separate walkthrough fixture; this receipt claims nothing about brew yields.");
                    continue;
                }
                active = scenario.id();
                row().put("status", "RUNNING");
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    world.getConnection().waitForChunksRender();
                    try {
                        stage(context);
                        for (String item : scenario.items()) readBook(context, item);
                        switch (scenario.id()) {
                            case "hunter_hex_rejection" -> hunterHexRejection(context);
                            case "hunter_lycanthropy_prevention" -> hunterLycanthropy(context);
                            case "hunter_doll_suppression" -> hunterDollSuppression(context);
                            case "hunter_silvered_werewolf_bonus" -> hunterSilveredBonus(context);
                            case "silver_multi_piece_scaling" -> silverScaling(context);
                            case "hedge_hat_reserve_evasion" -> hedgeEvasion(context);
                            case "girdle_quiver_synergy" -> girdleQuiver(context);
                            case "deaths_hand_advanced_disguise" -> deathsHand(context);
                            case "earmuffs_nausea" -> earmuffs(context);
                            case "seeping_shoes_depth_strider" -> seepingShoes(context);
                            default -> throw new AssertionError(scenario.id());
                        }
                        screenshot(context, scenario.id() + "-outcome");
                        row().put("status", "PASSED");
                    } catch (UnsupportedOperationException blocked) {
                        row().put("status", "NOT_RUN");
                        row().put("reason", blocked.toString());
                        try { screenshot(context, scenario.id() + "-fixture-blocked"); } catch (Throwable capture) { blocked.addSuppressed(capture); }
                    } catch (Throwable failure) {
                        row().put("status", "FAILED");
                        row().put("failure", failure.toString());
                        failures.add(scenario.id() + ": " + failure);
                        try { screenshot(context, scenario.id() + "-failure"); } catch (Throwable capture) { failure.addSuppressed(capture); }
                    } finally {
                        damageWatch = null;
                        release(context);
                        removeFixture();
                        row().put("finished_at", System.currentTimeMillis());
                        write(false);
                    }
                } finally { world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_EQUIPMENT_SECONDARY_SCENARIOS_COMPLETED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Equipment secondary evidence: " + evidence, failure);
        } finally { damageWatch = null; }
    }

    // ------------------------------------------------------------------ scenarios

    private void hunterHexRejection(final ClientGameTestContext context) throws Exception {
        final UUID victim = spawnFixturePlayer(context, new Vec3(0.5, 100, 0.5));
        server(player -> { for (String id : HUNTER_BASE) wearOnFixture(player, id); });
        hold(context, new ItemStack(ModItems.ALL.get("hexing_doll").get()));
        bindFixture(context);
        server(player -> player.teleportTo(0.5, 100, -8));
        ready(context);
        final Map<String, Integer> durabilityBefore = value(player -> armorDurability(fixturePlayer(player)));
        airUse(context, false);
        await(context, player -> player.getMainHandItem().getDamageValue() == 1, 60, "Native remote prick spends one doll charge");
        context.waitTicks(5);
        final Map<String, Integer> durabilityAfter = value(player -> armorDurability(fixturePlayer(player)));
        check(value(player -> fixturePlayer(player).getHealth() == 20.0F), "Complete Hunter set must intercept the hexing-doll prick without injury");
        for (String id : HUNTER_BASE) check(durabilityAfter.get(id) == durabilityBefore.get(id) + 1, "Each worn hunter piece must pay exactly one durability: " + id);
        row().put("protected_prick", Map.of("victim_health", 20.0F, "durability_before", durabilityBefore, "durability_after", durabilityAfter));
        screenshot(context, active + "-protected");
        server(player -> {
            final ServerPlayer other = fixturePlayer(player);
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) other.setItemSlot(slot, ItemStack.EMPTY);
            other.setHealth(20); other.damageCooldownTime = 0;
        });
        context.waitTicks(25);
        airUse(context, false);
        await(context, player -> fixturePlayer(player).getHealth() <= 16.0F && player.getMainHandItem().getDamageValue() == 2, 60,
            "The same native prick must hurt the unarmored control victim");
        row().put("control_prick", Map.of("victim_health", value(player -> fixturePlayer(player).getHealth())));
        row().put("fixture", "Rendered Survival caster; connected synthetic ServerPlayer victim wearing a staged complete base Hunter set. Binding and the prick use native focus/doll input from eight blocks. Real multiplayer transport is not certified.");
        row().put("victim", victim.toString());
    }

    private void hunterLycanthropy(final ClientGameTestContext context) throws Exception {
        spawnFixturePlayer(context, new Vec3(0.5, 100, 0.5));
        server(player -> {
            SupernaturalState.setForm(player, SupernaturalForm.WEREWOLF);
            SupernaturalProgression.setLevel(player, SupernaturalProgression.Path.WEREWOLF, 10);
            SupernaturalProgression.setWerewolfShape(player, WerewolfShape.WOLFMAN);
            wearOnFixture(player, "werewolf_hunter_hat");
            // Forty maximum health keeps every punch survivable while a twenty-health start is near-fatal (25%) after one Wolfman strike.
            fixturePlayer(player).getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        });
        context.waitTicks(25);
        // A fresh world starts on a full-moon night, so the moon rule may force the Wolf shape; any transformed shape qualifies.
        row().put("attacker_state", value(player -> Map.of("form", SupernaturalState.getForm(player).name(),
            "level", SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF),
            "shape", SupernaturalProgression.werewolfShape(player).name())));
        check(value(player -> SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF
            && SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF) == 10
            && SupernaturalProgression.werewolfShape(player) != WerewolfShape.HUMAN), "Staged attacker is a transformed level-ten werewolf: " + row().get("attacker_state"));
        hold(context, ItemStack.EMPTY);
        final int protectedHits = punchFixtureUntilCursed(context, 32);
        check(protectedHits == 32 && value(player -> SupernaturalState.getForm(fixturePlayer(player)) == SupernaturalForm.NONE),
            "Thirty-two near-fatal native werewolf punches must never curse a victim wearing one hunter piece");
        row().put("protected_hits", protectedHits);
        row().put("protected_victim_form", value(player -> SupernaturalState.getForm(fixturePlayer(player)).name()));
        screenshot(context, active + "-protected");
        server(player -> fixturePlayer(player).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
        final int controlHits = punchFixtureUntilCursed(context, 48);
        check(value(player -> SupernaturalState.getForm(fixturePlayer(player)) == SupernaturalForm.WEREWOLF),
            "The unarmored control victim must be cursed by the same native punches within forty-eight attempts (one-in-four per hit)");
        row().put("control_hits_until_cursed", controlHits);
        row().put("fixture", "Rendered player staged as level-ten Wolfman werewolf (Strength III from its own tick); connected synthetic ServerPlayer victim with forty maximum health and sixteen health so each native punch is near-fatal but survivable. Victim is re-positioned and re-healed between punches. The curse roll is one in four per qualifying hit.");
    }

    private int punchFixtureUntilCursed(final ClientGameTestContext context, final int maximum) {
        int hits = 0;
        while (hits < maximum && !value(player -> SupernaturalState.getForm(fixturePlayer(player)) == SupernaturalForm.WEREWOLF)) {
            server(player -> {
                final ServerPlayer other = fixturePlayer(player);
                other.teleportTo(0.5, 100, 0.5); other.setDeltaMovement(Vec3.ZERO);
                other.setHealth(16.0F); other.damageCooldownTime = 0;
                player.teleportTo(0.5, 100, -1.5); player.setDeltaMovement(Vec3.ZERO);
            });
            ready(context);
            aimFixture(context);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            await(context, player -> fixturePlayer(player).getHealth() < 16.0F || SupernaturalState.getForm(fixturePlayer(player)) == SupernaturalForm.WEREWOLF,
                30, "Each native punch must actually hurt the victim");
            hits++;
            context.waitTicks(8);
        }
        return hits;
    }

    private void hunterDollSuppression(final ClientGameTestContext context) throws Exception {
        hold(context, new ItemStack(ModItems.ALL.get("tool_mending_doll").get()));
        airUse(context, false);
        await(context, player -> DollItem.isBoundTo(player.getMainHandItem(), player), 60, "Native self binding of the mending doll");
        server(player -> {
            final ItemStack damaged = new ItemStack(Items.IRON_PICKAXE); damaged.setDamageValue(5);
            player.setItemSlot(EquipmentSlot.OFFHAND, damaged); player.inventoryMenu.broadcastChanges();
        });
        await(context, player -> player.getOffhandItem().getDamageValue() < 5, 100, "Control: the bound doll repairs the offhand pickaxe during ordinary ticks");
        row().put("control_repaired_to", value(player -> player.getOffhandItem().getDamageValue()));
        // Keep the natively bound doll in the inventory (hotbar slot 9) while the armor pieces pass through slot 1.
        server(player -> { player.getInventory().setItem(8, player.getMainHandItem().copy()); player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
        for (String id : HUNTER_BASE) equip(context, id);
        server(player -> {
            final ItemStack damaged = new ItemStack(Items.IRON_PICKAXE); damaged.setDamageValue(5);
            player.setItemSlot(EquipmentSlot.OFFHAND, damaged); player.inventoryMenu.broadcastChanges();
        });
        final int dollCharges = value(player -> player.getInventory().getItem(8).getDamageValue());
        check(value(player -> DollItem.isBoundTo(player.getInventory().getItem(8), player)), "Bound doll remains in the inventory for the suppressed phase");
        context.waitTicks(100);
        check(value(player -> player.getOffhandItem().getDamageValue() == 5), "Complete Hunter set must stop the bound mending doll from repairing");
        check(value(player -> player.getInventory().getItem(8).getDamageValue() == dollCharges), "Suppressed doll must not spend charges");
        row().put("suppressed_pickaxe_damage", 5);
        row().put("fixture", "Native air use self-binds the doll; a five-point damaged iron pickaxe is staged in the off hand. The Hunter set is put on through native inventory clicks. Ordinary inventory ticks alone repair or fail to repair.");
    }

    private void hunterSilveredBonus(final ClientGameTestContext context) throws Exception {
        for (String id : HUNTER_BASE) equip(context, id);
        final Hit base = werewolfHit(context);
        final boolean baseBurning = value(player -> living(player, base.attacker()).isOnFire());
        row().put("base_set_hit", base);
        row().put("base_set_attacker_burning", baseBurning);
        stage(context);
        for (String id : HUNTER_SILVERED) equip(context, id);
        final Hit silvered = werewolfHit(context);
        final boolean silveredBurning = value(player -> living(player, silvered.attacker()).isOnFire());
        row().put("silvered_set_hit", silvered);
        row().put("silvered_set_attacker_burning", silveredBurning);
        check(!baseBurning, "Base Hunter set control must not ignite the werewolf");
        check(silveredBurning, "Complete Silvered set must ignite the striking werewolf");
        check(silvered.playerDamage() < base.playerDamage(), "Silvered set must reduce the werewolf's actual strike below the base set control");
        row().put("fixture", "Rendered Survival wearer equips sets natively; a live werewolf with fixed attack strength is provoked by a native punch, then its normal AI strikes. Base set is the control; Silvered adds the werewolf branch.");
    }

    private void silverScaling(final ClientGameTestContext context) throws Exception {
        final Map<String, Object> results = new LinkedHashMap<>();
        final List<List<String>> configurations = List.of(SILVER.subList(0, 1), SILVER.subList(0, 2), SILVER);
        final List<Float> retaliation = new ArrayList<>();
        for (List<String> pieces : configurations) {
            if (!pieces.equals(configurations.getFirst())) stage(context);
            for (String id : pieces) equip(context, id);
            final Hit hit = werewolfHit(context);
            final float bite = hit.attackerHealthBefore() - hit.attackerHealth();
            for (String id : pieces) check(hit.armorDamage().getOrDefault(id, 0) >= 1, "Worn silver piece must pay durability: " + id);
            results.put(pieces.size() + "_pieces", Map.of("hit", hit, "observed_retaliation", bite));
            retaliation.add(bite);
            try { screenshot(context, active + "-" + pieces.size() + "-pieces"); } catch (Exception failure) { throw new AssertionError(failure); }
        }
        row().put("configurations", results);
        check(retaliation.get(0) > 0, "One silver piece must still injure the werewolf that struck");
        check(retaliation.get(1) > retaliation.get(0) && retaliation.get(2) > retaliation.get(1),
            "Retaliation must grow with two and four worn silver pieces: " + retaliation);
        row().put("note", "Raw retaliation equals the worn piece count before the werewolf's own damage rules; the observed health loss is after those rules.");
    }

    private void hedgeEvasion(final ClientGameTestContext context) throws Exception {
        equip(context, "hedge_crones_hat");
        final List<Float> control = new ArrayList<>();
        for (int drink = 0; drink < 4; drink++) control.add(harmingDrink(context).lost());
        check(control.stream().allMatch(lost -> lost > 0), "Without an infusion every native Harming drink must land: " + control);
        row().put("control_damage", control);
        row().put("control_reserve", "no active path; hat cannot evade");
        server(player -> MagicPathState.grantPermanent(player, MagicPath.LIGHT));
        final List<Map<String, Object>> attempts = new ArrayList<>();
        Map<String, Object> evasion = null;
        for (int drink = 0; drink < 24 && evasion == null; drink++) {
            final int reserveBefore = value(player -> MagicPathState.reserve(player, MagicPath.LIGHT));
            final Vec3 before = value(Entity::position);
            final Drink result = harmingDrink(context);
            final int reserveAfter = value(player -> MagicPathState.reserve(player, MagicPath.LIGHT));
            final double moved = value(player -> player.position().distanceTo(before));
            final Map<String, Object> attempt = new LinkedHashMap<>();
            attempt.put("health_lost", result.lost()); attempt.put("reserve_before", reserveBefore);
            attempt.put("reserve_after", reserveAfter); attempt.put("moved", moved);
            attempts.add(attempt);
            if (reserveAfter == reserveBefore - HedgeCroneHatRules.RESERVE_COST) evasion = attempt;
            else check(reserveAfter == reserveBefore && result.lost() > 0, "A non-evading drink must land without spending reserve");
        }
        row().put("attempts", attempts);
        check(evasion != null, "Twenty-four native Harming drinks with an active path must include at least one eight-reserve evasion attempt (one in four each)");
        row().put("evasion", evasion);
        check((double) evasion.get("moved") > 0.5 && (float) evasion.get("health_lost") == 0.0F,
            "A successful evasion landing must relocate the wearer and cancel the drink's damage");
        row().put("fixture", "Hat is put on natively; Harming potions are staged in the hotbar and drunk natively (thirty-two ticks each). Light attunement is a staged prerequisite. Reserve, health and position are observed after every drink.");
    }

    private void girdleQuiver(final ClientGameTestContext context) throws Exception {
        spawnFixturePlayer(context, new Vec3(0.5, 100, 4.5));
        equip(context, "forgewardens_girdle");
        context.waitTicks(45);
        check(value(player -> !player.hasEffect(MobEffects.RESISTANCE) && !fixturePlayer(player).hasEffect(MobEffects.RESISTANCE)),
            "Control: a girdle without a nearby quiver wearer grants no Resistance");
        server(player -> wearOnFixture(player, "stonebrokers_quiver"));
        await(context, player -> player.hasEffect(MobEffects.RESISTANCE) && player.getEffect(MobEffects.RESISTANCE).getAmplifier() == 0, 45,
            "Girdle wearer gains Resistance I once another player wears the quiver within twelve blocks");
        context.waitTicks(25);
        row().put("paired", Map.of("distance", value(player -> player.distanceTo(fixturePlayer(player))),
            "synthetic_quiver_wearer_resistance", value(player -> fixturePlayer(player).hasEffect(MobEffects.RESISTANCE))));
        screenshot(context, active + "-paired");
        server(player -> fixturePlayer(player).teleportTo(0.5, 100, 15.5));
        await(context, player -> !player.hasEffect(MobEffects.RESISTANCE), 80, "Resistance lapses after the quiver wearer moves beyond twelve blocks");
        row().put("separated_distance", value(player -> player.distanceTo(fixturePlayer(player))));
        // Reverse roles so the shared blessing is verified on the rendered player from the quiver side as well.
        stage(context);
        server(player -> {
            fixturePlayer(player).teleportTo(0.5, 100, 4.5);
            fixturePlayer(player).setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            wearOnFixture(player, "forgewardens_girdle");
        });
        equip(context, "stonebrokers_quiver");
        await(context, player -> player.hasEffect(MobEffects.RESISTANCE) && player.getEffect(MobEffects.RESISTANCE).getAmplifier() == 0, 45,
            "Quiver wearer gains Resistance I once another player wears the girdle within twelve blocks");
        row().put("reversed", Map.of("rendered_quiver_wearer_resistance", true, "distance", value(player -> player.distanceTo(fixturePlayer(player)))));
        row().put("fixture", "Rendered player natively wears the girdle (then the quiver); a connected synthetic ServerPlayer wears the staged counterpart four blocks away, then is moved fifteen blocks away. The synthetic player is not a real client, so its own Resistance is recorded as an observation only.");
    }

    private void deathsHand(final ClientGameTestContext context) throws Exception {
        for (String id : List.of("deathscowl", "deathsrobe", "deathsfeet")) equip(context, id);
        hold(context, new ItemStack(ModItems.ALL.get("deathshand").get()));
        server(player -> player.getFoodData().setFoodLevel(20));
        airUse(context, false);
        await(context, player -> HandOfDeathItem.isScythe(player.getMainHandItem()), 30, "Complete native disguise permits the scythe change");
        awaitOverlay(context, "message.warlockery.death_hand.scythe");
        final int food = value(player -> player.getFoodData().getFoodLevel());
        await(context, player -> player.getFoodData().getFoodLevel() <= food - 2, 60, "Active scythe drains hunger");
        row().put("scythe_drain", Map.of("before", food, "after", value(player -> player.getFoodData().getFoodLevel())));
        airUse(context, false);
        await(context, player -> !HandOfDeathItem.isScythe(player.getMainHandItem()), 30, "Second native use changes the scythe back");
        awaitOverlay(context, "message.warlockery.death_hand.hand");
        final int stable = value(player -> player.getFoodData().getFoodLevel());
        context.waitTicks(60);
        check(value(player -> player.getFoodData().getFoodLevel() == stable), "Control: hand form drains no hunger over sixty ticks");
        row().put("hand_form_hunger_stable", stable);
        airUse(context, false);
        await(context, player -> HandOfDeathItem.isScythe(player.getMainHandItem()), 30, "Scythe re-activated for the removal branch");
        moveInventorySlot(context, 5, 37);
        await(context, player -> player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), 30, "Native inventory click removes the hood");
        server(player -> player.getFoodData().setFoodLevel(18));
        context.waitTicks(60);
        check(value(player -> player.getFoodData().getFoodLevel() == 18 && HandOfDeathItem.isScythe(player.getMainHandItem())),
            "Scythe flag remains but an incomplete outfit stops the hunger drain");
        airUse(context, false);
        awaitOverlay(context, "message.warlockery.death_hand.incomplete");
        check(value(player -> HandOfDeathItem.isScythe(player.getMainHandItem())), "Incomplete outfit refuses to change the form");
        row().put("removed_hood", Map.of("hunger_after_sixty_ticks", 18, "scythe_flag", true, "refusal_overlay", overlay(context)));
        row().put("fixture", "All outfit pieces are put on and the hood taken off through native inventory clicks; the hand is used natively into empty air. Hunger is only staged to a known value before observation windows.");
    }

    private void earmuffs(final ClientGameTestContext context) throws Exception {
        server(player -> player.getFoodData().setFoodLevel(10));
        hold(context, new ItemStack(Items.PUFFERFISH));
        look(context, new Vec3(0.5, 105, 6));
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        try { await(context, player -> player.hasEffect(MobEffects.NAUSEA) && player.getMainHandItem().isEmpty(), 80, "Natively eating pufferfish causes Nausea"); }
        finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); }
        server(player -> player.removeEffect(MobEffects.POISON));
        context.waitTicks(60);
        check(value(player -> player.hasEffect(MobEffects.NAUSEA)), "Control: Nausea persists for sixty ticks without earmuffs");
        row().put("control_nausea_remaining", value(player -> player.getEffect(MobEffects.NAUSEA).getDuration()));
        screenshot(context, active + "-control-nausea");
        equip(context, "earmuffs");
        await(context, player -> !player.hasEffect(MobEffects.NAUSEA), 30, "Natively worn Earmuffs clear Nausea within their one-second cycle");
        row().put("fixture", "Hunger staged to ten so pufferfish can be eaten natively; the Poison side effect is removed as cleanup after Nausea is confirmed. Earmuffs are put on through the native inventory.");
    }

    private void seepingShoes(final ClientGameTestContext context) throws Exception {
        buildTrench(context);
        hold(context, new ItemStack(Items.LEATHER_BOOTS));
        equipHeld(context);
        final double control = waterWalk(context);
        stage(context);
        buildTrench(context);
        equip(context, "seepingshoes");
        final double seeping = waterWalk(context);
        row().put("walk_distance", Map.of("leather_boots", control, "seeping_shoes", seeping));
        check(control > 0.2, "Control walk must actually move through water");
        check(seeping > control * 1.3, "Seeping Shoes must carry the wearer noticeably farther through water than plain boots in the same ticks");
        row().put("fixture", "Two-deep water trench with stone walls; forty ticks of native forward walking from the same start, once in plain leather boots and once in natively worn Seeping Shoes.");
    }

    private void buildTrench(final ClientGameTestContext context) {
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-2, 100, -4), new BlockPos(2, 101, 22)))
                player.level().setBlockAndUpdate(pos, Math.abs(pos.getX()) == 2 || pos.getZ() == -4 || pos.getZ() == 22
                    ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
            player.teleportTo(0.5, 100, -2.5); player.setDeltaMovement(Vec3.ZERO);
        });
        ready(context);
    }

    private double waterWalk(final ClientGameTestContext context) {
        await(context, player -> player.isInWater(), 30, "Player starts in the staged water trench");
        final Vec3 start = value(Entity::position);
        look(context, new Vec3(0.5, 100.5, 30));
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { context.waitTicks(40); } finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); }
        context.waitTicks(3);
        return value(player -> player.position().distanceTo(start));
    }

    // ------------------------------------------------------------------ werewolf hits

    private Hit werewolfHit(final ClientGameTestContext context) {
        final UUID attacker = value(player -> {
            final Mob mob = (Mob) ModEntities.ALL.get("werewolf").get().create(player.level(), EntitySpawnReason.COMMAND);
            check(mob != null, "Werewolf fixture must exist");
            mob.setPos(0.5, 100, -1.3); mob.setNoAi(true);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0); mob.setHealth(100.0F);
            mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
            for (EquipmentSlot slot : EquipmentSlot.VALUES) mob.setItemSlot(slot, ItemStack.EMPTY);
            mob.setPersistenceRequired(); mob.setTarget(player);
            player.level().addFreshEntity(mob);
            return mob.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
        hold(context, ItemStack.EMPTY);
        look(context, value(player -> living(player, attacker).getEyePosition()));
        context.waitTicks(20);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        await(context, player -> living(player, attacker).getHealth() < 100.0F, 20, "Native punch provokes the werewolf");
        final float provoked = value(player -> living(player, attacker).getHealth());
        final DamageWatch watch = value(player -> new DamageWatch(player, attacker, provoked));
        damageWatch = watch;
        server(player -> ((Mob) living(player, attacker)).setNoAi(false));
        look(context, value(player -> living(player, attacker).getEyePosition()));
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { for (int tick = 0; tick < 160 && watch.hit == null; tick++) context.waitTicks(1); }
        finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); damageWatch = null; }
        if (watch.hit == null) throw new UnsupportedOperationException("Live werewolf did not strike within 160 ticks; no ability pass is claimed.");
        check(attacker.toString().equals(watch.hit.sourceOwner()), "Observed damage must come from the staged werewolf");
        server(player -> ((Mob) living(player, attacker)).setNoAi(true));
        return watch.hit;
    }

    private static final class DamageWatch {
        private final ServerPlayer player;
        private final UUID attacker;
        private final float before;
        private final float attackerBefore;
        private volatile Hit hit;
        private DamageWatch(final ServerPlayer player, final UUID attacker, final float attackerBefore) {
            this.player = player; this.attacker = attacker; this.before = player.getHealth(); this.attackerBefore = attackerBefore;
        }
        private void tick() {
            if (hit != null || player.getHealth() >= before) return;
            final Map<String, Integer> armorDamage = new LinkedHashMap<>();
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                final ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty()) armorDamage.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getDamageValue());
            }
            final Entity entity = player.level().getEntity(attacker);
            final var source = player.getLastDamageSource();
            hit = new Hit(attacker, before - player.getHealth(), attackerBefore, entity instanceof LivingEntity living ? living.getHealth() : -1.0F, armorDamage,
                source == null || source.getEntity() == null ? "" : source.getEntity().getUUID().toString());
        }
    }

    // ------------------------------------------------------------------ potions

    private Drink harmingDrink(final ClientGameTestContext context) {
        server(player -> { player.setHealth(player.getMaxHealth()); player.damageCooldownTime = 0; });
        hold(context, PotionContents.createItemStack(Items.POTION, Potions.HARMING));
        final float before = value(LivingEntity::getHealth);
        look(context, new Vec3(0.5, 105, 6));
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        try { await(context, player -> player.getMainHandItem().is(Items.GLASS_BOTTLE), 60, "Native drinking consumes the Harming potion"); }
        finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); }
        context.waitTicks(3);
        return new Drink(before - value(LivingEntity::getHealth));
    }

    // ------------------------------------------------------------------ fixture player

    private UUID spawnFixturePlayer(final ClientGameTestContext context, final Vec3 position) {
        fixture = value(player -> {
            final var server = player.level().getServer();
            final var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SecondaryFixture");
            final var other = new ServerPlayer(server, player.level(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            final var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, other, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
            other.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            other.setGameMode(GameType.SURVIVAL); other.teleportTo(position.x, position.y, position.z); other.setHealth(20);
            other.getFoodData().setFoodLevel(20);
            return other.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        final UUID id = fixture;
        context.waitFor(client -> client.level.getPlayerByUUID(id) != null, 60);
        return id;
    }

    private ServerPlayer fixturePlayer(final ServerPlayer player) {
        final ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(fixture);
        check(other != null, "Fixture player must remain connected");
        return other;
    }

    private void wearOnFixture(final ServerPlayer player, final String id) {
        final ItemStack stack = new ItemStack(ModItems.ALL.get(id).get());
        fixturePlayer(player).setItemSlot(stack.get(DataComponents.EQUIPPABLE).slot(), stack);
    }

    private static Map<String, Integer> armorDurability(final ServerPlayer player) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            final ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) result.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getDamageValue());
        }
        return result;
    }

    private void removeFixture() {
        if (fixture == null || world == null) return;
        final UUID id = fixture;
        fixture = null;
        server(player -> {
            final ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(id);
            if (other != null) player.level().getServer().getPlayerList().remove(other);
        });
    }

    private void aimFixture(final ClientGameTestContext context) {
        final UUID id = fixture;
        context.runOnClient(client -> {
            final Vec3 delta = client.level.getPlayerByUUID(id).getEyePosition().subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(3);
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(id)),
            "Native pointer selects the fixture player");
    }

    private void bindFixture(final ClientGameTestContext context) {
        server(player -> { player.teleportTo(0.5, 100, -1.5); player.setDeltaMovement(Vec3.ZERO); });
        ready(context);
        aimFixture(context);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        final UUID id = fixture;
        await(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(id)).isPresent(), 60,
            "Native entity use binds the doll to the fixture player");
    }

    // ------------------------------------------------------------------ staging and input

    private void stage(final ClientGameTestContext context) {
        damageWatch = null;
        release(context);
        if (context.computeOnClient(client -> client.gui.screen() != null)) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        server(player -> {
            final var server = player.level().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            player.level().getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
            player.level().getEntities((Entity) null, AREA, entity -> !(entity instanceof Player)).forEach(Entity::discard);
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 24; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.BEDROCK.defaultBlockState());
                player.level().setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                for (int y = 100; y <= 110; y++) player.level().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().invulnerable = false; player.getAbilities().flying = false; player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
            player.stopUsingItem(); player.removeAllEffects(); player.clearFire();
            player.getInventory().clearContent();
            for (EquipmentSlot slot : EquipmentSlot.VALUES) player.setItemSlot(slot, ItemStack.EMPTY);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
            player.resetFallDistance(); player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(START.x, START.y, START.z);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 18000");
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        ready(context);
    }

    private void ready(final ClientGameTestContext context) {
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player), 100, "Native player loaded and teleport acknowledged");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    private static boolean awaitingTeleport(final ServerPlayer player) {
        try {
            final var field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
            field.setAccessible(true); return field.get(player.connection) != null;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void airUse(final ClientGameTestContext context, final boolean secondary) {
        ready(context);
        if (secondary) context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
        try {
            context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(-60); });
            context.waitTicks(5);
            check(context.computeOnClient(client -> client.hitResult != null && client.hitResult.getType() == HitResult.Type.MISS), "Native air use has no entity or block hit");
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(2);
        } finally { if (secondary) context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); }
    }

    private void awaitOverlay(final ClientGameTestContext context, final String key) {
        final String expected = Component.translatable(key).getString();
        context.waitFor(client -> field(client.gui.hud, "overlayMessageString") instanceof Component message && expected.equals(message.getString()), 60);
        row().computeIfAbsent("overlays", ignored -> new ArrayList<String>());
        @SuppressWarnings("unchecked") final List<String> overlays = (List<String>) row().get("overlays");
        overlays.add(expected);
    }

    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            final Object text = field(client.gui.hud, "overlayMessageString");
            return text instanceof Component component ? component.getString() : "";
        });
    }

    private void equip(final ClientGameTestContext context, final String id) {
        hold(context, new ItemStack(ModItems.ALL.get(id).get()));
        equipHeld(context);
    }

    private void equipHeld(final ClientGameTestContext context) {
        final ItemStack held = value(player -> player.getMainHandItem().copy());
        check(held.has(DataComponents.EQUIPPABLE), "Held item must declare an equipment slot");
        final EquipmentSlot slot = held.get(DataComponents.EQUIPPABLE).slot();
        check(value(player -> player.getItemBySlot(slot).isEmpty()), "Equipment slot must start empty");
        final int destination = switch (slot) {
            case HEAD -> 5; case CHEST -> 6; case LEGS -> 7; case FEET -> 8;
            default -> throw new AssertionError("Unexpected armor slot " + slot);
        };
        moveInventorySlot(context, 36, destination);
        await(context, player -> ItemStack.isSameItemSameComponents(player.getItemBySlot(slot), held) && player.getMainHandItem().isEmpty(), 30,
            "Native inventory pickup and armor-slot placement must equip the exact held item");
    }

    private void moveInventorySlot(final ClientGameTestContext context, final int from, final int to) {
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_E);
        context.waitForScreen(InventoryScreen.class);
        for (int index : new int[] {from, to}) {
            final int[] point = context.computeOnClient(client -> {
                final var screen = client.gui.screen();
                final var menuSlot = client.player.containerMenu.getSlot(index);
                return new int[] {(int) field(screen, "leftPos") + menuSlot.x + 8, (int) field(screen, "topPos") + menuSlot.y + 8};
            });
            ManualClientAcceptance.click(context, point[0], point[1]);
        }
        context.waitTicks(3);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null, 30);
        world.getConnection().waitForClientboundPackets();
    }

    private void hold(final ClientGameTestContext context, final ItemStack stack) {
        server(player -> { player.getInventory().setItem(0, stack.copy()); player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        context.waitTicks(3);
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private static void release(final ClientGameTestContext context) {
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        for (int key : List.of(com.mojang.blaze3d.platform.InputConstants.KEY_W, com.mojang.blaze3d.platform.InputConstants.KEY_S, com.mojang.blaze3d.platform.InputConstants.KEY_A, com.mojang.blaze3d.platform.InputConstants.KEY_D, com.mojang.blaze3d.platform.InputConstants.KEY_SPACE, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT))
            context.getInput().releaseKey(key);
    }

    // ------------------------------------------------------------------ book

    private void readBook(final ClientGameTestContext context, final String id) throws Exception {
        final var match = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id)).findFirst();
        @SuppressWarnings("unchecked") final List<Object> read = (List<Object>) row().computeIfAbsent("book_guidance_read", ignored -> new ArrayList<>());
        if (match.isEmpty()) {
            read.add(Map.of("item", id, "status", "NO_EXACT_NAMED_ENTRY"));
            return;
        }
        final ManualProfile profile = match.orElseThrow();
        hold(context, new ItemStack(ModItems.ALL.get(profile.id()).get()));
        ready(context);
        context.runOnClient(client -> client.player.setXRot(-70));
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, id);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Indexed guide has readable instructions");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected),
                "Native paging visits every guide page");
            screenshot(context, active + "-" + id + "-guide-" + page);
        }
        read.add(Map.of("item", id, "book", profile.id(), "section", id, "pages_read", pages, "text", body));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null, 30);
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
    }

    // ------------------------------------------------------------------ plumbing

    private static Object field(final Object target, final String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
        }
        throw new AssertionError("Missing observation field " + name);
    }

    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        check(entity instanceof LivingEntity && entity.isAlive(), "Observed creature must remain alive");
        return (LivingEntity) entity;
    }

    private Map<String, Object> row() { return rows.get(active); }

    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int tick = 0; tick < ticks && !value(condition::test); tick++) context.waitTicks(1);
        check(value(condition::test), message);
        world.getConnection().waitForClientboundPackets();
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T value(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        server(player -> result.set(action.apply(player)));
        return result.get();
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void write(final boolean complete) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", complete);
        report.put("all_selected_scenarios_passed", complete && failures.isEmpty()
            && rows.values().stream().anyMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("fixture", "Rendered Survival client; fresh disposable world per scenario; stone arena over bedrock at night without natural regeneration or spawns. Equipment goes on through native inventory clicks; potions, food, dolls and the Hand of Death are used natively; werewolves attack under normal AI. Synthetic connected ServerPlayers act as the second player where the guide requires one. No production ability method is invoked to produce an outcome.");
        report.put("scenarios", rows);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        report.put("remaining", List.of("Hedge Crone brew yield (kettle walkthrough)", "Dawn Hunter vampire branch", "Rite of Blindness interception",
            "Nullifying Bolt hunter shots", "Death disguise encounters", "Acquisition of every item"));
        Files.writeString(evidence.resolve("equipment-secondary-abilities.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private record Scenario(String id, List<String> items, String contract, List<String> notRun) { }
    private record Hit(UUID attacker, float playerDamage, float attackerHealthBefore, float attackerHealth, Map<String, Integer> armorDamage, String sourceOwner) { }
    private record Drink(float lost) { }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
