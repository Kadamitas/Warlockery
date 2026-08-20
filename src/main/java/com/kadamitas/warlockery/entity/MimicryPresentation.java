package com.kadamitas.warlockery.entity;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * The closed copy allow-list of the Glass Doppelganger, and the only place in either family that
 * names anything derived from another entity.
 *
 * <p>Exactly two members are copied and one coarse fallback exists. Anything not named in this
 * record is not copied: not inventory, equipment stacks, held items, armour, item components,
 * enchantments, entity or item NBT, persistent data belonging to any other entity, advancements,
 * statistics, recipe unlocks, permission level, operator status, gamemode, experience, hunger,
 * saturation, air, attributes, max or current health, active status effects, potion contents,
 * ender chest contents, skin, cape, model, texture, scoreboard team, dimension state, spawn point
 * or any progression flag.</p>
 *
 * <p>It is an explicit allow-list rather than a struct copy on purpose: a whole-struct copy
 * silently inherits every field added to the source later, which is exactly how a likeness becomes
 * a data leak. {@code MimicryPresentationTest} asserts the negative directly against this source.</p>
 */
public record MimicryPresentation(Optional<Component> presentedName, Stance stance) {

    /** Horizontal speed above which a subject reads as moving rather than standing. */
    public static final double WALKING_SPEED_THRESHOLD = 0.03D;

    /** The entire second member: three values derived from one subject's own public pose. */
    public enum Stance {
        STILL, WALKING, CROUCHING
    }

    public MimicryPresentation {
        presentedName = Objects.requireNonNull(presentedName, "presentedName");
        stance = Objects.requireNonNull(stance, "stance");
    }

    /** The coarse fallback. Never derived from any entity at all. */
    public static MimicryPresentation fallback() {
        return new MimicryPresentation(Optional.empty(), Stance.STILL);
    }

    /**
     * Derives the stance from exactly two public pose facts: whether the subject is crouching, and
     * whether its own horizontal speed magnitude clears a fixed threshold. No direction is read, no
     * vector is stored, no record window exists, and the result never enters navigation.
     */
    public static Stance stanceOf(final boolean crouching, final double horizontalSpeed) {
        if (crouching) {
            return Stance.CROUCHING;
        }
        return Double.isFinite(horizontalSpeed) && horizontalSpeed > WALKING_SPEED_THRESHOLD
            ? Stance.WALKING
            : Stance.STILL;
    }

    /**
     * The presented name is the display name the acquisition routes already set. It is derived at
     * most once per episode and only when the copy carries no custom name at all, so a player's own
     * name tag is never overwritten and no name is ever cleared.
     */
    public static Optional<Component> presentedNameFor(final LivingEntity subject) {
        return subject == null
            ? Optional.empty()
            : Optional.of(Component.translatable("entity.warlockery.reflection_of", subject.getDisplayName()));
    }
}


