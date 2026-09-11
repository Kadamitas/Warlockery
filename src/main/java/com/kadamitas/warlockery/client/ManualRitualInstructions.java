package com.kadamitas.warlockery.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Preparation for runtime requirements that are not expressible by the ritual's ingredient JSON. */
public final class ManualRitualInstructions {
    private ManualRitualInstructions() {}

    static Component additional(final String id, final JsonObject ritual) {
        final String action = ritual.get("action").getAsString();
        final String target = ritual.has("target") ? ritual.get("target").getAsString() : "";
        final int radius = ritual.has("radius") ? ritual.get("radius").getAsInt() : 6;
        final var text = Component.empty();
        for (String key : keys(id, action, target)) {
            text.append("\n\n").append(Component.translatable("manual.warlockery.ritual.setup." + key,
                radius, Math.clamp(radius * 2, 8, 24)));
        }
        return text;
    }

    public static List<String> keys(final String id, final String action, final String target) {
        final List<String> keys = new ArrayList<>();
        keys.add("site");
        switch (action) {
            case "summon_entity", "call_familiar", "divorce", "earths_wrath", "climate_shift", "transform_nami",
                "bind_waystone", "copy_waystone", "teleport_waystone", "bind_circle", "glyph_transform" -> keys.add(action);
            case "prior_incarnation", "manifest", "cleanse", "bind_item", "teleport_entity", "hex" -> {
                keys.add("sample");
                keys.add(action);
            }
            case "marriage" -> { keys.add("sample"); keys.add("marriage"); }
            case "bind_entity" -> keys.add(target.equals("spectral") ? "bind_spectral" : "bind_familiar");
            case "infuse_path", "recharge_path" -> keys.add("infusion_target");
            case "summon_huntsman" -> keys.add("huntsman");
            case "bind_fetish" -> keys.add("fetish");
            case "cook" -> keys.add("cook");
            default -> { }
        }
        if (action.equals("transform_werewolf") || action.equals("hex") && target.equals("corrupt_doll")) {
            keys.add("owned_familiar");
        }
        return List.copyOf(keys);
    }
}
