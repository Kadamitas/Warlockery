package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.item.ManualView;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Path;
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
    private static final int DIAGRAM_HEIGHT = 84;
    private static final int PICTOGRAM_HEIGHT = 27;
    private final ManualView view;
    private final ManualProfile manual;
    private final List<String> availableSections;
    private final Path readingPreferences;
    private String selectedSection;
    private String selectedChapter;
    private String query = "";
    private List<String> filteredSections;
    private boolean searchDirty;
    private boolean refocusSearch;
    private boolean chapterIndex = true;
    private int sectionOffset;
    private int navigationEnd;
    private int bodyPage;
    private EditBox searchBox;
    private ManualScreen returnScreen;
    private boolean recipePreview;
    private final ManualRitualCasting ritualCasting;

    private ManualScreen(final ManualView view) {
        super(Component.translatable(view.profile().translatedTitleKey()));
        this.view = view;
        manual = view.profile();
        ritualCasting = com.kadamitas.warlockery.item.RitualBookAccess.BOOK.equals(manual.id())
            ? new ManualRitualCasting() : null;
        availableSections = view.sections();
        readingPreferences = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config/warlockery-manual-reading.properties");
        final ManualReadingPosition position = ManualReadingPosition.load(
            readingPreferences, manual.id(), availableSections);
        selectedSection = position.section();
        bodyPage = position.page();
        chapterIndex = selectedSection.equals(availableSections.getFirst()) && bodyPage == 0;
        selectedChapter = manual.chapterFor(selectedSection).id();
        filteredSections = availableSections;
        final var client = Minecraft.getInstance();
        if (ritualCasting != null && client.level != null && client.player != null) {
            com.kadamitas.warlockery.item.RitualBookAccess.nearbyHeart(client.level, client.player)
                .ifPresent(center -> ritualCasting.attach(center, List.of()));
        }
    }

    public static void open(final ManualView view) {
        Minecraft.getInstance().gui.setScreen(new ManualScreen(view));
    }

    public static void openOrUpdateRitual(final net.minecraft.core.BlockPos center,
        final List<com.kadamitas.warlockery.ritual.RitualManager.RitualOption> options, final boolean mayOpen) {
        final var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        ManualScreen current = client.gui.screen() instanceof ManualScreen book ? book : null;
        while (current != null) {
            if (current.ritualCasting != null && current.ritualCasting.matches(center)) {
                current.ritualCasting.update(options);
                if (client.gui.screen() == current) current.rebuildWidgets(false);
                return;
            }
            current = current.returnScreen;
        }
        if (!mayOpen) return;
        final var owned = com.kadamitas.warlockery.item.RitualBookAccess.find(client.player);
        if (owned.isEmpty()) return;
        final ItemStack stack = owned.orElseThrow();
        final var profile = ((com.kadamitas.warlockery.item.ManualItem) stack.getItem()).profile();
        final var book = new ManualScreen(ManualView.from(profile, stack));
        book.ritualCasting.attach(center, options);
        client.gui.setScreen(book);
    }

    @Override
    public void onClose() {
        if (ritualCasting != null && ritualCasting.performing()) {
            ritualCasting.back();
            rebuildWidgets(false);
            return;
        }
        Minecraft.getInstance().gui.setScreen(returnScreen);
    }

    private void openReference(final ManualBookLinks.Reference reference) {
        final var player = Minecraft.getInstance().player;
        if (player == null) return;
        final var inventory = new java.util.ArrayList<ManualView>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(
                Identifier.fromNamespaceAndPath("warlockery", reference.profile().id()))) {
                inventory.add(ManualView.from(reference.profile(), stack));
            }
        }
        final var owned = reference.ownedView(inventory);
        if (owned.isEmpty() && !inventory.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("screen.warlockery.manual.section_locked"));
            return;
        }
        final ManualScreen target = new ManualScreen(owned.orElseGet(reference::recipeView));
        target.recipePreview = owned.isEmpty();
        target.selectedSection = owned.isPresent() ? reference.section() : target.availableSections.getFirst();
        target.selectedChapter = target.manual.chapterFor(target.selectedSection).id();
        target.bodyPage = 0;
        target.chapterIndex = false;
        target.returnScreen = this;
        Minecraft.getInstance().gui.setScreen(target);
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    public void removed() {
        ManualReadingPosition.save(readingPreferences, manual.id(), selectedSection, bodyPage);
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (ritualCasting != null && ritualCasting.tick()) rebuildWidgets(false);
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
        rebuildWidgets(true);
    }

    private void rebuildWidgets(final boolean revealSelection) {
        if (width <= 0 || height <= 0) {
            return;
        }
        clearWidgets();
        if (ritualCasting != null) ritualCasting.selectSection(selectedSection);
        final ManualLayout layout = layout();
        if (ritualCasting != null && ritualCasting.performing()) {
            searchBox = null;
            ritualCasting.addWidgets(layout, button -> addRenderableWidget(button), () -> rebuildWidgets(false),
                () -> Minecraft.getInstance().gui.setScreen(returnScreen));
            return;
        }
        final List<Component> navigationLabels = navigationLabels();
        final List<Integer> navigationHeights = navigationLabels.stream()
            .map(label -> ManualNavigationButton.heightFor(font, label,
                layout.navigationWidth() - layout.textInset() * 2))
            .toList();
        if (revealSelection) {
            keepNavigationSelectionVisible(layout, navigationHeights);
        }
        navigationEnd = ManualNavigation.end(navigationHeights, sectionOffset, layout.sectionListHeight());
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
        } else {
            searchBox.setCursorPosition(0);
            searchBox.setHighlightPos(0);
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
            addChapterButtons(layout, navigationInset, navigationLabels);
        } else {
            addSubchapterButtons(layout, navigationInset, navigationLabels);
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
            Component.translatable(returnScreen == null ? "screen.warlockery.manual.close" : "screen.warlockery.manual.back")), button -> onClose())
            .bounds(
                controls.get(2).x(), controls.get(2).y(), controls.get(2).width(), controls.get(2).height()
            ).build());
        addReferenceButtons(layout);
        if (ritualCasting != null && ritualCasting.hasRitual() && !recipePreview) {
            final int inset = Math.min(14, Math.max(6, layout.contentWidth() / 12));
            addRenderableWidget(Button.builder(ManualTypography.readable(
                Component.translatable("screen.warlockery.manual.perform_ritual")), button -> {
                    ritualCasting.show();
                    rebuildWidgets(false);
                }).bounds(layout.contentLeft() + inset, layout.bodyTextBottom() - 20,
                    layout.contentWidth() - inset * 2, 20).build());
        }
    }

    private void addReferenceButtons(final ManualLayout layout) {
        final var pages = bodyPages(layout, selectedSection);
        bodyPage = Math.clamp(bodyPage, 0, pages.size() - 1);
        final int inset = Math.min(14, Math.max(6, layout.contentWidth() / 12));
        final int left = layout.contentLeft() + inset;
        int y = layout.bodyTextTop() + (bodyPage == 0 ? visualHeight(article(selectedSection)) : 0);
        for (final FormattedCharSequence line : pages.get(bodyPage)) {
            final int lineY = y;
            final int[] x = {left};
            final java.util.Map<Identifier, int[]> spans = new java.util.LinkedHashMap<>();
            line.accept((index, style, codePoint) -> {
                final int glyphWidth = font.width(Component.literal(Character.toString(codePoint)).setStyle(style));
                if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.Custom click
                    && ManualBookLinks.decode(click.id()).isPresent()) {
                    spans.computeIfAbsent(click.id(), ignored -> new int[] {x[0], x[0]})[1] = x[0] + glyphWidth;
                }
                x[0] += glyphWidth;
                return true;
            });
            spans.forEach((id, span) -> ManualBookLinks.decode(id).ifPresent(reference ->
                addRenderableWidget(new ManualReferenceButton(span[0], lineY, span[1] - span[0], reference.label(),
                    button -> openReference(reference)))));
            y += ManualTypography.BODY_LINE_HEIGHT;
        }
    }

    private void addChapterButtons(final ManualLayout layout, final int navigationInset,
        final List<Component> labels) {
        final List<ManualProfile.Chapter> chapters = chapterChoices();
        int y = layout.sectionListTop();
        for (int index = sectionOffset; index < navigationEnd; index++) {
            final ManualProfile.Chapter chapter = chapters.get(index);
            final ManualNavigationButton entry = addRenderableWidget(new ManualNavigationButton(
                font, layout.navigationLeft() + navigationInset, y,
                layout.navigationWidth() - navigationInset * 2, labels.get(index), button -> {
                selectedChapter = chapter.id();
                selectedSection = chapter.sections().stream()
                    .filter(filteredSections::contains)
                    .findFirst()
                    .orElseThrow();
                bodyPage = 0;
                chapterIndex = false;
                sectionOffset = 0;
                rebuildWidgets();
            }));
            y += entry.getHeight() + ManualNavigation.GAP;
        }
    }

    private void addSubchapterButtons(final ManualLayout layout, final int navigationInset,
        final List<Component> labels) {
        final List<String> sections = navigationSections();
        int y = layout.sectionListTop();
        for (int index = sectionOffset; index < navigationEnd; index++) {
            final String section = sections.get(index);
            final ManualNavigationButton entry = addRenderableWidget(new ManualNavigationButton(
                font, layout.navigationLeft() + navigationInset, y,
                layout.navigationWidth() - navigationInset * 2, labels.get(index), button -> {
                selectedSection = section;
                bodyPage = 0;
                rebuildWidgets();
            }));
            y += entry.getHeight() + ManualNavigation.GAP;
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
            rebuildWidgets(false);
            return;
        }
        if (direction < 0 && bodyPage > 0) {
            bodyPage--;
            rebuildWidgets(false);
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
        final ManualLayout layout = ManualLayout.calculate(width, height);
        return ritualCasting != null && ritualCasting.performing() ? layout.withoutNavigation() : layout;
    }

    private List<ManualProfile.Chapter> chapterChoices() {
        return manual.chapters().stream()
            .filter(chapter -> chapter.sections().stream().anyMatch(filteredSections::contains))
            .toList();
    }

    private List<String> navigationSections() {
        return manual.sectionsInChapter(selectedChapter, filteredSections);
    }

    private List<Component> navigationLabels() {
        if (chapterIndex) {
            return chapterChoices().stream().map(chapter -> (Component) ManualTypography.readable(
                Component.literal(chapter.id().equals(selectedChapter) ? "▶ " : "")
                    .append(Component.translatable(chapter.titleKey())))).toList();
        }
        return navigationSections().stream().map(section -> (Component) ManualTypography.readable(
            Component.literal(section.equals(selectedSection) ? "▶ " : "")
                .append(Component.translatable(manual.translatedSectionTitleKey(section))))).toList();
    }

    private void keepNavigationSelectionVisible(final ManualLayout layout, final List<Integer> heights) {
        final List<ManualProfile.Chapter> chapters = chapterChoices();
        final int selectedIndex = chapterIndex
            ? java.util.stream.IntStream.range(0, chapters.size())
                .filter(index -> chapters.get(index).id().equals(selectedChapter))
                .findFirst()
                .orElse(0)
            : navigationSections().indexOf(selectedSection);
        sectionOffset = ManualNavigation.reveal(heights, sectionOffset, selectedIndex, layout.sectionListHeight());
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
        if (layout.overNavigation(mouseX, mouseY)
            && (sectionOffset > 0 || navigationEnd < navigationEntryCount())) {
            final int maximumOffset = navigationEnd < navigationEntryCount()
                ? navigationEntryCount() - 1 : sectionOffset;
            final int nextOffset = Math.clamp(sectionOffset + direction, 0, maximumOffset);
            if (nextOffset != sectionOffset) {
                sectionOffset = nextOffset;
                rebuildWidgets(false);
            }
            return true;
        }
        if (layout.overContent(mouseX, mouseY) && ritualCasting != null && ritualCasting.scroll(scrollY)) {
            rebuildWidgets(false);
            return true;
        }
        if (layout.overContent(mouseX, mouseY)) {
            final int maximumPage = bodyPageCount(layout, selectedSection) - 1;
            bodyPage = Math.clamp(bodyPage + direction, 0, maximumPage);
            rebuildWidgets(false);
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

        if (layout.navigationWidth() > 0) {
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
                if (navigationEnd < navigationEntryCount()) {
                    drawText(graphics, layout.navigationRight() - navigationInset - 6, layout.bottom() - 22,
                        ManualTypography.readable(Component.literal("↓"), 0x795A44));
                }
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

        if (ritualCasting != null && ritualCasting.performing()) {
            ritualCasting.render(graphics, layout);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        final List<List<FormattedCharSequence>> pages = bodyPages(layout, selectedSection);
        final int pageCount = pages.size();
        bodyPage = Math.clamp(bodyPage, 0, pageCount - 1);
        final ManualArticleCatalog.Article article = article(selectedSection);
        if (bodyPage == 0 && article.hasDiagram()) {
            drawCircleDiagram(graphics, layout, contentTextX, contentTextWidth, article);
        }
        if (bodyPage == 0 && article.hasPictograms()) {
            drawPictograms(graphics, layout, contentTextX, contentTextWidth, mouseX, mouseY, article);
        }
        int bodyY = layout.bodyTextTop() + (bodyPage == 0 ? visualHeight(article) : 0);
        for (final FormattedCharSequence line : pages.get(bodyPage)) {
            drawScaledText(graphics, contentTextX, bodyY, line, ManualTypography.BODY_SCALE);
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
        if (font.width(readableChapter) + font.width(readablePage) + 8 <= contentTextWidth) {
            drawText(graphics, contentTextX, counterY, readableChapter);
        }
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
            ManualTypography.readable(article(section).body(), 0x3A271F),
            ManualTypography.wrappingWidth(width, ManualTypography.BODY_SCALE)
        );
    }

    private ManualArticleCatalog.Article article(final String section) {
        final var article = ManualArticleCatalog.article(manual, section);
        final Component body = recipePreview
            ? Component.translatable("screen.warlockery.manual.book_required",
                Component.translatable(manual.translatedTitleKey())).append("\n\n").append(article.body())
            : section.startsWith("rite_") || section.startsWith("crafting_") || section.startsWith("brew_entry_")
                ? ManualBookLinks.append(article.body(), manual, section) : article.body();
        return new ManualArticleCatalog.Article(body, article.glyphs(), article.pictograms());
    }

    private void drawCircleDiagram(
        final GuiGraphicsExtractor graphics,
        final ManualLayout layout,
        final int textX,
        final int textWidth,
        final ManualArticleCatalog.Article article
    ) {
        final int gridSize = 79;
        final int gridTop = layout.bodyTextTop();
        final int centerX = textX + 39;
        final int centerY = gridTop + 39;
        graphics.fill(textX, gridTop, textX + gridSize, gridTop + gridSize, 0xFF242632);
        for (int line = 0; line <= 15; line++) {
            final int offset = 2 + line * 5;
            graphics.fill(textX + offset, gridTop + 2, textX + offset + 1, gridTop + 78, 0xFF41424B);
            graphics.fill(textX + 2, gridTop + offset, textX + 78, gridTop + offset + 1, 0xFF41424B);
        }
        graphics.fill(centerX - 1, centerY - 1, centerX + 3, centerY + 3, 0xFFFFD866);
        final List<Map.Entry<String, Integer>> glyphs = article.glyphs().entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> ChalkCircleLayout.Size.forMarkCount(entry.getValue()).ordinal()))
            .toList();
        for (int index = 0; index < glyphs.size(); index++) {
            final Map.Entry<String, Integer> glyph = glyphs.get(index);
            final ChalkCircleLayout.Size size = ChalkCircleLayout.Size.forMarkCount(glyph.getValue());
            final int color = glyphColor(glyph.getKey());
            for (final var offset : size.offsets()) {
                final int x = centerX + offset.getX() * 5;
                final int y = centerY + offset.getZ() * 5;
                graphics.fill(x - 1, y - 1, x + 3, y + 3, color);
            }
            final Component label = ManualTypography.readable(Component.translatable(
                "screen.warlockery.manual.chalk_count",
                Component.translatable(glyphLabelKey(glyph.getKey())),
                glyph.getValue()
            ), 0x3A271F);
            final int labelY = gridTop + index * 27;
            final int labelX = textX + gridSize + 11;
            graphics.fill(textX + gridSize + 3, labelY, textX + gridSize + 9, labelY + 8, 0xFF242632);
            graphics.fill(textX + gridSize + 4, labelY + 1, textX + gridSize + 8, labelY + 7, color);
            final List<FormattedCharSequence> labelLines = font.split(label, Math.max(1, textX + textWidth - labelX));
            for (int line = 0; line < Math.min(2, labelLines.size()); line++) {
                drawText(graphics, labelX, labelY + line * ManualTypography.TITLE_LINE_HEIGHT, labelLines.get(line));
            }
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
        final int headingWidth = font.width(heading);
        final boolean showHeading = headingWidth + 28 <= textWidth;
        if (showHeading) {
            drawText(graphics, textX, y + 5, heading);
        }
        final int iconsX = textX + (showHeading ? headingWidth + 8 : 0);
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
            case "circleglyph_veil" -> 0xFF287C8E;
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
        return bodyPages(layout, section).size();
    }

    private List<List<FormattedCharSequence>> bodyPages(final ManualLayout layout, final String section) {
        final int inset = Math.min(14, Math.max(6, layout.contentWidth() / 12));
        final int textWidth = Math.max(1, layout.contentWidth() - inset * 2);
        final ManualArticleCatalog.Article article = article(section);
        final int extraControls = ritualCasting != null && section.startsWith("rite_") && !recipePreview ? 26 : 0;
        final int firstCapacity = Math.max(0,
            (layout.bodyTextBottom() - extraControls - layout.bodyTextTop() - visualHeight(article)) / ManualTypography.BODY_LINE_HEIGHT);
        final int capacity = Math.max(1, layout.bodyLineCapacity() - (extraControls + ManualTypography.BODY_LINE_HEIGHT - 1) / ManualTypography.BODY_LINE_HEIGHT);
        return ManualPagination.pages(bodyLines(section, textWidth), firstCapacity, capacity,
            ManualScreen::blankLine);
    }

    private static boolean blankLine(final FormattedCharSequence line) {
        return line.accept((index, style, codePoint) -> Character.isWhitespace(codePoint));
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
        graphics.fill(layout.contentLeft(), layout.top() + 8,
            layout.contentRight(), layout.bottom() - 8, 0xFFF1DFB6);
        graphics.fill(layout.contentLeft() + 5, layout.top() + 13,
            layout.contentRight() - 5, layout.bottom() - 13, 0xFFFFF0CF);
        if (layout.navigationWidth() > 0) {
            graphics.fill(layout.navigationLeft(), layout.top() + 8,
                layout.navigationRight(), layout.bottom() - 8, 0xFFF1DFB6);
            graphics.fill(layout.navigationLeft() + 5, layout.top() + 13,
                layout.navigationRight() - 5, layout.bottom() - 13, 0xFFFFF0CF);
            graphics.fill(layout.spine() - 5, layout.top() + 7,
                layout.spine() + 5, layout.bottom() - 7, 0xFF5B2A20);
            graphics.fill(layout.spine() - 1, layout.top() + 10,
                layout.spine() + 1, layout.bottom() - 10, 0xFFB7754F);
        }
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
