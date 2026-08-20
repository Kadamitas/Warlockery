package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

/**
 * The two tags F27 adds, the isolated GameTest resources, and the frozen surfaces around the two
 * steeds that the dedicated body must not have moved.
 *
 * <p><strong>Scope amendment.</strong> This file is not one of the thirty-one paths in
 * {@code f27-spectral-steeds-forge-file-scope.md}. It was added in place of scope item 14,
 * {@code SpectralSteedRuntimeTest.java}, which was never written. That substitution was never
 * recorded. Item 14 now exists as well, so this file is a genuine addition to the scope rather than
 * a swap, and it needs the owner's sign-off as such.</p>
 *
 * <p>The seven GameTest descriptors are real files, but they are held in {@code deferred/f27}
 * rather than in {@code src/main/resources/data/warlockery/test_instance} while their
 * {@code ModGameTests} registrations are deferred, because
 * {@code GameTestInstanceContractTest.everyGameTestRegistrationHasOneMatchingEmptyTemplateFixture}
 * lists that directory and asserts exact set equality against the registrations. The environment
 * descriptor is not held back: nothing enumerates {@code test_environment}, so it ships in place.
 * {@link #theIsolatedGameTestResourcesAreRealFilesWhereverTheyCurrentlySit} reads whichever copy
 * exists, so it holds on both sides of the wiring flip.</p>
 */
final class SpectralSteedResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path INSTANCES =
        RESOURCES.resolve(Path.of("data", "warlockery", "test_instance"));
    private static final Path HELD_BACK = Path.of("deferred", "f27", "test_instance");

    /** The seven live cases and the tick budget each descriptor declares. */
    private static final Map<String, Integer> FIXTURES = Map.of(
        "steed_owner_only_control_and_safe_dismount", 400,
        "pale_steed_bond_gait_fatigue_and_rest", 600,
        "pale_steed_balks_without_fear_or_ejection", 400,
        "nightmare_accelerates_and_warns_only_legal_hostiles", 400,
        "unbound_nightmare_remains_dream_hostile", 400,
        "steed_rest_releases_lost_support_without_hay_mutation", 400,
        "steed_two_player_caps_auras_and_owl_isolation", 400
    );

    @Test
    void theRestLandmarkTagIsExtendibleAndNamesOnlyHay() {
        final JsonObject tag = read(RESOURCES.resolve(Path.of(
            "data", "warlockery", "tags", "block", "ai", "spectral_steed_rest_landmarks.json"
        )));
        assertTrue(tag.has("replace"));
        assertFalse(tag.get("replace").getAsBoolean(), "a pack may add its own stable bedding");
        assertEquals(List.of("minecraft:hay_block", "#c:storage_blocks/wheat"), values(tag));
    }

    @Test
    void theWarningTargetTagNamesOnlyVanillaHostilesAndNoWarlockeryCreature() {
        final JsonObject tag = read(RESOURCES.resolve(Path.of(
            "data", "warlockery", "tags", "entity_type", "ai", "nightmare_fear_targets.json"
        )));
        assertFalse(tag.get("replace").getAsBoolean());
        final List<String> members = values(tag);
        assertFalse(members.isEmpty());
        for (final String member : members) {
            assertFalse(member.startsWith("warlockery:"),
                "a warning never targets another Warlockery family by tag: " + member);
            assertFalse(member.startsWith("#warlockery:"),
                "and never through a Warlockery tag either: " + member);
        }
        assertTrue(members.contains("minecraft:zombie"),
            "the live fixture warns a plain zombie, so the tag must admit one");
    }

    @Test
    void theNightmareRemainsTheDreamSystemsReferentAndNeitherSteedIsSpectral() {
        final List<String> nightmares = values(read(RESOURCES.resolve(Path.of(
            "data", "warlockery", "tags", "entity_type", "nightmares.json"
        ))));
        assertEquals(List.of("warlockery:nightmare"), nightmares);

        final List<String> spectral = values(read(RESOURCES.resolve(Path.of(
            "data", "warlockery", "tags", "entity_type", "spectral.json"
        ))));
        assertFalse(spectral.contains("warlockery:pale_steed"),
            "the spectral gameplay tag drives six other systems and gains no steed here");
        assertFalse(spectral.contains("warlockery:nightmare"),
            "the spectral gameplay tag drives six other systems and gains no steed here");
    }

    @Test
    void theExistingAcquisitionOfferingsAreUnchanged() {
        assertEquals(List.of("minecraft:golden_carrot", "minecraft:saddle"),
            values(read(RESOURCES.resolve(Path.of(
                "data", "warlockery", "tags", "item", "creature_interactions", "pale_steed_bonding.json"
            )))));
        assertEquals(List.of("minecraft:blaze_powder", "minecraft:fire_charge"),
            values(read(RESOURCES.resolve(Path.of(
                "data", "warlockery", "tags", "item", "creature_interactions", "nightmare_bonding.json"
            )))));
    }

    /**
     * The eight isolated GameTest resources exist as files with the right contents. They were
     * previously only markdown in the evidence document, which meant nothing checked them and the
     * first thing to read them would have been a server boot.
     */
    @Test
    void theIsolatedGameTestResourcesAreRealFilesWhereverTheyCurrentlySit() {
        final JsonObject environment = read(RESOURCES.resolve(Path.of(
            "data", "warlockery", "test_environment", "spectral_steeds_isolated.json"
        )));
        assertEquals("minecraft:all_of", environment.get("type").getAsString());
        assertTrue(environment.getAsJsonArray("definitions").isEmpty(),
            "the isolated F27 environment must not mutate shared world state");

        assertEquals(7, FIXTURES.size());
        for (final Map.Entry<String, Integer> fixture : FIXTURES.entrySet()) {
            final String id = fixture.getKey();
            final Path live = INSTANCES.resolve(id + ".json");
            final Path held = HELD_BACK.resolve(id + ".json");
            assertTrue(Files.exists(live) || Files.exists(held),
                "the descriptor for " + id + " must exist as a file, in place or held back");
            final JsonObject descriptor = read(Files.exists(live) ? live : held);
            assertEquals("minecraft:function", descriptor.get("type").getAsString(), id);
            assertEquals("warlockery:" + id, descriptor.get("function").getAsString(), id);
            assertEquals("warlockery:spectral_steeds_isolated",
                descriptor.get("environment").getAsString(),
                "every F27 case runs in the isolated environment and no other: " + id);
            assertEquals("forge:empty3x3x3", descriptor.get("structure").getAsString(), id);
            assertEquals(fixture.getValue().intValue(), descriptor.get("max_ticks").getAsInt(), id);
        }
    }

    @Test
    void everyNamedLiveCaseHasACompiledMethod() {
        final String source = readText(MAIN_JAVA.resolve(Path.of(
            "com", "kadamitas", "warlockery", "entity", "SpectralSteedGameTests.java"
        )));
        for (final String method : List.of(
            "steedOwnerOnlyControlAndSafeDismount",
            "paleSteedBondGaitFatigueAndRest",
            "paleSteedBalksWithoutFearOrEjection",
            "nightmareAcceleratesAndWarnsOnlyLegalHostiles",
            "unboundNightmareRemainsDreamHostile",
            "steedRestReleasesLostSupportWithoutHayMutation",
            "steedTwoPlayerCapsAurasAndOwlIsolation"
        )) {
            assertTrue(source.contains("public static void " + method + "("),
                "GameTest method must exist: " + method);
        }
    }

    /**
     * Read from the compiled bodies rather than from their source text. A call is a call whatever
     * the whitespace around it, and a call that has moved into a nested class or a lambda is still
     * found here, which a substring search over one {@code .java} file is not.
     */
    @Test
    void theRuntimeMutatesNoBlockSpawnsNothingAndForcesNoChunk() {
        for (final Class<?> owner : List.of(
            SpectralSteedRuntime.class, SpectralSteedEntity.class,
            SpectralSteedRules.class, SpectralSteedState.class
        )) {
            final Set<String> called = calledMethodsOf(owner);
            for (final String forbidden : List.of(
                "setBlock", "destroyBlock", "removeBlock", "addFreshEntity",
                "addRegionTicket", "getAllEntities"
            )) {
                assertFalse(called.contains(forbidden), () -> owner.getSimpleName()
                    + " must not call forbidden world mutation API: " + forbidden);
            }
        }
    }

    /**
     * The ridden seam is layered onto the shared one rather than replacing it. If these {@code super}
     * calls ever go, {@code ArcaneMob}'s mount overrides and two thirds of {@link SpectralMountRules}
     * become code that no longer runs while still describing how the mod behaves.
     *
     * <p>Both halves are structural. The non-overrides are proved by walking the real class
     * hierarchy for the class that actually declares each method, so a superseding override is
     * caught wherever it is put, including in a class inserted between this body and
     * {@code ArcaneMob}; the previous substring guards could only see one file and were defeated by
     * a space before a parenthesis. The {@code super} calls are proved by reading the compiled
     * bodies for the {@code invokespecial} that actually reaches {@code ArcaneMob}, which no
     * reformatting can fake and no equivalent-looking direct call can satisfy.</p>
     */
    @Test
    void theDedicatedBodyStillRunsTheSharedMountSeamUnderneathItself() {
        assertEquals(ArcaneMob.class, declaringClassOf("canAddPassenger", Entity.class),
            "the owner-only passenger check stays exactly where the mod already had it");
        assertEquals(ArcaneMob.class,
            declaringClassOf("mobInteract", Player.class, InteractionHand.class),
            "binding and mounting stay in the shared interaction path");
        assertEquals(ArcaneMob.class, declaringClassOf("getControllingPassenger"),
            "so does the controlling-passenger check");

        assertTrue(callsSuper("getRiddenSpeed"),
            "the band scales the shared mount speed rather than replacing it");
        assertTrue(callsSuper("getRiddenInput"),
            "the steering scale wraps the shared input rather than replacing it");
    }

    /**
     * The class that actually declares the most derived implementation of a method reachable on
     * {@link SpectralSteedEntity}, found by walking the real hierarchy rather than by reading one
     * source file.
     */
    private static Class<?> declaringClassOf(final String name, final Class<?>... parameters) {
        for (Class<?> type = SpectralSteedEntity.class; type != null; type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod(name, parameters);
                return type;
            } catch (final NoSuchMethodException notHere) {
                // This class does not declare it, so the inherited one is still the live one.
            }
        }
        throw new AssertionError("no class in the hierarchy declares " + name);
    }

    /** Whether the compiled body calls {@code super.<name>} on {@link ArcaneMob}. */
    private static boolean callsSuper(final String name) {
        for (final MethodModel method : parse(SpectralSteedEntity.class).methods()) {
            if (!method.methodName().equalsString(name) || method.code().isEmpty()) {
                continue;
            }
            for (final CodeElement element : method.code().orElseThrow().elementList()) {
                if (element instanceof InvokeInstruction invoke
                    && invoke.opcode() == Opcode.INVOKESPECIAL
                    && invoke.name().equalsString(name)
                    && invoke.owner().asInternalName().equals(internalName(ArcaneMob.class))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Every method name this class and its nested classes invoke on anything. */
    private static Set<String> calledMethodsOf(final Class<?> owner) {
        final Set<String> called = new LinkedHashSet<>();
        final List<Class<?>> bodies = new ArrayList<>();
        bodies.add(owner);
        bodies.addAll(List.of(owner.getDeclaredClasses()));
        for (final Class<?> body : bodies) {
            for (final MethodModel method : parse(body).methods()) {
                method.code().ifPresent(code -> code.elementList().forEach(element -> {
                    if (element instanceof InvokeInstruction invoke) {
                        called.add(invoke.name().stringValue());
                    }
                }));
            }
        }
        return called;
    }

    private static ClassModel parse(final Class<?> type) {
        final String resource = "/" + internalName(type) + ".class";
        try (var bytes = SpectralSteedResourceTest.class.getResourceAsStream(resource)) {
            if (bytes == null) {
                throw new AssertionError("compiled body not on the test classpath: " + resource);
            }
            return ClassFile.of().parse(bytes.readAllBytes());
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String internalName(final Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static List<String> values(final JsonObject tag) {
        final List<String> entries = new ArrayList<>();
        tag.getAsJsonArray("values").forEach(value -> entries.add(value.getAsString()));
        return List.copyOf(entries);
    }

    private static JsonObject read(final Path path) {
        return JsonParser.parseString(readText(path)).getAsJsonObject();
    }

    private static String readText(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}


