package com.kadamitas.warlockery.entity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * The versioned durable state of one spectral familiar.
 *
 * <h2>Constructor reconciliation, classified</h2>
 *
 * <p>Every reconciliation below is the <em>identity</em> shape, which the coordinator's brief names
 * as the legitimate one: a dependent field is meaningless without the identity it hangs off, so the
 * two are never allowed to disagree.</p>
 *
 * <ol>
 *   <li>A guide position without its dimension is not a place, and a dimension with no position in
 *       it is noise.</li>
 *   <li>An episode is <em>identified</em> by the guide block it opened for and the sample identity
 *       it opened with. Without both, there is no episode, so the phase is {@link
 *       SpectralFamiliarRules.Phase#DORMANT} and its deadline is zero. The converse holds too: a
 *       dormant familiar holds no guide identity.</li>
 *   <li>A defence lease with no defender is not a lease.</li>
 * </ol>
 *
 * <p>There is no <em>timer</em> reconciliation anywhere, and that absence is load bearing. This
 * constructor never says "the deadline passed, therefore the phase ended". Deciding a phase ended
 * also arms the guide cooldown, clears the frozen guide identity and records the survey epoch, so a
 * constructor that quietly zeroed the phase would steal all three from the tick branch that owns
 * them. Expiry is only ever <em>reported</em>, by {@link #phaseElapsed(long)}, {@link
 * #defenceElapsed(long)}, {@link #defenceReady(long)}, {@link #guideReady(long)} and {@link
 * #surveyDue(long)}, and {@code SpectralFamiliarRuntime.advanceEpisode} is the single place that
 * acts on those reports.</p>
 */
public record SpectralFamiliarState(
    int schemaVersion,
    SpectralFamiliarRules.Phase phase,
    Optional<String> episodeSample,
    Optional<BlockPos> guideBlock,
    Optional<String> guideDimension,
    long phaseEndsAt,
    long guideCooldownUntil,
    boolean signalSpent,
    Optional<UUID> defenceTargetId,
    long defenceLeaseUntil,
    long defenceCooldownUntil,
    AnimalFamiliarRules.SearchOutcome survey,
    long surveyBackoffUntil,
    long nextDriftAt,
    long lastSurveyEpoch
) {

    private static final String KEY_VERSION = "Version";
    private static final String KEY_PHASE = "Phase";
    private static final String KEY_EPISODE_SAMPLE = "EpisodeSample";
    private static final String KEY_GUIDE = "GuideBlock";
    private static final String KEY_GUIDE_DIMENSION = "GuideDimension";
    private static final String KEY_PHASE_ENDS_AT = "PhaseEndsAt";
    private static final String KEY_GUIDE_COOLDOWN = "GuideCooldownUntil";
    private static final String KEY_SIGNAL_SPENT = "SignalSpent";
    private static final String KEY_DEFENCE_TARGET = "DefenceTarget";
    private static final String KEY_DEFENCE_LEASE = "DefenceLeaseUntil";
    private static final String KEY_DEFENCE_COOLDOWN = "DefenceCooldownUntil";
    private static final String KEY_NEXT_SURVEY = "NextSurveyAt";
    private static final String KEY_SURVEY_FAILURES = "SurveyFailures";
    private static final String KEY_SURVEY_BACKOFF = "SurveyBackoffUntil";
    private static final String KEY_LAST_SURVEY_EPOCH = "LastSurveyEpoch";

    public SpectralFamiliarState {
        Objects.requireNonNull(phase, "phase");
        episodeSample = Objects.requireNonNull(episodeSample, "episodeSample");
        guideBlock = Objects.requireNonNull(guideBlock, "guideBlock").map(BlockPos::immutable);
        guideDimension = Objects.requireNonNull(guideDimension, "guideDimension");
        defenceTargetId = Objects.requireNonNull(defenceTargetId, "defenceTargetId");
        survey = Objects.requireNonNull(survey, "survey");

        // 1. A position without its dimension is not a place, and the converse.
        if (guideBlock.isEmpty()) {
            guideDimension = Optional.empty();
        }
        if (guideDimension.isEmpty()) {
            guideBlock = Optional.empty();
        }

        // 2. An episode is identified by its guide block and the sample it opened with. Without
        //    both there is no episode, so the phase and its deadline are the dependents that go.
        //    This is an identity reconcile and not a timer one: the deadline is never consulted.
        final boolean identified = guideBlock.isPresent() && episodeSample.isPresent();
        if (!identified) {
            phase = SpectralFamiliarRules.Phase.DORMANT;
        }
        if (phase == SpectralFamiliarRules.Phase.DORMANT) {
            episodeSample = Optional.empty();
            guideBlock = Optional.empty();
            guideDimension = Optional.empty();
            phaseEndsAt = 0L;
            signalSpent = false;
        }

        // 3. A lease with no defender is not a lease.
        if (defenceTargetId.isEmpty()) {
            defenceLeaseUntil = 0L;
        }

        phaseEndsAt = Math.max(0L, phaseEndsAt);
        guideCooldownUntil = Math.max(0L, guideCooldownUntil);
        defenceLeaseUntil = Math.max(0L, defenceLeaseUntil);
        defenceCooldownUntil = Math.max(0L, defenceCooldownUntil);
        surveyBackoffUntil = Math.max(0L, surveyBackoffUntil);
        nextDriftAt = Math.max(0L, nextDriftAt);
        lastSurveyEpoch = Math.max(0L, lastSurveyEpoch);
    }

    /** A familiar that has never surveyed, staggered off its own identity so a crowd does not sync. */
    public static SpectralFamiliarState empty(final UUID identity, final long now) {
        final long base = Math.max(0L, now);
        return new SpectralFamiliarState(
            SpectralFamiliarRules.STATE_SCHEMA_VERSION,
            SpectralFamiliarRules.Phase.DORMANT,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0L,
            0L,
            false,
            Optional.empty(),
            0L,
            0L,
            new AnimalFamiliarRules.SearchOutcome(
                base + SpectralFamiliarRules.stableOffset(
                    identity, SpectralFamiliarRules.SURVEY_INTERVAL_TICKS),
                0
            ),
            0L,
            0L,
            0L
        );
    }

    // ---- expiry is reported, never acted on here ----

    /** Whether the running phase's deadline has passed. Reported only. */
    public boolean phaseElapsed(final long now) {
        return phase != SpectralFamiliarRules.Phase.DORMANT && now >= phaseEndsAt;
    }

    /** Whether the defensive lease has run out. Reported only. */
    public boolean defenceElapsed(final long now) {
        return defenceTargetId.isPresent() && now >= defenceLeaseUntil;
    }

    /** Whether a new defensive lease may be taken. One intercept per window, never a chain. */
    public boolean defenceReady(final long now) {
        return now >= defenceCooldownUntil;
    }

    /** Whether the post-episode cooldown has elapsed. */
    public boolean guideReady(final long now) {
        return now >= guideCooldownUntil;
    }

    /** Whether the bounded survey cadence is due. */
    public boolean surveyDue(final long now) {
        return now >= survey.nextDueAt();
    }

    /** Whether three fruitless surveys in a row are currently holding a backoff window open. */
    public boolean surveyBackedOff(final long now) {
        return SpectralFamiliarRules.surveyBackedOff(now, surveyBackoffUntil);
    }

    public boolean episodeRunning() {
        return phase != SpectralFamiliarRules.Phase.DORMANT;
    }

    // ---- withers ----

    public SpectralFamiliarState withPhase(
        final SpectralFamiliarRules.Phase next,
        final long endsAt
    ) {
        return new SpectralFamiliarState(schemaVersion, next, episodeSample, guideBlock,
            guideDimension, endsAt, guideCooldownUntil, signalSpent, defenceTargetId,
            defenceLeaseUntil, defenceCooldownUntil, survey, surveyBackoffUntil, nextDriftAt,
            lastSurveyEpoch);
    }

    public SpectralFamiliarState withEpisode(
        final SpectralFamiliarRules.Phase next,
        final long endsAt,
        final Optional<String> sample,
        final Optional<BlockPos> block,
        final Optional<String> dimension
    ) {
        return new SpectralFamiliarState(schemaVersion, next, sample, block, dimension, endsAt,
            guideCooldownUntil, signalSpent, defenceTargetId, defenceLeaseUntil,
            defenceCooldownUntil, survey, surveyBackoffUntil, nextDriftAt, lastSurveyEpoch);
    }

    /**
     * Ends the episode and pays everything ending it implies in one place: the guide identity is
     * released, the phase goes dormant, the cooldown is armed and the epoch is stamped.
     */
    public SpectralFamiliarState withEpisodeEnded(final long now, final long cooldownUntil) {
        return new SpectralFamiliarState(schemaVersion, SpectralFamiliarRules.Phase.DORMANT,
            Optional.empty(), Optional.empty(), Optional.empty(), 0L, cooldownUntil, false,
            defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, survey, surveyBackoffUntil,
            nextDriftAt, Math.max(0L, now));
    }

    /** Test seam support: opens the post-episode cooldown window without touching anything else. */
    public SpectralFamiliarState withGuideCooldown(final long cooldownUntil) {
        return new SpectralFamiliarState(schemaVersion, phase, episodeSample, guideBlock,
            guideDimension, phaseEndsAt, cooldownUntil, signalSpent, defenceTargetId,
            defenceLeaseUntil, defenceCooldownUntil, survey, surveyBackoffUntil, nextDriftAt,
            lastSurveyEpoch);
    }

    public SpectralFamiliarState withSignalSpent() {
        return new SpectralFamiliarState(schemaVersion, phase, episodeSample, guideBlock,
            guideDimension, phaseEndsAt, guideCooldownUntil, true, defenceTargetId,
            defenceLeaseUntil, defenceCooldownUntil, survey, surveyBackoffUntil, nextDriftAt,
            lastSurveyEpoch);
    }

    public SpectralFamiliarState withDefence(
        final Optional<UUID> target,
        final long leaseUntil,
        final long cooldownUntil
    ) {
        return new SpectralFamiliarState(schemaVersion, phase, episodeSample, guideBlock,
            guideDimension, phaseEndsAt, guideCooldownUntil, signalSpent, target, leaseUntil,
            cooldownUntil, survey, surveyBackoffUntil, nextDriftAt, lastSurveyEpoch);
    }

    public SpectralFamiliarState withSurvey(
        final AnimalFamiliarRules.SearchOutcome outcome,
        final long backoffUntil
    ) {
        return new SpectralFamiliarState(schemaVersion, phase, episodeSample, guideBlock,
            guideDimension, phaseEndsAt, guideCooldownUntil, signalSpent, defenceTargetId,
            defenceLeaseUntil, defenceCooldownUntil, outcome, backoffUntil, nextDriftAt,
            lastSurveyEpoch);
    }

    public SpectralFamiliarState withDrift(final long nextAt) {
        return new SpectralFamiliarState(schemaVersion, phase, episodeSample, guideBlock,
            guideDimension, phaseEndsAt, guideCooldownUntil, signalSpent, defenceTargetId,
            defenceLeaseUntil, defenceCooldownUntil, survey, surveyBackoffUntil, nextAt,
            lastSurveyEpoch);
    }

    // ---- codec ----

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_VERSION, schemaVersion);
        tag.putString(KEY_PHASE, phase.name().toLowerCase(Locale.ROOT));
        episodeSample.ifPresent(sample -> tag.putString(KEY_EPISODE_SAMPLE, sample));
        guideBlock.ifPresent(position -> tag.putLong(KEY_GUIDE, position.asLong()));
        guideDimension.ifPresent(dimension -> tag.putString(KEY_GUIDE_DIMENSION, dimension));
        tag.putLong(KEY_PHASE_ENDS_AT, phaseEndsAt);
        tag.putLong(KEY_GUIDE_COOLDOWN, guideCooldownUntil);
        tag.putBoolean(KEY_SIGNAL_SPENT, signalSpent);
        defenceTargetId.ifPresent(id -> tag.putString(KEY_DEFENCE_TARGET, id.toString()));
        tag.putLong(KEY_DEFENCE_LEASE, defenceLeaseUntil);
        tag.putLong(KEY_DEFENCE_COOLDOWN, defenceCooldownUntil);
        tag.putLong(KEY_NEXT_SURVEY, survey.nextDueAt());
        tag.putInt(KEY_SURVEY_FAILURES, survey.consecutiveFailures());
        tag.putLong(KEY_SURVEY_BACKOFF, surveyBackoffUntil);
        tag.putLong(KEY_LAST_SURVEY_EPOCH, lastSurveyEpoch);
        return tag;
    }

    /**
     * The reload seam.
     *
     * <p>Volatile accumulators are reset and open backoff windows are <em>preserved</em>, which is
     * the pairing the brief names. Reset: the phase, the frozen guide identity, the live defence
     * target and its lease, the drift pacing and the per-destination drift failure count. Preserved,
     * clamped to at most one full window from the current clock: the consecutive survey failure
     * count, the survey backoff window and the defence cooldown window. A load emits no signal, no
     * attack, no Haste pulse and no discovery, and it never loops over elapsed ticks.</p>
     */
    public static SpectralFamiliarState read(
        final CompoundTag tag,
        final UUID identity,
        final long now
    ) {
        if (tag.getIntOr(KEY_VERSION, 0) != SpectralFamiliarRules.STATE_SCHEMA_VERSION) {
            return empty(identity, now);
        }
        final long base = Math.max(0L, now);
        // The cadence restaggers from the mixing hash rather than resuming a stored due time, so a
        // familiar that unloads more often than one interval cannot starve its survey and a crowd
        // that reloads together does not survey in lockstep. The failure COUNT does survive, because
        // it is what the preserved backoff window is computed from.
        final AnimalFamiliarRules.SearchOutcome survey = new AnimalFamiliarRules.SearchOutcome(
            base + SpectralFamiliarRules.stableOffset(
                identity, SpectralFamiliarRules.SURVEY_INTERVAL_TICKS),
            Math.clamp(tag.getIntOr(KEY_SURVEY_FAILURES, 0), 0,
                SpectralFamiliarRules.MAX_SURVEY_FAILURES)
        );
        return new SpectralFamiliarState(
            SpectralFamiliarRules.STATE_SCHEMA_VERSION,
            SpectralFamiliarRules.Phase.DORMANT,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0L,
            SpectralFamiliarRules.clampDeadline(
                tag.getLongOr(KEY_GUIDE_COOLDOWN, 0L), now,
                SpectralFamiliarRules.GUIDE_COOLDOWN_TICKS),
            false,
            Optional.empty(),
            0L,
            SpectralFamiliarRules.clampDeadline(
                tag.getLongOr(KEY_DEFENCE_COOLDOWN, 0L), now,
                AnimalFamiliarRules.DEFENSE_LEASE_TICKS),
            survey,
            SpectralFamiliarRules.clampDeadline(
                tag.getLongOr(KEY_SURVEY_BACKOFF, 0L), now,
                AnimalFamiliarRules.ROUTE_BACKOFF_TICKS),
            0L,
            Math.max(0L, tag.getLongOr(KEY_LAST_SURVEY_EPOCH, 0L))
        );
    }
}

