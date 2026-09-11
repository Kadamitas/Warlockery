package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualRequirementText;
import com.kadamitas.warlockery.ritual.RitualUiState;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class RitualSelectionScreen extends Screen {
    private BlockPos center;
    private List<RitualManager.RitualOption> options;
    private String selectedId;
    private int page;
    private int detailPage;
    private int castingRefreshTicks;

    public RitualSelectionScreen(final BlockPos center, final List<RitualManager.RitualOption> options) {
        super(Component.translatable("screen.warlockery.ritual.title"));
        this.center = center.immutable();
        this.options = List.copyOf(options);
        this.selectedId = options.isEmpty() ? "" : options.getFirst().id();
    }

    public static void openOrUpdate(final BlockPos center, final List<RitualManager.RitualOption> options) {
        openOrUpdate(center, options, true);
    }

    public static void openOrUpdate(
        final BlockPos center,
        final List<RitualManager.RitualOption> options,
        final boolean mayOpen
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof RitualSelectionScreen screen && screen.center().equals(center)) {
            screen.updateOptions(options);
        } else if (mayOpen) {
            minecraft.gui.setScreen(new RitualSelectionScreen(center, options));
        }
    }

    public BlockPos center() {
        return center;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        final var option = selected();
        if (option != null && RitualUiState.castInProgress(option)) {
            if (++castingRefreshTicks >= 20) {
                castingRefreshTicks = 0;
                ModClientNetwork.requestRefresh(center);
            }
        } else {
            castingRefreshTicks = 0;
        }
    }

    public void updateOptions(final List<RitualManager.RitualOption> updated) {
        options = List.copyOf(updated);
        if (options.stream().noneMatch(option -> option.id().equals(selectedId))) {
            selectedId = options.isEmpty() ? "" : options.getFirst().id();
        }
        page = Math.min(page, lastPage());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        if (width <= 0 || height <= 0) {
            return;
        }
        clearWidgets();
        final RitualSelectionLayout layout = layout();
        page = Math.min(page, lastPage());
        final int panelWidth = layout.width();
        final int left = layout.left();
        final int top = layout.top();
        final int listWidth = layout.listWidth();
        final int start = page * layout.rows();
        options.stream().skip(start).limit(layout.rows()).forEach(option -> {
            final int row = options.indexOf(option) - start;
            final Component marker = Component.literal(option.ready() ? "\u2713 " : "\u2022 ")
                .append(Component.translatable(option.title()));
            addRenderableWidget(Button.builder(marker, button -> {
                selectedId = option.id();
                detailPage = 0;
                rebuildWidgets();
            }).bounds(left + 8, top + 28 + row * 25, listWidth - 16, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(option.title())))
                .build());
        });

        addRenderableWidget(Button.builder(Component.literal("\u2039"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(left + 8, layout.actionY(), 28, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.translatable("screen.warlockery.ritual.refresh"), button ->
            ModClientNetwork.requestRefresh(center)
        ).bounds(left + 42, layout.actionY(), listWidth - 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u203a"), button -> {
            page = Math.min(lastPage(), page + 1);
            rebuildWidgets();
        }).bounds(left + listWidth - 36, layout.actionY(), 28, 20).build()).active = page < lastPage();

        final var detailPages = detailPages(layout);
        detailPage = Math.clamp(detailPage, 0, detailPages.size() - 1);
        if (detailPages.size() > 1) {
            addRenderableWidget(Button.builder(Component.translatable("screen.warlockery.ritual.previous_details"), button -> {
                detailPage--; rebuildWidgets();
            }).bounds(layout.detailX(), layout.actionY() - 24, 24, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.warlockery.ritual.previous_details_hint")))
                .build()).active = detailPage > 0;
            addRenderableWidget(Button.builder(Component.translatable("screen.warlockery.ritual.next_details"), button -> {
                detailPage++; rebuildWidgets();
            }).bounds(layout.detailX() + layout.detailWidth() - 24, layout.actionY() - 24, 24, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.warlockery.ritual.next_details_hint")))
                .build())
                .active = detailPage + 1 < detailPages.size();
        }
        final RitualManager.RitualOption selected = selected();
        // A cast already running here is reported by the session row, which is the same fact the server used
        // to refuse a second one. Offering the stop button only then keeps the refund path reachable without
        // asking the client to track state the server already sends.
        final boolean casting = selected != null && RitualUiState.castInProgress(selected);
        final int actionWidth = panelWidth - listWidth - 20;
        final int beginWidth = casting ? actionWidth - 74 : actionWidth;
        final Button begin = addRenderableWidget(Button.builder(
            Component.translatable(casting ? "screen.warlockery.ritual.casting" : selected != null && selected.ready()
                ? "screen.warlockery.ritual.begin"
                : "screen.warlockery.ritual.not_ready"),
            button -> {
                final RitualManager.RitualOption current = selected();
                if (current != null && current.ready()) {
                    ModClientNetwork.requestActivation(center, current.id());
                }
            }
        ).bounds(left + listWidth + 12, layout.actionY(), beginWidth, 20).build());
        begin.active = selected != null && selected.ready();
        if (casting) {
            addRenderableWidget(Button.builder(
                Component.translatable("screen.warlockery.ritual.stop"),
                button -> ModClientNetwork.requestCancellation(center)
            ).bounds(left + listWidth + 16 + beginWidth, layout.actionY(), 70, 20).build());
        }
    }

    private int lastPage() {
        return Math.max(0, (options.size() - 1) / layout().rows());
    }

    private RitualManager.RitualOption selected() {
        return options.stream().filter(option -> option.id().equals(selectedId)).findFirst().orElse(null);
    }

    @Override
    public void extractRenderState(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick
    ) {
        final RitualSelectionLayout layout = layout();
        final int panelWidth = layout.width();
        final int left = layout.left();
        final int top = layout.top();
        final int listWidth = layout.listWidth();
        graphics.fill(left, top, left + panelWidth, top + layout.height(), 0xE0100B18);
        graphics.fill(left + listWidth, top + 22, left + listWidth + 1, layout.actionY() - 5, 0xFF79518D);
        final var text = graphics.textRenderer();
        text.accept(left + 10, top + 8, title.copy().withColor(0xDDAAFF));
        text.accept(left + listWidth - 52, top + 8, Component.literal((page + 1) + "/" + (lastPage() + 1)).withColor(0xAAAAAA));

        final RitualManager.RitualOption option = selected();
        if (option == null) {
            text.accept(left + listWidth + 12, top + 34,
                Component.translatable("screen.warlockery.ritual.none").withColor(0xFF7777));
        } else {
            final var pages = detailPages(layout);
            detailPage = Math.clamp(detailPage, 0, pages.size() - 1);
            int y = layout.detailTop();
            for (final var line : pages.get(detailPage)) {
                text.accept(layout.detailX(), y, line);
                y += 11;
            }
            if (pages.size() > 1) {
                final Component counter = Component.translatable("screen.warlockery.ritual.details_page", detailPage + 1, pages.size());
                graphics.text(font, counter, layout.detailX() + (layout.detailWidth() - font.width(counter)) / 2,
                    layout.actionY() - 18, 0xFFAAAAAA, false);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private RitualSelectionLayout layout() {
        return RitualSelectionLayout.calculate(width, height);
    }

    private List<List<net.minecraft.util.FormattedCharSequence>> detailPages(final RitualSelectionLayout layout) {
        final var option = selected();
        if (option == null) return List.of(List.of());
        final var lines = detailContents(option).stream()
            .flatMap(component -> font.split(ManualTypography.readable(component), layout.detailWidth()).stream()).toList();
        return ManualPagination.pages(lines, layout.detailCapacity(), layout.detailCapacity(), line -> false);
    }

    static List<Component> detailContents(final RitualManager.RitualOption option) {
        final java.util.ArrayList<Component> text = new java.util.ArrayList<>();
        text.add(Component.translatable(option.title()).withColor(0xFFFFFF));
        text.add(Component.translatable("screen.warlockery.ritual.casting_time", Math.max(1, option.castingTime() / 20)).withColor(0xAAAAAA));
        if (RitualUiState.castInProgress(option)) {
            text.add(Component.translatable("screen.warlockery.ritual.casting").withColor(0xDDAAFF));
            text.add(Component.translatable("screen.warlockery.ritual.casting_help").withColor(0xBBBBBB));
        } else {
            text.add(Component.translatable("screen.warlockery.ritual.power", option.altarPower(), option.power())
                .withColor(option.altarPower() >= option.power() ? 0x55FF55 : 0xFF5555));
            final RitualUiState state = RitualUiState.from(option);
            if (state.showGreenCheck()) text.add(Component.translatable("overlay.warlockery.all_conditions_met").withColor(state.checklist().color()));
            RitualUiState.checklistRows(option).stream().map(RitualRequirementText::line).forEach(text::add);
        }
        text.add(Component.empty());
        text.add(Component.translatable(option.description()).withColor(0xBBBBBB));
        return List.copyOf(text);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        final var layout = layout();
        if (scrollY != 0 && mouseX >= layout.detailX() && mouseX < layout.detailX() + layout.detailWidth()
            && mouseY >= layout.detailTop() && mouseY < layout.actionY()) {
            final int count = detailPages(layout).size();
            detailPage = Math.clamp(detailPage + (scrollY > 0 ? -1 : 1), 0, count - 1);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

}
