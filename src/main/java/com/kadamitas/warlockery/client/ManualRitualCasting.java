package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.block.entity.CircleHeartBlockEntity;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualRequirementText;
import com.kadamitas.warlockery.ritual.RitualUiState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class ManualRitualCasting {
    private BlockPos center;
    private ResourceKey<Level> dimension;
    private List<RitualManager.RitualOption> options = List.of();
    private String selected = "";
    private boolean performing;
    private int detailPage;
    private int refreshTicks;

    void attach(final BlockPos position, final List<RitualManager.RitualOption> updated) {
        center = position.immutable();
        dimension = Minecraft.getInstance().level.dimension();
        update(updated);
        if (!selected.isEmpty()) ModNetwork.requestSelection(center, selected);
    }

    boolean matches(final BlockPos position) { return center != null && center.equals(position); }
    BlockPos center() { return center; }
    boolean performing() { return performing; }
    void back() { performing = false; detailPage = 0; }
    void show() { performing = true; detailPage = 0; refresh(); }

    void selectSection(final String section) {
        final String id = section.startsWith("rite_") ? "warlockery:" + section.substring(5) : "";
        if (id.equals(selected)) return;
        selected = id;
        back();
        if (inRange() && !selected.isEmpty()) ModNetwork.requestSelection(center, selected);
    }

    boolean hasRitual() { return !selected.isEmpty(); }

    boolean inRange() {
        final var client = Minecraft.getInstance();
        return center != null && client.player != null && client.level != null
            && dimension.equals(client.level.dimension())
            && client.player.distanceToSqr(Vec3.atCenterOf(center)) <= 64.0
            && client.level.isLoaded(center)
            && client.level.getBlockState(center).is(com.kadamitas.warlockery.registry.ModBlocks.ALL.get("circle").get());
    }

    void refresh() { if (inRange()) ModNetwork.requestRefresh(center); }

    boolean tick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            if (performing) { refresh(); return true; }
        }
        return false;
    }

    void update(final List<RitualManager.RitualOption> updated) { options = List.copyOf(updated); }

    RitualManager.RitualOption selected() {
        return options.stream().filter(option -> option.id().equals(selected)).findFirst().orElse(null);
    }

    List<List<FormattedCharSequence>> pages(final ManualLayout layout) {
        final var font = Minecraft.getInstance().font;
        final var text = new ArrayList<Component>();
        final var option = selected();
        if (!inRange()) {
            text.add(Component.translatable("screen.warlockery.manual.ritual_find_heart"));
        } else if (option == null) {
            text.add(Component.translatable("screen.warlockery.manual.ritual_loading"));
        } else {
            text.add(Component.translatable("screen.warlockery.ritual.casting_time", Math.max(1, option.castingTime() / 20)));
            if (RitualUiState.castInProgress(option)) {
                text.add(Component.translatable("screen.warlockery.ritual.casting"));
                text.add(Component.translatable("screen.warlockery.ritual.casting_help"));
            } else {
                text.add(Component.translatable("screen.warlockery.ritual.power", option.altarPower(), option.power()));
                text.add(Component.empty());
                RitualUiState.checklistRows(option).stream().map(RitualRequirementText::line).forEach(text::add);
            }
        }
        final List<FormattedCharSequence> lines = text.stream().flatMap(line -> font.split(
            ManualTypography.readable(line, 0x3A271F), textWidth(layout)).stream()).toList();
        final int capacity = Math.max(1, (layout.controlTop() - 105 - layout.bodyTextTop()) / ManualTypography.BODY_LINE_HEIGHT);
        return ManualPagination.pages(lines, capacity, capacity, line -> false);
    }

    void addWidgets(final ManualLayout layout, final Consumer<Button> add, final Runnable rebuild,
        final Runnable close) {
        final int x = textX(layout);
        final int width = textWidth(layout);
        final var pages = pages(layout);
        detailPage = Math.clamp(detailPage, 0, pages.size() - 1);
        final int actionY = layout.controlTop() - 72;
        final int half = (width - 4) / 2;
        final var option = selected();
        final boolean active = option != null && RitualUiState.castInProgress(option);
        final Button cast = Button.builder(Component.translatable(active ? "screen.warlockery.ritual.stop"
            : "screen.warlockery.ritual.begin"), button -> {
                if (active) ModNetwork.requestCancellation(center);
                else if (selected() != null && selected().ready()) ModNetwork.requestActivation(center, selected);
            }).bounds(x, actionY, half, 20).build();
        cast.active = inRange() && option != null && (active || option.ready());
        add.accept(cast);
        final Button refresh = Button.builder(Component.translatable("screen.warlockery.ritual.refresh"),
            button -> refresh()).bounds(x + half + 4, actionY, half, 20).build();
        refresh.active = inRange();
        add.accept(refresh);
        if (pages.size() > 1) {
            final Button previous = Button.builder(Component.literal("‹"), button -> { detailPage--; rebuild.run(); })
                .bounds(x, actionY - 24, 24, 20).build();
            previous.active = detailPage > 0;
            add.accept(previous);
            final Button next = Button.builder(Component.literal("›"), button -> { detailPage++; rebuild.run(); })
                .bounds(x + width - 24, actionY - 24, 24, 20).build();
            next.active = detailPage + 1 < pages.size();
            add.accept(next);
        }
        add.accept(Button.builder(ManualTypography.readable(Component.translatable("screen.warlockery.manual.back_to_ritual")),
            button -> { back(); rebuild.run(); }).bounds(x, layout.controlTop() - 24, width, 20).build());
        add.accept(Button.builder(Component.translatable("screen.warlockery.manual.close"), button -> close.run())
            .bounds(x, layout.controlTop(), width, 20).build());
    }

    void render(final GuiGraphicsExtractor graphics, final ManualLayout layout) {
        final var font = Minecraft.getInstance().font;
        final int x = textX(layout);
        final int width = textWidth(layout);
        final var pages = pages(layout);
        detailPage = Math.clamp(detailPage, 0, pages.size() - 1);
        int y = layout.bodyTextTop();
        for (var line : pages.get(detailPage)) {
            graphics.text(font, line, x, y, -1, false);
            y += ManualTypography.BODY_LINE_HEIGHT;
        }
        final var option = selected();
        if (option != null && RitualUiState.castInProgress(option)) {
            final var client = Minecraft.getInstance();
            final var heart = client.level != null && center != null && dimension.equals(client.level.dimension())
                && client.level.getBlockEntity(center) instanceof CircleHeartBlockEntity entity ? entity : null;
            final int percent = heart != null && heart.total() > 0 ? Math.clamp(heart.elapsed() * 100 / heart.total(), 0, 100) : 0;
            final int barY = layout.controlTop() - 36;
            graphics.fill(x, barY, x + width, barY + 7, 0xFF4A3028);
            graphics.fill(x + 1, barY + 1, x + 1 + (width - 2) * percent / 100, barY + 6, 0xFF9A78B5);
            graphics.text(font, Component.translatable("screen.warlockery.manual.ritual_progress", percent), x, barY - 12, 0xFF5B1F31, false);
        }
        if (pages.size() > 1) {
            final Component count = Component.translatable("screen.warlockery.manual.page", detailPage + 1, pages.size());
            graphics.text(font, count, x + (width - font.width(count)) / 2, layout.controlTop() - 91, 0xFF795A44, false);
        }
    }

    boolean scroll(final double delta) {
        if (!performing || delta == 0) return false;
        detailPage = Math.max(0, detailPage + (delta > 0 ? -1 : 1));
        return true;
    }

    private static int textX(final ManualLayout layout) {
        return layout.contentLeft() + Math.min(14, Math.max(6, layout.contentWidth() / 12));
    }

    private static int textWidth(final ManualLayout layout) { return layout.contentRight() - textX(layout) - Math.min(14, Math.max(6, layout.contentWidth() / 12)); }
}
