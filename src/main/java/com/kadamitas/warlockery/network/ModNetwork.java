package com.kadamitas.warlockery.network;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.item.FlyingBroomItem;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class ModNetwork {
    private static final int MAX_RITUALS = 128;
    private static final int MAX_REQUIREMENTS = 32;
    private static final int MAX_STRING = 256;
    private static Consumer<OpenRitualScreenPayload> clientScreenHandler = payload -> {
    };
    private static Consumer<DollActivationPayload> clientDollHandler = payload -> {
    };
    private static Consumer<SupernaturalSnapshotPayload> clientSupernaturalHandler = payload -> {
    };

    private static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "main"))
        .networkProtocolVersion(4)
        .simpleChannel()
        .play()
        .clientbound()
        .addMain(OpenRitualScreenPayload.class, OpenRitualScreenPayload.STREAM_CODEC, ModNetwork::handleOpenScreen)
        .addMain(DollActivationPayload.class, DollActivationPayload.STREAM_CODEC, ModNetwork::handleDollActivation)
        .addMain(
            SupernaturalSnapshotPayload.class,
            SupernaturalSnapshotPayload.STREAM_CODEC,
            ModNetwork::handleSupernaturalSnapshot
        )
        .serverbound()
        .addMain(RitualActionPayload.class, RitualActionPayload.STREAM_CODEC, ModNetwork::handleRitualAction)
        .addMain(
            SupernaturalActionPayload.class,
            SupernaturalActionPayload.STREAM_CODEC,
            ModNetwork::handleSupernaturalAction
        )
        .addMain(BroomControlPayload.class, BroomControlPayload.STREAM_CODEC, ModNetwork::handleBroomControl)
        .build();

    private ModNetwork() {
    }

    public static void init() {
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

    public static void setClientSupernaturalHandler(final Consumer<SupernaturalSnapshotPayload> handler) {
        clientSupernaturalHandler = handler;
    }

    public static void notifyDollActivation(
        final ServerPlayer player,
        final String dollKind,
        final int displayTicks
    ) {
        if (player.connection == null) {
            return;
        }
        CHANNEL.send(
            new DollActivationPayload(dollKind, Math.clamp(displayTicks, 1, 20 * 10)),
            PacketDistributor.PLAYER.with(player)
        );
    }

    public static void sendSupernaturalSnapshot(
        final ServerPlayer player,
        final SupernaturalSnapshot snapshot
    ) {
        if (player.connection == null) {
            return;
        }
        CHANNEL.send(
            new SupernaturalSnapshotPayload(snapshot),
            PacketDistributor.PLAYER.with(player)
        );
    }

    public static void requestSupernaturalAction(final SupernaturalAction action) {
        CHANNEL.send(new SupernaturalActionPayload(action), PacketDistributor.SERVER.noArg());
    }

    public static void requestBroomControl(
        final int strafe,
        final int forward,
        final boolean ascend,
        final boolean gliding
    ) {
        CHANNEL.send(
            new BroomControlPayload((byte) Math.clamp(strafe, -1, 1), (byte) Math.clamp(forward, -1, 1), ascend, gliding),
            PacketDistributor.SERVER.noArg()
        );
    }

    public static void requestRefresh(final BlockPos center) {
        CHANNEL.send(new RitualActionPayload(center, "", false), PacketDistributor.SERVER.noArg());
    }

    public static void requestActivation(final BlockPos center, final String ritualId) {
        CHANNEL.send(new RitualActionPayload(center, ritualId, true), PacketDistributor.SERVER.noArg());
    }

    private static void sendOptions(final ServerPlayer player, final BlockPos center) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        CHANNEL.send(
            new OpenRitualScreenPayload(center, RitualManager.INSTANCE.options(level, center, player)),
            PacketDistributor.PLAYER.with(player)
        );
    }

    private static void handleOpenScreen(
        final OpenRitualScreenPayload payload,
        final CustomPayloadEvent.Context context
    ) {
        if (context.isClientSide()) {
            clientScreenHandler.accept(payload);
        }
    }

    private static void handleDollActivation(
        final DollActivationPayload payload,
        final CustomPayloadEvent.Context context
    ) {
        if (context.isClientSide()) {
            clientDollHandler.accept(payload);
        }
    }

    private static void handleSupernaturalSnapshot(
        final SupernaturalSnapshotPayload payload,
        final CustomPayloadEvent.Context context
    ) {
        if (context.isClientSide()) {
            clientSupernaturalHandler.accept(payload);
        }
    }

    private static void handleSupernaturalAction(
        final SupernaturalActionPayload payload,
        final CustomPayloadEvent.Context context
    ) {
        final ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        switch (payload.action()) {
            case CYCLE -> SupernaturalProgressionRuntime.cyclePower(player);
            case ACTIVATE -> SupernaturalProgressionRuntime.activateSelectedPower(player);
        }
    }

    private static void handleBroomControl(
        final BroomControlPayload payload,
        final CustomPayloadEvent.Context context
    ) {
        final ServerPlayer player = context.getSender();
        if (player != null) {
            FlyingBroomItem.setControls(
                player,
                new com.kadamitas.warlockery.item.FlyingBroomRules.ControlInput(
                    Math.clamp(payload.strafe(), -1, 1),
                    Math.clamp(payload.forward(), -1, 1),
                    payload.ascend()
                ),
                payload.gliding()
            );
        }
    }

    private static void handleRitualAction(
        final RitualActionPayload payload,
        final CustomPayloadEvent.Context context
    ) {
        final ServerPlayer player = context.getSender();
        if (player == null || !(player.level() instanceof ServerLevel level)
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

    public record OpenRitualScreenPayload(BlockPos center, List<RitualManager.RitualOption> options) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenRitualScreenPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenRitualScreenPayload decode(final RegistryFriendlyByteBuf input) {
                    final BlockPos center = input.readBlockPos();
                    final int count = Math.clamp(input.readVarInt(), 0, MAX_RITUALS);
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
    }

    public record RitualActionPayload(BlockPos center, String ritualId, boolean activate) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RitualActionPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> {
                    output.writeBlockPos(value.center());
                    output.writeUtf(value.ritualId(), MAX_STRING);
                    output.writeBoolean(value.activate());
                },
                input -> new RitualActionPayload(input.readBlockPos(), input.readUtf(MAX_STRING), input.readBoolean())
            );
    }

    public record DollActivationPayload(String dollKind, int displayTicks) {
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
    }

    public enum SupernaturalAction {
        CYCLE,
        ACTIVATE
    }

    public record SupernaturalActionPayload(SupernaturalAction action) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SupernaturalActionPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> output.writeByte(value.action().ordinal()),
                input -> new SupernaturalActionPayload(actionAt(input.readUnsignedByte()))
            );

        private static SupernaturalAction actionAt(final int ordinal) {
            return ordinal >= 0 && ordinal < SupernaturalAction.values().length
                ? SupernaturalAction.values()[ordinal]
                : SupernaturalAction.CYCLE;
        }
    }

    public record BroomControlPayload(byte strafe, byte forward, boolean ascend, boolean gliding) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BroomControlPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> {
                    output.writeByte(value.strafe());
                    output.writeByte(value.forward());
                    output.writeBoolean(value.ascend());
                    output.writeBoolean(value.gliding());
                },
                input -> new BroomControlPayload(
                    input.readByte(),
                    input.readByte(),
                    input.readBoolean(),
                    input.readBoolean()
                )
            );
    }

    public record SupernaturalSnapshot(
        String identity,
        int level,
        int resource,
        int maxResource,
        String selectedPower,
        String shape,
        String questTitle,
        String questProgress,
        int selectedPowerCharges,
        int powerCooldownTicks,
        String magicPath,
        int magicResource,
        int magicMaxResource
    ) {
        public SupernaturalSnapshot(
            final String identity,
            final int level,
            final int resource,
            final int maxResource,
            final String selectedPower,
            final String shape,
            final String questTitle,
            final String questProgress
        ) {
            this(identity, level, resource, maxResource, selectedPower, shape, questTitle, questProgress,
                -1, 0, "", 0, 0);
        }

        public SupernaturalSnapshot {
            identity = safe(identity);
            level = Math.clamp(level, 0, 10);
            maxResource = Math.max(0, maxResource);
            resource = Math.clamp(resource, 0, maxResource);
            selectedPower = safe(selectedPower);
            shape = safe(shape);
            questTitle = safe(questTitle);
            questProgress = safe(questProgress);
            selectedPowerCharges = Math.max(-1, selectedPowerCharges);
            powerCooldownTicks = Math.max(0, powerCooldownTicks);
            magicPath = safe(magicPath);
            magicMaxResource = Math.max(0, magicMaxResource);
            magicResource = Math.clamp(magicResource, 0, magicMaxResource);
        }

        public boolean active() {
            return !identity.isBlank()
                && !"none".equalsIgnoreCase(identity)
                && !identity.endsWith(".none");
        }

        public float resourceFraction() {
            return maxResource == 0 ? 0.0F : (float) resource / maxResource;
        }

        public boolean magicActive() {
            return !magicPath.isBlank() && magicMaxResource > 0;
        }

        public float magicResourceFraction() {
            return magicMaxResource == 0 ? 0.0F : (float) magicResource / magicMaxResource;
        }

        private static String safe(final String value) {
            return value == null ? "" : value;
        }
    }

    public record SupernaturalSnapshotPayload(SupernaturalSnapshot snapshot) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SupernaturalSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> writeSnapshot(output, value.snapshot()),
                input -> new SupernaturalSnapshotPayload(readSnapshot(input))
            );
    }

    private static RitualManager.RitualOption readOption(final RegistryFriendlyByteBuf input) {
        final String id = input.readUtf(MAX_STRING);
        final String title = input.readUtf(MAX_STRING);
        final String description = input.readUtf(MAX_STRING);
        final int power = input.readVarInt();
        final int altarPower = input.readVarInt();
        final int castingTime = input.readVarInt();
        final int size = Math.clamp(input.readVarInt(), 0, MAX_REQUIREMENTS);
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

    private static SupernaturalSnapshot readSnapshot(final RegistryFriendlyByteBuf input) {
        return new SupernaturalSnapshot(
            input.readUtf(MAX_STRING),
            input.readVarInt(),
            input.readVarInt(),
            input.readVarInt(),
            input.readUtf(MAX_STRING),
            input.readUtf(MAX_STRING),
            input.readUtf(MAX_STRING),
            input.readUtf(MAX_STRING),
            input.readVarInt(),
            input.readVarInt(),
            input.readUtf(MAX_STRING),
            input.readVarInt(),
            input.readVarInt()
        );
    }

    private static void writeSnapshot(
        final RegistryFriendlyByteBuf output,
        final SupernaturalSnapshot snapshot
    ) {
        output.writeUtf(snapshot.identity(), MAX_STRING);
        output.writeVarInt(snapshot.level());
        output.writeVarInt(snapshot.resource());
        output.writeVarInt(snapshot.maxResource());
        output.writeUtf(snapshot.selectedPower(), MAX_STRING);
        output.writeUtf(snapshot.shape(), MAX_STRING);
        output.writeUtf(snapshot.questTitle(), MAX_STRING);
        output.writeUtf(snapshot.questProgress(), MAX_STRING);
        output.writeVarInt(snapshot.selectedPowerCharges());
        output.writeVarInt(snapshot.powerCooldownTicks());
        output.writeUtf(snapshot.magicPath(), MAX_STRING);
        output.writeVarInt(snapshot.magicResource());
        output.writeVarInt(snapshot.magicMaxResource());
    }
}
