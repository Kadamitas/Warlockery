package com.kadamitas.warlockery.world;

import java.util.List;

public final class LegacyStructureRules {
    public static final int LANDMARK_INTERVAL = 2_400;
    public static final int GATHERING_INTERVAL = 200;
    public static final int REGION_SIZE = 128;

    private static final List<Mapping> ARCHIVED_MAPPINGS = List.of(
        new Mapping("Stone circles", "Generated ritual center with nighttime Circle Mage gatherings"),
        new Mapping("Apothecary and shop", "Village Warlock apothecary with ingredient trades"),
        new Mapping("Strawmen", "Generated unbound scarecrows that attract nearby zombies"),
        new Mapping("Abandoned shacks", "Generated wilderness shelter with occult starter loot"),
        new Mapping("Hobgoblin huts", "Wilderness camps for friendly travelling Hobgoblins"),
        new Mapping("Town Walls", "Modern village guards, bells, raids, and a fortified keep"),
        new Mapping("Town Keeps", "Generated village keep with guards and treasury"),
        new Mapping("Village Book shoppes", "Vanilla librarian houses and lectern profession mechanics"),
        new Mapping("Village witch huts", "Warlock apothecary combining a workshop, crops, and trades")
    );

    private LegacyStructureRules() {
    }

    public static Landmark select(final int selector) {
        final Landmark[] values = Landmark.values();
        return values[Math.floorMod(selector, values.length)];
    }

    public static boolean canGenerate(
        final boolean overworld,
        final boolean village,
        final boolean regionAlreadyGenerated,
        final boolean clearFootprint
    ) {
        return overworld && !village && !regionAlreadyGenerated && clearFootprint;
    }

    public static boolean shouldGather(final boolean night, final int existingMages) {
        return night && existingMages >= 0 && existingMages < 3;
    }

    public static boolean attractsZombie(final boolean bound, final boolean alive, final double distanceSquared) {
        return !bound && alive && distanceSquared <= 24.0 * 24.0;
    }

    public static long regionKey(final int blockX, final int blockZ) {
        final int regionX = Math.floorDiv(blockX, REGION_SIZE);
        final int regionZ = Math.floorDiv(blockZ, REGION_SIZE);
        return (long) regionX & 0xffffffffL | ((long) regionZ & 0xffffffffL) << 32;
    }

    public static List<Mapping> archivedMappings() {
        return ARCHIVED_MAPPINGS;
    }

    public enum Landmark {
        STONE_CIRCLE(4, 3),
        STRAW_IDOL(1, 4),
        ABANDONED_SHACK(2, 4);

        private final int radius;
        private final int height;

        Landmark(final int radius, final int height) {
            this.radius = radius;
            this.height = height;
        }

        public int radius() {
            return radius;
        }

        public int height() {
            return height;
        }
    }

    public record Mapping(String archivedFeature, String modernImplementation) {
    }
}
