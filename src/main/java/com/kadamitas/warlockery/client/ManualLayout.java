package com.kadamitas.warlockery.client;

import java.util.List;

record ManualLayout(
    int left,
    int top,
    int width,
    int height,
    int pageInset,
    int navigationWidth,
    int contentWidth,
    int gutter,
    int controlRows,
    int sectionRows
) {
    private static final int MAX_BOOK_WIDTH = 780;
    private static final int MAX_BOOK_HEIGHT = 420;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    static ManualLayout calculate(final int screenWidth, final int screenHeight) {
        final int horizontalMargin = screenWidth < 400 ? 4 : 12;
        final int verticalMargin = screenHeight < 300 ? 4 : 12;
        final int bookWidth = Math.max(1, Math.min(MAX_BOOK_WIDTH, screenWidth - horizontalMargin * 2));
        final int bookHeight = Math.max(1, Math.min(MAX_BOOK_HEIGHT, screenHeight - verticalMargin * 2));
        final int left = (screenWidth - bookWidth) / 2;
        final int top = (screenHeight - bookHeight) / 2;
        final int pageInset = bookWidth < 360 ? 4 : 8;
        final int gutter = bookWidth < 520 ? 8 : 18;
        final int availableWidth = Math.max(2, bookWidth - pageInset * 2 - gutter);
        final int navigationWidth = bookWidth < 520
            ? Math.min(132, Math.max(88, availableWidth / 3))
            : availableWidth / 2;
        final int contentWidth = Math.max(1, availableWidth - navigationWidth);
        final int controlRows = contentWidth < 270 ? 2 : 1;
        final int sectionRows = Math.max(1, (bookHeight - 103) / 24);
        return new ManualLayout(
            left,
            top,
            bookWidth,
            bookHeight,
            pageInset,
            navigationWidth,
            contentWidth,
            gutter,
            controlRows,
            sectionRows
        );
    }

    int right() {
        return left + width;
    }

    int bottom() {
        return top + height;
    }

    int navigationLeft() {
        return left + pageInset;
    }

    int navigationRight() {
        return navigationLeft() + navigationWidth;
    }

    int contentLeft() {
        return navigationRight() + gutter;
    }

    int contentRight() {
        return contentLeft() + contentWidth;
    }

    int spine() {
        return navigationRight() + gutter / 2;
    }

    int controlTop() {
        return bottom() - pageInset - controlsHeight() - 5;
    }

    int bodyTextTop() {
        return top + 46;
    }

    int bodyTextBottom() {
        return controlTop() - 18;
    }

    int bodyLineCapacity() {
        return Math.max(1, (bodyTextBottom() - bodyTextTop()) / 12);
    }

    int sectionListTop() {
        return top + 79;
    }

    int textInset() {
        return Math.min(14, Math.max(6, navigationWidth / 12));
    }

    List<Bounds> controls() {
        final int inset = Math.min(14, Math.max(6, contentWidth / 12));
        final int x = contentLeft() + inset;
        final int available = Math.max(3, contentWidth - inset * 2);
        if (controlRows == 1) {
            final int width = Math.max(1, (available - BUTTON_GAP * 2) / 3);
            return List.of(
                new Bounds(x, controlTop(), width, BUTTON_HEIGHT),
                new Bounds(x + width + BUTTON_GAP, controlTop(), width, BUTTON_HEIGHT),
                new Bounds(x + (width + BUTTON_GAP) * 2, controlTop(), width, BUTTON_HEIGHT)
            );
        }
        final int halfWidth = Math.max(1, (available - BUTTON_GAP) / 2);
        return List.of(
            new Bounds(x, controlTop(), halfWidth, BUTTON_HEIGHT),
            new Bounds(x + halfWidth + BUTTON_GAP, controlTop(), halfWidth, BUTTON_HEIGHT),
            new Bounds(x, controlTop() + BUTTON_HEIGHT + BUTTON_GAP, available, BUTTON_HEIGHT)
        );
    }

    boolean overNavigation(final double mouseX, final double mouseY) {
        return mouseX >= navigationLeft() && mouseX < navigationRight()
            && mouseY >= top && mouseY < bottom();
    }

    boolean overContent(final double mouseX, final double mouseY) {
        return mouseX >= contentLeft() && mouseX < contentRight()
            && mouseY >= top && mouseY < bottom();
    }

    private int controlsHeight() {
        return controlRows * BUTTON_HEIGHT + (controlRows - 1) * BUTTON_GAP;
    }

    record Bounds(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean overlaps(final Bounds other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }
}
