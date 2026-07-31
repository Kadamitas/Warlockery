package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.registry.ModItems;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SupernaturalAdvancement {
    private static final SupernaturalProgression.Path WEREWOLF = SupernaturalProgression.Path.WEREWOLF;
    private static final SupernaturalProgression.Path VAMPIRE = SupernaturalProgression.Path.VAMPIRE;

    private SupernaturalAdvancement() {
    }

    public static boolean beginWerewolf(final Player player) {
        return SupernaturalProgression.beginPath(player, WEREWOLF);
    }

    public static boolean beginVampire(final Player player) {
        return SupernaturalProgression.beginPath(player, VAMPIRE);
    }

    public static WerewolfProgressionRules.Progress werewolfProgress(final Player player) {
        final EnumMap<WerewolfProgressionRules.Metric, Integer> counters = new EnumMap<>(
            WerewolfProgressionRules.Metric.class
        );
        for (final WerewolfProgressionRules.Metric metric : WerewolfProgressionRules.Metric.values()) {
            final int value = SupernaturalProgression.counter(player, WEREWOLF, metric);
            if (value > 0) {
                counters.put(metric, value);
            }
        }
        return new WerewolfProgressionRules.Progress(SupernaturalProgression.level(player, WEREWOLF), counters);
    }

    public static VampireProgressionRules.Progress vampireProgress(final Player player) {
        final EnumMap<VampireProgressionRules.Metric, Integer> counters = new EnumMap<>(
            VampireProgressionRules.Metric.class
        );
        final EnumMap<VampireProgressionRules.Metric, Set<String>> unique = new EnumMap<>(
            VampireProgressionRules.Metric.class
        );
        for (final VampireProgressionRules.Metric metric : VampireProgressionRules.Metric.values()) {
            final int value = SupernaturalProgression.counter(player, VAMPIRE, metric);
            if (value <= 0) {
                continue;
            }
            if (isUniqueVampireMetric(metric)) {
                final LinkedHashSet<String> identities = IntStream.range(0, value)
                    .mapToObj(index -> "stored-" + index)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                unique.put(metric, identities);
            } else {
                counters.put(metric, value);
            }
        }
        return new VampireProgressionRules.Progress(
            SupernaturalProgression.level(player, VAMPIRE),
            counters,
            unique
        );
    }

    public static ProgressUpdate recordVampire(
        final ServerPlayer player,
        final VampireProgressionRules.Metric metric,
        final int amount
    ) {
        final var result = VampireProgressionRules.observe(vampireProgress(player), metric, amount);
        if (result.changed()) {
            SupernaturalProgression.observe(player, VAMPIRE, metric, amount);
            showVampireProgress(player, result.evaluation());
            return advanceVampireIfReady(player);
        }
        return new ProgressUpdate(false, false, SupernaturalProgression.level(player, VAMPIRE),
            result.diagnostic().messageKey());
    }

    public static ProgressUpdate recordVampireValue(
        final ServerPlayer player,
        final VampireProgressionRules.Metric metric,
        final int value
    ) {
        final var result = VampireProgressionRules.observeValue(vampireProgress(player), metric, value);
        if (result.changed()) {
            SupernaturalProgression.setCounter(player, VAMPIRE, metric, value);
            showVampireProgress(player, result.evaluation());
            return advanceVampireIfReady(player);
        }
        return new ProgressUpdate(false, false, SupernaturalProgression.level(player, VAMPIRE),
            result.diagnostic().messageKey());
    }

    public static ProgressUpdate recordUniqueVampire(
        final ServerPlayer player,
        final VampireProgressionRules.Metric metric,
        final String identity
    ) {
        final long marker = ((long) identity.toLowerCase(java.util.Locale.ROOT).hashCode() << 32)
            ^ identity.length();
        final int before = SupernaturalProgression.counter(player, VAMPIRE, metric);
        final int after = SupernaturalProgression.observeUnique(player, VAMPIRE, metric, marker);
        if (after > before) {
            showVampireProgress(player, VampireProgressionRules.evaluate(vampireProgress(player)));
            return advanceVampireIfReady(player);
        }
        return new ProgressUpdate(false, false, SupernaturalProgression.level(player, VAMPIRE),
            VampireProgressionRules.Diagnostic.IDENTITY_ALREADY_RECORDED.messageKey());
    }

    public static ProgressUpdate advanceVampireIfReady(final ServerPlayer player) {
        final VampireProgressionRules.Transition transition = VampireProgressionRules.attemptAdvance(
            vampireProgress(player)
        );
        if (!transition.advanced()) {
            return new ProgressUpdate(false, false, transition.after().level(), transition.diagnostic().messageKey());
        }
        final int manual = SupernaturalProgression.counter(
            player,
            VAMPIRE,
            VampireProgressionRules.Metric.OBSERVATIONS_MANUAL_OWNED
        );
        final int pages = SupernaturalProgression.counter(
            player,
            VAMPIRE,
            VampireProgressionRules.Metric.TORN_PAGES_INSERTED
        );
        SupernaturalProgression.setLevel(player, VAMPIRE, transition.after().level());
        SupernaturalProgression.clearCounters(player, VAMPIRE);
        SupernaturalProgression.setCounter(
            player,
            VAMPIRE,
            VampireProgressionRules.Metric.OBSERVATIONS_MANUAL_OWNED,
            manual
        );
        SupernaturalProgression.setCounter(
            player,
            VAMPIRE,
            VampireProgressionRules.Metric.TORN_PAGES_INSERTED,
            pages
        );
        player.sendSystemMessage(Component.translatable(
            "message.warlockery.progression.level_up",
            Component.translatable("path.warlockery.vampire"),
            transition.after().level()
        ).withStyle(ChatFormatting.DARK_RED));
        return new ProgressUpdate(true, true, transition.after().level(), transition.diagnostic().messageKey());
    }

    public static ProgressUpdate recordWerewolf(
        final ServerPlayer player,
        final WerewolfProgressionRules.Metric metric,
        final int amount
    ) {
        final WerewolfProgressionRules.ObservationResult result = WerewolfProgressionRules.observe(
            werewolfProgress(player),
            metric,
            amount
        );
        if (result.changed()) {
            SupernaturalProgression.observe(player, WEREWOLF, metric, amount);
            showProgress(player, result.evaluation());
        }
        return new ProgressUpdate(
            result.changed(),
            false,
            SupernaturalProgression.level(player, WEREWOLF),
            result.diagnostic().messageKey()
        );
    }

    public static ProgressUpdate recordUniqueWerewolf(
        final ServerPlayer player,
        final WerewolfProgressionRules.Metric metric,
        final long marker
    ) {
        final int before = SupernaturalProgression.counter(player, WEREWOLF, metric);
        final int after = SupernaturalProgression.observeUnique(player, WEREWOLF, metric, marker);
        final boolean changed = after > before;
        if (changed) {
            showProgress(player, WerewolfProgressionRules.evaluate(werewolfProgress(player)));
        }
        return new ProgressUpdate(
            changed,
            false,
            SupernaturalProgression.level(player, WEREWOLF),
            WerewolfProgressionRules.evaluate(werewolfProgress(player)).diagnostic().messageKey()
        );
    }

    public static WolfAltarResult useWolfAltar(final ServerPlayer player, final ItemStack offering) {
        if (SupernaturalState.getForm(player) != SupernaturalForm.WEREWOLF) {
            return WolfAltarResult.failure("message.warlockery.werewolf_progression.curse_required");
        }
        final int level = SupernaturalProgression.level(player, WEREWOLF);
        if (level >= SupernaturalProgression.MAX_LEVEL) {
            return WolfAltarResult.failure("message.warlockery.werewolf_progression.path_complete");
        }
        if (level == 1) {
            return offerAndAdvance(player, offering, Items.GOLD_INGOT, 3,
                WerewolfProgressionRules.Metric.GOLD_INGOTS_OFFERED);
        }
        if (level == 2) {
            if (SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN) {
                return WolfAltarResult.failure("message.warlockery.werewolf_progression.wolf_form_required");
            }
            return offerAndAdvance(player, offering, Items.MUTTON, 30,
                WerewolfProgressionRules.Metric.RAW_MUTTON_OFFERED_FROM_WOLF_HUNTS);
        }
        if (level == 3) {
            return offerAndAdvance(
                player,
                offering,
                ModItems.ALL.get("ingredient_dog_tongue").get(),
                10,
                WerewolfProgressionRules.Metric.TONGUES_OF_DOG_OFFERED
            );
        }
        if (level == 4 && SupernaturalProgression.markFlag(player, WEREWOLF, "horn_received")) {
            grantOrDrop(player, new ItemStack(ModItems.ALL.get("hornofthehunt").get()));
            player.sendOverlayMessage(Component.translatable("message.warlockery.werewolf_progression.horn_received")
                .withStyle(ChatFormatting.GOLD));
            return new WolfAltarResult(true, false, 0, level,
                "message.warlockery.werewolf_progression.horn_received");
        }
        final ProgressUpdate update = advanceWerewolf(player);
        return new WolfAltarResult(update.changed(), update.advanced(), 0, update.level(), update.messageKey());
    }

    public static ProgressUpdate advanceWerewolf(final ServerPlayer player) {
        final WerewolfProgressionRules.Transition transition = WerewolfProgressionRules.attemptAdvance(
            werewolfProgress(player)
        );
        if (!transition.advanced()) {
            player.sendOverlayMessage(Component.translatable(transition.diagnostic().messageKey())
                .withStyle(ChatFormatting.RED));
            return new ProgressUpdate(false, false, transition.after().level(), transition.diagnostic().messageKey());
        }
        SupernaturalProgression.setLevel(player, WEREWOLF, transition.after().level());
        SupernaturalProgression.clearCounters(player, WEREWOLF);
        SupernaturalProgression.setResource(
            player,
            WEREWOLF,
            SupernaturalProgression.maximumResource(WEREWOLF, transition.after().level())
        );
        transition.completionRewards().forEach(reward -> grantWerewolfReward(player, reward));
        player.sendSystemMessage(Component.translatable(
            "message.warlockery.progression.level_up",
            Component.translatable("path.warlockery.werewolf"),
            transition.after().level()
        ).withStyle(ChatFormatting.GOLD));
        return new ProgressUpdate(true, true, transition.after().level(), transition.diagnostic().messageKey());
    }

    private static WolfAltarResult offerAndAdvance(
        final ServerPlayer player,
        final ItemStack offering,
        final net.minecraft.world.item.Item requiredItem,
        final int amount,
        final WerewolfProgressionRules.Metric metric
    ) {
        if (!offering.is(requiredItem) || offering.getCount() < amount) {
            return WolfAltarResult.failure(WerewolfProgressionRules.evaluate(werewolfProgress(player))
                .diagnostic().messageKey());
        }
        recordWerewolf(player, metric, amount);
        final ProgressUpdate update = advanceWerewolf(player);
        if (update.advanced() && !player.hasInfiniteMaterials()) {
            offering.shrink(amount);
        }
        return new WolfAltarResult(update.changed(), update.advanced(), update.advanced() ? amount : 0,
            update.level(), update.messageKey());
    }

    private static void grantWerewolfReward(
        final ServerPlayer player,
        final WerewolfProgressionRules.Reward reward
    ) {
        final String item = switch (reward) {
            case MOON_CHARM -> "mooncharm";
            case HORN_OF_THE_HUNT -> "hornofthehunt";
        };
        grantOrDrop(player, new ItemStack(ModItems.ALL.get(item).get()));
    }

    private static void grantOrDrop(final Player player, final ItemStack reward) {
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
    }

    private static void showProgress(
        final ServerPlayer player,
        final WerewolfProgressionRules.Evaluation evaluation
    ) {
        evaluation.quest().ifPresent(quest -> {
            final WerewolfProgressionRules.RequirementStatus focus = evaluation.requirements().stream()
                .filter(status -> !status.satisfied())
                .findFirst()
                .orElse(evaluation.requirements().getLast());
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.progression.updated",
                Component.translatable("quest.warlockery.werewolf." + quest.id()),
                focus.current(),
                focus.requirement().required()
            ).withStyle(evaluation.ready() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        });
    }

    private static void showVampireProgress(
        final ServerPlayer player,
        final VampireProgressionRules.Evaluation evaluation
    ) {
        evaluation.quest().ifPresent(quest -> {
            final VampireProgressionRules.RequirementStatus focus = evaluation.requirements().stream()
                .filter(status -> !status.satisfied())
                .findFirst()
                .orElse(evaluation.requirements().getLast());
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.progression.updated",
                Component.translatable("quest.warlockery.vampire." + quest.id()),
                focus.current(),
                focus.requirement().required()
            ).withStyle(evaluation.ready() ? ChatFormatting.GREEN : ChatFormatting.RED));
        });
    }

    private static boolean isUniqueVampireMetric(final VampireProgressionRules.Metric metric) {
        return switch (metric) {
            case DISTINCT_VILLAGERS_HALF_DRAINED,
                 DISTINCT_VILLAGES_REACHED_IN_BATSWARM_FORM,
                 DISTINCT_CAGED_VILLAGERS_HALF_DRAINED -> true;
            default -> false;
        };
    }

    public record ProgressUpdate(boolean changed, boolean advanced, int level, String messageKey) {
    }

    public record WolfAltarResult(
        boolean accepted,
        boolean advanced,
        int consumed,
        int level,
        String messageKey
    ) {
        private static WolfAltarResult failure(final String messageKey) {
            return new WolfAltarResult(false, false, 0, 0, messageKey);
        }
    }
}
