package com.kadamitas.warlockery.compat.rrv;

import cc.cassian.rrv.api.client.RecipeScreenContext;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.kadamitas.warlockery.client.MachineScreen;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

final class WarlockeryRrvRecipe implements ReliableClientRecipe {
    private final Identifier id;
    private final WarlockeryRrvType type;
    private final List<SlotContent> inputs;
    private final List<SlotContent> outputs;
    private final List<SlotContent> tools;
    private final Component text;
    private final MachineRecipeManager.Match machine;
    private final List<ChalkCircleLayout.Ring> rings;
    private final boolean information;
    private List<FormattedCharSequence> lines = List.of();
    private int page;

    WarlockeryRrvRecipe(Identifier id, WarlockeryRrvType type, List<SlotContent> inputs,
                       List<SlotContent> outputs, List<SlotContent> tools, Component text,
                       MachineRecipeManager.Match machine, List<ChalkCircleLayout.Ring> rings, boolean information) {
        this.id = id;
        this.type = type;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.tools = List.copyOf(tools);
        this.text = text;
        this.machine = machine;
        this.rings = List.copyOf(rings);
        this.information = information;
        if (inputs.size() > 8 || outputs.size() > 4 || tools.size() > 8) {
            throw new IllegalArgumentException("Recipe exceeds display capacity: " + id);
        }
    }

    @Override public Identifier getId() { return id; }
    @Override public ReliableClientRecipeType getType() { return type; }
    @Override public List<SlotContent> getIngredients() {
        var indexed = new ArrayList<>(inputs);
        indexed.addAll(tools);
        return List.copyOf(indexed);
    }
    @Override public List<SlotContent> getResults() { return outputs; }
    @Override public boolean isVisualOnly() { return information; }
    @Override public void bindSlots(RecipeViewMenu.SlotFillContext slots) {
        for (int i = 0; i < type.getSlotCount(); i++) {
            SlotContent content = i < inputs.size() ? inputs.get(i)
                : i >= 8 && i < 8 + outputs.size() ? outputs.get(i - 8)
                : i >= 12 && i < 12 + tools.size() ? tools.get(i - 12) : SlotContent.of();
            slots.bindOptionalSlot(i, content, RecipeViewMenu.OptionalSlotRenderer.DEFAULT);
            if (i >= 12) slots.addAdditionalStackModifier(i, (_, tooltip) -> tooltip.add(
                Component.translatable("jei.warlockery.ritual.not_consumed")));
        }
    }

    @Override public void addRecipeWidgets(RecipeScreenContext context) {
        lines = context.font().split(text, rings.isEmpty() ? 228 : 156);
        page = Math.clamp(page, 0, pages() - 1);
        if (pages() <= 1) return;
        int left = context.recipePosition().left();
        int top = context.recipePosition().top();
        context.widgets().addRecipeWidget(Button.builder(Component.literal("<"), _ -> page = Math.max(0, page - 1))
            .bounds(left + 4, top + 154, 20, 16).build());
        context.widgets().addRecipeWidget(Button.builder(Component.literal(">"), _ -> page = Math.min(pages() - 1, page + 1))
            .bounds(left + 88, top + 154, 20, 16).build());
    }

    static int pageCount(int lineCount) { return Math.max(1, (lineCount + 7) / 8); }
    private int pages() { return pageCount(lines.size()); }

    @Override public void renderRecipe(RecipeScreenContext context) {
        var graphics = context.guiGraphics();
        graphics.text(context.font(), "->", 111, 19, 0xFF404040, false);
        if (lines.isEmpty()) lines = context.font().split(text, rings.isEmpty() ? 228 : 156);
        for (int i = page * 8; i < Math.min(lines.size(), (page + 1) * 8); i++) {
            graphics.text(context.font(), lines.get(i), 4, 72 + (i % 8) * 10, 0xFF303030, false);
        }
        if (pages() > 1) graphics.text(context.font(), (page + 1) + " / " + pages(), 30, 158, 0xFF404040, false);
        if (!rings.isEmpty()) {
            int radius = rings.stream().mapToInt(r -> r.size().radius()).max().orElse(1);
            int step = Math.max(2, 62 / (radius * 2 + 1));
            int cx = 198, cy = 108;
            graphics.fill(164, 73, 232, 143, 0xFF29232E);
            graphics.fill(cx, cy, cx + step - 1, cy + step - 1, 0xFFFFCF48);
            for (var ring : rings) {
                int color = switch (ring.glyph()) {
                    case "circleglyphinfernal" -> 0xFFE36A64;
                    case "circleglyph_veil" -> 0xFFC48BEF;
                    case "circleglyphgolden" -> 0xFFFFCF48;
                    default -> 0xFFFFFFFF;
                };
                for (var offset : ring.size().offsets()) {
                    int x = cx + offset.getX() * step, y = cy + offset.getZ() * step;
                    graphics.fill(x, y, x + step - 1, y + step - 1, color);
                }
            }
        }
    }

    @Override public boolean supportsItemTransfer() { return machine != null && machine.recipe().fluid().isEmpty(); }
    @Override public List<Class<? extends AbstractContainerScreen<?>>> getTransferClasses() { return List.of(MachineScreen.class); }
    @Override public boolean canTransferToScreen(AbstractContainerScreen<?> screen) {
        return supportsItemTransfer() && screen instanceof MachineScreen target && target.getMenu().kind().equals(machine.recipe().machine());
    }
    @Override public void mapRecipeItems(RecipeTransferMap map, AbstractContainerScreen<?> screen) {
        if (!canTransferToScreen(screen)) return;
        var slots = MachineRecipeSlotPlan.inputSlots(MachineProfiles.forRecipeType(machine.recipe().machine()).orElseThrow(), machine.recipe());
        for (int i = 0; i < slots.size(); i++) map.linkSlots(i, slots.get(i));
    }
}
