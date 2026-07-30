package com.kadamitas.warlockery.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ManualProgress {
    private static final String OBSERVATIONS_ID = "vampirebook";
    private static final String TORN_PAGE_ID = "ingredient_vbook_page";
    private static final String UNLOCKED_SECTIONS = "WarlockeryUnlockedImmortalSections";
    private static final int INITIAL_SECTIONS = 1;

    private ManualProgress() {
    }

    public static List<String> visibleSections(final ManualProfile profile, final ItemStack stack) {
        return profile.sections().subList(0, unlockedSectionCount(profile, stack));
    }

    public static int unlockedSectionCount(final ManualProfile profile, final ItemStack stack) {
        if (!isObservations(profile)) {
            return profile.sections().size();
        }
        final int stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getIntOr(UNLOCKED_SECTIONS, INITIAL_SECTIONS);
        return Math.clamp(stored, INITIAL_SECTIONS, profile.sections().size());
    }

    static RevealResult revealNext(final ManualProfile profile, final ItemStack stack) {
        if (!isObservations(profile)) {
            return RevealResult.unsupported();
        }
        final int unlocked = unlockedSectionCount(profile, stack);
        if (unlocked >= profile.sections().size()) {
            return RevealResult.complete();
        }
        final String revealedSection = profile.sections().get(unlocked);
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
            data -> data.putInt(UNLOCKED_SECTIONS, unlocked + 1));
        return RevealResult.revealed(revealedSection);
    }

    public static boolean isObservations(final ManualProfile profile) {
        return OBSERVATIONS_ID.equals(profile.id());
    }

    static boolean isTornPage(final ManualProfile profile) {
        return TORN_PAGE_ID.equals(profile.id());
    }

    public enum RevealStatus {
        REVEALED,
        COMPLETE,
        UNSUPPORTED
    }

    public record RevealResult(RevealStatus status, Optional<String> section) {
        private static RevealResult revealed(final String section) {
            return new RevealResult(RevealStatus.REVEALED, Optional.of(section));
        }

        private static RevealResult complete() {
            return new RevealResult(RevealStatus.COMPLETE, Optional.empty());
        }

        private static RevealResult unsupported() {
            return new RevealResult(RevealStatus.UNSUPPORTED, Optional.empty());
        }
    }
}
