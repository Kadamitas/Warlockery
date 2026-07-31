package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Optional;

public final class ImpContractRules {
    public static final int BINDING_LEVEL_COST = 25;

    private ImpContractRules() {
    }

    public static Decision decide(
        final boolean contract,
        final boolean ownedByCaster,
        final boolean ownedByAnother,
        final int favor,
        final int requiredFavor,
        final boolean targetInDimension
    ) {
        if (!contract) {
            return new Decision(false, Diagnostic.WRONG_ITEM);
        }
        if (ownedByAnother) {
            return new Decision(false, Diagnostic.BOUND_ELSEWHERE);
        }
        if (!ownedByCaster) {
            return new Decision(false, Diagnostic.BINDING_REQUIRED);
        }
        if (favor < requiredFavor) {
            return new Decision(false, Diagnostic.IMP_UNIMPRESSED);
        }
        if (!targetInDimension) {
            return new Decision(false, Diagnostic.TARGET_OTHER_DIMENSION);
        }
        return new Decision(true, Diagnostic.READY);
    }

    public enum Spell implements StringIdentified {
        FIERY_TOUCH("ingredient_contract_fiery_touch", 1),
        EVAPORATION("ingredient_contract_evaporate", 2),
        FIRE_TOLERANCE("ingredient_contract_resist_fire", 2),
        MELTING_TOUCH("ingredient_contract_smelting", 3),
        LIVING_FLAME("ingredient_contract_blaze", 4),
        TORMENT("ingredient_contract_torment", 6);

        private static final EnumLookup<Spell> LOOKUP = EnumLookup.create("imp contract spell", values());
        private final String itemId;
        private final int favor;

        Spell(final String itemId, final int favor) {
            this.itemId = itemId;
            this.favor = favor;
        }

        public String itemId() {
            return itemId;
        }

        public String id() {
            return itemId;
        }

        public int favor() {
            return favor;
        }

        public static Optional<Spell> forItem(final String itemId) {
            return LOOKUP.find(itemId);
        }
    }

    public enum Diagnostic {
        WRONG_ITEM("wrong_item"),
        BOUND_ELSEWHERE("bound_elsewhere"),
        BINDING_REQUIRED("binding_required"),
        IMP_UNIMPRESSED("imp_unimpressed"),
        TARGET_OTHER_DIMENSION("target_other_dimension"),
        READY("ready");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Decision(boolean success, Diagnostic diagnostic) {
        public String messageKey() {
            return "message.warlockery.imp_contract." + diagnostic.id();
        }
    }
}
