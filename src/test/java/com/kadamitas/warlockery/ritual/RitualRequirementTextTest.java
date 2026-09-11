package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        for (final var item : List.of(net.minecraft.world.item.Items.COAL, net.minecraft.world.item.Items.STONE)) {
            final var holder = item.builtInRegistryHolder();
            final var components = net.minecraft.core.component.DataComponentMap.builder();
            if (holder.areComponentsBound()) components.addAll(holder.components());
            holder.bindComponents(components
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64)
                .set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
                .build());
        }
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
        assertEquals(
            "screen.warlockery.ritual.requirement.cleared_ocean_monument",
            key(RitualRequirementText.label(condition("cleared_ocean_monument")))
        );
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

    @Test
    void ingredientTagsUseClientTranslatedLabelsInsteadOfRegistrySyntax() {
        final var requirement = ingredient("#minecraft:coals", 1, 1, true);
        final Component label = RitualRequirementText.label(requirement);
        assertEquals("tag.item.minecraft.coals", key(label));
        assertFalse(label.getString().contains("#minecraft:"));
        assertEquals("#minecraft:coals", requirement.label(), "display names must not replace matching identifiers");
    }

    @Test
    void untranslatedMaterialTagsKeepBothTheMaterialAndIngredientKind() {
        final Component ingot = RitualRequirementText.label(ingredient("#othermod:ingots/blue_steel", 3, 1, false));
        assertEquals("tag.item.othermod.ingots.blue_steel", key(ingot));
        assertEquals("Blue steel ingot", ingot.getString());
        assertEquals("Ancient bones", RitualRequirementText.label(
            ingredient("#othermod:ancient_bones", 2, 0, false)).getString());
    }

    @Test
    void readableTagsPreserveChecklistCountsMarksAndColors() {
        final Component met = RitualRequirementText.line(ingredient("#minecraft:coals", 2, 2, true));
        assertTrue(met.getString().startsWith("✓ "));
        assertEquals(0x55FF55, met.getStyle().getColor().getValue());
        final var counted = (TranslatableContents) met.getSiblings().getFirst().getContents();
        assertEquals("tag.item.minecraft.coals", key((Component) counted.getArgs()[0]));
        assertEquals(2, counted.getArgs()[1]);
        assertEquals(2, counted.getArgs()[2]);
        final Component missing = RitualRequirementText.line(ingredient("#minecraft:coals", 2, 1, false));
        assertTrue(missing.getString().startsWith("✗ "));
        assertEquals(0xFF5555, missing.getStyle().getColor().getValue());
        assertFalse(RitualRequirementText.summary(List.of(ingredient("#minecraft:coals", 2, 1, false)))
            .orElseThrow().getString().contains("#minecraft:"));
    }

    @Test
    void directItemsRetainTheirRegisteredItemAndBlockNames() {
        assertEquals("item.minecraft.coal", key(RitualRequirementText.label(ingredient("minecraft:coal", 1, 1, true))));
        assertEquals("block.minecraft.stone", key(RitualRequirementText.label(ingredient("minecraft:stone", 1, 1, true))));
    }

    private static RitualManager.RequirementStatus ingredient(final String label, final int required, final int present, final boolean met) {
        return new RitualManager.RequirementStatus("ingredient", label, required, present, met);
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
