package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.block.FetishRuntime;
import com.kadamitas.warlockery.block.FetishBindingRules;
import com.kadamitas.warlockery.block.FetishBindingState;
import com.kadamitas.warlockery.block.FetishMode;
import com.kadamitas.warlockery.block.StatueBlock;
import com.kadamitas.warlockery.item.CircleTalismanItem;
import com.kadamitas.warlockery.item.BiomeNoteState;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.entity.CreatureBehaviorTags;
import com.kadamitas.warlockery.entity.DeathImpersonationRules;
import com.kadamitas.warlockery.entity.FamiliarRecallRules;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.hex.BlightHex;
import com.kadamitas.warlockery.ritual.hex.ToadRainHex;
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
import net.minecraft.server.commands.FillBiomeCommand;
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
import net.minecraft.world.item.component.CustomData;
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
import net.minecraftforge.event.ForgeEventFactory;
import org.jspecify.annotations.Nullable;

public final class RitualManager extends SimpleJsonResourceReloadListener<RitualDefinition> {
    private static final int REQUIRED_VOLCANIC_SOURCES = 4;
    private static final int VOLCANIC_SEARCH_DEPTH = 48;
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
        final RitualSessionData sessions = RitualSessionData.get(level);
        if (sessions.isActive(center) || !consumeAltarPower(level, center, definition.power())) {
            return false;
        }
        consumeIngredients(level, center, definition.requirements().ingredients());
        consumeEntityRequirements(level, center, definition.requirements().entities());
        if (!sessions.start(center, ritualId, caster.getUUID(), definition.castingTime())) {
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
        final RitualDefinition definition = rituals.get(ritualId);
        return definition != null
            && hasValidAltar(level, center)
            && matchesStructureAndWorld(definition, level, center, countGlyphs(level, center, 6));
    }

    void complete(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final Identifier ritualId
    ) {
        final RitualDefinition definition = rituals.get(ritualId);
        if (definition != null) {
            perform(level, center, caster, definition);
        }
    }

    private static boolean matchesStructureAndWorld(
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center,
        final Map<String, Long> nearbyGlyphs
    ) {
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
            || nearbyPlayers(level, center) < requirements.minimumPlayers()) {
            return false;
        }
        return definition.glyphs().entrySet().stream()
            .allMatch(required -> nearbyGlyphs.getOrDefault(required.getKey(), 0L) >= required.getValue())
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
            final boolean present = !spectralCandidates(level, center, definition.radius()).isEmpty();
            return Optional.of(new RequirementStatus(
                "condition", "nearby_spectral", 1, present ? 1 : 0, present
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
        if (action != RitualAction.BIND_ENTITY) {
            return Optional.empty();
        }
        final boolean present = bindingCandidate(level, center, definition.radius(), definition.target()).isPresent();
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
                "condition", "recorded_biome_note", 1, present ? 1 : 0, present
            ));
        }
        if (action == RitualAction.PRIOR_INCARNATION) {
            final int present = boundTargetId(level, center)
                .map(player -> PriorIncarnationRuntime.countRecoverable(level, player))
                .orElse(0);
            return Optional.of(new RequirementStatus(
                "condition", "recoverable_death_drops", 1, present, present > 0
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
        return Optional.empty();
    }

    private RitualOption diagnose(
        final Identifier id,
        final RitualDefinition definition,
        final ServerLevel level,
        final BlockPos center,
        final Player caster
    ) {
        final Map<String, Long> glyphs = countGlyphs(level, center, 6);
        final ArrayList<RequirementStatus> statuses = new ArrayList<>();
        definition.glyphs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(required -> {
            final int present = Math.toIntExact(Math.min(Integer.MAX_VALUE, glyphs.getOrDefault(required.getKey(), 0L)));
            statuses.add(new RequirementStatus("chalk", required.getKey(), required.getValue(), present,
                present >= required.getValue()));
        });

        statuses.addAll(inspectIngredients(level, center, definition.requirements().ingredients()));
        statuses.addAll(inspectEntityRequirements(level, center, definition.requirements().entities()));
        final Optional<AltarBlockEntity> altar = findBestAltar(level, center);
        statuses.add(new RequirementStatus("altar", "structure", 1, altar.isPresent() ? 1 : 0, altar.isPresent()));
        final int altarPower = altar.map(AltarBlockEntity::getPower).orElse(0);
        statuses.add(new RequirementStatus("power", "altar_power", definition.power(), altarPower,
            altarPower >= definition.power()));
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
            final int present = nearbyPlayers(level, center);
            statuses.add(new RequirementStatus(
                "coven", "coven", requirements.minimumPlayers(), present, present >= requirements.minimumPlayers()
            ));
        }
        actionEnvironmentRequirement(definition, level, center, caster).ifPresent(statuses::add);
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
            immutable, immutable.stream().allMatch(RequirementStatus::met)
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

    private static int nearbyPlayers(final ServerLevel level, final BlockPos center) {
        return level.getEntitiesOfClass(Player.class, new AABB(center).inflate(8.0), Player::isAlive).size();
    }

    private static int countRitualInhibitors(
        final ServerLevel level,
        final BlockPos center,
        final int ritualRadius
    ) {
        final int radius = Math.clamp(ritualRadius + 4, 6, 16);
        return Math.toIntExact(BlockPos.betweenClosedStream(
            center.offset(-radius, -radius, -radius),
            center.offset(radius, radius, radius)
        ).filter(pos -> {
            final BlockState state = level.getBlockState(pos);
            if (!state.is(WarlockeryTags.Blocks.RITUAL_INHIBITORS)) {
                return false;
            }
            return !(state.getBlock() instanceof StatueBlock statue) || statue.occludes(state);
        }).count());
    }

    public static boolean isCircleCenter(final ServerLevel level, final BlockPos center) {
        final Identifier id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(center).getBlock());
        return id != null && Warlockery.MOD_ID.equals(id.getNamespace())
            && ("circle".equals(id.getPath()) || id.getPath().startsWith("circleglyph"));
    }

    private static Map<String, Long> countGlyphs(final ServerLevel level, final BlockPos center, final int radius) {
        return BlockPos.betweenClosedStream(
                center.offset(-radius, -1, -radius),
                center.offset(radius, 1, radius)
            )
            .map(level::getBlockState)
            .map(state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()))
            .filter(java.util.Objects::nonNull)
            .filter(id -> Warlockery.MOD_ID.equals(id.getNamespace()))
            .map(Identifier::getPath)
            .filter(path -> path.startsWith("circleglyph") || "circle".equals(path))
            .collect(Collectors.groupingBy(path -> path, Collectors.counting()));
    }

    private static boolean consumeAltarPower(final ServerLevel level, final BlockPos center, final int power) {
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
        final RitualDefinition definition
    ) {
        switch (RitualAction.require(definition.action())) {
            case EFFECT -> applyEffect(level, center, definition);
            case STORM -> setStorm(level, definition.duration());
            case CLEAR_WEATHER -> clearWeather(level, definition.duration());
            case FERTILITY -> fertility(level, center, caster, definition);
            case FORESTATION -> forestation(level, center, definition);
            case NATURES_POWER -> restoreNature(level, center, definition.radius());
            case BLIGHT -> BlightHex.apply(level, center, definition.radius(), definition.duration());
            case TOAD_RAIN -> ToadRainHex.apply(
                level,
                center,
                definition.radius(),
                definition.count(),
                definition.duration()
            );
            case BANISH -> banish(level, center, definition.radius());
            case CALL_BEASTS -> callBeasts(level, center, definition);
            case CALL_FAMILIAR -> callFamiliar(level, center, caster);
            case ANGUISH_UNDEAD -> anguishUndead(level, center, definition);
            case DRAIN_GROWTH -> drainGrowth(level, center, caster, definition.radius());
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
            case TRANSFORM_WEREWOLF -> transform(level, center, definition.radius(), SupernaturalForm.WEREWOLF);
            case REMOVE_WEREWOLF -> removeWerewolf(level, center, definition.radius());
            case HEX -> applyHex(level, center, caster, definition);
            case CLEANSE -> cleanse(level, center, definition);
            case BIND_CIRCLE -> CircleTalismanItem.captureFromRitual(level, center);
            case BIND_WAYSTONE -> bindWaystone(level, center);
            case COPY_WAYSTONE -> copyWaystone(level, center);
            case TELEPORT_WAYSTONE -> teleportToWaystone(level, center, caster);
            case TELEPORT_ENTITY -> teleportBoundEntity(level, center);
            case TRANSPOSE_ORE -> transposeOres(level, center, definition.radius());
            case ICE_SPHERE -> iceSphere(level, center, definition.radius());
            case MANIFEST -> manifest(level, center, definition);
            case IMPRISONMENT_WARD -> placeWard(level, center, definition, RitualWardType.IMPRISONMENT);
            case PROTECTION_WARD -> placeWard(level, center, definition, RitualWardType.PROTECTION);
            case SANCTITY_WARD -> placeWard(level, center, definition, RitualWardType.SANCTITY);
            case CLIMATE_SHIFT -> shiftClimate(level, center, definition);
            case PRIOR_INCARNATION -> priorIncarnation(level, center, caster, definition);
            case INFUSE_PATH -> infusePath(level, center, definition);
            case RECHARGE_PATH -> rechargePaths(level, center, definition);
            case BIND_ENTITY -> bindEntity(level, center, caster, definition);
            case BIND_FETISH -> bindFetish(level, center, definition);
            case BIND_ITEM -> bindItem(level, center, definition);
            case GLYPH_TRANSFORM -> transformGlyphs(level, center, definition);
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
        BlockPos.betweenClosedStream(
                center.offset(-definition.radius(), -2, -definition.radius()),
                center.offset(definition.radius(), 3, definition.radius())
            )
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.RITUAL_SAPLINGS))
            .map(BlockPos::immutable)
            .limit(128)
            .toList()
            .forEach(pos -> forceGrow(level, pos, 3));

        int placed = 0;
        for (BlockPos column : RitualTerrainPlan.forestColumns(center, definition.radius())) {
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
            .limit(Math.clamp(definition.count(), 1, 32))
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
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(definition.radius()),
            entity -> entity.typeHolder().is(EntityTypeTags.UNDEAD)
        ).forEach(entity -> {
            entity.hurtServer(level, level.damageSources().magic(), 6.0F + definition.amplifier() * 2.0F);
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, definition.duration(), Math.max(1, definition.amplifier())));
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, definition.duration(), Math.max(1, definition.amplifier())));
        });
    }

    private static void drainGrowth(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
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
        if (caster != null && drained > 0) {
            caster.heal(Math.min(20.0F, drained * 0.5F));
            caster.getFoodData().eat(Math.min(20, (int) (drained / 2)), Math.min(1.0F, drained / 20.0F));
        }
    }

    private static void fortifyUndead(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(definition.radius()),
            entity -> entity.typeHolder().is(EntityTypeTags.UNDEAD)
        ).forEach(entity -> {
            entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, definition.duration(), definition.amplifier() + 1));
            entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, definition.duration(), definition.amplifier()));
            entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, definition.duration(), definition.amplifier()));
        });
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

    private static void callFamiliar(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster
    ) {
        if (caster == null) {
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
        BuiltInRegistries.BLOCK.get(id).ifPresent(holder -> java.util.stream.IntStream
            .rangeClosed(0, Math.clamp(definition.count(), 1, 16))
            .mapToObj(center::above)
            .filter(pos -> level.getBlockState(pos).canBeReplaced())
            .forEach(pos -> level.setBlockAndUpdate(pos, holder.value().defaultBlockState())));
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
            lightning.setDamage(5.0F + Math.max(0, definition.amplifier()) * 2.0F);
            lightning.snapTo(strike.x(), strike.y(), strike.z());
            level.addFreshEntity(lightning);
        }
    }

    private static void hellOnEarth(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        java.util.stream.IntStream.range(0, Math.clamp(definition.count(), 1, 32)).forEach(index ->
            BuiltInRegistries.ENTITY_TYPE
                .getRandomElementOf(WarlockeryTags.EntityTypes.DEMONS, level.getRandom())
                .map(holder -> holder.value().create(level, EntitySpawnReason.TRIGGERED))
                .filter(java.util.Objects::nonNull)
                .ifPresent(entity -> {
                    final double angle = Math.PI * 2.0 * index / Math.max(1, definition.count());
                    final double distance = Math.max(2.0, definition.radius() * 0.6);
                    final int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
                    final int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
                    final int y = Math.max(
                        center.getY() + 1,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                    );
                    entity.snapTo(x + 0.5, y, z + 0.5);
                    level.addFreshEntity(entity);
                })
        );
        final BlockState controlledFire = Blocks.SOUL_CAMPFIRE.defaultBlockState();
        RitualTerrainPlan.fireRing(center, definition.radius(), definition.count()).forEach(column ->
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
            .forEach(player -> SupernaturalState.setForm(player, SupernaturalForm.NONE));
    }

    private static void removeWerewolf(final ServerLevel level, final BlockPos center, final int radius) {
        targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF)
            .forEach(player -> SupernaturalState.setForm(player, SupernaturalForm.NONE));
    }

    private static void transform(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final SupernaturalForm form
    ) {
        targetPlayers(level, center, radius).stream()
            .filter(player -> SupernaturalState.getForm(player) == SupernaturalForm.NONE)
            .forEach(player -> SupernaturalState.setForm(player, form));
    }

    private static void applyHex(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
        targetLiving(level, center, definition.radius()).forEach(target -> {
            if (FetishRuntime.protects(target)) {
                if (target instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.translatable("message.warlockery.fetish.hex_blocked"));
                }
                return;
            }
            if (EquipmentSetEffects.tryBlockHex(target)) {
                if (target instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.translatable("message.warlockery.hunter_armor.hex_blocked"));
                }
                return;
            }
            if (DollItem.tryBlockHex(target, caster)) {
                if (target instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.translatable("message.warlockery.doll.hex_blocked"));
                }
                return;
            }
            HexBehaviors.forTarget(definition.target()).apply(target, definition.duration());
        });
    }

    private static void cleanse(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        targetLiving(level, center, definition.radius())
            .forEach(target -> HexBehaviors.forTarget(definition.target()).remove(target));
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
        if (caster == null) {
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
        boundTarget(level, center).ifPresent(target ->
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

    private static void iceSphere(final ServerLevel level, final BlockPos center, final int radius) {
        final int safeRadius = Math.clamp(radius, 2, 8);
        BlockPos.betweenClosedStream(center.offset(-safeRadius, -safeRadius, -safeRadius), center.offset(safeRadius, safeRadius, safeRadius))
            .filter(pos -> {
                final double distance = Math.sqrt(pos.distSqr(center));
                return distance >= safeRadius - 0.75 && distance <= safeRadius + 0.25;
            })
            .filter(pos -> level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced())
            .limit(1_024)
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState()));
    }

    private static void manifest(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        boundTarget(level, center)
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .ifPresent(dreamer -> ManifestationRuntime.manifest(
                level,
                center,
                dreamer,
                definition.duration()
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
    }

    private static void bindEntity(
        final ServerLevel level,
        final BlockPos center,
        final @Nullable Player caster,
        final RitualDefinition definition
    ) {
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

    private static void bindFetish(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        final Map<String, Mob> candidates = spectralCandidates(level, center, definition.radius()).stream()
            .collect(Collectors.toMap(
                RitualManager::spectralKind,
                entity -> entity,
                (first, _) -> first
            ));
        final Optional<FetishMode> mode = FetishBindingRules.select(candidates.keySet());
        final Identifier id = Identifier.tryParse(definition.target());
        if (mode.isEmpty() || id == null) {
            return;
        }
        BuiltInRegistries.ITEM.get(id).ifPresent(holder -> {
            final ItemStack output = new ItemStack(holder.value());
            FetishBindingState.write(output, mode.orElseThrow());
            level.addFreshEntity(new ItemEntity(
                level,
                center.getX() + 0.5,
                center.getY() + 1.0,
                center.getZ() + 0.5,
                output
            ));
            candidates.values().forEach(Mob::discard);
        });
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
            level.getGameTime() + definition.duration()
        );
    }

    private static void shiftClimate(
        final ServerLevel level,
        final BlockPos center,
        final RitualDefinition definition
    ) {
        nearbyRecordedBiome(level, center).flatMap(id -> level.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .get(ResourceKey.create(Registries.BIOME, id)))
            .ifPresent(biome -> FillBiomeCommand.fill(
                level,
                center.offset(-definition.radius(), -definition.radius(), -definition.radius()),
                center.offset(definition.radius(), definition.radius(), definition.radius()),
                biome
            ));
    }

    private static Optional<Identifier> nearbyRecordedBiome(final ServerLevel level, final BlockPos center) {
        return nearbyItems(level, center).stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack.is(ModItems.ALL.get("biomenote").get()))
            .map(BiomeNoteState::read)
            .flatMap(Optional::stream)
            .findFirst();
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
            .map(player -> PriorIncarnationRuntime.recover(level, center, player))
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

    private static void transformGlyphs(final ServerLevel level, final BlockPos center, final RitualDefinition definition) {
        final Identifier targetId = Identifier.tryParse(definition.target());
        if (targetId == null) {
            return;
        }
        BuiltInRegistries.BLOCK.get(targetId).ifPresent(target -> BlockPos.betweenClosedStream(
                center.offset(-6, -1, -6), center.offset(6, 1, 6)
            )
            .filter(pos -> !pos.equals(center))
            .filter(pos -> {
                final Identifier existing = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                return Warlockery.MOD_ID.equals(existing.getNamespace())
                    && (existing.getPath().startsWith("circleglyph") || "circle".equals(existing.getPath()));
            })
            .limit(64)
            .forEach(pos -> level.setBlockAndUpdate(pos, target.value().defaultBlockState())));
    }

    private static int specificity(final RitualDefinition definition) {
        return definition.glyphs().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static boolean validate(final Identifier id, final RitualDefinition definition) {
        final boolean basic = RitualValidator.isStructurallyValid(definition)
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
    }
}
