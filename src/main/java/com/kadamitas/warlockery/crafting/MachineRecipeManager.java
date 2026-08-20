package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.custom.CustomBrewDefinitionManager;
import com.kadamitas.warlockery.compat.jei.JeiRecipeRefreshSignal;
import com.kadamitas.warlockery.util.FluidIngredient;
import com.kadamitas.warlockery.util.ItemIngredient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public final class MachineRecipeManager extends SimpleJsonResourceReloadListener<MachineRecipeDefinition> {
    public static final MachineRecipeManager INSTANCE = new MachineRecipeManager();
    private static final Comparator<Candidate> DIAGNOSTIC_ORDER = Comparator
        .comparingInt(Candidate::score)
        .reversed()
        .thenComparing(Candidate::id);
    private volatile MachineRecipeCatalog catalog = MachineRecipeCatalog.EMPTY;
    private volatile long revision;

    private MachineRecipeManager() {
        super(MachineRecipeDefinition.CODEC, FileToIdConverter.json("warlockery_machine"));
    }

    @Override
    protected void apply(
        final Map<Identifier, MachineRecipeDefinition> definitions,
        final ResourceManager resourceManager,
        final ProfilerFiller profiler
    ) {
        final Map<Identifier, MachineRecipeDefinition> validRecipes = definitions.entrySet().stream()
            .filter(entry -> validate(entry.getKey(), entry.getValue()))
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
        final MachineRecipeCatalog loaded = MachineRecipeCatalog.create(validRecipes);
        catalog = loaded;
        revision++;
        Warlockery.LOGGER.info("Loaded {} Warlockery machine recipes", loaded.definitions().size());
        JeiRecipeRefreshSignal.publish();
    }

    public Optional<Match> find(final MachineProfile profile, final NonNullList<ItemStack> inventory) {
        return find(profile, inventory, FluidStack.EMPTY, Integer.MAX_VALUE);
    }

    public Optional<Match> find(
        final MachineProfile profile,
        final NonNullList<ItemStack> inventory,
        final FluidStack fluid
    ) {
        return find(profile, inventory, fluid, Integer.MAX_VALUE);
    }

    public Optional<Match> find(
        final MachineProfile profile,
        final NonNullList<ItemStack> inventory,
        final FluidStack fluid,
        final int altarPower
    ) {
        return catalog.forMachine(profile.recipeType()).stream()
            .map(recipe -> inspect(recipe, profile, inventory, fluid, altarPower))
            .filter(candidate -> candidate.inputsReady(profile))
            .findFirst()
            .map(candidate -> new Match(candidate.id(), candidate.recipe()));
    }

    static int specificity(final MachineRecipeDefinition recipe) {
        return recipe.inputs().stream().mapToInt(input -> {
            if (!input.ingredient().startsWith("#")) {
                return 3;
            }
            return input.ingredient().startsWith("#c:") || input.ingredient().startsWith("#minecraft:") ? 1 : 2;
        }).sum();
    }

    public boolean acceptsInput(final MachineProfile profile, final ItemStack stack) {
        return acceptsInput(profile, -1, stack);
    }

    public boolean acceptsInput(final MachineProfile profile, final int slot, final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final boolean recipeIngredient = catalog.inputsFor(profile.recipeType()).stream()
            .anyMatch(ingredient -> ingredient.matches(stack));
        final boolean accepted = recipeIngredient || "cauldron".equals(profile.recipeType())
            && CustomBrewDefinitionManager.INSTANCE.acceptsInput(stack);
        if (!accepted || !profile.hasDedicatedInputSlot() || slot < 0) {
            if (!accepted || !profile.hasPrimaryInputSlot() || slot < 0) {
                return accepted;
            }
            return catalog.forMachine(profile.recipeType()).stream().anyMatch(prepared -> {
                final Stream<MachineRecipeDefinition.Input> roleInputs = slot == 0
                    ? prepared.definition().inputs().stream().limit(1)
                    : prepared.definition().inputs().stream().skip(1);
                return roleInputs.anyMatch(input -> ItemIngredient.parse(input.ingredient())
                    .filter(ingredient -> ingredient.matches(stack))
                    .isPresent());
            });
        }
        final boolean dedicatedIngredient = matchesDedicatedIngredient(profile, stack);
        return profile.isDedicatedInputSlot(slot) == dedicatedIngredient;
    }

    public Diagnostic diagnose(final MachineProfile profile, final NonNullList<ItemStack> inventory) {
        return diagnose(profile, inventory, FluidStack.EMPTY, Integer.MAX_VALUE);
    }

    public Diagnostic diagnose(
        final MachineProfile profile,
        final NonNullList<ItemStack> inventory,
        final FluidStack fluid
    ) {
        return diagnose(profile, inventory, fluid, Integer.MAX_VALUE);
    }

    public Diagnostic diagnose(
        final MachineProfile profile,
        final NonNullList<ItemStack> inventory,
        final FluidStack fluid,
        final int altarPower
    ) {
        final int inputSlots = profile.inputSlots();
        final List<WrongInput> allInputs = IntStream.range(0, inputSlots)
            .mapToObj(inventory::get)
            .filter(stack -> !stack.isEmpty())
            .map(stack -> new WrongInput(itemId(stack), stack.getCount()))
            .toList();
        if (allInputs.isEmpty() && fluid.isEmpty()) {
            return Diagnostic.EMPTY;
        }

        return catalog.forMachine(profile.recipeType()).stream()
            .map(recipe -> inspect(recipe, profile, inventory, fluid, altarPower))
            .filter(candidate -> candidate.matched() > 0)
            .min(DIAGNOSTIC_ORDER)
            .map(candidate -> new Diagnostic(
                candidate.id().toString(),
                candidate.output(),
                candidate.recipe().processingTime(),
                candidate.missing(),
                candidate.wrong()
            ))
            .orElseGet(() -> new Diagnostic("", "", 0, List.of(), allInputs));
    }

    public void consumeInputs(
        final MachineRecipeDefinition recipe,
        final NonNullList<ItemStack> inventory,
        final int inputSlots
    ) {
        final List<ItemStack> inputs = inventory.stream().limit(inputSlots).toList();
        catalog.allocationPlan(recipe)
            .allocate(inputs, ItemStack::getCount)
            .consumeFrom(inputs);
    }

    public void consumeFluid(
        final MachineRecipeDefinition recipe,
        final ResourceHandler<FluidResource> handler
    ) {
        recipe.fluid().ifPresent(input -> {
            final FluidIngredient ingredient = FluidIngredient.parse(input.ingredient()).orElseThrow();
            try (var transaction = Transaction.openRoot()) {
                int remaining = input.amount();
                for (int index = 0; index < handler.size() && remaining > 0; index++) {
                    final FluidResource resource = handler.getResource(index);
                    if (ingredient.matches(resource)) {
                        remaining -= handler.extract(index, resource, remaining, transaction);
                    }
                }
                if (remaining == 0) {
                    transaction.commit();
                }
            }
        });
    }

    public List<ItemStack> createOutputs(final MachineRecipeDefinition recipe) {
        return recipe.outputs().stream().map(output -> {
            final Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(output.item()));
            return item == null ? ItemStack.EMPTY : new ItemStack(item, output.count());
        }).toList();
    }

    private static Candidate inspect(
        final MachineRecipeCatalog.PreparedRecipe prepared,
        final MachineProfile profile,
        final NonNullList<ItemStack> inventory,
        final FluidStack fluid,
        final int altarPower
    ) {
        final int inputSlots = profile.inputSlots();
        final List<ItemStack> inputs = inventory.stream().limit(inputSlots).toList();
        final List<ItemStack> roleEligibleInputs = IntStream.range(0, inputs.size())
            .mapToObj(slot -> roleAllows(prepared, profile, slot, inputs.get(slot))
                ? inputs.get(slot)
                : ItemStack.EMPTY)
            .toList();
        final var allocation = prepared.allocationPlan().allocate(roleEligibleInputs, ItemStack::getCount);
        final List<MissingInput> missingItems = allocation.requirements().stream()
            .filter(requirement -> !requirement.complete())
            .map(requirement -> new MissingInput(
                requirement.requirement().ingredient(),
                requirement.missing()
            ))
            .toList();
        final List<WrongInput> wrongItems = IntStream.range(0, inputs.size())
            .filter(slot -> !inputs.get(slot).isEmpty())
            .filter(slot -> !roleAllows(prepared, profile, slot, inputs.get(slot))
                || allocation.unreservedBySlot().get(slot) > 0)
            .mapToObj(slot -> new WrongInput(
                itemId(inputs.get(slot)),
                roleAllows(prepared, profile, slot, inputs.get(slot))
                    ? allocation.unreservedBySlot().get(slot)
                    : inputs.get(slot).getCount()
            ))
            .toList();
        final Optional<MachineRecipeDefinition.FluidInput> fluidInput = prepared.definition().fluid();
        final var expectedFluid = prepared.fluid();
        final boolean fluidMatches = expectedFluid.filter(ingredient -> ingredient.matches(fluid)).isPresent();
        final int availableFluid = fluidMatches ? fluid.getAmount() : 0;
        final int requiredPower = prepared.definition().powerMode()
            .requiredAvailablePower(prepared.definition().altarPower());
        final List<MissingInput> missing = Stream.concat(
            Stream.concat(
                missingItems.stream(),
                fluidInput.stream()
                    .filter(input -> availableFluid < input.amount())
                    .map(input -> new MissingInput(input.ingredient(), input.amount() - availableFluid))
            ),
            requiredPower > altarPower
                ? Stream.of(new MissingInput("warlockery:altar_power", requiredPower - Math.max(0, altarPower)))
                : Stream.empty()
        ).toList();
        final List<WrongInput> wrong = Stream.concat(
            wrongItems.stream(),
            Stream.ofNullable(
                fluid.isEmpty() || fluidMatches
                    ? null
                    : new WrongInput(fluidId(fluid), fluid.getAmount())
            )
        ).toList();
        return new Candidate(
            prepared.id(), prepared.definition(), prepared.primaryOutput(),
            allocation.matchedCount()
                + (fluidInput.isPresent() && availableFluid >= fluidInput.orElseThrow().amount() ? 1 : 0)
                + (requiredPower > 0 && altarPower >= requiredPower ? 1 : 0),
            missing,
            wrong
        );
    }

    private static boolean roleAllows(
        final MachineRecipeCatalog.PreparedRecipe prepared,
        final MachineProfile profile,
        final int slot,
        final ItemStack stack
    ) {
        if (stack.isEmpty()) {
            return true;
        }
        if (profile.hasDedicatedInputSlot()
            && profile.isDedicatedInputSlot(slot) != matchesDedicatedIngredient(profile, stack)) {
            return false;
        }
        if (!profile.hasPrimaryInputSlot()) {
            return true;
        }
        final Stream<MachineRecipeDefinition.Input> roleInputs = slot == 0
            ? prepared.definition().inputs().stream().limit(1)
            : prepared.definition().inputs().stream().skip(1);
        return roleInputs.anyMatch(input -> ItemIngredient.parse(input.ingredient())
            .filter(ingredient -> ingredient.matches(stack))
            .isPresent());
    }

    public static boolean matchesDedicatedIngredient(final MachineProfile profile, final ItemStack stack) {
        return profile.dedicatedInputIngredient()
            .flatMap(ItemIngredient::parse)
            .filter(ingredient -> ingredient.matches(stack))
            .isPresent();
    }

    /**
     * Uses the same parsed item/tag semantics as live slot validation so JEI also handles
     * datapack aliases that resolve to a machine's dedicated ingredient.
     */
    public static boolean ingredientUsesDedicatedSlot(
        final MachineProfile profile,
        final String recipeIngredient
    ) {
        if (!profile.hasDedicatedInputSlot()) {
            return false;
        }
        if (profile.dedicatedInputIngredient().filter(recipeIngredient::equals).isPresent()) {
            return true;
        }
        return profile.dedicatedInputIngredient()
            .flatMap(ItemIngredient::parse)
            .flatMap(dedicated -> ItemIngredient.parse(recipeIngredient)
                .map(ingredient -> ingredientsOverlap(ingredient, dedicated)))
            .orElse(false);
    }

    private static boolean ingredientsOverlap(
        final ItemIngredient left,
        final ItemIngredient right
    ) {
        if (!left.tag() && !right.tag()) {
            return left.id().equals(right.id());
        }
        if (left.tag() && right.tag()) {
            final TagKey<Item> rightTag = TagKey.create(Registries.ITEM, right.id());
            return StreamSupport.stream(BuiltInRegistries.ITEM
                    .getTagOrEmpty(TagKey.create(Registries.ITEM, left.id()))
                    .spliterator(), false)
                .anyMatch(holder -> holder.is(rightTag));
        }
        final ItemIngredient tag = left.tag() ? left : right;
        final ItemIngredient exact = left.tag() ? right : left;
        final TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tag.id());
        return BuiltInRegistries.ITEM.get(exact.id()).filter(holder -> holder.is(tagKey)).isPresent();
    }

    private static String itemId(final ItemStack stack) {
        final Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static String fluidId(final FluidStack stack) {
        final Identifier id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id == null ? "minecraft:empty" : id.toString();
    }

    private static boolean inputsReady(
        final MachineProfile profile,
        final String recipe,
        final List<MissingInput> missing,
        final List<WrongInput> wrong
    ) {
        return !recipe.isEmpty()
            && missing.isEmpty()
            && (!profile.rejectsUnexpectedInputs() || wrong.isEmpty());
    }

    private static boolean validate(final Identifier id, final MachineRecipeDefinition recipe) {
        final Optional<MachineProfile> profile = MachineProfiles.forRecipeType(recipe.machine());
        final boolean machineValid = profile
            .filter(value -> value.hasFuelSlot() == recipe.requiresFuel())
            .filter(value -> recipe.inputs().size() <= value.inputSlots())
            .filter(value -> recipe.outputs().size() <= value.outputSlots())
            .isPresent();
        final boolean countsValid = recipe.processingTime() > 0 && recipe.altarPower() >= 0
            && recipe.inputs().stream().allMatch(input -> input.count() > 0)
            && recipe.outputs().stream().allMatch(output -> output.count() > 0);
        final boolean inputsValid = recipe.inputs().stream()
            .map(input -> ItemIngredient.parse(input.ingredient()))
            .allMatch(parsed -> parsed.filter(ItemIngredient::isResolvable).isPresent());
        final boolean fluidValid = recipe.fluid()
            .map(input -> profile.filter(MachineProfile::supportsFluids).isPresent()
                && FluidIngredient.parse(input.ingredient()).filter(FluidIngredient::isResolvable).isPresent())
            .orElse(true);
        final boolean outputsValid = recipe.outputs().stream().allMatch(output -> {
            final Identifier itemId = Identifier.tryParse(output.item());
            return itemId != null && BuiltInRegistries.ITEM.containsKey(itemId);
        });
        if (!machineValid || !countsValid || !inputsValid || !fluidValid || !outputsValid) {
            Warlockery.LOGGER.error("Skipping invalid Warlockery machine recipe {}", id);
            return false;
        }
        return true;
    }

    public List<Identifier> ids() {
        return List.copyOf(catalog.definitions().keySet());
    }

    public Optional<Match> byId(final Identifier id) {
        return catalog.definition(id).map(recipe -> new Match(id, recipe));
    }

    public List<Match> all() {
        return catalog.definitions().entrySet().stream()
            .map(entry -> new Match(entry.getKey(), entry.getValue()))
            .toList();
    }

    public long revision() {
        return revision;
    }

    public record Match(Identifier id, MachineRecipeDefinition recipe) {
    }

    public record MissingInput(String ingredient, int count) {
        public static final Codec<MissingInput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ingredient").forGetter(MissingInput::ingredient),
            Codec.INT.fieldOf("count").forGetter(MissingInput::count)
        ).apply(instance, MissingInput::new));
    }

    public record WrongInput(String item, int count) {
        public static final Codec<WrongInput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("item").forGetter(WrongInput::item),
            Codec.INT.fieldOf("count").forGetter(WrongInput::count)
        ).apply(instance, WrongInput::new));
    }

    public record Diagnostic(
        String recipe,
        String output,
        int processingTime,
        List<MissingInput> missing,
        List<WrongInput> wrong
    ) {
        public static final Diagnostic EMPTY = new Diagnostic("", "", 0, List.of(), List.of());
        public static final Codec<Diagnostic> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("recipe").forGetter(Diagnostic::recipe),
            Codec.STRING.fieldOf("output").forGetter(Diagnostic::output),
            Codec.INT.fieldOf("processing_time").forGetter(Diagnostic::processingTime),
            MissingInput.CODEC.listOf().fieldOf("missing").forGetter(Diagnostic::missing),
            WrongInput.CODEC.listOf().fieldOf("wrong").forGetter(Diagnostic::wrong)
        ).apply(instance, Diagnostic::new));

        public Diagnostic {
            missing = List.copyOf(missing);
            wrong = List.copyOf(wrong);
        }

        public boolean isEmpty() {
            return recipe.isEmpty() && missing.isEmpty() && wrong.isEmpty();
        }

        public boolean inputsReady(final MachineProfile profile) {
            return MachineRecipeManager.inputsReady(profile, recipe, missing, wrong);
        }
    }

    private record Candidate(
        Identifier id,
        MachineRecipeDefinition recipe,
        String output,
        int matched,
        List<MissingInput> missing,
        List<WrongInput> wrong
    ) {
        private int score() {
            final int missingCount = missing.stream().mapToInt(MissingInput::count).sum();
            final int wrongCount = wrong.stream().mapToInt(WrongInput::count).sum();
            return matched * 100 - missingCount * 12 - wrongCount * 20;
        }

        private boolean inputsReady(final MachineProfile profile) {
            return MachineRecipeManager.inputsReady(profile, id.toString(), missing, wrong);
        }
    }
}
