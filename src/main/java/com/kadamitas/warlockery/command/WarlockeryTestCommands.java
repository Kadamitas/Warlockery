package com.kadamitas.warlockery.command;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.RitualSessionData;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class WarlockeryTestCommands {
    private WarlockeryTestCommands() {}

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final var events = Commands.literal("event");
        for (AssaultKind kind : AssaultKind.values()) {
            events.then(Commands.literal(kind.serializedName()).executes(context -> event(context.getSource(), kind)));
        }
        dispatcher.register(Commands.literal("warlockery")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("test").executes(context -> help(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("circle")
                    .then(Commands.argument("ritual", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            RitualManager.INSTANCE.ids().stream().map(Identifier::toString), builder))
                        .executes(context -> circle(context.getSource(), IdentifierArgument.getId(context, "ritual")))))
                .then(Commands.literal("altar_power").executes(context -> power(context.getSource(), Integer.MAX_VALUE))
                    .then(Commands.literal("full").executes(context -> power(context.getSource(), Integer.MAX_VALUE)))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                        .executes(context -> power(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("infusion")
                    .then(Commands.argument("path", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            Arrays.stream(MagicPath.values()).map(MagicPath::id), builder))
                        .executes(context -> infusion(context.getSource(), StringArgumentType.getString(context, "path")))))
                .then(events)));
    }

    private static int help(final CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Warlockery cheats (gamemaster permission):\n"
            + "/warlockery test circle <warlockery:ritual> draws the book's chalk rings at your feet on existing solid ground.\n"
            + "/warlockery test altar_power [full|1..1000000] adds power to an existing valid reachable altar, capped at capacity.\n"
            + "/warlockery test infusion <path> grants a permanent infusion with full reserve.\n"
            + "/warlockery test event <goblin|vampire|werewolf> starts an existing settlement assault; vampires need night, werewolves need a full-moon night.\n"
            + "Run as a player. Circles do not supply offerings, altar structures, or completed ritual results."), false);
        return 1;
    }

    static Map<BlockPos, String> circlePlan(final String ritual, final BlockPos center, final Map<String, Integer> declared) {
        final Map<String, Integer> glyphs = ritual.equals("glyph_to_ritual") && !declared.isEmpty()
            ? Map.of("circleglyph_veil", declared.values().iterator().next()) : declared;
        final Map<BlockPos, String> plan = new LinkedHashMap<>();
        plan.put(center.immutable(), "circle");
        for (final var ring : ChalkCircleLayout.rings(glyphs)) {
            ring.size().offsets().forEach(offset -> plan.put(center.offset(offset), ring.glyph()));
        }
        return java.util.Collections.unmodifiableMap(plan);
    }

    private static int circle(final CommandSourceStack source, final Identifier supplied) throws CommandSyntaxException {
        final var player = source.getPlayerOrException();
        final ServerLevel level = player.level();
        final Identifier id = supplied.getNamespace().equals("minecraft")
            ? Identifier.fromNamespaceAndPath("warlockery", supplied.getPath()) : supplied;
        final var entry = RitualManager.INSTANCE.byId(id).orElseThrow(() -> error("Unknown ritual: " + id));
        final BlockPos center = player.blockPosition();
        final Map<BlockPos, String> plan;
        try {
            plan = circlePlan(id.getPath(), center, entry.definition().glyphs());
        } catch (IllegalArgumentException invalid) {
            throw error("Cannot draw this ritual: " + invalid.getMessage());
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -1, -8), center.offset(8, 0, 8))) {
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                throw error("Circle needs loaded ground inside the world border at " + pos.toShortString() + ". Nothing changed.");
            }
            if (RitualSessionData.get(level).isActive(pos)) {
                throw error("A ritual is already casting in this area. Nothing changed.");
            }
        }
        final Map<BlockPos, BlockState> placements = new LinkedHashMap<>();
        for (var mark : plan.entrySet()) {
            final var registered = ModBlocks.ALL.get(mark.getValue());
            if (registered == null) throw error("Unknown chalk: " + mark.getValue() + ". Nothing changed.");
            final BlockPos pos = mark.getKey();
            final BlockState existing = level.getBlockState(pos);
            final BlockState state = registered.get().defaultBlockState();
            if (!state.is(WarlockeryTags.Blocks.CHALK_GLYPHS)) throw error("Not a chalk block: " + mark.getValue() + ". Nothing changed.");
            if (level.getBlockEntity(pos) != null || !(existing.isAir() || existing.is(WarlockeryTags.Blocks.CHALK_GLYPHS))
                || !state.canSurvive(level, pos)) {
                throw error("Obstructed or unsupported chalk at " + pos.toShortString() + ". Only air or chalk on solid ground can be replaced. Nothing changed.");
            }
            placements.put(pos, state);
        }
        placements.forEach(level::setBlockAndUpdate);
        source.sendSuccess(() -> Component.literal("Drew " + id + ": " + placements.size()
            + " chalk marks including the golden heart at " + center.toShortString()
            + ". Add the required offerings and use an Arcane Focus to cast."), true);
        return placements.size();
    }

    private static int power(final CommandSourceStack source, final int amount) throws CommandSyntaxException {
        final var player = source.getPlayerOrException();
        final AltarBlockEntity altar = AltarPowerNetwork.best(player.level(), player.blockPosition())
            .orElseThrow(() -> error("No valid loaded altar reaches this position. Build a complete altar nearby and let it update first."));
        final int received = altar.receivePower(amount);
        source.sendSuccess(() -> Component.literal("Altar at " + altar.getBlockPos().toShortString() + " received " + received
            + " power; stored " + altar.getPower() + "/" + altar.getCapacity() + ", available " + altar.availablePower() + "."), true);
        return received;
    }

    private static int infusion(final CommandSourceStack source, final String name) throws CommandSyntaxException {
        final var player = source.getPlayerOrException();
        final MagicPath path = MagicPath.find(name).orElseThrow(() -> error("Unknown infusion: " + name));
        MagicPathState.grantPermanent(player, path);
        MagicPathState.recharge(player, path, path.maximumReserve());
        source.sendSuccess(() -> Component.literal("Granted permanent " + path.id() + " infusion with " + path.maximumReserve() + " reserve."), true);
        return 1;
    }

    private static int event(final CommandSourceStack source, final AssaultKind kind) throws CommandSyntaxException {
        final var player = source.getPlayerOrException();
        final var failure = VillageAssaultRuntime.requestAssault(player, kind);
        if (failure.isPresent()) throw error(failure.orElseThrow());
        source.sendSuccess(() -> Component.literal("Started " + kind.serializedName()
            + " settlement assault. The normal assault lifecycle will dispatch its waves on the next event tick."), true);
        return 1;
    }

    private static CommandSyntaxException error(final String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
