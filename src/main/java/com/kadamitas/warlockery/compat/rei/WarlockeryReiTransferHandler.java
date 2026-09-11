package com.kadamitas.warlockery.compat.rei;

import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.menu.MachineMenu;
import java.util.stream.IntStream;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import net.minecraft.network.chat.Component;

/** Item transfer for the live nine-slot machine menus. Fluid tanks remain a manual player action. */
public final class WarlockeryReiTransferHandler implements SimpleTransferHandler {
    private static final Component MANUAL_FLUID = Component.literal(
        "Add the required fluid manually; REI item transfer does not fill machine tanks."
    );
    private static final int MACHINE_SLOT_COUNT = 9;
    private final String machine;

    public WarlockeryReiTransferHandler(final String machine) {
        this.machine = machine;
    }

    @Override
    public TransferHandler.ApplicabilityResult checkApplicable(final TransferHandler.Context context) {
        if (!(context.getMenu() instanceof MachineMenu menu)
            || !(context.getDisplay() instanceof WarlockeryReiDisplay display)
            || display.kind() != WarlockeryReiDisplay.Kind.MACHINE
            || !machine.equals(menu.kind())
            || !machine.equals(display.machine())) {
            return TransferHandler.ApplicabilityResult.createNotApplicable();
        }
        if (!isDryTransfer(display)) {
            return TransferHandler.ApplicabilityResult.createApplicableWithError(MANUAL_FLUID);
        }
        return TransferHandler.ApplicabilityResult.createApplicable();
    }

    @Override
    public Iterable<SlotAccessor> getInputSlots(final TransferHandler.Context context) {
        final WarlockeryReiDisplay display = (WarlockeryReiDisplay) context.getDisplay();
        final var profile = MachineProfiles.forRecipeType(machine).orElseThrow();
        return MachineRecipeSlotPlan.inputSlots(profile, display.machineRecipe().recipe()).stream()
            .map(context.getMenu()::getSlot)
            .map(SlotAccessor::fromSlot)
            .toList();
    }

    @Override
    public Iterable<SlotAccessor> getInventorySlots(final TransferHandler.Context context) {
        return IntStream.range(MACHINE_SLOT_COUNT, context.getMenu().slots.size())
            .mapToObj(context.getMenu()::getSlot)
            .map(SlotAccessor::fromSlot)
            .toList();
    }

    static boolean isDryTransfer(final WarlockeryReiDisplay display) {
        return display.kind() == WarlockeryReiDisplay.Kind.MACHINE
            && display.machineRecipe().recipe().fluid().isEmpty();
    }
}
