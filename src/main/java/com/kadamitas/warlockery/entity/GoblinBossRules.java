package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Optional;

public final class GoblinBossRules {
    private GoblinBossRules() {
    }

    public static boolean isBoss(final CreatureKind kind) {
        return combatProfile(kind).isPresent();
    }

    public static Optional<CombatProfile> combatProfile(final CreatureKind kind) {
        return switch (kind) {
            case FORGEWARDEN -> Optional.of(new CombatProfile(400.0, 11.0, 8.0, 0.32));
            case STONEBROKER -> Optional.of(new CombatProfile(400.0, 9.0, 6.0, 0.3));
            default -> Optional.empty();
        };
    }

    public static Optional<CreatureKind> counterpart(final CreatureKind kind) {
        return switch (kind) {
            case FORGEWARDEN -> Optional.of(CreatureKind.STONEBROKER);
            case STONEBROKER -> Optional.of(CreatureKind.FORGEWARDEN);
            default -> Optional.empty();
        };
    }

    public static float pairedDamageMultiplier(final double distanceSquared) {
        if (distanceSquared <= 36.0) {
            return 0.2F;
        }
        if (distanceSquared <= 81.0) {
            return 0.5F;
        }
        if (distanceSquared <= 256.0) {
            return 0.8F;
        }
        return 1.0F;
    }

    public static float pairedAttackBonus(final double distanceSquared) {
        if (distanceSquared <= 36.0) {
            return 12.0F;
        }
        if (distanceSquared <= 81.0) {
            return 8.0F;
        }
        if (distanceSquared <= 256.0) {
            return 4.0F;
        }
        return 0.0F;
    }

    public record CombatProfile(double health, double attack, double armor, double speed) {
        public CombatProfile {
            if (health <= 0.0 || attack <= 0.0 || armor < 0.0 || speed <= 0.0) {
                throw new IllegalArgumentException("Goblin boss combat values must be positive");
            }
        }
    }
}
