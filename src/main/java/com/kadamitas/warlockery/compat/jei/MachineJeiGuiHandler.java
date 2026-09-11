package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.client.MachineScreen;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.types.IRecipeType;

final class MachineJeiGuiHandler implements IGuiContainerHandler<MachineScreen> {
    @Override public Collection<IGuiClickableArea> getGuiClickableAreas(MachineScreen screen, double mouseX, double mouseY) {
        String kind = screen.getMenu().kind();
        IRecipeType<?> type = WarlockeryJeiRecipeTypes.MACHINES.get(kind);
        if (type == null) return List.of();
        var layout = screen.getMenu().layout();
        IRecipeType<?>[] types = kind.equals("cauldron")
            ? new IRecipeType<?>[] {type, WarlockeryJeiRecipeTypes.CUSTOM_BREWS}
            : new IRecipeType<?>[] {type};
        return List.of(IGuiClickableArea.createBasic(25, layout.statusY(), layout.width() - 42, 16, types));
    }
}
