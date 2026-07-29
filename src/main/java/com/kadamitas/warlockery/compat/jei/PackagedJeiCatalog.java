package com.kadamitas.warlockery.compat.jei;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

public final class PackagedJeiCatalog {
    private static final String INDEX_ROOT = "/assets/warlockery/jei_catalog/";
    private static final String DATA_ROOT = "/data/warlockery/";

    private PackagedJeiCatalog() {
    }

    public static List<MachineRecipeManager.Match> machines() {
        return MachineCatalog.RECIPES;
    }

    public static List<RitualManager.Entry> rituals() {
        return RitualCatalog.RITUALS;
    }

    private static <T> List<Loaded<T>> load(
        final String index,
        final String directory,
        final Codec<T> codec
    ) {
        try (var lines = reader(INDEX_ROOT + index).lines()) {
            return lines
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .map(id -> new Loaded<>(id, decode(DATA_ROOT + directory + "/" + id + ".json", codec)))
                .toList();
        }
    }

    private static <T> T decode(final String path, final Codec<T> codec) {
        try (BufferedReader reader = reader(path)) {
            return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                .getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to close " + path, exception);
        }
    }

    private static BufferedReader reader(final String path) {
        final InputStream stream = Objects.requireNonNull(
            PackagedJeiCatalog.class.getResourceAsStream(path),
            "Missing packaged JEI resource " + path
        );
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private record Loaded<T>(String id, T value) {
        private Identifier identifier() {
            return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id);
        }
    }

    private static final class MachineCatalog {
        private static final List<MachineRecipeManager.Match> RECIPES = load(
            "machines.txt",
            "warlockery_machine",
            MachineRecipeDefinition.CODEC
        ).stream().map(entry -> new MachineRecipeManager.Match(entry.identifier(), entry.value())).toList();
    }

    private static final class RitualCatalog {
        private static final List<RitualManager.Entry> RITUALS = load(
            "rituals.txt",
            "ritual",
            RitualDefinition.CODEC
        ).stream().map(entry -> new RitualManager.Entry(entry.identifier(), entry.value())).toList();
    }
}
