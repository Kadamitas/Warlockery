package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.transformation.VampireSustenanceRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public final class VampireBloodHud {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID, "vampire_blood_pool"
    );
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID, "textures/gui/vampire_blood_pool.png"
    );
    private static final int WIDTH = 81;
    private static final int HEIGHT = 14;
    private static final int INNER_TOP = 3;
    private static final int INNER_HEIGHT = 8;

    private VampireBloodHud() {
    }

    public static void extract(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
            || minecraft.level == null
            || minecraft.gameMode == null
            || !minecraft.gameMode.canHurtPlayer()
            || minecraft.gui.hud.getVehicleMaxHearts(minecraft.gui.hud.getPlayerVehicleWithHealth()) != 0) {
            return;
        }
        extract(graphics, minecraft.player, graphics.guiHeight() - 39, graphics.guiWidth() / 2 + 91);
    }

    public static boolean extract(
        final GuiGraphicsExtractor graphics,
        final Player player,
        final int yLineBase,
        final int xRight
    ) {
        if (!ClientSupernaturalState.isVampire()) {
            return false;
        }
        final ModNetwork.SupernaturalSnapshot snapshot = ClientSupernaturalState.snapshot();
        final int x = xRight - WIDTH;
        final int y = yLineBase - 3;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF080A0F);
        final int fill = VampireBloodHudModel.filledHeight(snapshot.resource(), snapshot.maxResource(), INNER_HEIGHT);
        if (fill > 0) {
            final int fillTop = y + INNER_TOP + INNER_HEIGHT - fill;
            graphics.fill(x + 3, fillTop, x + WIDTH - 3, y + INNER_TOP + INNER_HEIGHT, 0xE5A10D31);
            graphics.fill(x + 4, fillTop, x + WIDTH - 4, Math.min(y + INNER_TOP + INNER_HEIGHT, fillTop + 1), 0xFFE75B76);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);

        final VampireSustenanceRules.Status status = VampireSustenanceRules.status(
            snapshot.resource(), snapshot.maxResource(), snapshot.sanguine()
        );
        final Component label = Component.translatable(VampireBloodHudModel.statusKey(status));
        final Minecraft minecraft = Minecraft.getInstance();
        final int textX = x + (WIDTH - minecraft.font.width(label)) / 2;
        final int alpha = Math.round(VampireBloodHudModel.pulseAlpha(status, player.tickCount) * 255.0F);
        graphics.text(minecraft.font, label, textX, y + 3, alpha << 24 | 0xF6DCE3, true);
        return true;
    }
}
