package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.EntEntity;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.entity.KoboldBossRules;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.ArcaneMob;
import com.kadamitas.warlockery.entity.SpiritMob;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
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
        Map.entry("emberhorn_archfiend", CreatureKind.EMBERHORN_ARCHFIEND), Map.entry("crimson_matriarch", CreatureKind.CRIMSON_MATRIARCH),
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
    public static final Set<String> SPIRIT_IDS = ARCANE_KINDS.entrySet().stream()
        .filter(entry -> isSpiritKind(entry.getValue()))
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());

    public static final RegistryObject<EntityType<EntEntity>> ENT = register("ent",
        () -> {
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(CreatureKind.ENT);
            return EntityType.Builder.of(EntEntity::new, MobCategory.CREATURE)
                .sized(visual.width(), visual.height()).build(REGISTRY.key("ent"));
        });
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
    public static final RegistryObject<EntityType<HobgoblinEntity>> GOBLIN = hobgoblin("goblin", CreatureKind.HOBGOBLIN);
    public static final RegistryObject<EntityType<HobgoblinEntity>> STONEBROKER = hobgoblin("stonebroker", CreatureKind.STONEBROKER);
    public static final RegistryObject<EntityType<HobgoblinEntity>> FORGEWARDEN = hobgoblin("forgewarden", CreatureKind.FORGEWARDEN);

    public static final Map<String, RegistryObject<? extends EntityType<?>>> ALL;

    static {
        ARCANE_KINDS.forEach((id, kind) -> {
            if (kind == CreatureKind.WEREWOLF) {
                final CreatureVisualProfile visual = CreatureVisualProfile.forKind(kind);
                register(id, () -> EntityType.Builder.of(WerewolfEntity::new, MobCategory.MONSTER)
                    .sized(visual.width(), visual.height()).notInPeaceful().build(REGISTRY.key(id)));
            } else if (isSpiritKind(kind)) {
                registerSpirit(id, kind);
            } else {
                registerGround(id, kind);
            }
        });
        ALL = Collections.unmodifiableMap(MUTABLE);
    }

    private ModEntities() {
    }

    private static RegistryObject<EntityType<HobgoblinEntity>> hobgoblin(final String id, final CreatureKind kind) {
        return register(id, () -> {
            final boolean boss = KoboldBossRules.isBoss(kind);
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(kind);
            final var builder = EntityType.Builder.<HobgoblinEntity>of(
                (type, level) -> new HobgoblinEntity(type, level, kind),
                boss ? MobCategory.MONSTER : MobCategory.CREATURE
            ).sized(visual.width(), visual.height());
            if (boss) {
                builder.notInPeaceful();
            }
            return builder.build(REGISTRY.key(id));
        });
    }

    private static void registerGround(final String id, final CreatureKind kind) {
        register(id, () -> {
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(kind);
            var builder = EntityType.Builder.of((EntityType<ArcaneMob> type, net.minecraft.world.level.Level level) ->
                new ArcaneMob(type, level, kind), kind == CreatureKind.OWL || kind == CreatureKind.TOAD || kind == CreatureKind.CAT
                    ? MobCategory.CREATURE : MobCategory.MONSTER).sized(visual.width(), visual.height());
            if (kind != CreatureKind.OWL && kind != CreatureKind.TOAD && kind != CreatureKind.CAT) {
                builder.notInPeaceful();
            }
            if (kind == CreatureKind.DEMON || kind == CreatureKind.HELLHOUND) {
                builder.fireImmune();
            }
            return builder.build(REGISTRY.key(id));
        });
    }

    private static void registerSpirit(final String id, final CreatureKind kind) {
        register(id, () -> {
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(kind);
            final var builder = EntityType.Builder.of(
                (EntityType<SpiritMob> type, net.minecraft.world.level.Level level) -> new SpiritMob(type, level, kind),
                kind == CreatureKind.SPIRIT ? MobCategory.CREATURE : MobCategory.MONSTER
            ).sized(visual.width(), visual.height());
            if (kind != CreatureKind.SPIRIT) {
                builder.notInPeaceful();
            }
            return builder.build(REGISTRY.key(id));
        });
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
        return switch (id) {
            case "ent" -> CreatureKind.ENT;
            case "werewolf" -> CreatureKind.WEREWOLF;
            case "werewolf_hunter" -> CreatureKind.WEREWOLF_HUNTER;
            case "hobgoblin", "goblin" -> CreatureKind.HOBGOBLIN;
            case "stonebroker" -> CreatureKind.STONEBROKER;
            case "forgewarden" -> CreatureKind.FORGEWARDEN;
            default -> {
                final CreatureKind kind = ARCANE_KINDS.get(id);
                if (kind == null) {
                    throw new IllegalArgumentException("Unknown Warlockery creature id: " + id);
                }
                yield kind;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerAttributes(final EntityAttributeCreationEvent event) {
        ALL.forEach((id, object) -> {
            final EntityType type = object.get();
            if (id.equals("ent")) event.put(type, IronGolem.createAttributes().build());
            else if (id.equals("forgewarden")) event.put(type, patronAttributes(CreatureKind.FORGEWARDEN));
            else if (id.equals("stonebroker")) event.put(type, patronAttributes(CreatureKind.STONEBROKER));
            else if (Set.of("hobgoblin", "goblin").contains(id)) event.put(type, Villager.createAttributes().build());
            else if (id.equals("werewolf_hunter")) event.put(type, Pillager.createAttributes().build());
            else if (SPIRIT_IDS.contains(id)) event.put(type, Vex.createAttributes().build());
            else event.put(type, groundAttributes(id).build());
        });
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder groundAttributes(final String id) {
        final var attributes = Zombie.createAttributes();
        return switch (id) {
            case "death" -> attributes.add(Attributes.MAX_HEALTH, 160).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.ARMOR, 12);
            case "abyssal_regent", "thorned_pursuer", "emberhorn_archfiend", "crimson_matriarch" ->
                attributes.add(Attributes.MAX_HEALTH, 100).add(Attributes.ATTACK_DAMAGE, 11).add(Attributes.ARMOR, 8);
            case "hedge_crone", "demon" -> attributes.add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.ARMOR, 6);
            case "werewolf", "feral_lycan", "lycan_villager" ->
                attributes.add(Attributes.MAX_HEALTH, 42).add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.MOVEMENT_SPEED, 0.32);
            case "vampire", "blood_thrall", "hellhound", "bramble_colossus" ->
                attributes.add(Attributes.MAX_HEALTH, 36).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.MOVEMENT_SPEED, 0.3);
            default -> attributes;
        };
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier patronAttributes(final CreatureKind kind) {
        final KoboldBossRules.CombatProfile profile = KoboldBossRules.combatProfile(kind).orElseThrow();
        return Villager.createAttributes()
            .add(Attributes.MAX_HEALTH, profile.health())
            .add(Attributes.ATTACK_DAMAGE, profile.attack())
            .add(Attributes.ARMOR, profile.armor())
            .add(Attributes.MOVEMENT_SPEED, profile.speed())
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerSpawnPlacements(final SpawnPlacementRegisterEvent event) {
        Set.of("ent", "hobgoblin", "spirit", "hellhound", "mandrake", "dreamroot").forEach(id -> {
            final EntityType type = ALL.get(id).get();
            event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Set.of("ent", "hobgoblin", "spirit").contains(id)
                    ? Mob::checkMobSpawnRules
                    : Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        });
    }

}
