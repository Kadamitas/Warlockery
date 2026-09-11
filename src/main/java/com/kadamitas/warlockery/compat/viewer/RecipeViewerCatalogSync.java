package com.kadamitas.warlockery.compat.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.custom.CustomBrewComponentDefinition;
import com.kadamitas.warlockery.brew.custom.CustomBrewDefinitionManager;
import com.kadamitas.warlockery.compat.jei.CustomBrewJeiRecipe;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.network.RecipeViewerCatalogPayload;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualValidator;
import com.kadamitas.warlockery.util.FluidIngredient;
import com.kadamitas.warlockery.util.ItemIngredient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

public final class RecipeViewerCatalogSync {
    private static final int MAX_ENTRIES = 16_384;
    private static final AtomicLong SERVER_REVISION = new AtomicLong();
    private static final Receiver CLIENT = new Receiver();

    private RecipeViewerCatalogSync() { }

    public static synchronized void beginConnection(final Object connection) {
        CLIENT.connect(connection);
        RecipeViewerCatalog.beginConnection();
    }

    public static synchronized void disconnect(final Object connection) {
        if (CLIENT.disconnect(connection)) RecipeViewerCatalog.disconnect();
    }

    public static synchronized void accept(final Object connection, final RecipeViewerCatalogPayload payload) {
        CLIENT.accept(connection, payload).ifPresent(RecipeViewerCatalog::publish);
    }

    public static List<RecipeViewerCatalogPayload> serverPackets() {
        final var components = CustomBrewDefinitionManager.INSTANCE;
        final var snapshot = new RecipeViewerCatalog.Snapshot(MachineRecipeManager.INSTANCE.all(),
            RitualManager.INSTANCE.all(), components.ids().stream()
                .map(id -> new CustomBrewJeiRecipe(id, components.byId(id).orElseThrow())).toList());
        return packets(SERVER_REVISION.incrementAndGet(), snapshot);
    }

    public static List<RecipeViewerCatalogPayload> packets(
        final long revision, final RecipeViewerCatalog.Snapshot snapshot
    ) {
        final byte[] bytes = encode(snapshot);
        final int chunks = (bytes.length + RecipeViewerCatalogPayload.CHUNK_BYTES - 1)
            / RecipeViewerCatalogPayload.CHUNK_BYTES;
        final List<RecipeViewerCatalogPayload> result = new ArrayList<>(chunks);
        for (int index = 0; index < chunks; index++) {
            final int start = index * RecipeViewerCatalogPayload.CHUNK_BYTES;
            result.add(new RecipeViewerCatalogPayload(revision, index, chunks, bytes.length,
                Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + RecipeViewerCatalogPayload.CHUNK_BYTES))));
        }
        return List.copyOf(result);
    }

    public static byte[] encode(final RecipeViewerCatalog.Snapshot snapshot) {
        if ((long) snapshot.machines().size() + snapshot.rituals().size() + snapshot.customBrews().size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Recipe catalog has too many entries");
        }
        final JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.add("machines", entries(snapshot.machines(), MachineRecipeManager.Match::id,
            MachineRecipeManager.Match::recipe, MachineRecipeDefinition.CODEC));
        root.add("rituals", entries(snapshot.rituals(), RitualManager.Entry::id,
            RitualManager.Entry::definition, RitualDefinition.CODEC));
        root.add("custom_brews", entries(snapshot.customBrews(), CustomBrewJeiRecipe::id,
            CustomBrewJeiRecipe::definition, CustomBrewComponentDefinition.CODEC));
        final byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > RecipeViewerCatalogPayload.MAX_BYTES) {
            throw new IllegalArgumentException("Recipe catalog exceeds the sync size limit");
        }
        return bytes;
    }

    public static RecipeViewerCatalog.Snapshot decode(final byte[] bytes) {
        if (bytes.length == 0 || bytes.length > RecipeViewerCatalogPayload.MAX_BYTES) {
            throw new IllegalArgumentException("Invalid recipe catalog size");
        }
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid recipe catalog encoding", exception);
        }
        checkNesting(json);
        final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("format") || root.get("format").getAsInt() != 1) {
            throw new IllegalArgumentException("Unsupported recipe catalog format");
        }
        validateJson(root);
        final JsonArray machines = root.getAsJsonArray("machines");
        final JsonArray rituals = root.getAsJsonArray("rituals");
        final JsonArray components = root.getAsJsonArray("custom_brews");
        if (machines == null || rituals == null || components == null
            || (long) machines.size() + rituals.size() + components.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid recipe catalog entries");
        }
        final var snapshot = new RecipeViewerCatalog.Snapshot(
            readEntries(machines, MachineRecipeDefinition.CODEC, MachineRecipeManager.Match::new),
            readEntries(rituals, RitualDefinition.CODEC, RitualManager.Entry::new),
            readEntries(components, CustomBrewComponentDefinition.CODEC, CustomBrewJeiRecipe::new));
        snapshot.machines().forEach(match -> validateMachine(match.recipe()));
        snapshot.rituals().forEach(entry -> {
            if (!RitualValidator.isStructurallyValid(entry.definition())
                || entry.definition().requirements().ingredients().stream()
                    .anyMatch(input -> ItemIngredient.parse(input.ingredient()).isEmpty())) {
                throw new IllegalArgumentException("Invalid ritual in recipe catalog");
            }
        });
        return snapshot;
    }

    private static void validateMachine(final MachineRecipeDefinition definition) {
        final var profile = MachineProfiles.forRecipeType(definition.machine()).orElseThrow(() ->
            new IllegalArgumentException("Unknown recipe catalog machine"));
        if (profile.hasFuelSlot() != definition.requiresFuel() || definition.inputs().size() > profile.inputSlots()
            || definition.outputs().size() > profile.outputSlots()
            || definition.inputs().stream().anyMatch(input -> ItemIngredient.parse(input.ingredient()).isEmpty())
            || definition.outputs().stream().anyMatch(output -> Identifier.tryParse(output.item()) == null)
            || definition.fluid().stream().anyMatch(input -> FluidIngredient.parse(input.ingredient()).isEmpty())) {
            throw new IllegalArgumentException("Invalid machine in recipe catalog");
        }
        MachineRecipeSlotPlan.inputSlots(profile, definition);
    }

    private static <T, D> JsonArray entries(
        final List<T> entries, final Function<T, Identifier> id, final Function<T, D> value, final Codec<D> codec
    ) {
        final JsonArray result = new JsonArray();
        for (T entry : entries) {
            final JsonObject json = new JsonObject();
            json.addProperty("id", id.apply(entry).toString());
            json.add("definition", codec.encodeStart(JsonOps.INSTANCE, value.apply(entry))
                .getOrThrow(message -> new IllegalArgumentException(message)));
            result.add(json);
        }
        return result;
    }

    private static <D, T> List<T> readEntries(
        final JsonArray entries, final Codec<D> codec, final BiFunction<Identifier, D, T> factory
    ) {
        final HashSet<Identifier> ids = new HashSet<>();
        final List<T> result = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            final JsonObject json = entry.getAsJsonObject();
            final Identifier id = Identifier.parse(json.get("id").getAsString());
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate recipe catalog id");
            result.add(factory.apply(id, codec.parse(JsonOps.INSTANCE, json.get("definition"))
                .getOrThrow(message -> new IllegalArgumentException(message))));
        }
        return List.copyOf(result);
    }

    private static void validateJson(final JsonElement element) {
        if (element.isJsonArray()) {
            if (element.getAsJsonArray().size() > MAX_ENTRIES) throw new IllegalArgumentException("Oversized recipe list");
            element.getAsJsonArray().forEach(RecipeViewerCatalogSync::validateJson);
        } else if (element.isJsonObject()) {
            if (element.getAsJsonObject().size() > 128) throw new IllegalArgumentException("Oversized recipe object");
            element.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getKey().length() > 256) throw new IllegalArgumentException("Oversized recipe key");
                validateJson(entry.getValue());
            });
        } else if (element.isJsonPrimitive() && element.getAsString().length() > 32_768) {
            throw new IllegalArgumentException("Oversized recipe value");
        }
    }

    private static void checkNesting(final String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            final char value = json.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') quoted = true;
            else if (value == '{' || value == '[') {
                if (++depth > 32) throw new IllegalArgumentException("Recipe catalog nesting exceeds limit");
            } else if (value == '}' || value == ']') depth--;
        }
    }

    public static final class Receiver {
        private Object connection;
        private long completedRevision;
        private long pendingRevision;
        private int totalBytes;
        private byte[][] chunks;
        private int received;

        public synchronized void connect(final Object connection) {
            this.connection = Objects.requireNonNull(connection, "connection");
            completedRevision = 0;
            clearPending();
        }

        public synchronized boolean disconnect(final Object connection) {
            if (this.connection != connection) return false;
            this.connection = null;
            completedRevision = 0;
            clearPending();
            return true;
        }

        public synchronized Optional<RecipeViewerCatalog.Snapshot> accept(
            final Object connection, final RecipeViewerCatalogPayload payload
        ) {
            if (this.connection == null || this.connection != connection || payload.revision() <= completedRevision
                || payload.revision() < pendingRevision) return Optional.empty();
            if (payload.revision() > pendingRevision) {
                clearPending();
                pendingRevision = payload.revision();
                totalBytes = payload.totalBytes();
                chunks = new byte[payload.chunkCount()][];
            }
            if (payload.totalBytes() != totalBytes || payload.chunkCount() != chunks.length) {
                rejectPending();
                return Optional.empty();
            }
            final byte[] data = payload.data();
            final int index = payload.chunkIndex();
            if (chunks[index] != null) {
                if (!Arrays.equals(chunks[index], data)) rejectPending();
                return Optional.empty();
            }
            chunks[index] = data;
            if (++received != chunks.length) return Optional.empty();
            final byte[] bytes = new byte[totalBytes];
            for (int chunk = 0; chunk < chunks.length; chunk++) {
                System.arraycopy(chunks[chunk], 0, bytes, chunk * RecipeViewerCatalogPayload.CHUNK_BYTES, chunks[chunk].length);
            }
            rejectPending();
            try {
                return Optional.of(decode(bytes));
            } catch (RuntimeException exception) {
                Warlockery.LOGGER.warn("Ignoring invalid recipe viewer catalog: {}", exception.getMessage());
                return Optional.empty();
            }
        }

        private void rejectPending() {
            completedRevision = pendingRevision;
            clearPending();
        }

        private void clearPending() {
            pendingRevision = 0;
            totalBytes = 0;
            chunks = null;
            received = 0;
        }
    }
}
