package com.kadamitas.warlockery.client;

record RitualSelectionLayout(int left, int top, int width, int height, int listWidth) {
    static RitualSelectionLayout calculate(final int screenWidth, final int screenHeight) {
        final int width = Math.min(620, Math.max(160, screenWidth - 24));
        final int height = Math.min(280, Math.max(150, screenHeight - 16));
        return new RitualSelectionLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
            width, height, Math.min(220, width * 2 / 5));
    }

    int actionY() { return top + height - 28; }
    int detailX() { return left + listWidth + 12; }
    int detailWidth() { return width - listWidth - 24; }
    int detailTop() { return top + 28; }
    int detailBottom() { return actionY() - 28; }
    int detailCapacity() { return Math.max(1, (detailBottom() - detailTop()) / 11); }
    int rows() { return Math.max(1, Math.min(7, (actionY() - top - 32) / 25)); }
}
