package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.compat.jei.JeiRecipeRefreshSignal;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.block.FetishRuntime;
import com.kadamitas.warlockery.block.FetishBindingRules;
import com.kadamitas.warlockery.block.VoidBrambleBlock;
import com.kadamitas.warlockery.block.FetishBindingState;
import com.kadamitas.warlockery.block.FetishMode;
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
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
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
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RitualManager extends SimpleJsonResourceReloadListener<RitualDefinition> {
    private static final int REQUIRED_VOLCANIC_SOURCES = 4;
    private static final int VOLCANIC_SEARCH_DEPTH = 48;
    private static final int CLIMATE_EMPOWERMENT_PARTICIPANTS = 5;
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

    public Optional<Identifier> activate(final ServerLevel level, final BlockPos center, final Player caster) {
        return options(level, center, caster).stream()
            .filter(RitualOption::ready)
            .map(option -> Identifier.tryParse(option.id()))
            .filter(java.util.Objects::nonNull)
            .filter(id -> activate(level, center, caster, id))
            .findFirst();
    }

    public boolean activate(
        final ServerLevel level,
        final BlockPos center,
        final Player caster,
        final Identifier ritualId
    ) {
        final RitualDefinition definition = rituals.get(ritualId);
        if (definition == null || !diagnose(ritualId, definition, level, center, caster).ready()) {
            return false;
        }
        final Optional<TransformSelection> transform = transformSelection(level, center, definition);
        final RitualAction action = RitualAction.require(definition.action());
        if (action == RitualAction.GLYPH_TRANSFORM && transform.isEmpty()) {
            return false;
        }
        final Optional<BiomeShiftPlan> climateShift = action == RitualAction.CLIMATE_SHIFT
            ? Optional.of(climateShiftPlan(level, center))
            : Optional.empty();
        final RitualSessionData sessions = RitualSessionData.get(level);
        if (sessions.isActive(center) || !consumeAltarPower(level, center, definition.power())) {
            return false;
        }
        if (transform.isPresent()) {
            consumeTransformChalk(level, center, transform.orElseThrow());
        } else {
            consumeIngredients(level, center, definition.requirements().ingredients());
        }
        climateShift.ifPresent(plan -> consumeClimateNetherStars(level, center, plan.netherStars()));
        consumeEntityRequirements(level, center, definition.requirements().entities());
        final int variant = transform.map(selection -> selection.size().ordinal() + 1)
            .orElseGet(() -> climateShift.map(BiomeShiftPlan::chunkRadius).orElse(0));
        if (!sessions.start(center, ritualId, caster.getUUID(), definition.castingTime(), variant)) {
            return false;
        }
        caster.sendSystemMessage(Component.translatable("message.warlockery.ritual.started"));
        return true;
    }

    public List<RitualOption> options(final ServerLevel level, final BlockPos center, final Player caster) {
        return rituals.entrySet().stream()
            .filter(entry -> entry.getValue().visible())
            .map(entry -> diagnose(entry.getKey(), entry.getValue(), level, center, caster))
            .sorted(Comparator.comparing(RitualOption::title).thenComparing(RitualOption::id))
            .toList();
    }

    boolean isSessionValid(final ServerLevel level, final BlockPos center, final Identifier ritualId) {
        return isSessionValid(level, center, ritualId, 0);
    }

    boolean isSessionValid(
        final ServerLevel level,
        final BlockPos center,
        final Identifier ritualId,
        final int variant
    ) {
        final RitualDefinition definition = rituals.get(ritualId);
        return definition != null
            && (definition.power() == 0 || hasValidAltar(level, center))
            && matchesStructureAndWorld(definition, level, center, variant);
    }

    void complete(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final Identifier ritualId
    ) {
        complete(level, center, caster, ritualId, 0);
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

    private static boolean matchesStructureAndWorld(
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center,
        final int variant
    ) {
        if (SpiritWorldRuntime.isSpiritWorld(level)) {
            return false;
        }
        if (!isCircleCenter(level, center)) {
            return false;
        }
        if (countRitualInhibitors(level, center, definition.radius()) > 0) {
            return false;
        }
        if (definition.nightOnly() && !level.isDarkOutside()) {
            return false;
        }
        final RitualDefinition.Requirements requirements = definition.requirements();
        if (requirements.dayOnly() && level.isDarkOutside()
            || requirements.fullMoon() && !isFullMoon(level, center)
            || requirements.raining() && !level.isRaining()
            || requirements.thundering() && !level.isThundering()
            || !requirements.dimension().isBlank()
                && !requirements.dimension().equals(level.dimension().identifier().toString())
            || nearbyParticipants(level, center) < requirements.minimumPlayers()) {
            return false;
        }
        final boolean chalkReady = RitualAction.require(definition.action()) == RitualAction.GLYPH_TRANSFORM
            ? transformRing(level, center, definition, transformSize(definition, variant)).isPresent()
            : ChalkCircleLayout.matches(level, center, definition.glyphs());
        return chalkReady
            && actionEnvironmentReady(definition, level, center);
    }

    private static boolean actionEnvironmentReady(
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center
    ) {
        return actionEnvironmentRequirement(definition, level, center)
            .map(RequirementStatus::met)
            .orElse(true);
    }

    private static Optional<RequirementStatus> actionEnvironmentRequirement(
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center
    ) {
        return actionEnvironmentRequirement(definition, level, center, null);
    }

    private static Optional<RequirementStatus> actionEnvironmentRequirement(
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
            final boolean present = unmarriedNami(level, center, definition.radius()).isPresent();
            return Optional.of(new RequirementStatus(
                "condition", "unmarried_nami", 1, present ? 1 : 0, present
            ));
        }
        if (action == RitualAction.CLEANSE) {
            final Optional<LivingEntity> target = boundTarget(level, center);
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
                return Optional.empty();
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
                return Optional.empty();
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
                return Optional.empty();
            }
            final boolean married = MarriageData.get(level).isMarried(caster.getUUID());
            return Optional.of(new RequirementStatus("condition", "existing_marriage", 1, married ? 1 : 0, married));
        }
        if ((action == RitualAction.TRANSFORM_WEREWOLF
            || action == RitualAction.HEX && definition.target().equals("corrupt_doll")) && caster != null) {
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
            final Optional<ServerPlayer> target = boundTarget(level, center)
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast);
            final boolean present = target.isPresent();
            final boolean ready = target.map(ManifestationRuntime::diagnose)
                .map(ManifestationRules.Decision::ready)
                .orElse(false);
            final String label = target.map(ManifestationRuntime::diagnose)
                .map(ManifestationRules.Decision::diagnostic)
                .map(ManifestationRules.Diagnostic::id)
                .orElse(ManifestationRules.Diagnostic.MISSING_BOUND_TARGET.id());
            return Optional.of(new RequirementStatus(
                "condition", label, 1, ready ? 1 : 0, present && ready
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
        final ArrayList<RequirementStatus> statuses = new ArrayList<>();
        statuses.add(condition("spirit_world_circle_magic", !SpiritWorldRuntime.isSpiritWorld(level, caster)));
        if (RitualAction.require(definition.action()) == RitualAction.GLYPH_TRANSFORM) {
            final TransformInspection inspection = inspectTransform(level, center, definition);
            statuses.add(new RequirementStatus(
                "chalk",
                inspection.sourceGlyph().orElse("chalk_ring"),
                inspection.size().markCount(),
                inspection.presentMarks(),
                inspection.ringReady()
            ));
            statuses.add(new RequirementStatus(
                "ingredient",
                inspection.targetChalk(),
                Math.clamp(inspection.matchingChalk(), 1, 3),
                inspection.matchingChalk(),
                inspection.matchingChalk() >= 1 && inspection.matchingChalk() <= 3
            ));
            statuses.add(condition("matching_transform_chalk", inspection.foreignChalk() == 0));
        } else {
            ChalkCircleLayout.rings(definition.glyphs()).forEach(ring -> {
                final int present = ChalkCircleLayout.present(level, center, ring);
                statuses.add(new RequirementStatus("chalk", ring.glyph(), ring.requiredCount(), present,
                    present == ring.requiredCount()));
            });
            statuses.addAll(inspectIngredients(level, center, definition.requirements().ingredients()));
        }
        statuses.addAll(inspectEntityRequirements(level, center, definition.requirements().entities()));
        final Optional<AltarBlockEntity> altar = findBestAltar(level, center);
        final boolean altarRequired = definition.power() > 0;
        statuses.add(new RequirementStatus(
            "altar", "structure", altarRequired ? 1 : 0, altar.isPresent() ? 1 : 0,
            !altarRequired || altar.isPresent()
        ));
        final int altarPower = altar.map(AltarBlockEntity::getPower).orElse(0);
        statuses.add(new RequirementStatus("power", "altar_power", definition.power(), altarPower,
            definition.power() == 0 || altarPower >= definition.power()));
        if (definition.nightOnly()) {
            statuses.add(condition("night", level.isDarkOutside()));
        }
        final RitualDefinition.Requirements requirements = definition.requirements();
        if (requirements.dayOnly()) {
            statuses.add(condition("day", !level.isDarkOutside()));
        }
        if (requirements.fullMoon()) {
            statuses.add(condition("full_moon", isFullMoon(level, center)));
        }
        if (requirements.raining()) {
            statuses.add(condition("rain", level.isRaining()));
        }
        if (requirements.thundering()) {
            statuses.add(condition("thunder", level.isThundering()));
        }
        if (!requirements.dimension().isBlank()) {
            statuses.add(new RequirementStatus(
                "condition", requirements.dimension(), 1,
                requirements.dimension().equals(level.dimension().identifier().toString()) ? 1 : 0,
                requirements.dimension().equals(level.dimension().identifier().toString())
            ));
        }
        if (requirements.minimumPlayers() > 1) {
            final int present = nearbyParticipants(level, center);
            statuses.add(new RequirementStatus(
                "coven", "coven", requirements.minimumPlayers(), present, present >= requirements.minimumPlayers()
            ));
        }
        actionEnvironmentRequirement(definition, level, center, caster).ifPresent(statuses::add);
        if (RitualAction.require(definition.action()) == RitualAction.CLIMATE_SHIFT) {
            final ClimateShiftInputs inputs = climateShiftInputs(level, center);
            final BiomeShiftPlan plan = inputs.plan();
            statuses.add(new RequirementStatus(
                "optional", "climate_seer_stone", 1, inputs.seerStone() ? 1 : 0, inputs.seerStone()
            ));
            statuses.add(new RequirementStatus(
                "optional", "climate_participants", CLIMATE_EMPOWERMENT_PARTICIPANTS,
                inputs.participants(), inputs.participants() >= CLIMATE_EMPOWERMENT_PARTICIPANTS
            ));
            statuses.add(new RequirementStatus(
                "optional", "climate_nether_stars", BiomeShiftPlan.MAX_NETHER_STARS,
                plan.netherStars(), plan.netherStars() > 0
            ));
        }
        final int inhibitors = countRitualInhibitors(level, center, definition.radius());
        statuses.add(new RequirementStatus(
            "condition",
            "ritual_inhibitors",
            0,
            inhibitors,
            inhibitors == 0
        ));
        statuses.add(new RequirementStatus("center", "circle_center", 1, isCircleCenter(level, center) ? 1 : 0,
            isCircleCenter(level, center)));
        final boolean inactive = !RitualSessionData.get(level).isActive(center);
        statuses.add(new RequirementStatus("session", "inactive", 1, inactive ? 1 : 0, inactive));
        final List<RequirementStatus> immutable = List.copyOf(statuses);
        final String title = definition.title().isBlank() ? "ritual.warlockery." + id.getPath() + ".title" : definition.title();
        final String description = definition.description().isBlank()
            ? "ritual.warlockery." + id.getPath() + ".description"
            : definition.description();
        return new RitualOption(
            id.toString(), title, description, definition.power(), altarPower, definition.castingTime(),
            immutable, immutable.stream().filter(RequirementStatus::blocksActivation).allMatch(RequirementStatus::met)
        );
    }

    private static RequirementStatus condition(final String label, final boolean met) {
        return new RequirementStatus("condition", label, 1, met ? 1 : 0, met);
    }

    private static List<RequirementStatus> inspectIngredients(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.Ingredient> requirements
    ) {
        final List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6.0), Entity -> Entity.isAlive());
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
        final List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6.0), Entity -> Entity.isAlive());
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
            new AABB(center).inflate(6.0),
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
            new AABB(center).inflate(6.0),
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

    private static List<RequirementStatus> inspectEntityRequirements(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.EntityRequirement> requirements
    ) {
        final List<Mob> entities = level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(6.0), Mob::isAlive);
        final var reserved = new HashSet<java.util.UUID>();
        final List<RequirementStatus> statuses = new ArrayList<>();
        for (final RitualDefinition.EntityRequirement requirement : requirements) {
            final Optional<EntityTypeIngredient> ingredient = EntityTypeIngredient.parse(requirement.entity());
            final List<Mob> matched = ingredient.stream()
                .flatMap(value -> entities.stream()
                    .filter(entity -> !reserved.contains(entity.getUUID()))
                    .filter(value::matches)
                    .limit(requirement.count()))
                .toList();
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

    private static void consumeEntityRequirements(
        final ServerLevel level,
        final BlockPos center,
        final List<RitualDefinition.EntityRequirement> requirements
    ) {
        final List<Mob> entities = level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(6.0), Mob::isAlive);
        final var consumed = new HashSet<java.util.UUID>();
        requirements.stream().filter(RitualDefinition.EntityRequirement::consume).forEach(requirement ->
            EntityTypeIngredient.parse(requirement.entity()).ifPresent(ingredient -> entities.stream()
                .filter(entity -> !consumed.contains(entity.getUUID()))
                .filter(ingredient::matches)
                .limit(requirement.count())
                .forEach(entity -> {
                    consumed.add(entity.getUUID());
                    entity.discard();
                }))
        );
    }

    private static boolean isFullMoon(final ServerLevel level, final BlockPos center) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, Vec3.atCenterOf(center)) == MoonPhase.FULL_MOON
            && level.isDarkOutside();
    }

    static int nearbyParticipants(final ServerLevel level, final BlockPos center) {
        return SeerCovenRuntime.countParticipants(level, center, SeerCovenRuntime.PARTICIPANT_RADIUS);
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

    static int ritualInhibitorCount(final int fixedInhibitors, final boolean voidBrambleSuppression) {
        return ritualInhibitorCount(fixedInhibitors, voidBrambleSuppression, false);
    }

    public static boolean isCircleCenter(final ServerLevel level, final BlockPos center) {
        return level.getBlockState(center).is(ModBlocks.ALL.get("circle").get());
    }

    private static boolean consumeAltarPower(final ServerLevel level, final BlockPos center, final int power) {
        if (power == 0) {
            return true;
        }
        return findBestAltar(level, center).filter(altar -> altar.consumePower(power)).isPresent();
    }

    private static boolean hasValidAltar(final ServerLevel level, final BlockPos center) {
        return findBestAltar(level, center).isPresent();
    }

    private static Optional<AltarBlockEntity> findBestAltar(final ServerLevel level, final BlockPos center) {
        final int range = 12;
        return BlockPos.betweenClosedStream(center.offset(-range, -4, -range), center.offset(range, 6, range))
            .map(level::getBlockEntity)
            .filter(AltarBlockEntity.class::isInstance)
            .map(AltarBlockEntity.class::cast)
            .filter(AltarBlockEntity::isMultiblockValid)
            .max(Comparator.comparingInt(AltarBlockEntity::getPower));
    }

    private static void perform(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition,
        final int variant
    ) {
        switch (RitualAction.require(definition.action())) {
            case EFFECT -> applyEffect(level, center, definition);
            case STORM -> setStorm(level, definition.duration());
            case CLEAR_WEATHER -> clearWeather(level, definition.duration());
            case FERTILITY -> fertility(level, center, caster, definition);
            case FORESTATION -> forestation(level, center, definition);
            case NATURES_POWER -> restoreNature(level, center, definition.radius());
            case BLIGHT -> applyBlight(level, center, caster, definition);
            case TOAD_RAIN -> {
                startRain(level, definition.duration());
                ToadRainHex.apply(
                    level,
                    center,
                    definition.radius(),
                    definition.count(),
                    definition.duration()
                );
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
            case CRATER -> createCrater(level, center, definition.radius());
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
            case TELEPORT_ENTITY -> teleportBoundEntity(level, center);
            case TRANSPOSE_ORE -> transposeOres(level, center, definition.radius());
            case ICE_SPHERE -> iceSphere(level, center, definition.radius(), nearbyParticipants(level, center));
            case MANIFEST -> manifest(level, center, definition);
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
        }
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
        if (caster != null) {
            caster.sendSystemMessage(Component.translatable("message.warlockery.ritual.success"));
        }
    }

    private static void applyEffect(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(definition.effect())).ifPresent(effect -> {
            final AABB area = new AABB(center).inflate(definition.radius());
            level.getEntitiesOfClass(LivingEntity.class, area).forEach(entity ->
                entity.addEffect(new MobEffectInstance(effect, definition.duration(), definition.amplifier()))
            );
        });
    }

    private static void setStorm(final ServerLevel level, final int duration) {
        final var weather = level.getWeatherData();
        weather.setRaining(true);
        weather.setThundering(true);
        weather.setRainTime(duration);
        weather.setThunderTime(duration);
    }

    private static void startRain(final ServerLevel level, final int duration) {
        final var weather = level.getWeatherData();
        weather.setRaining(true);
        weather.setThundering(false);
        weather.setRainTime(Math.max(200, duration));
        weather.setThunderTime(0);
    }

    private static void clearWeather(final ServerLevel level, final int duration) {
        final var weather = level.getWeatherData();
        weather.setRaining(false);
        weather.setThundering(false);
        weather.setRainTime(duration);
        weather.setThunderTime(duration);
    }

    private static void fertility(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        growTaggedPlants(level, center, definition.radius(), 1);
        final AABB area = new AABB(center).inflate(definition.radius());
        level.getEntitiesOfClass(Player.class, area).forEach(player -> {
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.NAUSEA);
            player.removeEffect(MobEffects.BLINDNESS);
        });
        level.getEntitiesOfClass(ZombieVillager.class, area).forEach(zombie -> cureZombieVillager(level, zombie));
        if (caster != null && hasFertilityFamiliar(level, caster, definition.radius())) {
            level.getEntitiesOfClass(Player.class, area).forEach(player -> {
                player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    Math.max(200, definition.duration() / 2),
                    Math.max(0, definition.amplifier())
                ));
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 0));
            });
        }
    }

    private static void cureZombieVillager(final ServerLevel level, final ZombieVillager zombie) {
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

    private static void growTaggedPlants(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int passes
    ) {
        BlockPos.betweenClosedStream(
                center.offset(-radius, -2, -radius),
                center.offset(radius, 3, radius)
            )
            .filter(pos -> pos.distSqr(center) <= radius * radius)
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_GROWABLES))
            .limit(2_048)
            .forEach(pos -> forceGrow(level, pos, passes));
    }

    private static void forceGrow(final ServerLevel level, final BlockPos pos, final int passes) {
        for (int pass = 0; pass < passes; pass++) {
            final BlockState state = level.getBlockState(pos);
            if (!state.is(WarlockeryTags.Blocks.RITUAL_GROWABLES)
                || !(state.getBlock() instanceof BonemealableBlock growable)
                || !growable.isValidBonemealTarget(level, pos, state)) {
                return;
            }
            growable.performBonemeal(level, level.getRandom(), pos, state);
        }
    }

    private static void restoreNature(final ServerLevel level, final BlockPos center, final int radius) {
        BlockPos.betweenClosedStream(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))
            .filter(pos -> pos.distSqr(center) <= radius * radius)
            .limit(4_096)
            .forEach(pos -> {
                final BlockState state = level.getBlockState(pos);
                if (state.is(WarlockeryTags.Blocks.NATURE_REPAIRABLE_SOILS)
                    && level.getBlockEntity(pos) == null
                    && level.getBlockState(pos.above()).canBeReplaced()) {
                    level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                } else if (state.is(WarlockeryTags.Blocks.NATURE_DAMAGED_VEGETATION)) {
                    final BlockState restored = Blocks.SHORT_GRASS.defaultBlockState();
                    if (restored.canSurvive(level, pos)) {
                        level.setBlockAndUpdate(pos, restored);
                    }
                }
            });
        growTaggedPlants(level, center, radius, 2);
    }

    private static void forestation(
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
        BlockPos.betweenClosedStream(
                origin.offset(-definition.radius(), -2, -definition.radius()),
                origin.offset(definition.radius(), 3, definition.radius())
            )
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_SAPLINGS))
            .map(BlockPos::immutable)
            .limit(128)
            .toList()
            .forEach(pos -> forceGrow(level, pos, 3));

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

    private static void banish(final ServerLevel level, final BlockPos center, final int radius) {
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(radius),
            entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.DEMONS)
        )
            .forEach(LivingEntity::discard);
    }

    private static void callBeasts(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        level.getEntitiesOfClass(
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
            .forEach(mob -> mob.getNavigation().moveTo(
                center.getX() + 0.5,
                center.getY() + 0.5,
                center.getZ() + 0.5,
                1.25
            ));
    }

    private static void anguishUndead(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(definition.radius()))
            .forEach(entity -> entity.addEffect(new MobEffectInstance(
                MobEffects.STRENGTH,
                definition.duration(),
                Math.max(0, definition.amplifier())
            )));
    }

    private static void drainGrowth(
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
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(radius),
            entity -> entity.typeHolder().is(EntityTypeTags.UNDEAD)
        ).forEach(entity -> entity.heal(Math.min(20.0F, drained * 0.5F)));
    }

    private static void fortifyUndead(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(definition.radius()))
            .forEach(entity -> entity.addEffect(new MobEffectInstance(
                MobEffects.RESISTANCE,
                definition.duration(),
                Math.max(0, definition.amplifier())
            )));
    }

    private static void graveyardMist(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(definition.radius())).forEach(entity -> {
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
    }

    private static void summonEntities(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        final Identifier id = Identifier.tryParse(definition.target());
        if (id == null) {
            return;
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
                return;
            }
        }
        BuiltInRegistries.ENTITY_TYPE.get(id).ifPresent(holder -> java.util.stream.IntStream
            .range(0, Math.clamp(definition.count(), 1, 16))
            .mapToObj(index -> holder.value().create(level, EntitySpawnReason.COMMAND))
            .filter(java.util.Objects::nonNull)
            .forEach(entity -> {
                final double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                final double distance = 1.5 + level.getRandom().nextDouble() * Math.max(1, definition.radius() - 1);
                entity.snapTo(center.getX() + 0.5 + Math.cos(angle) * distance, center.getY() + 1.0, center.getZ() + 0.5 + Math.sin(angle) * distance);
                level.addFreshEntity(entity);
            }));
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
            WarlockeryEntityData.get(naamah).putString(
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

    private static void callFamiliar(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        if (caster == null || FetishRuntime.protects(caster)) {
            return;
        }
        ownedFamiliar(level, caster).ifPresent(familiar -> familiar.teleport(new net.minecraft.world.level.portal.TeleportTransition(
            level,
            Vec3.atCenterOf(center).add(0.0, 1.0, 0.0),
            Vec3.ZERO,
            familiar.getYRot(),
            familiar.getXRot(),
            net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING
        )));
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

    private static void summonHuntsman(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        if (HuntsmanSummoningStructure.consume(level, center)) {
            summonEntities(level, center, caster, definition);
        }
    }

    private static void summonItem(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        final Identifier id = Identifier.tryParse(definition.target());
        if (id == null) {
            return;
        }
        BuiltInRegistries.ITEM.get(id).ifPresent(holder -> level.addFreshEntity(new ItemEntity(
            level,
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            new ItemStack(holder.value(), Math.clamp(definition.count(), 1, 64))
        )));
    }

    private static void raiseColumn(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        final Identifier id = Identifier.tryParse(definition.target());
        if (id == null) {
            return;
        }
        BuiltInRegistries.BLOCK.get(id).ifPresent(holder -> {
            final int radius = Math.clamp(definition.radius() / 3, 1, 4);
            final int height = Math.clamp(definition.count(), 1, 16);
            BlockPos.betweenClosedStream(
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
                .forEach(pos -> level.setBlockAndUpdate(pos, holder.value().defaultBlockState()));
        });
    }

    private static void createFissure(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final int radius
    ) {
        final Direction direction = caster == null ? Direction.NORTH : caster.getDirection();
        RitualTerrainPlan.fissure(center, direction, radius).stream()
            .filter(level::isLoaded)
            .filter(pos -> level.getBlockEntity(pos) == null)
            .filter(pos -> {
                final BlockState state = level.getBlockState(pos);
                return state.is(WarlockeryTags.Blocks.FISSURE_BREAKABLES)
                    && state.getDestroySpeed(level, pos) >= 0.0F;
            })
            .limit(768)
            .forEach(pos -> level.destroyBlock(pos, false));
    }

    private static void raiseVolcano(final ServerLevel level, final BlockPos center, final int radius) {
        nearestVolcanicSource(level, center, radius).ifPresent(source -> {
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
            }
        });
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

    private static void skyWrath(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        setStorm(level, definition.duration());
        final Optional<LivingEntity> target = boundTarget(level, center)
            .filter(entity -> entity.level() == level && entity.isAlive())
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
        if (lightning != null) {
            if (caster instanceof ServerPlayer serverPlayer) {
                lightning.setCause(serverPlayer);
            }
            lightning.snapTo(strike.x(), strike.y(), strike.z());
            level.addFreshEntity(lightning);
        }
    }

    private static void hellOnEarth(
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

    private static void createCrater(final ServerLevel level, final BlockPos center, final int radius) {
        final int safeRadius = Math.clamp(radius, 1, 8);
        BlockPos.betweenClosedStream(center.offset(-safeRadius, -safeRadius, -safeRadius), center.offset(safeRadius, safeRadius, safeRadius))
            .filter(pos -> pos.distSqr(center) <= safeRadius * safeRadius)
            .filter(pos -> level.getBlockEntity(pos) == null)
            .filter(pos -> {
                final var state = level.getBlockState(pos);
                return !state.isAir() && state.getBlock() != Blocks.BEDROCK && state.getDestroySpeed(level, pos) >= 0.0F;
            })
            .limit(2_048)
            .forEach(pos -> level.destroyBlock(pos, false));
    }

    private static void cookItems(final ServerLevel level, final BlockPos center, final int radius) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius)).forEach(entity -> {
            final ItemStack stack = entity.getItem();
            cookingResult(level, stack).ifPresent(result -> replaceItemEntity(level, entity, result, stack.getCount()));
        });
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

    private static void eclipse(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        RitualEclipseData.get(level).begin(level, definition.duration());
        final RitualDefinition darkness = new RitualDefinition(
            "effect", "minecraft:darkness", definition.power(), definition.radius(), definition.duration(), 0,
            definition.glyphs(), definition.nightOnly(), definition.castingTime(), "", 1
        );
        applyEffect(level, center, darkness);
    }

    private static void removeVampirism(final ServerLevel level, final BlockPos center, final int radius) {
        targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE)
            .forEach(com.kadamitas.warlockery.transformation.SupernaturalProgression::cure);
    }

    private static void removeWerewolf(final ServerLevel level, final BlockPos center, final int radius) {
        targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF)
            .forEach(com.kadamitas.warlockery.transformation.SupernaturalProgression::cure);
    }

    private static void transform(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final int radius,
        final SupernaturalForm form
    ) {
        targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.NONE)
            .filter(player -> !protectedFromHex(player, caster))
            .forEach(player -> {
                if (form == SupernaturalForm.WEREWOLF) {
                    com.kadamitas.warlockery.transformation.SupernaturalAdvancement.beginWerewolf(player);
                } else if (form == SupernaturalForm.VAMPIRE) {
                    com.kadamitas.warlockery.transformation.SupernaturalAdvancement.beginVampire(player);
                }
            });
    }

    private static void applyBlight(
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
        if (!intercepted) {
            BlightHex.apply(level, center, definition.radius(), definition.duration());
        }
    }

    private static void applyHex(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        targetLiving(level, center, definition.radius()).forEach(target -> {
            if (protectedFromHex(target, caster)) {
                return;
            }
            HexBehaviors.forTarget(definition.target()).apply(target, definition.duration());
        });
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

    private static void cleanse(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        targetLiving(level, center, definition.radius())
            .forEach(target -> HexBehaviors.forTarget(definition.target()).remove(target));
    }

    private static void marry(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        final Optional<LivingEntity> candidate = marriageCandidate(level, center, player, definition.radius());
        if (candidate.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.warlockery.marriage.missing_partner"));
            return;
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
    }

    private static void divorce(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        final Optional<MarriageData.Bond> removed = MarriageData.get(level).divorce(player.getUUID());
        if (removed.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.warlockery.divorce.not_married"));
            return;
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
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6.0), ItemEntity::isAlive).stream()
            .filter(entity -> MarriageRuntime.isWeddingRing(entity.getItem()))
            .findFirst()
            .ifPresent(entity -> entity.getItem().shrink(1));
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
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6.0), ItemEntity::isAlive).stream()
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
        return boundTarget(level, center).map(List::of)
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

    private static Optional<java.util.UUID> boundTargetId(final ServerLevel level, final BlockPos center) {
        return nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .map(SympatheticBinding::read)
            .flatMap(Optional::stream)
            .map(SympatheticBinding::targetId)
            .findFirst();
    }

    private static List<ItemEntity> nearbyItems(final ServerLevel level, final BlockPos center) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6.0), ItemEntity::isAlive);
    }

    private static void bindWaystone(final ServerLevel level, final BlockPos center) {
        final Item boundItem = ModItems.ALL.get("ingredient_waystone_bound").get();
        nearbyItems(level, center).stream()
            .filter(entity -> entity.getItem().is(ModItems.ALL.get("ingredient_waystone").get()))
            .findFirst()
            .ifPresent(entity -> {
                final ItemStack bound = entity.getItem().transmuteCopy(boundItem, entity.getItem().getCount());
                CustomData.update(DataComponents.CUSTOM_DATA, bound, data -> {
                    data.putString("WarlockeryDimension", level.dimension().identifier().toString());
                    data.putLong("WarlockeryWaystonePos", center.asLong());
                });
                entity.setItem(bound);
            });
    }

    private static void copyWaystone(final ServerLevel level, final BlockPos center) {
        final List<ItemEntity> items = nearbyItems(level, center);
        final Optional<ItemStack> source = items.stream().map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("ingredient_waystone_bound").get()))
            .filter(stack -> !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty())
            .findFirst();
        source.ifPresent(bound -> items.stream()
            .filter(entity -> entity.getItem().is(ModItems.ALL.get("ingredient_waystone").get()))
            .findFirst()
            .ifPresent(entity -> entity.setItem(bound.copyWithCount(entity.getItem().getCount()))));
    }

    private static void teleportToWaystone(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        if (caster == null || FetishRuntime.protects(caster)) {
            return;
        }
        nearbyItems(level, center).stream().map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("ingredient_waystone_bound").get()))
            .map(stack -> stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())
            .filter(tag -> tag.contains("WarlockeryWaystonePos"))
            .findFirst()
            .ifPresent(tag -> {
                final BlockPos destination = BlockPos.of(tag.getLongOr("WarlockeryWaystonePos", center.asLong()));
                final Identifier dimension = Identifier.tryParse(tag.getStringOr("WarlockeryDimension", ""));
                if (dimension == null) {
                    return;
                }
                if (dimension.equals(level.dimension().identifier())) {
                    caster.teleportTo(destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5);
                } else if (caster instanceof ServerPlayer player && MagicPathRuntime.hasOtherwhere(player)) {
                    MagicPathRuntime.teleportToBoundPosition(player, dimension, destination);
                }
            });
    }

    private static void teleportBoundEntity(final ServerLevel level, final BlockPos center) {
        boundTarget(level, center).filter(target -> !FetishRuntime.protects(target)).ifPresent(target ->
            target.teleportTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5));
    }

    private static void transposeOres(final ServerLevel level, final BlockPos center, final int radius) {
        BlockPos.betweenClosedStream(center.offset(-radius, -Math.min(32, radius * 3), -radius), center.offset(radius, -1, radius))
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_ORES))
            .limit(48)
            .forEach(pos -> {
                final Block block = level.getBlockState(pos).getBlock();
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                level.addFreshEntity(new ItemEntity(level, center.getX() + 0.5, center.getY() + 1.0,
                    center.getZ() + 0.5, new ItemStack(block.asItem())));
            });
    }

    private static void iceSphere(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int participants
    ) {
        final int safeRadius = Math.clamp(radius + Math.max(0, participants - 3), 2, 8);
        BlockPos.betweenClosedStream(center.offset(-safeRadius, -safeRadius, -safeRadius), center.offset(safeRadius, safeRadius, safeRadius))
            .filter(pos -> {
                final double distance = Math.sqrt(pos.distSqr(center));
                return distance >= safeRadius - 0.75 && distance <= safeRadius + 0.25;
            })
            .filter(pos -> level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced())
            .limit(1_024)
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState()));
        BlockPos.betweenClosedStream(
                center.offset(-safeRadius + 1, -safeRadius + 1, -safeRadius + 1),
                center.offset(safeRadius - 1, safeRadius - 1, safeRadius - 1)
            )
            .filter(pos -> pos.distSqr(center) < (safeRadius - 0.75) * (safeRadius - 0.75))
            .filter(pos -> level.getBlockEntity(pos) == null)
            .filter(pos -> !level.getFluidState(pos).isEmpty())
            .limit(1_024)
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
    }

    private static void manifest(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        boundTarget(level, center)
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .ifPresent(dreamer -> ManifestationRuntime.manifest(
                level,
                center,
                dreamer,
                ManifestationRules.durationTicks(definition.duration(), nearbyParticipants(level, center))
            ));
    }

    private static void infusePath(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        MagicPathRuntime.infuse(targetPlayers(level, center, definition.radius()), MagicPath.require(definition.target()));
    }

    private static void rechargePaths(
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
    }

    private static void bindEntity(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        if (definition.target().equals("spectral")) {
            bindSpectralStone(level, center, definition.radius());
            return;
        }
        if (caster == null) {
            return;
        }
        bindingCandidate(level, center, definition.radius(), definition.target()).ifPresent(entity -> {
            CreatureBehaviorState.bind(entity, caster.getUUID());
            entity.setPersistenceRequired();
            entity.setTarget(null);
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, definition.duration(), 1));
            entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, definition.duration(), 0));
        });
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

    private static void bindSpectralStone(
        final ServerLevel level,
        final BlockPos center,
        final int radius
    ) {
        final Optional<ItemStack> stone = nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("spectralstone").get()))
            .findFirst();
        if (stone.isEmpty()) {
            return;
        }
        final ItemStack stack = stone.orElseThrow();
        final SpectralStoneState initialState = SpectralStoneState.read(stack);
        final Optional<Map.Entry<Identifier, List<Mob>>> compatible = RitualCandidateIndex.create(
            spectralCandidates(level, center, radius),
            entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
        ).largestMatching(initialState::canCapture);
        if (compatible.isEmpty()) {
            return;
        }
        final Map.Entry<Identifier, List<Mob>> selection = compatible.orElseThrow();
        SpectralStoneState state = initialState;
        final int count = Math.min(SpectralStoneState.CAPACITY - state.captured().size(), selection.getValue().size());
        for (int index = 0; index < count; index++) {
            state = state.with(selection.getKey());
            selection.getValue().get(index).discard();
        }
        state.write(stack);
    }

    private static void bindFetish(
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
            return;
        }
        BuiltInRegistries.ITEM.get(id).ifPresent(holder -> {
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
        });
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
        return level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(radius), Mob::isAlive).stream()
            .filter(entity -> switch (target) {
                case "familiar" -> entity.typeHolder().is(CreatureBehaviorTags.EntityTypes.FAMILIARS);
                case "spectral" -> entity.typeHolder().is(WarlockeryTags.EntityTypes.SPECTRAL);
                default -> false;
            })
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(Vec3.atCenterOf(center))));
    }

    private static void bindItem(
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
            return;
        }
        BuiltInRegistries.ITEM.get(id).ifPresent(holder -> {
            final ItemStack output = new ItemStack(holder.value(), Math.clamp(definition.count(), 1, 64));
            binding.orElseThrow().write(output);
            level.addFreshEntity(new ItemEntity(
                level,
                center.getX() + 0.5,
                center.getY() + 1.0,
                center.getZ() + 0.5,
                output
            ));
        });
    }

    private static void placeWard(
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
    }

    private static void shiftClimate(
        final ServerLevel level,
        final BlockPos center,
        final int chunkRadius
    ) {
        nearbyRecordedBiome(level, center).flatMap(id -> level.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .get(ResourceKey.create(Registries.BIOME, id)))
            .ifPresent(biome -> BiomeShiftRuntime.apply(level, center, biome, chunkRadius));
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

    static BiomeShiftPlan climateShiftPlan(final ServerLevel level, final BlockPos center) {
        return climateShiftInputs(level, center).plan();
    }

    private static ClimateShiftInputs climateShiftInputs(final ServerLevel level, final BlockPos center) {
        final List<ItemStack> offerings = nearbyItems(level, center).stream().map(ItemEntity::getItem).toList();
        final int participants = nearbyParticipants(level, center);
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
        return stack.is(ConventionalItemTags.NETHER_STARS) || stack.is(Items.NETHER_STAR);
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

    private static void priorIncarnation(
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
    }

    private static void transformGlyphs(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition,
        final ChalkCircleLayout.Size size
    ) {
        final Identifier targetId = Identifier.tryParse(definition.target());
        if (targetId != null) {
            transformGlyphRing(level, center, size, targetId);
        }
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
        final boolean basic = RitualValidator.isStructurallyValid(definition)
            && definition.glyphs().size() <= ChalkCircleLayout.Size.values().length
            && definition.glyphs().entrySet().stream().allMatch(entry ->
                BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, entry.getKey())))
            && definition.requirements().ingredients().stream().allMatch(ingredient ->
                validIngredient(ingredient.ingredient()))
            && definition.requirements().entities().stream().allMatch(requirement ->
                validEntityIngredient(requirement.entity()));
        final boolean actionTarget = switch (RitualAction.require(definition.action())) {
            case EFFECT -> validRegistryId(BuiltInRegistries.MOB_EFFECT, definition.effect());
            case SUMMON_ENTITY, SUMMON_HUNTSMAN -> validRegistryId(BuiltInRegistries.ENTITY_TYPE, definition.target());
            case SUMMON_ITEM -> validRegistryId(BuiltInRegistries.ITEM, definition.target());
            case RAISE_COLUMN, GLYPH_TRANSFORM -> validRegistryId(BuiltInRegistries.BLOCK, definition.target());
            default -> true;
        };
        if (!basic || !actionTarget) {
            Warlockery.LOGGER.error("Skipping invalid Warlockery ritual {}", id);
            return false;
        }
        return true;
    }

    private static boolean validIngredient(final String ingredient) {
        return ItemIngredient.parse(ingredient).filter(ItemIngredient::isResolvable).isPresent();
    }

    private static boolean validEntityIngredient(final String ingredient) {
        return EntityTypeIngredient.parse(ingredient).filter(EntityTypeIngredient::isResolvable).isPresent();
    }

    private static <T> boolean validRegistryId(final net.minecraft.core.Registry<T> registry, final String value) {
        final Identifier id = Identifier.tryParse(value);
        return id != null && registry.containsKey(id);
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
