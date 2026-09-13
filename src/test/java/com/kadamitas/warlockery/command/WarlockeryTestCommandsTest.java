package com.kadamitas.warlockery.command;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class WarlockeryTestCommandsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nativeCommandTreeRejectsOrdinaryPlayersAndModerators() {
        final var dispatcher = new CommandDispatcher<CommandSourceStack>();
        WarlockeryTestCommands.register(dispatcher);
        final var root = dispatcher.getRoot().getChild("warlockery");
        assertFalse(root.canUse(Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS)));
        assertFalse(root.canUse(Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR)));
        assertTrue(root.canUse(Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER)));
        assertNotNull(root.getChild("test").getChild("event"));
    }

    @Test
    void circlePlanPreservesDistinctConcentricRingsAndFunctionalHeart() {
        final BlockPos center = new BlockPos(15, 80, -17);
        final var plan = WarlockeryTestCommands.circlePlan("manifestation", center,
            Map.of("circleglyphritual", 16, "circleglyph_veil", 16));
        assertEquals("circle", plan.get(center));
        assertEquals(1, plan.values().stream().filter("circle"::equals).count());
        assertFalse(plan.containsValue("circleglyphgolden"));
        assertEquals(69, plan.size());
        assertTrue(plan.keySet().stream().allMatch(pos -> pos.getY() == 80
            && Math.abs(pos.getX() - 15) <= 7 && Math.abs(pos.getZ() + 17) <= 7));
    }

    @Test
    void conversionPlanDrawsVeilSourceRatherThanRitualOutput() {
        final var plan = WarlockeryTestCommands.circlePlan("glyph_to_ritual", BlockPos.ZERO,
            Map.of("circleglyphritual", 8));
        assertEquals(17, plan.size());
        assertFalse(plan.containsValue("circleglyphritual"));
        assertEquals(16, plan.values().stream().filter("circleglyph_veil"::equals).count());
    }
}
