package com.kadamitas.warlockery.block.entity;

import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import com.kadamitas.warlockery.util.DataParsing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class CircleHeartBlockEntity extends BlockEntity {
    private String selected = "";
    private String selectingPlayer = "";
    private String title = "";
    private boolean ready;
    private int elapsed;
    private int total;
    private int power;
    private int requiredPower;

    public CircleHeartBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.CIRCLE_HEART.get(), pos, state);
    }

    public void select(final ServerPlayer player, final String ritual) {
        if (!(level instanceof ServerLevel server) || RitualSessionData.get(server).isActive(worldPosition)) return;
        selected = ritual;
        selectingPlayer = player.getStringUUID();
        refresh(server);
    }

    public static void serverTick(final Level level, final BlockPos pos, final CircleHeartBlockEntity heart) {
        if (level instanceof ServerLevel server && !heart.selected.isEmpty() && level.getGameTime() % 10 == 0) {
            heart.refresh(server);
        }
    }

    private void refresh(final ServerLevel server) {
        final var progress = RitualSessionData.get(server).progressAt(worldPosition);
        elapsed = progress.elapsed();
        total = progress.total();
        if (!progress.ritual().isEmpty()) selected = progress.ritual();
        final var player = DataParsing.uuid(selectingPlayer).map(server::getPlayerByUUID).orElse(null);
        final var id = Identifier.tryParse(selected);
        final var option = id == null ? java.util.Optional.<RitualManager.RitualOption>empty()
            : RitualManager.INSTANCE.option(server, worldPosition, player, id);
        title = option.map(RitualManager.RitualOption::title).orElse("");
        ready = option.map(RitualManager.RitualOption::ready).orElse(false);
        power = option.map(RitualManager.RitualOption::altarPower).orElse(0);
        requiredPower = option.map(RitualManager.RitualOption::power).orElse(0);
        setChanged();
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public String selected() { return selected; }
    public String title() { return title; }
    public boolean ready() { return ready; }
    public int elapsed() { return elapsed; }
    public int total() { return total; }
    public int power() { return power; }
    public int requiredPower() { return requiredPower; }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        selected = input.getStringOr("SelectedRitual", "");
        selectingPlayer = input.getStringOr("SelectingPlayer", "");
        title = input.getStringOr("RitualTitle", "");
        ready = input.getBooleanOr("Ready", false);
        elapsed = input.getIntOr("Elapsed", 0);
        total = input.getIntOr("Total", 0);
        power = input.getIntOr("Power", 0);
        requiredPower = input.getIntOr("RequiredPower", 0);
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.putString("SelectedRitual", selected);
        output.putString("SelectingPlayer", selectingPlayer);
        output.putString("RitualTitle", title);
        output.putBoolean("Ready", ready);
        output.putInt("Elapsed", elapsed);
        output.putInt("Total", total);
        output.putInt("Power", power);
        output.putInt("RequiredPower", requiredPower);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) { return saveCustomOnly(registries); }
}
