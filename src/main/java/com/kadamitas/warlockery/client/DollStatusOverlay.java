package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.DollKind;
import com.kadamitas.warlockery.network.ModNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;

public final class DollStatusOverlay {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "doll_status");
    private static final Identifier DOLL_ICON = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID, "textures/gui/doll_status.png"
    );
    private static final Identifier HEX_ICON = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID, "textures/gui/hex_status.png"
    );
    private static final int ROW_WIDTH = 144;
    private static final int ROW_HEIGHT = 23;
    private static final int MAX_ROWS = 8;
    private static final int VANILLA_BOTTOM_RESERVE = 28;
    private static final Map<DollKind, Long> ACTIVATIONS = new LinkedHashMap<>();

    private DollStatusOverlay() {
    }

    public static void activate(final ModNetwork.DollActivationPayload payload) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        DollKind.find(payload.dollKind()).ifPresent(kind ->
            ACTIVATIONS.put(kind, (long) minecraft.player.tickCount + payload.displayTicks()));
    }

    public static void extract(final GuiGraphicsExtractor graphics, final net.minecraft.client.DeltaTracker deltaTracker) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        final long tick = minecraft.player.tickCount;
        ACTIVATIONS.entrySet().removeIf(entry -> entry.getValue() < tick);
        final int startY = Math.max(24, SupernaturalStatusOverlay.resourceStackBottom(minecraft) + 4);
        final int visibleRows = PlayerResourceHudLayout.visibleRowCount(
            minecraft.getWindow().getGuiScaledHeight(),
            startY,
            ROW_HEIGHT,
            MAX_ROWS,
            VANILLA_BOTTOM_RESERVE
        );
        final List<Row> rows = rows(minecraft, tick).stream().limit(visibleRows).toList();
        for (int index = 0; index < rows.size(); index++) {
            renderRow(graphics, minecraft, rows.get(index), 6, startY + index * ROW_HEIGHT);
        }
    }

    static List<Row> rows(final Minecraft minecraft, final long tick) {
        final Map<DollKind, ItemStack> boundDolls = new LinkedHashMap<>();
        final var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof DollItem item && DollItem.isBoundTo(stack, minecraft.player)) {
                boundDolls.putIfAbsent(item.kind(), stack);
            }
        }

        final List<Row> rows = new ArrayList<>();
        boundDolls.forEach((kind, stack) -> rows.add(new Row(
            Component.translatable("item.warlockery." + kind.id()),
            chargeText(stack, ACTIVATIONS.getOrDefault(kind, 0L) >= tick),
            DOLL_ICON,
            ACTIVATIONS.getOrDefault(kind, 0L) >= tick
        )));
        ACTIVATIONS.forEach((kind, endTick) -> {
            if (endTick >= tick && !boundDolls.containsKey(kind)) {
                rows.add(new Row(
                    Component.translatable("item.warlockery." + kind.id()),
                    Component.translatable("overlay.warlockery.doll.activated"),
                    DOLL_ICON,
                    true
                ));
            }
        });
        minecraft.player.getActiveEffects().stream()
            .filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
            .sorted(Comparator.comparingInt(MobEffectInstance::getDuration))
            .map(effect -> new Row(
                Component.translatable("overlay.warlockery.hex", effect.getEffect().value().getDisplayName()),
                MobEffectUtil.formatDuration(effect, 1.0F, minecraft.level.tickRateManager().tickrate()),
                HEX_ICON,
                false
            ))
            .forEach(rows::add);
        return List.copyOf(rows);
    }

    private static Component chargeText(final ItemStack stack, final boolean active) {
        if (active) {
            return Component.translatable("overlay.warlockery.doll.activated");
        }
        if (stack.isDamageableItem()) {
            return Component.translatable(
                "overlay.warlockery.doll.charges",
                stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage()
            );
        }
        return Component.translatable("overlay.warlockery.doll.ready");
    }

    private static void renderRow(
        final GuiGraphicsExtractor graphics,
        final Minecraft minecraft,
        final Row row,
        final int x,
        final int y
    ) {
        graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT - 1, row.active() ? 0xC02A4B2A : 0xB0181022);
        graphics.outline(x, y, ROW_WIDTH, ROW_HEIGHT - 1, row.active() ? 0xFF66FF66 : 0xFF6B537C);
        graphics.blit(RenderPipelines.GUI_TEXTURED, row.icon(), x + 3, y + 3, 0, 0, 16, 16, 16, 16);
        graphics.text(minecraft.font, row.title(), x + 23, y + 3, row.active() ? 0xFF88FF88 : 0xFFF3E9FF, true);
        graphics.text(minecraft.font, row.detail(), x + 23, y + 12, 0xFFB9AFC5, false);
    }

    record Row(Component title, Component detail, Identifier icon, boolean active) {
    }
}
