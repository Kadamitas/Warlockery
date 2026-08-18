package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RitualRequirementTextTest {
    private static final String DETAILED = "message.warlockery.ritual.cancelled_requirements";
    private static final String PLAIN = "message.warlockery.ritual.cancelled";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aNoticeNamesTheRequirementsThatLapsed() {
        final Component notice = RitualRequirementText.notice(
            List.of(chalk("circleglyphritual"), condition("night")),
            DETAILED,
            PLAIN
        );
        assertEquals(DETAILED, key(notice), "a notice with known requirements must use the detailed sentence");
        final String named = RitualRequirementText.summary(
            List.of(chalk("circleglyphritual"), condition("night"))
        ).orElseThrow().getString();
        assertTrue(named.contains("circleglyphritual"), "the notice must name the chalk ring: " + named);
        assertTrue(named.contains("requirement.night"), "the notice must name the lapsed condition: " + named);
    }

    @Test
    void aNoticeWithNothingToNameKeepsThePlainSentence() {
        assertEquals(
            PLAIN,
            key(RitualRequirementText.notice(List.of(), DETAILED, PLAIN)),
            "a cast that ended for a reason no requirement describes must still say something"
        );
        assertTrue(RitualRequirementText.summary(List.of()).isEmpty());
    }

    @Test
    void aCrowdedFailureIsCappedSoChatStaysReadable() {
        final List<RitualManager.RequirementStatus> unmet = List.of(
            condition("night"), condition("day"), condition("rain"), condition("thunder"),
            condition("full_moon"), condition("ritual_inhibitors")
        );
        final String named = RitualRequirementText.summary(unmet).orElseThrow().getString();
        final long mentioned = unmet.stream()
            .map(RitualManager.RequirementStatus::label)
            .filter(label -> named.contains("requirement." + label))
            .count();
        assertEquals(
            RitualRequirementText.NAMED_IN_MESSAGES,
            (int) mentioned,
            "a fifteen row rite must not empty its whole checklist into chat: " + named
        );
        assertTrue(named.contains("ritual.more"), "the notice must say how many it left out: " + named);
    }

    private static String key(final Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static RitualManager.RequirementStatus chalk(final String glyph) {
        return new RitualManager.RequirementStatus("chalk", glyph, 16, 15, false);
    }

    private static RitualManager.RequirementStatus condition(final String label) {
        return new RitualManager.RequirementStatus("condition", label, 1, 0, false);
    }
}
