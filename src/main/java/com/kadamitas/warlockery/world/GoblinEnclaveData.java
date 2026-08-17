package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RelationEvent;
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
 * The only strategic Goblin enclave authority in one dimension. Every lookup is a direct
 * region-and-kind key lookup, never a dimension scan, and every list is capped and normalized on
 * both read and write.
 *
 * <p>This is a semantic ownership ledger, not an offscreen simulator: it never spawns, breeds,
 * attacks, trades, moves, mines, gathers, or edits a block, and it stores no material inventory,
 * live entity, live container, path, chunk, or recursive relation. A record holding committed
 * structures is never evicted merely to admit a new enclave; when the dimension cap is reached and
 * no safe provisional record exists, new Goblins simply stay solitary.</p>
 */
public final class GoblinEnclaveData extends SavedData {
    private static final Codec<ClaimRecord> CLAIM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(ClaimRecord::id),
        Codec.STRING.fieldOf("kind").forGetter(claim -> claim.kind().name()),
        UUIDUtil.STRING_CODEC.fieldOf("claimant").forGetter(ClaimRecord::claimant),
        Codec.LONG.optionalFieldOf("site", Long.MIN_VALUE).forGetter(ClaimRecord::encodedSite),
        Codec.INT.optionalFieldOf("lease", 0).forGetter(ClaimRecord::remainingLeaseTicks)
    ).apply(instance, ClaimRecord::decode));
    private static final Codec<MemberRecord> MEMBER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(MemberRecord::id),
        Codec.INT.optionalFieldOf("remaining", GoblinEnclaveRules.MEMBER_EXPIRY_TICKS)
            .forGetter(MemberRecord::remainingTicks)
    ).apply(instance, MemberRecord::new));
    private static final Codec<ThreatRecord> THREAT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(ThreatRecord::id),
        Codec.INT.optionalFieldOf("urgency", 1).forGetter(ThreatRecord::urgency),
        Codec.INT.optionalFieldOf("remaining", 0).forGetter(ThreatRecord::remainingTicks)
    ).apply(instance, ThreatRecord::new));
    private static final Codec<RelationRecord> RELATION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(RelationRecord::id),
        Codec.INT.optionalFieldOf("score", 0).forGetter(RelationRecord::score),
        Codec.INT.optionalFieldOf("age", 0).forGetter(RelationRecord::lastInteractionAgeTicks),
        Codec.INT.optionalFieldOf("remaining", 0).forGetter(RelationRecord::remainingTicks)
    ).apply(instance, RelationRecord::new));
    private static final Codec<EnclaveRecord> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("key").forGetter(EnclaveRecord::key),
        Codec.INT.optionalFieldOf("version", GoblinEnclaveRules.DATA_SCHEMA_VERSION)
            .forGetter(EnclaveRecord::schemaVersion),
        MEMBER_CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(EnclaveRecord::members),
        Codec.LONG.listOf().optionalFieldOf("huts", List.of()).forGetter(EnclaveRecord::huts),
        Codec.LONG.listOf().optionalFieldOf("tunnels", List.of()).forGetter(EnclaveRecord::tunnels),
        Codec.INT.optionalFieldOf("edits", 0).forGetter(EnclaveRecord::ownedEdits),
        CLAIM_CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(EnclaveRecord::claims),
        THREAT_CODEC.listOf().optionalFieldOf("threats", List.of()).forGetter(EnclaveRecord::threats),
        RELATION_CODEC.listOf().optionalFieldOf("relations", List.of()).forGetter(EnclaveRecord::relations),
        Codec.INT.optionalFieldOf("provisional", GoblinEnclaveRules.PROVISIONAL_EXPIRY_TICKS)
            .forGetter(EnclaveRecord::provisionalRemainingTicks),
        Codec.LONG.optionalFieldOf("advanced", Long.MIN_VALUE)
            .forGetter(EnclaveRecord::lastAdvancedGameTime)
    ).apply(instance, EnclaveRecord::new));
    private static final Codec<GoblinEnclaveData> CODEC = RECORD_CODEC.listOf()
        .optionalFieldOf("enclaves", List.of())
        .xmap(GoblinEnclaveData::new, GoblinEnclaveData::entries)
        .codec();
    public static final SavedDataType<GoblinEnclaveData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "goblin_enclave"),
        GoblinEnclaveData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, EnclaveRecord> enclaves;

    public GoblinEnclaveData() {
        this(List.of());
    }

    private GoblinEnclaveData(final List<EnclaveRecord> loaded) {
        enclaves = new HashMap<>();
        loaded.stream()
            .filter(record -> record.schemaVersion() <= GoblinEnclaveRules.DATA_SCHEMA_VERSION)
            .map(EnclaveRecord::normalized)
            .limit(GoblinEnclaveRules.MAX_RECORDS_PER_DIMENSION)
            .forEach(record -> enclaves.put(record.key(), record));
    }

    public static GoblinEnclaveData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---------------------------------------------------------------- lookup

    /** Direct key lookup. Absent keys read as an empty record without allocating storage. */
    public EnclaveRecord record(final long key) {
        return enclaves.getOrDefault(key, EnclaveRecord.empty(key));
    }

    public int recordCount() {
        return enclaves.size();
    }

    public boolean exists(final long key) {
        return enclaves.containsKey(key);
    }

    /**
     * Admits a new enclave only when the dimension cap allows it, evicting at most one expired
     * provisional record that owns no structure, member, or claim. A structure-bearing record is
     * never evicted; the caller stays solitary instead.
     */
    public boolean admit(final long key) {
        if (enclaves.containsKey(key)) {
            return true;
        }
        if (enclaves.size() >= GoblinEnclaveRules.MAX_RECORDS_PER_DIMENSION && !evictOneProvisional()) {
            return false;
        }
        put(EnclaveRecord.empty(key));
        return true;
    }

    private boolean evictOneProvisional() {
        return enclaves.values().stream()
            .filter(EnclaveRecord::safelyEvictable)
            .min(Comparator.comparingLong(EnclaveRecord::key))
            .map(record -> {
                enclaves.remove(record.key());
                setDirty();
                return true;
            })
            .orElse(false);
    }

    // ---------------------------------------------------------------- membership

    /**
     * Joining is also the membership heartbeat: a loaded member re-joins on its own reconciliation
     * cadence, which refreshes its expiry. A member that stops re-joining because it died, was
     * removed, or unloaded ages out of the record instead of inflating the population forever.
     */
    public boolean joinEnclave(final long key, final UUID member) {
        if (member == null || !admit(key)) {
            return false;
        }
        final EnclaveRecord current = record(key);
        if (current.hasMember(member)) {
            put(current.withRefreshedMember(member));
            return true;
        }
        if (current.members().size() >= GoblinEnclaveRules.MAX_MEMBERS) {
            return false;
        }
        put(current.withMember(member));
        return true;
    }

    /** Immediate departure on death or removal; also releases every lease that member held. */
    public void leaveEnclave(final long key, final UUID member) {
        if (member == null) {
            return;
        }
        final EnclaveRecord current = record(key);
        if (!current.hasMember(member)) {
            releaseClaimsOf(key, member);
            return;
        }
        put(current.withoutMember(member).withoutClaimsOfMember(member));
    }

    public int population(final long key) {
        return record(key).members().size();
    }

    public boolean hasMember(final long key, final UUID member) {
        return record(key).hasMember(member);
    }

    // ---------------------------------------------------------------- claims

    /**
     * Grants at most one lease per claimant and at most one per worksite, within the per-enclave
     * claim cap. Returns empty rather than replacing an existing claim.
     */
    public Optional<UUID> claim(
        final long key,
        final Intent kind,
        final UUID claimant,
        final Optional<BlockPos> site
    ) {
        if (claimant == null || kind == null || !admit(key)) {
            return Optional.empty();
        }
        final EnclaveRecord current = record(key);
        final long encodedSite = site.map(BlockPos::asLong).orElse(Long.MIN_VALUE);
        final boolean claimantHolds = current.claims().stream()
            .anyMatch(existing -> existing.claimant().equals(claimant));
        final boolean siteClaimed = encodedSite != Long.MIN_VALUE && current.claims().stream()
            .anyMatch(existing -> existing.encodedSite() == encodedSite);
        if (!GoblinEnclaveRules.canGrantClaim(current.claims().size(), claimantHolds, siteClaimed)) {
            return Optional.empty();
        }
        final UUID id = UUID.nameUUIDFromBytes(
            (key + ":" + kind.name() + ":" + claimant + ":" + encodedSite).getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )
        );
        put(current.withClaim(new ClaimRecord(
            id, kind, claimant, encodedSite, GoblinEnclaveRules.leaseTicks()
        )));
        return Optional.of(id);
    }

    public void releaseClaim(final long key, final UUID claimId) {
        final EnclaveRecord current = record(key);
        if (claimId == null || current.claims().stream().noneMatch(claim -> claim.id().equals(claimId))) {
            return;
        }
        put(current.withoutClaim(claimId));
    }

    public void releaseClaimsOf(final long key, final UUID claimant) {
        final EnclaveRecord current = record(key);
        if (claimant == null
            || current.claims().stream().noneMatch(claim -> claim.claimant().equals(claimant))) {
            return;
        }
        put(current.withoutClaimsOf(claimant));
    }

    public boolean holdsClaim(final long key, final UUID claimId) {
        return record(key).claims().stream()
            .anyMatch(claim -> claim.id().equals(claimId) && !claim.expired());
    }

    public int defenderCount(final long key) {
        return (int) record(key).claims().stream().filter(claim -> claim.kind().isAlarmIntent()).count();
    }

    /**
     * Advances every remaining-tick counter by exactly one loaded tick and drops what has expired.
     * Only a loaded member's tick calls this, so an unloaded region never ages. {@code gameTime} is
     * used purely as a same-tick identity token so eight co-loaded members cannot age one shared
     * lease eight times; no due-ness anywhere is ever computed against absolute world time.
     */
    public void advanceLoadedTick(final long key, final long gameTime) {
        if (!enclaves.containsKey(key)) {
            return;
        }
        final EnclaveRecord current = record(key);
        if (current.lastAdvancedGameTime() == gameTime) {
            return;
        }
        put(current.advanced(gameTime));
    }

    // ---------------------------------------------------------------- structures

    public boolean reserveHut(final long key, final BlockPos center) {
        if (center == null || !admit(key)) {
            return false;
        }
        final EnclaveRecord current = record(key);
        if (!GoblinEnclaveRules.canReserveHut(current.huts().size(), current.ownedEdits())
            || current.hasStructureNear(current.huts(), center, 8.0D)) {
            return false;
        }
        put(current.withHut(center));
        return true;
    }

    public boolean reserveTunnel(final long key, final BlockPos entrance, final int edits) {
        if (entrance == null || !admit(key)) {
            return false;
        }
        final EnclaveRecord current = record(key);
        if (!GoblinEnclaveRules.canReserveTunnel(current.tunnels().size(), current.ownedEdits(), edits)
            || current.hasStructureNear(current.tunnels(), entrance, 48.0D)) {
            return false;
        }
        put(current.withTunnel(entrance, edits));
        return true;
    }

    /**
     * Un-reserves a hut whose world transaction rolled back, returning both the hut slot and the
     * exact edit budget it charged. Without this, one rolled-back hut would permanently burn a slot
     * and thirty-two edits of the lifetime budget for a hut that never existed.
     */
    public void releaseHut(final long key, final BlockPos center) {
        final EnclaveRecord current = record(key);
        if (center == null || !current.huts().contains(center.asLong())) {
            return;
        }
        put(current.withoutHut(center));
    }

    /** Un-reserves a tunnel whose world transaction rolled back, refunding its exact edit charge. */
    public void releaseTunnel(final long key, final BlockPos entrance, final int edits) {
        final EnclaveRecord current = record(key);
        if (entrance == null || !current.tunnels().contains(entrance.asLong())) {
            return;
        }
        put(current.withoutTunnel(entrance, edits));
    }

    /** Refunds a loose edit charge whose world transaction rolled back. */
    public void releaseEdits(final long key, final int edits) {
        final EnclaveRecord current = record(key);
        if (edits <= 0 || current.ownedEdits() <= 0) {
            return;
        }
        put(current.withEdits(-Math.min(edits, current.ownedEdits())));
    }

    public boolean recordEdits(final long key, final int edits) {
        if (edits <= 0 || !admit(key)) {
            return false;
        }
        final EnclaveRecord current = record(key);
        if (!GoblinEnclaveRules.canRecordEdit(current.ownedEdits(), edits)) {
            return false;
        }
        put(current.withEdits(edits));
        return true;
    }

    // ---------------------------------------------------------------- threats and relations

    public void rememberThreat(final long key, final UUID threat, final int urgency) {
        if (threat == null || !admit(key)) {
            return;
        }
        put(record(key).withThreat(new ThreatRecord(threat, urgency, GoblinEnclaveRules.CLAIM_LEASE_TICKS)));
    }

    public List<ThreatRecord> threats(final long key) {
        return record(key).threats();
    }

    public void recordRelation(final long key, final UUID player, final RelationEvent event) {
        if (player == null || event == null || !admit(key)) {
            return;
        }
        put(record(key).withRelation(player, event));
    }

    public int relationScore(final long key, final UUID player) {
        return record(key).relations().stream()
            .filter(relation -> relation.id().equals(player))
            .findFirst()
            .map(RelationRecord::score)
            .orElse(0);
    }

    // ---------------------------------------------------------------- migration

    /**
     * Conservative 1.4 settlement migration. Only the region key and valid bounded hut, tunnel, and
     * edit counts cross over: no member, job, threat, relation, material, or patron fact is
     * invented, duplicates and overflow are deterministically truncated, and an existing new record
     * is never overwritten. Overflow past the dimension cap is refused rather than evicting a
     * structure-bearing record.
     */
    public int migrateFrom(final GoblinSettlementLifeData legacy, final long key) {
        if (legacy == null || enclaves.containsKey(key) || !admit(key)) {
            return 0;
        }
        final GoblinSettlementLifeData.SettlementState source = legacy.state(key);
        final EnclaveRecord migrated = EnclaveRecord.empty(key)
            .withMigratedStructures(source.huts(), source.tunnels(), source.worldEdits());
        put(migrated);
        return migrated.huts().size() + migrated.tunnels().size();
    }

    // ---------------------------------------------------------------- internals

    private void put(final EnclaveRecord updated) {
        enclaves.put(updated.key(), updated.normalized());
        setDirty();
    }

    private List<EnclaveRecord> entries() {
        return List.copyOf(enclaves.values());
    }

    /** Test-only reset so a live fixture can claim a clean key inside a shared world. */
    public void clearForGameTest(final long key) {
        if (enclaves.remove(key) != null) {
            setDirty();
        }
    }

    // ---------------------------------------------------------------- records

    public record MemberRecord(UUID id, int remainingTicks) {
        public MemberRecord {
            id = id == null ? new UUID(0L, 0L) : id;
            remainingTicks = GoblinEnclaveRules.clampRemaining(
                remainingTicks, GoblinEnclaveRules.MEMBER_EXPIRY_TICKS
            );
        }

        static MemberRecord fresh(final UUID id) {
            return new MemberRecord(id, GoblinEnclaveRules.MEMBER_EXPIRY_TICKS);
        }

        public boolean expired() {
            return GoblinEnclaveRules.isDue(remainingTicks);
        }

        MemberRecord advanced() {
            return new MemberRecord(id, remainingTicks - 1);
        }
    }

    public record ClaimRecord(
        UUID id,
        Intent kind,
        UUID claimant,
        long encodedSite,
        int remainingLeaseTicks
    ) {
        public ClaimRecord {
            id = id == null ? new UUID(0L, 0L) : id;
            kind = kind == null ? Intent.IDLE : kind;
            claimant = claimant == null ? new UUID(0L, 0L) : claimant;
            remainingLeaseTicks = GoblinEnclaveRules.clampRemaining(
                remainingLeaseTicks, GoblinEnclaveRules.CLAIM_LEASE_TICKS
            );
        }

        private static ClaimRecord decode(
            final UUID id,
            final String kind,
            final UUID claimant,
            final long site,
            final int lease
        ) {
            Intent parsed = Intent.IDLE;
            for (final Intent candidate : Intent.values()) {
                if (candidate.name().equals(kind)) {
                    parsed = candidate;
                }
            }
            return new ClaimRecord(id, parsed, claimant, site, lease);
        }

        public Optional<BlockPos> site() {
            return encodedSite == Long.MIN_VALUE ? Optional.empty() : Optional.of(BlockPos.of(encodedSite));
        }

        public boolean expired() {
            return GoblinEnclaveRules.claimExpired(remainingLeaseTicks);
        }

        ClaimRecord advanced() {
            return new ClaimRecord(id, kind, claimant, encodedSite, remainingLeaseTicks - 1);
        }
    }

    public record ThreatRecord(UUID id, int urgency, int remainingTicks) {
        public ThreatRecord {
            id = id == null ? new UUID(0L, 0L) : id;
            urgency = Math.clamp(urgency, 0, 3);
            remainingTicks = GoblinEnclaveRules.clampRemaining(
                remainingTicks, GoblinEnclaveRules.CLAIM_LEASE_TICKS
            );
        }

        public boolean expired() {
            return GoblinEnclaveRules.isDue(remainingTicks);
        }

        ThreatRecord advanced() {
            return new ThreatRecord(id, urgency, remainingTicks - 1);
        }
    }

    public record RelationRecord(UUID id, int score, int lastInteractionAgeTicks, int remainingTicks) {
        public RelationRecord {
            id = id == null ? new UUID(0L, 0L) : id;
            score = GoblinEnclaveRules.clampRelation(score);
            lastInteractionAgeTicks = Math.max(0, lastInteractionAgeTicks);
            remainingTicks = GoblinEnclaveRules.clampRemaining(
                remainingTicks, (int) GoblinEnclaveRules.FAR_FUTURE_TICKS
            );
        }

        public boolean expired() {
            return GoblinEnclaveRules.isDue(remainingTicks);
        }

        RelationRecord advanced() {
            return new RelationRecord(id, score, lastInteractionAgeTicks + 1, remainingTicks - 1);
        }
    }

    public record EnclaveRecord(
        long key,
        int schemaVersion,
        List<MemberRecord> members,
        List<Long> huts,
        List<Long> tunnels,
        int ownedEdits,
        List<ClaimRecord> claims,
        List<ThreatRecord> threats,
        List<RelationRecord> relations,
        int provisionalRemainingTicks,
        long lastAdvancedGameTime
    ) {
        public EnclaveRecord {
            members = List.copyOf(members);
            huts = List.copyOf(huts);
            tunnels = List.copyOf(tunnels);
            claims = List.copyOf(claims);
            threats = List.copyOf(threats);
            relations = List.copyOf(relations);
            ownedEdits = Math.clamp(ownedEdits, 0, GoblinEnclaveRules.MAX_OWNED_EDITS);
            provisionalRemainingTicks = GoblinEnclaveRules.clampRemaining(
                provisionalRemainingTicks, GoblinEnclaveRules.PROVISIONAL_EXPIRY_TICKS
            );
            schemaVersion = GoblinEnclaveRules.DATA_SCHEMA_VERSION;
        }

        static EnclaveRecord empty(final long key) {
            return new EnclaveRecord(key, GoblinEnclaveRules.DATA_SCHEMA_VERSION, List.of(), List.of(),
                List.of(), 0, List.of(), List.of(), List.of(),
                GoblinEnclaveRules.PROVISIONAL_EXPIRY_TICKS, Long.MIN_VALUE);
        }

        /** Deterministic truncation: distinct, ordered, and capped on every field. */
        EnclaveRecord normalized() {
            return new EnclaveRecord(
                key,
                GoblinEnclaveRules.DATA_SCHEMA_VERSION,
                members.stream().filter(member -> !member.expired()).distinct()
                    .limit(GoblinEnclaveRules.MAX_MEMBERS).toList(),
                huts.stream().distinct().limit(GoblinEnclaveRules.MAX_HUTS).toList(),
                tunnels.stream().distinct().limit(GoblinEnclaveRules.MAX_TUNNELS).toList(),
                ownedEdits,
                claims.stream().filter(claim -> !claim.expired()).distinct()
                    .limit(GoblinEnclaveRules.MAX_CLAIMS).toList(),
                threats.stream().filter(threat -> !threat.expired()).distinct()
                    .limit(GoblinEnclaveRules.MAX_THREATS).toList(),
                relations.stream().filter(relation -> !relation.expired()).distinct()
                    .limit(GoblinEnclaveRules.MAX_RELATIONS).toList(),
                provisionalRemainingTicks,
                lastAdvancedGameTime
            );
        }

        /** A record owning a structure, member, or claim is never a safe eviction candidate. */
        public boolean safelyEvictable() {
            return huts.isEmpty()
                && tunnels.isEmpty()
                && ownedEdits == 0
                && members.isEmpty()
                && claims.isEmpty()
                && GoblinEnclaveRules.isDue(provisionalRemainingTicks);
        }

        public boolean hasStructureNear(
            final List<Long> encoded,
            final BlockPos position,
            final double radius
        ) {
            return encoded.stream().map(BlockPos::of).anyMatch(site -> site.closerThan(position, radius));
        }

        EnclaveRecord advanced(final long gameTime) {
            final List<MemberRecord> agedMembers = members.stream()
                .map(MemberRecord::advanced)
                .filter(member -> !member.expired())
                .toList();
            final List<UUID> present = agedMembers.stream().map(MemberRecord::id).toList();
            return new EnclaveRecord(key, schemaVersion, agedMembers, huts, tunnels, ownedEdits,
                claims.stream().map(ClaimRecord::advanced)
                    .filter(claim -> !claim.expired() && present.contains(claim.claimant())).toList(),
                threats.stream().map(ThreatRecord::advanced).filter(threat -> !threat.expired()).toList(),
                relations.stream().map(RelationRecord::advanced).filter(relation -> !relation.expired()).toList(),
                committed() ? GoblinEnclaveRules.PROVISIONAL_EXPIRY_TICKS : provisionalRemainingTicks - 1,
                gameTime
            );
        }

        private boolean committed() {
            return !huts.isEmpty() || !tunnels.isEmpty() || ownedEdits > 0 || !members.isEmpty();
        }

        public boolean hasMember(final UUID member) {
            return members.stream().anyMatch(existing -> existing.id().equals(member));
        }

        EnclaveRecord withMember(final UUID member) {
            return copyWith(concat(members, MemberRecord.fresh(member)), huts, tunnels, ownedEdits,
                claims, threats, relations);
        }

        EnclaveRecord withRefreshedMember(final UUID member) {
            return copyWith(
                members.stream()
                    .map(existing -> existing.id().equals(member) ? MemberRecord.fresh(member) : existing)
                    .toList(),
                huts, tunnels, ownedEdits, claims, threats, relations);
        }

        EnclaveRecord withoutMember(final UUID member) {
            return copyWith(members.stream().filter(existing -> !existing.id().equals(member)).toList(),
                huts, tunnels, ownedEdits, claims, threats, relations);
        }

        EnclaveRecord withoutClaimsOfMember(final UUID claimant) {
            return copyWith(members, huts, tunnels, ownedEdits,
                claims.stream().filter(claim -> !claim.claimant().equals(claimant)).toList(),
                threats, relations);
        }

        EnclaveRecord withoutHut(final BlockPos center) {
            return copyWith(members,
                huts.stream().filter(hut -> hut != center.asLong()).toList(), tunnels,
                Math.max(0, ownedEdits - GoblinEnclaveRules.HUT_MAX_EDITS),
                claims, threats, relations);
        }

        EnclaveRecord withoutTunnel(final BlockPos entrance, final int edits) {
            return copyWith(members, huts,
                tunnels.stream().filter(tunnel -> tunnel != entrance.asLong()).toList(),
                Math.max(0, ownedEdits - Math.max(0, edits)), claims, threats, relations);
        }

        EnclaveRecord withClaim(final ClaimRecord claim) {
            return copyWith(members, huts, tunnels, ownedEdits, concat(claims, claim), threats, relations);
        }

        EnclaveRecord withoutClaim(final UUID claimId) {
            return copyWith(members, huts, tunnels, ownedEdits,
                claims.stream().filter(claim -> !claim.id().equals(claimId)).toList(), threats, relations);
        }

        EnclaveRecord withoutClaimsOf(final UUID claimant) {
            return copyWith(members, huts, tunnels, ownedEdits,
                claims.stream().filter(claim -> !claim.claimant().equals(claimant)).toList(),
                threats, relations);
        }

        EnclaveRecord withHut(final BlockPos center) {
            return copyWith(members, concat(huts, center.asLong()), tunnels,
                ownedEdits + GoblinEnclaveRules.HUT_MAX_EDITS, claims, threats, relations);
        }

        EnclaveRecord withTunnel(final BlockPos entrance, final int edits) {
            return copyWith(members, huts, concat(tunnels, entrance.asLong()),
                ownedEdits + edits, claims, threats, relations);
        }

        EnclaveRecord withEdits(final int edits) {
            return copyWith(members, huts, tunnels, Math.max(0, ownedEdits + edits),
                claims, threats, relations);
        }

        EnclaveRecord withThreat(final ThreatRecord threat) {
            final List<ThreatRecord> retained = new ArrayList<>(
                threats.stream().filter(existing -> !existing.id().equals(threat.id())).toList()
            );
            retained.add(threat);
            retained.sort(Comparator.comparingInt(ThreatRecord::urgency).reversed()
                .thenComparing(ThreatRecord::id));
            return copyWith(members, huts, tunnels, ownedEdits, claims,
                retained.stream().limit(GoblinEnclaveRules.MAX_THREATS).toList(), relations);
        }

        EnclaveRecord withRelation(final UUID player, final RelationEvent event) {
            final Optional<RelationRecord> existing = relations.stream()
                .filter(relation -> relation.id().equals(player))
                .findFirst();
            final RelationRecord updated = new RelationRecord(
                player,
                GoblinEnclaveRules.applyRelation(existing.map(RelationRecord::score).orElse(0), event),
                0,
                (int) GoblinEnclaveRules.FAR_FUTURE_TICKS
            );
            final List<RelationRecord> without = relations.stream()
                .filter(relation -> !relation.id().equals(player))
                .toList();
            final Optional<UUID> evicted = GoblinEnclaveRules.relationToEvict(without.stream()
                .map(relation -> new GoblinEnclaveRules.RelationFact(
                    relation.id(), relation.score(), relation.lastInteractionAgeTicks(),
                    relation.remainingTicks()
                ))
                .toList());
            final List<RelationRecord> retained = without.stream()
                .filter(relation -> evicted.map(id -> !relation.id().equals(id)).orElse(true))
                .toList();
            return copyWith(members, huts, tunnels, ownedEdits, claims, threats,
                concat(retained, updated));
        }

        EnclaveRecord withMigratedStructures(
            final List<Long> legacyHuts,
            final List<Long> legacyTunnels,
            final int legacyEdits
        ) {
            return copyWith(
                members,
                legacyHuts.stream().distinct().limit(GoblinEnclaveRules.MAX_HUTS).toList(),
                legacyTunnels.stream().distinct().limit(GoblinEnclaveRules.MAX_TUNNELS).toList(),
                Math.clamp(legacyEdits, 0, GoblinEnclaveRules.MAX_OWNED_EDITS),
                claims, threats, relations
            );
        }

        private EnclaveRecord copyWith(
            final List<MemberRecord> updatedMembers,
            final List<Long> updatedHuts,
            final List<Long> updatedTunnels,
            final int updatedEdits,
            final List<ClaimRecord> updatedClaims,
            final List<ThreatRecord> updatedThreats,
            final List<RelationRecord> updatedRelations
        ) {
            return new EnclaveRecord(key, GoblinEnclaveRules.DATA_SCHEMA_VERSION, updatedMembers,
                updatedHuts, updatedTunnels, updatedEdits, updatedClaims, updatedThreats,
                updatedRelations, provisionalRemainingTicks, lastAdvancedGameTime);
        }

        private static <T> List<T> concat(final List<T> source, final T added) {
            final List<T> combined = new ArrayList<>(source);
            combined.add(added);
            return List.copyOf(combined);
        }
    }
}
