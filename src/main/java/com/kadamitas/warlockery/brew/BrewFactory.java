package com.kadamitas.warlockery.brew;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;

public final class BrewFactory {
    private static final Map<String, BrewKind> KINDS_BY_ITEM_ID = createKindsByItemId();
    private static final Map<String, BrewKind> LEGACY_KINDS_BY_ITEM_ID = Map.ofEntries(
        Map.entry("ingredient_brew_bats", BrewKind.BATS),
        Map.entry("ingredient_brew_congealed_spirit", BrewKind.INVISIBLE),
        Map.entry("ingredient_brew_hexed_leaping", BrewKind.CURSED_LEAPING),
        Map.entry("ingredient_brew_depths", BrewKind.DEPTHS),
        Map.entry("ingredient_brew_erosion", BrewKind.EROSION),
        Map.entry("ingredient_brew_frogs_tongue", BrewKind.FROGS_TONGUE),
        Map.entry("ingredient_brew_grotesque", BrewKind.GROTESQUE),
        Map.entry("ingredient_brew_hitchcock", BrewKind.MURDEROUS_FLOCK),
        Map.entry("ingredient_brew_ice", BrewKind.FREEZE),
        Map.entry("ingredient_brew_infection", BrewKind.INFECTION),
        Map.entry("ingredient_brew_ink", BrewKind.INK),
        Map.entry("ingredient_brew_love", BrewKind.LOVE),
        Map.entry("ingredient_brew_raising", BrewKind.RAISING),
        Map.entry("ingredient_brew_revealing", BrewKind.REVEALING),
        Map.entry("ingredient_brew_sleep", BrewKind.SLEEPING),
        Map.entry("ingredient_brew_soaring", BrewKind.SOARING),
        Map.entry("ingredient_brew_solid_dirt", BrewKind.SOLIDIFY_DIRT),
        Map.entry("ingredient_brew_solid_erosion", BrewKind.SOLIDIFY_EROSION),
        Map.entry("ingredient_brew_solid_sand", BrewKind.SOLIDIFY_SAND),
        Map.entry("ingredient_brew_solid_sandstone", BrewKind.SOLIDIFY_SANDSTONE),
        Map.entry("ingredient_brew_solid_stone", BrewKind.SOLIDIFY_STONE),
        Map.entry("ingredient_brew_soul_anguish", BrewKind.WEAKNESS),
        Map.entry("ingredient_brew_soul_fear", BrewKind.FEAR),
        Map.entry("ingredient_brew_soul_hunger", BrewKind.WASTING),
        Map.entry("ingredient_brew_soul_torment", BrewKind.INSANITY),
        Map.entry("ingredient_brew_sprouting", BrewKind.SPROUTING),
        Map.entry("ingredient_brew_substitution", BrewKind.SUBSTITUTION),
        Map.entry("ingredient_brew_thorns", BrewKind.THORNS),
        Map.entry("ingredient_brew_vines", BrewKind.VINES),
        Map.entry("ingredient_brew_wasting", BrewKind.WASTING),
        Map.entry("ingredient_brew_web", BrewKind.WEBS)
    );

    private BrewFactory() {
    }

    public static List<String> ids() {
        return List.copyOf(KINDS_BY_ITEM_ID.keySet());
    }

    public static boolean supports(final String itemId) {
        return KINDS_BY_ITEM_ID.containsKey(itemId);
    }

    public static BrewKind requireKind(final String itemId) {
        final BrewKind kind = KINDS_BY_ITEM_ID.get(itemId);
        if (kind == null) {
            throw new IllegalArgumentException("Unknown brew item: " + itemId);
        }
        return kind;
    }

    public static BrewItem create(final Item.Properties properties, final String itemId) {
        return new BrewItem(Objects.requireNonNull(properties, "properties"), requireKind(itemId));
    }

    public static boolean supportsLegacy(final String itemId) {
        return LEGACY_KINDS_BY_ITEM_ID.containsKey(itemId);
    }

    public static Optional<BrewKind> legacyKind(final String itemId) {
        return Optional.ofNullable(LEGACY_KINDS_BY_ITEM_ID.get(itemId));
    }

    public static BrewItem createLegacy(final Item.Properties properties, final String itemId) {
        final BrewKind kind = legacyKind(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown legacy brew item: " + itemId));
        return new BrewItem(Objects.requireNonNull(properties, "properties"), kind);
    }

    public static String itemId(final BrewKind kind) {
        return "brew_" + Objects.requireNonNull(kind, "kind").id();
    }

    private static Map<String, BrewKind> createKindsByItemId() {
        final Map<String, BrewKind> kinds = new LinkedHashMap<>();
        BrewKind.builtIns().forEach(kind -> {
            final String itemId = itemId(kind);
            if (kinds.put(itemId, kind) != null) {
                throw new IllegalStateException("Duplicate brew item id: " + itemId);
            }
        });
        return Collections.unmodifiableMap(kinds);
    }
}
