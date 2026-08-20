/**
 * Opt-in primitives extracted from the mob families, for whoever wants them.
 *
 * <h2>What this is not</h2>
 *
 * <p>Not a framework. Nothing here is a base class, nothing requires registration, and no family is
 * expected to justify not using it. Every primitive is either a free function or a small immutable
 * value that a family holds in a field it already owns. A family with a different perception or
 * movement model, no pathfinding, or a swarm rather than an individual, should hand-roll the twenty
 * lines it actually needs and ignore this package entirely. That is a normal choice, not a
 * deviation.</p>
 *
 * <p>Adopt a piece when it is obviously better than rewriting it. If it is not winning that argument
 * on its own, it is the wrong primitive and the family is right to skip it.</p>
 *
 * <h2>What each piece is for, and what it prevents</h2>
 *
 * <dl>
 *   <dt>{@link com.kadamitas.warlockery.entity.behavior.ScanEnvelope}</dt>
 *   <dd>A budget-constrained box search that still covers its whole envelope. Use it when the read
 *       cap is smaller than the box volume, which is every real case. Prevents the scan that spends
 *       its entire budget near the origin and never evaluates the far envelope, and equally the one
 *       that rotates everything and stops evaluating the entity's own position.</dd>
 *
 *   <dt>{@link com.kadamitas.warlockery.entity.behavior.ReadBudget}</dt>
 *   <dd>A read allowance spent before the value can be judged. Covers block reads and line of sight
 *       traces alike. Prevents the cap that bounds only accepted candidates while rejected ones
 *       silently cost real world reads.</dd>
 *
 *   <dt>{@link com.kadamitas.warlockery.entity.behavior.PhaseTimer}</dt>
 *   <dd>A phase and its remaining ticks, where a running phase with zero ticks left cannot be built.
 *       Prevents the canonical constructor that reconciles a phase away at the moment its timer
 *       expires, so the tick branch that owned ending it never runs and never arms the cooldown,
 *       backoff or anchor clear that ending implies.</dd>
 *
 *   <dt>{@link com.kadamitas.warlockery.entity.behavior.Cadence}</dt>
 *   <dd>A periodic trigger where arming records that work ran, not that it succeeded.</dd>
 *
 *   <dt>{@link com.kadamitas.warlockery.entity.behavior.RouteRequest}</dt>
 *   <dd>Route pacing and failure backoff. Prevents the search that qualifies nothing from leaving
 *       the cadence unarmed and repeating every tick forever.</dd>
 *
 *   <dt>{@link com.kadamitas.warlockery.entity.behavior.PriorityLadder}</dt>
 *   <dd>The hazard over combat over routine ordering, stated once. {@code select} answers the
 *       ranking question and runs nothing, so a family keeps its own tick; {@code dispatch} is
 *       there only if the family would rather hand the branch over.</dd>
 *
 *   <dt>{@link com.kadamitas.warlockery.entity.behavior.Ticks} and
 *       {@link com.kadamitas.warlockery.entity.behavior.Candidates}</dt>
 *   <dd>The small arithmetic and ordering helpers that already exist five to thirteen times each.</dd>
 * </dl>
 *
 * <h2>Adopting one, in a family that already works</h2>
 *
 * <p>Take one primitive at a time and keep the family's own tests as the safety net; they are the
 * only thing that proves the behaviour did not move.</p>
 *
 * <ol>
 *   <li>Replace the family's own helper with the shared one and delete the local copy. Run the
 *       family's suite. If it goes red, the local copy differed from the shared one in a way that
 *       mattered, and that difference is the finding.</li>
 *   <li>For {@code PhaseTimer} specifically, the compact constructor that reconciled the phase has
 *       to come out at the same time, because the timer no longer produces the pair it existed to
 *       clean up. Removing it can expose a second defect where a normaliser elsewhere resets state
 *       that the newly surviving phase depends on, so grep the state class for anything that zeroes
 *       a field the phase reads and check each one deliberately.</li>
 *   <li>Two schedule dialects exist and do not mix. A family storing absolute deadlines against
 *       game time wants {@code Ticks.due} and {@code Ticks.clampDeadline}. A family storing
 *       countdowns advanced only while loaded wants {@code Ticks.decrementLoaded} and
 *       {@code Ticks.clampRemaining}, and {@code RouteRequest}, which is countdown based
 *       throughout. Converting between dialects is a behaviour change, not an extraction.</li>
 *   <li>{@code Ticks.stableOffset} uses the mixing hash. A family that persisted a schedule derived
 *       from the other variant will see its entities restagger once. That changes which tick within
 *       a period an entity fires on and nothing else.</li>
 * </ol>
 *
 * <p>The reachability checker in the test tree's {@code audit} package is the backstop for behaviour
 * that is implemented and tested but never reached from the live tick. It is a backstop rather than
 * the defence: prefer wiring a behaviour so its absence is visible, and run the checker to catch
 * what slipped through. It reports three verdicts and the third matters, because a member it cannot
 * resolve is reported as unresolved rather than as clean.</p>
 */
package com.kadamitas.warlockery.entity.behavior;
