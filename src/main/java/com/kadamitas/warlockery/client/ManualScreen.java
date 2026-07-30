package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.ManualView;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class ManualScreen extends Screen {
    private final ManualView view;
    private final ManualProfile manual;
    private final List<String> availableSections;
    private String selectedSection;
    private String query = "";
    private List<String> filteredSections;
    private boolean searchDirty;
    private boolean refocusSearch;
    private int sectionOffset;
    private int bodyPage;
    private EditBox searchBox;

    private ManualScreen(final ManualView view) {
        super(Component.translatable(view.profile().translatedTitleKey()));
        this.view = view;
        manual = view.profile();
        availableSections = view.sections();
        selectedSection = availableSections.getFirst();
        filteredSections = availableSections;
    }

    public static void open(final ManualView view) {
        Minecraft.getInstance().gui.setScreen(new ManualScreen(view));
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (!searchDirty) {
            return;
        }
        searchDirty = false;
        refocusSearch = true;
        filteredSections = searchSections(query);
        sectionOffset = 0;
        bodyPage = 0;
        if (!filteredSections.isEmpty() && !filteredSections.contains(selectedSection)) {
            selectedSection = filteredSections.getFirst();
        }
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        if (width <= 0 || height <= 0) {
            return;
        }
        clearWidgets();
        final ManualLayout layout = layout();
        keepSelectedSectionVisible(layout);
        final int navigationInset = layout.textInset();
        searchBox = addRenderableWidget(new EditBox(
            font,
            layout.navigationLeft() + navigationInset,
            layout.top() + 53,
            Math.max(20, layout.navigationWidth() - navigationInset * 2),
            20,
            ManualTypography.readable(Component.translatable("screen.warlockery.manual.search"))
        ));
        searchBox.setMaxLength(80);
        searchBox.setValue(query);
        searchBox.setHint(ManualTypography.readable(Component.translatable("screen.warlockery.manual.search")));
        searchBox.setResponder(value -> {
            query = value;
            searchDirty = true;
        });
        if (refocusSearch) {
            refocusSearch = false;
            setInitialFocus(searchBox);
            searchBox.setFocused(true);
        }

        final int sectionEnd = Math.min(filteredSections.size(), sectionOffset + layout.sectionRows());
        for (int index = sectionOffset; index < sectionEnd; index++) {
            final String section = filteredSections.get(index);
            final Component label = ManualTypography.readable(
                Component.literal(section.equals(selectedSection) ? "▶ " : "")
                    .append(Component.translatable(manual.translatedSectionTitleKey(section)))
            );
            final int row = index - sectionOffset;
            addRenderableWidget(Button.builder(label, button -> {
                selectedSection = section;
                bodyPage = 0;
                rebuildWidgets();
            }).bounds(
                layout.navigationLeft() + navigationInset,
                layout.sectionListTop() + row * 24,
                Math.max(20, layout.navigationWidth() - navigationInset * 2),
                20
            ).build());
        }

        final List<ManualLayout.Bounds> controls = layout.controls();
        addRenderableWidget(Button.builder(ManualTypography.readable(
            Component.translatable("screen.warlockery.manual.previous")), button ->
            navigate(-1, layout)).bounds(
                controls.get(0).x(), controls.get(0).y(), controls.get(0).width(), controls.get(0).height()
            ).build());
        addRenderableWidget(Button.builder(ManualTypography.readable(
            Component.translatable("screen.warlockery.manual.next")), button ->
            navigate(1, layout)).bounds(
                controls.get(1).x(), controls.get(1).y(), controls.get(1).width(), controls.get(1).height()
            ).build());
        addRenderableWidget(Button.builder(ManualTypography.readable(
            Component.translatable("screen.warlockery.manual.close")), button -> onClose())
            .bounds(
                controls.get(2).x(), controls.get(2).y(), controls.get(2).width(), controls.get(2).height()
            ).build());
    }

    private void navigate(final int direction, final ManualLayout layout) {
        final int pageCount = bodyPageCount(layout, selectedSection);
        if (direction > 0 && bodyPage + 1 < pageCount) {
            bodyPage++;
            return;
        }
        if (direction < 0 && bodyPage > 0) {
            bodyPage--;
            return;
        }
        selectedSection = adjacentSection(direction);
        query = "";
        filteredSections = availableSections;
        sectionOffset = 0;
        bodyPage = direction < 0 ? bodyPageCount(layout, selectedSection) - 1 : 0;
        rebuildWidgets();
    }

    private String adjacentSection(final int direction) {
        return view.adjacentSection(selectedSection, direction);
    }

    private List<String> searchSections(final String value) {
        final String needle = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return availableSections;
        }
        return availableSections.stream()
            .filter(section -> Component.translatable(manual.translatedSectionTitleKey(section)).getString()
                .toLowerCase(Locale.ROOT).contains(needle)
                || ManualArticleCatalog.article(manual, section).body().getString()
                    .toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }

    private ManualLayout layout() {
        return ManualLayout.calculate(width, height);
    }

    private void keepSelectedSectionVisible(final ManualLayout layout) {
        final int selectedIndex = filteredSections.indexOf(selectedSection);
        final int maximumOffset = Math.max(0, filteredSections.size() - layout.sectionRows());
        if (selectedIndex >= 0) {
            if (selectedIndex < sectionOffset) {
                sectionOffset = selectedIndex;
            } else if (selectedIndex >= sectionOffset + layout.sectionRows()) {
                sectionOffset = selectedIndex - layout.sectionRows() + 1;
            }
        }
        sectionOffset = Math.clamp(sectionOffset, 0, maximumOffset);
    }

    @Override
    public boolean mouseScrolled(
        final double mouseX,
        final double mouseY,
        final double scrollX,
        final double scrollY
    ) {
        if (scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        final ManualLayout layout = layout();
        final int direction = scrollY > 0.0D ? -1 : 1;
        if (layout.overNavigation(mouseX, mouseY) && filteredSections.size() > layout.sectionRows()) {
            final int maximumOffset = filteredSections.size() - layout.sectionRows();
            final int nextOffset = Math.clamp(sectionOffset + direction, 0, maximumOffset);
            if (nextOffset != sectionOffset) {
                sectionOffset = nextOffset;
                rebuildWidgets();
            }
            return true;
        }
        if (layout.overContent(mouseX, mouseY)) {
            final int maximumPage = bodyPageCount(layout, selectedSection) - 1;
            bodyPage = Math.clamp(bodyPage + direction, 0, maximumPage);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick
    ) {
        final ManualLayout layout = layout();
        drawBook(graphics, layout);
        final int navigationInset = layout.textInset();
        final int navigationTextX = layout.navigationLeft() + navigationInset;
        final int navigationTextWidth = Math.max(1, layout.navigationWidth() - navigationInset * 2);
        final int contentInset = Math.min(14, Math.max(6, layout.contentWidth() / 12));
        final int contentTextX = layout.contentLeft() + contentInset;
        final int contentTextWidth = Math.max(1, layout.contentWidth() - contentInset * 2);

        int titleY = layout.top() + 16;
        final List<FormattedCharSequence> manualTitle = font.split(
            ManualTypography.readable(title, 0x4A241B),
            navigationTextWidth
        );
        for (int index = 0; index < Math.min(2, manualTitle.size()); index++) {
            drawText(graphics, navigationTextX, titleY, manualTitle.get(index));
            titleY += 10;
        }
        drawText(graphics, navigationTextX, layout.top() + 40,
            ManualTypography.readable(Component.translatable("screen.warlockery.manual.chapters"), 0x6B3D27));

        if (filteredSections.isEmpty()) {
            drawText(graphics, navigationTextX, layout.top() + 82,
                ManualTypography.readable(Component.translatable("screen.warlockery.manual.no_results"), 0x9C302F));
        } else {
            if (sectionOffset > 0) {
                drawText(graphics, layout.navigationRight() - navigationInset - 6, layout.top() + 81,
                    ManualTypography.readable(Component.literal("↑"), 0x795A44));
            }
            if (sectionOffset + layout.sectionRows() < filteredSections.size()) {
                drawText(graphics, layout.navigationRight() - navigationInset - 6, layout.bottom() - 22,
                    ManualTypography.readable(Component.literal("↓"), 0x795A44));
            }
        }

        final List<FormattedCharSequence> sectionTitle = font.split(
            ManualTypography.readable(
                Component.translatable(manual.translatedSectionTitleKey(selectedSection)),
                0x5B1F31
            ),
            contentTextWidth
        );
        int sectionTitleY = layout.top() + 16;
        for (int index = 0; index < Math.min(2, sectionTitle.size()); index++) {
            drawText(graphics, contentTextX, sectionTitleY, sectionTitle.get(index));
            sectionTitleY += 10;
        }

        final List<FormattedCharSequence> bodyLines = bodyLines(selectedSection, contentTextWidth);
        final int lineCapacity = bodyLineCapacity(layout, selectedSection);
        final int pageCount = Math.max(1, Math.ceilDiv(bodyLines.size(), lineCapacity));
        bodyPage = Math.clamp(bodyPage, 0, pageCount - 1);
        final int firstLine = bodyPage * lineCapacity;
        final int lastLine = Math.min(bodyLines.size(), firstLine + lineCapacity);
        final ManualArticleCatalog.Article article = ManualArticleCatalog.article(manual, selectedSection);
        if (article.hasDiagram()) {
            drawCircleDiagram(graphics, layout, contentTextX, article);
        }
        int bodyY = layout.bodyTextTop() + (article.hasDiagram() ? 68 : 0);
        for (int index = firstLine; index < lastLine; index++) {
            drawText(graphics, contentTextX, bodyY, bodyLines.get(index));
            bodyY += 12;
        }

        final Component chapter = Component.translatable(
            "screen.warlockery.manual.chapter",
            availableSections.indexOf(selectedSection) + 1,
            availableSections.size()
        );
        final Component page = Component.translatable(
            "screen.warlockery.manual.page",
            bodyPage + 1,
            pageCount
        );
        final Component readableChapter = ManualTypography.readable(chapter, 0x795A44);
        final Component readablePage = ManualTypography.readable(page, 0x795A44);
        final int counterY = layout.controlTop() - 13;
        drawText(graphics, contentTextX, counterY, readableChapter);
        drawText(
            graphics,
            Math.max(contentTextX, layout.contentRight() - contentInset - font.width(readablePage)),
            counterY,
            readablePage
        );
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private List<FormattedCharSequence> bodyLines(final String section, final int width) {
        return font.split(
            ManualTypography.readable(ManualArticleCatalog.article(manual, section).body(), 0x3A271F),
            width
        );
    }

    private void drawCircleDiagram(
        final GuiGraphicsExtractor graphics,
        final ManualLayout layout,
        final int textX,
        final ManualArticleCatalog.Article article
    ) {
        final int centerX = textX + 30;
        final int centerY = layout.bodyTextTop() + 30;
        final List<Map.Entry<String, Integer>> glyphs = article.glyphs().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList();
        for (int index = 0; index < glyphs.size(); index++) {
            final Map.Entry<String, Integer> glyph = glyphs.get(index);
            final int radius = 10 + index * 9;
            final int color = glyphColor(glyph.getKey());
            final int points = Math.max(3, glyph.getValue());
            for (int point = 0; point < points; point++) {
                final double angle = Math.PI * 2.0D * point / points;
                final int x = centerX + (int) Math.round(Math.cos(angle) * radius);
                final int y = centerY + (int) Math.round(Math.sin(angle) * radius);
                graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
            }
            final Component label = ManualTypography.readable(Component.literal(
                glyphLabel(glyph.getKey()) + " x" + glyph.getValue()
            ), color & 0xFFFFFF);
            drawText(graphics, textX + 66, layout.bodyTextTop() + 7 + index * 13, label);
        }
    }

    private static int glyphColor(final String id) {
        return switch (id) {
            case "circleglyphinfernal" -> 0xFFFF7A22;
            case "circleglyph_veil" -> 0xFF8B58C8;
            default -> 0xFFE7EEF5;
        };
    }

    private static String glyphLabel(final String id) {
        return switch (id) {
            case "circleglyphinfernal" -> "Infernal";
            case "circleglyph_veil" -> "Veil";
            default -> "Ritual";
        };
    }

    private void drawText(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final Component text
    ) {
        graphics.text(font, text, x, y, -1, false);
    }

    private void drawText(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final FormattedCharSequence text
    ) {
        graphics.text(font, text, x, y, -1, false);
    }

    private int bodyPageCount(final ManualLayout layout, final String section) {
        final int inset = Math.min(14, Math.max(6, layout.contentWidth() / 12));
        final int textWidth = Math.max(1, layout.contentWidth() - inset * 2);
        return Math.max(1, Math.ceilDiv(bodyLines(section, textWidth).size(), bodyLineCapacity(layout, section)));
    }

    private int bodyLineCapacity(final ManualLayout layout, final String section) {
        return ManualArticleCatalog.article(manual, section).hasDiagram()
            ? Math.max(3, layout.bodyLineCapacity() - 6)
            : layout.bodyLineCapacity();
    }

    private static void drawBook(final GuiGraphicsExtractor graphics, final ManualLayout layout) {
        graphics.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xFF4A2118);
        graphics.fill(layout.left() + 4, layout.top() + 4, layout.right() - 4, layout.bottom() - 4, 0xFF7A3E2B);
        graphics.fill(layout.navigationLeft(), layout.top() + 8,
            layout.navigationRight(), layout.bottom() - 8, 0xFFF1DFB6);
        graphics.fill(layout.contentLeft(), layout.top() + 8,
            layout.contentRight(), layout.bottom() - 8, 0xFFF1DFB6);
        graphics.fill(layout.navigationLeft() + 5, layout.top() + 13,
            layout.navigationRight() - 5, layout.bottom() - 13, 0xFFFFF0CF);
        graphics.fill(layout.contentLeft() + 5, layout.top() + 13,
            layout.contentRight() - 5, layout.bottom() - 13, 0xFFFFF0CF);
        graphics.fill(layout.spine() - 5, layout.top() + 7,
            layout.spine() + 5, layout.bottom() - 7, 0xFF5B2A20);
        graphics.fill(layout.spine() - 1, layout.top() + 10,
            layout.spine() + 1, layout.bottom() - 10, 0xFFB7754F);
        graphics.fill(layout.navigationLeft() + 3, layout.top() + 12,
            layout.navigationLeft() + 5, layout.bottom() - 12, 0xFFC99A67);
        graphics.fill(layout.contentRight() - 5, layout.top() + 12,
            layout.contentRight() - 3, layout.bottom() - 12, 0xFFC99A67);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
