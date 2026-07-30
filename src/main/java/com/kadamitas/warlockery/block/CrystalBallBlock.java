package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.DivinationRules;
import com.kadamitas.warlockery.item.DivinationRuntime;
import com.kadamitas.warlockery.item.UtilityDecision;
import com.kadamitas.warlockery.item.WaystoneState;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CrystalBallBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0),
        Block.box(5.0, 3.0, 5.0, 11.0, 6.0, 11.0),
        Block.box(3.0, 6.0, 3.0, 13.0, 14.0, 13.0)
    );

    public CrystalBallBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(
        final BlockState state,
        final BlockGetter level,
        final BlockPos pos,
        final CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack stack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hit
    ) {
        if (player.isSecondaryUseActive() && stack.is(WarlockeryTags.Items.BABA_YAGA_SUMMONERS)) {
            final long time = Math.floorMod(level.getOverworldClockTime(), 24_000L);
            final boolean night = time >= 13_000L && time <= 23_000L;
            final boolean present = level.getEntities(
                (net.minecraft.world.entity.Entity) null,
                new AABB(pos).inflate(32.0),
                entity -> entity.getType() == ModEntities.ALL.get("hedge_crone").get()
            ).stream().findAny().isPresent();
            final UtilityDecision decision = DivinationRules.babaYagaEncounter(true, night, present);
            if (decision.success() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                ModEntities.ALL.get("hedge_crone").get().spawn(
                    serverLevel, pos.relative(player.getDirection(), 4).above(), EntitySpawnReason.EVENT
                );
                if (!player.hasInfiniteMaterials()) {
                    stack.shrink(1);
                }
            }
            show(player, decision);
            return decision.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        final var location = WaystoneState.read(stack);
        if (location.isPresent()) {
            if (!level.isClientSide()) {
                final WaystoneState.Location target = location.orElseThrow();
                player.sendSystemMessage(Component.translatable(
                    "message.warlockery.divination.remote_view",
                    target.dimension().toString(),
                    target.position().getX(),
                    target.position().getY(),
                    target.position().getZ()
                ).withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        if (!stack.is(WarlockeryTags.Items.DIVINATION_CATALYSTS)) {
            show(player, DivinationRules.crystalBall(false, false));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            final var prediction = DivinationRuntime.predict(level, pos);
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1_200, 0));
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.sendSystemMessage(Component.translatable(
                "message.warlockery.divination.prediction." + prediction.name().toLowerCase(java.util.Locale.ROOT)
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hit
    ) {
        show(player, DivinationRules.crystalBall(false, false));
        return InteractionResult.SUCCESS;
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("divination"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
