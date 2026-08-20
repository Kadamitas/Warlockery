package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The only shared F11 Hobgoblin authority in one dimension: caravan membership, the one camp per
 * caravan with its exact owned edit journal, and the bounded work claims. Every lookup is a direct
 * key lookup, never a dimension scan, and every list is capped and normalized on both read and
 * write.
 *
 * <p>This is a semantic ownership ledger, not an offscreen simulator. It never spawns, breeds,
 * attacks, trades, moves, mines, gathers, requests a path, forces a chunk, or edits a block, and it
 * stores no live entity, live container, block state object, path, or recursive relation. Records
 * age arithmetically from loaded ticks only; an unloaded caravan simply stops aging.</p>
 */
public final class HobgoblinJourneyData extends SavedData {
    private static final Codec<MemberRecord> MEMBER_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(MemberRecord::id),
            Codec.INT.optionalFieldOf("remaining", HobgoblinJourneyRules.MEMBER_EXPIRY_TICKS)
                .forGetter(MemberRecord::remainingTicks)
        ).apply(instance, MemberRecord::new));
    private static final Codec<CaravanRecord> CARAVAN_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.LONG.fieldOf("key").forGetter(CaravanRecord::key),
            Codec.INT.optionalFieldOf("version", HobgoblinJourneyRules.DATA_SCHEMA_VERSION)
                .forGetter(CaravanRecord::schemaVersion),
            MEMBER_CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(CaravanRecord::members),
            Codec.STRING.optionalFieldOf("leader", "").forGetter(CaravanRecord::encodedLeader),
            Codec.LONG.optionalFieldOf("waypoint", Long.MIN_VALUE).forGetter(CaravanRecord::encodedWaypoint),
            Codec.LONG.optionalFieldOf("camp", Long.MIN_VALUE).forGetter(CaravanRecord::encodedCampKey),
            Codec.INT.optionalFieldOf("stabilize", 0).forGetter(CaravanRecord::leaderStabilizeTicks),
            Codec.LONG.optionalFieldOf("advanced", Long.MIN_VALUE)
                .forGetter(CaravanRecord::lastAdvancedGameTime)
        ).apply(instance, CaravanRecord::new));
    private static final Codec<CampEdit> EDIT_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.LONG.fieldOf("pos").forGetter(CampEdit::encodedPosition),
            Codec.STRING.fieldOf("placed").forGetter(CampEdit::placedBlockId)
        ).apply(instance, CampEdit::new));
    private static final Codec<CampRecord> CAMP_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.LONG.fieldOf("key").forGetter(CampRecord::key),
            Codec.LONG.fieldOf("caravan").forGetter(CampRecord::caravanKey),
            Codec.LONG.optionalFieldOf("anchor", Long.MIN_VALUE).forGetter(CampRecord::encodedAnchor),
            Codec.STRING.optionalFieldOf("phase", CampPhase.NONE.name()).forGetter(CampRecord::encodedPhase),
            Codec.INT.optionalFieldOf("expiry", HobgoblinJourneyRules.CAMP_EXPIRY_TICKS)
                .forGetter(CampRecord::expiryRemainingTicks),
            Codec.INT.optionalFieldOf("hold", 0).forGetter(CampRecord::eventHoldRemainingTicks),
            EDIT_CODEC.listOf().optionalFieldOf("journal", List.of()).forGetter(CampRecord::journal),
            Codec.INT.optionalFieldOf("dirt", 0).forGetter(CampRecord::reservedDirt),
            Codec.INT.optionalFieldOf("logs", 0).forGetter(CampRecord::reservedLogs),
            Codec.LONG.optionalFieldOf("advanced", Long.MIN_VALUE)
                .forGetter(CampRecord::lastAdvancedGameTime)
        ).apply(instance, CampRecord::new));
    private static final Codec<ClaimRecord> CLAIM_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(ClaimRecord::id),
            Codec.STRING.fieldOf("kind").forGetter(ClaimRecord::kind),
            UUIDUtil.STRING_CODEC.fieldOf("claimant").forGetter(ClaimRecord::claimant),
            Codec.LONG.optionalFieldOf("site", Long.MIN_VALUE).forGetter(ClaimRecord::encodedSite),
            Codec.INT.optionalFieldOf("lease", 0).forGetter(ClaimRecord::remainingLeaseTicks)
        ).apply(instance, ClaimRecord::new));
    private static final Codec<HobgoblinJourneyData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            CARAVAN_CODEC.listOf().optionalFieldOf("caravans", List.of())
                .forGetter(HobgoblinJourneyData::caravanEntries),
            CAMP_CODEC.listOf().optionalFieldOf("camps", List.of())
                .forGetter(HobgoblinJourneyData::campEntries),
            CLAIM_CODEC.listOf().optionalFieldOf("claims", List.of())
                .forGetter(HobgoblinJourneyData::claimEntries),
            Codec.LONG.optionalFieldOf("advanced", Long.MIN_VALUE)
                .forGetter(HobgoblinJourneyData::lastAdvancedGameTime)
        ).apply(instance, HobgoblinJourneyData::new));
    public static final SavedDataType<HobgoblinJourneyData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "hobgoblin_journey"),
        HobgoblinJourneyData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, CaravanRecord> caravans = new HashMap<>();
    private final Map<Long, CampRecord> camps = new HashMap<>();
    private final Map<UUID, ClaimRecord> claims = new HashMap<>();
    private long lastAdvancedGameTime = Long.MIN_VALUE;

    public HobgoblinJourneyData() {
        this(List.of(), List.of(), List.of(), Long.MIN_VALUE);
    }

    private HobgoblinJourneyData(
        final List<CaravanRecord> loadedCaravans,
        final List<CampRecord> loadedCamps,
        final List<ClaimRecord> loadedClaims,
        final long advanced
    ) {
        loadedCaravans.stream()
            .filter(record -> record.schemaVersion() <= HobgoblinJourneyRules.DATA_SCHEMA_VERSION)
            .map(CaravanRecord::normalized)
            .limit(HobgoblinJourneyRules.MAX_CARAVAN_RECORDS)
            .forEach(record -> caravans.put(record.key(), record));
        loadedCamps.stream()
            .map(CampRecord::normalized)
            .limit(HobgoblinJourneyRules.MAX_CAMP_RECORDS)
            .forEach(record -> camps.put(record.key(), record));
        loadedClaims.stream()
            .limit(HobgoblinJourneyRules.MAX_CLAIM_RECORDS)
            .forEach(record -> claims.put(record.id(), record));
        lastAdvancedGameTime = advanced;
    }

    public static HobgoblinJourneyData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<CaravanRecord> caravanEntries() {
        return caravans.values().stream().sorted(Comparator.comparingLong(CaravanRecord::key)).toList();
    }

    private List<CampRecord> campEntries() {
        return camps.values().stream().sorted(Comparator.comparingLong(CampRecord::key)).toList();
    }

    private List<ClaimRecord> claimEntries() {
        return claims.values().stream()
            .sorted(Comparator.comparing(record -> record.id().toString()))
            .toList();
    }

    private long lastAdvancedGameTime() {
        return lastAdvancedGameTime;
    }

    // ---------------------------------------------------------------- caravans

    public CaravanRecord caravan(final long key) {
        return caravans.getOrDefault(key, CaravanRecord.empty(key));
    }

    public int caravanCount() {
        return caravans.size();
    }

    public int population(final long key) {
        return caravan(key).members().size();
    }

    public List<UUID> members(final long key) {
        return caravan(key).members().stream().map(MemberRecord::id).toList();
    }

    /**
     * Joining is also the membership heartbeat: a loaded member re-joins on its own reconciliation
     * cadence, which refreshes its lease. A member that died, was removed, or unloaded stops
     * refreshing and ages out instead of inflating the population forever.
     */
    public boolean joinCaravan(final long key, final UUID member) {
        if (member == null) {
            return false;
        }
        final CaravanRecord current = caravan(key);
        if (current.hasMember(member)) {
            put(current.withRefreshedMember(member));
            return true;
        }
        if (!caravans.containsKey(key) && caravans.size() >= HobgoblinJourneyRules.MAX_CARAVAN_RECORDS
            && !evictOneEmptyCaravan()) {
            return false;
        }
        if (current.members().size() >= HobgoblinJourneyRules.MAX_CARAVAN_MEMBERS) {
            return false;
        }
        put(current.withMember(member));
        return true;
    }

    /** Immediate departure on death or removal; also releases every claim that member held. */
    public void leaveCaravan(final long key, final UUID member) {
        if (member == null) {
            return;
        }
        releaseClaimsOf(member);
        if (!caravans.containsKey(key)) {
            return;
        }
        put(caravan(key).withoutMember(member));
    }

    public Optional<UUID> leader(final long key) {
        return caravan(key).leader();
    }

    /**
     * Leader election is deterministic and stabilized: a newly lost leader is only replaced after
     * the declared stabilization delay, so a briefly unloaded leader does not cause a route flap.
     */
    public Optional<UUID> electLeader(final long key, final List<UUID> presentAdults) {
        final CaravanRecord current = caravan(key);
        final Optional<UUID> existing = current.leader();
        if (existing.isPresent() && presentAdults != null && presentAdults.contains(existing.get())) {
            // A present leader keeps the delay fully charged, so the countdown that follows a real
            // absence always starts from the top rather than from whatever was left over.
            put(current.withLeaderStabilize(HobgoblinJourneyRules.LEADER_STABILIZE_TICKS));
            return existing;
        }
        if (existing.isPresent() && !HobgoblinJourneyRules.isDue(current.leaderStabilizeTicks())) {
            // Briefly missing, not lost. Replacing here would flap the shared route every time a
            // leader stepped behind a hill for one reconciliation cadence.
            return existing;
        }
        final Optional<UUID> elected = HobgoblinJourneyRules.electLeader(presentAdults);
        put(current.withLeader(elected)
            .withLeaderStabilize(HobgoblinJourneyRules.LEADER_STABILIZE_TICKS));
        return elected;
    }

    public Optional<BlockPos> waypoint(final long key) {
        return caravan(key).waypoint();
    }

    public void setWaypoint(final long key, final BlockPos waypoint) {
        put(caravan(key).withWaypoint(waypoint));
    }

    // ---------------------------------------------------------------- claims

    /**
     * At most one live claim per claimant and per worksite, capped per dimension. Every rejection is
     * explicit; the caller simply does not act.
     */
    public Optional<UUID> claim(final String kind, final UUID claimant, final Optional<BlockPos> site) {
        if (claimant == null || kind == null) {
            return Optional.empty();
        }
        final long encodedSite = site == null || site.isEmpty() ? Long.MIN_VALUE : site.get().asLong();
        final boolean holds = claims.values().stream()
            .anyMatch(record -> record.claimant().equals(claimant));
        final boolean taken = encodedSite != Long.MIN_VALUE && claims.values().stream()
            .anyMatch(record -> record.encodedSite() == encodedSite);
        if (!HobgoblinJourneyRules.canGrantClaim(claims.size(), holds, taken)) {
            return Optional.empty();
        }
        final UUID id = UUID.nameUUIDFromBytes(
            (claimant + "/" + kind + "/" + encodedSite + "/" + claims.size()).getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )
        );
        claims.put(id, new ClaimRecord(
            id, kind, claimant, encodedSite, HobgoblinJourneyRules.leaseTicks()
        ));
        setDirty();
        return Optional.of(id);
    }

    public boolean holdsClaim(final UUID id) {
        return id != null && claims.containsKey(id);
    }

    public void releaseClaim(final UUID id) {
        if (id != null && claims.remove(id) != null) {
            setDirty();
        }
    }

    public void releaseClaimsOf(final UUID claimant) {
        if (claimant == null) {
            return;
        }
        if (claims.values().removeIf(record -> record.claimant().equals(claimant))) {
            setDirty();
        }
    }

    public int claimCount() {
        return claims.size();
    }

    public boolean siteClaimed(final BlockPos site) {
        return site != null && claims.values().stream()
            .anyMatch(record -> record.encodedSite() == site.asLong());
    }

    // ---------------------------------------------------------------- camps

    public CampRecord camp(final long key) {
        return camps.getOrDefault(key, CampRecord.empty(key));
    }

    public int campCount() {
        return camps.size();
    }

    public boolean caravanHasCamp(final long caravanKey) {
        return caravan(caravanKey).campKey().isPresent();
    }

    /**
     * Reserves the camp record and its exact material counts before any block is touched. The
     * record is created in {@link CampPhase#RESERVE}; only the runtime's commit branch may advance
     * it, so a failed proposal can never leave an active camp behind.
     */
    public boolean openCamp(
        final long campKey,
        final long caravanKey,
        final BlockPos anchor,
        final int reservedDirt,
        final int reservedLogs
    ) {
        if (anchor == null || camps.containsKey(campKey)
            || camps.size() >= HobgoblinJourneyRules.MAX_CAMP_RECORDS
            || caravanHasCamp(caravanKey)) {
            return false;
        }
        camps.put(campKey, new CampRecord(
            campKey, caravanKey, anchor.asLong(), CampPhase.RESERVE.name(),
            HobgoblinJourneyRules.CAMP_EXPIRY_TICKS, 0, List.of(),
            Math.max(0, reservedDirt), Math.max(0, reservedLogs), Long.MIN_VALUE
        ));
        put(caravan(caravanKey).withCampKey(Optional.of(campKey)));
        setDirty();
        return true;
    }

    public void setCampPhase(final long campKey, final CampPhase phase) {
        if (!camps.containsKey(campKey) || phase == null) {
            return;
        }
        camps.put(campKey, camp(campKey).withPhase(phase));
        setDirty();
    }

    /**
     * Records one owned placement <em>before</em> the world is mutated, so a crash between the
     * record and the placement leaves an owned entry whose current state simply will not match at
     * teardown, rather than an unowned block nobody can ever remove.
     */
    public boolean recordCampEdit(final long campKey, final BlockPos position, final String placedBlockId) {
        if (!camps.containsKey(campKey) || position == null || placedBlockId == null) {
            return false;
        }
        final CampRecord current = camp(campKey);
        if (current.journal().size() >= HobgoblinJourneyRules.CAMP_MAX_EDITS) {
            return false;
        }
        camps.put(campKey, current.withEdit(new CampEdit(position.asLong(), placedBlockId)));
        setDirty();
        return true;
    }

    public List<CampEdit> campJournal(final long campKey) {
        return camp(campKey).journal();
    }

    public void removeCampEdit(final long campKey, final BlockPos position) {
        if (!camps.containsKey(campKey) || position == null) {
            return;
        }
        camps.put(campKey, camp(campKey).withoutEdit(position.asLong()));
        setDirty();
    }

    /**
     * The external-event hold. F03/F04 remain authoritative for the event itself; F11 only refuses
     * to tear its own camp down while a matching event is live, and only until the bounded stale
     * deadline runs out.
     */
    public void holdCampForEvent(final long campKey) {
        if (!camps.containsKey(campKey)) {
            return;
        }
        camps.put(campKey, camp(campKey).withEventHold(HobgoblinJourneyRules.CAMP_EVENT_HOLD_TICKS));
        setDirty();
    }

    public void closeCamp(final long campKey) {
        final CampRecord removed = camps.remove(campKey);
        if (removed == null) {
            return;
        }
        put(caravan(removed.caravanKey()).withCampKey(Optional.empty()));
        setDirty();
    }

    // ---------------------------------------------------------------- ageing

    /**
     * Ages every remaining-tick counter by exactly one loaded tick. {@code gameTime} is used purely
     * as a same-tick identity token, so four co-loaded caravan members cannot age one shared
     * 200-tick lease four times. No due-ness anywhere is computed against absolute world time.
     */
    public void advanceLoadedTick(final long gameTime) {
        if (lastAdvancedGameTime == gameTime) {
            return;
        }
        lastAdvancedGameTime = gameTime;
        caravans.replaceAll((key, record) -> record.advanced(gameTime));
        // Every caravan that has lost its last member, whether or not its record is still held open
        // by a camp key, so an emptied caravan's camp always reaches teardown.
        final List<Long> emptied = caravans.values().stream()
            .filter(record -> record.members().isEmpty())
            .map(CaravanRecord::key)
            .toList();
        caravans.values().removeIf(record -> record.members().isEmpty() && record.campKey().isEmpty());
        camps.replaceAll((key, record) -> record.advanced(gameTime));
        // A claim is a lease: it ages once per loaded tick and is dropped the moment it lapses, so
        // an abandoned worksite never stays reserved by an entity that will not come back.
        claims.replaceAll((id, record) -> record.aged());
        claims.values().removeIf(record -> HobgoblinJourneyRules.isDue(record.remainingLeaseTicks()));
        emptied.forEach(key -> {
            // A camp whose caravan is gone becomes tear-down eligible rather than an orphan record.
            camps.values().stream()
                .filter(record -> record.caravanKey() == key)
                .map(CampRecord::key)
                .toList()
                .forEach(campKey -> camps.put(campKey, camp(campKey).withPhase(CampPhase.EXPIRE)));
        });
        setDirty();
    }

    /** GameTest-only reset of one caravan and its camp. Never called from production code. */
    public void clearForGameTest(final long caravanKey) {
        camps.values().stream()
            .filter(record -> record.caravanKey() == caravanKey)
            .map(CampRecord::key)
            .toList()
            .forEach(camps::remove);
        caravans.remove(caravanKey);
        claims.clear();
        lastAdvancedGameTime = Long.MIN_VALUE;
        setDirty();
    }

    private void put(final CaravanRecord record) {
        caravans.put(record.key(), record.normalized());
        setDirty();
    }

    private boolean evictOneEmptyCaravan() {
        return caravans.values().stream()
            .filter(record -> record.members().isEmpty() && record.campKey().isEmpty())
            .min(Comparator.comparingLong(CaravanRecord::key))
            .map(record -> {
                caravans.remove(record.key());
                setDirty();
                return true;
            })
            .orElse(false);
    }

    // ================================================================ records

    public record MemberRecord(UUID id, int remainingTicks) {
        public MemberRecord {
            remainingTicks = HobgoblinJourneyRules.clampRemaining(
                remainingTicks, HobgoblinJourneyRules.MEMBER_EXPIRY_TICKS
            );
        }

        MemberRecord aged() {
            return new MemberRecord(id, Math.max(0, remainingTicks - 1));
        }
    }

    public record CaravanRecord(
        long key,
        int schemaVersion,
        List<MemberRecord> members,
        String encodedLeader,
        long encodedWaypoint,
        long encodedCampKey,
        int leaderStabilizeTicks,
        long lastAdvancedGameTime
    ) {
        public CaravanRecord {
            members = members == null ? List.of() : List.copyOf(
                members.stream().limit(HobgoblinJourneyRules.MAX_CARAVAN_MEMBERS).toList()
            );
            encodedLeader = encodedLeader == null ? "" : encodedLeader;
            leaderStabilizeTicks = HobgoblinJourneyRules.clampRemaining(
                leaderStabilizeTicks, HobgoblinJourneyRules.LEADER_STABILIZE_TICKS
            );
            schemaVersion = HobgoblinJourneyRules.DATA_SCHEMA_VERSION;
        }

        static CaravanRecord empty(final long key) {
            return new CaravanRecord(
                key, HobgoblinJourneyRules.DATA_SCHEMA_VERSION, List.of(), "",
                Long.MIN_VALUE, Long.MIN_VALUE, 0, Long.MIN_VALUE
            );
        }

        CaravanRecord normalized() {
            return new CaravanRecord(key, schemaVersion, members, encodedLeader, encodedWaypoint,
                encodedCampKey, leaderStabilizeTicks, lastAdvancedGameTime);
        }

        public Optional<UUID> leader() {
            if (encodedLeader.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(encodedLeader));
            } catch (final IllegalArgumentException malformed) {
                return Optional.empty();
            }
        }

        public Optional<BlockPos> waypoint() {
            return encodedWaypoint == Long.MIN_VALUE
                ? Optional.empty()
                : Optional.of(BlockPos.of(encodedWaypoint));
        }

        public Optional<Long> campKey() {
            return encodedCampKey == Long.MIN_VALUE ? Optional.empty() : Optional.of(encodedCampKey);
        }

        public boolean hasMember(final UUID member) {
            return members.stream().anyMatch(record -> record.id().equals(member));
        }

        CaravanRecord withMember(final UUID member) {
            final List<MemberRecord> updated = new ArrayList<>(members);
            updated.add(new MemberRecord(member, HobgoblinJourneyRules.MEMBER_EXPIRY_TICKS));
            return new CaravanRecord(key, schemaVersion, updated, encodedLeader, encodedWaypoint,
                encodedCampKey, leaderStabilizeTicks, lastAdvancedGameTime);
        }

        CaravanRecord withRefreshedMember(final UUID member) {
            final List<MemberRecord> updated = members.stream()
                .map(record -> record.id().equals(member)
                    ? new MemberRecord(member, HobgoblinJourneyRules.MEMBER_EXPIRY_TICKS)
                    : record)
                .toList();
            return new CaravanRecord(key, schemaVersion, updated, encodedLeader, encodedWaypoint,
                encodedCampKey, leaderStabilizeTicks, lastAdvancedGameTime);
        }

        CaravanRecord withoutMember(final UUID member) {
            final List<MemberRecord> updated = members.stream()
                .filter(record -> !record.id().equals(member))
                .toList();
            final String leader = encodedLeader.equals(member.toString()) ? "" : encodedLeader;
            return new CaravanRecord(key, schemaVersion, updated, leader, encodedWaypoint,
                encodedCampKey, leaderStabilizeTicks, lastAdvancedGameTime);
        }

        CaravanRecord withLeader(final Optional<UUID> updated) {
            return new CaravanRecord(key, schemaVersion, members,
                updated == null || updated.isEmpty() ? "" : updated.get().toString(),
                encodedWaypoint, encodedCampKey, leaderStabilizeTicks, lastAdvancedGameTime);
        }

        CaravanRecord withLeaderStabilize(final int ticks) {
            return new CaravanRecord(key, schemaVersion, members, encodedLeader, encodedWaypoint,
                encodedCampKey, ticks, lastAdvancedGameTime);
        }

        CaravanRecord withWaypoint(final BlockPos waypoint) {
            return new CaravanRecord(key, schemaVersion, members, encodedLeader,
                waypoint == null ? Long.MIN_VALUE : waypoint.asLong(), encodedCampKey,
                leaderStabilizeTicks, lastAdvancedGameTime);
        }

        CaravanRecord withCampKey(final Optional<Long> updated) {
            return new CaravanRecord(key, schemaVersion, members, encodedLeader, encodedWaypoint,
                updated == null || updated.isEmpty() ? Long.MIN_VALUE : updated.get(),
                leaderStabilizeTicks, lastAdvancedGameTime);
        }

        CaravanRecord advanced(final long gameTime) {
            if (lastAdvancedGameTime == gameTime) {
                return this;
            }
            final List<MemberRecord> aged = members.stream()
                .map(MemberRecord::aged)
                .filter(record -> !HobgoblinJourneyRules.isDue(record.remainingTicks()))
                .toList();
            return new CaravanRecord(key, schemaVersion, aged, encodedLeader, encodedWaypoint,
                encodedCampKey, Math.max(0, leaderStabilizeTicks - 1), gameTime);
        }
    }

    /** One owned placement. Only a position and the placed block identity are ever stored. */
    public record CampEdit(long encodedPosition, String placedBlockId) {
        public BlockPos position() {
            return BlockPos.of(encodedPosition);
        }
    }

    public record CampRecord(
        long key,
        long caravanKey,
        long encodedAnchor,
        String encodedPhase,
        int expiryRemainingTicks,
        int eventHoldRemainingTicks,
        List<CampEdit> journal,
        int reservedDirt,
        int reservedLogs,
        long lastAdvancedGameTime
    ) {
        public CampRecord {
            encodedPhase = encodedPhase == null ? CampPhase.NONE.name() : encodedPhase;
            expiryRemainingTicks = HobgoblinJourneyRules.clampRemaining(
                expiryRemainingTicks, HobgoblinJourneyRules.CAMP_EXPIRY_TICKS
            );
            eventHoldRemainingTicks = HobgoblinJourneyRules.clampRemaining(
                eventHoldRemainingTicks, HobgoblinJourneyRules.CAMP_EVENT_HOLD_TICKS
            );
            journal = journal == null ? List.of() : List.copyOf(
                journal.stream().limit(HobgoblinJourneyRules.CAMP_MAX_EDITS).toList()
            );
            reservedDirt = Math.clamp(reservedDirt, 0, HobgoblinJourneyRules.CAMP_DIRT_COST);
            reservedLogs = Math.clamp(reservedLogs, 0, HobgoblinJourneyRules.CAMP_LOG_COST);
        }

        static CampRecord empty(final long key) {
            return new CampRecord(key, Long.MIN_VALUE, Long.MIN_VALUE, CampPhase.NONE.name(),
                0, 0, List.of(), 0, 0, Long.MIN_VALUE);
        }

        CampRecord normalized() {
            return new CampRecord(key, caravanKey, encodedAnchor, encodedPhase, expiryRemainingTicks,
                eventHoldRemainingTicks, journal, reservedDirt, reservedLogs, lastAdvancedGameTime);
        }

        public CampPhase phase() {
            for (final CampPhase candidate : CampPhase.values()) {
                if (candidate.name().equalsIgnoreCase(encodedPhase)) {
                    return candidate;
                }
            }
            return CampPhase.NONE;
        }

        public Optional<BlockPos> anchor() {
            return encodedAnchor == Long.MIN_VALUE
                ? Optional.empty()
                : Optional.of(BlockPos.of(encodedAnchor));
        }

        public boolean present() {
            return anchor().isPresent() && phase() != CampPhase.NONE;
        }

        public boolean eventHeld() {
            return !HobgoblinJourneyRules.isDue(eventHoldRemainingTicks);
        }

        CampRecord withPhase(final CampPhase phase) {
            return new CampRecord(key, caravanKey, encodedAnchor, phase.name(), expiryRemainingTicks,
                eventHoldRemainingTicks, journal, reservedDirt, reservedLogs, lastAdvancedGameTime);
        }

        CampRecord withEventHold(final int ticks) {
            return new CampRecord(key, caravanKey, encodedAnchor, encodedPhase, expiryRemainingTicks,
                ticks, journal, reservedDirt, reservedLogs, lastAdvancedGameTime);
        }

        CampRecord withEdit(final CampEdit edit) {
            final List<CampEdit> updated = new ArrayList<>(journal);
            updated.add(edit);
            return new CampRecord(key, caravanKey, encodedAnchor, encodedPhase, expiryRemainingTicks,
                eventHoldRemainingTicks, updated, reservedDirt, reservedLogs, lastAdvancedGameTime);
        }

        CampRecord withoutEdit(final long encodedPosition) {
            final List<CampEdit> updated = journal.stream()
                .filter(edit -> edit.encodedPosition() != encodedPosition)
                .toList();
            return new CampRecord(key, caravanKey, encodedAnchor, encodedPhase, expiryRemainingTicks,
                eventHoldRemainingTicks, updated, reservedDirt, reservedLogs, lastAdvancedGameTime);
        }

        CampRecord advanced(final long gameTime) {
            if (lastAdvancedGameTime == gameTime) {
                return this;
            }
            return new CampRecord(key, caravanKey, encodedAnchor, encodedPhase,
                Math.max(0, expiryRemainingTicks - 1), Math.max(0, eventHoldRemainingTicks - 1),
                journal, reservedDirt, reservedLogs, gameTime);
        }
    }

    public record ClaimRecord(
        UUID id,
        String kind,
        UUID claimant,
        long encodedSite,
        int remainingLeaseTicks
    ) {
        public ClaimRecord {
            kind = kind == null ? "" : kind;
            remainingLeaseTicks = HobgoblinJourneyRules.clampRemaining(
                remainingLeaseTicks, HobgoblinJourneyRules.CLAIM_LEASE_TICKS
            );
        }

        ClaimRecord aged() {
            return new ClaimRecord(id, kind, claimant, encodedSite, Math.max(0, remainingLeaseTicks - 1));
        }
    }
}
