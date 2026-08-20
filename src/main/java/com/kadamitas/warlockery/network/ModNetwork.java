package com.kadamitas.warlockery.network;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.item.FlyingBroomItem;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualRequirementText;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ModNetwork {
    private static final int MAX_RITUALS = 128;
    private static final int MAX_REQUIREMENTS = 32;
    private static final int MAX_STRING = 256;
    private static final String PROTOCOL_PATH = "network/v6/";
    private static boolean initialized;

    private ModNetwork() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(
            OpenRitualScreenPayload.TYPE,
            OpenRitualScreenPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            DollActivationPayload.TYPE,
            DollActivationPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            SupernaturalSnapshotPayload.TYPE,
            SupernaturalSnapshotPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            PlayerWolfVisualPayload.TYPE,
            PlayerWolfVisualPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(RitualActionPayload.TYPE, RitualActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            SupernaturalActionPayload.TYPE,
            SupernaturalActionPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(BroomControlPayload.TYPE, BroomControlPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
            RitualActionPayload.TYPE,
            (payload, context) -> handleRitualAction(payload, context.player())
        );
        ServerPlayNetworking.registerGlobalReceiver(
            SupernaturalActionPayload.TYPE,
            (payload, context) -> handleSupernaturalAction(payload, context.player())
        );
        ServerPlayNetworking.registerGlobalReceiver(
            BroomControlPayload.TYPE,
            (payload, context) -> handleBroomControl(payload, context.player())
        );
        initialized = true;
    }

    public static void openRitualScreen(final ServerPlayer player, final BlockPos center) {
        sendOptions(player, center);
    }

    public static void notifyDollActivation(
        final ServerPlayer player,
        final String dollKind,
        final int displayTicks
    ) {
        if (player.connection == null) {
            return;
        }
        send(player, new DollActivationPayload(dollKind, Math.clamp(displayTicks, 1, 20 * 10)));
    }

    public static void sendSupernaturalSnapshot(
        final ServerPlayer player,
        final SupernaturalSnapshot snapshot
    ) {
        if (player.connection == null) {
            return;
        }
        send(player, new SupernaturalSnapshotPayload(snapshot));
    }

    public static void broadcastPlayerWolfVisual(final ServerPlayer player, final boolean wolf) {
        final PlayerWolfVisualPayload payload = new PlayerWolfVisualPayload(player.getUUID(), wolf);
        send(player, payload);
        PlayerLookup.tracking(player).stream()
            .filter(recipient -> recipient != player)
            .forEach(recipient -> send(recipient, payload));
    }

    public static void sendPlayerWolfVisual(
        final ServerPlayer recipient,
        final ServerPlayer subject,
        final boolean wolf
    ) {
        send(recipient, new PlayerWolfVisualPayload(subject.getUUID(), wolf));
    }

    public static void clearPlayerWolfVisual(final ServerPlayer player) {
        final PlayerWolfVisualPayload payload = new PlayerWolfVisualPayload(player.getUUID(), false);
        PlayerLookup.tracking(player).forEach(recipient -> send(recipient, payload));
    }

    private static void sendOptions(final ServerPlayer player, final BlockPos center) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        send(player, new OpenRitualScreenPayload(center, RitualManager.INSTANCE.options(level, center, player)));
    }

    private static void handleSupernaturalAction(
        final SupernaturalActionPayload payload,
        final ServerPlayer player
    ) {
        switch (payload.action()) {
            case CYCLE -> SupernaturalProgressionRuntime.cyclePower(player);
            case ACTIVATE -> SupernaturalProgressionRuntime.activateSelectedPower(player);
        }
    }

    private static void handleBroomControl(
        final BroomControlPayload payload,
        final ServerPlayer player
    ) {
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

    private static void handleRitualAction(
        final RitualActionPayload payload,
        final ServerPlayer player
    ) {
        if (!(player.level() instanceof ServerLevel level)
            || player.distanceToSqr(Vec3.atCenterOf(payload.center())) > 64.0
            || !level.isLoaded(payload.center())
            || !RitualManager.isCircleCenter(level, payload.center())) {
            return;
        }
        if (payload.cancel()
            && RitualSessionData.get(level).cancel(level, payload.center(), player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.warlockery.ritual.stopped"));
        }
        if (payload.activate()) {
            final Identifier ritualId = Identifier.tryParse(payload.ritualId());
            // Activation already knows which requirements refused it. Naming them here is what the player was
            // previously sent to read off the screen for themselves.
            final List<RitualManager.RequirementStatus> unmet = ritualId == null
                ? List.of()
                : RitualManager.INSTANCE.activate(level, payload.center(), player, ritualId);
            if (ritualId == null || !unmet.isEmpty()) {
                player.sendSystemMessage(RitualRequirementText.notice(
                    unmet,
                    "message.warlockery.ritual.failed_requirements",
                    "message.warlockery.ritual.failed_detailed"
                ));
            }
        }
        sendOptions(player, payload.center());
    }

    private static void send(final ServerPlayer player, final CustomPacketPayload payload) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(final String path) {
        return new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, PROTOCOL_PATH + path)
        );
    }

    public record OpenRitualScreenPayload(BlockPos center, List<RitualManager.RitualOption> options)
        implements CustomPacketPayload {
        public static final Type<OpenRitualScreenPayload> TYPE = payloadType("open_ritual_screen");
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

        @Override
        public Type<OpenRitualScreenPayload> type() {
            return TYPE;
        }
    }

    public record RitualActionPayload(BlockPos center, String ritualId, boolean activate, boolean cancel)
        implements CustomPacketPayload {
        public static final Type<RitualActionPayload> TYPE = payloadType("ritual_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, RitualActionPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> {
                    output.writeBlockPos(value.center());
                    output.writeUtf(value.ritualId(), MAX_STRING);
                    output.writeBoolean(value.activate());
                    output.writeBoolean(value.cancel());
                },
                input -> new RitualActionPayload(
                    input.readBlockPos(),
                    input.readUtf(MAX_STRING),
                    input.readBoolean(),
                    input.readBoolean()
                )
            );

        @Override
        public Type<RitualActionPayload> type() {
            return TYPE;
        }
    }

    public record DollActivationPayload(String dollKind, int displayTicks) implements CustomPacketPayload {
        public static final Type<DollActivationPayload> TYPE = payloadType("doll_activation");
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
        public Type<DollActivationPayload> type() {
            return TYPE;
        }
    }

    public enum SupernaturalAction {
        CYCLE,
        ACTIVATE
    }

    public record SupernaturalActionPayload(SupernaturalAction action) implements CustomPacketPayload {
        public static final Type<SupernaturalActionPayload> TYPE = payloadType("supernatural_action");
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

        @Override
        public Type<SupernaturalActionPayload> type() {
            return TYPE;
        }
    }

    public record BroomControlPayload(byte strafe, byte forward, boolean ascend, boolean gliding)
        implements CustomPacketPayload {
        public static final Type<BroomControlPayload> TYPE = payloadType("broom_control");
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

        @Override
        public Type<BroomControlPayload> type() {
            return TYPE;
        }
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

    public record SupernaturalSnapshotPayload(SupernaturalSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<SupernaturalSnapshotPayload> TYPE = payloadType("supernatural_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, SupernaturalSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> writeSnapshot(output, value.snapshot()),
                input -> new SupernaturalSnapshotPayload(readSnapshot(input))
            );

        @Override
        public Type<SupernaturalSnapshotPayload> type() {
            return TYPE;
        }
    }

    public record PlayerWolfVisualPayload(UUID playerId, boolean wolf) implements CustomPacketPayload {
        public static final Type<PlayerWolfVisualPayload> TYPE = payloadType("player_wolf_visual");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerWolfVisualPayload> STREAM_CODEC =
            StreamCodec.of(
                (output, value) -> {
                    output.writeUUID(value.playerId());
                    output.writeBoolean(value.wolf());
                },
                input -> new PlayerWolfVisualPayload(input.readUUID(), input.readBoolean())
            );

        public PlayerWolfVisualPayload {
            Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public Type<PlayerWolfVisualPayload> type() {
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
