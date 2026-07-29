package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public enum PlantMinePayload implements StringRepresentable {
    UNARMED("unarmed", null, 0, 0),
    INK("ink", WarlockeryTags.Items.PLANT_MINE_INK_PAYLOADS, 4, 240),
    SPROUTING("sprouting", WarlockeryTags.Items.PLANT_MINE_SPROUTING_PAYLOADS, 4, 0),
    THORNS("thorns", WarlockeryTags.Items.PLANT_MINE_THORNS_PAYLOADS, 4, 0),
    WEBS("webs", WarlockeryTags.Items.PLANT_MINE_WEBS_PAYLOADS, 3, 100);

    private final String serializedName;
    private final TagKey<Item> inputTag;
    private final int radius;
    private final int duration;

    PlantMinePayload(
        final String serializedName,
        final TagKey<Item> inputTag,
        final int radius,
        final int duration
    ) {
        this.serializedName = serializedName;
        this.inputTag = inputTag;
        this.radius = radius;
        this.duration = duration;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public int radius() {
        return radius;
    }

    public int duration() {
        return duration;
    }

    public boolean isArmed() {
        return this != UNARMED;
    }

    public static Optional<PlantMinePayload> from(final ItemStack stack) {
        final var matches = Arrays.stream(values())
            .filter(PlantMinePayload::isArmed)
            .filter(payload -> stack.is(payload.inputTag))
            .limit(2)
            .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
}
