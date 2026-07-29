package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class AltarUpgradeResolver {
    private AltarUpgradeResolver() {
    }

    public static Stream<UpgradeClass> classes(final BlockState state) {
        return Stream.of(UpgradeClass.values()).filter(upgrade -> upgrade.matches(state));
    }

    public static Stream<UpgradeClass> classes(final ItemStack stack) {
        return Stream.of(UpgradeClass.values()).filter(upgrade -> upgrade.matches(stack));
    }

    public static Modifiers resolve(final Stream<UpgradeClass> upgrades) {
        final EnumSet<UpgradeClass> active = upgrades.collect(
            () -> EnumSet.noneOf(UpgradeClass.class),
            EnumSet::add,
            EnumSet::addAll
        );
        final int capacityMultiplier = 1 + active.stream().mapToInt(UpgradeClass::capacityBonus).sum();
        final int rechargeMultiplier = active.stream().mapToInt(UpgradeClass::rechargeMultiplier).reduce(1, (a, b) -> a * b);
        return new Modifiers(capacityMultiplier, rechargeMultiplier, active);
    }

    public static Modifiers discoverItems(
        final Level level,
        final BlockPos origin,
        final int radius,
        final Stream<UpgradeClass> blockUpgrades
    ) {
        final AABB search = new AABB(origin).inflate(radius, 6.0, radius);
        final Stream<UpgradeClass> framed = level.getEntitiesOfClass(ItemFrame.class, search).stream()
            .flatMap(frame -> classes(frame.getItem()));
        final Stream<UpgradeClass> dropped = level.getEntitiesOfClass(ItemEntity.class, search).stream()
            .flatMap(entity -> classes(entity.getItem()));
        return resolve(Stream.concat(blockUpgrades, Stream.concat(framed, dropped)));
    }

    public enum UpgradeClass {
        CANDELABRA(0, 2),
        CHALICE(1, 1),
        PENTACLE(0, 2);

        private final int capacityBonus;
        private final int rechargeMultiplier;

        UpgradeClass(final int capacityBonus, final int rechargeMultiplier) {
            this.capacityBonus = capacityBonus;
            this.rechargeMultiplier = rechargeMultiplier;
        }

        public int capacityBonus() {
            return capacityBonus;
        }

        public int rechargeMultiplier() {
            return rechargeMultiplier;
        }

        private boolean matches(final BlockState state) {
            return switch (this) {
                case CANDELABRA -> state.is(WarlockeryTags.Blocks.ALTAR_CANDELABRA_UPGRADES);
                case CHALICE -> state.is(WarlockeryTags.Blocks.ALTAR_CHALICE_UPGRADES);
                case PENTACLE -> state.is(WarlockeryTags.Blocks.ALTAR_PENTACLE_UPGRADES);
            };
        }

        private boolean matches(final ItemStack stack) {
            return switch (this) {
                case CANDELABRA -> stack.is(WarlockeryTags.Items.ALTAR_CANDELABRA_UPGRADES);
                case CHALICE -> stack.is(WarlockeryTags.Items.ALTAR_CHALICE_UPGRADES);
                case PENTACLE -> stack.is(WarlockeryTags.Items.ALTAR_PENTACLE_UPGRADES);
            };
        }
    }

    public record Modifiers(
        int capacityMultiplier,
        int rechargeMultiplier,
        Set<UpgradeClass> activeClasses
    ) {
        public Modifiers {
            activeClasses = Set.copyOf(activeClasses);
        }

        public int applyCapacity(final int baseCapacity) {
            return baseCapacity * capacityMultiplier;
        }

        public int applyRecharge(final int baseRecharge) {
            return baseRecharge * rechargeMultiplier;
        }
    }
}
