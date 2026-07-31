package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

public final class ChalkCircleLayout {
    private ChalkCircleLayout() {
    }

    public static List<Ring> rings(final Map<String, Integer> declaredGlyphs) {
        if (declaredGlyphs.isEmpty()) {
            return List.of();
        }
        if (declaredGlyphs.size() > Size.values().length) {
            throw new IllegalArgumentException("A ritual can use at most three chalk rings");
        }
        final List<Map.Entry<String, Integer>> glyphs = declaredGlyphs.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList();
        final Assignment assignment = assignments(glyphs.size()).stream()
            .min(Comparator.comparingInt((Assignment candidate) -> candidate.cost(glyphs))
                .thenComparing(Assignment::signature))
            .orElseThrow();
        final List<Ring> rings = new ArrayList<>(glyphs.size());
        for (int index = 0; index < glyphs.size(); index++) {
            rings.add(new Ring(glyphs.get(index).getKey(), assignment.sizes().get(index)));
        }
        return rings.stream().sorted(Comparator.comparing(Ring::size)).toList();
    }

    public static Map<String, Integer> canonicalGlyphs(final Map<String, Integer> declaredGlyphs) {
        final Map<String, Integer> canonical = new LinkedHashMap<>();
        rings(declaredGlyphs).forEach(ring -> canonical.put(ring.glyph(), ring.requiredCount()));
        return Collections.unmodifiableMap(canonical);
    }

    public static boolean matches(
        final ServerLevel level,
        final BlockPos center,
        final Map<String, Integer> declaredGlyphs
    ) {
        return rings(declaredGlyphs).stream().allMatch(ring -> present(level, center, ring) == ring.requiredCount());
    }

    public static int present(final ServerLevel level, final BlockPos center, final Ring ring) {
        return Math.toIntExact(ring.size().offsets().stream()
            .map(center::offset)
            .filter(position -> ring.glyph().equals(glyphId(level, position).orElse("")))
            .count());
    }

    public static Optional<String> uniformGlyph(
        final ServerLevel level,
        final BlockPos center,
        final Size size
    ) {
        String glyph = null;
        for (final BlockPos offset : size.offsets()) {
            final Optional<String> candidate = glyphId(level, center.offset(offset)).filter(ChalkCircleLayout::isRingGlyph);
            if (candidate.isEmpty()) {
                return Optional.empty();
            }
            if (glyph == null) {
                glyph = candidate.orElseThrow();
            } else if (!glyph.equals(candidate.orElseThrow())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(glyph);
    }

    public static int presentGlyphs(
        final ServerLevel level,
        final BlockPos center,
        final Size size,
        final java.util.Set<String> acceptedGlyphs
    ) {
        return Math.toIntExact(size.offsets().stream()
            .map(center::offset)
            .map(position -> glyphId(level, position))
            .flatMap(Optional::stream)
            .filter(acceptedGlyphs::contains)
            .count());
    }

    private static Optional<String> glyphId(final ServerLevel level, final BlockPos position) {
        final Identifier id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock());
        return id != null && Warlockery.MOD_ID.equals(id.getNamespace())
            ? Optional.of(id.getPath())
            : Optional.empty();
    }

    private static boolean isRingGlyph(final String id) {
        return id.startsWith("circleglyph");
    }

    private static List<Assignment> assignments(final int count) {
        final List<Assignment> assignments = new ArrayList<>();
        assign(count, new ArrayList<>(), assignments);
        return List.copyOf(assignments);
    }

    private static void assign(final int count, final List<Size> selected, final List<Assignment> assignments) {
        if (selected.size() == count) {
            assignments.add(new Assignment(List.copyOf(selected)));
            return;
        }
        for (final Size size : Size.values()) {
            if (!selected.contains(size)) {
                selected.add(size);
                assign(count, selected, assignments);
                selected.removeLast();
            }
        }
    }

    private static Size preferredSize(final int declaredCount) {
        if (declaredCount <= 8) {
            return Size.SMALL;
        }
        if (declaredCount <= 12) {
            return Size.MEDIUM;
        }
        return Size.LARGE;
    }

    public enum Size {
        SMALL(ChalkCircleRules.SMALL_RADIUS),
        MEDIUM(ChalkCircleRules.MEDIUM_RADIUS),
        LARGE(ChalkCircleRules.LARGE_RADIUS);

        private final int radius;
        private final List<BlockPos> offsets;

        Size(final int radius) {
            this.radius = radius;
            offsets = createOffsets(radius);
        }

        public int radius() {
            return radius;
        }

        public int diameter() {
            return radius * 2 + 1;
        }

        public int markCount() {
            return offsets.size();
        }

        public List<BlockPos> offsets() {
            return offsets;
        }

        public static Size forMarkCount(final int markCount) {
            for (final Size size : values()) {
                if (size.markCount() == markCount) {
                    return size;
                }
            }
            throw new IllegalArgumentException("Unsupported canonical chalk mark count: " + markCount);
        }

        public static Size forOfferingCount(final int chalkCount) {
            if (chalkCount < 1 || chalkCount > values().length) {
                throw new IllegalArgumentException("A glyph transformation needs one, two, or three chalk pieces");
            }
            return values()[chalkCount - 1];
        }

        private static List<BlockPos> createOffsets(final int radius) {
            final int edge = (radius - 1) / 2;
            final List<BlockPos> offsets = new ArrayList<>();
            for (int coordinate = -edge; coordinate <= edge; coordinate++) {
                offsets.add(new BlockPos(coordinate, 0, -radius));
                offsets.add(new BlockPos(coordinate, 0, radius));
                offsets.add(new BlockPos(-radius, 0, coordinate));
                offsets.add(new BlockPos(radius, 0, coordinate));
            }
            for (int step = 1; step <= edge; step++) {
                final int horizontal = edge + step;
                final int vertical = radius - step;
                offsets.add(new BlockPos(-horizontal, 0, -vertical));
                offsets.add(new BlockPos(horizontal, 0, -vertical));
                offsets.add(new BlockPos(-horizontal, 0, vertical));
                offsets.add(new BlockPos(horizontal, 0, vertical));
            }
            return List.copyOf(offsets);
        }
    }

    public record Ring(String glyph, Size size) {
        public Ring {
            if (glyph.isBlank()) {
                throw new IllegalArgumentException("A chalk ring must name its glyph");
            }
        }

        public int requiredCount() {
            return size.markCount();
        }
    }

    private record Assignment(List<Size> sizes) {
        private int cost(final List<Map.Entry<String, Integer>> glyphs) {
            int cost = 0;
            for (int index = 0; index < sizes.size(); index++) {
                cost += Math.abs(sizes.get(index).ordinal() - preferredSize(glyphs.get(index).getValue()).ordinal());
            }
            return cost;
        }

        private String signature() {
            return sizes.stream().map(size -> Integer.toString(size.ordinal())).reduce("", String::concat);
        }
    }
}
