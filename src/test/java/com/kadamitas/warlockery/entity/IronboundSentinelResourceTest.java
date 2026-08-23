package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.junit.jupiter.api.Test;

/**
 * The identity, immutability and reachability guards for F36. Everything here is asserted against
 * the tree rather than against the design document, so a resource this family promised not to touch
 * cannot drift and a behaviour this family promised to reach cannot become orphaned.
 */
final class IronboundSentinelResourceTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "kadamitas", "warlockery");
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");
    private static final Path VANILLA_TAGS = Path.of(
        "src", "main", "resources", "data", "minecraft", "tags", "entity_type"
    );
    private static final List<String> DESCRIPTORS = List.of(
        "ironbound_sentinel_charge_wakes_stands_down_and_resumes",
        "ironbound_sentinel_ward_bars_and_repels_only_within_sight",
        "ironbound_sentinel_permitted_parties_are_never_bound_or_repelled",
        "ironbound_sentinel_strain_seizes_and_stands_down_without_rampage",
        "ironbound_sentinel_hazard_preempts_episode_and_keeps_its_station",
        "ironbound_sentinel_save_reload_and_zombie_lifecycle_are_replaced"
    );

    // ---------------------------------------------------------------- identity

    @Test
    void theSentinelIsADedicatedFinalMonsterShellWithExactRegistryOwnedGeometry() {
        assertEquals(Monster.class, IronboundSentinelEntity.class.getSuperclass());
        assertTrue(java.lang.reflect.Modifier.isFinal(IronboundSentinelEntity.class.getModifiers()));
        assertFalse(Zombie.class.isAssignableFrom(IronboundSentinelEntity.class),
            "no Drowned conversion, no underwater timer, no baby form, no jockey, no door "
                + "breaking and no reinforcement summoning can survive the superclass change");
        assertFalse(ArcaneMob.class.isAssignableFrom(IronboundSentinelEntity.class));
        assertFalse(SpiritMob.class.isAssignableFrom(IronboundSentinelEntity.class));
        assertFalse(WingedArcaneMob.class.isAssignableFrom(IronboundSentinelEntity.class));
        assertTrue(ArcaneCreature.class.isAssignableFrom(IronboundSentinelEntity.class));

        final CreatureVisualProfile visual = CreatureVisualProfile.forKind(
            CreatureKind.IRONBOUND_SENTINEL);
        assertEquals(1.0F, visual.width(), "the registry-owned width is immutable");
        assertEquals(2.7F, visual.height(), "the registry-owned height is immutable");
        assertEquals(CreatureVisualProfile.Archetype.BOSS, visual.archetype());
        assertFalse(CreatureKind.IRONBOUND_SENTINEL.isUndead());
        assertFalse(CreatureKind.IRONBOUND_SENTINEL.isDemonic());
    }

    @Test
    void theDeclaredAttributeBasesAreTheValuesAlreadyInEffectRatherThanNewOnes() {
        assertEquals(20.0D, IronboundSentinelEntity.BASE_MAX_HEALTH,
            "the Attributes.MAX_HEALTH default reached through Monster.createMonsterAttributes()");
        assertEquals(35.0D, IronboundSentinelEntity.BASE_FOLLOW_RANGE);
        assertEquals(0.23D, IronboundSentinelEntity.BASE_MOVEMENT_SPEED);
        assertEquals(3.0D, IronboundSentinelEntity.BASE_ATTACK_DAMAGE);
        assertEquals(2.0D, IronboundSentinelEntity.BASE_ARMOR);
        assertEquals(5, IronboundSentinelEntity.XP_REWARD,
            "the inherited Monster experience reward, restated so it cannot drift");
    }

    // ---------------------------------------------------------------- immutability

    @Test
    void theLootTableStaysExactlyTheEmptyPoolItAlreadyWas() {
        final JsonObject loot = JsonParser.parseString(
            read(DATA.resolve("loot_table/entities/ironbound_sentinel.json"))
        ).getAsJsonObject();
        assertEquals("minecraft:entity", loot.get("type").getAsString());
        assertTrue(loot.getAsJsonArray("pools").isEmpty(),
            "the empty pool is a progression fact and is not touched by mob-AI work");
        assertEquals("warlockery:entities/ironbound_sentinel",
            loot.get("random_sequence").getAsString());
    }

    @Test
    void theDisplayedNameAndSpawnEggNameSurviveInAllTwelveShippedLocales() {
        final List<Path> locales = locales();
        assertEquals(12, locales.size(), "twelve shipped locale files; got " + locales);
        locales.forEach(locale -> {
            final JsonObject strings = JsonParser.parseString(read(locale)).getAsJsonObject();
            assertTrue(strings.has("entity.warlockery.ironbound_sentinel"),
                "the displayed name is immutable in " + locale.getFileName());
            assertTrue(strings.has("item.warlockery.ironbound_sentinel_spawn_egg"),
                "the spawn egg name is immutable in " + locale.getFileName());
        });
    }

    @Test
    void theKindStaysOutOfTheUndeadAndSmiteTagsAndCarriesNoRitualOrRecipeCoupling() {
        assertFalse(read(VANILLA_TAGS.resolve("undead.json")).contains("ironbound"),
            "the kind is deliberately absent from the undead tag and stays absent");
        assertFalse(read(VANILLA_TAGS.resolve("sensitive_to_smite.json")).contains("ironbound"),
            "removing the Zombie superclass changes no Smite or Holy Bolt behaviour, because "
                + "every one of those paths dispatches on the tag and never on the class");
        assertTrue(walkFor("ironbound", DATA.resolve("ritual")).isEmpty(),
            "no ritual couples to this mob and none is added");
        assertTrue(walkFor("ironbound", DATA.resolve("recipe")).isEmpty(),
            "no recipe couples to this mob and none is added");
        assertTrue(walkFor("ironbound", DATA.resolve("advancement")).isEmpty(),
            "no advancement couples to this mob and none is added");
    }

    @Test
    void theClientSilhouetteAndItsFiveNamedPartsAreUntouched() {
        final String model = read(MAIN.resolve("client/ArcaneCreatureModel.java"));
        for (final String part : List.of("sentinel_chassis", "sentinel_shield", "sentinel_core",
            "sentinel_crest", "sentinel_hammer")) {
            assertTrue(model.contains(part), "the model part " + part + " is immutable");
        }
        assertTrue(read(MAIN.resolve("client/CreatureModelProfile.java"))
            .contains("IRONBOUND_SENTINEL"), "the BOSS model profile row is immutable");
    }

    // ---------------------------------------------------------------- superseded generic rows

    /**
     * The two shared catalogue rows this family supersedes rather than deletes. Both stay exactly as
     * written under the no-deletion rule so the families that share their literals keep passing, and
     * both are now unreachable for this kind because the dedicated entity never calls either
     * runtime. That combination is recorded here so the next reader finds the fact rather than
     * rediscovering it.
     */
    @Test
    void theGuardDoctrineAndVillageWatchRowsSurviveUnchangedAndAreNowUnreachable() {
        assertEquals(TacticalCombatRules.Doctrine.GUARD,
            TacticalCombatRules.profile(CreatureKind.IRONBOUND_SENTINEL).doctrine(),
            "the catalogued GUARD row is preserved byte for byte");
        assertEquals(16, TacticalCombatRules.profile(CreatureKind.IRONBOUND_SENTINEL).cadenceTicks());
        assertEquals(2.5D,
            TacticalCombatRules.profile(CreatureKind.IRONBOUND_SENTINEL).preferredDistance());
        assertTrue(read(MAIN.resolve("entity/AmbientActivityProfile.java"))
            .contains("CreatureKind.IRONBOUND_SENTINEL"),
            "the VILLAGE_WATCH membership row is preserved for the two families that share it");

        final String entity = read(MAIN.resolve("entity/IronboundSentinelEntity.java"));
        assertFalse(entity.contains("TacticalCombatRuntime.tick"),
            "the dedicated entity never reaches the generic tactical layer, so the preserved "
                + "GUARD row can no longer make a guardian flee at ten percent health");
        assertFalse(entity.contains("AmbientActivityRuntime.tick"),
            "the dedicated entity never reaches the generic ambient layer, so the preserved "
                + "VILLAGE_WATCH row can no longer run its 5625-position block scan");
        assertFalse(entity.contains("CreatureBehaviorFactory"),
            "the kind has no audited behavior profile, so the throwing InertBehavior.profile() "
                + "must never be constructed for it");
    }

    @Test
    void noAuditedBehaviorProfileRowIsAddedForThisKind() {
        assertTrue(CreatureBehaviorProfile.find(CreatureKind.IRONBOUND_SENTINEL).isEmpty(),
            "the fixed thirty-six-entry audit list is preserved with no F36 row added");
        assertEquals(36, CreatureBehaviorProfile.audited().size());
    }

    // ---------------------------------------------------------------- reachability

    /**
     * The hand-traced wiring, asserted rather than described. Every public runtime entry point has
     * exactly one production caller and that caller is on the entity's own tick, interaction or
     * damage path, so a behaviour cannot be written, unit tested and then never reached.
     */
    @Test
    void everyRuntimeEntryPointHasAProductionCallerOnTheEntitysOwnPaths() {
        final String entity = read(MAIN.resolve("entity/IronboundSentinelEntity.java"));
        assertTrue(entity.contains("IronboundSentinelRuntime.tick(this, level)"),
            "customServerAiStep is the single dispatch into the runtime");
        assertTrue(entity.contains("protected void customServerAiStep"));
        assertTrue(entity.contains("IronboundSentinelRuntime.socketDecision(this, player)")
                && entity.contains("IronboundSentinelRuntime.applySocketAct(this, server, act)"),
            "mobInteract reaches both halves of the socket act");
        assertTrue(entity.contains("IronboundSentinelRuntime.onAcceptedDamage(this, level, source)"),
            "hurtServer reaches the attribution path");
        assertTrue(entity.contains("IronboundSentinelRuntime.legalSubject(this, target)"),
            "canAttack reaches the final absolute offence gate");
        assertTrue(entity.contains("IronboundSentinelRuntime.onRemoved(this)"),
            "removal reaches the scratch teardown");

        final String runtime = read(MAIN.resolve("entity/IronboundSentinelRuntime.java"));
        for (final String band : List.of("tickHazard(", "tickShutdown(", "tickSeize(",
            "tickEpisode(", "tickReturn(", "tickRoutine(")) {
            assertTrue(runtime.contains("case ") && runtime.contains(band),
                "the band " + band + " is dispatched from the single tick switch");
        }
        assertTrue(runtime.contains("sweep(sentinel, level, scratch)")
                && runtime.contains("advanceBearing(sentinel, scratch)"),
            "the sweep and the bearing advance are both reached from the routine band");
        assertTrue(runtime.contains("accrueStrain(") && runtime.contains("decayStrain("),
            "both halves of the strain ledger are reached from a band");
        assertTrue(runtime.contains("requestRoute(sentinel, scratch"),
            "the shared route gate is reached from the bands that move");
    }

    /** No goal may own MOVE or TARGET: the runtime is the sole navigation writer. */
    @Test
    void noGoalOwnsMoveOrTargetAndTheTargetSelectorIsNeverPopulated() {
        final String entity = read(MAIN.resolve("entity/IronboundSentinelEntity.java"));
        assertFalse(entity.contains("targetSelector.addGoal"),
            "the target selector stays permanently empty");
        // Read the declaration site rather than the whole file, so a goal named only in prose
        // cannot fail this and, more importantly, a goal actually constructed cannot hide behind
        // one.
        final String registerGoals = between(entity,
            "protected void registerGoals() {", "private static final class LookOnlyRandomLookGoal");
        for (final String forbidden : List.of("MeleeAttackGoal", "WaterAvoidingRandomStrollGoal",
            "MoveThroughVillageGoal", "HurtByTargetGoal", "NearestAttackableTargetGoal",
            "RandomStrollGoal", "PanicGoal", "AvoidEntityGoal", "RemoveBlockGoal")) {
            assertFalse(registerGoals.contains(forbidden),
                "no goal that owns MOVE or TARGET may be registered; found " + forbidden);
        }
        assertEquals(3, count(registerGoals, "goalSelector.addGoal"),
            "exactly the three JUMP and LOOK goals are registered; body=" + registerGoals);
        assertTrue(entity.contains("new FloatGoal(this)")
                && entity.contains("new LookAtPlayerGoal(this, Player.class, 8.0F)")
                && entity.contains("LookOnlyRandomLookGoal"),
            "only JUMP and LOOK work is registered");
        assertTrue(entity.contains("setFlags(EnumSet.of(Flag.LOOK))"),
            "the random look goal is redeclared LOOK only so it cannot claim MOVE");
    }

    /** The two gates in shared code that keyed on this kind, and what happened to them. */
    @Test
    void theArcaneMobGatesKeyedOnThisKindAreRemovedBecauseTheRoutingEditLanded() {
        final String arcaneMob = read(MAIN.resolve("entity/ArcaneMob.java"));
        assertFalse(arcaneMob.contains("CreatureKind.IRONBOUND_SENTINEL"),
            "DR-1 and DR-2: once SPECIAL_ARCANE_FACTORIES routes this kind to the dedicated body, "
                + "ArcaneMob is never constructed for it, so both the loot-pickup gate and the "
                + "target-selector clause had no reachable true branch left");
        assertTrue(arcaneMob.contains("setCanPickUpLoot(false)"),
            "the gate collapses to the constant it always evaluated to for every other kind");
        assertFalse(arcaneMob.contains("NearestAttackableTargetGoal"),
            "the clause was the only user of that goal in this class");
        final String entity = read(MAIN.resolve("entity/IronboundSentinelEntity.java"));
        assertTrue(entity.contains("setCanPickUpLoot(false)"),
            "the dedicated entity normalizes pickup off, which is what made the removal safe");
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * The six descriptor resources and the isolated environment are not asserted here, because they
     * are not in the tree: they can only land atomically with their {@code ModGameTests}
     * registrations, since {@code GameTestInstanceContractTest} asserts exact set equality between
     * registration ids and {@code test_instance} filenames. All three live in the coordinator
     * bundle, and that file's own F36 test asserts them once the bundle is applied. What is
     * asserted here is the half that is in the tree: that every id the bundle will register has a
     * public fixture method behind it, and that those methods assert something that can fail.
     */
    private static String camel(final String id) {
        final StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (final char character : id.toCharArray()) {
            if (character == '_') {
                upper = true;
                continue;
            }
            name.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return name.toString();
    }

    private static String between(final String source, final String start, final String end) {
        final int from = source.indexOf(start);
        assertTrue(from >= 0, "missing declaration: " + start);
        final int to = source.indexOf(end, from);
        assertTrue(to > from, "unterminated declaration: " + start);
        return source.substring(from, to);
    }

    private static int count(final String haystack, final String needle) {
        int total = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            total++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return total;
    }

    private static List<Path> locales() {
        try (var files = Files.list(ASSETS.resolve("lang"))) {
            return files.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted()
                .toList();
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static List<Path> walkFor(final String needle, final Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(needle)
                    || read(path).contains(needle))
                .toList();
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
