package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.compat.jei.JeiRecipeRefreshSignal;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.block.FetishRuntime;
import com.kadamitas.warlockery.block.FetishBindingRules;
import com.kadamitas.warlockery.block.VoidBrambleBlock;
import com.kadamitas.warlockery.block.FetishBindingState;
import com.kadamitas.warlockery.block.StatueBlock;
import com.kadamitas.warlockery.block.StatueWardData;
import com.kadamitas.warlockery.item.CircleTalismanItem;
import com.kadamitas.warlockery.item.BiomeNoteState;
import com.kadamitas.warlockery.item.ManualItem;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.item.SpectralStoneState;
import com.kadamitas.warlockery.item.WaystoneState;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.entity.CreatureBehaviorTags;
import com.kadamitas.warlockery.entity.DeathImpersonationRules;
import com.kadamitas.warlockery.entity.FamiliarRecallRules;
import com.kadamitas.warlockery.entity.NamiEntity;
import com.kadamitas.warlockery.entity.NaamahEntity;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.hex.BlightHex;
import com.kadamitas.warlockery.ritual.hex.ToadRainHex;
import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import com.kadamitas.warlockery.ritual.marriage.MarriageRuntime;
import com.kadamitas.warlockery.util.IngredientAllocator;
import com.kadamitas.warlockery.util.EntityTypeIngredient;
import com.kadamitas.warlockery.util.ItemIngredient;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.TagKey;
import net.minecraftforge.event.ForgeEventFactory;
import org.jspecify.annotations.Nullable;

public final class RitualManager extends SimpleJsonResourceReloadListener<RitualDefinition> {
    public static final double OFFERING_RADIUS = 6.0;
    private static final int REQUIRED_VOLCANIC_SOURCES = 4;
    private static final int VOLCANIC_SEARCH_DEPTH = 48;
    private static final int CLIMATE_EMPOWERMENT_PARTICIPANTS = 5;
    private static final TagKey<Item> COMMON_NETHER_STARS = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("c", "nether_stars")
    );
    private static final java.util.Set<String> TRANSFORMABLE_GLYPHS = java.util.Set.of(
        "circleglyphritual", "circleglyphinfernal", "circleglyph_veil"
    );
    private static final java.util.Set<String> TRANSFORM_CHALKS = java.util.Set.of(
        "chalkritual", "chalkinfernal", "chalk_veil"
    );
    public static final RitualManager INSTANCE = new RitualManager();
    private volatile Map<Identifier, RitualDefinition> rituals = Map.of();

    private RitualManager() {
        super(RitualDefinition.CODEC, FileToIdConverter.json("ritual"));
    }

    @Override
    protected void apply(
        final Map<Identifier, RitualDefinition> definitions,
        final ResourceManager manager,
        final ProfilerFiller profiler
    ) {
        rituals = java.util.Collections.unmodifiableMap(definitions.entrySet().stream()
            .filter(entry -> validate(entry.getKey(), entry.getValue()))
            .sorted(Map.Entry.<Identifier, RitualDefinition>comparingByValue(
                Comparator.comparingInt(RitualManager::specificity).reversed()
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (_, second) -> second, java.util.LinkedHashMap::new)));
        Warlockery.LOGGER.info("Loaded {} Warlockery rituals", rituals.size());
        JeiRecipeRefreshSignal.publish();
    }

    /**
     * Begins the rite, or reports what stood in the way. An empty list means the cast started; otherwise
     * every entry is a requirement the site does not meet, ready to be named to the player.
     *
     * <p>Every refusal produces an entry, including the ones that are not a checklist row: an exit with
     * nothing to say would be indistinguishable from success, and used to leave the player holding a spent
     * circle and one sentence telling them to go and read a screen.</p>
     */
    public List<RequirementStatus> activate(
        final ServerLevel level,
        final BlockPos center,
        final Player caster,
        final Identifier ritualId
    ) {
        final RitualDefinition definition = rituals.get(ritualId);
        if (definition == null) {
            return List.of(unmet("condition", "known_rite"));
        }
        final RitualOption option = diagnose(ritualId, definition, level, center, caster);
        if (!option.ready()) {
            return unmetOf(option.requirements());
        }
        final Optional<TransformSelection> transform = transformSelection(level, center, definition);
        final RitualAction action = RitualAction.require(definition.action());
        if (action == RitualAction.GLYPH_TRANSFORM && transform.isEmpty()) {
            return List.of(unmet("condition", "matching_transform_chalk"));
        }
        final Optional<BiomeShiftPlan> climateShift = action == RitualAction.CLIMATE_SHIFT
            ? Optional.of(climateShiftPlan(level, center, caster))
            : Optional.empty();
        // Everything the activation is going to take is resolved before anything is taken. The offerings are
        // loose item entities and can move or despawn between the inspection and this point, and consuming a
        // plan that no longer holds used to fail after the altar had already been drained.
        final Optional<String> shortfall = transform.isPresent()
            ? Optional.empty()
            : unofferedIngredient(level, center, definition.requirements().ingredients());
        if (shortfall.isPresent()) {
            return List.of(unmet("ingredient", shortfall.orElseThrow()));
        }
        final RitualSessionData sessions = RitualSessionData.get(level);
        final Optional<BlockPos> escrow = escrowAltarPower(level, center, definition.power());
        if (escrow.isEmpty()) {
            return List.of(unmet("power", "altar_power"));
        }
        final BlockPos escrowAltar = escrow.orElseThrow();
        if (transform.isPresent()) {
            consumeTransformChalk(level, center, transform.orElseThrow());
        } else {
            consumeIngredients(level, center, definition.requirements().ingredients());
        }
        climateShift.ifPresent(plan -> consumeClimateNetherStars(level, center, plan.netherStars()));
        consumeEntityRequirements(level, center, definition.requirements().entities());
        final int variant = transform.map(selection -> selection.size().ordinal() + 1)
            .orElseGet(() -> climateShift.map(BiomeShiftPlan::chunkRadius).orElse(0));
        if (!sessions.start(
            center, ritualId, caster.getUUID(), definition.castingTime(), variant,
            escrowAltar, definition.power()
        )) {
            releaseAltarEscrow(level, escrowAltar, definition.power());
            return List.of(unmet("session", "inactive"));
        }
        caster.sendSystemMessage(Component.translatable("message.warlockery.ritual.started"));
        return List.of();
    }

    public List<RitualOption> options(final ServerLevel level, final BlockPos center, final Player caster) {
        return rituals.entrySet().stream()
            .filter(entry -> entry.getValue().visible())
            .map(entry -> diagnose(entry.getKey(), entry.getValue(), level, center, caster))
            .sorted(Comparator.comparing(RitualOption::title).thenComparing(RitualOption::id))
            .toList();
    }

    /**
     * The requirements a cast in progress has stopped meeting. An empty list means the site still supports
     * the rite; anything else is both the reason to cancel and the reason to give the caster.
     */
    List<RequirementStatus> castingObstacles(
        final ServerLevel level,
        final BlockPos center,
        final Identifier ritualId,
        final int variant,
        final @Nullable Player caster
    ) {
        final RitualDefinition definition = rituals.get(ritualId);
        if (definition == null) {
            return List.of(unmet("condition", "known_rite"));
        }
        return unmetOf(inspect(definition, level, center, caster, variant).stream()
            .filter(SiteRequirement::survivesCasting)
            .map(SiteRequirement::status)
            .toList());
    }

    private static List<RequirementStatus> unmetOf(final List<RequirementStatus> statuses) {
        return statuses.stream()
            .filter(RequirementStatus::blocksActivation)
            .filter(status -> !status.met())
            .toList();
    }

    /**
     * A requirement stated as failed, for the refusals that never reach a site inspection.
     */
    private static RequirementStatus unmet(final String category, final String label) {
        return new RequirementStatus(category, label, 1, 0, false);
    }

    void complete(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final Identifier ritualId,
        final int variant
    ) {
        final RitualDefinition definition = rituals.get(ritualId);
        if (definition != null) {
            perform(level, center, caster, definition, variant);
        }
    }

    /**
     * The condition a rite's action imposes on its site, if it imposes one.
     *
     * <p>A rite whose action acts on or through its caster reports an unmet requirement when no caster can be
     * resolved in this level, rather than dropping the requirement. Dropping it let the cast run to term after
     * the caster had walked through a portal or logged out, whereupon the action quietly declined and the
     * player was left with no rite and no cost returned.</p>
     */
    /**
     * True when {@code center} lies inside an ocean monument that has been cleared of its Elder
     * Guardians.
     *
     * <p>Elder Guardians never despawn, so their absence from the structure is exactly the record
     * that the monument was taken. Only loaded entities can be seen, but the caster is standing
     * inside the structure while the circle is inspected, so the occupied part of it is loaded.</p>
     */
    static boolean clearedOceanMonument(final ServerLevel level, final BlockPos center) {
        final StructureStart start = level.structureManager().getStructureWithPieceAt(
            center, holder -> holder.is(BuiltinStructures.OCEAN_MONUMENT)
        );
        if (!start.isValid()) {
            return false;
        }
        return level.getEntitiesOfClass(
            ElderGuardian.class, AABB.of(start.getBoundingBox()), LivingEntity::isAlive
        ).isEmpty();
    }

    static Optional<RequirementStatus> actionEnvironmentRequirement(
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        final RitualAction action = RitualAction.require(definition.action());
        final Optional<RequirementStatus> general = actionEnvironmentRequirement(
            action,
            definition.radius(),
            level,
            center
        );
        if (general.isPresent()) {
            return general;
        }
        if (action == RitualAction.TRANSFORM_NAMI) {
            // The journey is the gate. Nami has to be brought to a drowned monument whose Elder
            // Guardians are already dead, so the rite cannot be performed until the place has
            // actually been taken. Report the unfinished half first: being told the monument is
            // still guarded is more use than being told a married Nami is ineligible.
            if (!clearedOceanMonument(level, center)) {
                return Optional.of(new RequirementStatus(
                    "condition", "cleared_ocean_monument", 1, 0, false
                ));
            }
            final boolean present = unmarriedNami(level, center, definition.radius()).isPresent();
            return Optional.of(new RequirementStatus(
                "condition", "unmarried_nami", 1, present ? 1 : 0, present
            ));
        }
        if (action == RitualAction.CLEANSE) {
            final Optional<LivingEntity> target = boundTargetWithin(level, center, definition.radius());
            final boolean active = target.map(entity -> HexBehaviors.isActive(entity, definition.target())).orElse(false);
            return Optional.of(new RequirementStatus(
                "condition",
                target.isPresent() ? "selected_hex_present" : "bound_hex_target",
                1,
                active ? 1 : 0,
                active
            ));
        }
        if (action == RitualAction.CALL_FAMILIAR) {
            if (caster == null) {
                return Optional.of(absentCaster());
            }
            final boolean present = ownedFamiliar(level, caster).isPresent();
            return Optional.of(new RequirementStatus(
                "condition", "owned_familiar", 1, present ? 1 : 0, present
            ));
        }
        if (action == RitualAction.BIND_FETISH) {
            final boolean present = FetishBindingRules.plan(spectralCounts(level, center, definition.radius())).isPresent();
            return Optional.of(new RequirementStatus(
                "condition", "fetish_spectral_pattern", 1, present ? 1 : 0, present
            ));
        }
        if (action == RitualAction.BIND_ITEM) {
            final boolean present = nearbyItems(level, center).stream()
                .map(ItemEntity::getItem)
                .map(SympatheticBinding::read)
                .anyMatch(Optional::isPresent);
            return Optional.of(new RequirementStatus(
                "condition", "bound_sympathetic_sample", 1, present ? 1 : 0, present
            ));
        }
        if (action == RitualAction.MARRIAGE) {
            if (caster == null) {
                return Optional.of(absentCaster());
            }
            final MarriageData marriages = MarriageData.get(level);
            if (marriages.isMarried(caster.getUUID())) {
                return Optional.of(new RequirementStatus("condition", "unmarried_caster", 1, 0, false));
            }
            final Optional<LivingEntity> partner = marriageCandidate(level, center, caster, definition.radius());
            final boolean available = partner.filter(entity -> entity instanceof NamiEntity nami
                ? marriages.ownerForNami(nami.getUUID()).isEmpty() && marriages.hasAvailableDemonName()
                : entity instanceof Player player && !marriages.isMarried(player.getUUID())).isPresent();
            return Optional.of(new RequirementStatus(
                "condition",
                partner.isPresent() ? "unmarried_partner" : "marriage_partner",
                1,
                available ? 1 : 0,
                available
            ));
        }
        if (action == RitualAction.DIVORCE) {
            if (caster == null) {
                return Optional.of(absentCaster());
            }
            final boolean married = MarriageData.get(level).isMarried(caster.getUUID());
            return Optional.of(new RequirementStatus("condition", "existing_marriage", 1, married ? 1 : 0, married));
        }
        if (action == RitualAction.TRANSFORM_WEREWOLF
            || action == RitualAction.HEX && definition.target().equals("corrupt_doll")) {
            if (caster == null) {
                return Optional.of(absentCaster());
            }
            final boolean present = ownedFamiliar(level, caster).isPresent();
            return Optional.of(new RequirementStatus(
                "condition", "owned_familiar", 1, present ? 1 : 0, present
            ));
        }
        if (action != RitualAction.BIND_ENTITY) {
            return Optional.empty();
        }
        final boolean present = definition.target().equals("spectral")
            ? spectralBindingReady(level, center, definition.radius())
            : bindingCandidate(level, center, definition.radius(), definition.target()).isPresent();
        return Optional.of(new RequirementStatus(
            "condition",
            "nearby_" + definition.target(),
            1,
            present ? 1 : 0,
            present
        ));
    }

    static Optional<RequirementStatus> actionEnvironmentRequirement(
        final RitualAction action,
        final int radius,
        final ServerLevel level,
        final BlockPos center
    ) {
        if (action == RitualAction.EARTHS_WRATH) {
            final int present = countVolcanicSources(level, center, radius);
            return Optional.of(new RequirementStatus(
                "condition",
                "nearby_volcanic_fluid",
                REQUIRED_VOLCANIC_SOURCES,
                present,
                present >= REQUIRED_VOLCANIC_SOURCES
            ));
        }
        if (action == RitualAction.CLIMATE_SHIFT) {
            final boolean present = nearbyRecordedBiome(level, center).isPresent();
            return Optional.of(new RequirementStatus(
                "condition", "recorded_biome_book", 1, present ? 1 : 0, present
            ));
        }
        if (action == RitualAction.PRIOR_INCARNATION) {
            final Optional<LivingEntity> target = boundTarget(level, center)
                .filter(entity -> entity.level() == level)
                .filter(entity -> entity.distanceToSqr(Vec3.atCenterOf(center)) <= (double) radius * radius);
            final int present = target
                .map(LivingEntity::getUUID)
                .map(player -> PriorIncarnationRuntime.countRecoverable(level, center, player, 16))
                .orElse(0);
            return Optional.of(new RequirementStatus(
                "condition", target.isPresent() ? "recoverable_death_drops" : "prior_incarnation_target",
                1, present, present > 0
            ));
        }
        if (action == RitualAction.MANIFEST) {
            final ManifestationRules.Decision decision = ManifestationRuntime.diagnose(
                boundTargetWithin(level, center, radius)
                    .filter(ServerPlayer.class::isInstance)
                    .map(ServerPlayer.class::cast)
                    .orElse(null)
            );
            return Optional.of(new RequirementStatus(
                "condition", decision.diagnostic().id(), 1, decision.ready() ? 1 : 0, decision.ready()
            ));
        }
        if (action == RitualAction.SUMMON_HUNTSMAN) {
            final int present = HuntsmanSummoningStructure.completedBundles(level, center);
            return Optional.of(new RequirementStatus(
                "condition",
                "bloodied_wicker_structure",
                HuntsmanSummoningStructure.REQUIRED_BUNDLES,
                present,
                HuntsmanSummoningStructure.ready(present)
            ));
        }
        if (action == RitualAction.SUMMON_ENTITY) {
            final int blocked = blockedSummoningPositions(level, center);
            return Optional.of(new RequirementStatus(
                "condition", "summoning_space", 0, blocked, blocked == 0
            ));
        }
        return Optional.empty();
    }

    private RitualOption diagnose(
        final Identifier id,
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center,
        final Player caster
    ) {
        final List<RequirementStatus> statuses = inspect(definition, level, center, caster, 0).stream()
            .map(SiteRequirement::status)
            .toList();
        final String title = definition.title().isBlank() ? "ritual.warlockery." + id.getPath() + ".title" : definition.title();
        final String description = definition.description().isBlank()
            ? "ritual.warlockery." + id.getPath() + ".description"
            : definition.description();
        return new RitualOption(
            id.toString(),
            title,
            description,
            definition.power(),
            findBestAltar(level, center).map(AltarBlockEntity::availablePower).orElse(0),
            definition.castingTime(),
            statuses,
            statuses.stream().filter(RequirementStatus::blocksActivation).allMatch(RequirementStatus::met)
        );
    }

    private static List<SiteRequirement> inspect(
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final int variant
    ) {
        final ArrayList<SiteRequirement> requirements = new ArrayList<>();
        requirements.add(persistent(condition(
            "spirit_world_circle_magic",
            !SpiritWorldRuntime.isSpiritWorld(level, caster)
        )));
        if (RitualAction.require(definition.action()) == RitualAction.GLYPH_TRANSFORM) {
            final TransformInspection inspection = inspectTransform(level, center, definition);
            requirements.add(persistent(new RequirementStatus(
                "chalk",
                inspection.sourceGlyph().orElse("chalk_ring"),
                inspection.size().markCount(),
                inspection.presentMarks(),
                variant == 0
                    ? inspection.ringReady()
                    : transformRing(level, center, definition, transformSize(definition, variant)).isPresent()
            )));
            requirements.add(consumedAtStart(new RequirementStatus(
                "ingredient",
                inspection.targetChalk(),
                Math.clamp(inspection.matchingChalk(), 1, 3),
                inspection.matchingChalk(),
                inspection.matchingChalk() >= 1 && inspection.matchingChalk() <= 3
            )));
            requirements.add(consumedAtStart(condition(
                "matching_transform_chalk",
                inspection.foreignChalk() == 0
            )));
        } else {
            ChalkCircleLayout.rings(definition.glyphs()).forEach(ring -> {
                final int present = ChalkCircleLayout.present(level, center, ring);
                requirements.add(persistent(new RequirementStatus(
                    "chalk", ring.glyph(), ring.requiredCount(), present, present == ring.requiredCount()
                )));
            });
            inspectIngredients(level, center, definition.requirements().ingredients())
                .forEach(status -> requirements.add(consumedAtStart(status)));
        }
        inspectEntityRequirements(level, center, definition.requirements().entities())
            .forEach(status -> requirements.add(consumedAtStart(status)));
        final Optional<AltarBlockEntity> altar = findBestAltar(level, center);
        final boolean altarRequired = definition.power() > 0;
        requirements.add(persistent(new RequirementStatus(
            "altar", "structure", altarRequired ? 1 : 0, altar.isPresent() ? 1 : 0,
            !altarRequired || altar.isPresent()
        )));
        final int spendable = altar.map(AltarBlockEntity::availablePower).orElse(0);
        requirements.add(consumedAtStart(new RequirementStatus(
            "power", "altar_power", definition.power(), spendable,
            definition.power() == 0 || spendable >= definition.power()
        )));
        if (definition.nightOnly()) {
            requirements.add(persistent(condition("night", level.isDarkOutside())));
        }
        final RitualDefinition.Requirements declared = definition.requirements();
        if (declared.dayOnly()) {
            requirements.add(persistent(condition("day", !level.isDarkOutside())));
        }
        if (declared.fullMoon()) {
            requirements.add(persistent(condition("full_moon", isFullMoon(level, center))));
        }
        if (declared.raining()) {
            requirements.add(persistent(condition("rain", level.isRaining())));
        }
        if (declared.thundering()) {
            requirements.add(persistent(condition("thunder", level.isThundering())));
        }
        if (!declared.dimension().isBlank()) {
            final boolean matches = declared.dimension().equals(level.dimension().identifier().toString());
            requirements.add(persistent(new RequirementStatus(
                "condition", declared.dimension(), 1, matches ? 1 : 0, matches
            )));
        }
        if (declared.minimumPlayers() > 1) {
            final int present = nearbyParticipants(level, center, caster);
            requirements.add(persistent(new RequirementStatus(
                "coven", "coven", declared.minimumPlayers(), present, present >= declared.minimumPlayers()
            )));
        }
        actionEnvironmentRequirement(definition, level, center, caster)
            .ifPresent(status -> requirements.add(persistent(status)));
        if (RitualAction.require(definition.action()) == RitualAction.CLIMATE_SHIFT) {
            final ClimateShiftInputs inputs = climateShiftInputs(level, center, caster);
            requirements.add(consumedAtStart(new RequirementStatus(
                "optional", "climate_seer_stone", 1, inputs.seerStone() ? 1 : 0, inputs.seerStone()
            )));
            requirements.add(consumedAtStart(new RequirementStatus(
                "optional", "climate_participants", CLIMATE_EMPOWERMENT_PARTICIPANTS,
                inputs.participants(), inputs.participants() >= CLIMATE_EMPOWERMENT_PARTICIPANTS
            )));
            requirements.add(consumedAtStart(new RequirementStatus(
                "optional", "climate_nether_stars", BiomeShiftPlan.MAX_NETHER_STARS,
                inputs.plan().netherStars(), inputs.plan().netherStars() > 0
            )));
        }
        final int inhibitors = countRitualInhibitors(level, center, definition.radius());
        requirements.add(persistent(new RequirementStatus(
            "condition", "ritual_inhibitors", 0, inhibitors, inhibitors == 0
        )));
        final boolean atCenter = isCircleCenter(level, center);
        requirements.add(persistent(new RequirementStatus(
            "center", "circle_center", 1, atCenter ? 1 : 0, atCenter
        )));
        final boolean inactive = !RitualSessionData.get(level).isActive(center);
        requirements.add(consumedAtStart(new RequirementStatus(
            "session", "inactive", 1, inactive ? 1 : 0, inactive
        )));
        return List.copyOf(requirements);
    }

    /**
     * The requirement a caster-driven rite reports when its caster is not in this level. Stated as unmet rather
     * than omitted so the site is judged incomplete for as long as the caster is away.
     */
    private static RequirementStatus absentCaster() {
        return new RequirementStatus("condition", "caster_present", 1, 0, false);
    }

    private static SiteRequirement persistent(final RequirementStatus status) {
        return new SiteRequirement(status, status.blocksActivation());
    }

    private static SiteRequirement consumedAtStart(final RequirementStatus status) {
        return new SiteRequirement(status, false);
    }

    private static RequirementStatus condition(final String label, final boolean met) {
        return new RequirementStatus("condition", label, 1, met ? 1 : 0, met);
    }

    private static List<RequirementStatus> inspectIngredients(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.Ingredient> requirements
    ) {
        final List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(OFFERING_RADIUS), ItemEntity::isAlive);
        final IngredientAllocator.Allocation allocation = IngredientAllocator.allocate(
            requirements,
            entities.stream().map(ItemEntity::getItem).toList()
        );
        return allocation.requirements().stream()
            .map(match -> new RequirementStatus(
                "ingredient",
                match.requirement().ingredient(),
                match.requirement().count(),
                match.matched(),
                match.complete()
            ))
            .toList();
    }

    private static void consumeIngredients(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.Ingredient> requirements
    ) {
        final List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(OFFERING_RADIUS), ItemEntity::isAlive);
        final List<ItemStack> stacks = entities.stream().map(ItemEntity::getItem).toList();
        final List<RitualDefinition.Ingredient> consumed = requirements.stream()
            .filter(RitualDefinition.Ingredient::consume)
            .toList();
        IngredientAllocator.allocate(consumed, stacks).consumeFrom(stacks);
        for (int index = 0; index < entities.size(); index++) {
            if (stacks.get(index).isEmpty()) {
                entities.get(index).discard();
            } else {
                entities.get(index).setItem(stacks.get(index));
            }
        }
    }

    private static TransformInspection inspectTransform(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final String targetChalk = targetTransformChalk(definition).orElse("");
        int matching = 0;
        int foreign = 0;
        for (final ItemEntity entity : level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(center).inflate(OFFERING_RADIUS),
            ItemEntity::isAlive
        )) {
            final Identifier itemId = BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
            if (itemId == null || !Warlockery.MOD_ID.equals(itemId.getNamespace())
                || !TRANSFORM_CHALKS.contains(itemId.getPath())) {
                continue;
            }
            if (targetChalk.equals(itemId.toString())) {
                matching += entity.getItem().getCount();
            } else {
                foreign += entity.getItem().getCount();
            }
        }
        final ChalkCircleLayout.Size size = matching >= 1 && matching <= 3
            ? ChalkCircleLayout.Size.forOfferingCount(matching)
            : transformSize(definition, 0);
        final Optional<String> source = transformRing(level, center, definition, size);
        return new TransformInspection(
            targetChalk,
            matching,
            foreign,
            size,
            source,
            ChalkCircleLayout.presentGlyphs(level, center, size, TRANSFORMABLE_GLYPHS)
        );
    }

    private static Optional<TransformSelection> transformSelection(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        if (RitualAction.require(definition.action()) != RitualAction.GLYPH_TRANSFORM) {
            return Optional.empty();
        }
        final TransformInspection inspection = inspectTransform(level, center, definition);
        return inspection.ready()
            ? Optional.of(new TransformSelection(
                inspection.targetChalk(),
                1,
                inspection.size()
            ))
            : Optional.empty();
    }

    private static Optional<String> targetTransformChalk(final RitualDefinition definition) {
        return definition.requirements().ingredients().stream()
            .map(RitualDefinition.Ingredient::ingredient)
            .map(Identifier::tryParse)
            .filter(java.util.Objects::nonNull)
            .filter(id -> Warlockery.MOD_ID.equals(id.getNamespace()))
            .filter(id -> TRANSFORM_CHALKS.contains(id.getPath()))
            .map(Identifier::toString)
            .findFirst();
    }

    private static void consumeTransformChalk(
        final ServerLevel level,
        final BlockPos center,
        final TransformSelection selection
    ) {
        int remaining = selection.count();
        for (final ItemEntity entity : level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(center).inflate(OFFERING_RADIUS),
            ItemEntity::isAlive
        )) {
            if (remaining == 0) {
                break;
            }
            final Identifier itemId = BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
            if (itemId == null || !selection.targetChalk().equals(itemId.toString())) {
                continue;
            }
            final int consumed = Math.min(remaining, entity.getItem().getCount());
            entity.getItem().shrink(consumed);
            remaining -= consumed;
            if (entity.getItem().isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(entity.getItem());
            }
        }
    }

    static List<RequirementStatus> inspectEntityRequirements(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.EntityRequirement> requirements
    ) {
        final List<Mob> entities = level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(OFFERING_RADIUS), Mob::isAlive);
        final var reserved = new HashSet<java.util.UUID>();
        final List<RequirementStatus> statuses = new ArrayList<>();
        for (final RitualDefinition.EntityRequirement requirement : requirements) {
            final List<Mob> matched = reserveMatching(entities, reserved, requirement);
            matched.forEach(entity -> reserved.add(entity.getUUID()));
            statuses.add(new RequirementStatus(
                "entity",
                requirement.entity(),
                requirement.count(),
                matched.size(),
                matched.size() >= requirement.count()
            ));
        }
        return List.copyOf(statuses);
    }

    static void consumeEntityRequirements(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.EntityRequirement> requirements
    ) {
        final List<Mob> entities = level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(OFFERING_RADIUS), Mob::isAlive);
        final var claimed = new HashSet<java.util.UUID>();
        // Inspection reserves across every entity requirement out of one pool, so a mob it counted towards a
        // presence-only requirement is already spoken for. Claiming those first here keeps the two passes on the
        // same assignment; without it a consuming requirement whose matcher is broader eats the mob the site was
        // told only had to be standing there, and the rite destroys something it never declared it would.
        requirements.stream()
            .filter(requirement -> !requirement.consume())
            .forEach(requirement -> reserveMatching(entities, claimed, requirement)
                .forEach(entity -> claimed.add(entity.getUUID())));
        requirements.stream()
            .filter(RitualDefinition.EntityRequirement::consume)
            .forEach(requirement -> reserveMatching(entities, claimed, requirement).forEach(entity -> {
                claimed.add(entity.getUUID());
                entity.discard();
            }));
    }

    /**
     * The mobs one entity requirement claims out of the shared pool: up to its declared count, skipping any
     * already claimed. Shared so inspection and consumption cannot drift into two different assignments.
     */
    private static List<Mob> reserveMatching(
        final List<Mob> entities,
        final java.util.Set<java.util.UUID> claimed,
        final RitualDefinition.EntityRequirement requirement
    ) {
        return EntityTypeIngredient.parse(requirement.entity()).stream()
            .flatMap(ingredient -> entities.stream()
                .filter(entity -> !claimed.contains(entity.getUUID()))
                .filter(ingredient::matches)
                .limit(requirement.count()))
            .toList();
    }

    private static boolean isFullMoon(final ServerLevel level, final BlockPos center) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, Vec3.atCenterOf(center)) == MoonPhase.FULL_MOON
            && level.isDarkOutside();
    }

    /**
      * The participants this rite may draw on. Every ritual counts the coven of the player who started it and
      * nobody else's, so the caster is threaded to every site that asks; there is deliberately no overload
      * that counts every Mage in range, because such an overload is exactly how the rule would erode.
      */
    static int nearbyParticipants(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        return SeerCovenRuntime.countParticipants(
            level,
            center,
            SeerCovenRuntime.PARTICIPANT_RADIUS,
            caster == null ? null : caster.getUUID()
        );
    }

    static int blockedSummoningPositions(final ServerLevel level, final BlockPos center) {
        return (int) BlockPos.betweenClosedStream(
                center.offset(-3, 1, -3),
                center.offset(3, 4, 3)
            )
            .filter(position -> level.getBlockEntity(position) != null
                || !level.getBlockState(position).canBeReplaced())
            .count();
    }

    private static int countRitualInhibitors(
        final ServerLevel level,
        final BlockPos center,
        final int ritualRadius
    ) {
        final int radius = Math.clamp(ritualRadius + 4, 6, 16);
        final int fixedInhibitors = Math.toIntExact(BlockPos.betweenClosedStream(
            center.offset(-radius, -radius, -radius),
            center.offset(radius, radius, radius)
        ).filter(pos -> {
            final BlockState state = level.getBlockState(pos);
            if (!state.is(WarlockeryTags.Blocks.RITUAL_INHIBITORS)
                || state.getBlock() instanceof VoidBrambleBlock) {
                return false;
            }
            return !(state.getBlock() instanceof StatueBlock statue) || statue.occludes(state);
        }).count());
        return ritualInhibitorCount(
            fixedInhibitors,
            VoidBrambleBlock.suppressesMagic(level, center),
            StatueWardData.get(level).occludesSummoning(center)
        );
    }

    static int ritualInhibitorCount(
        final int fixedInhibitors,
        final boolean voidBrambleSuppression,
        final boolean statueSuppression
    ) {
        return Math.max(0, fixedInhibitors)
            + (voidBrambleSuppression ? 1 : 0)
            + (statueSuppression ? 1 : 0);
    }

    public static boolean isCircleCenter(final ServerLevel level, final BlockPos center) {
        return level.getBlockState(center).is(ModBlocks.ALL.get("circle").get());
    }

    /**
     * Sets aside the rite's power without draining it, and reports which altar is holding it.
     *
     * <p>A cast used to spend the altar the instant it began, so a circle broken on the first tick of a five
     * hundred tick rite destroyed the whole cost. Holding it instead means the power is unavailable to anything
     * else for the duration but is still there to hand back if the cast does not finish.</p>
     *
     * <p>A rite that costs nothing still names its altar, so the session records a consistent position.</p>
     */
    private static Optional<BlockPos> escrowAltarPower(
        final ServerLevel level,
        final BlockPos center,
        final int power
    ) {
        if (power == 0) {
            return Optional.of(center);
        }
        return findBestAltar(level, center)
            .filter(altar -> altar.escrowPower(power))
            .map(AltarBlockEntity::getBlockPos);
    }

    private static void releaseAltarEscrow(final ServerLevel level, final BlockPos altarPos, final int power) {
        if (power > 0 && level.getBlockEntity(altarPos) instanceof AltarBlockEntity altar) {
            altar.releaseEscrow(power);
        }
    }

    /**
     * The first offering the circle can no longer supply, or empty when the whole consumption plan still
     * holds. Resolved before anything is taken, because the offerings are loose item entities that can drift,
     * be picked up or despawn between the site inspection and the moment a cast commits.
     */
    private static Optional<String> unofferedIngredient(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.Ingredient> requirements
    ) {
        final List<ItemStack> offered = level
            .getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(OFFERING_RADIUS), ItemEntity::isAlive)
            .stream()
            .map(ItemEntity::getItem)
            .toList();
        return IngredientAllocator.allocate(
                requirements.stream().filter(RitualDefinition.Ingredient::consume).toList(),
                offered
            )
            .requirements()
            .stream()
            .filter(match -> !match.complete())
            .map(match -> match.requirement().ingredient())
            .findFirst();
    }

    private static boolean hasValidAltar(final ServerLevel level, final BlockPos center) {
        return findBestAltar(level, center).isPresent();
    }

    /**
     * The richest usable altar near the circle, ranked by what it can still spend rather than what it holds.
     * An altar already holding power for another cast must not be picked for a second one on the strength of
     * power that is already promised away.
     */
    private static Optional<AltarBlockEntity> findBestAltar(final ServerLevel level, final BlockPos center) {
        final int range = 12;
        return BlockPos.betweenClosedStream(center.offset(-range, -4, -range), center.offset(range, 6, range))
            .map(level::getBlockEntity)
            .filter(AltarBlockEntity.class::isInstance)
            .map(AltarBlockEntity.class::cast)
            .filter(AltarBlockEntity::isMultiblockValid)
            .max(Comparator.comparingInt(AltarBlockEntity::availablePower));
    }

    private static void perform(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition,
        final int variant
    ) {
        final boolean changedTheWorld = dispatch(level, center, caster, definition, variant);
        if (changedTheWorld) {
            level.sendParticles(
                ParticleTypes.ENCHANT,
                center.getX() + 0.5,
                center.getY() + 0.3,
                center.getZ() + 0.5,
                120,
                definition.radius() * 0.5,
                1.0,
                definition.radius() * 0.5,
                0.1
            );
            level.playSound(null, center, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
        } else {
            level.sendParticles(
                ParticleTypes.SMOKE,
                center.getX() + 0.5,
                center.getY() + 0.3,
                center.getZ() + 0.5,
                40,
                definition.radius() * 0.3,
                0.4,
                definition.radius() * 0.3,
                0.02
            );
            level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.8F);
        }
        if (caster != null) {
            caster.sendSystemMessage(Component.translatable(changedTheWorld
                ? "message.warlockery.ritual.success"
                : "message.warlockery.ritual.no_effect"));
        }
    }

    private static boolean dispatch(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition,
        final int variant
    ) {
        return switch (RitualAction.require(definition.action())) {
            case EFFECT -> applyEffect(level, center, definition);
            case STORM -> setStorm(level, definition.duration());
            case FERTILITY -> fertility(level, center, caster, definition);
            case FORESTATION -> forestation(level, center, definition);
            case NATURES_POWER -> restoreNature(level, center, definition.radius());
            case BLIGHT -> applyBlight(level, center, caster, definition);
            case TOAD_RAIN -> {
                startRain(level, definition.duration());
                yield ToadRainHex.apply(
                    level,
                    center,
                    definition.radius(),
                    definition.count(),
                    definition.duration()
                ).spawned() > 0;
            }
            case BANISH -> banish(level, center, definition.radius());
            case CALL_BEASTS -> callBeasts(level, center, definition);
            case CALL_FAMILIAR -> callFamiliar(level, center, caster);
            case ANGUISH_UNDEAD -> anguishUndead(level, center, definition);
            case DRAIN_GROWTH -> drainGrowth(level, center, definition.radius());
            case FORTIFY_UNDEAD -> fortifyUndead(level, center, definition);
            case GRAVEYARD_MIST -> graveyardMist(level, center, definition);
            case SUMMON_ENTITY -> summonEntities(level, center, caster, definition);
            case SUMMON_HUNTSMAN -> summonHuntsman(level, center, caster, definition);
            case SUMMON_ITEM -> summonItem(level, center, definition);
            case RAISE_COLUMN -> raiseColumn(level, center, definition);
            case BROKEN_EARTH -> createFissure(level, center, caster, definition.radius());
            case EARTHS_WRATH -> raiseVolcano(level, center, definition.radius());
            case SKYS_WRATH -> skyWrath(level, center, caster, definition);
            case HELL_ON_EARTH -> hellOnEarth(level, center, definition);
            case COOK -> cookItems(level, center, definition.radius());
            case ECLIPSE -> eclipse(level, center, definition);
            case REMOVE_VAMPIRISM -> removeVampirism(level, center, definition.radius());
            case TRANSFORM_NAMI -> transformNami(level, center, caster, definition.radius());
            case TRANSFORM_WEREWOLF -> transform(
                level,
                center,
                caster,
                definition.radius(),
                SupernaturalForm.WEREWOLF
            );
            case REMOVE_WEREWOLF -> removeWerewolf(level, center, definition.radius());
            case HEX -> applyHex(level, center, caster, definition);
            case CLEANSE -> cleanse(level, center, definition);
            case BIND_CIRCLE -> CircleTalismanItem.captureFromRitual(level, center);
            case BIND_WAYSTONE -> bindWaystone(level, center);
            case COPY_WAYSTONE -> copyWaystone(level, center);
            case TELEPORT_WAYSTONE -> teleportToWaystone(level, center, caster);
            case TELEPORT_ENTITY -> teleportBoundEntity(level, center, definition);
            case TRANSPOSE_ORE -> transposeOres(level, center, definition.radius());
            case ICE_SPHERE -> iceSphere(level, center, definition.radius(), nearbyParticipants(level, center, caster));
            case MANIFEST -> manifest(level, center, caster, definition);
            case IMPRISONMENT_WARD -> placeWard(level, center, definition, RitualWardType.IMPRISONMENT);
            case PROTECTION_WARD -> placeWard(level, center, definition, RitualWardType.PROTECTION);
            case SANCTITY_WARD -> placeWard(level, center, definition, RitualWardType.SANCTITY);
            case CLIMATE_SHIFT -> shiftClimate(level, center, variant);
            case PRIOR_INCARNATION -> priorIncarnation(level, center, caster, definition);
            case INFUSE_PATH -> infusePath(level, center, definition);
            case RECHARGE_PATH -> rechargePaths(level, center, definition);
            case BIND_ENTITY -> bindEntity(level, center, caster, definition);
            case BIND_FETISH -> bindFetish(level, center, definition);
            case BIND_ITEM -> bindItem(level, center, definition);
            case MARRIAGE -> marry(level, center, caster, definition);
            case DIVORCE -> divorce(level, center, caster);
            case GLYPH_TRANSFORM -> transformGlyphs(level, center, definition, transformSize(definition, variant));
        };
    }

    private static boolean applyEffect(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(definition.effect())).map(effect -> {
            final AABB area = new AABB(center).inflate(definition.radius());
            final List<LivingEntity> affected = level.getEntitiesOfClass(LivingEntity.class, area);
            affected.forEach(entity ->
                entity.addEffect(new MobEffectInstance(effect, definition.duration(), definition.amplifier()))
            );
            return !affected.isEmpty();
        }).orElse(false);
    }

    private static boolean setStorm(final ServerLevel level, final int duration) {
        final var weather = level.getWeatherData();
        weather.setRaining(true);
        weather.setThundering(true);
        weather.setRainTime(duration);
        weather.setThunderTime(duration);
        return true;
    }

    private static void startRain(final ServerLevel level, final int duration) {
        final var weather = level.getWeatherData();
        weather.setRaining(true);
        weather.setThundering(false);
        weather.setRainTime(Math.max(200, duration));
        weather.setThunderTime(0);
    }

    private static boolean fertility(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        final int grown = growTaggedPlants(level, center, definition.radius(), 1);
        final AABB area = new AABB(center).inflate(definition.radius());
        final List<Player> players = level.getEntitiesOfClass(Player.class, area);
        players.forEach(player -> {
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.NAUSEA);
            player.removeEffect(MobEffects.BLINDNESS);
        });
        final List<ZombieVillager> zombies = level.getEntitiesOfClass(ZombieVillager.class, area);
        zombies.forEach(zombie -> cureZombieVillager(level, zombie));
        if (caster != null && hasFertilityFamiliar(level, caster, definition.radius())) {
            players.forEach(player -> {
                player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    Math.max(200, definition.duration() / 2),
                    Math.max(0, definition.amplifier())
                ));
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 0));
            });
        }
        return grown > 0 || !players.isEmpty() || !zombies.isEmpty();
    }

    private static void cureZombieVillager(final ServerLevel level, final ZombieVillager zombie) {
        if (!ForgeEventFactory.canLivingConvert(zombie, EntityTypes.VILLAGER, _ -> { })) {
            return;
        }
        zombie.convertTo(EntityTypes.VILLAGER, ConversionParams.single(zombie, false, false), villager -> {
            villager.setVillagerDataFinalized(zombie.getVillagerDataFinalized());
            villager.setVillagerData(zombie.getVillagerData());
            villager.setVillagerXp(zombie.getVillagerXp());
            villager.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(villager.blockPosition()),
                EntitySpawnReason.CONVERSION,
                null
            );
            villager.refreshBrain(level);
            level.levelEvent(null, 1027, zombie.blockPosition(), 0);
            ForgeEventFactory.onLivingConvert(zombie, villager);
        });
    }

    private static boolean hasFertilityFamiliar(
        final ServerLevel level,
        final Player caster,
        final int radius
    ) {
        return !level.getEntitiesOfClass(
            LivingEntity.class,
            caster.getBoundingBox().inflate(Math.max(6, radius)),
            entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.FERTILITY_FAMILIARS)
        ).isEmpty();
    }

    private static int growTaggedPlants(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int passes
    ) {
        return Math.toIntExact(BlockPos.betweenClosedStream(
                center.offset(-radius, -2, -radius),
                center.offset(radius, 3, radius)
            )
            .filter(pos -> pos.distSqr(center) <= radius * radius)
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_GROWABLES))
            .limit(2_048)
            .map(BlockPos::immutable)
            .toList()
            .stream()
            .filter(pos -> forceGrow(level, pos, passes))
            .count());
    }

    private static boolean forceGrow(final ServerLevel level, final BlockPos pos, final int passes) {
        boolean grew = false;
        for (int pass = 0; pass < passes; pass++) {
            final BlockState state = level.getBlockState(pos);
            if (!state.is(WarlockeryTags.Blocks.RITUAL_GROWABLES)
                || !(state.getBlock() instanceof BonemealableBlock growable)
                || !growable.isValidBonemealTarget(level, pos, state)) {
                return grew;
            }
            growable.performBonemeal(level, level.getRandom(), pos, state);
            grew = true;
        }
        return grew;
    }

    private static boolean restoreNature(final ServerLevel level, final BlockPos center, final int radius) {
        final long restored = BlockPos.betweenClosedStream(
                center.offset(-radius, -2, -radius),
                center.offset(radius, 3, radius)
            )
            .filter(pos -> pos.distSqr(center) <= radius * radius)
            .limit(4_096)
            .map(BlockPos::immutable)
            .toList()
            .stream()
            .filter(pos -> repairNature(level, pos))
            .count();
        return growTaggedPlants(level, center, radius, 2) > 0 || restored > 0;
    }

    private static boolean repairNature(final ServerLevel level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.is(WarlockeryTags.Blocks.NATURE_REPAIRABLE_SOILS)
            && level.getBlockEntity(pos) == null
            && level.getBlockState(pos.above()).canBeReplaced()) {
            return level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
        }
        if (!state.is(WarlockeryTags.Blocks.NATURE_DAMAGED_VEGETATION)) {
            return false;
        }
        final BlockState grass = Blocks.SHORT_GRASS.defaultBlockState();
        return grass.canSurvive(level, pos) && level.setBlockAndUpdate(pos, grass);
    }

    private static boolean forestation(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final BlockPos origin = nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .map(WaystoneState::read)
            .flatMap(Optional::stream)
            .filter(location -> location.dimension().equals(level.dimension().identifier()))
            .map(WaystoneState.Location::position)
            .findFirst()
            .orElse(center);
        level.getChunkAt(origin);
        final long grown = BlockPos.betweenClosedStream(
                origin.offset(-definition.radius(), -2, -definition.radius()),
                origin.offset(definition.radius(), 3, definition.radius())
            )
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_SAPLINGS))
            .map(BlockPos::immutable)
            .limit(128)
            .toList()
            .stream()
            .filter(pos -> forceGrow(level, pos, 3))
            .count();

        int placed = 0;
        for (BlockPos column : RitualTerrainPlan.forestColumns(origin, definition.radius())) {
            if (placed >= Math.clamp(definition.count(), 1, 32)) {
                break;
            }
            final Optional<BlockState> sapling = BuiltInRegistries.BLOCK
                .getRandomElementOf(WarlockeryTags.Blocks.RITUAL_SAPLINGS, level.getRandom())
                .map(holder -> holder.value().defaultBlockState())
                .filter(state -> state.getBlock() instanceof BonemealableBlock);
            if (sapling.isEmpty()) {
                break;
            }
            final Optional<BlockPos> destination = findPlantingPosition(level, column, sapling.orElseThrow());
            if (destination.isPresent()) {
                final BlockPos pos = destination.orElseThrow();
                level.setBlockAndUpdate(pos, sapling.orElseThrow());
                forceGrow(level, pos, 3);
                placed++;
            }
        }
        return placed > 0 || grown > 0;
    }

    private static Optional<BlockPos> findPlantingPosition(
        final ServerLevel level,
        final BlockPos column,
        final BlockState sapling
    ) {
        for (int offset = 3; offset >= -3; offset--) {
            final BlockPos pos = column.offset(0, offset, 0);
            if (level.getBlockEntity(pos) == null
                && level.getBlockState(pos).canBeReplaced()
                && sapling.canSurvive(level, pos)) {
                return Optional.of(pos.immutable());
            }
        }
        return Optional.empty();
    }

    private static boolean banish(final ServerLevel level, final BlockPos center, final int radius) {
        final List<LivingEntity> demons = level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(radius),
            entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.DEMONS)
        );
        demons.forEach(LivingEntity::discard);
        return !demons.isEmpty();
    }

    private static boolean callBeasts(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final List<Mob> beasts = level.getEntitiesOfClass(
                Mob.class,
                new AABB(center).inflate(Math.max(16, definition.radius() * 4)),
                mob -> mob.typeHolder().is(WarlockeryTags.EntityTypes.RITUAL_BEASTS)
            ).stream()
            .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(
                center.getX() + 0.5,
                center.getY() + 0.5,
                center.getZ() + 0.5
            )))
            .limit(Math.clamp(definition.count(), 1, 128))
            .toList();
        beasts.forEach(mob -> mob.getNavigation().moveTo(
            center.getX() + 0.5,
            center.getY() + 0.5,
            center.getZ() + 0.5,
            1.25
        ));
        return !beasts.isEmpty();
    }

    private static boolean anguishUndead(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final List<LivingEntity> affected =
            level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(definition.radius()));
        affected.forEach(entity -> entity.addEffect(new MobEffectInstance(
            MobEffects.STRENGTH,
            definition.duration(),
            Math.max(0, definition.amplifier())
        )));
        return !affected.isEmpty();
    }

    private static boolean drainGrowth(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        final long drained = BlockPos.betweenClosedStream(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_CROPS))
            .filter(pos -> level.getBlockState(pos).getBlock() instanceof CropBlock)
            .filter(pos -> {
                final var state = level.getBlockState(pos);
                return ((CropBlock) state.getBlock()).getAge(state) > 0;
            })
            .limit(256)
            .mapToLong(pos -> {
                final CropBlock crop = (CropBlock) level.getBlockState(pos).getBlock();
                level.setBlockAndUpdate(pos, crop.getStateForAge(0));
                return 1L;
            })
            .sum();
        final List<LivingEntity> undead = level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(radius),
            entity -> entity.typeHolder().is(EntityTypeTags.UNDEAD)
        );
        undead.forEach(entity -> entity.heal(Math.min(20.0F, drained * 0.5F)));
        return drained > 0 || !undead.isEmpty();
    }

    private static boolean fortifyUndead(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final List<LivingEntity> affected =
            level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(definition.radius()));
        affected.forEach(entity -> entity.addEffect(new MobEffectInstance(
            MobEffects.RESISTANCE,
            definition.duration(),
            Math.max(0, definition.amplifier())
        )));
        return !affected.isEmpty();
    }

    private static boolean graveyardMist(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final List<LivingEntity> affected =
            level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(definition.radius()));
        affected.forEach(entity -> {
            if (entity.typeHolder().is(EntityTypeTags.UNDEAD)) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, definition.duration(), definition.amplifier()));
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, definition.duration(), 0));
            } else {
                entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, definition.duration(), 0));
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, definition.duration(), definition.amplifier()));
            }
        });
        level.sendParticles(
            ParticleTypes.ASH,
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            Math.clamp(definition.radius() * 24, 24, 384),
            definition.radius() * 0.6,
            1.5,
            definition.radius() * 0.6,
            0.02
        );
        return !affected.isEmpty();
    }

    private static boolean summonEntities(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        final Identifier id = Identifier.tryParse(definition.target());
        if (id == null) {
            return false;
        }
        if (id.equals(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "death"))) {
            final Optional<Player> impersonator = level.getEntitiesOfClass(
                    Player.class,
                    new AABB(center).inflate(Math.max(8, definition.radius())),
                    DeathImpersonationRules::isComplete
                ).stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(Vec3.atCenterOf(center))));
            if (impersonator.isPresent()) {
                impersonator.orElseThrow().teleportTo(
                    center.getX() + 0.5,
                    center.getY() + 1.0,
                    center.getZ() + 0.5
                );
                return true;
            }
        }
        return BuiltInRegistries.ENTITY_TYPE.get(id).map(holder -> java.util.stream.IntStream
            .range(0, Math.clamp(definition.count(), 1, 16))
            .mapToObj(index -> holder.value().create(level, EntitySpawnReason.COMMAND))
            .filter(java.util.Objects::nonNull)
            .filter(entity -> {
                final double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                final double distance = 1.5 + level.getRandom().nextDouble() * Math.max(1, definition.radius() - 1);
                entity.snapTo(center.getX() + 0.5 + Math.cos(angle) * distance, center.getY() + 1.0, center.getZ() + 0.5 + Math.sin(angle) * distance);
                return level.addFreshEntity(entity);
            })
            .count() > 0
        ).orElse(false);
    }

    static boolean transformNami(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final int radius
    ) {
        final Optional<NamiEntity> candidate = unmarriedNami(level, center, radius);
        if (candidate.isEmpty()) {
            return false;
        }
        final NamiEntity nami = candidate.orElseThrow();
        final var created = ModEntities.ALL.get("naamah").get().create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof NaamahEntity naamah)) {
            return false;
        }
        naamah.snapTo(nami.getX(), nami.getY(), nami.getZ());
        naamah.setYRot(nami.getYRot());
        if (caster instanceof ServerPlayer player) {
            naamah.getPersistentData().putString(
                com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime.NAAMAH_TRIAL_OWNER,
                player.getStringUUID()
            );
        }
        if (!level.addFreshEntity(naamah)) {
            return false;
        }
        nami.discard();
        level.sendParticles(
            ParticleTypes.FLAME,
            naamah.getX(),
            naamah.getEyeY(),
            naamah.getZ(),
            48,
            0.45,
            0.8,
            0.45,
            0.04
        );
        return true;
    }

    private static Optional<NamiEntity> unmarriedNami(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        final MarriageData marriages = MarriageData.get(level);
        return level.getEntitiesOfClass(
                NamiEntity.class,
                new AABB(center).inflate(Math.max(6, radius)),
                nami -> nami.isAlive() && marriages.ownerForNami(nami.getUUID()).isEmpty()
            ).stream()
            .min(Comparator.comparingDouble(nami -> nami.distanceToSqr(Vec3.atCenterOf(center))));
    }

    private static boolean callFamiliar(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        if (caster == null || FetishRuntime.protects(caster)) {
            return false;
        }
        return ownedFamiliar(level, caster).map(familiar -> familiar.teleport(
            new net.minecraft.world.level.portal.TeleportTransition(
                level,
                Vec3.atCenterOf(center).add(0.0, 1.0, 0.0),
                Vec3.ZERO,
                familiar.getYRot(),
                familiar.getXRot(),
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING
            )
        ) != null).orElse(false);
    }

    private static Optional<Mob> ownedFamiliar(final ServerLevel level, final Player caster) {
        return StreamSupport.stream(level.getServer().getAllLevels().spliterator(), false)
            .flatMap(candidateLevel -> StreamSupport.stream(candidateLevel.getAllEntities().spliterator(), false))
            .filter(Mob.class::isInstance)
            .map(Mob.class::cast)
            .filter(entity -> FamiliarRecallRules.eligible(
                entity.typeHolder().is(CreatureBehaviorTags.EntityTypes.FAMILIARS),
                entity.isAlive(),
                CreatureBehaviorState.isOwnedBy(entity, caster.getUUID())
            ))
            .min(Comparator.comparingDouble(entity -> entity.level() == level
                ? entity.distanceToSqr(caster)
                : Double.MAX_VALUE));
    }

    private static boolean summonHuntsman(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        return HuntsmanSummoningStructure.consume(level, center)
            && summonEntities(level, center, caster, definition);
    }

    private static boolean summonItem(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        final Identifier id = Identifier.tryParse(definition.target());
        if (id == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.get(id).map(holder -> level.addFreshEntity(new ItemEntity(
            level,
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            new ItemStack(holder.value(), Math.clamp(definition.count(), 1, 64))
        ))).orElse(false);
    }

    private static boolean raiseColumn(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        final Identifier id = Identifier.tryParse(definition.target());
        if (id == null) {
            return false;
        }
        return BuiltInRegistries.BLOCK.get(id).map(holder -> {
            final int radius = Math.clamp(definition.radius() / 3, 1, 4);
            final int height = Math.clamp(definition.count(), 1, 16);
            return BlockPos.betweenClosedStream(
                    center.offset(-radius, 0, -radius),
                    center.offset(radius, height, radius)
                )
                .filter(pos -> {
                    final int dx = pos.getX() - center.getX();
                    final int dz = pos.getZ() - center.getZ();
                    return dx * dx + dz * dz <= radius * radius;
                })
                .filter(pos -> level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced())
                .limit(2_048)
                .map(BlockPos::immutable)
                .toList()
                .stream()
                .filter(pos -> level.setBlockAndUpdate(pos, holder.value().defaultBlockState()))
                .count() > 0;
        }).orElse(false);
    }

    private static boolean createFissure(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final int radius
    ) {
        final Direction direction = caster == null ? Direction.NORTH : caster.getDirection();
        return RitualTerrainPlan.fissure(center, direction, radius).stream()
            .filter(level::isLoaded)
            .filter(pos -> level.getBlockEntity(pos) == null)
            .filter(pos -> {
                final BlockState state = level.getBlockState(pos);
                return state.is(WarlockeryTags.Blocks.FISSURE_BREAKABLES)
                    && state.getDestroySpeed(level, pos) >= 0.0F;
            })
            .limit(768)
            .toList()
            .stream()
            .filter(pos -> level.destroyBlock(pos, false))
            .count() > 0;
    }

    private static boolean raiseVolcano(final ServerLevel level, final BlockPos center, final int radius) {
        return nearestVolcanicSource(level, center, radius).map(source -> {
            final FluidState volcanicFluid = level.getFluidState(source);
            final int surface = Math.max(
                center.getY() + 1,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ())
            );
            final int height = Math.clamp(radius / 2, 3, 6);
            final BlockPos basin = new BlockPos(center.getX(), surface + height, center.getZ());
            final List<BlockPos> column = java.util.stream.IntStream.range(surface, basin.getY())
                .mapToObj(y -> new BlockPos(center.getX(), y, center.getZ()))
                .toList();
            final List<BlockPos> rim = BlockPos.betweenClosedStream(
                    basin.offset(-1, 0, -1), basin.offset(1, 0, 1)
                )
                .filter(pos -> !pos.equals(basin))
                .map(BlockPos::immutable)
                .toList();
            if (java.util.stream.Stream.concat(column.stream(), rim.stream()).allMatch(pos -> safeTerrainPlacement(level, pos))
                && safeTerrainPlacement(level, basin)) {
                column.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.BASALT.defaultBlockState()));
                level.setBlockAndUpdate(basin.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
                rim.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.BASALT.defaultBlockState()));
                level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(basin, volcanicFluid.createLegacyBlock());
                return true;
            }
            return false;
        }).orElse(false);
    }

    private static boolean safeTerrainPlacement(final ServerLevel level, final BlockPos pos) {
        return level.isLoaded(pos)
            && level.isInWorldBounds(pos)
            && level.getBlockEntity(pos) == null
            && level.getBlockState(pos).canBeReplaced();
    }

    private static int countVolcanicSources(final ServerLevel level, final BlockPos center, final int radius) {
        return (int) volcanicSources(level, center, radius).limit(REQUIRED_VOLCANIC_SOURCES).count();
    }

    private static Optional<BlockPos> nearestVolcanicSource(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        return volcanicSources(level, center, radius)
            .min(Comparator.comparingDouble(pos -> pos.distSqr(center)));
    }

    private static java.util.stream.Stream<BlockPos> volcanicSources(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        final int searchRadius = Math.clamp(radius * 2, 8, 24);
        final int depth = Math.min(VOLCANIC_SEARCH_DEPTH, center.getY() - level.getMinY());
        if (depth <= 0) {
            return java.util.stream.Stream.empty();
        }
        return BlockPos.betweenClosedStream(
                center.offset(-searchRadius, -depth, -searchRadius),
                center.offset(searchRadius, -1, searchRadius)
            )
            .filter(level::isLoaded)
            .filter(pos -> {
                final FluidState state = level.getFluidState(pos);
                return state.isSource() && state.typeHolder().is(WarlockeryTags.Fluids.VOLCANIC_FLUIDS);
            })
            .map(BlockPos::immutable);
    }

    private static boolean skyWrath(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        setStorm(level, definition.duration());
        final Optional<LivingEntity> target = boundTargetWithin(level, center, definition.radius())
            .filter(LivingEntity::isAlive)
            .or(() -> level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(center).inflate(definition.radius()),
                    entity -> entity.isAlive() && entity != caster
                ).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(Vec3.atCenterOf(center)))));
        final Vec3 strike = target.map(LivingEntity::position).orElseGet(() -> {
            final BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
            return Vec3.atBottomCenterOf(surface);
        });
        final LightningBolt lightning = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (lightning == null) {
            return false;
        }
        if (caster instanceof ServerPlayer serverPlayer) {
            lightning.setCause(serverPlayer);
        }
        lightning.setDamage(5.0F + Math.max(0, definition.amplifier()) * 2.0F);
        lightning.snapTo(strike.x(), strike.y(), strike.z());
        return level.addFreshEntity(lightning);
    }

    private static boolean hellOnEarth(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final BlockPos destination = nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .map(WaystoneState::read)
            .flatMap(Optional::stream)
            .filter(location -> location.dimension().equals(level.dimension().identifier()))
            .map(WaystoneState.Location::position)
            .findFirst()
            .orElse(center);
        level.getChunkAt(destination);
        HellRiftData.get(level).open(level, destination, center, definition.radius(), definition.duration());
        final BlockState controlledFire = Blocks.SOUL_CAMPFIRE.defaultBlockState();
        RitualTerrainPlan.fireRing(destination, definition.radius(), definition.count()).forEach(column ->
            findControlledFirePosition(level, column, controlledFire)
                .ifPresent(pos -> level.setBlockAndUpdate(pos, controlledFire))
        );
        return true;
    }

    private static Optional<BlockPos> findControlledFirePosition(
        final ServerLevel level,
        final BlockPos column,
        final BlockState controlledFire
    ) {
        for (int offset = 3; offset >= -3; offset--) {
            final BlockPos pos = column.offset(0, offset, 0);
            if (level.getBlockState(pos.below()).is(WarlockeryTags.Blocks.CONTROLLED_FIRE_SUPPORTS)
                && level.getBlockEntity(pos) == null
                && level.getBlockState(pos).canBeReplaced()
                && controlledFire.canSurvive(level, pos)) {
                return Optional.of(pos.immutable());
            }
        }
        return Optional.empty();
    }

    private static boolean cookItems(final ServerLevel level, final BlockPos center, final int radius) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius)).stream()
            .filter(entity -> {
                final ItemStack stack = entity.getItem();
                return cookingResult(level, stack)
                    .map(result -> {
                        replaceItemEntity(level, entity, result, stack.getCount());
                        return true;
                    })
                    .orElse(false);
            })
            .count() > 0;
    }

    private static Optional<ItemStack> cookingResult(final ServerLevel level, final ItemStack stack) {
        final SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
        return cookingResult(level, RecipeType.SMELTING, input)
            .or(() -> cookingResult(level, RecipeType.SMOKING, input))
            .or(() -> cookingResult(level, RecipeType.CAMPFIRE_COOKING, input));
    }

    private static <T extends AbstractCookingRecipe> Optional<ItemStack> cookingResult(
        final ServerLevel level,
        final RecipeType<T> type,
        final SingleRecipeInput input
    ) {
        return level.recipeAccess().getRecipeFor(type, input, level)
            .map(recipe -> recipe.value().assemble(input))
            .filter(result -> !result.isEmpty());
    }

    private static void replaceItemEntity(
        final ServerLevel level,
        final ItemEntity entity,
        final ItemStack recipeResult,
        final int inputCount
    ) {
        int remaining = recipeResult.getCount() * inputCount;
        final int firstCount = Math.min(remaining, recipeResult.getMaxStackSize());
        entity.setItem(recipeResult.copyWithCount(firstCount));
        remaining -= firstCount;
        while (remaining > 0) {
            final int count = Math.min(remaining, recipeResult.getMaxStackSize());
            level.addFreshEntity(new ItemEntity(
                level,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                recipeResult.copyWithCount(count)
            ));
            remaining -= count;
        }
    }

    private static boolean eclipse(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        RitualEclipseData.get(level).begin(level, definition.duration());
        final RitualDefinition darkness = new RitualDefinition(
            "effect", "minecraft:darkness", definition.power(), definition.radius(), definition.duration(), 0,
            definition.glyphs(), definition.nightOnly(), definition.castingTime(), "", 1
        );
        applyEffect(level, center, darkness);
        return true;
    }

    private static boolean removeVampirism(final ServerLevel level, final BlockPos center, final int radius) {
        final List<Player> cured = targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE)
            .toList();
        cured.forEach(com.kadamitas.warlockery.transformation.SupernaturalProgression::cure);
        return !cured.isEmpty();
    }

    private static boolean removeWerewolf(final ServerLevel level, final BlockPos center, final int radius) {
        final List<Player> cured = targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF)
            .toList();
        cured.forEach(com.kadamitas.warlockery.transformation.SupernaturalProgression::cure);
        return !cured.isEmpty();
    }

    private static boolean transform(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final int radius,
        final SupernaturalForm form
    ) {
        final List<Player> changed = targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.NONE)
            .filter(player -> !protectedFromHex(player, caster))
            .toList();
        changed.forEach(player -> {
            if (form == SupernaturalForm.WEREWOLF) {
                com.kadamitas.warlockery.transformation.SupernaturalAdvancement.beginWerewolf(player);
            } else if (form == SupernaturalForm.VAMPIRE) {
                com.kadamitas.warlockery.transformation.SupernaturalAdvancement.beginVampire(player);
            }
        });
        return !changed.isEmpty();
    }

    private static boolean applyBlight(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        final boolean intercepted = level.getEntitiesOfClass(
                Player.class,
                new AABB(center).inflate(definition.radius()),
                Player::isAlive
            ).stream()
            .anyMatch(player -> protectedFromHex(player, caster));
        if (intercepted) {
            return false;
        }
        final BlightHex.BlightReport report =
            BlightHex.apply(level, center, definition.radius(), definition.duration());
        return report.vegetationDestroyed() > 0
            || report.soilsSpoiled() > 0
            || report.victimsAfflicted() > 0;
    }

    private static boolean applyHex(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        final Optional<HexBehavior> behavior = HexBehaviors.find(definition.target());
        if (behavior.isEmpty()) {
            Warlockery.LOGGER.error("Ritual hex target {} reaches no behavior", definition.target());
            return false;
        }
        final List<LivingEntity> affected = targetLiving(level, center, definition.radius()).stream()
            .filter(target -> !protectedFromHex(target, caster))
            .toList();
        affected.forEach(target -> behavior.orElseThrow().apply(target, definition.duration()));
        return !affected.isEmpty();
    }

    private static boolean protectedFromHex(
        final LivingEntity target,
        final @Nullable LivingEntity caster
    ) {
        if (FetishRuntime.protects(target)) {
            if (target instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("message.warlockery.fetish.hex_blocked"));
            }
            return true;
        }
        if (EquipmentSetEffects.tryBlockHex(target)) {
            if (target instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("message.warlockery.hunter_armor.hex_blocked"));
            }
            return true;
        }
        if (!DollItem.tryBlockHex(target, caster)) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.warlockery.doll.hex_blocked"));
        }
        return true;
    }

    private static boolean cleanse(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        final Optional<HexBehavior> behavior = HexBehaviors.find(definition.target());
        if (behavior.isEmpty()) {
            Warlockery.LOGGER.error("Ritual cleanse target {} reaches no behavior", definition.target());
            return false;
        }
        final List<LivingEntity> targets = targetLiving(level, center, definition.radius());
        targets.forEach(target -> behavior.orElseThrow().remove(target));
        return !targets.isEmpty();
    }

    private static boolean marry(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        final Optional<LivingEntity> candidate = marriageCandidate(level, center, player, definition.radius());
        if (candidate.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.warlockery.marriage.missing_partner"));
            return false;
        }
        final MarriageData marriages = MarriageData.get(level);
        final LivingEntity partner = candidate.orElseThrow();
        final MarriageData.MarriageResult result;
        if (partner instanceof ServerPlayer other) {
            result = marriages.marryPlayers(player.getUUID(), other.getUUID());
            if (result == MarriageData.MarriageResult.SUCCESS) {
                other.sendSystemMessage(Component.translatable("message.warlockery.marriage.bound", player.getDisplayName()));
                bindWeddingRings(level, center, player.getDisplayName(), other.getDisplayName());
            }
        } else if (partner instanceof NamiEntity nami) {
            result = marriages.marryNami(player.getUUID(), nami.getUUID());
            if (result == MarriageData.MarriageResult.SUCCESS) {
                final String spouseName = marriages.bond(player.getUUID()).orElseThrow().spouseName();
                nami.acceptMarriage(player, spouseName);
                bindWeddingRings(level, center, player.getDisplayName(), Component.literal(spouseName));
            }
        } else {
            result = MarriageData.MarriageResult.INVALID_PARTNER;
        }
        if (result == MarriageData.MarriageResult.SUCCESS) {
            player.sendSystemMessage(Component.translatable("message.warlockery.marriage.bound", partner.getDisplayName()));
        } else {
            player.sendSystemMessage(Component.translatable("message.warlockery.marriage.failed." + result.name().toLowerCase()));
        }
        return result == MarriageData.MarriageResult.SUCCESS;
    }

    private static boolean divorce(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        final Optional<MarriageData.Bond> removed = MarriageData.get(level).divorce(player.getUUID());
        if (removed.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.warlockery.divorce.not_married"));
            return false;
        }
        final MarriageData.Bond bond = removed.orElseThrow();
        if (bond.isNami()) {
            for (final ServerLevel serverLevel : level.getServer().getAllLevels()) {
                if (serverLevel.getEntity(bond.partnerUuid()) instanceof NamiEntity nami) {
                    nami.divorce();
                    break;
                }
            }
        } else {
            final ServerPlayer partner = level.getServer().getPlayerList().getPlayer(bond.partnerUuid());
            if (partner != null) {
                partner.sendSystemMessage(Component.translatable("message.warlockery.divorce.complete"));
            }
        }
        player.sendSystemMessage(Component.translatable("message.warlockery.divorce.complete"));
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(OFFERING_RADIUS), ItemEntity::isAlive).stream()
            .filter(entity -> MarriageRuntime.isWeddingRing(entity.getItem()))
            .findFirst()
            .ifPresent(entity -> entity.getItem().shrink(1));
        return true;
    }

    private static Optional<LivingEntity> marriageCandidate(
        final ServerLevel level,
        final BlockPos center,
        final Player caster,
        final int radius
    ) {
        final Optional<LivingEntity> bound = boundTarget(level, center)
            .filter(target -> target != caster)
            .filter(target -> target instanceof ServerPlayer || target instanceof NamiEntity);
        if (bound.isPresent()) {
            return bound;
        }
        final Optional<LivingEntity> player = level.getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(center).inflate(radius),
                target -> target != caster
            ).stream()
            .min(Comparator.comparingDouble(target -> target.distanceToSqr(Vec3.atCenterOf(center))))
            .map(LivingEntity.class::cast);
        return player.or(() -> MarriageRuntime.nearestUnmarriedNami(level, center, radius).map(LivingEntity.class::cast));
    }

    private static void bindWeddingRings(
        final ServerLevel level,
        final BlockPos center,
        final Component first,
        final Component second
    ) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(OFFERING_RADIUS), ItemEntity::isAlive).stream()
            .map(ItemEntity::getItem)
            .filter(MarriageRuntime::isWeddingRing)
            .findFirst()
            .ifPresent(stack -> stack.set(
                DataComponents.LORE,
                new ItemLore(List.of(Component.translatable("tooltip.warlockery.wedding_ring.bound", first, second)))
            ));
    }

    private static List<Player> targetPlayers(final ServerLevel level, final BlockPos center, final int radius) {
        final Optional<LivingEntity> bound = boundTarget(level, center);
        if (bound.filter(Player.class::isInstance).isPresent()) {
            return List.of((Player) bound.orElseThrow());
        }
        return level.getEntitiesOfClass(Player.class, new AABB(center).inflate(radius));
    }

    private static List<LivingEntity> targetLiving(final ServerLevel level, final BlockPos center, final int radius) {
        return boundTargetWithin(level, center, radius).<List<LivingEntity>>map(List::of)
            .orElseGet(() -> level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(radius)));
    }

    private static Optional<LivingEntity> boundTarget(final ServerLevel level, final BlockPos center) {
        return nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .map(SympatheticBinding::read)
            .flatMap(Optional::stream)
            .map(binding -> binding.resolve(level.getServer()))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private static Optional<LivingEntity> boundTargetWithin(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        return boundTarget(level, center)
            .filter(target -> target.level() == level)
            .filter(target -> target.distanceToSqr(Vec3.atCenterOf(center)) <= (double) radius * radius);
    }

    private static Optional<java.util.UUID> boundTargetId(final ServerLevel level, final BlockPos center) {
        return nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .map(SympatheticBinding::read)
            .flatMap(Optional::stream)
            .map(SympatheticBinding::targetId)
            .findFirst();
    }

    private static List<ItemEntity> nearbyItems(final ServerLevel level, final BlockPos center) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(OFFERING_RADIUS), ItemEntity::isAlive);
    }

    private static boolean bindWaystone(final ServerLevel level, final BlockPos center) {
        final Item boundItem = ModItems.ALL.get("ingredient_waystone_bound").get();
        return nearbyItems(level, center).stream()
            .filter(entity -> entity.getItem().is(ModItems.ALL.get("ingredient_waystone").get()))
            .findFirst()
            .map(entity -> {
                final ItemStack bound = entity.getItem().transmuteCopy(boundItem, entity.getItem().getCount());
                CustomData.update(DataComponents.CUSTOM_DATA, bound, data -> {
                    data.putString("WarlockeryDimension", level.dimension().identifier().toString());
                    data.putLong("WarlockeryWaystonePos", center.asLong());
                });
                entity.setItem(bound);
                return true;
            })
            .orElse(false);
    }

    private static boolean copyWaystone(final ServerLevel level, final BlockPos center) {
        final List<ItemEntity> items = nearbyItems(level, center);
        final Optional<ItemStack> source = items.stream().map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("ingredient_waystone_bound").get()))
            .filter(stack -> !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty())
            .findFirst();
        return source.flatMap(bound -> items.stream()
            .filter(entity -> entity.getItem().is(ModItems.ALL.get("ingredient_waystone").get()))
            .findFirst()
            .map(entity -> {
                entity.setItem(bound.copyWithCount(entity.getItem().getCount()));
                return true;
            })).orElse(false);
    }

    private static boolean teleportToWaystone(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        if (caster == null || FetishRuntime.protects(caster)) {
            return false;
        }
        return nearbyItems(level, center).stream().map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("ingredient_waystone_bound").get()))
            .map(stack -> stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())
            .filter(tag -> tag.contains("WarlockeryWaystonePos"))
            .findFirst()
            .map(tag -> {
                final BlockPos destination = BlockPos.of(tag.getLongOr("WarlockeryWaystonePos", center.asLong()));
                final Identifier dimension = Identifier.tryParse(tag.getStringOr("WarlockeryDimension", ""));
                if (dimension == null) {
                    return false;
                }
                if (dimension.equals(level.dimension().identifier())) {
                    caster.teleportTo(destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5);
                    return true;
                }
                if (!(caster instanceof ServerPlayer player) || !MagicPathRuntime.hasOtherwhere(player)) {
                    return false;
                }
                MagicPathRuntime.teleportToBoundPosition(player, dimension, destination);
                return true;
            })
            .orElse(false);
    }

    private static boolean teleportBoundEntity(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        return boundTargetWithin(level, center, definition.radius())
            .filter(target -> !FetishRuntime.protects(target))
            .map(target -> {
                target.teleportTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
                return true;
            })
            .orElse(false);
    }

    private static boolean transposeOres(final ServerLevel level, final BlockPos center, final int radius) {
        return BlockPos.betweenClosedStream(
                center.offset(-radius, -Math.min(32, radius * 3), -radius),
                center.offset(radius, -1, radius)
            )
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_ORES))
            .limit(48)
            .map(BlockPos::immutable)
            .toList()
            .stream()
            .filter(pos -> {
                final Block block = level.getBlockState(pos).getBlock();
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                return level.addFreshEntity(new ItemEntity(level, center.getX() + 0.5, center.getY() + 1.0,
                    center.getZ() + 0.5, new ItemStack(block.asItem())));
            })
            .count() > 0;
    }

    private static boolean iceSphere(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int participants
    ) {
        final int safeRadius = Math.clamp(radius + Math.max(0, participants - 3), 2, 8);
        final long shell = BlockPos.betweenClosedStream(
                center.offset(-safeRadius, -safeRadius, -safeRadius),
                center.offset(safeRadius, safeRadius, safeRadius)
            )
            .filter(pos -> {
                final double distance = Math.sqrt(pos.distSqr(center));
                return distance >= safeRadius - 0.75 && distance <= safeRadius + 0.25;
            })
            .filter(pos -> level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced())
            .limit(1_024)
            .map(BlockPos::immutable)
            .toList()
            .stream()
            .filter(pos -> level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState()))
            .count();
        BlockPos.betweenClosedStream(
                center.offset(-safeRadius + 1, -safeRadius + 1, -safeRadius + 1),
                center.offset(safeRadius - 1, safeRadius - 1, safeRadius - 1)
            )
            .filter(pos -> pos.distSqr(center) < (safeRadius - 0.75) * (safeRadius - 0.75))
            .filter(pos -> level.getBlockEntity(pos) == null)
            .filter(pos -> !level.getFluidState(pos).isEmpty())
            .limit(1_024)
            .map(BlockPos::immutable)
            .toList()
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
        return shell > 0;
    }

    private static boolean manifest(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        return boundTargetWithin(level, center, definition.radius())
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .map(dreamer -> ManifestationRuntime.manifest(
                level,
                center,
                dreamer,
                ManifestationRules.durationTicks(definition.duration(), nearbyParticipants(level, center, caster))
            ))
            .orElse(false);
    }

    private static boolean infusePath(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final List<Player> players = targetPlayers(level, center, definition.radius());
        MagicPathRuntime.infuse(players, MagicPath.require(definition.target()));
        return !players.isEmpty();
    }

    private static boolean rechargePaths(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        MagicPathRuntime.recharge(targetPlayers(level, center, definition.radius()), definition.power());
        RitualWardData.get(level).place(
            level,
            RitualWardType.RECHARGE,
            center,
            definition.radius(),
            level.getGameTime() + definition.duration(),
            true
        );
        return true;
    }

    private static boolean bindEntity(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        if (definition.target().equals(RitualBindTarget.SPECTRAL.id())) {
            return bindSpectralStone(level, center, definition.radius());
        }
        if (caster == null) {
            return false;
        }
        return bindingCandidate(level, center, definition.radius(), definition.target())
            .map(entity -> {
                CreatureBehaviorState.bind(entity, caster.getUUID());
                entity.setPersistenceRequired();
                entity.setTarget(null);
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, definition.duration(), 1));
                entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, definition.duration(), 0));
                return true;
            })
            .orElse(false);
    }

    private static boolean spectralBindingReady(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        final List<SpectralStoneState> stones = nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("spectralstone").get()))
            .map(SpectralStoneState::read)
            .toList();
        if (stones.isEmpty()) {
            return false;
        }
        final RitualCandidateIndex<Identifier, Mob> candidates = RitualCandidateIndex.create(
            spectralCandidates(level, center, radius),
            entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
        );
        return stones.stream().anyMatch(state -> candidates.anyKey(state::canCapture));
    }

    private static boolean bindSpectralStone(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        final Optional<ItemStack> stone = nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("spectralstone").get()))
            .findFirst();
        if (stone.isEmpty()) {
            return false;
        }
        final ItemStack stack = stone.orElseThrow();
        final SpectralStoneState initialState = SpectralStoneState.read(stack);
        final Optional<Map.Entry<Identifier, List<Mob>>> compatible = RitualCandidateIndex.create(
            spectralCandidates(level, center, radius),
            entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
        ).largestMatching(initialState::canCapture);
        if (compatible.isEmpty()) {
            return false;
        }
        final Map.Entry<Identifier, List<Mob>> selection = compatible.orElseThrow();
        SpectralStoneState state = initialState;
        final int count = Math.min(SpectralStoneState.CAPACITY - state.captured().size(), selection.getValue().size());
        for (int index = 0; index < count; index++) {
            state = state.with(selection.getKey());
            selection.getValue().get(index).discard();
        }
        state.write(stack);
        return count > 0;
    }

    private static boolean bindFetish(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final RitualCandidateIndex<String, Mob> candidates = RitualCandidateIndex.create(
            spectralCandidates(level, center, definition.radius()),
            RitualManager::spectralKind
        );
        final Optional<FetishBindingRules.BindingPlan> plan = FetishBindingRules.plan(candidates.counts());
        final Identifier id = Identifier.tryParse(definition.target());
        if (plan.isEmpty() || id == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.get(id).map(holder -> {
            final ItemStack output = new ItemStack(holder.value());
            FetishBindingState.write(output, plan.orElseThrow().mode());
            level.addFreshEntity(new ItemEntity(
                level,
                center.getX() + 0.5,
                center.getY() + 1.0,
                center.getZ() + 0.5,
                output
            ));
            plan.orElseThrow().requirements().forEach((kind, count) ->
                candidates.candidates(kind).stream().limit(count).forEach(Mob::discard)
            );
            return true;
        }).orElse(false);
    }

    private static Map<String, Integer> spectralCounts(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        return RitualCandidateIndex.create(
            spectralCandidates(level, center, radius),
            RitualManager::spectralKind
        ).counts();
    }

    private static List<Mob> spectralCandidates(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        return level.getEntitiesOfClass(
            Mob.class,
            new AABB(center).inflate(radius),
            entity -> entity.isAlive() && entity.typeHolder().is(WarlockeryTags.EntityTypes.SPECTRAL)
        );
    }

    private static String spectralKind(final Mob entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    private static Optional<Mob> bindingCandidate(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final String target
    ) {
        return RitualBindTarget.find(target).stream()
            .flatMap(bindTarget -> level
                .getEntitiesOfClass(Mob.class, new AABB(center).inflate(radius), Mob::isAlive).stream()
                .filter(bindTarget::matches))
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(Vec3.atCenterOf(center))));
    }

    private static boolean bindItem(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final Optional<SympatheticBinding> binding = nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .map(SympatheticBinding::read)
            .flatMap(Optional::stream)
            .findFirst();
        final Identifier id = Identifier.tryParse(definition.target());
        if (binding.isEmpty() || id == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.get(id).map(holder -> {
            final ItemStack output = new ItemStack(holder.value(), Math.clamp(definition.count(), 1, 64));
            binding.orElseThrow().write(output);
            return level.addFreshEntity(new ItemEntity(
                level,
                center.getX() + 0.5,
                center.getY() + 1.0,
                center.getZ() + 0.5,
                output
            ));
        }).orElse(false);
    }

    private static boolean placeWard(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition,
        final RitualWardType type
    ) {
        RitualWardData.get(level).place(
            level,
            type,
            center,
            definition.radius(),
            level.getGameTime() + definition.duration(),
            definition.power() > 0
        );
        return true;
    }

    private static boolean shiftClimate(
        final ServerLevel level,
        final BlockPos center,
        final int chunkRadius
    ) {
        return nearbyRecordedBiome(level, center).flatMap(id -> level.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .get(ResourceKey.create(Registries.BIOME, id)))
            .map(biome -> BiomeShiftRuntime.apply(level, center, biome, chunkRadius) > 0)
            .orElse(false);
    }

    static Optional<Identifier> nearbyRecordedBiome(final ServerLevel level, final BlockPos center) {
        return nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .filter(RitualManager::isBiomeRecord)
            .map(BiomeNoteState::read)
            .flatMap(Optional::stream)
            .filter(biome -> level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .get(ResourceKey.create(Registries.BIOME, biome))
                .isPresent())
            .findFirst();
    }

    static BiomeShiftPlan climateShiftPlan(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        return climateShiftInputs(level, center, caster).plan();
    }

    private static ClimateShiftInputs climateShiftInputs(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        final List<ItemStack> offerings = nearbyItems(level, center).stream().map(ItemEntity::getItem).toList();
        final int participants = nearbyParticipants(level, center, caster);
        final boolean seerStone = offerings.stream()
            .anyMatch(stack -> stack.is(ModItems.ALL.get("ingredient_seer_stone").get()));
        final int stars = offerings.stream()
            .filter(RitualManager::isNetherStar)
            .mapToInt(ItemStack::getCount)
            .sum();
        return new ClimateShiftInputs(
            BiomeShiftPlan.create(seerStone && participants >= CLIMATE_EMPOWERMENT_PARTICIPANTS, stars),
            seerStone,
            participants
        );
    }

    private static boolean isBiomeRecord(final ItemStack stack) {
        return stack.is(ModItems.ALL.get("biomenote").get())
            || stack.getItem() instanceof ManualItem manual && manual.recordsBiomes();
    }

    private static boolean isNetherStar(final ItemStack stack) {
        return stack.is(COMMON_NETHER_STARS) || stack.is(Items.NETHER_STAR);
    }

    static void consumeClimateNetherStars(
        final ServerLevel level,
        final BlockPos center,
        final int requested
    ) {
        int remaining = Math.clamp(requested, 0, BiomeShiftPlan.MAX_NETHER_STARS);
        for (final ItemEntity entity : nearbyItems(level, center)) {
            if (remaining == 0) {
                return;
            }
            final ItemStack stack = entity.getItem();
            if (!isNetherStar(stack)) {
                continue;
            }
            final int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
            if (stack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(stack);
            }
        }
    }

    private static boolean priorIncarnation(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        final Optional<java.util.UUID> target = boundTargetId(level, center)
            .or(() -> Optional.ofNullable(caster).map(Player::getUUID));
        final PriorIncarnationRuntime.RecoveryReport report = target
            .map(player -> PriorIncarnationRuntime.recover(level, center, player, 16))
            .orElse(PriorIncarnationRuntime.RecoveryReport.EMPTY);
        if (caster != null) {
            caster.sendSystemMessage(Component.translatable(
                report.recoveredAnything()
                    ? "message.warlockery.prior_incarnation.recovered"
                    : "message.warlockery.prior_incarnation.missing",
                report.items(),
                report.stacks()
            ));
        }
        return report.recoveredAnything();
    }

    private static boolean transformGlyphs(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition,
        final ChalkCircleLayout.Size size
    ) {
        final Identifier targetId = Identifier.tryParse(definition.target());
        return targetId != null && transformGlyphRing(level, center, size, targetId);
    }

    static boolean transformGlyphRing(
        final ServerLevel level,
        final BlockPos center,
        final ChalkCircleLayout.Size size,
        final Identifier targetId
    ) {
        final Optional<net.minecraft.core.Holder.Reference<Block>> target = BuiltInRegistries.BLOCK.get(targetId);
        if (target.isEmpty() || ChalkCircleLayout.uniformGlyph(level, center, size)
            .filter(TRANSFORMABLE_GLYPHS::contains)
            .filter(source -> !source.equals(targetId.getPath()))
            .isEmpty()) {
            return false;
        }
        size.offsets().stream()
            .map(center::offset)
            .forEach(position -> level.setBlockAndUpdate(position, target.orElseThrow().value().defaultBlockState()));
        return true;
    }

    private static ChalkCircleLayout.Size transformSize(
        final RitualDefinition definition,
        final int variant
    ) {
        if (variant >= 1 && variant <= ChalkCircleLayout.Size.values().length) {
            return ChalkCircleLayout.Size.forOfferingCount(variant);
        }
        return ChalkCircleLayout.rings(definition.glyphs()).getFirst().size();
    }

    private static Optional<String> transformRing(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition,
        final ChalkCircleLayout.Size size
    ) {
        final Identifier target = Identifier.tryParse(definition.target());
        if (target == null) {
            return Optional.empty();
        }
        return ChalkCircleLayout.uniformGlyph(level, center, size)
            .filter(TRANSFORMABLE_GLYPHS::contains)
            .filter(source -> !source.equals(target.getPath()));
    }

    private static int specificity(final RitualDefinition definition) {
        return ChalkCircleLayout.canonicalGlyphs(definition.glyphs()).values().stream()
            .mapToInt(Integer::intValue)
            .sum();
    }

    private static boolean validate(final Identifier id, final RitualDefinition definition) {
        final List<String> problems = problems(definition);
        problems.forEach(problem ->
            Warlockery.LOGGER.error("Skipping invalid Warlockery ritual {}: {}", id, problem)
        );
        return problems.isEmpty();
    }

    static List<String> problems(final RitualDefinition definition) {
        final List<String> problems = new ArrayList<>(RitualValidator.structuralErrors(definition));
        if (definition.glyphs().size() > ChalkCircleLayout.Size.values().length) {
            problems.add("glyphs: a ritual can use at most " + ChalkCircleLayout.Size.values().length + " chalk rings");
        }
        definition.glyphs().keySet().stream()
            .filter(glyph -> !BuiltInRegistries.BLOCK.containsKey(
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, glyph)))
            .forEach(glyph -> problems.add("glyphs: unknown glyph block " + glyph));
        final List<RitualDefinition.Ingredient> ingredients = definition.requirements().ingredients();
        for (int index = 0; index < ingredients.size(); index++) {
            final String ingredient = ingredients.get(index).ingredient();
            if (ItemIngredient.parse(ingredient).filter(ItemIngredient::isResolvable).isEmpty()) {
                problems.add("requirements.ingredients[" + index + "].ingredient: unresolvable " + ingredient);
            }
        }
        final List<RitualDefinition.EntityRequirement> entities = definition.requirements().entities();
        for (int index = 0; index < entities.size(); index++) {
            final String entity = entities.get(index).entity();
            if (EntityTypeIngredient.parse(entity).filter(EntityTypeIngredient::isResolvable).isEmpty()) {
                problems.add("requirements.entities[" + index + "].entity: unresolvable " + entity);
            }
        }
        RitualAction.find(definition.action())
            .flatMap(action -> targetProblem(action, definition))
            .ifPresent(problems::add);
        return List.copyOf(problems);
    }

    static Optional<String> targetProblem(
        final RitualAction action,
        final RitualDefinition definition
    ) {
        final String target = definition.target();
        return switch (action) {
            case EFFECT -> registryProblem(BuiltInRegistries.MOB_EFFECT, "effect", definition.effect());
            case SUMMON_ENTITY, SUMMON_HUNTSMAN, TOAD_RAIN ->
                registryProblem(BuiltInRegistries.ENTITY_TYPE, "target", target);
            case SUMMON_ITEM, BIND_CIRCLE, BIND_FETISH, BIND_ITEM ->
                registryProblem(BuiltInRegistries.ITEM, "target", target);
            case RAISE_COLUMN, GLYPH_TRANSFORM -> registryProblem(BuiltInRegistries.BLOCK, "target", target);
            case HEX, CLEANSE -> HexBehaviors.supports(target)
                ? Optional.empty()
                : Optional.of("target: no hex behavior is registered for " + target);
            case INFUSE_PATH -> MagicPath.find(target).isPresent()
                ? Optional.empty()
                : Optional.of("target: unknown magic path " + target);
            case BIND_ENTITY -> RitualBindTarget.find(target).isPresent()
                ? Optional.empty()
                : Optional.of("target: unknown bind target " + target);
            // Actions that resolve what they act on from the circle, the caster or their own constants, and so
            // read no target field. Listing them instead of falling through a default arm is what makes a new
            // action fail to compile here until somebody decides which of the two groups it belongs in; an
            // unchecked target is a rite that loads clean, charges the player and then does nothing.
            case STORM, FERTILITY, FORESTATION, NATURES_POWER, BLIGHT, BANISH, CALL_BEASTS, CALL_FAMILIAR,
                ANGUISH_UNDEAD, DRAIN_GROWTH, FORTIFY_UNDEAD, GRAVEYARD_MIST, BROKEN_EARTH, EARTHS_WRATH,
                SKYS_WRATH, HELL_ON_EARTH, COOK, ECLIPSE, REMOVE_VAMPIRISM, TRANSFORM_NAMI, TRANSFORM_WEREWOLF,
                REMOVE_WEREWOLF, BIND_WAYSTONE, COPY_WAYSTONE, TELEPORT_WAYSTONE, TELEPORT_ENTITY, TRANSPOSE_ORE,
                ICE_SPHERE, MANIFEST, IMPRISONMENT_WARD, PROTECTION_WARD, SANCTITY_WARD, CLIMATE_SHIFT,
                PRIOR_INCARNATION, RECHARGE_PATH, MARRIAGE, DIVORCE -> Optional.empty();
        };
    }

    private static <T> Optional<String> registryProblem(
        final net.minecraft.core.Registry<T> registry,
        final String field,
        final String value
    ) {
        final Identifier id = Identifier.tryParse(value);
        return id != null && registry.containsKey(id)
            ? Optional.empty()
            : Optional.of(field + ": unregistered " + registry.key().identifier() + " id " + value);
    }

    public List<Identifier> ids() {
        return List.copyOf(rituals.keySet());
    }

    public List<Entry> all() {
        return rituals.entrySet().stream()
            .map(entry -> new Entry(entry.getKey(), entry.getValue()))
            .toList();
    }

    public Optional<Entry> byId(final Identifier id) {
        return Optional.ofNullable(rituals.get(id)).map(definition -> new Entry(id, definition));
    }

    private record TransformInspection(
        String targetChalk,
        int matchingChalk,
        int foreignChalk,
        ChalkCircleLayout.Size size,
        Optional<String> sourceGlyph,
        int presentMarks
    ) {
        private boolean ringReady() {
            return sourceGlyph.isPresent() && presentMarks == size.markCount();
        }

        private boolean ready() {
            return !targetChalk.isBlank()
                && matchingChalk >= 1
                && matchingChalk <= 3
                && foreignChalk == 0
                && ringReady();
        }
    }

    private record TransformSelection(String targetChalk, int count, ChalkCircleLayout.Size size) {
    }

    private record SiteRequirement(RequirementStatus status, boolean survivesCasting) {
    }

    public record Entry(Identifier id, RitualDefinition definition) {
    }

    public record RitualOption(
        String id,
        String title,
        String description,
        int power,
        int altarPower,
        int castingTime,
        List<RequirementStatus> requirements,
        boolean ready
    ) {
        public RitualOption {
            requirements = List.copyOf(requirements);
        }
    }

    public record RequirementStatus(
        String category,
        String label,
        int required,
        int present,
        boolean met
    ) {
        public boolean blocksActivation() {
            return !"optional".equals(category);
        }
    }

    private record ClimateShiftInputs(BiomeShiftPlan plan, boolean seerStone, int participants) {
    }
}
