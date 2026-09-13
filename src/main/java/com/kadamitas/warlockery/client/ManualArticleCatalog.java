package com.kadamitas.warlockery.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

final class ManualArticleCatalog {
    private static final String RITUAL_PREFIX = "rite_";
    private static final String BREW_PREFIX = "brew_entry_";
    private static final String BIOME_PREFIX = "biome_entry_";
    private static final String MACHINE_RECIPE_PREFIX = "machine_recipe_";
    private static final Set<String> UTILITY_ITEM_SECTIONS = Set.of(
        "ingredient_contract", "ingredient_contract_fiery_touch", "ingredient_contract_evaporate",
        "ingredient_contract_resist_fire", "ingredient_contract_smelting", "ingredient_contract_blaze",
        "ingredient_contract_torment", "ingredient_bolt_holy", "ingredient_bolt_stake",
        "mooncharm", "canesword", "ingredient_bolt_splitting", "ingredient_bolt_anti_magic", "ingredient_bolt_silver",
        "arcane_focus", "ingredient_brew_grave", "mysticbranch",
        "louse", "archfiends_urn", "circletalisman",
        "rowanwooddoor", "ingredient_door_key", "ingredient_door_keyring",
        "bloodcrucible", "coffinblock", "glassgoblet",
        "mirrorblock", "mirrorblock2", "mirrorwall",
        "ingredient_verdant_catalyst", "ingredient_verdant_catalyst_prime", "ingredient_redstone_soup",
        "leechchest", "shadedglass", "shadedglass_active",
        "divinerwater", "divinerlava", "playercompass", "shelfcompass", "brewbag",
        "mirror", "ingredient_seer_stone", "ruby_slippers",
        "ingredient_attuned_stone", "ingredient_attuned_stone_charged", "ingredient_bone_needle",
        "ingredient_creeper_heart", "ingredient_graveyard_dust", "ingredient_artichoke",
        "mutator", "seedsdreamroot", "ingredient_rock",
        "biomenote", "replication_staff", "replication_charge", "universal_antidote",
        "ingredient_purified_milk", "ingredient_warm_blood", "ingredient_infernal_animus",
        "sungrenade", "ingredient_soul_of_torment",
        "doll", "earth_guard_doll", "water_guard_doll", "hunger_guard_doll", "fire_guard_doll",
        "tool_mending_doll", "death_guard_doll", "hex_guard_doll", "hexing_doll", "blood_link_doll",
        "doll_guard", "armor_mending_doll",
        "ingredient_waystone", "ingredient_waystone_bound", "ingredient_waystone_creature_bound",
        "boline", "ingredient_icy_needle", "ingredient_wolfsbane",
        "bucketspirit", "buckethollowtears", "bucketerosionbrew", "bucketbrew",
        "ingredient_bat_ball", "spectralstone", "ingredient_subdued_spirit",
        "ingredient_subdued_spirit_village", "hornofthehunt", "ingredient_fool_skull", "ingredient_necro_stone",
        "earmuffs", "seepingshoes", "barkbelt", "twisting_band", "iceslippers",
        "forgewardens_girdle", "stonebrokers_quiver", "deathscowl", "deathsrobe", "deathsfeet", "deathshand",
        "bitingbelt", "emberstep_slippers", "witchhat", "witchrobe", "hedge_crones_hat", "necromancerrobe",
        "silverhelm", "silverchestplate", "silverleggings", "silverboots",
        "werewolf_hunter_hat", "werewolf_hunter_coat", "werewolf_hunter_leggings", "werewolf_hunter_boots",
        "werewolf_hunter_hat_silvered", "werewolf_hunter_coat_silvered",
        "werewolf_hunter_leggings_silvered", "werewolf_hunter_boots_silvered",
        "werewolf_hunter_hat_dawn", "werewolf_hunter_coat_dawn",
        "werewolf_hunter_leggings_dawn", "werewolf_hunter_boots_dawn"
    );

    private static final Map<String, String> DEVICE_ITEM_SECTIONS = Map.ofEntries(
        Map.entry("device_alluring_skull", "warlockery:alluringskull"),
        Map.entry("device_bear_trap", "warlockery:beartrap"),
        Map.entry("device_plant_mine", "warlockery:plantmine"),
        Map.entry("device_wicker_bundle", "warlockery:wickerbundle"),
        Map.entry("device_demon_heart", "warlockery:demonheart"),
        Map.entry("device_crystal_ball", "warlockery:crystalball"),
        Map.entry("device_sun_collector", "warlockery:daylightcollector"),
        Map.entry("device_void_bramble", "warlockery:voidbramble"),
        Map.entry("device_dream_weaver", "warlockery:dreamcatcher")
    );

    private ManualArticleCatalog() {
    }

    static Article article(final ManualProfile manual, final String section) {
        if (section.startsWith("crafting_")) {
            return crafting(section.substring("crafting_".length()));
        }
        if (section.startsWith(RITUAL_PREFIX)) {
            return ritual(section.substring(RITUAL_PREFIX.length()));
        }
        if (section.startsWith(BREW_PREFIX)) {
            final Article article = brew(section.substring(BREW_PREFIX.length()));
            return new Article(ManualBookLinks.append(article.body(), manual, section), article.glyphs(), article.pictograms());
        }
        if (section.startsWith(BIOME_PREFIX)) {
            return biome(section.substring(BIOME_PREFIX.length()));
        }
        if (section.startsWith(MACHINE_RECIPE_PREFIX)) {
            final Article article = machineRecipe(section.substring(MACHINE_RECIPE_PREFIX.length()));
            return new Article(ManualBookLinks.append(article.body(), manual, section), article.glyphs(), article.pictograms());
        }
        final MutableComponent text = Component.translatable(manual.translatedSectionKey(section));
        if ("ingredient_book_distilling".equals(manual.id()) && "power".equals(section)) {
            return new Article(Component.translatable("manual.warlockery.onboarding.altar"), Map.of(),
                List.of(picture("warlockery:altar", 6)));
        }
        final String guidance = switch (manual.id() + "/" + section) {
            case "ingredient_book_oven/preamble" -> "first_steps";
            case "ingredient_book_distilling/inputs" -> "distillery";
            case "ingredient_book_circle_magic/power" -> "altar";
            case "ingredient_book_circle_magic/ritual_ui" -> "cast";
            case "ingredient_book_herbology/preamble", "ingredient_book_herbology/plant_belladonna",
                "ingredient_book_herbology/plant_mandrake" -> "seeds";
            default -> "";
        };
        if (!guidance.isEmpty()) text.append("\n\n").append(Component.translatable("manual.warlockery.onboarding." + guidance));
        if ("preamble".equals(section) && !"ingredient_vbook_page".equals(manual.id())) {
            text.append("\n\n").append(crafting(manual.id()).body());
        }
        return new Article(
            ManualBookLinks.append(text, manual, section),
            Map.of(),
            manualPictograms(manual, section)
        );
    }

    private static Article crafting(final String id) {
        final String recipeId = id.equals("ingredient_soft_clay_jar") ? "ingredient_clay_jar_soft" : id;
        final JsonObject recipe = resource("/data/warlockery/recipe/" + recipeId + ".json");
        final MutableComponent body = Component.translatable("manual.warlockery.crafting.intro").copy();
        final String type = recipe.get("type").getAsString();
        final Map<String, Integer> amounts = new LinkedHashMap<>();
        if ("minecraft:crafting_shaped".equals(type)) {
            final JsonObject key = recipe.getAsJsonObject("key");
            final JsonArray pattern = recipe.getAsJsonArray("pattern");
            body.append("\n\n").append(Component.translatable("manual.warlockery.crafting.shaped"));
            for (int row = 0; row < pattern.size(); row++) {
                final String cells = pattern.get(row).getAsString();
                final MutableComponent contents = Component.empty();
                for (int column = 0; column < cells.length(); column++) {
                    if (column > 0) contents.append(" | ");
                    final String symbol = cells.substring(column, column + 1);
                    if (" ".equals(symbol)) {
                        contents.append(Component.translatable("manual.warlockery.crafting.empty"));
                    } else {
                        final String ingredient = key.get(symbol).getAsString();
                        contents.append(craftingIngredientName(ingredient));
                        amounts.merge(ingredient, 1, Integer::sum);
                    }
                }
                body.append("\n").append(Component.translatable("manual.warlockery.crafting.row", row + 1, contents));
            }
        } else if ("minecraft:crafting_shapeless".equals(type)) {
            body.append("\n\n").append(Component.translatable("manual.warlockery.crafting.shapeless"));
            recipe.getAsJsonArray("ingredients").forEach(value -> amounts.merge(value.getAsString(), 1, Integer::sum));
        } else if ("minecraft:smelting".equals(type)) {
            body.append("\n\n").append(Component.translatable("manual.warlockery.crafting.smelting"));
            amounts.put(recipe.get("ingredient").getAsString(), 1);
        } else {
            throw new IllegalArgumentException("Unsupported manual crafting type: " + type);
        }
        append(body, "manual.warlockery.entry.ingredients", amounts.entrySet().stream()
            .map(entry -> Component.translatable("manual.warlockery.entry.amount", entry.getValue(), craftingIngredientName(entry.getKey())))
            .toList());
        final JsonObject result = recipe.getAsJsonObject("result");
        final String output = result.get("id").getAsString();
        final int count = result.has("count") ? result.get("count").getAsInt() : 1;
        append(body, "manual.warlockery.entry.workings", List.of(Component.translatable(
            "manual.warlockery.machine_recipe.produces", count, ingredientName(output))));
        final java.util.ArrayList<Pictogram> pictures = new java.util.ArrayList<>();
        pictures.add(picture(output, count));
        amounts.forEach((ingredient, amount) -> pictures.add(picture(representativeItem(ingredient), amount)));
        return new Article(body, Map.of(), List.copyOf(pictures));
    }

    private static Component craftingIngredientName(final String ingredient) {
        final String example = representativeItem(ingredient);
        return ingredientName(ingredient.startsWith("#") && !"minecraft:paper".equals(example)
            ? example : ingredient);
    }

    private static Article biome(final String id) {
        return new Article(Component.translatable(
            ManualProfile.translatedBiomeEntryKey(id),
            biomeName(id)
        ), Map.of(), List.of());
    }

    private static Article machineRecipe(final String id) {
        final JsonObject recipe = resource("/data/warlockery/warlockery_machine/" + id + ".json");
        final MutableComponent body = Component.translatable(
            "manual.warlockery.machine_recipe.entry",
            machineName(recipe.get("machine").getAsString())
        ).copy();
        final JsonArray inputs = recipe.getAsJsonArray("inputs");
        appendIngredients(body, inputs);
        final java.util.List<Component> workings = new java.util.ArrayList<>();
        com.kadamitas.warlockery.crafting.MachineProfiles.forRecipeType(recipe.get("machine").getAsString()).ifPresent(profile -> {
            if (profile.requiresExternalHeat()) workings.add(Component.translatable("manual.warlockery.machine_recipe.heat"));
            if (profile.hasFuelSlot()) workings.add(Component.translatable("manual.warlockery.machine_recipe.requires_fuel"));
        });
        if ("brazier".equals(recipe.get("machine").getAsString())) {
            workings.add(Component.translatable("manual.warlockery.machine_recipe.ignite"));
        }
        final JsonArray outputs = recipe.getAsJsonArray("outputs");
        if (outputs != null) {
            java.util.stream.StreamSupport.stream(outputs.spliterator(), false).forEach(element -> {
                final JsonObject output = element.getAsJsonObject();
                final int count = output.has("count") ? output.get("count").getAsInt() : 1;
                workings.add(Component.translatable(
                    "manual.warlockery.machine_recipe.produces",
                    count,
                    ingredientName(output.get("item").getAsString())
                ));
            });
        }
        if (recipe.has("processing_time")) {
            workings.add(Component.translatable(
                "manual.warlockery.machine_recipe.processing_time",
                decimal(recipe.get("processing_time").getAsInt() / 20.0F)
            ));
        }
        com.kadamitas.warlockery.crafting.BrazierEffectRuntime.Effect.fromRecipe(Identifier.fromNamespaceAndPath("warlockery", id))
            .ifPresent(effect -> workings.add(Component.translatable("jei.warlockery.effect." + effect.recipePath())));
        if (recipe.has("requires_fuel") && recipe.get("requires_fuel").getAsBoolean()) {
            workings.add(Component.translatable("manual.warlockery.machine_recipe.requires_fuel"));
        }
        append(body, "manual.warlockery.entry.workings", workings);
        if (recipe.has("altar_power") && recipe.get("altar_power").getAsInt() > 0) {
            append(body, "manual.warlockery.entry.altar_power", List.of(Component.literal(recipe.get("altar_power").getAsString())));
            if (recipe.has("power_mode") && "continuous".equals(recipe.get("power_mode").getAsString())) {
                body.append("\n").append(Component.translatable("manual.warlockery.machine_recipe.continuous_power"));
            }
        }
        if (recipe.has("fluid")) {
            final JsonObject fluid = recipe.getAsJsonObject("fluid");
            append(body, "manual.warlockery.machine_recipe.fluid", java.util.List.of(
                Component.translatable(
                    "manual.warlockery.entry.fluid_amount",
                    fluid.get("amount").getAsString(),
                    ingredientName(fluid.get("ingredient").getAsString())
                )
            ));
        }
        final String setup = switch (id) {
            case "cauldron_flowing_spirit" -> "spirit_world";
            case "kettle_brew_bodega" -> "bodega";
            case "kettle_brew_cursed_leaping" -> "cursed_leaping";
            case "kettle_brew_frogs_tongue" -> "frogs_tongue";
            case "brazier_drain_growth" -> "drain_growth";
            default -> "";
        };
        if (!setup.isEmpty()) body.append("\n\n").append(Component.translatable("manual.warlockery.machine_setup." + setup));
        if (setup.equals("spirit_world")) {
            appendBookReference(body, "ingredient_book_burning", "spirit_world_entry");
            appendBookReference(body, "ingredient_book_burning", "spirit_world_laws");
        } else if (setup.equals("bodega") || setup.equals("cursed_leaping") || setup.equals("frogs_tongue")) {
            appendBookReference(body, "ingredient_book_circle_magic", "rite_bind_familiar");
        }
        return new Article(body, Map.of(), pictograms(inputs));
    }

    private static void appendBookReference(final MutableComponent body, final String book, final String section) {
        final var reference = new ManualBookLinks.Reference(ManualProfile.find(book).orElseThrow(), section);
        body.append("\n\n").append(reference.label().copy().withStyle(style -> style.withColor(0x174B9A).withUnderlined(true)
            .withClickEvent(new net.minecraft.network.chat.ClickEvent.Custom(reference.id(), java.util.Optional.empty()))));
    }

    private static Article ritual(final String id) {
        final JsonObject ritual = resource("/data/warlockery/ritual/" + id + ".json");
        final MutableComponent body = Component.translatable(ritual.get("description").getAsString()).copy();
        final Map<String, Integer> declaredGlyphs = integers(ritual.getAsJsonObject("glyphs"));
        // Transformation diagrams show the ring to prepare, not the resulting chalk color.
        final Map<String, Integer> glyphs = ChalkCircleLayout.canonicalGlyphs(
            id.equals("glyph_to_ritual")
                ? Map.of("circleglyph_veil", declaredGlyphs.values().iterator().next())
                : declaredGlyphs
        );
        if (ritual.has("power")) {
            append(body, "manual.warlockery.entry.altar_power", java.util.List.of(
                Component.literal(ritual.get("power").getAsString())
            ));
        }
        final JsonObject requirements = ritual.has("requirements")
            ? ritual.getAsJsonObject("requirements")
            : new JsonObject();
        final JsonArray ingredients = requirements.getAsJsonArray("ingredients");
        final JsonArray entities = requirements.getAsJsonArray("entities");
        appendIngredients(body, ingredients, ritual.get("action").getAsString());
        appendEntities(body, entities);
        appendConditions(body, ritual, requirements);
        body.append("\n\n").append(Component.translatable("manual.warlockery.ritual.cast_time",
            decimal((ritual.has("casting_time") ? ritual.get("casting_time").getAsInt() : 80) / 20.0F)));
        final String action = ritual.get("action").getAsString();
        if ("summon_item".equals(action)) body.append("\n\n").append(Component.translatable(
            "manual.warlockery.ritual.item_result", ritual.has("count") ? ritual.get("count").getAsInt() : 1,
            ingredientName(ritual.get("target").getAsString())));
        if ("summon_entity".equals(action) || "summon_huntsman".equals(action)) body.append("\n\n").append(Component.translatable(
            "manual.warlockery.ritual.entity_result", ritual.has("count") ? ritual.get("count").getAsInt() : 1,
            entityName(ritual.get("target").getAsString())));
        if ("bind_fetish".equals(action)) body.append("\n\n").append(Component.translatable("manual.warlockery.ritual.fetish_setup"));
        if ("summon_huntsman".equals(action)) body.append("\n\n").append(Component.translatable("manual.warlockery.ritual.huntsman_setup"));
        if ("cook".equals(action)) body.append("\n\n").append(Component.translatable("manual.warlockery.ritual.cook_setup"));
        if ("climate_change".equals(id)) {
            append(body, "manual.warlockery.entry.climate_reach", List.of(
                Component.translatable("manual.warlockery.ritual.climate_change.guide")
            ));
        }
        if ("glyph_transform".equals(ritual.get("action").getAsString())) {
            body.append("\n").append(Component.translatable("manual.warlockery.glyph_transform.sizes"));
        }
        body.append(ManualRitualInstructions.additional(id, ritual));
        if ("remove_vampirism".equals(action) || "remove_werewolf".equals(action)
            || "transform_werewolf".equals(action)) {
            appendBookReference(body, "ingredient_book_burning", "sympathetic_vials");
        }
        return new Article(body, glyphs, ritualPictograms(ritual, requirements, ingredients, entities));
    }

    private static Article brew(final String id) {
        final BrewKind kind = BrewKind.require(id);
        final MutableComponent body = Component.translatable("manual.warlockery.brew.effect_intro").copy();
        final java.util.List<Component> workings = java.util.stream.Stream.concat(
            kind.effects().stream().map(effect -> Component.translatable(
                "manual.warlockery.brew.effect",
                effectName(effect.effect()),
                roman(effect.amplifier() + 1),
                Math.max(1, effect.duration() / 20)
            )),
            kind.behaviors().stream().map(behavior -> translatedFallback(
                "manual.warlockery.brew.behavior." + behavior.id(),
                humanize(behavior.id())
            ))
        ).toList();
        append(body, "manual.warlockery.entry.workings", workings, "\n\n");
        body.append("\n");
        body.append(Component.translatable("manual.warlockery.brew.reach", decimal(kind.radius()), decimal(kind.potency())));
        body.append("\n\n").append(Component.translatable("manual.warlockery.brew.kettle_recipe"));
        final Article preparation = machineRecipe("kettle_brew_" + id);
        body.append("\n\n").append(preparation.body());
        return new Article(body, Map.of(), preparation.pictograms());
    }

    private static List<Pictogram> ritualPictograms(
        final JsonObject ritual,
        final JsonObject requirements,
        final JsonArray ingredients,
        final JsonArray entities
    ) {
        final List<Pictogram> climateFocus = "climate_shift".equals(ritual.get("action").getAsString())
            ? List.of(
                picture("warlockery:ingredient_book_biomes", 1),
                picture("warlockery:ingredient_seer_stone", 1),
                picture("minecraft:player_head", 5),
                picture("minecraft:nether_star", 3)
            )
            : List.of();
        return java.util.stream.Stream.of(
            java.util.stream.Stream.of(picture("warlockery:arcane_focus", 1)),
            climateFocus.stream(),
            conditionPictograms(ritual, requirements).stream(),
            pictograms(ingredients).stream(),
            entityPictograms(entities).stream()
        ).flatMap(java.util.function.Function.identity()).toList();
    }

    private static List<Pictogram> conditionPictograms(
        final JsonObject ritual,
        final JsonObject requirements
    ) {
        final java.util.ArrayList<Pictogram> pictures = new java.util.ArrayList<>();
        if ((ritual.has("night_only") && ritual.get("night_only").getAsBoolean())
            || (requirements.has("full_moon") && requirements.get("full_moon").getAsBoolean())) {
            pictures.add(picture("minecraft:clock", 1));
        }
        if (requirements.has("day_only") && requirements.get("day_only").getAsBoolean()) {
            pictures.add(picture("minecraft:sunflower", 1));
        }
        if (requirements.has("raining") && requirements.get("raining").getAsBoolean()) {
            pictures.add(picture("minecraft:water_bucket", 1));
        }
        if (requirements.has("thundering") && requirements.get("thundering").getAsBoolean()) {
            pictures.add(picture("minecraft:lightning_rod", 1));
        }
        return List.copyOf(pictures);
    }

    private static List<Pictogram> pictograms(final JsonArray ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(ingredients.spliterator(), false)
            .map(element -> {
                final JsonObject ingredient = element.getAsJsonObject();
                final String raw = ingredient.get("ingredient").getAsString();
                final int count = ingredient.has("count") ? ingredient.get("count").getAsInt() : 1;
                return picture(representativeItem(raw), count);
            })
            .toList();
    }

    private static List<Pictogram> entityPictograms(final JsonArray entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(entities.spliterator(), false)
            .map(element -> {
                final JsonObject entity = element.getAsJsonObject();
                final String raw = entity.get("entity").getAsString();
                final int count = entity.has("count") ? entity.get("count").getAsInt() : 1;
                return picture(representativeEntity(raw), count);
            })
            .toList();
    }

    private static String representativeEntity(final String raw) {
        if (raw.startsWith("#warlockery:death_binding/")) {
            final String family = raw.substring(raw.lastIndexOf('/') + 1);
            final String singular = family.endsWith("s") ? family.substring(0, family.length() - 1) : family;
            return "warlockery:" + singular + "_spawn_egg";
        }
        final Identifier id = Identifier.tryParse(raw);
        return id == null
            ? "minecraft:egg"
            : id.getNamespace() + ":" + id.getPath() + "_spawn_egg";
    }

    private static List<Pictogram> manualPictograms(final ManualProfile manual, final String section) {
        final String device = DEVICE_ITEM_SECTIONS.get(section);
        if (device != null) return List.of(picture(device, 1));
        if (UTILITY_ITEM_SECTIONS.contains(section)) {
            return List.of(picture("warlockery:" + section, 1));
        }
        if ("preamble".equals(section)) {
            return "vampirebook".equals(manual.id())
                ? List.of(
                    picture("warlockery:vampirebook", 1),
                    picture("warlockery:ingredient_vbook_page", 1)
                )
                : List.of(picture("warlockery:" + manual.id(), 1));
        }
        if ("vampirebook".equals(manual.id())) {
            return immortalPictograms(section);
        }
        if ("beast_speech".equals(section)) {
            return List.of(
                picture("minecraft:player_head", 1),
                picture("warlockery:beast_speech_charm", 1),
                picture("minecraft:sheep_spawn_egg", 1)
            );
        }
        if (section.startsWith("fetish_")) {
            return List.of(picture(fetishItem(section), 1));
        }
        if (section.startsWith("plant_")) {
            return List.of(picture(plantItem(section.substring("plant_".length())), 1));
        }
        return List.of();
    }

    private static List<Pictogram> immortalPictograms(final String section) {
        return switch (section) {
            case "nami" -> List.of(
                picture("warlockery:nami_spawn_egg", 1),
                picture("warlockery:wedding_ring", 1)
            );
            case "blood_audience", "vampire_level_1" -> List.of(
                picture("warlockery:nami_spawn_egg", 1),
                picture("minecraft:clock", 1),
                picture("warlockery:ingredient_necro_stone", 1),
                picture("warlockery:ingredient_drop_of_luck", 1),
                picture("minecraft:wither_rose", 1),
                picture("minecraft:ghast_tear", 1)
            );
            case "vampire_level_2" -> List.of(
                picture("warlockery:ingredient_vbook_page", 1),
                picture("warlockery:glassgoblet", 1)
            );
            case "vampire_level_3" -> List.of(
                picture("warlockery:ingredient_vbook_page", 2),
                picture("minecraft:villager_spawn_egg", 5)
            );
            case "vampire_level_4" -> List.of(
                picture("warlockery:ingredient_vbook_page", 3),
                picture("minecraft:clock", 4)
            );
            case "vampire_level_5" -> List.of(
                picture("warlockery:ingredient_vbook_page", 4),
                picture("warlockery:sungrenade", 10)
            );
            case "vampire_level_6" -> List.of(
                picture("warlockery:ingredient_vbook_page", 5),
                picture("minecraft:blaze_spawn_egg", 20)
            );
            case "vampire_level_7" -> List.of(
                picture("warlockery:ingredient_vbook_page", 6),
                picture("warlockery:nami_spawn_egg", 1),
                picture("minecraft:poppy", 1)
            );
            case "vampire_level_8" -> List.of(
                picture("warlockery:ingredient_vbook_page", 7),
                picture("minecraft:bell", 4)
            );
            case "vampire_level_9" -> List.of(
                picture("warlockery:ingredient_vbook_page", 8),
                picture("minecraft:minecart", 5)
            );
            case "vampire_level_10" -> List.of(
                picture("warlockery:ingredient_vbook_page", 9),
                picture("warlockery:glassgoblet", 1),
                picture("warlockery:coffin", 1)
            );
            default -> List.of();
        };
    }

    private static String plantItem(final String id) {
        return switch (id) {
            case "artichoke" -> "warlockery:seedsartichoke";
            case "belladonna" -> "warlockery:seedsbelladonna";
            case "garlic" -> "warlockery:garlic";
            case "mandrake" -> "warlockery:seedsmandrake";
            case "dreamroot" -> "warlockery:seedsdreamroot";
            case "snowbell" -> "warlockery:seedssnowbell";
            case "wolfsbane" -> "warlockery:seedswolfsbane";
            case "wormwood" -> "warlockery:seedswormwood";
            case "somnian_cotton" -> "warlockery:somniancotton";
            case "leaping_lily" -> "warlockery:leapinglily";
            case "blood_rose" -> "warlockery:bloodrose";
            case "void_bramble" -> "warlockery:voidbramble";
            case "critter_snare" -> "warlockery:crittersnare";
            default -> "warlockery:" + id.replace("_", "");
        };
    }

    private static String fetishItem(final String section) {
        return switch (section) {
            case "fetish_scarecrow" -> "warlockery:scarecrow";
            case "fetish_trent_effigy" -> "warlockery:trent";
            case "fetish_alluring_skull" -> "warlockery:alluringskull";
            case "fetish_statue_goddess" -> "warlockery:statuegoddess";
            case "fetish_statue_worship" -> "warlockery:statueofworship";
            case "fetish_statue_broken_hexes" -> "warlockery:broken_hexes_statue";
            case "fetish_statue_occluded_summons" -> "warlockery:occluded_summons_statue";
            case "fetish_doll_shelf" -> "warlockery:doll_shelf";
            default -> "warlockery:" + section.substring("fetish_".length());
        };
    }

    private static String representativeItem(final String raw) {
        if (!raw.startsWith("#")) {
            return raw;
        }
        final String tag = raw.substring(1);
        final var loaded = loadedTagItems(tag).stream().findFirst();
        if (loaded.isPresent()) return loaded.orElseThrow();
        final String basic = switch (tag) {
            case "c:dyes/red" -> "minecraft:red_dye";
            case "c:dyes/yellow" -> "minecraft:yellow_dye";
            case "c:dyes/black" -> "minecraft:black_dye";
            case "c:dusts/redstone" -> "minecraft:redstone";
            case "c:dusts/glowstone" -> "minecraft:glowstone_dust";
            case "c:fertilizers" -> "minecraft:bone_meal";
            case "c:ender_pearls" -> "minecraft:ender_pearl";
            case "c:stones" -> "minecraft:stone";
            case "c:cobblestones" -> "minecraft:cobblestone";
            case "c:bones" -> "minecraft:bone";
            case "c:strings" -> "minecraft:string";
            case "c:ingots/copper" -> "minecraft:copper_ingot";
            case "c:rods/blaze" -> "minecraft:blaze_rod";
            case "c:tools/shield" -> "minecraft:shield";
            case "c:gems/amethyst" -> "minecraft:amethyst_shard";
            case "c:gems/diamond" -> "minecraft:diamond";
            case "c:gems/quartz" -> "minecraft:quartz";
            case "c:gems/lapis" -> "minecraft:lapis_lazuli";
            case "c:buckets/milk" -> "minecraft:milk_bucket";
            case "c:buckets/lava" -> "minecraft:lava_bucket";
            case "c:slime_balls" -> "minecraft:slime_ball";
            case "c:feathers" -> "minecraft:feather";
            case "c:gunpowders" -> "minecraft:gunpowder";
            case "c:player_workstations/furnaces" -> "minecraft:furnace";
            case "c:bars/iron" -> "minecraft:iron_bars";
            case "c:glass_blocks/colorless" -> "minecraft:glass";
            case "minecraft:planks" -> "minecraft:oak_planks";
            case "minecraft:coals" -> "minecraft:coal";
            case "minecraft:wool" -> "minecraft:white_wool";
            case "minecraft:foot_armor" -> "minecraft:leather_boots";
            case "warlockery:crafting/flints" -> "minecraft:flint";
            case "warlockery:crafting/bone_needles" -> "warlockery:ingredient_bone_needle";
            case "warlockery:luck_essences" -> "warlockery:ingredient_drop_of_luck";
            case "warlockery:manual_reagents/biome_manuals" -> "warlockery:ingredient_book_biomes";
            case "warlockery:manual_reagents/books" -> "minecraft:book";
            case "warlockery:utility_reagents/water_diviners" -> "warlockery:divinerwater";
            case "warlockery:utility_reagents/water_bottles" -> "minecraft:potion";
            case "warlockery:utility_reagents/clocks" -> "minecraft:clock";
            case "warlockery:utility_reagents/null_catalysts" -> "warlockery:ingredient_nullcatalyst";
            case "warlockery:brazier/tears" -> "warlockery:ingredient_tear_of_the_goddess";
            case "warlockery:bat_binding_fibers" -> "warlockery:ingredient_bat_wool";
            case "warlockery:torment_souls" -> "warlockery:ingredient_soul_of_torment";
            case "warlockery:woven_cruor" -> "warlockery:ingredient_woven_cruor";
            case "warlockery:mutation/mutandis_extremis" -> "warlockery:ingredient_verdant_catalyst_prime";
            case "warlockery:solar_chargeables" -> "warlockery:ingredient_quartz_sphere";
            default -> null;
        };
        if (basic != null) {
            return basic;
        }
        if (tag.contains("ingots/gold")) {
            return "minecraft:gold_ingot";
        }
        if (tag.contains("ingots/iron")) {
            return "minecraft:iron_ingot";
        }
        if (tag.contains("ingots/silver")) {
            return "warlockery:silver_ingot";
        }
        if (tag.contains("rods/wooden")) {
            return "minecraft:stick";
        }
        if (tag.contains("logs")) {
            return "minecraft:oak_log";
        }
        if (tag.contains("sapling")) {
            return "minecraft:oak_sapling";
        }
        if (tag.contains("flower")) {
            return "minecraft:poppy";
        }
        if (tag.contains("leather")) {
            return "minecraft:leather";
        }
        return "minecraft:paper";
    }

    private static Pictogram picture(final String itemId, final int count) {
        return new Pictogram(itemId, count);
    }

    private static void appendIngredients(final MutableComponent body, final JsonArray ingredients) {
        appendIngredients(body, ingredients, "");
    }

    private static void appendIngredients(
        final MutableComponent body, final JsonArray ingredients, final String action
    ) {
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }
        append(body, "manual.warlockery.entry.ingredients", java.util.stream.StreamSupport.stream(
            ingredients.spliterator(), false
        ).map(element -> {
            final JsonObject ingredient = element.getAsJsonObject();
            final int count = ingredient.has("count") ? ingredient.get("count").getAsInt() : 1;
            final boolean consumed = !ingredient.has("consume") || ingredient.get("consume").getAsBoolean();
            final Component amount = Component.translatable(
                "manual.warlockery.entry.amount",
                count,
                ingredientName(ingredient.get("ingredient").getAsString())
            );
            if (action.equals("divorce") && ingredient.get("ingredient").getAsString().equals("warlockery:wedding_ring")) {
                return Component.translatable("manual.warlockery.entry.consumed_on_success", amount);
            }
            return consumed
                ? amount
                : Component.translatable("manual.warlockery.entry.kept", amount);
        }).toList());
    }

    private static void appendEntities(final MutableComponent body, final JsonArray entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        append(body, "manual.warlockery.entry.offerings", java.util.stream.StreamSupport.stream(
            entities.spliterator(), false
        ).map(element -> {
            final JsonObject entity = element.getAsJsonObject();
            final int count = entity.has("count") ? entity.get("count").getAsInt() : 1;
            return Component.translatable(
                "manual.warlockery.entry.amount",
                count,
                entityName(entity.get("entity").getAsString())
            );
        }).toList());
    }

    private static void appendConditions(
        final MutableComponent body,
        final JsonObject ritual,
        final JsonObject requirements
    ) {
        final java.util.List<Component> conditions = new java.util.ArrayList<>();
        if (ritual.has("night_only") && ritual.get("night_only").getAsBoolean()) {
            conditions.add(Component.translatable("manual.warlockery.condition.night"));
        }
        addCondition(requirements, conditions, "day_only", "manual.warlockery.condition.daylight");
        addCondition(requirements, conditions, "full_moon", "manual.warlockery.condition.full_moon");
        addCondition(requirements, conditions, "raining", "manual.warlockery.condition.rain");
        addCondition(requirements, conditions, "thundering", "manual.warlockery.condition.thunderstorm");
        if (requirements.has("dimension") && !requirements.get("dimension").getAsString().isBlank()) {
            conditions.add(Component.translatable(
                "manual.warlockery.condition.dimension",
                dimensionName(requirements.get("dimension").getAsString())
            ));
        }
        if (requirements.has("minimum_players") && requirements.get("minimum_players").getAsInt() > 1) {
            conditions.add(Component.translatable(
                "manual.warlockery.condition.participants",
                requirements.get("minimum_players").getAsInt()
            ));
        }
        append(body, "manual.warlockery.entry.conditions", conditions);
    }

    private static void addCondition(
        final JsonObject requirements,
        final java.util.List<Component> conditions,
        final String key,
        final String translationKey
    ) {
        if (requirements.has(key) && requirements.get(key).getAsBoolean()) {
            conditions.add(Component.translatable(translationKey));
        }
    }

    private static void append(
        final MutableComponent body,
        final String heading,
        final List<? extends Component> values
    ) {
        append(body, heading, values, "\n");
    }

    private static void append(
        final MutableComponent body,
        final String heading,
        final List<? extends Component> values,
        final String separator
    ) {
        if (values.isEmpty()) {
            return;
        }
        body.append("\n\n");
        body.append(Component.translatable(heading).withStyle(ChatFormatting.DARK_PURPLE));
        body.append("\n");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                body.append(separator);
            }
            body.append(values.get(index));
        }
    }

    private static Component ingredientName(final String raw) {
        if (raw.startsWith("#")) {
            final String tag = raw.substring(1);
            final var examples = loadedTagItems(tag).stream().map(id -> ingredientName(id).getString()).limit(4).toList();
            final Component label = translatedFallback("tag.item." + tag.replace(':', '.').replace('/', '.'), humanizeTag(tag));
            return examples.isEmpty() ? label : Component.translatable("manual.warlockery.entry.ingredient_examples",
                label, String.join(", ", examples));
        }
        final Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return Component.literal(humanize(raw));
        }
        return BuiltInRegistries.ITEM.get(id)
            .map(holder -> holder.value().getDescriptionId())
            .<Component>map(Component::translatable)
            .orElseGet(() -> translatedFallback(id.toLanguageKey("item"), humanize(raw)));
    }

    private static Component entityName(final String raw) {
        final Identifier id = Identifier.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
        return id == null
            ? Component.literal(humanize(raw))
            : translatedFallback(id.toLanguageKey("entity"), humanize(raw));
    }

    private static List<String> loadedTagItems(final String tag) {
        try {
            return java.util.stream.StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(tag)))
                .spliterator(), false).map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).sorted().toList();
        } catch (final IllegalStateException unboundTags) {
            if (unboundTags.getMessage() != null && unboundTags.getMessage().startsWith("Tags not bound")) return List.of();
            throw unboundTags;
        }
    }

    private static Component effectName(final String raw) {
        final Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return Component.literal(humanize(raw));
        }
        return BuiltInRegistries.MOB_EFFECT.get(id)
            .map(holder -> holder.value().getDescriptionId())
            .<Component>map(Component::translatable)
            .orElseGet(() -> translatedFallback(id.toLanguageKey("effect"), humanize(raw)));
    }

    private static Component machineName(final String raw) {
        final Identifier id = Identifier.fromNamespaceAndPath("warlockery",
            com.kadamitas.warlockery.crafting.MachineProfiles.forRecipeType(raw).map(profile -> profile.displayBlock()).orElse(raw));
        return translatedFallback(id.toLanguageKey("block"), humanize(raw));
    }

    private static Component biomeName(final String raw) {
        final Identifier id = Identifier.fromNamespaceAndPath("minecraft", raw);
        return translatedFallback(id.toLanguageKey("biome"), humanize(raw));
    }

    private static Component dimensionName(final String raw) {
        final Identifier id = Identifier.tryParse(raw);
        return id == null
            ? Component.literal(humanize(raw))
            : translatedFallback(id.toLanguageKey("dimension"), humanize(raw));
    }

    private static Component translatedFallback(final String key, final String fallback) {
        return Component.translatableWithFallback(key, fallback);
    }

    private static String humanize(final String raw) {
        final String path = raw.substring(Math.max(raw.lastIndexOf(':'), raw.lastIndexOf('/')) + 1);
        final String words = path.replace('_', ' ').replace('-', ' ').strip();
        if (words.isEmpty()) {
            return raw;
        }
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    private static String humanizeTag(final String raw) {
        final String path = raw.substring(raw.indexOf(':') + 1);
        final String[] parts = path.split("/");
        if (parts.length < 2) {
            return humanize(path);
        }
        final String category = parts[parts.length - 2];
        final String material = humanize(parts[parts.length - 1]);
        final String singular = category.endsWith("s") ? category.substring(0, category.length() - 1) : category;
        return material + " " + humanize(singular).toLowerCase(Locale.ROOT);
    }

    private static String roman(final int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(value);
        };
    }

    private static String decimal(final float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Map<String, Integer> integers(final JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        final Map<String, Integer> values = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsInt()));
        return Map.copyOf(values);
    }

    private static JsonObject resource(final String path) {
        try (var stream = ManualArticleCatalog.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing manual source " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    record Article(Component body, Map<String, Integer> glyphs, List<Pictogram> pictograms) {
        Article {
            glyphs = Map.copyOf(glyphs);
            pictograms = List.copyOf(pictograms);
        }

        boolean hasDiagram() {
            return !glyphs.isEmpty();
        }

        boolean hasPictograms() {
            return !pictograms.isEmpty();
        }
    }

    record Pictogram(String itemId, int count) {
        Pictogram {
            if (Identifier.tryParse(itemId) == null || count < 1) {
                throw new IllegalArgumentException("Manual pictograms require an item and count");
            }
        }
    }
}
