import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Stream;

public final class GenerateOriginalAssets {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color[] ARCANE = colors("121225", "27234b", "5d3295", "a65bd4", "d88b5b", "65d5c1", "e8d6ad");
    private static final Color[] VERDANT = colors("101b19", "1e3a2d", "376b42", "68a84f", "a7c957", "d0b46c", "e8dfb5");
    private static final Color[] INFERNAL = colors("1b1015", "3b1721", "6b2435", "a83a3a", "e0693d", "f2b35d", "f6ddab");
    private static final Color[] SILVER = colors("11141d", "29303d", "4c5868", "8190a3", "b8c5d2", "e5edf2", "7ad4c8");
    private static final Color[] FROST = colors("111a2c", "1e3650", "315b76", "4f8fa3", "80c4cd", "c6eef0", "ecfbf8");
    private static final List<Color[]> PALETTES = List.of(ARCANE, VERDANT, INFERNAL, SILVER, FROST);

    private GenerateOriginalAssets() {
    }

    public static void main(final String[] args) throws Exception {
        final Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        final Path textureRoot = root.resolve("src/main/resources/assets/warlockery/textures");
        final Path entityRoot = textureRoot.resolve("entity");
        final Path lootRoot = root.resolve("src/main/resources/data/warlockery/loot_table/entities");
        if (!Files.isDirectory(textureRoot) || !Files.isDirectory(lootRoot)) {
            throw new IllegalArgumentException("Run from the Warlockery project root: " + root);
        }

        final List<Path> existing;
        try (Stream<Path> files = Files.walk(textureRoot)) {
            existing = files.filter(path -> path.toString().endsWith(".png"))
                .filter(path -> !path.startsWith(entityRoot))
                .sorted()
                .toList();
        }
        for (final Path path : existing) {
            final int[] dimensions = dimensions(path);
            final String category = textureRoot.relativize(path).getName(0).toString();
            final BufferedImage image = switch (category) {
                case "block" -> blockTexture(path.getFileName().toString(), dimensions[0], dimensions[1]);
                case "gui" -> guiTexture(path.getFileName().toString(), dimensions[0], dimensions[1]);
                case "particle" -> particleTexture(path.getFileName().toString(), dimensions[0], dimensions[1]);
                default -> itemTexture(path.getFileName().toString(), dimensions[0], dimensions[1]);
            };
            ImageIO.write(image, "png", path.toFile());
        }

        Files.createDirectories(entityRoot);
        final List<String> entities;
        try (Stream<Path> files = Files.list(lootRoot)) {
            entities = files.filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .sorted()
                .toList();
        }
        for (final String entity : entities) {
            final int size = entity.equals("ent") ? 128 : 64;
            ImageIO.write(entityTexture(entity, size), "png", entityRoot.resolve(entity + ".png").toFile());
        }

        final Path emblem = root.resolve("docs/art-source/warlockery-emblem.png");
        if (Files.isRegularFile(emblem)) {
            final BufferedImage source = ImageIO.read(emblem.toFile());
            final BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = icon.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, 64, 64, null);
            graphics.dispose();
            quantize(icon, ARCANE);
            ImageIO.write(icon, "png", root.resolve("src/main/resources/assets/warlockery/icon.png").toFile());
        }

        final String manifest = """
            # Warlockery generated asset manifest

            - Generator: `tools/GenerateOriginalAssets.java`
            - Method: deterministic geometric pixel art; existing files supply dimensions only
            - Entity skins: generated from registered entity IDs using original palettes and UV-safe blocks
            - Palette: charcoal, violet, oxidized copper, teal, parchment, plus semantic variants
            - Generated PNG count: %d
            - Generated entity skin count: %d
            """.formatted(existing.size() + entities.size() + 1, entities.size());
        Files.writeString(root.resolve("docs/GENERATED_ASSETS.md"), manifest);
        System.out.printf("Generated %d original Warlockery PNGs (%d entity skins).%n",
            existing.size() + entities.size() + 1, entities.size());
    }

    private static int[] dimensions(final Path path) throws IOException {
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            final var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return new int[]{16, 16};
            }
            final var reader = readers.next();
            try {
                reader.setInput(input);
                return new int[]{Math.max(1, reader.getWidth(0)), Math.max(1, reader.getHeight(0))};
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage blockTexture(final String name, final int width, final int height) {
        final BufferedImage image = image(width, height);
        final Color[] palette = palette(name);
        final RandomGenerator random = random(name);
        final int frame = Math.max(1, Math.min(width, 16));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int checker = ((x / 4) + (y / 4)) & 1;
                final int noise = random.nextInt(7) == 0 ? 1 : 0;
                image.setRGB(x, y, palette[Math.min(4, 1 + checker + noise)].getRGB());
            }
            if (y % frame == 0) {
                for (int x = 0; x < width; x += 5) image.setRGB(x, y, palette[4].getRGB());
            }
        }
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(palette[0]);
        for (int x = 3; x < width; x += 8) graphics.drawLine(x, 0, x, height - 1);
        graphics.setColor(palette[5]);
        for (int y = 3; y < height; y += frame) graphics.drawLine(0, y, width - 1, y);
        graphics.dispose();
        return image;
    }

    private static BufferedImage itemTexture(final String name, final int width, final int height) {
        if (width > 32 || height > Math.max(64, width * 2)) {
            return entityTexture(name, Math.max(width, height));
        }
        final BufferedImage image = image(width, height);
        final Color[] palette = palette(name);
        final Graphics2D graphics = image.createGraphics();
        graphics.setBackground(TRANSPARENT);
        graphics.clearRect(0, 0, width, height);
        final int scale = Math.max(1, Math.min(width, height) / 16);
        final int cx = width / 2;
        final int cy = height / 2;
        final Polygon diamond = new Polygon(
            new int[]{cx, width - 2 * scale, cx, 2 * scale},
            new int[]{2 * scale, cy, height - 2 * scale, cy}, 4);
        graphics.setColor(palette[0]);
        graphics.fillPolygon(diamond);
        final Polygon inner = new Polygon(
            new int[]{cx, width - 4 * scale, cx, 4 * scale},
            new int[]{4 * scale, cy, height - 4 * scale, cy}, 4);
        graphics.setColor(palette[3]);
        graphics.fillPolygon(inner);
        graphics.setColor(palette[5]);
        graphics.fillRect(cx - scale, 4 * scale, 2 * scale, Math.max(scale, height - 8 * scale));
        graphics.fillRect(4 * scale, cy - scale, Math.max(scale, width - 8 * scale), 2 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(cx - scale, cy - scale, 2 * scale, 2 * scale);
        graphics.dispose();
        return image;
    }

    private static BufferedImage entityTexture(final String name, final int size) {
        final BufferedImage image = image(size, size);
        final Color[] palette = palette(name);
        final RandomGenerator random = random(name);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(palette[1]);
        graphics.fillRect(0, 0, size, size);
        final int unit = Math.max(1, size / 64);
        for (int y = 0; y < size; y += 4 * unit) {
            for (int x = 0; x < size; x += 4 * unit) {
                graphics.setColor(palette[1 + random.nextInt(4)]);
                graphics.fillRect(x, y, 4 * unit, 4 * unit);
            }
        }
        graphics.setColor(palette[4]);
        graphics.fillRect(8 * unit, 8 * unit, 8 * unit, 8 * unit);
        graphics.setColor(palette[0]);
        graphics.fillRect(10 * unit, 11 * unit, 2 * unit, unit);
        graphics.fillRect(14 * unit, 11 * unit, 2 * unit, unit);
        graphics.setColor(palette[5]);
        graphics.fillRect(20 * unit, 20 * unit, 8 * unit, 12 * unit);
        graphics.fillRect(44 * unit, 20 * unit, 4 * unit, 12 * unit);
        graphics.fillRect(4 * unit, 20 * unit, 4 * unit, 12 * unit);
        graphics.setColor(palette[6]);
        graphics.fillRect(23 * unit, 23 * unit, 2 * unit, 6 * unit);
        graphics.setColor(palette[0]);
        graphics.drawRect(8 * unit, 8 * unit, 8 * unit, 8 * unit);
        graphics.drawRect(20 * unit, 20 * unit, 8 * unit, 12 * unit);
        graphics.dispose();
        return image;
    }

    private static BufferedImage guiTexture(final String name, final int width, final int height) {
        final BufferedImage image = image(width, height);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(ARCANE[0]);
        graphics.fillRect(0, 0, width, height);
        final int step = Math.max(4, Math.min(width, height) / 16);
        graphics.setColor(ARCANE[1]);
        for (int y = 0; y < height; y += step) graphics.drawLine(0, y, width - 1, y);
        graphics.setColor(ARCANE[4]);
        graphics.drawRect(1, 1, Math.max(0, width - 3), Math.max(0, height - 3));
        graphics.setColor(ARCANE[5]);
        graphics.drawRect(step, step, Math.max(0, width - 2 * step - 1), Math.max(0, height - 2 * step - 1));
        graphics.dispose();
        return image;
    }

    private static BufferedImage particleTexture(final String name, final int width, final int height) {
        final BufferedImage image = image(width, height);
        final Graphics2D graphics = image.createGraphics();
        graphics.setBackground(TRANSPARENT);
        graphics.clearRect(0, 0, width, height);
        graphics.setColor(ARCANE[3]);
        graphics.fillOval(width / 4, height / 4, Math.max(1, width / 2), Math.max(1, height / 2));
        graphics.setColor(ARCANE[5]);
        graphics.fillRect(width / 2, height / 8, 1, Math.max(1, height * 3 / 4));
        graphics.fillRect(width / 8, height / 2, Math.max(1, width * 3 / 4), 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage image(final int width, final int height) {
        return new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
    }

    private static Color[] palette(final String name) {
        final String lower = name.toLowerCase();
        if (lower.contains("silver") || lower.contains("iron") || lower.contains("hunter")
            || lower.contains("wolf") || lower.contains("lycan")) return SILVER;
        if (lower.contains("ice") || lower.contains("snow") || lower.contains("frost")) return FROST;
        if (lower.contains("blood") || lower.contains("vamp") || lower.contains("demon") || lower.contains("infernal")) return INFERNAL;
        if (lower.contains("wood") || lower.contains("plant") || lower.contains("leaf") || lower.contains("ent") || lower.contains("delve")) return VERDANT;
        return PALETTES.get(Math.floorMod(name.hashCode(), PALETTES.size()));
    }

    private static RandomGenerator random(final String value) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(value.hashCode() * 0x9E3779B97F4A7C15L);
    }

    private static void quantize(final BufferedImage image, final Color[] palette) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final Color source = new Color(image.getRGB(x, y), true);
                if (source.getAlpha() == 0) continue;
                final Color closest = Arrays.stream(palette)
                    .min(Comparator.comparingInt(color -> distance(source, color)))
                    .orElse(palette[0]);
                image.setRGB(x, y, closest.getRGB());
            }
        }
    }

    private static int distance(final Color first, final Color second) {
        final int red = first.getRed() - second.getRed();
        final int green = first.getGreen() - second.getGreen();
        final int blue = first.getBlue() - second.getBlue();
        return red * red + green * green + blue * blue;
    }

    private static Color[] colors(final String... values) {
        return Arrays.stream(values).map(value -> new Color(Integer.parseInt(value, 16))).toArray(Color[]::new);
    }
}
