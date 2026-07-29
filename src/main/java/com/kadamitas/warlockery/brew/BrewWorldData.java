package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class BrewWorldData extends SavedData {
    private static final Codec<BrewWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        TemporaryChange.CODEC.listOf().optionalFieldOf("temporary_changes", List.of())
            .forGetter(data -> data.temporaryChanges)
    ).apply(instance, BrewWorldData::new));
    public static final SavedDataType<BrewWorldData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "brew_world_effects"),
        BrewWorldData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<TemporaryChange> temporaryChanges;

    public BrewWorldData() {
        temporaryChanges = new ArrayList<>();
    }

    private BrewWorldData(final List<TemporaryChange> temporaryChanges) {
        this.temporaryChanges = new ArrayList<>(temporaryChanges);
    }

    public static BrewWorldData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public int replaceTemporarily(
        final ServerLevel level,
        final List<BlockPos> positions,
        final BlockState replacement,
        final long expiration,
        final int limit
    ) {
        final List<Cell> cells = positions.stream()
            .distinct()
            .filter(level::isLoaded)
            .filter(pos -> level.getBlockEntity(pos) == null)
            .limit(Math.clamp(limit, 1, 256))
            .map(pos -> new Cell(pos.asLong(), level.getBlockState(pos), replacement))
            .filter(cell -> !cell.previous().equals(replacement))
            .filter(cell -> level.setBlockAndUpdate(BlockPos.of(cell.position()), replacement))
            .toList();
        if (!cells.isEmpty()) {
            temporaryChanges.add(new TemporaryChange(cells, expiration));
            setDirty();
        }
        return cells.size();
    }

    public void tick(final ServerLevel level) {
        boolean changed = false;
        final ListIterator<TemporaryChange> iterator = temporaryChanges.listIterator();
        while (iterator.hasNext()) {
            final TemporaryChange change = iterator.next();
            if (level.getGameTime() < change.expiration()) {
                continue;
            }
            final List<Cell> pending = change.cells().stream()
                .filter(cell -> !resolveExpiredCell(level, cell))
                .toList();
            if (pending.size() == change.cells().size()) {
                continue;
            }
            changed = true;
            if (pending.isEmpty()) {
                iterator.remove();
            } else {
                iterator.set(new TemporaryChange(pending, change.expiration()));
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public int activeChanges() {
        return temporaryChanges.size();
    }

    private static boolean resolveExpiredCell(final ServerLevel level, final Cell cell) {
        final BlockPos position = BlockPos.of(cell.position());
        final boolean loaded = level.isLoaded(position);
        final BrewWorldRestorationRules.ExpiredCellAction action = BrewWorldRestorationRules.decide(
            loaded,
            loaded && level.getBlockState(position).equals(cell.replacement())
        );
        return switch (action) {
            case RETAIN -> false;
            case DROP -> true;
            case RESTORE -> level.setBlockAndUpdate(position, cell.previous());
        };
    }

    public record Cell(long position, BlockState previous, BlockState replacement) {
        private static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Cell::position),
            BlockState.CODEC.fieldOf("previous").forGetter(Cell::previous),
            BlockState.CODEC.fieldOf("replacement").forGetter(Cell::replacement)
        ).apply(instance, Cell::new));
    }

    public record TemporaryChange(List<Cell> cells, long expiration) {
        private static final Codec<TemporaryChange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Cell.CODEC.listOf().fieldOf("cells").forGetter(TemporaryChange::cells),
            Codec.LONG.fieldOf("expiration").forGetter(TemporaryChange::expiration)
        ).apply(instance, TemporaryChange::new));

        public TemporaryChange {
            cells = List.copyOf(cells);
        }
    }
}
