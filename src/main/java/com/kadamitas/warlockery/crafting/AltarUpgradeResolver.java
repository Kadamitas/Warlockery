package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.EnumSet;
import java.util.EnumMap;
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
        final EnumSet<UpgradeClass> discovered = upgrades.collect(
            () -> EnumSet.noneOf(UpgradeClass.class),
            EnumSet::add,
            EnumSet::addAll
        );
        final EnumMap<UpgradeFamily, UpgradeClass> strongest = new EnumMap<>(UpgradeFamily.class);
        discovered.forEach(upgrade -> strongest.merge(
            upgrade.family(),
            upgrade,
            (first, second) -> first.strength() >= second.strength() ? first : second
        ));
        final EnumSet<UpgradeClass> active = strongest.values().isEmpty()
            ? EnumSet.noneOf(UpgradeClass.class)
            : EnumSet.copyOf(strongest.values());
        final double capacityMultiplier = 1.0 + active.stream().mapToDouble(UpgradeClass::capacityBonus).sum();
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
        TORCH(UpgradeFamily.LIGHT, 1, 0, 2),
        CANDELABRA(UpgradeFamily.LIGHT, 2, 0, 3),
        SKULL(UpgradeFamily.SKULL, 1, 1, 2),
        WITHER_SKULL(UpgradeFamily.SKULL, 2, 2, 3),
        PLAYER_HEAD(UpgradeFamily.SKULL, 3, 1.5, 4),
        CHALICE(UpgradeFamily.CHALICE, 1, 1, 1),
        FILLED_CHALICE(UpgradeFamily.CHALICE, 2, 2, 1),
        PENTACLE(UpgradeFamily.PENTACLE, 1, 0, 2),
        PARADOX_EGG(UpgradeFamily.PARADOX_EGG, 1, 9, 10);

        private final UpgradeFamily family;
        private final int strength;
        private final double capacityBonus;
        private final int rechargeMultiplier;

        UpgradeClass(
            final UpgradeFamily family,
            final int strength,
            final double capacityBonus,
            final int rechargeMultiplier
        ) {
            this.family = family;
            this.strength = strength;
            this.capacityBonus = capacityBonus;
            this.rechargeMultiplier = rechargeMultiplier;
        }

        public UpgradeFamily family() {
            return family;
        }

        public int strength() {
            return strength;
        }

        public double capacityBonus() {
            return capacityBonus;
        }

        public int rechargeMultiplier() {
            return rechargeMultiplier;
        }

        private boolean matches(final BlockState state) {
            return switch (this) {
                case TORCH -> state.is(WarlockeryTags.Blocks.ALTAR_TORCH_UPGRADES);
                case CANDELABRA -> state.is(WarlockeryTags.Blocks.ALTAR_CANDELABRA_UPGRADES);
                case SKULL -> state.is(WarlockeryTags.Blocks.ALTAR_SKULL_UPGRADES);
                case WITHER_SKULL -> state.is(WarlockeryTags.Blocks.ALTAR_WITHER_SKULL_UPGRADES);
                case PLAYER_HEAD -> state.is(WarlockeryTags.Blocks.ALTAR_PLAYER_HEAD_UPGRADES);
                case CHALICE -> state.is(WarlockeryTags.Blocks.ALTAR_CHALICE_UPGRADES);
                case FILLED_CHALICE -> state.is(WarlockeryTags.Blocks.ALTAR_FILLED_CHALICE_UPGRADES);
                case PENTACLE -> state.is(WarlockeryTags.Blocks.ALTAR_PENTACLE_UPGRADES);
                case PARADOX_EGG -> state.is(WarlockeryTags.Blocks.ALTAR_PARADOX_EGG_UPGRADES);
            };
        }

        private boolean matches(final ItemStack stack) {
            return switch (this) {
                case TORCH -> stack.is(WarlockeryTags.Items.ALTAR_TORCH_UPGRADES);
                case CANDELABRA -> stack.is(WarlockeryTags.Items.ALTAR_CANDELABRA_UPGRADES);
                case SKULL -> stack.is(WarlockeryTags.Items.ALTAR_SKULL_UPGRADES);
                case WITHER_SKULL -> stack.is(WarlockeryTags.Items.ALTAR_WITHER_SKULL_UPGRADES);
                case PLAYER_HEAD -> stack.is(WarlockeryTags.Items.ALTAR_PLAYER_HEAD_UPGRADES);
                case CHALICE -> stack.is(WarlockeryTags.Items.ALTAR_CHALICE_UPGRADES);
                case FILLED_CHALICE -> stack.is(WarlockeryTags.Items.ALTAR_FILLED_CHALICE_UPGRADES);
                case PENTACLE -> stack.is(WarlockeryTags.Items.ALTAR_PENTACLE_UPGRADES);
                case PARADOX_EGG -> stack.is(WarlockeryTags.Items.ALTAR_PARADOX_EGG_UPGRADES);
            };
        }
    }

    public enum UpgradeFamily {
        LIGHT,
        SKULL,
        CHALICE,
        PENTACLE,
        PARADOX_EGG
    }

    public record Modifiers(
        double capacityMultiplier,
        int rechargeMultiplier,
        Set<UpgradeClass> activeClasses
    ) {
        public Modifiers {
            activeClasses = Set.copyOf(activeClasses);
        }

        public int applyCapacity(final int baseCapacity) {
            return (int) Math.floor(baseCapacity * capacityMultiplier);
        }

        public int applyRecharge(final int baseRecharge) {
            return baseRecharge * rechargeMultiplier;
        }
    }
}
