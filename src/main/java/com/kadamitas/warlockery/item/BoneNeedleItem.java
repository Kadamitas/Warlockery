package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.FetishRuntime;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BoneNeedleItem extends Item {
    public BoneNeedleItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!(player instanceof ServerPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        final ItemStack dollStack = player.getOffhandItem();
        final boolean hexDoll = dollStack.getItem() instanceof DollItem doll && doll.kind() == DollKind.HEXING;
        final Optional<SympatheticBinding> binding = hexDoll ? SympatheticBinding.read(dollStack) : Optional.empty();
        final Optional<LivingEntity> target = binding.flatMap(value -> value.resolve(serverLevel.getServer()))
            .filter(value -> value != player);
        final BoneNeedleRules.Diagnostic diagnostic = BoneNeedleRules.diagnostic(
            hexDoll,
            binding.isPresent(),
            target.isPresent(),
            target.filter(FetishRuntime::protects).isPresent()
        );
        if (diagnostic != BoneNeedleRules.Diagnostic.READY) {
            player.sendOverlayMessage(Component.literal("Missing requirement: " + diagnostic.name().toLowerCase()));
            return InteractionResult.FAIL;
        }
        final LivingEntity victim = target.orElseThrow();
        if (!(victim.level() instanceof ServerLevel victimLevel)
            || !victim.hurtServer(victimLevel, victim.damageSources().magic(), 1.0F)) {
            player.sendOverlayMessage(Component.literal("The bound target resisted the needle"));
            return InteractionResult.FAIL;
        }
        player.getItemInHand(hand).consume(1, player);
        player.sendOverlayMessage(Component.literal("\u2713 The bound target was pricked"));
        return InteractionResult.SUCCESS;
    }
}
