package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.ManualView;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class ManualBookLinks {
    private ManualBookLinks() {}

    static List<Reference> references(final ManualProfile profile, final String section) {
        if (section.startsWith("crafting_") && section.contains("book_")) return List.of();
        if (section.equals("golden_chalk")) return List.of(
            reference("distilling", "machine_recipe_distill_vitriol"), reference("distilling", "inputs"));
        if (section.equals("crafting_arcane_focus")) return List.of(reference("herbology", "plant_belladonna"), reference("herbology", "plant_mandrake"));
        if (section.startsWith("rite_")) return List.of(reference("circle_magic", "ritual_ui"), reference("circle_magic", "power"));
        if (section.startsWith("brew_entry_")) return List.of(reference("brews", "machines"));
        if (section.startsWith("machine_recipe_brazier_")) return List.of(reference("burning", "brazier"));
        if (section.startsWith("machine_recipe_distill_")) return List.of(reference("distilling", "inputs"));
        if (section.startsWith("machine_recipe_oven_")) return List.of(reference("oven", "oven"));
        if (section.startsWith("machine_recipe_spin_")) return List.of(reference("distilling", "spinningwheel"));
        if (section.startsWith("machine_recipe_silver_")) return List.of(reference("distilling", "silvervat"));
        if (section.startsWith("machine_recipe_kettle_") || section.startsWith("machine_recipe_cauldron_"))
            return List.of(reference("brews", "machines"));
        if (profile.id().equals("ingredient_book_herbology") && section.equals("preamble"))
            return List.of(reference("oven", "oven"));
        if (profile.id().equals("ingredient_book_burning") && section.equals("fetish_scarecrow"))
            return List.of(reference("circle_magic", "rite_bind_fetish"));
        if (profile.id().equals("vampirebook") && section.equals("blood_audience"))
            return List.of(reference("circle_magic", "rite_blood_audience"));
        if (profile.id().equals("ingredient_book_circle_magic") && (section.equals("preamble") || section.equals("chalk")))
            return List.of(reference("oven", "oven"), reference("distilling", "inputs"), reference("herbology", "preamble"));
        if (profile.id().equals("ingredient_book_oven") && (section.equals("preamble") || section.equals("oven")))
            return List.of(reference("distilling", "inputs"));
        if (profile.id().equals("ingredient_book_distilling") && (section.equals("preamble") || section.equals("inputs")))
            return List.of(reference("oven", "oven"), reference("circle_magic", "golden_chalk"));
        return List.of();
    }

    private static Reference reference(final String book, final String section) {
        final String id = book.equals("brews") ? "cauldronbook" : "ingredient_book_" + book;
        return new Reference(ManualProfile.find(id).orElseThrow(), section);
    }

    static Optional<Reference> decode(final Identifier id) {
        if (!id.getNamespace().equals("warlockery")) return Optional.empty();
        final String[] parts = id.getPath().split("/", -1);
        if (parts.length != 3 || !parts[0].equals("manual")) return Optional.empty();
        return ManualProfile.find(parts[1]).filter(profile -> profile.sections().contains(parts[2]))
            .map(profile -> new Reference(profile, parts[2]));
    }

    static Component append(final Component body, final ManualProfile profile, final String section) {
        final var result = body.copy();
        for (final Reference target : references(profile, section)) {
            result.append("\n\n").append(target.label().copy().withStyle(style -> style
                .withColor(0x174B9A).withUnderlined(true).withClickEvent(new ClickEvent.Custom(target.id(), Optional.empty()))));
        }
        return result;
    }

    record Reference(ManualProfile profile, String section) {
        Reference {
            if (!profile.sections().contains(section)) throw new IllegalArgumentException("Unknown book section: " + section);
        }

        Identifier id() { return Identifier.fromNamespaceAndPath("warlockery", "manual/" + profile.id() + "/" + section); }

        Component label() {
            return Component.translatable("screen.warlockery.manual.book_link",
                Component.translatable(profile.translatedTitleKey()), Component.translatable(profile.translatedSectionTitleKey(section)));
        }

        Optional<ManualView> ownedView(final List<ManualView> inventory) {
            return inventory.stream().filter(view -> view.profile().id().equals(profile.id()) && view.sections().contains(section)).findFirst();
        }

        ManualView recipeView() {
            final String recipe = "crafting_" + profile.id();
            final var preview = new ManualProfile("recipe_" + profile.id(), profile.titleKey(), List.of(recipe),
                List.of(new ManualProfile.Chapter("crafting", "manual.warlockery.crafting.title", List.of(recipe))));
            return new ManualView(preview, preview.sections());
        }
    }
}
