package com.kadamitas.warlockery.transformation;

import net.minecraft.world.entity.player.Player;

public final class SupernaturalProgression {
    public static final int MAX_LEVEL = 10;

    private SupernaturalProgression() {
    }

    public static int level(final Player player, final Path path) {
        return Math.clamp(player.getPersistentData().getIntOr(path.key(), 0), 0, MAX_LEVEL);
    }

    public static void setLevel(final Player player, final Path path, final int level) {
        player.getPersistentData().putInt(path.key(), Math.clamp(level, 0, MAX_LEVEL));
    }

    public static void copy(final Player source, final Player destination) {
        for (final Path path : Path.values()) {
            setLevel(destination, path, level(source, path));
        }
    }

    public enum Path {
        WEREWOLF("WarlockeryWerewolfLevel", SupernaturalForm.WEREWOLF),
        VAMPIRE("WarlockeryVampireLevel", SupernaturalForm.VAMPIRE);

        private final String key;
        private final SupernaturalForm form;

        Path(final String key, final SupernaturalForm form) {
            this.key = key;
            this.form = form;
        }

        public String key() {
            return key;
        }

        public SupernaturalForm form() {
            return form;
        }
    }
}
