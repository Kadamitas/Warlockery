package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.entity.CreatureBehaviorRules;
import com.kadamitas.warlockery.entity.CreatureBehaviorTags;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

public final class AltarPowerNetwork {
    private static final int HORIZONTAL_RANGE = 12;
    private static final int DOWN_RANGE = 4;
    private static final int UP_RANGE = 6;

    private AltarPowerNetwork() {
    }

    public static int available(final ServerLevel level, final BlockPos center) {
        return best(level, center).map(AltarBlockEntity::getPower).orElse(0);
    }

    public static boolean consume(final ServerLevel level, final BlockPos center, final int amount) {
        return amount <= 0 || best(level, center).filter(altar -> altar.consumePower(amount)).isPresent();
    }

    private static Optional<AltarBlockEntity> best(final ServerLevel level, final BlockPos center) {
        final int range = CreatureBehaviorRules.altarSearchRange(
            HORIZONTAL_RANGE,
            (int) level.getEntitiesOfClass(
                Mob.class,
                new AABB(center).inflate(32.0),
                creature -> creature.isAlive()
                    && creature.typeHolder().is(CreatureBehaviorTags.EntityTypes.CAULDRON_RANGE_EXTENDERS)
            ).stream().limit(2).count()
        );
        final Stream<BlockPos> ordinary = BlockPos.betweenClosedStream(
            center.offset(-range, -DOWN_RANGE, -range),
            center.offset(range, UP_RANGE, range)
        );
        final Set<BlockPos> focusedPositions = AltarRangeIndex.within(
            level,
            center,
            AltarRangeIndex.effectiveRange(range, true),
            AltarRangeIndex.effectiveRange(DOWN_RANGE, true),
            AltarRangeIndex.effectiveRange(UP_RANGE, true)
        ).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return Stream.concat(ordinary, focusedPositions.stream())
            .distinct()
            .map(level::getBlockEntity)
            .filter(AltarBlockEntity.class::isInstance)
            .map(AltarBlockEntity.class::cast)
            .filter(AltarBlockEntity::isMultiblockValid)
            .filter(altar -> !focusedPositions.contains(altar.getBlockPos()) || altar.hasRangeFocus())
            .max(Comparator.comparingInt(AltarBlockEntity::getPower));
    }
}
