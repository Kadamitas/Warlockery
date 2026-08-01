package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.util.FluidContents;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.FluidTags;

public final class CustomBrewDefinitionManager extends SimpleJsonResourceReloadListener<CustomBrewComponentDefinition> {
    public static final CustomBrewDefinitionManager INSTANCE = new CustomBrewDefinitionManager();
    private volatile Map<Identifier, CustomBrewComponentDefinition> definitions = Map.of();
    private volatile List<ResolvedDefinition> resolved = List.of();
    private volatile long revision;

    private CustomBrewDefinitionManager() {
        super(CustomBrewComponentDefinition.CODEC, FileToIdConverter.json("custom_brew_component"));
    }

    @Override
    protected void apply(
        final Map<Identifier, CustomBrewComponentDefinition> loaded,
        final ResourceManager resourceManager,
        final ProfilerFiller profiler
    ) {
        final Map<Identifier, CustomBrewComponentDefinition> valid = loaded.entrySet().stream()
            .filter(entry -> validate(entry.getKey(), entry.getValue()))
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (_, replacement) -> replacement,
                LinkedHashMap::new
            ));
        definitions = Collections.unmodifiableMap(valid);
        resolved = valid.entrySet().stream()
            .map(entry -> new ResolvedDefinition(
                entry.getKey(),
                entry.getValue(),
                ItemIngredient.parse(entry.getValue().ingredient()).orElseThrow()
            ))
            .sorted(Comparator
                .comparingInt((ResolvedDefinition entry) -> entry.definition().priority()).reversed()
                .thenComparing(entry -> entry.ingredient().tag())
                .thenComparing(entry -> entry.id().toString()))
            .toList();
        revision++;
        Warlockery.LOGGER.info("Loaded {} custom brew components", definitions.size());
    }

    public Inspection inspect(
        final NonNullList<ItemStack> inventory,
        final int inputSlots,
        final FluidContents fluid,
        final int availablePower,
        final boolean heated,
        final boolean outputAvailable,
        final boolean previouslyEngaged
    ) {
        final List<ItemStack> orderedStacks = IntStream.range(0, Math.min(inputSlots, inventory.size()))
            .mapToObj(inventory::get)
            .filter(stack -> !stack.isEmpty())
            .toList();
        if (orderedStacks.isEmpty()) {
            return new Inspection(false, CustomBrewCauldronState.EMPTY);
        }

        final ArrayList<CustomBrewComposer.Ingredient> ingredients = new ArrayList<>();
        String invalid = "";
        for (ItemStack stack : orderedStacks) {
            final Optional<ResolvedDefinition> match = match(stack);
            if (match.isEmpty()) {
                invalid = itemId(stack);
                break;
            }
            final ResolvedDefinition definition = match.orElseThrow();
            ingredients.add(new CustomBrewComposer.Ingredient(
                definition.id().toString(),
                definition.definition(),
                color(stack)
            ));
        }

        final boolean hasEffect = ingredients.stream()
            .anyMatch(ingredient -> ingredient.definition().role() == CustomBrewComponentRole.EFFECT);
        final boolean engaged = hasEffect || previouslyEngaged;
        if (!engaged) {
            return new Inspection(false, CustomBrewCauldronState.EMPTY);
        }
        if (!invalid.isEmpty()) {
            return new Inspection(true, CustomBrewComposer.invalidInput(
                ingredients,
                invalid,
                CustomBrewComposer.Conditions.ready(availablePower)
            ));
        }

        final Identifier fluidIdentifier = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        final CustomBrewComposer.Conditions conditions = new CustomBrewComposer.Conditions(
            fluid.getAmount(),
            fluid.isEmpty() || BuiltInRegistries.FLUID.wrapAsHolder(fluid.getFluid()).is(FluidTags.WATER),
            fluidIdentifier == null ? "minecraft:empty" : fluidIdentifier.toString(),
            availablePower,
            heated,
            outputAvailable
        );
        return new Inspection(true, CustomBrewComposer.compose(ingredients, conditions));
    }

    public List<Identifier> ids() {
        return List.copyOf(definitions.keySet());
    }

    public Optional<CustomBrewComponentDefinition> byId(final Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public boolean acceptsInput(final ItemStack stack) {
        return !stack.isEmpty() && match(stack).isPresent();
    }

    public long revision() {
        return revision;
    }

    private Optional<ResolvedDefinition> match(final ItemStack stack) {
        return resolved.stream().filter(entry -> entry.ingredient().matches(stack)).findFirst();
    }

    private static OptionalInt color(final ItemStack stack) {
        final Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return OptionalInt.empty();
        }
        final String path = id.getPath();
        return java.util.Arrays.stream(DyeColor.values())
            .filter(dye -> path.equals(dye.getName() + "_wool") || path.startsWith(dye.getName() + "_"))
            .mapToInt(dye -> dye.getTextureDiffuseColor() & 0xFFFFFF)
            .findFirst();
    }

    private static String itemId(final ItemStack stack) {
        final Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static boolean validate(final Identifier id, final CustomBrewComponentDefinition definition) {
        final boolean effectsResolvable = definition.effects().stream().allMatch(effect -> {
            final Identifier effectId = Identifier.tryParse(effect.effect());
            return effectId != null && BuiltInRegistries.MOB_EFFECT.containsKey(effectId);
        });
        final boolean valid = definition.structurallyValid() && definition.resolvable() && effectsResolvable;
        if (!valid) {
            Warlockery.LOGGER.error("Skipping invalid custom brew component {}", id);
        }
        return valid;
    }

    public record Inspection(boolean engaged, CustomBrewCauldronState state) {
    }

    private record ResolvedDefinition(
        Identifier id,
        CustomBrewComponentDefinition definition,
        ItemIngredient ingredient
    ) {
    }
}
