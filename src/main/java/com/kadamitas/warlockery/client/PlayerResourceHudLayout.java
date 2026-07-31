package com.kadamitas.warlockery.client;

public record PlayerResourceHudLayout(
    int x,
    int top,
    int rowStep,
    int rowCount,
    int rowHeight
) {
    public PlayerResourceHudLayout {
        x = Math.max(0, x);
        top = Math.max(0, top);
        rowStep = Math.max(0, rowStep);
        rowCount = Math.max(0, rowCount);
        rowHeight = Math.max(0, rowHeight);
    }

    public static PlayerResourceHudLayout leftLane(
        final int rowCount,
        final int x,
        final int top,
        final int rowHeight,
        final int rowGap
    ) {
        final int safeHeight = Math.max(0, rowHeight);
        return new PlayerResourceHudLayout(
            x,
            top,
            safeHeight + Math.max(0, rowGap),
            rowCount,
            safeHeight
        );
    }

    public int rowY(final int index) {
        return top + Math.max(0, index) * rowStep;
    }

    public int stackBottom() {
        return rowCount == 0 ? top : rowY(rowCount - 1) + rowHeight;
    }

    public int nextStackTop(final int originalTop, final int gap) {
        return Math.max(Math.max(0, originalTop), stackBottom() + Math.max(0, gap));
    }

    public static int visibleRowCount(
        final int guiHeight,
        final int startY,
        final int rowHeight,
        final int maximumRows,
        final int reservedBottom
    ) {
        if (rowHeight <= 0 || maximumRows <= 0) {
            return 0;
        }
        final int availableHeight = Math.max(
            0,
            guiHeight - Math.max(0, reservedBottom) - Math.max(0, startY)
        );
        return Math.clamp(availableHeight / rowHeight, 0, maximumRows);
    }
}
