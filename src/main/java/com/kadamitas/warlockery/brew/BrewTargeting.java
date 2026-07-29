package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.Optional;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class BrewTargeting {
    private BrewTargeting() {
    }

    public static boolean matches(final LivingEntity entity, final Target target) {
        final boolean tagged = entity.typeHolder().is(tag(target));
        final SupernaturalForm form = entity instanceof Player player
            ? SupernaturalState.getForm(player)
            : SupernaturalForm.NONE;
        final Optional<CreatureKind> creatureKind = entity instanceof ArcaneCreature creature
            ? Optional.of(creature.creatureKind())
            : Optional.empty();
        return matches(target, new Facts(tagged, form, creatureKind));
    }

    public static boolean matches(final Target target, final Facts facts) {
        if (facts.tagged()) {
            return true;
        }
        return switch (target) {
            case WEREWOLF -> facts.form() == SupernaturalForm.WEREWOLF
                || facts.creatureKind().filter(BrewTargeting::isWerewolf).isPresent();
            case VAMPIRE -> facts.form() == SupernaturalForm.VAMPIRE
                || facts.creatureKind().filter(CreatureKind::isVampiric).isPresent();
            case DEMON -> facts.creatureKind().filter(BrewTargeting::isDemon).isPresent();
        };
    }

    private static TagKey<EntityType<?>> tag(final Target target) {
        return switch (target) {
            case WEREWOLF -> WarlockeryTags.EntityTypes.WEREWOLVES;
            case VAMPIRE -> WarlockeryTags.EntityTypes.VAMPIRES;
            case DEMON -> WarlockeryTags.EntityTypes.DEMONS;
        };
    }

    private static boolean isWerewolf(final CreatureKind kind) {
        return kind == CreatureKind.WEREWOLF || kind == CreatureKind.LYCAN_VILLAGER;
    }

    private static boolean isDemon(final CreatureKind kind) {
        return switch (kind) {
            case DEMON, IMP, EMBERHORN_ARCHFIEND, ABYSSAL_REGENT -> true;
            default -> false;
        };
    }

    public enum Target {
        DEMON,
        VAMPIRE,
        WEREWOLF
    }

    public record Facts(boolean tagged, SupernaturalForm form, Optional<CreatureKind> creatureKind) {
        public Facts {
            form = java.util.Objects.requireNonNull(form, "form");
            creatureKind = java.util.Objects.requireNonNull(creatureKind, "creatureKind");
        }
    }
}
