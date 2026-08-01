package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class WaystoneItem extends Item implements DroppedItemBehavior {
    private final Kind kind;

    public WaystoneItem(final Properties properties, final Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public boolean tickDroppedItem(final ItemStack stack, final ItemEntity entity) {
        VeilWaystoneRuntime.tick(entity, kind);
        return false;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        if (kind == Kind.POSITION && !context.getPlayer().isSecondaryUseActive()) {
            return teleport(context.getItemInHand(), context.getPlayer(), level);
        }
        if (kind == Kind.CREATURE) {
            show(context.getPlayer(), UtilityDecision.failure("requires_creature"));
            return InteractionResult.FAIL;
        }
        final ItemStack bound = kind == Kind.BASE
            ? context.getItemInHand().transmuteCopy(ModItems.ALL.get("ingredient_waystone_bound").get(), 1)
            : context.getItemInHand();
        WaystoneState.write(bound, level.dimension().identifier(), context.getClickedPos().above());
        bound.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.translatable(
            "tooltip.warlockery.waystone.position",
            level.dimension().identifier().toString(),
            context.getClickedPos().getX(),
            context.getClickedPos().getY() + 1,
            context.getClickedPos().getZ()
        ))));
        context.getPlayer().setItemInHand(context.getHand(), bound);
        show(context.getPlayer(), UtilityDecision.success("bound"));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (kind != Kind.BASE) {
            show(player, UtilityDecision.failure("already_bound"));
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
            final ItemStack bound = stack.transmuteCopy(ModItems.ALL.get("ingredient_waystone_creature_bound").get(), 1);
            final SympatheticBinding binding = SympatheticBinding.from(target);
            binding.write(bound);
            bound.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.translatable(
                "tooltip.warlockery.waystone.creature",
                binding.targetName()
            ))));
            player.setItemInHand(hand, bound);
            show(player, UtilityDecision.success("creature_bound"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        final ItemStack stack = player.getItemInHand(hand);
        return switch (kind) {
            case BASE -> {
                show(player, UtilityDecision.failure("requires_position_or_creature"));
                yield InteractionResult.FAIL;
            }
            case POSITION -> teleport(stack, player, serverLevel);
            case CREATURE -> inspectCreature(stack, player, serverLevel);
        };
    }

    private static InteractionResult teleport(
        final ItemStack stack,
        final Player player,
        final ServerLevel currentLevel
    ) {
        final var location = WaystoneState.read(stack);
        if (location.isEmpty()) {
            show(player, UtilityDecision.failure("missing_destination"));
            return InteractionResult.FAIL;
        }
        final WaystoneState.Location destination = location.orElseThrow();
        final boolean moved;
        if (destination.dimension().equals(currentLevel.dimension().identifier())) {
            player.teleportTo(
                destination.position().getX() + 0.5,
                destination.position().getY(),
                destination.position().getZ() + 0.5
            );
            moved = true;
        } else {
            moved = player instanceof ServerPlayer serverPlayer
                && MagicPathRuntime.hasOtherwhere(player)
                && MagicPathRuntime.teleportToBoundPosition(
                    serverPlayer,
                    destination.dimension(),
                    destination.position()
                );
        }
        show(player, moved
            ? UtilityDecision.success("travelled")
            : UtilityDecision.failure("otherwhere_required"));
        return moved ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static InteractionResult inspectCreature(
        final ItemStack stack,
        final Player player,
        final ServerLevel level
    ) {
        final var binding = SympatheticBinding.read(stack);
        if (binding.isEmpty()) {
            show(player, UtilityDecision.failure("missing_creature"));
            return InteractionResult.FAIL;
        }
        final SympatheticBinding value = binding.orElseThrow();
        final boolean present = value.resolve(level.getServer()).isPresent();
        player.sendOverlayMessage(Component.translatable(
            present ? "message.warlockery.waystone.creature_found" : "message.warlockery.waystone.creature_missing",
            value.targetName()
        ).withStyle(present ? ChatFormatting.GREEN : ChatFormatting.RED));
        return present ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static void show(final Player player, final UtilityDecision decision) {
        player.sendOverlayMessage(Component.translatable(decision.messageKey("waystone"))
            .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public enum Kind {
        BASE,
        POSITION,
        CREATURE
    }
}
