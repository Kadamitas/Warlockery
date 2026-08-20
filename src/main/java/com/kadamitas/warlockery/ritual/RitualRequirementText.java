package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.util.ItemDisplayNames;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The one place a {@link RitualManager.RequirementStatus} is turned into words.
 *
 * <p>The ritual panel, the notice a refused activation sends and the notice a collapsed cast sends all read
 * from here, so a requirement cannot be described one way on the screen and another way in chat. Everything
 * returned is a translatable component, resolved by whoever displays it, so the server may build these for a
 * player whose language it does not know.</p>
 */
public final class RitualRequirementText {
    /**
     * How many unmet requirements a chat notice names before it stops counting. Large rites carry fifteen
     * rows, and a chat line that lists all of them is read by nobody.
     */
    public static final int NAMED_IN_MESSAGES = 3;

    private static final Component SEPARATOR = Component.literal(", ");

    private RitualRequirementText() {
    }

    /** One checklist row: a mark, the requirement's name, and its counts where counts are meaningful. */
    public static Component line(final RitualManager.RequirementStatus requirement) {
        final Component label = label(requirement);
        final Component line = switch (requirement.category()) {
            case "chalk", "ingredient", "entity", "coven", "optional" -> Component.translatable(
                "screen.warlockery.ritual.requirement_count", label, requirement.present(), requirement.required()
            );
            default -> Component.translatable("screen.warlockery.ritual.requirement", label);
        };
        if ("optional".equals(requirement.category())) {
            return Component.literal(requirement.met() ? "◇ " : "○ ")
                .append(line)
                .withColor(requirement.met() ? 0xDDAA33 : 0xAAAAAA);
        }
        return Component.literal(requirement.met() ? "✓ " : "✗ ")
            .append(line)
            .withColor(requirement.met() ? 0x55FF55 : 0xFF5555);
    }

    /** The requirement's name on its own, with no mark and no counts. */
    public static Component label(final RitualManager.RequirementStatus requirement) {
        return switch (requirement.category()) {
            case "chalk" -> Component.translatable("block.warlockery." + requirement.label());
            case "ingredient" -> ItemDisplayNames.component(requirement.label());
            case "entity" -> requirement.label().startsWith("#")
                ? Component.literal(requirement.label())
                : Component.translatable("entity." + requirement.label().replace(':', '.'));
            case "altar" -> Component.translatable("screen.warlockery.ritual.requirement.altar");
            case "center" -> Component.translatable("screen.warlockery.ritual.requirement.center");
            case "session" -> Component.translatable("screen.warlockery.ritual.requirement.inactive");
            case "coven" -> Component.translatable("screen.warlockery.ritual.requirement.coven");
            case "optional" -> Component.translatable("screen.warlockery.ritual.requirement." + requirement.label());
            case "condition" -> Component.translatable("screen.warlockery.ritual.requirement." + requirement.label().replace(':', '.'));
            default -> Component.literal(requirement.label());
        };
    }

    /**
     * The unmet requirements as one phrase, capped at {@link #NAMED_IN_MESSAGES}. Returns empty when nothing
     * is unmet, which lets a caller fall back to its plain notice rather than send an empty sentence.
     */
    public static Optional<Component> summary(final List<RitualManager.RequirementStatus> unmet) {
        final int omitted = Math.max(0, unmet.size() - NAMED_IN_MESSAGES);
        return unmet.stream()
            .limit(NAMED_IN_MESSAGES)
            .<Component>map(RitualRequirementText::label)
            .reduce((phrase, next) -> Component.empty().append(phrase).append(SEPARATOR).append(next))
            .map(phrase -> omitted == 0
                ? phrase
                : Component.empty()
                    .append(phrase)
                    .append(Component.translatable("screen.warlockery.ritual.more", omitted)));
    }

    /**
     * A complete notice built from {@code detailedKey} when the unmet requirements are known, and from
     * {@code plainKey} when they are not. Both keys stay in use: a cast can end for a reason that is not a
     * site requirement at all, such as its definition no longer being loaded.
     */
    public static Component notice(
        final List<RitualManager.RequirementStatus> unmet,
        final String detailedKey,
        final String plainKey
    ) {
        return summary(unmet)
            .<Component>map(phrase -> Component.translatable(detailedKey, phrase))
            .orElseGet(() -> Component.translatable(plainKey));
    }
}
