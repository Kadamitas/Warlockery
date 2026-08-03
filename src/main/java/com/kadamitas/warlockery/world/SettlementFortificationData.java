package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.world.SettlementFortificationRules.SettlementKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class SettlementFortificationData extends SavedData {
    private static final Codec<SettlementKind> KIND_CODEC = Codec.STRING.xmap(
        name -> SettlementKind.valueOf(name.toUpperCase(java.util.Locale.ROOT)),
        kind -> kind.name().toLowerCase(java.util.Locale.ROOT)
    );
    private static final Codec<Layout> LAYOUT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("center").forGetter(Layout::encodedCenter),
        KIND_CODEC.fieldOf("kind").forGetter(Layout::kind),
        Codec.INT.fieldOf("radius").forGetter(Layout::radius),
        Codec.INT.fieldOf("deck_y").forGetter(Layout::deckY)
    ).apply(instance, Layout::new));
    private static final Codec<SettlementFortificationData> CODEC = LAYOUT_CODEC.listOf()
        .xmap(SettlementFortificationData::new, SettlementFortificationData::entries);
    public static final SavedDataType<SettlementFortificationData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "settlement_fortifications"),
        SettlementFortificationData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, Layout> layouts;

    public SettlementFortificationData() {
        layouts = new LinkedHashMap<>();
    }

    private SettlementFortificationData(final List<Layout> entries) {
        layouts = new LinkedHashMap<>();
        entries.forEach(layout -> layouts.put(layout.encodedCenter(), layout));
    }

    public static SettlementFortificationData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean contains(final BlockPos center) {
        return layouts.containsKey(center.asLong());
    }

    public Optional<Layout> layout(final BlockPos center) {
        return Optional.ofNullable(layouts.get(center.asLong()));
    }

    public List<Layout> layouts() {
        return List.copyOf(layouts.values());
    }

    public void mark(
        final BlockPos center,
        final SettlementKind kind,
        final int radius,
        final int deckY
    ) {
        final Layout layout = new Layout(center.asLong(), kind, radius, deckY);
        if (!layout.equals(layouts.put(center.asLong(), layout))) {
            setDirty();
        }
    }

    private List<Layout> entries() {
        return layouts();
    }

    public record Layout(long encodedCenter, SettlementKind kind, int radius, int deckY) {
        public Layout {
            final boolean compactTestLayout = radius == 1;
            final boolean productionHumanLayout = kind == SettlementKind.HUMAN
                && radius >= SettlementFortificationRules.MIN_HUMAN_RADIUS
                && radius <= SettlementFortificationRules.MAX_HUMAN_RADIUS;
            final boolean productionHobgoblinLayout = kind == SettlementKind.HOBGOBLIN
                && radius == SettlementFortificationRules.HOBGOBLIN_RADIUS;
            if (!compactTestLayout && !productionHumanLayout && !productionHobgoblinLayout) {
                throw new IllegalArgumentException("Saved fortification radius is outside supported bounds");
            }
        }

        public BlockPos center() {
            return BlockPos.of(encodedCenter);
        }
    }
}
