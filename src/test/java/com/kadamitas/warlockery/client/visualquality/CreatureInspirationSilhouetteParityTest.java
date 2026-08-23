package com.kadamitas.warlockery.client.visualquality;

import static com.kadamitas.warlockery.client.model.CreatureModelTestSupport.softwareSnapshot;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.client.model.BansheeModel;
import com.kadamitas.warlockery.client.model.CreatureModelTestSupport;
import com.kadamitas.warlockery.client.model.DemonModel;
import com.kadamitas.warlockery.client.model.GoblinModel;
import com.kadamitas.warlockery.client.model.PaleSteedModel;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

/** Direct silhouette comparison against approved production-board front and side crops. */
final class CreatureInspirationSilhouetteParityTest {
    private static final Path CONCEPT_ROOT = Path.of(
        "docs/art-source/creature-concepts/production"
    );
    private static final int NORMALIZED_SIZE = 128;
    private static final int NORMALIZED_PADDING = 6;
    private static final double MINIMUM_DICE = 0.55;
    private static final double MAXIMUM_WIDTH_PROFILE_ERROR = 0.23;
    private static final double MAXIMUM_ASPECT_ERROR = 0.60;

    private static final List<SpeciesReference> REFERENCES = List.of(
        new SpeciesReference(
            "Demon",
            CONCEPT_ROOT.resolve("wave-01a-infernal-boss-turnarounds.png"),
            new Crop(135, 5, 235, 198),
            new Crop(382, 5, 165, 198),
            () -> DemonModel.createBodyLayer().bakeRoot()
        ),
        new SpeciesReference(
            "Banshee",
            CONCEPT_ROOT.resolve("wave-01b-occult-anomaly-wiki-fusion-turnarounds.png"),
            new Crop(30, 40, 275, 300),
            new Crop(300, 40, 225, 300),
            () -> BansheeModel.createBodyLayer().bakeRoot()
        ),
        new SpeciesReference(
            "Goblin",
            CONCEPT_ROOT.resolve("wave-04c-goblin-clan-turnarounds.png"),
            new Crop(75, 30, 215, 217),
            new Crop(320, 30, 205, 217),
            () -> GoblinModel.createBodyLayer().bakeRoot()
        ),
        new SpeciesReference(
            "Pale Steed",
            CONCEPT_ROOT.resolve("wave-03b-occult-mount-turnarounds.png"),
            new Crop(15, 50, 155, 400),
            new Crop(180, 48, 340, 402),
            () -> PaleSteedModel.createBodyLayer().bakeRoot()
        )
    );

    @Test
    void bakedFrontAndSideSilhouettesMatchApprovedConceptCrops() throws Exception {
        final List<String> failures = new ArrayList<>();
        for (final SpeciesReference reference : REFERENCES) {
            final BufferedImage board = ImageIO.read(reference.board().toFile());
            assertNotNull(board, reference.board().toString());
            compare(
                reference.name(),
                "front",
                conceptMask(board, reference.front()),
                renderedMask(reference.model().get(), CreatureModelTestSupport.Projection.FRONT),
                failures
            );
            compare(
                reference.name(),
                "side",
                conceptMask(board, reference.side()),
                renderedMask(reference.model().get(), CreatureModelTestSupport.Projection.SIDE),
                failures
            );
        }
        assertTrue(
            failures.isEmpty(),
            () -> "Baked models diverge from approved production-board silhouettes:\n"
                + String.join("\n", failures)
        );
    }

    private static void compare(
        final String species,
        final String view,
        final Mask concept,
        final Mask rendered,
        final List<String> failures
    ) {
        final Mask normalizedConcept = normalize(concept);
        final Mask normalizedRender = normalize(rendered);
        final double dice = Math.max(
            dice(normalizedConcept, normalizedRender),
            dice(normalizedConcept, mirror(normalizedRender))
        );
        final double profileError = widthProfileError(normalizedConcept, normalizedRender);
        final double aspectError = Math.abs(aspect(concept) - aspect(rendered)) / aspect(concept);
        if (dice < MINIMUM_DICE
            || profileError > MAXIMUM_WIDTH_PROFILE_ERROR
            || aspectError > MAXIMUM_ASPECT_ERROR) {
            failures.add(String.format(
                Locale.ROOT,
                "%s %s [dice=%.3f required>=%.2f, widthProfileMAE=%.3f required<=%.2f, "
                    + "conceptAspect=%.3f, modelAspect=%.3f, aspectError=%.1f%% required<=%.0f%%]",
                species,
                view,
                dice,
                MINIMUM_DICE,
                profileError,
                MAXIMUM_WIDTH_PROFILE_ERROR,
                aspect(concept),
                aspect(rendered),
                aspectError * 100.0,
                MAXIMUM_ASPECT_ERROR * 100.0
            ));
        }
    }

    private static Mask renderedMask(
        final ModelPart root,
        final CreatureModelTestSupport.Projection projection
    ) {
        final BufferedImage image = softwareSnapshot(root, projection, 256, 8);
        final boolean[] pixels = new boolean[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y * image.getWidth() + x] = (image.getRGB(x, y) >>> 24) != 0;
            }
        }
        return largestComponent(new Mask(image.getWidth(), image.getHeight(), pixels));
    }

    private static Mask conceptMask(final BufferedImage board, final Crop crop) {
        assertTrue(crop.x() >= 0 && crop.y() >= 0
            && crop.x() + crop.width() <= board.getWidth()
            && crop.y() + crop.height() <= board.getHeight(), crop.toString());
        final BufferedImage image = board.getSubimage(
            crop.x(), crop.y(), crop.width(), crop.height()
        );
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean[] background = new boolean[width * height];
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            enqueueBackground(x, 0, width, background, pending);
            enqueueBackground(x, height - 1, width, background, pending);
        }
        for (int y = 0; y < height; y++) {
            enqueueBackground(0, y, width, background, pending);
            enqueueBackground(width - 1, y, width, background, pending);
        }
        while (!pending.isEmpty()) {
            final int index = pending.removeFirst();
            final int x = index % width;
            final int y = index / width;
            final int source = image.getRGB(x, y);
            visitBackgroundNeighbor(image, x - 1, y, source, background, pending);
            visitBackgroundNeighbor(image, x + 1, y, source, background, pending);
            visitBackgroundNeighbor(image, x, y - 1, source, background, pending);
            visitBackgroundNeighbor(image, x, y + 1, source, background, pending);
        }
        final boolean[] foreground = new boolean[background.length];
        for (int index = 0; index < foreground.length; index++) {
            foreground[index] = !background[index];
        }
        final Mask result = largestComponent(new Mask(width, height, foreground));
        assertTrue(bounds(result).height() >= height * 0.55, "concept crop lost its full-height subject: " + crop);
        return result;
    }

    private static void visitBackgroundNeighbor(
        final BufferedImage image,
        final int x,
        final int y,
        final int source,
        final boolean[] background,
        final ArrayDeque<Integer> pending
    ) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        final int index = y * image.getWidth() + x;
        if (background[index]) {
            return;
        }
        final int candidate = image.getRGB(x, y);
        if (saturation(candidate) <= 0.22 && colorDistance(source, candidate) <= 8.0) {
            background[index] = true;
            pending.addLast(index);
        }
    }

    private static void enqueueBackground(
        final int x,
        final int y,
        final int width,
        final boolean[] background,
        final ArrayDeque<Integer> pending
    ) {
        final int index = y * width + x;
        if (!background[index]) {
            background[index] = true;
            pending.addLast(index);
        }
    }

    private static double saturation(final int argb) {
        final int red = (argb >>> 16) & 0xFF;
        final int green = (argb >>> 8) & 0xFF;
        final int blue = argb & 0xFF;
        final int maximum = Math.max(red, Math.max(green, blue));
        final int minimum = Math.min(red, Math.min(green, blue));
        return maximum == 0 ? 0.0 : (maximum - minimum) / (double) maximum;
    }

    private static double colorDistance(final int first, final int second) {
        final int red = ((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF);
        final int green = ((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF);
        final int blue = (first & 0xFF) - (second & 0xFF);
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    private static Mask largestComponent(final Mask mask) {
        final boolean[] visited = new boolean[mask.pixels().length];
        boolean[] largest = new boolean[mask.pixels().length];
        int largestSize = 0;
        for (int start = 0; start < mask.pixels().length; start++) {
            if (!mask.pixels()[start] || visited[start]) {
                continue;
            }
            final ArrayDeque<Integer> pending = new ArrayDeque<>();
            final List<Integer> component = new ArrayList<>();
            pending.add(start);
            visited[start] = true;
            while (!pending.isEmpty()) {
                final int index = pending.removeFirst();
                component.add(index);
                final int x = index % mask.width();
                final int y = index / mask.width();
                for (int deltaY = -1; deltaY <= 1; deltaY++) {
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        final int nextX = x + deltaX;
                        final int nextY = y + deltaY;
                        if (nextX < 0 || nextY < 0
                            || nextX >= mask.width() || nextY >= mask.height()) {
                            continue;
                        }
                        final int next = nextY * mask.width() + nextX;
                        if (mask.pixels()[next] && !visited[next]) {
                            visited[next] = true;
                            pending.addLast(next);
                        }
                    }
                }
            }
            if (component.size() > largestSize) {
                largestSize = component.size();
                largest = new boolean[mask.pixels().length];
                for (final int index : component) {
                    largest[index] = true;
                }
            }
        }
        assertTrue(largestSize > 0, "silhouette extraction produced no foreground");
        return new Mask(mask.width(), mask.height(), largest);
    }

    private static Mask normalize(final Mask source) {
        final Bounds bounds = bounds(source);
        final boolean[] normalized = new boolean[NORMALIZED_SIZE * NORMALIZED_SIZE];
        final double scale = (NORMALIZED_SIZE - NORMALIZED_PADDING * 2.0) / bounds.height();
        final double scaledWidth = bounds.width() * scale;
        final double left = (NORMALIZED_SIZE - scaledWidth) / 2.0;
        for (int y = NORMALIZED_PADDING; y < NORMALIZED_SIZE - NORMALIZED_PADDING; y++) {
            final int sourceY = bounds.minY() + Math.min(
                bounds.height() - 1,
                Math.max(0, (int) ((y - NORMALIZED_PADDING + 0.5) / scale))
            );
            for (int x = 0; x < NORMALIZED_SIZE; x++) {
                final int sourceX = bounds.minX() + (int) ((x - left + 0.5) / scale);
                if (sourceX >= bounds.minX() && sourceX <= bounds.maxX()
                    && source.pixels()[sourceY * source.width() + sourceX]) {
                    normalized[y * NORMALIZED_SIZE + x] = true;
                }
            }
        }
        return new Mask(NORMALIZED_SIZE, NORMALIZED_SIZE, normalized);
    }

    private static Mask mirror(final Mask source) {
        final boolean[] mirrored = new boolean[source.pixels().length];
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                mirrored[y * source.width() + x] = source.pixels()[
                    y * source.width() + source.width() - 1 - x
                ];
            }
        }
        return new Mask(source.width(), source.height(), mirrored);
    }

    private static double dice(final Mask first, final Mask second) {
        int firstPixels = 0;
        int secondPixels = 0;
        int intersection = 0;
        for (int index = 0; index < first.pixels().length; index++) {
            if (first.pixels()[index]) {
                firstPixels++;
            }
            if (second.pixels()[index]) {
                secondPixels++;
            }
            if (first.pixels()[index] && second.pixels()[index]) {
                intersection++;
            }
        }
        return 2.0 * intersection / Math.max(firstPixels + secondPixels, 1);
    }

    private static double widthProfileError(final Mask first, final Mask second) {
        double total = 0.0;
        int rows = 0;
        for (int y = NORMALIZED_PADDING; y < NORMALIZED_SIZE - NORMALIZED_PADDING; y++) {
            final double firstWidth = rowWidth(first, y) / (double) (NORMALIZED_SIZE - 2 * NORMALIZED_PADDING);
            final double secondWidth = rowWidth(second, y) / (double) (NORMALIZED_SIZE - 2 * NORMALIZED_PADDING);
            total += Math.abs(firstWidth - secondWidth);
            rows++;
        }
        return total / rows;
    }

    private static int rowWidth(final Mask mask, final int y) {
        int minimum = mask.width();
        int maximum = -1;
        for (int x = 0; x < mask.width(); x++) {
            if (mask.pixels()[y * mask.width() + x]) {
                minimum = Math.min(minimum, x);
                maximum = Math.max(maximum, x);
            }
        }
        return maximum < minimum ? 0 : maximum - minimum + 1;
    }

    private static double aspect(final Mask mask) {
        final Bounds bounds = bounds(mask);
        return bounds.width() / (double) bounds.height();
    }

    private static Bounds bounds(final Mask mask) {
        int minimumX = mask.width();
        int minimumY = mask.height();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                if (mask.pixels()[y * mask.width() + x]) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        assertTrue(maximumX >= minimumX && maximumY >= minimumY, "empty silhouette");
        return new Bounds(minimumX, minimumY, maximumX, maximumY);
    }

    private record SpeciesReference(
        String name,
        Path board,
        Crop front,
        Crop side,
        Supplier<ModelPart> model
    ) {
    }

    private record Crop(int x, int y, int width, int height) {
    }

    private record Mask(int width, int height, boolean[] pixels) {
    }

    private record Bounds(int minX, int minY, int maxX, int maxY) {
        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }
    }
}
