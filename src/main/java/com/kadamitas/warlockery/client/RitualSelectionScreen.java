package com.kadamitas.warlockery.client;

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
    private static final int PAGE_SIZE = 7;
    private BlockPos center;
    private List<RitualManager.RitualOption> options;
    private String selectedId;
    private int page;

    public RitualSelectionScreen(final BlockPos center, final List<RitualManager.RitualOption> options) {
        super(Component.translatable("screen.warlockery.ritual.title"));
        this.center = center.immutable();
        this.options = List.copyOf(options);
        this.selectedId = options.isEmpty() ? "" : options.getFirst().id();
    }

    public static void openOrUpdate(final BlockPos center, final List<RitualManager.RitualOption> options) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof RitualSelectionScreen screen && screen.center().equals(center)) {
            screen.updateOptions(options);
        } else {
            minecraft.gui.setScreen(new RitualSelectionScreen(center, options));
        }
    }

    public BlockPos center() {
        return center;
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
        final int panelWidth = Math.min(620, width - 24);
        final int left = (width - panelWidth) / 2;
        final int top = Math.max(18, (height - 250) / 2);
        final int listWidth = Math.min(220, panelWidth / 2 - 8);
        final int start = page * PAGE_SIZE;
        options.stream().skip(start).limit(PAGE_SIZE).forEach(option -> {
            final int row = options.indexOf(option) - start;
            final Component marker = Component.literal(option.ready() ? "✓ " : "• ")
                .append(Component.translatable(option.title()));
            addRenderableWidget(Button.builder(marker, button -> {
                selectedId = option.id();
                rebuildWidgets();
            }).bounds(left + 8, top + 28 + row * 25, listWidth - 16, 20).build());
        });

        addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(left + 8, top + 210, 28, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.translatable("screen.warlockery.ritual.refresh"), button ->
            ModClientNetwork.requestRefresh(center)
        ).bounds(left + 42, top + 210, listWidth - 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            page = Math.min(lastPage(), page + 1);
            rebuildWidgets();
        }).bounds(left + listWidth - 36, top + 210, 28, 20).build()).active = page < lastPage();

        final RitualManager.RitualOption selected = selected();
        // A cast already running here is reported by the session row, which is the same fact the server used
        // to refuse a second one. Offering the stop button only then keeps the refund path reachable without
        // asking the client to track state the server already sends.
        final boolean casting = selected != null && RitualUiState.castInProgress(selected);
        final int actionWidth = panelWidth - listWidth - 20;
        final int beginWidth = casting ? actionWidth - 74 : actionWidth;
        final Button begin = addRenderableWidget(Button.builder(
            Component.translatable(selected != null && selected.ready()
                ? "screen.warlockery.ritual.begin"
                : "screen.warlockery.ritual.not_ready"),
            button -> {
                final RitualManager.RitualOption current = selected();
                if (current != null && current.ready()) {
                    ModClientNetwork.requestActivation(center, current.id());
                }
            }
        ).bounds(left + listWidth + 12, top + 210, beginWidth, 20).build());
        begin.active = selected != null && selected.ready();
        if (casting) {
            addRenderableWidget(Button.builder(
                Component.translatable("screen.warlockery.ritual.stop"),
                button -> ModClientNetwork.requestCancellation(center)
            ).bounds(left + listWidth + 16 + beginWidth, top + 210, 70, 20).build());
        }
    }

    private int lastPage() {
        return Math.max(0, (options.size() - 1) / PAGE_SIZE);
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
        final int panelWidth = Math.min(620, width - 24);
        final int left = (width - panelWidth) / 2;
        final int top = Math.max(18, (height - 250) / 2);
        final int listWidth = Math.min(220, panelWidth / 2 - 8);
        graphics.fill(left, top, left + panelWidth, top + 240, 0xE0100B18);
        graphics.fill(left + listWidth, top + 22, left + listWidth + 1, top + 205, 0xFF79518D);
        final var text = graphics.textRenderer();
        text.accept(left + 10, top + 8, title.copy().withColor(0xDDAAFF));
        text.accept(left + listWidth - 52, top + 8, Component.literal((page + 1) + "/" + (lastPage() + 1)).withColor(0xAAAAAA));

        final RitualManager.RitualOption option = selected();
        if (option == null) {
            text.accept(left + listWidth + 12, top + 34,
                Component.translatable("screen.warlockery.ritual.none").withColor(0xFF7777));
        } else {
            int y = top + 30;
            text.accept(left + listWidth + 12, y, Component.translatable(option.title()).withColor(0xFFFFFF));
            y += 14;
            for (final var line : font.split(Component.translatable(option.description()).withColor(0xBBBBBB), panelWidth - listWidth - 28)) {
                text.accept(left + listWidth + 12, y, line);
                y += 10;
            }
            y += 3;
            text.accept(left + listWidth + 12, y, Component.translatable(
                "screen.warlockery.ritual.power", option.altarPower(), option.power()
            ).withColor(option.altarPower() >= option.power() ? 0x55FF55 : 0xFF5555));
            y += 11;
            text.accept(left + listWidth + 12, y, Component.translatable(
                "screen.warlockery.ritual.casting_time", Math.max(1, option.castingTime() / 20)
            ).withColor(0xAAAAAA));
            y += 14;
            final RitualUiState uiState = RitualUiState.from(option);
            if (uiState.showGreenCheck()) {
                text.accept(left + listWidth + 12, y,
                    Component.translatable("overlay.warlockery.all_conditions_met").withColor(uiState.checklist().color()));
                y += 14;
            }
            final List<Component> requirements = RitualUiState.checklistRows(option).stream()
                .map(RitualRequirementText::line)
                .toList();
            for (final Component requirement : requirements.stream().limit(RitualUiState.CHECKLIST_ROWS).toList()) {
                text.accept(left + listWidth + 12, y, requirement);
                y += 11;
            }
            if (requirements.size() > RitualUiState.CHECKLIST_ROWS) {
                text.accept(left + listWidth + 12, y, Component.translatable(
                    "screen.warlockery.ritual.more", requirements.size() - RitualUiState.CHECKLIST_ROWS
                ).withColor(0xAAAAAA));
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

}
