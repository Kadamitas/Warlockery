package com.kadamitas.warlockery.network;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final int MAX_RITUALS = 128;
    private static final int MAX_REQUIREMENTS = 32;
    private static final int MAX_STRING = 256;
    private static Consumer<OpenRitualScreenPayload> clientScreenHandler = payload -> {
    };
    private static Consumer<DollActivationPayload> clientDollHandler = payload -> {
    };

    private ModNetwork() {
    }

    public static void init(final IEventBus modBus) {
        modBus.addListener(ModNetwork::registerPayloads);
    }

    public static void registerClientPayloadHandlers(final RegisterClientPayloadHandlersEvent event) {
        event.register(OpenRitualScreenPayload.TYPE, ModNetwork::handleOpenScreen);
        event.register(DollActivationPayload.TYPE, ModNetwork::handleDollActivation);
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(OpenRitualScreenPayload.TYPE, OpenRitualScreenPayload.STREAM_CODEC);
        registrar.playToClient(DollActivationPayload.TYPE, DollActivationPayload.STREAM_CODEC);
        registrar.playToServer(RitualActionPayload.TYPE, RitualActionPayload.STREAM_CODEC, ModNetwork::handleRitualAction);
    }

    public static void openRitualScreen(final ServerPlayer player, final BlockPos center) {
        sendOptions(player, center);
    }

    public static void setClientScreenHandler(final Consumer<OpenRitualScreenPayload> handler) {
        clientScreenHandler = handler;
    }

    public static void setClientDollHandler(final Consumer<DollActivationPayload> handler) {
        clientDollHandler = handler;
    }

    public static void notifyDollActivation(
        final ServerPlayer player,
        final String dollKind,
        final int displayTicks
    ) {
        if (player.connection == null || !player.connection.hasChannel(DollActivationPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(
            player,
            new DollActivationPayload(dollKind, Math.clamp(displayTicks, 1, 20 * 10))
        );
    }

    public static void requestRefresh(final BlockPos center) {
        ClientPacketDistributor.sendToServer(new RitualActionPayload(center, "", false));
    }

    public static void requestActivation(final BlockPos center, final String ritualId) {
        ClientPacketDistributor.sendToServer(new RitualActionPayload(center, ritualId, true));
    }

    private static void sendOptions(final ServerPlayer player, final BlockPos center) {
        if (!(player.level() instanceof ServerLevel level)
            || player.connection == null
            || !player.connection.hasChannel(OpenRitualScreenPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(
            player,
            new OpenRitualScreenPayload(center, RitualManager.INSTANCE.options(level, center, player))
        );
    }

    private static void handleOpenScreen(
        final OpenRitualScreenPayload payload,
        final IPayloadContext context
    ) {
        clientScreenHandler.accept(payload);
    }

    private static void handleDollActivation(
        final DollActivationPayload payload,
        final IPayloadContext context
    ) {
        clientDollHandler.accept(payload);
    }

    private static void handleRitualAction(
        final RitualActionPayload payload,
        final IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)
            || !(player.level() instanceof ServerLevel level)
            || player.distanceToSqr(Vec3.atCenterOf(payload.center())) > 64.0
            || !level.isLoaded(payload.center())
            || !RitualManager.isCircleCenter(level, payload.center())) {
            return;
        }
        if (payload.activate()) {
            final Identifier ritualId = Identifier.tryParse(payload.ritualId());
            if (ritualId == null || !RitualManager.INSTANCE.activate(level, payload.center(), player, ritualId)) {
                player.sendSystemMessage(Component.translatable("message.warlockery.ritual.failed_detailed"));
            }
        }
        sendOptions(player, payload.center());
    }

    public record OpenRitualScreenPayload(
        BlockPos center,
        List<RitualManager.RitualOption> options
    ) implements CustomPacketPayload {
        public static final Type<OpenRitualScreenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "open_ritual_screen")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenRitualScreenPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenRitualScreenPayload decode(final RegistryFriendlyByteBuf input) {
                    final BlockPos center = input.readBlockPos();
                    final int count = readBoundedSize(input, MAX_RITUALS, "ritual options");
                    final List<RitualManager.RitualOption> options = IntStream.range(0, count)
                        .mapToObj(_ -> readOption(input))
                        .toList();
                    return new OpenRitualScreenPayload(center, options);
                }

                @Override
                public void encode(final RegistryFriendlyByteBuf output, final OpenRitualScreenPayload value) {
                    output.writeBlockPos(value.center());
                    final List<RitualManager.RitualOption> options = value.options().stream().limit(MAX_RITUALS).toList();
                    output.writeVarInt(options.size());
                    options.forEach(option -> writeOption(output, option));
                }
            };

        public OpenRitualScreenPayload {
            options = List.copyOf(options);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RitualActionPayload(
        BlockPos center,
        String ritualId,
        boolean activate
    ) implements CustomPacketPayload {
        public static final Type<RitualActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "ritual_action")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, RitualActionPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> {
                    output.writeBlockPos(value.center());
                    output.writeUtf(value.ritualId(), MAX_STRING);
                    output.writeBoolean(value.activate());
                },
                input -> new RitualActionPayload(input.readBlockPos(), input.readUtf(MAX_STRING), input.readBoolean())
            );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DollActivationPayload(String dollKind, int displayTicks) implements CustomPacketPayload {
        public static final Type<DollActivationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "doll_activation")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, DollActivationPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> {
                    output.writeUtf(value.dollKind(), MAX_STRING);
                    output.writeVarInt(value.displayTicks());
                },
                input -> new DollActivationPayload(
                    input.readUtf(MAX_STRING),
                    Math.clamp(input.readVarInt(), 1, 20 * 10)
                )
            );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static RitualManager.RitualOption readOption(final RegistryFriendlyByteBuf input) {
        final String id = input.readUtf(MAX_STRING);
        final String title = input.readUtf(MAX_STRING);
        final String description = input.readUtf(MAX_STRING);
        final int power = input.readVarInt();
        final int altarPower = input.readVarInt();
        final int castingTime = input.readVarInt();
        final int size = readBoundedSize(input, MAX_REQUIREMENTS, "ritual requirements");
        final List<RitualManager.RequirementStatus> requirements = IntStream.range(0, size)
            .mapToObj(_ -> new RitualManager.RequirementStatus(
                input.readUtf(MAX_STRING),
                input.readUtf(MAX_STRING),
                input.readVarInt(),
                input.readVarInt(),
                input.readBoolean()
            ))
            .toList();
        return new RitualManager.RitualOption(
            id, title, description, power, altarPower, castingTime, requirements, input.readBoolean()
        );
    }

    private static void writeOption(final RegistryFriendlyByteBuf output, final RitualManager.RitualOption option) {
        output.writeUtf(option.id(), MAX_STRING);
        output.writeUtf(option.title(), MAX_STRING);
        output.writeUtf(option.description(), MAX_STRING);
        output.writeVarInt(option.power());
        output.writeVarInt(option.altarPower());
        output.writeVarInt(option.castingTime());
        final List<RitualManager.RequirementStatus> requirements = option.requirements().stream()
            .limit(MAX_REQUIREMENTS)
            .toList();
        output.writeVarInt(requirements.size());
        requirements.forEach(requirement -> {
            output.writeUtf(requirement.category(), MAX_STRING);
            output.writeUtf(requirement.label(), MAX_STRING);
            output.writeVarInt(requirement.required());
            output.writeVarInt(requirement.present());
            output.writeBoolean(requirement.met());
        });
        output.writeBoolean(option.ready());
    }

    private static int readBoundedSize(
        final RegistryFriendlyByteBuf input,
        final int maximum,
        final String field
    ) {
        final int size = input.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + size);
        }
        return size;
    }
}
