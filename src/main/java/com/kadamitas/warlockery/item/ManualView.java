package com.kadamitas.warlockery.item;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public record ManualView(ManualProfile profile, List<String> sections) {
    public ManualView {
        sections = List.copyOf(sections);
        final List<String> orderedSections = profile.sections().stream().filter(sections::contains).toList();
        if (sections.isEmpty() || !sections.equals(orderedSections)) {
            throw new IllegalArgumentException("Manual view sections must be a nonempty ordered subset of the profile");
        }
    }

    public static ManualView from(final ManualProfile profile, final ItemStack stack) {
        return new ManualView(profile, ManualProgress.visibleSections(profile, stack));
    }

    public String adjacentSection(final String current, final int offset) {
        final int index = Math.max(0, sections.indexOf(current));
        return sections.get(Math.floorMod(index + offset, sections.size()));
    }
}
