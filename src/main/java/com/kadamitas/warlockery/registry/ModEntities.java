package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.AbyssalRegentRules;
import com.kadamitas.warlockery.entity.EldritchWatcherEntity;
import com.kadamitas.warlockery.entity.EntEntity;
import com.kadamitas.warlockery.entity.BansheeEntity;
import com.kadamitas.warlockery.entity.BroomEntity;
import com.kadamitas.warlockery.entity.HexBatEntity;
import com.kadamitas.warlockery.entity.HexBatRules;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.entity.ImpEntity;
import com.kadamitas.warlockery.entity.GoblinBossRules;
import com.kadamitas.warlockery.entity.LycanVillagerEntity;
import com.kadamitas.warlockery.entity.LycanVillagerRules;
import com.kadamitas.warlockery.entity.NamiEntity;
import com.kadamitas.warlockery.entity.NaamahEntity;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.ArcaneMob;
import com.kadamitas.warlockery.entity.HellhoundEntity;
import com.kadamitas.warlockery.entity.CorpseEntity;
import com.kadamitas.warlockery.entity.InfernalHierarchyEntity;
import com.kadamitas.warlockery.entity.SpiritMob;
import com.kadamitas.warlockery.entity.StormSimianEntity;
import com.kadamitas.warlockery.entity.VampireCourtEntity;
import com.kadamitas.warlockery.entity.FeralLycanEntity;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.entity.WingedArcaneMob;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final String BROOM_ID = "broom";
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Warlockery.MOD_ID);
    private static final Map<String, RegistryObject<? extends EntityType<?>>> MUTABLE = new LinkedHashMap<>();

    private static final Map<String, CreatureKind> ARCANE_KINDS = Map.ofEntries(
        Map.entry("hex_bat", CreatureKind.HEX_BAT), Map.entry("hedge_crone", CreatureKind.HEDGE_CRONE),
        Map.entry("banshee", CreatureKind.BANSHEE), Map.entry("familiar_cat", CreatureKind.CAT),
        Map.entry("corpse", CreatureKind.CORPSE), Map.entry("circle_mage", CreatureKind.CIRCLE_MAGE),
        Map.entry("umbral_sigil", CreatureKind.UMBRAL_SIGIL), Map.entry("death", CreatureKind.DEATH),
        Map.entry("pale_steed", CreatureKind.PALE_STEED), Map.entry("demon", CreatureKind.DEMON),
        Map.entry("eldritch_watcher", CreatureKind.ELDRITCH_WATCHER), Map.entry("spectral_familiar", CreatureKind.FAMILIAR),
        Map.entry("blood_thrall", CreatureKind.BLOOD_THRALL), Map.entry("hellhound", CreatureKind.HELLHOUND),
        Map.entry("thorned_pursuer", CreatureKind.THORNED_PURSUER),
        Map.entry("illusion_creeper", CreatureKind.ILLUSION_CREEPER),
        Map.entry("illusion_spider", CreatureKind.ILLUSION_SPIDER),
        Map.entry("illusion_zombie", CreatureKind.ILLUSION_ZOMBIE), Map.entry("imp", CreatureKind.IMP),
        Map.entry("emberhorn_archfiend", CreatureKind.EMBERHORN_ARCHFIEND), Map.entry("naamah", CreatureKind.NAAMAH),
        Map.entry("abyssal_regent", CreatureKind.ABYSSAL_REGENT), Map.entry("lost_soul", CreatureKind.LOST_SOUL),
        Map.entry("parasytic_louse", CreatureKind.LOUSE), Map.entry("mandrake", CreatureKind.MANDRAKE),
        Map.entry("dreamroot", CreatureKind.DREAMROOT), Map.entry("glass_doppelganger", CreatureKind.GLASS_DOPPELGANGER),
        Map.entry("nightmare", CreatureKind.NIGHTMARE), Map.entry("owl", CreatureKind.OWL),
        Map.entry("poltergeist", CreatureKind.POLTERGEIST), Map.entry("echo_shade", CreatureKind.ECHO_SHADE),
        Map.entry("spectre", CreatureKind.SPECTRE), Map.entry("spirit", CreatureKind.SPIRIT),
        Map.entry("toad", CreatureKind.TOAD), Map.entry("bramble_colossus", CreatureKind.BRAMBLE_COLOSSUS),
        Map.entry("vampire", CreatureKind.VAMPIRE), Map.entry("ironbound_sentinel", CreatureKind.IRONBOUND_SENTINEL),
        Map.entry("lycan_villager", CreatureKind.LYCAN_VILLAGER), Map.entry("storm_simian", CreatureKind.STORM_SIMIAN),
        Map.entry("feral_lycan", CreatureKind.WEREWOLF)
    );
    private static final Map<String, CreatureKind> SPECIAL_KINDS = Map.of(
        "ent", CreatureKind.ENT,
        "werewolf", CreatureKind.WEREWOLF,
        "werewolf_hunter", CreatureKind.WEREWOLF_HUNTER,
        "hobgoblin", CreatureKind.HOBGOBLIN,
        "goblin", CreatureKind.GOBLIN,
        "stonebroker", CreatureKind.STONEBROKER,
        "forgewarden", CreatureKind.FORGEWARDEN
    );
    private static final Set<CreatureKind> PASSIVE_GROUND_KINDS = Set.of(
        CreatureKind.OWL,
        CreatureKind.TOAD,
        CreatureKind.CAT
    );
    private static final Set<CreatureKind> FIRE_IMMUNE_GROUND_KINDS = Set.of(
        CreatureKind.DEMON,
        CreatureKind.HELLHOUND,
        CreatureKind.EMBERHORN_ARCHFIEND,
        CreatureKind.ABYSSAL_REGENT
    );
    private static final Map<CreatureKind, ContentFactory<EntityRegistration, EntityType<?>>> SPECIAL_ARCANE_FACTORIES = Map.ofEntries(
        Map.entry(CreatureKind.ELDRITCH_WATCHER, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createEldritchWatcher),
        Map.entry(CreatureKind.WEREWOLF, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createWerewolf),
        Map.entry(CreatureKind.IMP, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createImp),
        Map.entry(CreatureKind.STORM_SIMIAN, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createStormSimian),
        Map.entry(CreatureKind.LYCAN_VILLAGER, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createLycanVillager),
        Map.entry(CreatureKind.NAAMAH, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createNaamah),
        Map.entry(CreatureKind.VAMPIRE, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createVampireCourt),
        Map.entry(CreatureKind.BLOOD_THRALL, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createVampireCourt),
        Map.entry(CreatureKind.CORPSE, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createCorpse),
        Map.entry(CreatureKind.DEMON, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createInfernal),
        Map.entry(CreatureKind.EMBERHORN_ARCHFIEND, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createInfernal),
        Map.entry(CreatureKind.ABYSSAL_REGENT, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createInfernal),
        Map.entry(CreatureKind.HELLHOUND, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createHellhound),
        Map.entry(CreatureKind.HEX_BAT, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createHexBat),
        Map.entry(CreatureKind.BANSHEE, (ContentFactory<EntityRegistration, EntityType<?>>) ModEntities::createBanshee)
    );
    public static final Set<String> SPIRIT_IDS = ARCANE_KINDS.entrySet().stream()
        .filter(entry -> isSpiritKind(entry.getValue()))
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> WINGED_ATTRIBUTE_IDS = Set.of("imp", "storm_simian");
    private static final Set<String> VILLAGER_ATTRIBUTE_IDS = Set.of("hobgoblin", "nami");
    private static final Set<String> NATURAL_SPAWN_IDS = Set.of(
        "ent",
        "goblin",
        "hobgoblin",
        "spirit",
        "hellhound",
        "mandrake",
        "dreamroot"
    );
    private static final Set<String> PASSIVE_SPAWN_IDS = Set.of("ent", "goblin", "hobgoblin", "spirit");
    private static final List<AttributeFactoryRule> ATTRIBUTE_FACTORY_RULES = List.of(
        AttributeFactoryRule.exact("corpse", _ -> CorpseEntity.createAttributes().build()),
        AttributeFactoryRule.exact("ent", _ -> IronGolem.createAttributes().build()),
        AttributeFactoryRule.exact("forgewarden", _ -> patronAttributes(CreatureKind.FORGEWARDEN)),
        AttributeFactoryRule.exact("stonebroker", _ -> patronAttributes(CreatureKind.STONEBROKER)),
        AttributeFactoryRule.exact("goblin", _ -> Villager.createAttributes()
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 24.0)
            .build()),
        AttributeFactoryRule.exact("lycan_villager", _ -> Villager.createAttributes()
            .add(Attributes.MAX_HEALTH, LycanVillagerRules.MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, LycanVillagerRules.MOVEMENT_SPEED)
            .add(Attributes.FOLLOW_RANGE, LycanVillagerRules.FOLLOW_RANGE)
            .add(Attributes.ATTACK_DAMAGE, LycanVillagerRules.ATTACK_DAMAGE)
            .build()),
        new AttributeFactoryRule(VILLAGER_ATTRIBUTE_IDS::contains, _ -> Villager.createAttributes().build()),
        AttributeFactoryRule.exact("werewolf_hunter", _ -> Pillager.createAttributes().build()),
        new AttributeFactoryRule(WINGED_ATTRIBUTE_IDS::contains, id ->
            WingedArcaneMob.createAttributes(kindFor(id)).build()),
        AttributeFactoryRule.exact("hex_bat", _ -> Vex.createAttributes()
            .add(Attributes.FLYING_SPEED, HexBatRules.FLYING_SPEED)
            .build()),
        AttributeFactoryRule.exact("banshee", _ -> Vex.createAttributes()
            .add(Attributes.FLYING_SPEED, com.kadamitas.warlockery.entity.BansheeRules.FLYING_SPEED)
            .build()),
        new AttributeFactoryRule(SPIRIT_IDS::contains, _ -> Vex.createAttributes().build())
    );

    public static final RegistryObject<EntityType<EntEntity>> ENT = register("ent",
        () -> {
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.ENT);
            return EntityType.Builder.of(EntEntity::new, MobCategory.CREATURE)
                .sized(visual.width(), visual.height()).build(REGISTRY.key("ent"));
        });
    public static final RegistryObject<EntityType<BroomEntity>> BROOM = REGISTRY.register(BROOM_ID,
        () -> EntityType.Builder.of(BroomEntity::new, MobCategory.MISC)
            .sized(1.45F, 0.35F)
            .clientTrackingRange(10)
            .updateInterval(1)
            .noSummon()
            .build(REGISTRY.key(BROOM_ID)));
    public static final RegistryObject<EntityType<WerewolfEntity>> WEREWOLF = register("werewolf",
        () -> {
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.WEREWOLF);
            return EntityType.Builder.of(WerewolfEntity::new, MobCategory.MONSTER)
                .sized(visual.width(), visual.height()).notInPeaceful().build(REGISTRY.key("werewolf"));
        });
    public static final RegistryObject<EntityType<WerewolfHunterEntity>> WEREWOLF_HUNTER = register("werewolf_hunter",
        () -> {
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.WEREWOLF_HUNTER);
            return EntityType.Builder.of(WerewolfHunterEntity::new, MobCategory.MONSTER)
                .sized(visual.width(), visual.height()).notInPeaceful().build(REGISTRY.key("werewolf_hunter"));
        });
    public static final RegistryObject<EntityType<HobgoblinEntity>> HOBGOBLIN = hobgoblin("hobgoblin", CreatureKind.HOBGOBLIN);
    public static final RegistryObject<EntityType<HobgoblinEntity>> GOBLIN = hobgoblin("goblin", CreatureKind.GOBLIN);
    public static final RegistryObject<EntityType<HobgoblinEntity>> STONEBROKER = hobgoblin("stonebroker", CreatureKind.STONEBROKER);
    public static final RegistryObject<EntityType<HobgoblinEntity>> FORGEWARDEN = hobgoblin("forgewarden", CreatureKind.FORGEWARDEN);
    public static final RegistryObject<EntityType<NamiEntity>> NAMI = register("nami",
        () -> EntityType.Builder.of(NamiEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)
            .clientTrackingRange(10)
            .build(REGISTRY.key("nami")));

    public static final Map<String, RegistryObject<? extends EntityType<?>>> ALL;

    static {
        ARCANE_KINDS.forEach((id, kind) -> register(id, () -> createArcaneType(id, kind)));
        ALL = Collections.unmodifiableMap(new LinkedHashMap<>(MUTABLE));
    }

    private ModEntities() {
    }

    private static RegistryObject<EntityType<HobgoblinEntity>> hobgoblin(final String id, final CreatureKind kind) {
        return register(id, () -> {
            final boolean boss = GoblinBossRules.isBoss(kind);
            final boolean hostile = boss || kind == CreatureKind.GOBLIN;
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(kind);
            final var builder = EntityType.Builder.<HobgoblinEntity>of(
                (type, level) -> new HobgoblinEntity(type, level, kind),
                hostile ? MobCategory.MONSTER : MobCategory.CREATURE
            ).sized(visual.width(), visual.height());
            if (hostile) {
                builder.notInPeaceful();
            }
            return builder.build(REGISTRY.key(id));
        });
    }

    private static EntityType<?> createArcaneType(final String id, final CreatureKind kind) {
        final EntityRegistration registration = new EntityRegistration(id, kind, CreatureVisualProfile.forKind(kind));
        return Optional.ofNullable(SPECIAL_ARCANE_FACTORIES.get(kind))
            .orElseGet(() -> isSpiritKind(kind) ? ModEntities::createSpirit : ModEntities::createGround)
            .create(registration);
    }

    private static EntityType<?> createWerewolf(final EntityRegistration registration) {
        final boolean feral = "feral_lycan".equals(registration.id());
        final EntityType.EntityFactory<WerewolfEntity> factory = feral
            ? FeralLycanEntity::new
            : WerewolfEntity::new;
        return EntityType.Builder.of(factory, MobCategory.MONSTER)
            .sized(feral ? 0.95F : registration.width(), feral ? 1.25F : registration.height())
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createImp(final EntityRegistration registration) {
        return EntityType.Builder.of(ImpEntity::new, MobCategory.MONSTER)
            .sized(registration.width(), registration.height())
            .fireImmune()
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createStormSimian(final EntityRegistration registration) {
        return EntityType.Builder.of(StormSimianEntity::new, MobCategory.CREATURE)
            .sized(registration.width(), registration.height())
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createLycanVillager(final EntityRegistration registration) {
        return EntityType.Builder.of(LycanVillagerEntity::new, MobCategory.CREATURE)
            .sized(registration.width(), registration.height())
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createNaamah(final EntityRegistration registration) {
        return EntityType.Builder.of(NaamahEntity::new, MobCategory.MONSTER)
            .sized(registration.width(), registration.height())
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createHexBat(final EntityRegistration registration) {
        return EntityType.Builder.of(HexBatEntity::new, MobCategory.MONSTER)
            .sized(registration.width(), registration.height())
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createVampireCourt(final EntityRegistration registration) {
        return EntityType.Builder.of(
            (EntityType<VampireCourtEntity> type, net.minecraft.world.level.Level level) ->
                new VampireCourtEntity(type, level, registration.kind()),
            MobCategory.MONSTER
        ).sized(registration.width(), registration.height())
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createBanshee(final EntityRegistration registration) {
        return EntityType.Builder.of(
            (EntityType<BansheeEntity> type, net.minecraft.world.level.Level level) ->
                new BansheeEntity(type, level),
            MobCategory.MONSTER
        ).sized(registration.width(), registration.height())
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createCorpse(final EntityRegistration registration) {
        return EntityType.Builder.of(
            (EntityType<CorpseEntity> type, net.minecraft.world.level.Level level) ->
                new CorpseEntity(type, level),
            MobCategory.MONSTER
        ).sized(registration.width(), registration.height())
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createEldritchWatcher(final EntityRegistration registration) {
        return EntityType.Builder.of(
            (EntityType<EldritchWatcherEntity> type, net.minecraft.world.level.Level level) ->
                new EldritchWatcherEntity(type, level),
            MobCategory.MONSTER
        ).sized(registration.width(), registration.height())
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createInfernal(final EntityRegistration registration) {
        return EntityType.Builder.of(
            (EntityType<InfernalHierarchyEntity> type, net.minecraft.world.level.Level level) ->
                new InfernalHierarchyEntity(type, level, registration.kind()),
            MobCategory.MONSTER
        ).sized(registration.width(), registration.height())
            .fireImmune()
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createHellhound(final EntityRegistration registration) {
        return EntityType.Builder.of(HellhoundEntity::new, MobCategory.MONSTER)
            .sized(registration.width(), registration.height())
            .fireImmune()
            .notInPeaceful()
            .build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createGround(final EntityRegistration registration) {
        final boolean passive = PASSIVE_GROUND_KINDS.contains(registration.kind());
        final var builder = EntityType.Builder.of(
            (EntityType<ArcaneMob> type, net.minecraft.world.level.Level level) ->
                new ArcaneMob(type, level, registration.kind()),
            passive ? MobCategory.CREATURE : MobCategory.MONSTER
        ).sized(registration.width(), registration.height());
        if (!passive) {
            builder.notInPeaceful();
        }
        if (FIRE_IMMUNE_GROUND_KINDS.contains(registration.kind())) {
            builder.fireImmune();
        }
        return builder.build(REGISTRY.key(registration.id()));
    }

    private static EntityType<?> createSpirit(final EntityRegistration registration) {
        final boolean passive = registration.kind() == CreatureKind.SPIRIT;
        final var builder = EntityType.Builder.of(
            (EntityType<SpiritMob> type, net.minecraft.world.level.Level level) ->
                new SpiritMob(type, level, registration.kind()),
            passive ? MobCategory.CREATURE : MobCategory.MONSTER
        ).sized(registration.width(), registration.height());
        if (!passive) {
            builder.notInPeaceful();
        }
        return builder.build(REGISTRY.key(registration.id()));
    }

    private static <T extends EntityType<?>> RegistryObject<T> register(final String id, final java.util.function.Supplier<T> factory) {
        final RegistryObject<T> object = REGISTRY.register(id, factory);
        MUTABLE.put(id, object);
        return object;
    }

    public static boolean isSpiritKind(final CreatureKind kind) {
        return CreatureVisualProfile.forKind(kind).archetype() == CreatureVisualProfile.Archetype.SPIRIT;
    }

    public static CreatureKind kindFor(final String id) {
        return Optional.ofNullable(SPECIAL_KINDS.get(id))
            .or(() -> Optional.ofNullable(ARCANE_KINDS.get(id)))
            .orElseThrow(() -> new IllegalArgumentException("Unknown Warlockery creature id: " + id));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerAttributes(final EntityAttributeCreationEvent event) {
        ALL.forEach((id, object) -> {
            final EntityType type = object.get();
            event.put(type, attributesFor(id));
        });
    }

    private static AttributeSupplier attributesFor(final String id) {
        return ATTRIBUTE_FACTORY_RULES.stream()
            .filter(rule -> rule.supports(id))
            .findFirst()
            .map(rule -> rule.create(id))
            .orElseGet(() -> groundAttributes(id).build());
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder groundAttributes(final String id) {
        final var attributes = Zombie.createAttributes();
        return switch (id) {
            case "death" -> attributes
                .add(Attributes.MAX_HEALTH, com.kadamitas.warlockery.entity.DeathCombatRules.MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 14)
                .add(Attributes.ARMOR, 12);
            case "abyssal_regent" -> attributes
                .add(Attributes.MAX_HEALTH, AbyssalRegentRules.MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, AbyssalRegentRules.ATTACK_DAMAGE)
                .add(Attributes.ARMOR, AbyssalRegentRules.ARMOR)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
            case "thorned_pursuer" ->
                attributes.add(Attributes.MAX_HEALTH, 100).add(Attributes.ATTACK_DAMAGE, 11).add(Attributes.ARMOR, 8);
            case "emberhorn_archfiend" -> attributes.add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
            case "naamah" -> attributes.add(Attributes.MAX_HEALTH, 100).add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 8).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
            case "hedge_crone" -> attributes.add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.ARMOR, 6);
            case "demon" -> attributes.add(Attributes.MAX_HEALTH, 60)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
            case "werewolf", "feral_lycan" -> attributes
                .add(Attributes.MAX_HEALTH, 42)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
            case "vampire", "blood_thrall" -> attributes
                .add(Attributes.MAX_HEALTH, 36)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
            case "hellhound" ->
                attributes.add(Attributes.MAX_HEALTH, 36).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.MOVEMENT_SPEED, 0.3)
                    .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
            case "bramble_colossus" ->
                attributes.add(Attributes.MAX_HEALTH, 36).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.MOVEMENT_SPEED, 0.3);
            default -> attributes;
        };
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier patronAttributes(final CreatureKind kind) {
        final GoblinBossRules.CombatProfile profile = GoblinBossRules.combatProfile(kind).orElseThrow();
        return Villager.createAttributes()
            .add(Attributes.MAX_HEALTH, profile.health())
            .add(Attributes.ATTACK_DAMAGE, profile.attack())
            .add(Attributes.ARMOR, profile.armor())
            .add(Attributes.MOVEMENT_SPEED, profile.speed())
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerSpawnPlacements(final SpawnPlacementRegisterEvent event) {
        event.register(
            HOBGOBLIN.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            HobgoblinEntity::checkNaturalSpawnRules,
            SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        NATURAL_SPAWN_IDS.stream().filter(id -> !"hobgoblin".equals(id)).forEach(id -> {
            final EntityType type = ALL.get(id).get();
            event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                PASSIVE_SPAWN_IDS.contains(id)
                    ? Mob::checkMobSpawnRules
                    : Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        });
    }

    private record EntityRegistration(
        String id,
        CreatureKind kind,
        CreatureVisualProfile visual
    ) {
        private float width() {
            return visual.width();
        }

        private float height() {
            return visual.height();
        }
    }

    private record AttributeFactoryRule(
        Predicate<String> selector,
        Function<String, AttributeSupplier> factory
    ) {
        private static AttributeFactoryRule exact(
            final String id,
            final Function<String, AttributeSupplier> factory
        ) {
            return new AttributeFactoryRule(id::equals, factory);
        }

        private boolean supports(final String id) {
            return selector.test(id);
        }

        private AttributeSupplier create(final String id) {
            return factory.apply(id);
        }
    }

}
