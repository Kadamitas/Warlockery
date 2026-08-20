package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinPatronRules.DirectiveKind;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * The single immutable outbound boundary between an F12 patron and the F10 Goblin enclave or F11
 * Hobgoblin caravan.
 *
 * <p>This is a value, not a callback and not an owner reference. It carries no path, entity,
 * inventory, container, effect instance, mutable collection, or world handle, so a recipient can
 * store the summary, decide for itself whether to accept it, and keep complete ownership of its own
 * action, target, effect, and navigation. A patron publishes at most one current directive; a
 * recipient queries only loaded local patrons on its own cadence.</p>
 *
 * <p>Expiry is a remaining loaded-tick count carried by the publishing patron's state, so an
 * unresolvable or elapsed directive simply becomes absent. No global search, no cross-dimension
 * instruction, and no forced target ever leaves this type.</p>
 */
public record GoblinPatronDirective(
    UUID patron,
    CreatureKind patronKind,
    long authorityEpoch,
    long resultEpoch,
    DirectiveKind result,
    String dimension,
    BlockPos anchor,
    Optional<UUID> challenger,
    long createdGameTime,
    long expiresGameTime
) {
    public GoblinPatronDirective {
        patron = Objects.requireNonNull(patron, "patron");
        patronKind = Objects.requireNonNull(patronKind, "patronKind");
        if (!GoblinPatronRules.isPatron(patronKind)) {
            throw new IllegalArgumentException("Only Stonebroker and Forgewarden publish directives");
        }
        result = Objects.requireNonNull(result, "result");
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("A directive needs its source dimension");
        }
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        challenger = Objects.requireNonNull(challenger, "challenger");
        authorityEpoch = Math.max(0L, authorityEpoch);
        resultEpoch = Math.max(0L, resultEpoch);
        createdGameTime = Math.max(0L, createdGameTime);
        expiresGameTime = Math.max(createdGameTime, expiresGameTime);
    }

    /** True while the directive is still inside its own declared window in its own dimension. */
    public boolean valid(final String currentDimension, final long gameTime) {
        return dimension.equals(currentDimension) && gameTime < expiresGameTime;
    }

    /**
     * The exact preference a Goblin enclave or a Hobgoblin caravan is free to ignore.
     *
     * <p>{@code FORGE_WARD} is published by Forgewarden and carries no accessor yet: F10's adapter
     * deliberately consumes only the work preference, and the morale interpretation belongs to the
     * single goblin-society reconciliation slice after F11 and F12 have both landed.</p>
     */
    public boolean prefersWork() {
        return result == DirectiveKind.BROKERED_WORK;
    }
}
