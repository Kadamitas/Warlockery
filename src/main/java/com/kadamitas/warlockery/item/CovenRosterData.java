package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.FamiliarBondRules;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Versioned, legacy-compatible coven roster. Each owner maps to at most
 * {@link FamiliarBondRules#MAX_COVEN_MAGES} distinct Mage UUIDs; malformed and duplicate rows are
 * dropped and legacy overflow is UUID-sorted and capped deterministically. Public snapshots are
 * immutable and UUID-sorted, and the data is dirtied exactly once per real semantic change.
 *
 * <p>Membership is never inferred from species, proximity, or ritual attendance: only an explicit
 * owner-bound registration creates it, and only death or an explicit unregister removes it. Mere
 * unload, owner logout, or dimension separation retains membership, and no lookup here loads a
 * chunk or dimension.</p>
 */
public final class CovenRosterData extends SavedData {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PER_OWNER = FamiliarBondRules.MAX_COVEN_MAGES;

    /**
     * Version 1 is a compound carrying an explicit schema version. The legacy alternative is the
     * original bare assignment list, so an existing unversioned save still decodes and is
     * normalized exactly once on read.
     */
    private static final Codec<Versioned> VERSIONED_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("version").forGetter(Versioned::version),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(Versioned::entries)
        ).apply(instance, Versioned::new));

    private static final Codec<CovenRosterData> CODEC = Codec
        .either(VERSIONED_CODEC, Entry.CODEC.listOf())
        .xmap(
            either -> either.map(
                versioned -> new CovenRosterData(versioned.version(), versioned.entries()),
                legacy -> new CovenRosterData(0, legacy)
            ),
            data -> Either.left(new Versioned(SCHEMA_VERSION, data.entries()))
        );

    public static final SavedDataType<CovenRosterData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "coven_rosters"),
        CovenRosterData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    /** Owner to its membership. Bounded at six entries per owner; never an unbounded collection. */
    private final Map<UUID, LinkedHashSet<UUID>> roster = new LinkedHashMap<>();

    public CovenRosterData() {
    }

    /**
     * Decodes both layouts. Invalid UUIDs and duplicates are discarded and overflow is stable
     * UUID-capped; the data is marked dirty only when that normalization actually changed the
     * stored meaning, so an already clean versioned save is not rewritten.
     */
    private CovenRosterData(final int storedVersion, final List<Entry> entries) {
        boolean normalized = storedVersion != SCHEMA_VERSION;
        final Map<UUID, Integer> rawRowsPerOwner = new LinkedHashMap<>();
        final List<Assignment> parsed = new ArrayList<>();
        for (final Entry entry : entries) {
            final Optional<UUID> owner = parse(entry.owner());
            final Optional<UUID> mage = parse(entry.mage());
            if (owner.isEmpty() || mage.isEmpty()) {
                normalized = true;
                continue;
            }
            rawRowsPerOwner.merge(owner.orElseThrow(), 1, Integer::sum);
            parsed.add(new Assignment(owner.orElseThrow(), mage.orElseThrow()));
        }
        // Sort first, then deduplicate. Deduplicating in raw decode order would make the result
        // depend on row order: [(A,M),(B,M)] and its reverse would resolve to different owners.
        parsed.sort(Comparator.comparing((Assignment assignment) -> assignment.owner().toString())
            .thenComparing(assignment -> assignment.mage().toString()));
        final Set<UUID> seenMages = new LinkedHashSet<>();
        final List<Assignment> accepted = new ArrayList<>();
        for (final Assignment assignment : parsed) {
            if (seenMages.add(assignment.mage())) {
                accepted.add(assignment);
            } else {
                normalized = true;
            }
        }
        final List<Assignment> discarded = new ArrayList<>();
        for (final Assignment assignment : accepted) {
            final LinkedHashSet<UUID> members =
                roster.computeIfAbsent(assignment.owner(), _ -> new LinkedHashSet<>());
            if (members.size() >= MAX_PER_OWNER) {
                normalized = true;
                discarded.add(assignment);
                continue;
            }
            members.add(assignment.mage());
        }
        reportDiscardedOverflow(discarded, rawRowsPerOwner);
        if (normalized) {
            setDirty();
        }
    }

    /**
     * Deliberate, logged, and documented truncation. The pre-F13 roster had no cap and the old
     * countOwnedMages re-registered every loaded owned Mage on every call, so a real 1.4 save can
     * legitimately hold more than {@link #MAX_PER_OWNER} rows for one owner. The approved design
     * fixes the coven at six, so the surplus cannot be kept: decode retains the six
     * lexicographically lowest Mage UUIDs per owner and drops the rest.
     *
     * <p>This is a real, deliberate data loss with a player-visible consequence. A discarded Mage
     * keeps its owner tag in {@code CreatureBehaviorState}, so it stays bound, alive, and
     * persistent, but while its owner remains full it is not counted by the recruitment cap and is
     * not called by the Seer Stone, and {@link #register} will not re-add it. The state is not
     * permanent: {@code CircleMageRuntime} re-registers a bound Mage on every load, so a discarded
     * Mage rejoins the roster as soon as a slot frees. That is exactly the adversarial overflow
     * state the approved design requires to be recorded rather than hidden, so it is logged once
     * per affected owner with exact counts and identities.</p>
     */
    private static void reportDiscardedOverflow(
        final List<Assignment> discarded,
        final Map<UUID, Integer> rawRowsPerOwner
    ) {
        if (discarded.isEmpty()) {
            return;
        }
        discarded.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Assignment::owner,
                LinkedHashMap::new,
                java.util.stream.Collectors.mapping(
                    Assignment::mage, java.util.stream.Collectors.toList())
            ))
            .forEach((owner, mages) -> LOGGER.warn(
                "Warlockery coven roster: owner {} held {} raw assignment rows, above the cap of {}."
                    + " Retained the {} lowest Mage UUIDs and discarded {}: {}."
                    + " Those Mages stay bound and alive but are not counted or callable until a"
                    + " roster slot frees, at which point they rejoin on their next load.",
                owner, rawRowsPerOwner.getOrDefault(owner, MAX_PER_OWNER + mages.size()),
                MAX_PER_OWNER, MAX_PER_OWNER, mages.size(), mages
            ));
    }

    public static CovenRosterData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Idempotent server-authoritative registration. Re-registering an existing pair changes and
     * dirties nothing. A legitimate member is never displaced once the owner is full, so an
     * adversarial overflow leaves the Mage bound but without a new roster slot.
     */
    public boolean register(final UUID owner, final UUID mage) {
        if (owner == null || mage == null) {
            return false;
        }
        final LinkedHashSet<UUID> members = roster.computeIfAbsent(owner, _ -> new LinkedHashSet<>());
        if (members.contains(mage)) {
            return true;
        }
        // The cap is checked before anything is removed. Stripping the Mage from its real owner
        // and only then refusing to admit it would leave it in no roster at all, which is strictly
        // worse than refusing the reassignment outright.
        if (members.size() >= MAX_PER_OWNER) {
            return false;
        }
        removeFromOtherOwners(owner, mage);
        members.add(mage);
        setDirty();
        return true;
    }

    private void removeFromOtherOwners(final UUID keep, final UUID mage) {
        for (final Map.Entry<UUID, LinkedHashSet<UUID>> entry : roster.entrySet()) {
            if (!entry.getKey().equals(keep)) {
                entry.getValue().remove(mage);
            }
        }
        roster.entrySet().removeIf(entry -> entry.getValue().isEmpty() && !entry.getKey().equals(keep));
    }

    public void unregister(final UUID mage) {
        if (mage == null) {
            return;
        }
        boolean removed = false;
        for (final LinkedHashSet<UUID> members : roster.values()) {
            removed |= members.remove(mage);
        }
        roster.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (removed) {
            setDirty();
        }
    }

    public int count(final UUID owner) {
        return members(owner).size();
    }

    /** Immutable UUID-sorted snapshot capped at six. Never a live view of the stored set. */
    public List<UUID> members(final UUID owner) {
        if (owner == null) {
            return List.of();
        }
        return roster.getOrDefault(owner, new LinkedHashSet<>()).stream()
            .sorted(Comparator.comparing(UUID::toString))
            .limit(MAX_PER_OWNER)
            .toList();
    }

    public Optional<UUID> ownerOf(final UUID mage) {
        if (mage == null) {
            return Optional.empty();
        }
        return roster.entrySet().stream()
            .filter(entry -> entry.getValue().contains(mage))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    /** Deterministic encoding: owners UUID-sorted, then each capped UUID-sorted membership. */
    public List<Entry> entries() {
        return roster.keySet().stream()
            .sorted(Comparator.comparing(UUID::toString))
            .flatMap(owner -> members(owner).stream()
                .map(mage -> new Entry(owner.toString(), mage.toString())))
            .toList();
    }

    private static Optional<UUID> parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** Test-visible decode entry point covering both the versioned and legacy layouts. */
    public static CovenRosterData decode(final int storedVersion, final List<Entry> entries) {
        return new CovenRosterData(storedVersion, entries);
    }

    private record Assignment(UUID owner, UUID mage) {
    }

    private record Versioned(int version, List<Entry> entries) {
    }

    public record Entry(String owner, String mage) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("owner").forGetter(Entry::owner),
            Codec.STRING.fieldOf("mage").forGetter(Entry::mage)
        ).apply(instance, Entry::new));
    }
}
