package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class SupernaturalProgression {
    public static final int MAX_LEVEL = 10;
    private static final String ROOT = "WarlockerySupernaturalProgression";
    private static final String LEVEL = "level";
    private static final String RESOURCE = "resource";
    private static final String COUNTERS = "counters";
    private static final String SELECTED_POWER = "selected_power";
    private static final String COOLDOWNS = "cooldowns";
    private static final String WEREWOLF_SHAPE = "werewolf_shape";
    private static final String BAT_SWARM_UNTIL = "bat_swarm_until";
    private static final String SANGUINE = "sanguine";

    private SupernaturalProgression() {
    }

    public static boolean beginPath(final Player player, final Path path) {
        final SupernaturalForm current = SupernaturalState.getForm(player);
        if (current != SupernaturalForm.NONE && current != path.form()) {
            return false;
        }
        final boolean newlyInitiated = current == SupernaturalForm.NONE || level(player, path) == 0;
        clearPath(player, path.opposite());
        SupernaturalState.setIdentity(player, path.form());
        if (!newlyInitiated) {
            return true;
        }
        setLevel(player, path, 1);
        setResource(player, path, path == Path.VAMPIRE ? 125 : maximumResource(path, 1));
        if (path == Path.WEREWOLF) {
            setWerewolfShape(player, WerewolfShape.HUMAN);
        }
        return true;
    }

    public static void cure(final Player player) {
        Arrays.stream(Path.values()).forEach(path -> clearPath(player, path));
        setWerewolfShape(player, WerewolfShape.HUMAN);
        setBatSwarmUntil(player, 0L);
        SupernaturalState.setIdentity(player, SupernaturalForm.NONE);
    }

    public static int level(final Player player, final Path path) {
        final Optional<CompoundTag> current = pathState(WarlockeryEntityData.get(player), path, false);
        final int legacy = WarlockeryEntityData.get(player).getIntOr(path.key(), 0);
        return Math.clamp(current.map(tag -> tag.getIntOr(LEVEL, legacy)).orElse(legacy), 0, MAX_LEVEL);
    }

    public static void setLevel(final Player player, final Path path, final int requestedLevel) {
        final int level = Math.clamp(requestedLevel, 0, MAX_LEVEL);
        WarlockeryEntityData.get(player).putInt(path.key(), level);
        pathState(WarlockeryEntityData.get(player), path, true).orElseThrow().putInt(LEVEL, level);
        setResource(player, path, Math.min(resource(player, path), maximumResource(path, level)));
    }

    public static int maximumResource(final Path path, final int requestedLevel) {
        final int level = Math.clamp(requestedLevel, 0, MAX_LEVEL);
        if (level == 0) {
            return 0;
        }
        return path == Path.WEREWOLF
            ? 100 + level * 20
            : VampireBloodCapacityRules.capacity(level);
    }

    public static int resource(final Player player, final Path path) {
        final int maximum = maximumResource(path, level(player, path));
        return pathState(WarlockeryEntityData.get(player), path, false)
            .map(tag -> Math.clamp(tag.getIntOr(RESOURCE, 0), 0, maximum))
            .orElse(0);
    }

    public static void setResource(final Player player, final Path path, final int amount) {
        final int maximum = maximumResource(path, level(player, path));
        pathState(WarlockeryEntityData.get(player), path, true).orElseThrow()
            .putInt(RESOURCE, Math.clamp(amount, 0, maximum));
        if (path == Path.VAMPIRE) {
            reconcileSanguine(player);
        }
    }

    public static int addResource(final Player player, final Path path, final int amount) {
        setResource(player, path, resource(player, path) + amount);
        return resource(player, path);
    }

    public static boolean spend(final Player player, final Path path, final int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Resource cost cannot be negative");
        }
        final int current = resource(player, path);
        if (current < amount) {
            return false;
        }
        setResource(player, path, current - amount);
        return true;
    }

    public static int counter(final Player player, final Path path, final Enum<?> metric) {
        return counters(WarlockeryEntityData.get(player), path, false)
            .map(tag -> Math.max(0, tag.getIntOr(metric.name(), 0)))
            .orElse(0);
    }

    public static int observe(final Player player, final Path path, final Enum<?> metric, final int amount) {
        if (amount <= 0) {
            return counter(player, path, metric);
        }
        final CompoundTag counters = counters(WarlockeryEntityData.get(player), path, true).orElseThrow();
        final int updated = (int) Math.min(Integer.MAX_VALUE, (long) counter(player, path, metric) + amount);
        counters.putInt(metric.name(), updated);
        return updated;
    }

    public static void setCounter(final Player player, final Path path, final Enum<?> metric, final int value) {
        final CompoundTag counters = counters(WarlockeryEntityData.get(player), path, true).orElseThrow();
        if (value <= 0) {
            counters.remove(metric.name());
        } else {
            counters.putInt(metric.name(), value);
        }
    }

    public static int observeUnique(
        final Player player,
        final Path path,
        final Enum<?> metric,
        final long marker
    ) {
        final CompoundTag state = pathState(WarlockeryEntityData.get(player), path, true).orElseThrow();
        final String key = metric.name() + "_seen";
        final long[] markers = state.getLongArray(key).orElseGet(() -> new long[0]);
        if (Arrays.stream(markers).anyMatch(existing -> existing == marker)) {
            return counter(player, path, metric);
        }
        state.putLongArray(
            key,
            LongStream.concat(Arrays.stream(markers), LongStream.of(marker)).distinct().toArray()
        );
        observe(player, path, metric, 1);
        return counter(player, path, metric);
    }

    public static void clearCounters(final Player player, final Path path) {
        final CompoundTag state = pathState(WarlockeryEntityData.get(player), path, true).orElseThrow();
        state.put(COUNTERS, new CompoundTag());
        state.keySet().stream().filter(key -> key.endsWith("_seen")).toList().forEach(state::remove);
    }

    public static boolean flag(final Player player, final Path path, final String flag) {
        return pathState(WarlockeryEntityData.get(player), path, false)
            .map(state -> state.getBooleanOr("flag_" + flag, false))
            .orElse(false);
    }

    public static boolean markFlag(final Player player, final Path path, final String flag) {
        final CompoundTag state = pathState(WarlockeryEntityData.get(player), path, true).orElseThrow();
        final String key = "flag_" + flag;
        final boolean changed = !state.getBooleanOr(key, false);
        state.putBoolean(key, true);
        return changed;
    }

    public static long value(final Player player, final Path path, final String key) {
        return pathState(WarlockeryEntityData.get(player), path, false)
            .map(state -> state.getLongOr("value_" + key, 0L))
            .orElse(0L);
    }

    public static void setValue(final Player player, final Path path, final String key, final long value) {
        pathState(WarlockeryEntityData.get(player), path, true).orElseThrow().putLong("value_" + key, value);
    }

    public static SupernaturalPower selectedPower(final Player player) {
        final Path path = Path.forForm(SupernaturalState.getForm(player)).orElse(Path.VAMPIRE);
        final List<SupernaturalPower> unlocked = SupernaturalPower.unlocked(path, level(player, path));
        if (unlocked.isEmpty()) {
            return null;
        }
        final String stored = pathState(WarlockeryEntityData.get(player), path, false)
            .map(tag -> tag.getStringOr(SELECTED_POWER, ""))
            .orElse("");
        return SupernaturalPower.find(stored).filter(unlocked::contains).orElse(unlocked.getFirst());
    }

    public static SupernaturalPower cyclePower(final Player player) {
        final Optional<Path> currentPath = Path.forForm(SupernaturalState.getForm(player));
        if (currentPath.isEmpty()) {
            return null;
        }
        final Path path = currentPath.orElseThrow();
        final List<SupernaturalPower> unlocked = SupernaturalPower.unlocked(
            path,
            level(player, path)
        );
        if (unlocked.isEmpty()) {
            return null;
        }
        final SupernaturalPower current = selectedPower(player);
        final SupernaturalPower next = unlocked.get((unlocked.indexOf(current) + 1) % unlocked.size());
        pathState(WarlockeryEntityData.get(player), path, true).orElseThrow()
            .putString(SELECTED_POWER, next.id());
        return next;
    }

    public static boolean selectPower(final Player player, final SupernaturalPower power) {
        if (SupernaturalState.getForm(player) != power.path().form()
            || level(player, power.path()) < power.level()) {
            return false;
        }
        pathState(WarlockeryEntityData.get(player), power.path(), true).orElseThrow()
            .putString(SELECTED_POWER, power.id());
        return true;
    }

    public static boolean onCooldown(final Player player, final SupernaturalPower power) {
        return cooldown(player, power) > player.level().getGameTime();
    }

    public static long cooldown(final Player player, final SupernaturalPower power) {
        return cooldowns(WarlockeryEntityData.get(player), power.path(), false)
            .map(tag -> tag.getLongOr(power.id(), 0L))
            .orElse(0L);
    }

    public static void startCooldown(final Player player, final SupernaturalPower power) {
        cooldowns(WarlockeryEntityData.get(player), power.path(), true).orElseThrow()
            .putLong(power.id(), player.level().getGameTime() + power.cooldown());
    }

    public static WerewolfShape werewolfShape(final Player player) {
        return root(WarlockeryEntityData.get(player), false)
            .map(tag -> WerewolfShape.parse(tag.getStringOr(WEREWOLF_SHAPE, WerewolfShape.HUMAN.name())))
            .orElse(WerewolfShape.HUMAN);
    }

    public static void setWerewolfShape(final Player player, final WerewolfShape shape) {
        root(WarlockeryEntityData.get(player), true).orElseThrow().putString(WEREWOLF_SHAPE, shape.name());
    }

    public static long batSwarmUntil(final Player player) {
        return root(WarlockeryEntityData.get(player), false).map(tag -> tag.getLongOr(BAT_SWARM_UNTIL, 0L)).orElse(0L);
    }

    public static void setBatSwarmUntil(final Player player, final long gameTime) {
        root(WarlockeryEntityData.get(player), true).orElseThrow().putLong(BAT_SWARM_UNTIL, Math.max(0L, gameTime));
    }

    public static boolean sanguine(final Player player) {
        return pathState(WarlockeryEntityData.get(player), Path.VAMPIRE, false)
            .map(tag -> tag.getBooleanOr(SANGUINE, false))
            .orElse(false);
    }

    public static void setSanguine(final Player player, final boolean sanguine) {
        pathState(WarlockeryEntityData.get(player), Path.VAMPIRE, true).orElseThrow()
            .putBoolean(SANGUINE, sanguine);
    }

    static boolean reconcileSanguine(final Player player) {
        final Optional<CompoundTag> state = pathState(WarlockeryEntityData.get(player), Path.VAMPIRE, false);
        if (state.isEmpty() && SupernaturalState.getForm(player) != SupernaturalForm.VAMPIRE) {
            return false;
        }
        final boolean updated = VampireSustenanceRules.updateSanguine(
            SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE,
            sanguine(player),
            resource(player, Path.VAMPIRE),
            maximumResource(Path.VAMPIRE, level(player, Path.VAMPIRE))
        );
        state.orElseGet(() -> pathState(WarlockeryEntityData.get(player), Path.VAMPIRE, true).orElseThrow())
            .putBoolean(SANGUINE, updated);
        return updated;
    }

    public static void copy(final Player source, final Player destination) {
        WarlockeryEntityData.get(source).getCompound(ROOT)
            .ifPresent(state -> WarlockeryEntityData.get(destination).put(ROOT, state.copy()));
        for (final Path path : Path.values()) {
            WarlockeryEntityData.get(destination).putInt(path.key(), level(source, path));
        }
    }

    private static void clearPath(final Player player, final Path path) {
        WarlockeryEntityData.get(player).putInt(path.key(), 0);
        root(WarlockeryEntityData.get(player), false).ifPresent(root -> root.remove(path.id()));
    }

    private static Optional<CompoundTag> root(final CompoundTag data, final boolean create) {
        return child(data, ROOT, create);
    }

    private static Optional<CompoundTag> pathState(final CompoundTag data, final Path path, final boolean create) {
        return root(data, create).flatMap(root -> child(root, path.id(), create));
    }

    private static Optional<CompoundTag> counters(final CompoundTag data, final Path path, final boolean create) {
        return pathState(data, path, create).flatMap(state -> child(state, COUNTERS, create));
    }

    private static Optional<CompoundTag> cooldowns(final CompoundTag data, final Path path, final boolean create) {
        return pathState(data, path, create).flatMap(state -> child(state, COOLDOWNS, create));
    }

    private static Optional<CompoundTag> child(
        final CompoundTag parent,
        final String key,
        final boolean create
    ) {
        final Optional<CompoundTag> existing = parent.getCompound(key);
        if (existing.isPresent() || !create) {
            return existing;
        }
        final CompoundTag created = new CompoundTag();
        parent.put(key, created);
        return Optional.of(created);
    }

    public enum Path implements StringIdentified {
        WEREWOLF("werewolf", "WarlockeryWerewolfLevel", SupernaturalForm.WEREWOLF),
        VAMPIRE("vampire", "WarlockeryVampireLevel", SupernaturalForm.VAMPIRE);

        private static final EnumLookup<Path> LOOKUP = EnumLookup.create("supernatural path", values());
        private static final Map<SupernaturalForm, Path> BY_FORM = Arrays.stream(values()).collect(
            Collectors.toUnmodifiableMap(Path::form, Function.identity())
        );

        private final String id;
        private final String key;
        private final SupernaturalForm form;

        Path(final String id, final String key, final SupernaturalForm form) {
            this.id = id;
            this.key = key;
            this.form = form;
        }

        @Override
        public String id() {
            return id;
        }

        public String key() {
            return key;
        }

        public SupernaturalForm form() {
            return form;
        }

        public Path opposite() {
            return this == WEREWOLF ? VAMPIRE : WEREWOLF;
        }

        public static Optional<Path> forForm(final SupernaturalForm form) {
            return Optional.ofNullable(form).map(BY_FORM::get);
        }

        public static Optional<Path> find(final String id) {
            final String normalized = id.toLowerCase(Locale.ROOT);
            return LOOKUP.find(normalized);
        }
    }
}
