package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.client.PlayerResourceHudModel.Kind;
import com.kadamitas.warlockery.client.PlayerResourceHudModel.Meter;
import com.kadamitas.warlockery.network.ModNetwork;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class SupernaturalStatusOverlay {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID,
        "personal_resources"
    );
    private static final int WIDTH = 158;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 3;
    private static ModNetwork.SupernaturalSnapshot snapshot = new ModNetwork.SupernaturalSnapshot(
        "none", 0, 0, 0, "", "", "", ""
    );
    private static UUID recipient;

    private SupernaturalStatusOverlay() {
    }

    public static void update(final ModNetwork.SupernaturalSnapshotPayload payload) {
        snapshot = payload.snapshot();
        final Minecraft minecraft = Minecraft.getInstance();
        recipient = minecraft.player == null ? null : minecraft.player.getUUID();
    }

    public static void clear() {
        snapshot = new ModNetwork.SupernaturalSnapshot("none", 0, 0, 0, "", "", "", "");
        recipient = null;
    }

    public static void extract(
        final GuiGraphicsExtractor graphics,
        final net.minecraft.client.DeltaTracker deltaTracker
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        final List<Meter> meters = meters(minecraft);
        final PlayerResourceHudLayout layout = PlayerResourceHudLayout.leftLane(
            meters.size(),
            8,
            8,
            ROW_HEIGHT,
            ROW_GAP
        );
        for (int index = 0; index < meters.size(); index++) {
            drawMeter(graphics, minecraft.font, meters.get(index), layout.x(), layout.rowY(index));
        }
    }

    static int resourceStackBottom(final Minecraft minecraft) {
        return PlayerResourceHudLayout.leftLane(
            meters(minecraft).size(),
            8,
            8,
            ROW_HEIGHT,
            ROW_GAP
        ).stackBottom();
    }

    private static List<Meter> meters(final Minecraft minecraft) {
        if (minecraft.player == null
            || minecraft.level == null
            || !minecraft.player.getUUID().equals(recipient)) {
            return List.of();
        }
        return PlayerResourceHudModel.meters(snapshot, holdsFocus(minecraft));
    }

    private static void drawMeter(
        final GuiGraphicsExtractor graphics,
        final Font font,
        final Meter meter,
        final int x,
        final int y
    ) {
        final Palette palette = palette(meter.kind());
        graphics.fill(x + 2, y + 2, x + WIDTH + 2, y + ROW_HEIGHT + 2, 0x70000000);
        graphics.fill(x, y, x + WIDTH, y + ROW_HEIGHT, 0xD40A0D14);
        graphics.outline(x, y, WIDTH, ROW_HEIGHT, palette.border());
        graphics.fill(x + 1, y + 1, x + 3, y + ROW_HEIGHT - 1, palette.accent());
        drawIcon(graphics, meter.kind(), x + 7, y + 5, palette);

        final int barX = x + 18;
        final int barY = y + 14;
        final int barWidth = WIDTH - 23;
        graphics.fill(barX, barY, barX + barWidth, barY + 3, 0xFF252A35);
        final int filled = meter.filledWidth(barWidth);
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + 3, palette.fill());
            graphics.fill(barX, barY, barX + filled, barY + 1, palette.highlight());
        }

        final Component value = value(meter);
        final int valueX = x + WIDTH - 5 - font.width(value);
        final int titleWidth = Math.max(12, valueX - barX - 4);
        graphics.text(font, clipped(font, title(meter), titleWidth), barX, y + 3, palette.text(), false);
        graphics.text(font, value, valueX, y + 3, 0xFFF5F6FA, false);
    }

    private static Component title(final Meter meter) {
        if (meter.kind() == Kind.MANA) {
            return Component.translatable(
                "overlay.warlockery.resource.mana_path",
                Component.translatable("magic_path.warlockery." + meter.detail())
            );
        }
        if (meter.kind() == Kind.UNATTUNED) {
            return Component.translatable("overlay.warlockery.resource.mana");
        }
        final Component base = Component.translatable(meter.kind() == Kind.BLOOD
            ? "overlay.warlockery.resource.blood"
            : "overlay.warlockery.resource.ferocity");
        if (meter.detail().isBlank()) {
            return base;
        }
        final Component power = Component.translatable(meter.detail());
        final Component status = meter.charges() >= 0
            ? Component.translatable("overlay.warlockery.resource.charges_short", power, meter.charges())
            : meter.cooldownTicks() > 0
                ? Component.translatable("overlay.warlockery.resource.cooldown_short", power, meter.cooldownSeconds())
                : power;
        return Component.empty().append(base).append(" · ").append(status);
    }

    private static Component value(final Meter meter) {
        if (meter.kind() == Kind.UNATTUNED) {
            return Component.translatable("overlay.warlockery.resource.unattuned");
        }
        return Component.literal(meter.resource() + "/" + meter.maximum());
    }

    private static boolean holdsFocus(final Minecraft minecraft) {
        return isFocus(minecraft.player.getMainHandItem()) || isFocus(minecraft.player.getOffhandItem());
    }

    private static boolean isFocus(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return PlayerResourceHudModel.isFocusItem(itemId.getNamespace(), itemId.getPath());
    }

    private static void drawIcon(
        final GuiGraphicsExtractor graphics,
        final Kind kind,
        final int x,
        final int y,
        final Palette palette
    ) {
        switch (kind) {
            case BLOOD -> {
                graphics.fill(x + 3, y, x + 5, y + 2, palette.highlight());
                graphics.fill(x + 2, y + 2, x + 6, y + 6, palette.accent());
                graphics.fill(x + 3, y + 6, x + 5, y + 8, palette.fill());
            }
            case FEROCITY -> {
                graphics.fill(x, y + 1, x + 2, y + 7, palette.accent());
                graphics.fill(x + 3, y, x + 5, y + 6, palette.highlight());
                graphics.fill(x + 6, y + 1, x + 8, y + 7, palette.accent());
            }
            case MANA -> {
                graphics.fill(x + 3, y, x + 5, y + 8, palette.accent());
                graphics.fill(x, y + 3, x + 8, y + 5, palette.accent());
                graphics.fill(x + 2, y + 2, x + 6, y + 6, palette.highlight());
            }
            case UNATTUNED -> {
                graphics.fill(x + 3, y, x + 5, y + 2, palette.accent());
                graphics.fill(x + 1, y + 2, x + 3, y + 6, palette.accent());
                graphics.fill(x + 5, y + 2, x + 7, y + 6, palette.accent());
                graphics.fill(x + 3, y + 6, x + 5, y + 8, palette.accent());
            }
        }
    }

    private static Palette palette(final Kind kind) {
        return switch (kind) {
            case BLOOD -> new Palette(0xFFC95772, 0xFFE17890, 0xFFA72245, 0xFFFFA0B0, 0xFFF6DCE3);
            case FEROCITY -> new Palette(0xFFD89045, 0xFFF0B763, 0xFFA96027, 0xFFFFD38A, 0xFFF9E8D0);
            case MANA -> new Palette(0xFF6FCADA, 0xFFA278E7, 0xFF4778C9, 0xFFB7F2F4, 0xFFE8E4FF);
            case UNATTUNED -> new Palette(0xFF6F7784, 0xFF8D96A4, 0xFF4E5560, 0xFFADB6C3, 0xFFD9DEE5);
        };
    }

    private static Component clipped(final Font font, final Component text, final int width) {
        if (font.width(text) <= width) {
            return text;
        }
        final String ellipsis = "…";
        return Component.literal(font.plainSubstrByWidth(text.getString(), width - font.width(ellipsis)) + ellipsis);
    }

    private record Palette(int border, int accent, int fill, int highlight, int text) {
    }
}
