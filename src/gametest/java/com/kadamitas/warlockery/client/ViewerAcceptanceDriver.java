package com.kadamitas.warlockery.client;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;

interface ViewerAcceptanceDriver {
    record Stack(String kind, String id, long amount) {}
    record Entry(String id, String category, Object handle, List<List<Stack>> inputs, List<List<Stack>> catalysts,
                 List<Stack> outputs, boolean transfer, List<Stack> stations) {}
    record Point(int x, int y) {}
    record Visible(List<List<Stack>> slots, Point input, Point output, Point transfer, Point nextText,
                   int textPage, int textPages, int x, int y, int width, int height) {}

    boolean ready();
    List<Entry> entries();
    void open(Entry recipe);
    boolean isRecipeScreen(Screen screen);
    Visible visible(Entry recipe, Screen screen);
    boolean indexed(Entry recipe, boolean output, int slot, int alternative);
    String currentCategory(Screen screen);
    boolean containsVisibleResult(Screen screen);

    static ViewerAcceptanceDriver create(String viewer) {
        return switch (viewer) {
            case "emi" -> new EmiAcceptanceDriver();
            case "rrv" -> new RrvAcceptanceDriver();
            case "rei" -> throw new UnsupportedOperationException("REI client acceptance driver is not implemented; no REI acceptance coverage is available.");
            default -> throw new IllegalArgumentException("Unknown viewer: " + viewer);
        };
    }
}
