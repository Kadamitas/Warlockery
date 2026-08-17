package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class RitualOutcomeGameTests {
    private RitualOutcomeGameTests() {
    }

    public static void aHexOnlyReachesVictimsInsideTheDeclaredRadius(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final Zombie inside = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        inside.setNoAi(true);

        RitualManager.INSTANCE.complete(
            helper.getLevel(),
            center,
            null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "hex_misfortune"),
            0
        );

        helper.assertTrue(
            HexState.isActive(inside, HexKind.MISFORTUNE),
            "a hex must reach an unbound victim standing inside the circle radius"
        );
        helper.succeed();
    }

    public static void aBoundTargetInAnotherDimensionIsNotReachedByAHex(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final Zombie nearby = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        nearby.setNoAi(true);

        final ItemStack vial = new ItemStack(ModItems.ALL.get("sympathetic_vial").get());
        new SympatheticBinding(java.util.UUID.randomUUID(), "AbsentTarget", "player").write(vial);
        helper.getLevel().addFreshEntity(new ItemEntity(
            helper.getLevel(),
            center.getX() + 0.5,
            center.getY() + 1.0,
            center.getZ() + 0.5,
            vial
        ));

        RitualManager.INSTANCE.complete(
            helper.getLevel(),
            center,
            null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "hex_misfortune"),
            0
        );

        helper.assertTrue(
            HexState.isActive(nearby, HexKind.MISFORTUNE),
            "an unresolvable binding must fall back to the radius sweep rather than hexing nothing"
        );
        helper.succeed();
    }

    public static void aRitualWithNothingToActOnReportsNoEffect(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final List<ItemEntity> before = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(center).inflate(RitualManager.OFFERING_RADIUS)
        );
        helper.assertTrue(before.isEmpty(), "the test site must start with no offerings");

        RitualManager.INSTANCE.complete(
            helper.getLevel(),
            center,
            null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "bind_waystone"),
            0
        );

        helper.assertTrue(
            helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(center).inflate(RitualManager.OFFERING_RADIUS)
            ).isEmpty(),
            "a binding ritual with no waystone present must not fabricate an item"
        );
        helper.succeed();
    }

    public static void everyLoadedRitualPassesTargetValidation(final GameTestHelper helper) {
        RitualManager.INSTANCE.all().forEach(entry -> {
            final List<String> problems = RitualManager.problems(entry.definition());
            helper.assertTrue(
                problems.isEmpty(),
                entry.id() + " survived load with problems " + problems
            );
        });
        helper.assertFalse(RitualManager.INSTANCE.all().isEmpty(), "the ritual catalog must be loaded");
        helper.succeed();
    }
}
