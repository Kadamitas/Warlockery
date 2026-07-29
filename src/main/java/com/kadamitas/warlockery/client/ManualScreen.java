package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.item.ManualProfile;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ManualScreen extends Screen {
    private static final int PANEL_HEIGHT = 390;
    private ManualProfile selectedManual;
    private String selectedSection;
    private String query = "";
    private List<ManualProfile> filteredManuals = ManualProfile.profiles();
    private int manualPage;
    private boolean searchDirty;
    private boolean refocusSearch;
    private EditBox searchBox;

    private ManualScreen(final ManualProfile initial) {
        super(Component.translatable("screen.warlockery.manual.title"));
        selectedManual = initial;
        selectedSection = initial.sections().getFirst();
    }

    public static void open(final ManualProfile initial) {
        Minecraft.getInstance().gui.setScreen(new ManualScreen(initial));
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (searchDirty) {
            searchDirty = false;
            refocusSearch = true;
            manualPage = 0;
            filteredManuals = ManualProfile.search(query, key -> Component.translatable(key).getString());
            if (!filteredManuals.isEmpty() && !filteredManuals.contains(selectedManual)) {
                selectManual(filteredManuals.getFirst());
            }
            rebuildWidgets();
        }
    }

    @Override
    protected void rebuildWidgets() {
        if (width <= 0 || height <= 0) {
            return;
        }
        clearWidgets();
        final Layout layout = layout();
        searchBox = addRenderableWidget(new EditBox(
            font,
            layout.left() + 10,
            layout.top() + 25,
            layout.manualWidth() - 20,
            20,
            Component.translatable("screen.warlockery.manual.search")
        ));
        searchBox.setMaxLength(80);
        searchBox.setValue(query);
        searchBox.setHint(Component.translatable("screen.warlockery.manual.search"));
        searchBox.setResponder(value -> {
            query = value;
            searchDirty = true;
        });
        if (refocusSearch) {
            refocusSearch = false;
            setInitialFocus(searchBox);
            searchBox.setFocused(true);
        }

        final int pageSize = pageSize(layout);
        final int start = manualPage * pageSize;
        filteredManuals.stream().skip(start).limit(pageSize).forEach(profile -> {
            final int row = filteredManuals.indexOf(profile) - start;
            final Component label = Component.literal(profile.equals(selectedManual) ? "▶ " : "")
                .append(Component.translatable(profile.translatedTitleKey()));
            addRenderableWidget(Button.builder(label, button -> {
                selectManual(profile);
                rebuildWidgets();
            }).bounds(layout.left() + 10, layout.top() + 57 + row * 23, layout.manualWidth() - 20, 20).build());
        });

        final Button previousManualPage = addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            manualPage = Math.max(0, manualPage - 1);
            rebuildWidgets();
        }).bounds(layout.left() + 10, layout.bottom() - 30, 28, 20).build());
        previousManualPage.active = manualPage > 0;
        final Button nextManualPage = addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            manualPage = Math.min(lastManualPage(pageSize), manualPage + 1);
            rebuildWidgets();
        }).bounds(layout.left() + layout.manualWidth() - 38, layout.bottom() - 30, 28, 20).build());
        nextManualPage.active = manualPage < lastManualPage(pageSize);

        if (selectedManual != null && !filteredManuals.isEmpty()) {
            selectedManual.sections().forEach(section -> {
                final int row = selectedManual.sections().indexOf(section);
                final Component label = Component.literal(section.equals(selectedSection) ? "▶ " : "")
                    .append(Component.translatable(selectedManual.translatedSectionTitleKey(section)));
                addRenderableWidget(Button.builder(label, button -> {
                    selectedSection = section;
                    rebuildWidgets();
                }).bounds(layout.chapterLeft() + 10, layout.top() + 45 + row * 23, layout.chapterWidth() - 20, 20).build());
            });

            addRenderableWidget(Button.builder(Component.translatable("screen.warlockery.manual.previous"), button -> {
                selectedSection = selectedManual.adjacentSection(selectedSection, -1);
                rebuildWidgets();
            }).bounds(layout.contentLeft() + 10, layout.bottom() - 30, 105, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.warlockery.manual.next"), button -> {
                selectedSection = selectedManual.adjacentSection(selectedSection, 1);
                rebuildWidgets();
            }).bounds(layout.contentLeft() + 121, layout.bottom() - 30, 105, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.warlockery.manual.close"), button -> onClose())
            .bounds(layout.right() - 70, layout.bottom() - 30, 60, 20).build());
    }

    private void selectManual(final ManualProfile profile) {
        selectedManual = profile;
        selectedSection = profile.sections().getFirst();
    }

    private int pageSize(final Layout layout) {
        return Math.max(3, Math.min(12, (layout.height() - 100) / 23));
    }

    private int lastManualPage(final int pageSize) {
        return Math.max(0, (filteredManuals.size() - 1) / pageSize);
    }

    private Layout layout() {
        final int panelWidth = Math.min(920, Math.max(320, width - 20));
        final int panelHeight = Math.min(PANEL_HEIGHT, Math.max(300, height - 20));
        final int left = (width - panelWidth) / 2;
        final int top = (height - panelHeight) / 2;
        final int manualWidth = Math.min(220, Math.max(110, panelWidth / 4));
        final int chapterWidth = Math.min(185, Math.max(110, panelWidth / 4));
        return new Layout(left, top, panelWidth, panelHeight, manualWidth, chapterWidth);
    }

    @Override
    public void extractRenderState(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick
    ) {
        final Layout layout = layout();
        graphics.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xF0100B18);
        graphics.fill(layout.chapterLeft(), layout.top() + 20, layout.chapterLeft() + 1, layout.bottom() - 40, 0xFF79518D);
        graphics.fill(layout.contentLeft(), layout.top() + 20, layout.contentLeft() + 1, layout.bottom() - 40, 0xFF79518D);
        final var text = graphics.textRenderer();
        text.accept(layout.left() + 10, layout.top() + 8, title.copy().withColor(0xDDAAFF));
        text.accept(layout.chapterLeft() + 10, layout.top() + 27,
            Component.translatable("screen.warlockery.manual.chapters").withColor(0xCFA8E0));

        if (filteredManuals.isEmpty()) {
            text.accept(layout.left() + 10, layout.top() + 60,
                Component.translatable("screen.warlockery.manual.no_results").withColor(0xFF7777));
        } else {
            final int pageSize = pageSize(layout);
            text.accept(layout.left() + 44, layout.bottom() - 25, Component.translatable(
                "screen.warlockery.manual.page", manualPage + 1, lastManualPage(pageSize) + 1
            ).withColor(0xAAAAAA));
        }

        if (selectedManual != null && !filteredManuals.isEmpty()) {
            int y = layout.top() + 28;
            text.accept(layout.contentLeft() + 12, y,
                Component.translatable(selectedManual.translatedTitleKey()).withColor(0xDDAAFF));
            y += 18;
            text.accept(layout.contentLeft() + 12, y,
                Component.translatable(selectedManual.translatedSectionTitleKey(selectedSection)).withColor(0xFFFFFF));
            y += 18;
            final int contentWidth = layout.right() - layout.contentLeft() - 24;
            for (final var line : font.split(
                Component.translatable(selectedManual.translatedSectionKey(selectedSection)).withColor(0xDDDDDD),
                contentWidth
            )) {
                text.accept(layout.contentLeft() + 12, y, line);
                y += 11;
            }
            text.accept(layout.contentLeft() + 236, layout.bottom() - 25, Component.translatable(
                "screen.warlockery.manual.chapter",
                selectedManual.sections().indexOf(selectedSection) + 1,
                selectedManual.sections().size()
            ).withColor(0xAAAAAA));
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Layout(
        int left,
        int top,
        int width,
        int height,
        int manualWidth,
        int chapterWidth
    ) {
        int right() {
            return left + width;
        }

        int bottom() {
            return top + height;
        }

        int chapterLeft() {
            return left + manualWidth;
        }

        int contentLeft() {
            return chapterLeft() + chapterWidth;
        }
    }
}
