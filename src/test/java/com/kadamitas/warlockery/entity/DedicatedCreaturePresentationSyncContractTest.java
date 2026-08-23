package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Entity-side half of the dedicated-model presentation boundary.
 *
 * <p>Each branch names only the small typed getter surface a logical client needs. The owning
 * entity must publish that surface through vanilla primitive {@code SynchedEntityData}; the
 * complete durable AI/runtime record remains server authority and never becomes a presentation
 * payload.</p>
 */
final class DedicatedCreaturePresentationSyncContractTest {
    private static final Path ENTITY_ROOT = Path.of(
        "src/main/java/com/kadamitas/warlockery/entity"
    );
    private static final Pattern PRESENTATION_ACCESSOR = Pattern.compile(
        "EntityDataAccessor<(Byte|Integer|Boolean)>\\s+(DATA_PRESENTATION_[A-Z_]+)\\s*=\\s*"
            + "SynchedEntityData\\.defineId\\([^;]+EntityDataSerializers\\.(BYTE|INT|BOOLEAN)\\);",
        Pattern.DOTALL
    );

    private static final List<Owner> OWNERS = List.of(
        owner(InfernalHierarchyEntity.class, "InfernalHierarchyEntity.java"),
        owner(BrambleColossusEntity.class, "BrambleColossusEntity.java"),
        owner(WerewolfEntity.class, "WerewolfEntity.java"),
        owner(GoblinEntity.class, "GoblinEntity.java"),
        owner(HobgoblinEntity.class, "HobgoblinEntity.java"),
        owner(StonebrokerEntity.class, "StonebrokerEntity.java"),
        owner(ForgewardenEntity.class, "ForgewardenEntity.java"),
        owner(HellhoundEntity.class, "HellhoundEntity.java"),
        owner(AbstractMimicEntity.class, "AbstractMimicEntity.java"),
        owner(ImpEntity.class, "ImpEntity.java"),
        owner(IronboundSentinelEntity.class, "IronboundSentinelEntity.java"),
        owner(NaamahEntity.class, "NaamahEntity.java"),
        owner(SpectralSteedEntity.class, "SpectralSteedEntity.java"),
        owner(ParasyticLouseEntity.class, "ParasyticLouseEntity.java"),
        owner(SpectralFamiliarEntity.class, "SpectralFamiliarEntity.java"),
        owner(StormSimianEntity.class, "StormSimianEntity.java"),
        owner(ThornedPursuerEntity.class, "ThornedPursuerEntity.java"),
        owner(UmbralSigilEntity.class, "UmbralSigilEntity.java")
    );

    private static final List<Branch> BRANCHES = List.of(
        branch("AbyssalRegentModel", InfernalHierarchyEntity.class,
            getter("presentationIntent", InfernalHierarchyRules.Intent.class),
            getter("presentationPhaseState", InfernalHierarchyRules.PhaseState.class)),
        branch("EmberhornArchfiendModel", InfernalHierarchyEntity.class,
            getter("presentationIntent", InfernalHierarchyRules.Intent.class)),
        branch("BrambleColossusModel", BrambleColossusEntity.class,
            getter("presentationPosted", boolean.class),
            getter("presentationNerve", int.class),
            getter("presentationLeg", int.class),
            getter("presentationPhase", BrambleColossusRules.Phase.class)),
        branch("WerewolfModel", WerewolfEntity.class,
            getter("presentationHunger", int.class),
            getter("presentationFear", int.class),
            getter("presentationAction", LycanPackRules.ActionKind.class)),
        branch("FeralLycanModel", FeralLycanEntity.class,
            getter("presentationHunger", int.class),
            getter("presentationFear", int.class),
            getter("presentationAction", LycanPackRules.ActionKind.class)),
        branch("GoblinModel", GoblinEntity.class,
            getter("presentationIntent", GoblinEnclaveRules.Intent.class),
            getter("presentationAssaultMember", boolean.class),
            getter("presentationAssaultLeader", boolean.class),
            getter("presentationAssaultWave", int.class)),
        branch("HobgoblinModel", HobgoblinEntity.class,
            getter("presentationMode", HobgoblinJourneyRules.Mode.class)),
        branch("StonebrokerModel", StonebrokerEntity.class,
            getter("presentationAction", GoblinPatronRules.Action.class)),
        branch("ForgewardenModel", ForgewardenEntity.class,
            getter("presentationAction", GoblinPatronRules.Action.class)),
        branch("HellhoundModel", HellhoundEntity.class,
            getter("presentationBound", boolean.class),
            getter("presentationWarning", boolean.class),
            getter("presentationBiting", boolean.class),
            getter("presentationRetreating", boolean.class)),
        branch("IllusionCreeperModel", IllusionCreeperEntity.class,
            getter("presentationPhase", MimicryRules.Phase.class)),
        branch("IllusionSpiderModel", IllusionSpiderEntity.class,
            getter("presentationPhase", MimicryRules.Phase.class)),
        branch("IllusionZombieModel", IllusionZombieEntity.class,
            getter("presentationPhase", MimicryRules.Phase.class),
            getter("presentationAcceptedHits", int.class)),
        branch("ImpModel", ImpEntity.class,
            getter("presentationAction", ImpLifeRules.Action.class)),
        branch("IronboundSentinelModel", IronboundSentinelEntity.class,
            getter("presentationCharged", boolean.class),
            getter("presentationPhase", IronboundSentinelRules.Phase.class)),
        branch("NaamahModel", NaamahEntity.class,
            getter("presentationAction", NaamahCourtRules.Action.class),
            getter("presentationPhase", NaamahCourtRules.Phase.class),
            getter("presentationGazeMending", boolean.class)),
        branch("PaleSteedModel", SpectralSteedEntity.class,
            getter("presentationGait", SpectralSteedRules.Gait.class),
            getter("presentationBond", int.class),
            getter("presentationFatigue", int.class),
            getter("presentationBalking", boolean.class),
            getter("presentationResting", boolean.class)),
        branch("NightmareModel", SpectralSteedEntity.class,
            getter("presentationGait", SpectralSteedRules.Gait.class),
            getter("presentationBond", int.class),
            getter("presentationFatigue", int.class),
            getter("presentationBalking", boolean.class),
            getter("presentationResting", boolean.class),
            getter("presentationWarning", boolean.class)),
        branch("ParasyticLouseModel", ParasyticLouseEntity.class,
            getter("presentationPhase", ParasyticLouseTenancyRules.Phase.class),
            getter("presentationNourishment", int.class)),
        branch("SpectralFamiliarModel", SpectralFamiliarEntity.class,
            getter("presentationPhase", SpectralFamiliarRules.Phase.class)),
        branch("StormSimianModel", StormSimianEntity.class,
            getter("presentationCharge", int.class),
            getter("presentationHasGrip", boolean.class)),
        branch("ThornedPursuerModel", ThornedPursuerEntity.class,
            getter("presentationPhase", ThornedPursuerRules.Phase.class),
            getter("presentationSnareCooldownRemaining", int.class)),
        branch("UmbralSigilModel", UmbralSigilEntity.class,
            getter("presentationPhase", UmbralSigilRules.Phase.class))
    );

    @Test
    void everyModelFacingCustomBranchHasACompleteTypedClientSurface() {
        assertTrue(BRANCHES.size() >= 23, "every affected dedicated model branch must stay mapped");
        for (final Branch branch : BRANCHES) {
            for (final Getter getter : branch.getters()) {
                final Method method = assertDoesNotThrow(
                    () -> branch.owner().getMethod(getter.name()),
                    branch.model() + " needs the synchronized getter " + getter.name()
                );
                assertTrue(
                    method.getReturnType() == getter.returnType(),
                    branch.model() + " requires " + getter.name() + " to return "
                        + getter.returnType().getTypeName()
                );
            }
        }
    }

    @Test
    void everyOwnerPublishesAfterItsAuthoritativeRuntimeTick() throws Exception {
        for (final Owner owner : OWNERS) {
            final String source = source(owner.file());
            assertTrue(source.contains("defineSynchedData"), owner.type().getSimpleName());
            assertTrue(
                source.contains("syncPresentationFromRuntime();"),
                owner.type().getSimpleName() + " must publish after server-side runtime changes"
            );
            final int runtimeTick = source.indexOf("Runtime.tick(this, level);");
            final int publish = source.indexOf("syncPresentationFromRuntime();", runtimeTick);
            assertTrue(
                runtimeTick >= 0 && publish > runtimeTick,
                owner.type().getSimpleName() + " must publish after its authoritative runtime tick"
            );
        }
    }

    @Test
    void presentationPayloadsUseOnlyVanillaByteIntAndBooleanSerializers() throws Exception {
        for (final Owner owner : OWNERS) {
            final String source = source(owner.file());
            final Matcher matcher = PRESENTATION_ACCESSOR.matcher(source);
            int count = 0;
            while (matcher.find()) {
                count++;
                final String boxedType = matcher.group(1);
                final String serializer = matcher.group(3);
                assertTrue(
                    Set.of("Byte", "Integer", "Boolean").contains(boxedType),
                    owner.type().getSimpleName() + " exposed a non-primitive presentation payload"
                );
                assertTrue(
                    Set.of("BYTE", "INT", "BOOLEAN").contains(serializer),
                    owner.type().getSimpleName() + " used a non-vanilla primitive serializer"
                );
                assertEquals(
                    switch (boxedType) {
                        case "Byte" -> "BYTE";
                        case "Integer" -> "INT";
                        case "Boolean" -> "BOOLEAN";
                        default -> throw new AssertionError(boxedType);
                    },
                    serializer,
                    owner.type().getSimpleName() + " paired an accessor with the wrong serializer"
                );
            }
            assertTrue(count > 0, owner.type().getSimpleName() + " needs a presentation accessor");
        }
    }

    @Test
    void clientSurfaceNeverExposesDurableStateOrRuntimeRecords() {
        for (final Branch branch : BRANCHES) {
            for (final Getter getter : branch.getters()) {
                final Class<?> returnType = getter.returnType();
                assertTrue(
                    returnType.isPrimitive() || returnType.isEnum(),
                    branch.model() + " presentation must be a primitive or enum"
                );
                assertFalse(
                    !returnType.isEnum() && returnType.getSimpleName().endsWith("State"),
                    branch.model() + " must not expose a durable state record"
                );
                assertFalse(returnType.getSimpleName().endsWith("Runtime"));
            }
        }
    }

    @Test
    void corruptEnumOrdinalsFallBackInsteadOfEscapingOntoTheRenderThread() throws Exception {
        final Class<?> codec = Class.forName(
            "com.kadamitas.warlockery.entity.EntityPresentationSync"
        );
        final Method decode = codec.getDeclaredMethod("decode", int.class, Enum.class);
        decode.setAccessible(true);

        assertEquals(
            InfernalHierarchyRules.Intent.COMMAND,
            decode.invoke(null, InfernalHierarchyRules.Intent.COMMAND.ordinal(),
                InfernalHierarchyRules.Intent.IDLE)
        );
        assertEquals(
            InfernalHierarchyRules.Intent.IDLE,
            decode.invoke(null, -1, InfernalHierarchyRules.Intent.IDLE)
        );
        assertEquals(
            InfernalHierarchyRules.Intent.IDLE,
            decode.invoke(null, 255, InfernalHierarchyRules.Intent.IDLE)
        );
    }

    private static String source(final String file) throws Exception {
        return Files.readString(ENTITY_ROOT.resolve(file));
    }

    private static Owner owner(final Class<?> type, final String file) {
        return new Owner(type, file);
    }

    private static Getter getter(final String name, final Class<?> returnType) {
        return new Getter(name, returnType);
    }

    private static Branch branch(
        final String model,
        final Class<?> owner,
        final Getter... getters
    ) {
        return new Branch(model, owner, List.of(getters));
    }

    private record Owner(Class<?> type, String file) {
    }

    private record Getter(String name, Class<?> returnType) {
    }

    private record Branch(String model, Class<?> owner, List<Getter> getters) {
    }
}
