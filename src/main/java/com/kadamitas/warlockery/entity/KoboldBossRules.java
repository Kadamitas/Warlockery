package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Optional;

public final class KoboldBossRules {
    private KoboldBossRules() {
    }

    public static boolean isBoss(final CreatureKind kind) {
        return combatProfile(kind).isPresent();
    }

    public static Optional<CombatProfile> combatProfile(final CreatureKind kind) {
        return switch (kind) {
            case FORGEWARDEN -> Optional.of(new CombatProfile(100.0, 11.0, 8.0, 0.32));
            case STONEBROKER -> Optional.of(new CombatProfile(80.0, 9.0, 6.0, 0.3));
            default -> Optional.empty();
        };
    }

    public record CombatProfile(double health, double attack, double armor, double speed) {
        public CombatProfile {
            if (health <= 0.0 || attack <= 0.0 || armor < 0.0 || speed <= 0.0) {
                throw new IllegalArgumentException("Kobold boss combat values must be positive");
            }
        }
    }
}
