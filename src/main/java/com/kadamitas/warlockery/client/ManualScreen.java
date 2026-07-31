package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.item.ManualView;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class ManualScreen extends Screen {
    private static final int DIAGRAM_HEIGHT = 58;
    private static final int PICTOGRAM_HEIGHT = 27;
    private final ManualView view;
    private final ManualProfile manual;
    private final List<String> availableSections;
    private String selectedSection;
    private String selectedChapter;
    private String query = "";
    private List<String> filteredSections;
    private boolean searchDirty;
    private boolean refocusSearch;
    private boolean chapterIndex = true;
    private int sectionOffset;
    private int bodyPage;
    private EditBox searchBox;

    private ManualScreen(final ManualView view) {
        super(Component.translatable(view.profile().translatedTitleKey()));
        this.view = view;
        manual = view.profile();
        availableSections = view.sections();
        selectedSection = availableSections.getFirst();
        selectedChapter = manual.chapterFor(selectedSection).id();
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
            selectedChapter = manual.chapterFor(selectedSection).id();
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
        keepNavigationSelectionVisible(layout);
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

        final List<ManualLayout.Bounds> chapterControls = layout.chapterControls();
        final List<ManualProfile.Chapter> chapterChoices = chapterChoices();
        final Button previousChapter = addRenderableWidget(Button.builder(ManualTypography.readable(
            Component.translatable("screen.warlockery.manual.previous_chapter")), button ->
            navigateChapter(-1)).bounds(
                chapterControls.get(0).x(),
                chapterControls.get(0).y(),
                chapterControls.get(0).width(),
                chapterControls.get(0).height()
            ).build());
        final Button chapterTitle = addRenderableWidget(Button.builder(ManualTypography.readable(
            Component.translatable(chapterIndex
                ? "screen.warlockery.manual.open_chapter"
                : "screen.warlockery.manual.table_of_contents")), button -> toggleChapterIndex()).bounds(
                chapterControls.get(1).x(),
                chapterControls.get(1).y(),
                chapterControls.get(1).width(),
                chapterControls.get(1).height()
            ).build());
        final Button nextChapter = addRenderableWidget(Button.builder(ManualTypography.readable(
            Component.translatable("screen.warlockery.manual.next_chapter")), button ->
            navigateChapter(1)).bounds(
                chapterControls.get(2).x(),
                chapterControls.get(2).y(),
                chapterControls.get(2).width(),
                chapterControls.get(2).height()
            ).build());
        chapterTitle.active = !filteredSections.isEmpty();
        previousChapter.active = chapterChoices.size() > 1;
        nextChapter.active = chapterChoices.size() > 1;

        if (chapterIndex) {
            addChapterButtons(layout, navigationInset);
        } else {
            addSubchapterButtons(layout, navigationInset);
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

    private void addChapterButtons(final ManualLayout layout, final int navigationInset) {
        final List<ManualProfile.Chapter> chapters = chapterChoices();
        final int end = Math.min(chapters.size(), sectionOffset + layout.sectionRows());
        for (int index = sectionOffset; index < end; index++) {
            final ManualProfile.Chapter chapter = chapters.get(index);
            final Component label = ManualTypography.readable(
                Component.literal(chapter.id().equals(selectedChapter) ? "▶ " : "")
                    .append(Component.translatable(chapter.titleKey()))
            );
            final int row = index - sectionOffset;
            addRenderableWidget(Button.builder(label, button -> {
                selectedChapter = chapter.id();
                selectedSection = chapter.sections().stream()
                    .filter(filteredSections::contains)
                    .findFirst()
                    .orElseThrow();
                bodyPage = 0;
                chapterIndex = false;
                sectionOffset = 0;
                rebuildWidgets();
            }).bounds(
                layout.navigationLeft() + navigationInset,
                layout.sectionListTop() + row * layout.sectionRowHeight(),
                Math.max(20, layout.navigationWidth() - navigationInset * 2),
                layout.sectionButtonHeight()
            ).build());
        }
    }

    private void addSubchapterButtons(final ManualLayout layout, final int navigationInset) {
        final List<String> sections = navigationSections();
        final int end = Math.min(sections.size(), sectionOffset + layout.sectionRows());
        for (int index = sectionOffset; index < end; index++) {
            final String section = sections.get(index);
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
                layout.sectionListTop() + row * layout.sectionRowHeight(),
                Math.max(20, layout.navigationWidth() - navigationInset * 2),
                layout.sectionButtonHeight()
            ).build());
        }
    }

    private void toggleChapterIndex() {
        chapterIndex = !chapterIndex;
        sectionOffset = 0;
        rebuildWidgets();
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
        selectedChapter = manual.chapterFor(selectedSection).id();
        chapterIndex = false;
        query = "";
        filteredSections = availableSections;
        sectionOffset = 0;
        bodyPage = direction < 0 ? bodyPageCount(layout, selectedSection) - 1 : 0;
        rebuildWidgets();
    }

    private String adjacentSection(final int direction) {
        return view.adjacentSection(selectedSection, direction);
    }

    private void navigateChapter(final int direction) {
        final List<ManualProfile.Chapter> choices = chapterChoices();
        if (choices.isEmpty()) {
            return;
        }
        final int current = Math.max(0, java.util.stream.IntStream.range(0, choices.size())
            .filter(index -> choices.get(index).id().equals(selectedChapter))
            .findFirst()
            .orElse(0));
        final ManualProfile.Chapter chapter = choices.get(Math.floorMod(current + direction, choices.size()));
        selectedChapter = chapter.id();
        selectedSection = chapter.sections().stream().filter(filteredSections::contains).findFirst().orElseThrow();
        sectionOffset = 0;
        bodyPage = 0;
        rebuildWidgets();
    }

    private List<String> searchSections(final String value) {
        final String needle = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return availableSections;
        }
        return availableSections.stream()
            .filter(section -> Component.translatable(manual.translatedSectionTitleKey(section)).getString()
                .toLowerCase(Locale.ROOT).contains(needle)
                || Component.translatable(manual.chapterFor(section).titleKey()).getString()
                    .toLowerCase(Locale.ROOT).contains(needle)
                || ManualArticleCatalog.article(manual, section).body().getString()
                    .toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }

    private ManualLayout layout() {
        return ManualLayout.calculate(width, height);
    }

    private List<ManualProfile.Chapter> chapterChoices() {
        return manual.chapters().stream()
            .filter(chapter -> chapter.sections().stream().anyMatch(filteredSections::contains))
            .toList();
    }

    private List<String> navigationSections() {
        return manual.sectionsInChapter(selectedChapter, filteredSections);
    }

    private void keepNavigationSelectionVisible(final ManualLayout layout) {
        final List<ManualProfile.Chapter> chapters = chapterChoices();
        final int selectedIndex = chapterIndex
            ? java.util.stream.IntStream.range(0, chapters.size())
                .filter(index -> chapters.get(index).id().equals(selectedChapter))
                .findFirst()
                .orElse(0)
            : navigationSections().indexOf(selectedSection);
        final int maximumOffset = Math.max(0, navigationEntryCount() - layout.sectionRows());
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
        if (layout.overNavigation(mouseX, mouseY) && navigationEntryCount() > layout.sectionRows()) {
            final int maximumOffset = navigationEntryCount() - layout.sectionRows();
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
            ManualTypography.wrappingWidth(navigationTextWidth, ManualTypography.TITLE_SCALE)
        );
        for (int index = 0; index < Math.min(2, manualTitle.size()); index++) {
            drawScaledText(graphics, navigationTextX, titleY, manualTitle.get(index), ManualTypography.TITLE_SCALE);
            titleY += ManualTypography.TITLE_LINE_HEIGHT;
        }
        drawText(graphics, navigationTextX, layout.top() + 40,
            ManualTypography.readable(Component.translatable("screen.warlockery.manual.chapters"), 0x6B3D27));

        if (filteredSections.isEmpty()) {
            drawText(graphics, navigationTextX, layout.sectionListTop(),
                ManualTypography.readable(Component.translatable("screen.warlockery.manual.no_results"), 0x9C302F));
        } else {
            if (sectionOffset > 0) {
                drawText(graphics, layout.navigationRight() - navigationInset - 6, layout.top() + 81,
                    ManualTypography.readable(Component.literal("↑"), 0x795A44));
            }
            if (sectionOffset + layout.sectionRows() < navigationEntryCount()) {
                drawText(graphics, layout.navigationRight() - navigationInset - 6, layout.bottom() - 22,
                    ManualTypography.readable(Component.literal("↓"), 0x795A44));
            }
        }

        final List<FormattedCharSequence> sectionTitle = font.split(
            ManualTypography.readable(
                Component.translatable(manual.translatedSectionTitleKey(selectedSection)),
                0x5B1F31
            ),
            ManualTypography.wrappingWidth(contentTextWidth, ManualTypography.TITLE_SCALE)
        );
        int sectionTitleY = layout.top() + 16;
        for (int index = 0; index < Math.min(2, sectionTitle.size()); index++) {
            drawScaledText(graphics, contentTextX, sectionTitleY, sectionTitle.get(index),
                ManualTypography.TITLE_SCALE);
            sectionTitleY += ManualTypography.TITLE_LINE_HEIGHT;
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
        if (article.hasPictograms()) {
            drawPictograms(graphics, layout, contentTextX, contentTextWidth, mouseX, mouseY, article);
        }
        int bodyY = layout.bodyTextTop() + visualHeight(article);
        for (int index = firstLine; index < lastLine; index++) {
            drawScaledText(graphics, contentTextX, bodyY, bodyLines.get(index), ManualTypography.BODY_SCALE);
            bodyY += ManualTypography.BODY_LINE_HEIGHT;
        }

        final List<String> currentChapterSections = manual.sectionsInChapter(selectedChapter, availableSections);
        final Component chapter = Component.translatable(
            "screen.warlockery.manual.subchapter",
            currentChapterSections.indexOf(selectedSection) + 1,
            currentChapterSections.size()
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
            ManualTypography.wrappingWidth(width, ManualTypography.BODY_SCALE)
        );
    }

    private void drawCircleDiagram(
        final GuiGraphicsExtractor graphics,
        final ManualLayout layout,
        final int textX,
        final ManualArticleCatalog.Article article
    ) {
        final int centerX = textX + 30;
        final int centerY = layout.bodyTextTop() + 25;
        final List<Map.Entry<String, Integer>> glyphs = article.glyphs().entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> ChalkCircleLayout.Size.forMarkCount(entry.getValue()).ordinal()))
            .toList();
        for (int index = 0; index < glyphs.size(); index++) {
            final Map.Entry<String, Integer> glyph = glyphs.get(index);
            final ChalkCircleLayout.Size size = ChalkCircleLayout.Size.forMarkCount(glyph.getValue());
            final int radius = 10 + size.ordinal() * 9;
            final int color = glyphColor(glyph.getKey());
            final int points = glyph.getValue();
            for (int point = 0; point < points; point++) {
                final double angle = Math.PI * 2.0D * point / points;
                final int x = centerX + (int) Math.round(Math.cos(angle) * radius);
                final int y = centerY + (int) Math.round(Math.sin(angle) * radius);
                graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
            }
            final Component label = ManualTypography.readable(Component.translatable(
                "screen.warlockery.manual.chalk_count",
                Component.translatable(glyphLabelKey(glyph.getKey())),
                glyph.getValue()
            ), color & 0xFFFFFF);
            drawScaledText(graphics, textX + 62, layout.bodyTextTop() + 5 + index * 11, label,
                ManualTypography.BODY_SCALE);
        }
    }

    private void drawPictograms(
        final GuiGraphicsExtractor graphics,
        final ManualLayout layout,
        final int textX,
        final int textWidth,
        final int mouseX,
        final int mouseY,
        final ManualArticleCatalog.Article article
    ) {
        final int y = layout.bodyTextTop() + (article.hasDiagram() ? DIAGRAM_HEIGHT : 0);
        final Component heading = ManualTypography.readable(
            Component.translatable("screen.warlockery.manual.pictograms"),
            0x6B3D27
        );
        drawScaledText(graphics, textX, y + 5, heading, ManualTypography.BODY_SCALE);
        final int iconsX = textX + Math.min(82, Math.max(54, textWidth / 4));
        final int capacity = Math.max(1, (textX + textWidth - iconsX) / 20);
        final int shown = Math.min(capacity, article.pictograms().size());
        for (int index = 0; index < shown; index++) {
            final ManualArticleCatalog.Pictogram pictogram = article.pictograms().get(index);
            final ItemStack stack = pictogramStack(pictogram);
            if (stack.isEmpty()) {
                continue;
            }
            final int x = iconsX + index * 20;
            graphics.fakeItem(stack, x, y);
            graphics.itemDecorations(font, stack, x, y, pictogram.count() > 1
                ? Integer.toString(pictogram.count())
                : null);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
        if (shown < article.pictograms().size()) {
            drawScaledText(
                graphics,
                iconsX + Math.max(0, shown - 1) * 20,
                y + 17,
                ManualTypography.readable(Component.literal("+" + (article.pictograms().size() - shown)), 0x6B3D27),
                ManualTypography.BODY_SCALE
            );
        }
    }

    private static ItemStack pictogramStack(final ManualArticleCatalog.Pictogram pictogram) {
        final Identifier id = Identifier.tryParse(pictogram.itemId());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.get(id)
            .map(holder -> new ItemStack(holder.value(), pictogram.count()))
            .orElse(ItemStack.EMPTY);
    }

    private static int glyphColor(final String id) {
        return switch (id) {
            case "circleglyphgolden" -> 0xFFFFD866;
            case "circleglyphinfernal" -> 0xFFFF7A22;
            case "circleglyph_veil" -> 0xFF8B58C8;
            default -> 0xFFE7EEF5;
        };
    }

    private static String glyphLabelKey(final String id) {
        return switch (id) {
            case "circleglyphgolden" -> "item.warlockery.chalkheart";
            case "circleglyphinfernal" -> "screen.warlockery.manual.chalk.infernal";
            case "circleglyph_veil" -> "screen.warlockery.manual.chalk.veil";
            default -> "screen.warlockery.manual.chalk.ritual";
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

    private void drawScaledText(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final Component text,
        final float scale
    ) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, -1, false);
        graphics.pose().popMatrix();
    }

    private void drawScaledText(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final FormattedCharSequence text,
        final float scale
    ) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, -1, false);
        graphics.pose().popMatrix();
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
        final ManualArticleCatalog.Article article = ManualArticleCatalog.article(manual, section);
        return Math.max(
            3,
            layout.bodyLineCapacity() - Math.ceilDiv(visualHeight(article), ManualTypography.BODY_LINE_HEIGHT)
        );
    }

    private static int visualHeight(final ManualArticleCatalog.Article article) {
        return (article.hasDiagram() ? DIAGRAM_HEIGHT : 0)
            + (article.hasPictograms() ? PICTOGRAM_HEIGHT : 0);
    }

    private int navigationEntryCount() {
        return chapterIndex ? chapterChoices().size() : navigationSections().size();
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
