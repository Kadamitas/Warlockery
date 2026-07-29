package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class MagicConstructData extends SavedData {
    private static final Codec<MagicConstructData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Construct.CODEC.listOf().optionalFieldOf("constructs", List.of()).forGetter(data -> data.constructs)
    ).apply(instance, MagicConstructData::new));
    public static final SavedDataType<MagicConstructData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "magic_constructs"),
        MagicConstructData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<Construct> constructs;

    public MagicConstructData() {
        constructs = new ArrayList<>();
    }

    private MagicConstructData(final List<Construct> constructs) {
        this.constructs = new ArrayList<>(constructs);
    }

    public static MagicConstructData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public int place(
        final ServerLevel level,
        final List<BlockPos> positions,
        final long expiration
    ) {
        final List<Cell> cells = positions.stream()
            .distinct()
            .filter(level::isLoaded)
            .filter(pos -> level.getBlockEntity(pos) == null)
            .filter(pos -> level.getBlockState(pos).canBeReplaced())
            .limit(64)
            .map(pos -> new Cell(pos.asLong(), level.getBlockState(pos)))
            .toList();
        cells.forEach(cell -> level.setBlockAndUpdate(
            BlockPos.of(cell.position()),
            Blocks.BARRIER.defaultBlockState()
        ));
        if (!cells.isEmpty()) {
            constructs.add(new Construct(cells, expiration));
            setDirty();
        }
        return cells.size();
    }

    public void tick(final ServerLevel level) {
        final List<Construct> expired = constructs.stream()
            .filter(construct -> level.getGameTime() >= construct.expiration())
            .toList();
        expired.forEach(construct -> construct.cells().forEach(cell -> {
            final BlockPos position = BlockPos.of(cell.position());
            if (level.isLoaded(position) && level.getBlockState(position).is(Blocks.BARRIER)) {
                level.setBlockAndUpdate(position, cell.previous());
            }
        }));
        if (!expired.isEmpty()) {
            constructs.removeAll(expired);
            setDirty();
        }
    }

    public int activeConstructs() {
        return constructs.size();
    }

    public record Cell(long position, BlockState previous) {
        private static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Cell::position),
            BlockState.CODEC.fieldOf("previous").forGetter(Cell::previous)
        ).apply(instance, Cell::new));
    }

    public record Construct(List<Cell> cells, long expiration) {
        private static final Codec<Construct> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Cell.CODEC.listOf().fieldOf("cells").forGetter(Construct::cells),
            Codec.LONG.fieldOf("expiration").forGetter(Construct::expiration)
        ).apply(instance, Construct::new));

        public Construct {
            cells = List.copyOf(cells);
        }
    }
}
