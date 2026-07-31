import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GenerateOriginalAssets {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color[] ARCANE = colors("121225", "27234b", "5d3295", "a65bd4", "d88b5b", "65d5c1", "e8d6ad");
    private static final Color[] VERDANT = colors("101b19", "1e3a2d", "376b42", "68a84f", "a7c957", "d0b46c", "e8dfb5");
    private static final Color[] INFERNAL = colors("1b1015", "3b1721", "6b2435", "a83a3a", "e0693d", "f2b35d", "f6ddab");
    private static final Color[] SILVER = colors("11141d", "29303d", "4c5868", "8190a3", "b8c5d2", "e5edf2", "7ad4c8");
    private static final Color[] FROST = colors("111a2c", "1e3650", "315b76", "4f8fa3", "80c4cd", "c6eef0", "ecfbf8");
    private static final Color[] WOOD = colors("201510", "4a2a20", "754733", "9d6845", "c79a65", "e0bd83", "f0ddb0");
    private static final List<Color[]> PALETTES = List.of(ARCANE, VERDANT, INFERNAL, SILVER, FROST, WOOD);
    private static final SpritePalette GOBLINITE = new SpritePalette(
        new Color(0x09130F), new Color(0x173326), new Color(0x245A3B),
        new Color(0x3F8652), new Color(0x77BD67), new Color(0xB7E58D)
    );

    private GenerateOriginalAssets() {
    }

    public static void main(final String[] args) throws Exception {
        final boolean polishOnly = Arrays.asList(args).contains("--polish");
        final boolean goblinsOnly = Arrays.asList(args).contains("--goblins");
        final boolean transformedWomenOnly = Arrays.asList(args).contains("--nami-naamah");
        final Path root = Arrays.stream(args)
            .filter(argument -> !argument.startsWith("--"))
            .findFirst()
            .map(Path::of)
            .orElseGet(() -> Path.of("."))
            .toAbsolutePath()
            .normalize();
        final Path textureRoot = root.resolve("src/main/resources/assets/warlockery/textures");
        final Path entityRoot = textureRoot.resolve("entity");
        final Path lootRoot = root.resolve("src/main/resources/data/warlockery/loot_table/entities");
        if (!Files.isDirectory(textureRoot) || !Files.isDirectory(lootRoot)) {
            throw new IllegalArgumentException("Run from the Warlockery project root: " + root);
        }

        if (goblinsOnly) {
            Files.createDirectories(entityRoot);
            ImageIO.write(penguinGoblinTexture(false), "png", entityRoot.resolve("goblin.png").toFile());
            ImageIO.write(penguinGoblinTexture(true), "png", entityRoot.resolve("hobgoblin.png").toFile());
            final Path itemRoot = textureRoot.resolve("item");
            ImageIO.write(itemTexture("goblin_spawn_egg.png", 16, 16, false), "png", itemRoot.resolve("goblin_spawn_egg.png").toFile());
            ImageIO.write(itemTexture("hobgoblin_spawn_egg.png", 16, 16, false), "png", itemRoot.resolve("hobgoblin_spawn_egg.png").toFile());
            System.out.println("Generated goblin and hobgoblin textures.");
            return;
        }

        if (transformedWomenOnly) {
            Files.createDirectories(entityRoot);
            ImageIO.write(namiSkin(), "png", entityRoot.resolve("nami.png").toFile());
            ImageIO.write(naamahSkin(), "png", entityRoot.resolve("naamah.png").toFile());
            System.out.println("Generated Nami and Naamah slim-player skins.");
            return;
        }

        if (polishOnly) {
            polishVisualAssets(textureRoot);
            writeCompassModelAssets(root);
            System.out.println("Polished release-facing Warlockery textures.");
            return;
        }

        final List<Path> visibleTextures = visibleTextures(root, textureRoot, entityRoot);
        final Set<String> blockItemIds = blockItemIds(root);
        for (final Path path : visibleTextures) {
            Files.createDirectories(path.getParent());
            final int[] dimensions = dimensions(path);
            final Path relative = textureRoot.relativize(path);
            final String category = relative.getName(0).toString();
            final String sourceName = category.equals("entity") ? relative.toString() : path.getFileName().toString();
            final BufferedImage image = switch (category) {
                case "block" -> blockTexture(sourceName, dimensions[0], dimensions[1]);
                case "entity" -> equipmentLayerTexture(sourceName, 64, 32);
                case "gui" -> guiTexture(sourceName, dimensions[0], dimensions[1]);
                case "particle" -> particleTexture(sourceName, dimensions[0], dimensions[1]);
                default -> itemTexture(
                    sourceName,
                    dimensions[0],
                    dimensions[1],
                    blockItemIds.contains(sourceName.replaceFirst("\\.png$", ""))
                );
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
            ImageIO.write(entityTexture(entity, 64), "png", entityRoot.resolve(entity + ".png").toFile());
        }

        final Path effectRoot = textureRoot.resolve("mob_effect");
        Files.createDirectories(effectRoot);
        ImageIO.write(soaringEffectTexture(), "png", effectRoot.resolve("soaring.png").toFile());
        writeHunterArmorTextures(textureRoot);
        polishVisualAssets(textureRoot);
        writeCompassModelAssets(root);

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
            """.formatted(visibleTextures.size() + entities.size() + 11, entities.size());
        Files.writeString(root.resolve("docs/GENERATED_ASSETS.md"), manifest);
        System.out.printf("Generated %d original Warlockery PNGs (%d entity skins).%n",
            visibleTextures.size() + entities.size() + 11, entities.size());
    }

    private static void polishVisualAssets(final Path textureRoot) throws IOException {
        final Path itemRoot = textureRoot.resolve("item");
        removeDetachedPaletteMarks(itemRoot);
        Files.deleteIfExists(itemRoot.resolve("brew_combustion.png"));
        Files.deleteIfExists(itemRoot.resolve("brew_endless_water.png"));

        final Map<String, BufferedImage> sprites = Map.ofEntries(
            Map.entry("boline.png", bolineSprite()),
            Map.entry("ritual_knife.png", ritualKnifeSprite()),
            Map.entry("ingredient_graveyard_dust.png", dustSprite(colors("17151A", "3C3442", "66546B", "927A86", "B7A2A7", "D8C7C2"))),
            Map.entry("ingredient_silverdust.png", dustSprite(colors("11151B", "34404D", "5D6D7C", "8999A8", "BBC8D2", "F0F4F4"))),
            Map.entry("ingredient_spectral_dust.png", dustSprite(colors("101B19", "174138", "246858", "3D9474", "77C992", "C4F1C9"))),
            Map.entry("ingredient_delvealloydust.png", dustSprite(GOBLINITE.colors())),
            Map.entry("deepslate_delvealloy_ore.png", gobliniteMineralBlockSprite(false)),
            Map.entry("raw_delvealloy_block.png", gobliniteMineralBlockSprite(true)),
            Map.entry("coffin.png", coffinSprite(new SpritePalette(colors("170F12", "342028", "56313A", "7B4550", "AE6A65", "D69C78")), false)),
            Map.entry("coffinblock.png", coffinSprite(new SpritePalette(colors("1C130E", "41271B", "6A3D27", "925A36", "BC8150", "E0B878")), false)),
            Map.entry("vcoffin.png", coffinSprite(new SpritePalette(colors("100B10", "241220", "4A1830", "79233E", "AD3650", "D98A79")), true)),
            Map.entry("alder_planks.png", plankSprite(new SpritePalette(colors("1D150F", "4A2D1E", "74472A", "9B673B", "C49559", "E4C27F")))),
            Map.entry("hawthorn_planks.png", plankSprite(new SpritePalette(colors("1D1110", "48251F", "6F392D", "9A5A43", "C5825C", "E4B182")))),
            Map.entry("rowan_planks.png", plankSprite(new SpritePalette(colors("1B1210", "42261B", "6D3E27", "9B6038", "C98B51", "E8BF78")))),
            Map.entry("ice_slippers.png", slippersSprite(new SpritePalette(colors("101A24", "24445A", "376F82", "58A1AD", "8ED5D8", "D8FAF3")))),
            Map.entry("ruby_slippers.png", slippersSprite(new SpritePalette(colors("1B0D13", "491424", "7A1E35", "AD2E47", "DE5260", "FF9A8E")))),
            Map.entry("glassgoblet.png", gobletSprite(false, false)),
            Map.entry("glassgobletfull.png", gobletSprite(true, false)),
            Map.entry("chalice.png", gobletSprite(false, true)),
            Map.entry("ingredient_chalice.png", ingredientChaliceSprite(false)),
            Map.entry("ingredient_chalice_full.png", ingredientChaliceSprite(true)),
            Map.entry("chalk_heart.png", chalkSprite(new Color(0xD1A542), new Color(0xFFE18A), false)),
            Map.entry("chalk_infernal.png", chalkSprite(new Color(0xA62D37), new Color(0xFF6A54), false)),
            Map.entry("chalk_ritual.png", chalkSprite(new Color(0xB6C2D0), new Color(0xF3FBFF), false)),
            Map.entry("chalk_ritual_charged.png", chalkSprite(new Color(0x6B42A7), new Color(0xD29BFF), true)),
            Map.entry("chalk_the_veil.png", chalkSprite(new Color(0x318698), new Color(0x9EEFF0), false)),
            Map.entry("circle.png", glyphItemSprite(new Color(0xA97422), new Color(0xFFE187), GlyphMotif.HEART)),
            Map.entry("circleglyphritual.png", glyphItemSprite(new Color(0x78899C), new Color(0xECF7FF), GlyphMotif.RITUAL)),
            Map.entry("circleglyphinfernal.png", glyphItemSprite(new Color(0x9D2632), new Color(0xFF7355), GlyphMotif.INFERNAL)),
            Map.entry("circleglyph_veil.png", glyphItemSprite(new Color(0x287C8E), new Color(0x8FE9E9), GlyphMotif.VEIL)),
            Map.entry("delvealloysword.png", gobliniteToolSprite(ToolShape.SWORD)),
            Map.entry("delvealloyaxe.png", gobliniteToolSprite(ToolShape.AXE)),
            Map.entry("delvealloypickaxe.png", gobliniteToolSprite(ToolShape.PICKAXE)),
            Map.entry("delvealloyshovel.png", gobliniteToolSprite(ToolShape.SHOVEL)),
            Map.entry("delvealloyhoe.png", gobliniteToolSprite(ToolShape.HOE)),
            Map.entry("delvealloyhelm.png", gobliniteArmorSprite(ArmorShape.HELMET)),
            Map.entry("delvealloychestplate.png", gobliniteArmorSprite(ArmorShape.CHESTPLATE)),
            Map.entry("delvealloyleggings.png", gobliniteArmorSprite(ArmorShape.LEGGINGS)),
            Map.entry("delvealloyboots.png", gobliniteArmorSprite(ArmorShape.BOOTS)),
            Map.entry("ingredient_delvealloyingot.png", gobliniteIngotSprite(false)),
            Map.entry("ingredient_delvealloynugget.png", gobliniteIngotSprite(true))
        );
        for (final Map.Entry<String, BufferedImage> sprite : sprites.entrySet()) {
            ImageIO.write(sprite.getValue(), "png", itemRoot.resolve(sprite.getKey()).toFile());
        }

        for (int frame = 0; frame < 33; frame++) {
            ImageIO.write(compassSprite(frame, 33, false), "png", itemRoot.resolve("playercompass" + frame + ".png").toFile());
        }
        for (int frame = 0; frame < 33; frame++) {
            ImageIO.write(compassSprite(frame, 33, true), "png", itemRoot.resolve("shelfcompass_" + frame + ".png").toFile());
        }

        final Path blockRoot = textureRoot.resolve("block");
        ImageIO.write(glyphTexture(new Color(0xA97422), new Color(0xFFE187), GlyphMotif.HEART), "png", blockRoot.resolve("circleglyph1.9.png").toFile());
        ImageIO.write(glyphTexture(new Color(0x78899C), new Color(0xECF7FF), GlyphMotif.RITUAL), "png", blockRoot.resolve("circleglyphritual.png").toFile());
        ImageIO.write(glyphTexture(new Color(0x9D2632), new Color(0xFF7355), GlyphMotif.INFERNAL), "png", blockRoot.resolve("circleglyphinfernal.png").toFile());
        ImageIO.write(glyphTexture(new Color(0x287C8E), new Color(0x8FE9E9), GlyphMotif.VEIL), "png", blockRoot.resolve("circleglyph_veil.png").toFile());
    }

    private static void writeCompassModelAssets(final Path root) throws IOException {
        final Path modelRoot = root.resolve("src/main/resources/assets/warlockery/models/item");
        final Path definitionRoot = root.resolve("src/main/resources/assets/warlockery/items");
        Files.createDirectories(modelRoot);
        for (int frame = 0; frame < 33; frame++) {
            writeGeneratedItemModel(modelRoot.resolve("playercompass" + frame + ".json"), "playercompass" + frame);
            writeGeneratedItemModel(modelRoot.resolve("shelfcompass_" + frame + ".json"), "shelfcompass_" + frame);
        }
        Files.writeString(definitionRoot.resolve("playercompass.json"), compassDefinition("playercompass"));
        Files.writeString(definitionRoot.resolve("shelfcompass.json"), compassDefinition("shelfcompass_"));
    }

    private static void writeGeneratedItemModel(final Path path, final String texture) throws IOException {
        Files.writeString(path, """
            {
              "parent": "minecraft:item/generated",
              "textures": {
                "layer0": "warlockery:item/%s"
              }
            }
            """.formatted(texture));
    }

    private static String compassDefinition(final String modelPrefix) {
        return """
            {
              "model": {
                "type": "minecraft:condition",
                "component": "minecraft:lodestone_tracker",
                "on_false": %s,
                "on_true": %s,
                "property": "minecraft:has_component"
              }
            }
            """.formatted(
                compassRangeDispatch(modelPrefix, "spawn"),
                compassRangeDispatch(modelPrefix, "lodestone")
            );
    }

    private static String compassRangeDispatch(final String modelPrefix, final String target) {
        final String entries = java.util.stream.IntStream.range(0, 32)
            .mapToObj(frame -> """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "warlockery:item/%s%d"
                  },
                  "threshold": %s
                }
                """.formatted(modelPrefix, frame, frame == 0 ? "0" : frame - 0.5F).indent(4).stripTrailing())
            .collect(java.util.stream.Collectors.joining(",\n"));
        return """
            {
              "type": "minecraft:range_dispatch",
              "entries": [
            %s
              ],
              "property": "minecraft:compass",
              "scale": 32,
              "target": "%s"
            }
            """.formatted(entries.indent(4).stripTrailing(), target).strip();
    }

    private static void removeDetachedPaletteMarks(final Path itemRoot) throws IOException {
        final List<Path> textures;
        try (Stream<Path> files = Files.list(itemRoot)) {
            textures = files.filter(path -> path.toString().endsWith(".png")).sorted().toList();
        }
        for (final Path texture : textures) {
            final BufferedImage image = ImageIO.read(texture.toFile());
            final boolean[][] visited = new boolean[image.getHeight()][image.getWidth()];
            boolean changed = false;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (visited[y][x] || alpha(image, x, y) == 0) {
                        continue;
                    }
                    final List<Integer> component = connectedComponent(image, visited, x, y);
                    final int minimumY = component.stream().mapToInt(pixel -> pixel / image.getWidth()).min().orElse(0);
                    if (minimumY >= image.getHeight() - 2 && component.size() <= 6) {
                        component.forEach(pixel -> image.setRGB(pixel % image.getWidth(), pixel / image.getWidth(), 0));
                        changed = true;
                    }
                }
            }
            if (changed) {
                ImageIO.write(image, "png", texture.toFile());
            }
        }
    }

    private static List<Integer> connectedComponent(
        final BufferedImage image,
        final boolean[][] visited,
        final int startX,
        final int startY
    ) {
        final ArrayDeque<Integer> remaining = new ArrayDeque<>();
        final List<Integer> component = new ArrayList<>();
        remaining.add(startY * image.getWidth() + startX);
        visited[startY][startX] = true;
        while (!remaining.isEmpty()) {
            final int pixel = remaining.removeFirst();
            final int x = pixel % image.getWidth();
            final int y = pixel / image.getWidth();
            component.add(pixel);
            for (int neighborY = Math.max(0, y - 1); neighborY <= Math.min(image.getHeight() - 1, y + 1); neighborY++) {
                for (int neighborX = Math.max(0, x - 1); neighborX <= Math.min(image.getWidth() - 1, x + 1); neighborX++) {
                    if (!visited[neighborY][neighborX] && alpha(image, neighborX, neighborY) > 0) {
                        visited[neighborY][neighborX] = true;
                        remaining.addLast(neighborY * image.getWidth() + neighborX);
                    }
                }
            }
        }
        return component;
    }

    private static int alpha(final BufferedImage image, final int x, final int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static BufferedImage bolineSprite() {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        drawBoline(graphics, 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage ritualKnifeSprite() {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        drawRitualKnife(graphics, 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage dustSprite(final Color[] colors) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        graphics.setColor(colors[0]);
        graphics.fillPolygon(new Polygon(new int[]{1, 3, 5, 7, 9, 11, 14, 15, 13, 3}, new int[]{13, 10, 9, 6, 8, 8, 11, 14, 15, 15}, 10));
        graphics.setColor(colors[2]);
        graphics.fillPolygon(new Polygon(new int[]{3, 5, 7, 9, 12, 14, 13, 4}, new int[]{13, 10, 8, 9, 10, 13, 14, 14}, 8));
        graphics.setColor(colors[3]);
        graphics.fillRect(5, 10, 6, 3);
        graphics.fillRect(3, 12, 3, 2);
        graphics.setColor(colors[4]);
        graphics.fillRect(7, 8, 2, 2);
        graphics.fillRect(11, 11, 2, 1);
        graphics.setColor(colors[5]);
        graphics.fillRect(8, 8, 1, 1);
        graphics.fillRect(5, 11, 1, 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage coffinSprite(final SpritePalette palette, final boolean vampiric) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        graphics.setColor(palette.outline());
        graphics.fillPolygon(new Polygon(new int[]{5, 11, 14, 13, 10, 6, 3, 2}, new int[]{1, 1, 5, 12, 15, 15, 12, 5}, 8));
        graphics.setColor(palette.shadow());
        graphics.fillPolygon(new Polygon(new int[]{6, 10, 12, 12, 9, 7, 4, 4}, new int[]{2, 2, 5, 11, 14, 14, 11, 5}, 8));
        graphics.setColor(palette.mid());
        graphics.fillPolygon(new Polygon(new int[]{7, 10, 11, 10, 8, 5, 5}, new int[]{3, 3, 6, 11, 13, 10, 5}, 7));
        graphics.setColor(palette.bright());
        graphics.fillRect(6, 4, 4, 1);
        graphics.fillRect(5, 10, 6, 1);
        graphics.setColor(vampiric ? new Color(0xC52E45) : palette.highlight());
        graphics.fillRect(7, 5, 2, 7);
        graphics.fillRect(5, 7, 6, 2);
        graphics.setColor(palette.light());
        graphics.fillRect(6, 3, 3, 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage plankSprite(final SpritePalette palette) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        drawBoard(graphics, 3, 2, 11, palette, 0);
        drawBoard(graphics, 2, 6, 12, palette, 2);
        drawBoard(graphics, 1, 10, 13, palette, 4);
        graphics.dispose();
        return image;
    }

    private static void drawBoard(
        final Graphics2D graphics,
        final int x,
        final int y,
        final int width,
        final SpritePalette palette,
        final int grainOffset
    ) {
        graphics.setColor(palette.outline());
        graphics.fillPolygon(new Polygon(
            new int[]{x + 1, x + width - 2, x + width, x + width, x + width - 2, x + 1, x, x},
            new int[]{y, y, y + 1, y + 3, y + 4, y + 4, y + 3, y + 1},
            8
        ));
        graphics.setColor(palette.mid());
        graphics.fillRect(x + 1, y + 1, width - 2, 3);
        graphics.setColor(palette.bright());
        graphics.fillRect(x + 2, y + 1, width - 4, 1);
        graphics.setColor(palette.shadow());
        graphics.fillRect(x + width - 2, y + 1, 1, 3);
        graphics.setColor(palette.highlight());
        graphics.fillRect(x + 2 + grainOffset % Math.max(1, width - 6), y + 2, 3, 1);
        graphics.setColor(palette.light());
        graphics.fillRect(x + width - 3, y + 1, 1, 1);
    }

    private static BufferedImage slippersSprite(final SpritePalette palette) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        drawSlipper(graphics, 1, 2, palette);
        drawSlipper(graphics, 8, 1, palette);
        graphics.dispose();
        return image;
    }

    private static void drawSlipper(final Graphics2D graphics, final int x, final int y, final SpritePalette palette) {
        graphics.setColor(palette.outline());
        graphics.fillPolygon(new Polygon(
            new int[]{x + 2, x + 5, x + 6, x + 6, x + 4, x + 1, x},
            new int[]{y, y + 1, y + 6, y + 10, y + 12, y + 12, y + 9},
            7
        ));
        graphics.setColor(palette.mid());
        graphics.fillRect(x + 2, y + 2, 3, 6);
        graphics.fillRect(x + 1, y + 8, 4, 3);
        graphics.setColor(palette.bright());
        graphics.fillRect(x + 3, y + 2, 2, 5);
        graphics.setColor(palette.light());
        graphics.fillRect(x + 2, y + 9, 3, 1);
    }

    private static BufferedImage gobletSprite(final boolean filled, final boolean metal) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        final Color outline = metal ? new Color(0x332819) : new Color(0x20313C);
        final Color body = metal ? new Color(0xB58A42) : new Color(0x8E, 0xC9, 0xD8, 190);
        final Color highlight = metal ? new Color(0xF3D886) : new Color(0xDD, 0xFB, 0xFF, 230);
        graphics.setColor(outline);
        graphics.fillRect(3, 2, 10, 2);
        graphics.fillRect(3, 3, 2, 5);
        graphics.fillRect(11, 3, 2, 5);
        graphics.fillRect(5, 7, 6, 2);
        graphics.fillRect(7, 8, 2, 5);
        graphics.fillRect(4, 12, 8, 2);
        graphics.setColor(body);
        graphics.fillRect(5, 4, 6, 3);
        graphics.fillRect(6, 7, 4, 1);
        graphics.fillRect(8, 9, 1, 3);
        graphics.fillRect(5, 12, 6, 1);
        if (filled) {
            graphics.setColor(metal ? new Color(0x7D2434) : new Color(0x4C, 0x9B, 0x68, 220));
            graphics.fillRect(5, 5, 6, 2);
            graphics.fillRect(6, 7, 4, 1);
        }
        graphics.setColor(highlight);
        graphics.fillRect(5, 3, 2, 1);
        graphics.fillRect(5, 4, 1, 2);
        graphics.dispose();
        return image;
    }

    private static BufferedImage ingredientChaliceSprite(final boolean filled) {
        final BufferedImage image = gobletSprite(filled, true);
        image.setRGB(8, 10, new Color(filled ? 0xE8B866 : 0x7D5AC0).getRGB());
        return image;
    }

    private static BufferedImage chalkSprite(final Color body, final Color highlight, final boolean charged) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        graphics.setColor(new Color(0x15151A));
        graphics.fillPolygon(new Polygon(new int[]{2, 4, 14, 13, 11, 1}, new int[]{11, 13, 4, 2, 1, 10}, 6));
        graphics.setColor(body);
        graphics.fillPolygon(new Polygon(new int[]{3, 5, 13, 12, 10, 2}, new int[]{10, 12, 4, 3, 2, 10}, 6));
        graphics.setColor(highlight);
        graphics.drawLine(4, 10, 11, 3);
        if (charged) {
            graphics.setColor(new Color(0x76E3D6));
            graphics.fillRect(7, 7, 2, 1);
            graphics.fillRect(8, 6, 1, 3);
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage glyphItemSprite(final Color base, final Color highlight, final GlyphMotif motif) {
        final BufferedImage image = glyphTexture(base, highlight, motif);
        final Graphics2D graphics = pixelGraphics(image);
        graphics.setColor(new Color(0x17151D));
        graphics.drawRect(1, 1, 13, 13);
        graphics.dispose();
        return image;
    }

    private static BufferedImage glyphTexture(final Color base, final Color highlight, final GlyphMotif motif) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        final Color shadow = new Color(
            Math.max(0, base.getRed() / 2),
            Math.max(0, base.getGreen() / 2),
            Math.max(0, base.getBlue() / 2)
        );
        graphics.setColor(shadow);
        graphics.fillRect(6, 0, 4, 16);
        graphics.fillRect(0, 6, 16, 4);
        graphics.drawOval(2, 2, 11, 11);
        graphics.setColor(base);
        graphics.fillRect(7, 0, 2, 16);
        graphics.fillRect(0, 7, 16, 2);
        graphics.drawOval(3, 3, 9, 9);
        graphics.setColor(highlight);
        graphics.fillRect(7, 0, 1, 4);
        graphics.fillRect(12, 7, 4, 1);
        drawGlyphMotif(graphics, motif);
        graphics.dispose();
        return image;
    }

    private static void drawGlyphMotif(final Graphics2D graphics, final GlyphMotif motif) {
        switch (motif) {
            case HEART -> {
                graphics.fillRect(5, 5, 2, 2);
                graphics.fillRect(9, 5, 2, 2);
                graphics.fillRect(6, 6, 4, 4);
                graphics.fillRect(7, 10, 2, 2);
            }
            case RITUAL -> {
                graphics.drawRect(5, 5, 5, 5);
                graphics.fillRect(7, 4, 2, 8);
            }
            case INFERNAL -> {
                graphics.drawLine(5, 5, 10, 10);
                graphics.drawLine(10, 5, 5, 10);
                graphics.fillRect(7, 4, 2, 8);
            }
            case VEIL -> {
                graphics.drawLine(5, 8, 8, 5);
                graphics.drawLine(8, 5, 11, 8);
                graphics.drawLine(5, 8, 8, 11);
                graphics.drawLine(8, 11, 11, 8);
            }
        }
    }

    private static Graphics2D pixelGraphics(final BufferedImage image) {
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return graphics;
    }

    private static BufferedImage gobliniteToolSprite(final ToolShape shape) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        graphics.setColor(new Color(0x251A14));
        for (int step = 0; step < 8; step++) {
            graphics.fillRect(2 + step, 13 - step, 2, 2);
        }
        graphics.setColor(new Color(0x805333));
        for (int step = 0; step < 7; step++) {
            graphics.fillRect(3 + step, 13 - step, 1, 1);
        }
        graphics.setColor(GOBLINITE.outline());
        switch (shape) {
            case SWORD -> graphics.fillPolygon(new Polygon(
                new int[]{8, 12, 15, 15, 12, 7},
                new int[]{8, 3, 0, 3, 6, 10},
                6
            ));
            case PICKAXE -> graphics.fillPolygon(new Polygon(
                new int[]{5, 9, 13, 15, 14, 10, 6, 3, 1},
                new int[]{2, 1, 2, 4, 5, 4, 5, 4, 5},
                9
            ));
            case AXE -> graphics.fillPolygon(new Polygon(
                new int[]{7, 11, 14, 15, 13, 9, 7},
                new int[]{2, 1, 3, 6, 9, 8, 6},
                7
            ));
            case SHOVEL -> graphics.fillPolygon(new Polygon(
                new int[]{10, 13, 15, 14, 11, 8, 8},
                new int[]{1, 1, 4, 7, 9, 7, 4},
                7
            ));
            case HOE -> graphics.fillPolygon(new Polygon(
                new int[]{8, 12, 15, 15, 12, 8},
                new int[]{1, 1, 3, 5, 5, 4},
                6
            ));
        }
        graphics.setColor(GOBLINITE.mid());
        switch (shape) {
            case SWORD -> graphics.fillPolygon(new Polygon(new int[]{9, 12, 14, 13, 8}, new int[]{8, 3, 2, 5, 10}, 5));
            case PICKAXE -> {
                graphics.fillRect(5, 2, 8, 2);
                graphics.fillRect(3, 3, 3, 1);
            }
            case AXE -> graphics.fillPolygon(new Polygon(new int[]{8, 11, 13, 14, 12, 8}, new int[]{3, 2, 3, 5, 7, 6}, 6));
            case SHOVEL -> graphics.fillPolygon(new Polygon(new int[]{10, 13, 14, 13, 11, 9}, new int[]{2, 2, 4, 7, 8, 6}, 6));
            case HOE -> graphics.fillRect(9, 2, 5, 2);
        }
        graphics.setColor(GOBLINITE.highlight());
        switch (shape) {
            case SWORD -> graphics.drawLine(11, 6, 14, 2);
            case PICKAXE -> graphics.drawLine(6, 2, 13, 3);
            case AXE -> graphics.drawLine(10, 3, 13, 4);
            case SHOVEL -> graphics.fillRect(11, 3, 2, 2);
            case HOE -> graphics.fillRect(10, 2, 3, 1);
        }
        graphics.setColor(GOBLINITE.light());
        graphics.fillRect(8, 7, 1, 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage gobliniteArmorSprite(final ArmorShape shape) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        graphics.setColor(GOBLINITE.outline());
        switch (shape) {
            case HELMET -> {
                graphics.fillRect(3, 3, 10, 9);
                graphics.fillRect(2, 7, 12, 6);
            }
            case CHESTPLATE -> {
                graphics.fillPolygon(new Polygon(new int[]{4, 1, 5, 11, 15, 12, 12, 4}, new int[]{2, 5, 7, 7, 5, 2, 15, 15}, 8));
                graphics.fillRect(1, 5, 4, 7);
                graphics.fillRect(11, 5, 4, 7);
            }
            case LEGGINGS -> {
                graphics.fillRect(3, 2, 10, 7);
                graphics.fillRect(3, 7, 4, 8);
                graphics.fillRect(9, 7, 4, 8);
            }
            case BOOTS -> {
                graphics.fillRect(2, 4, 5, 8);
                graphics.fillRect(1, 10, 7, 4);
                graphics.fillRect(9, 4, 5, 8);
                graphics.fillRect(8, 10, 7, 4);
            }
        }
        graphics.setColor(GOBLINITE.mid());
        switch (shape) {
            case HELMET -> {
                graphics.fillRect(4, 4, 8, 5);
                graphics.fillRect(3, 9, 3, 2);
                graphics.fillRect(10, 9, 3, 2);
            }
            case CHESTPLATE -> {
                graphics.fillRect(5, 4, 6, 10);
                graphics.fillRect(2, 6, 3, 5);
                graphics.fillRect(11, 6, 3, 5);
            }
            case LEGGINGS -> {
                graphics.fillRect(4, 3, 8, 5);
                graphics.fillRect(4, 8, 2, 6);
                graphics.fillRect(10, 8, 2, 6);
            }
            case BOOTS -> {
                graphics.fillRect(3, 5, 3, 6);
                graphics.fillRect(2, 11, 5, 2);
                graphics.fillRect(10, 5, 3, 6);
                graphics.fillRect(9, 11, 5, 2);
            }
        }
        graphics.setColor(GOBLINITE.bright());
        switch (shape) {
            case HELMET -> graphics.fillRect(5, 4, 6, 2);
            case CHESTPLATE -> {
                graphics.fillRect(6, 4, 4, 2);
                graphics.fillRect(7, 6, 2, 6);
            }
            case LEGGINGS -> graphics.fillRect(5, 3, 6, 2);
            case BOOTS -> {
                graphics.fillRect(4, 5, 1, 5);
                graphics.fillRect(11, 5, 1, 5);
            }
        }
        graphics.setColor(GOBLINITE.highlight());
        graphics.fillRect(shape == ArmorShape.BOOTS ? 3 : 6, shape == ArmorShape.LEGGINGS ? 4 : 5, 2, 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage gobliniteIngotSprite(final boolean nugget) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        if (nugget) {
            graphics.setColor(GOBLINITE.outline());
            graphics.fillPolygon(new Polygon(new int[]{4, 7, 12, 13, 10, 5, 3}, new int[]{7, 4, 5, 9, 12, 12, 9}, 7));
            graphics.setColor(GOBLINITE.mid());
            graphics.fillPolygon(new Polygon(new int[]{5, 8, 11, 11, 9, 5, 4}, new int[]{7, 5, 6, 9, 11, 11, 9}, 7));
            graphics.setColor(GOBLINITE.highlight());
            graphics.fillRect(7, 6, 4, 2);
        } else {
            graphics.setColor(GOBLINITE.outline());
            graphics.fillPolygon(new Polygon(new int[]{3, 6, 13, 15, 12, 4, 1}, new int[]{5, 3, 4, 9, 12, 12, 9}, 7));
            graphics.setColor(GOBLINITE.mid());
            graphics.fillPolygon(new Polygon(new int[]{4, 7, 12, 13, 11, 4, 3}, new int[]{6, 4, 5, 9, 11, 11, 9}, 7));
            graphics.setColor(GOBLINITE.highlight());
            graphics.fillRect(6, 5, 6, 2);
            graphics.setColor(GOBLINITE.light());
            graphics.fillRect(7, 5, 3, 1);
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage gobliniteMineralBlockSprite(final boolean raw) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        final Polygon outline = new Polygon(
            new int[]{2, 8, 14, 14, 8, 2},
            new int[]{4, 1, 4, 12, 15, 12},
            6
        );
        graphics.setColor(raw ? GOBLINITE.outline() : new Color(0x171A20));
        graphics.fillPolygon(outline);
        graphics.setColor(raw ? GOBLINITE.bright() : new Color(0x424955));
        graphics.fillPolygon(new Polygon(new int[]{3, 8, 13, 8}, new int[]{4, 2, 4, 7}, 4));
        graphics.setColor(raw ? GOBLINITE.mid() : new Color(0x303640));
        graphics.fillPolygon(new Polygon(new int[]{3, 8, 8, 3}, new int[]{5, 8, 14, 11}, 4));
        graphics.setColor(raw ? GOBLINITE.shadow() : new Color(0x252A32));
        graphics.fillPolygon(new Polygon(new int[]{9, 13, 13, 9}, new int[]{8, 5, 11, 14}, 4));
        if (raw) {
            graphics.setColor(GOBLINITE.highlight());
            graphics.fillRect(5, 4, 3, 2);
            graphics.fillRect(4, 8, 3, 2);
            graphics.fillRect(9, 9, 3, 2);
        } else {
            graphics.setColor(GOBLINITE.highlight());
            graphics.fillRect(7, 3, 2, 2);
            graphics.fillRect(4, 7, 2, 2);
            graphics.fillRect(10, 8, 2, 2);
            graphics.setColor(GOBLINITE.light());
            graphics.fillRect(8, 4, 1, 1);
            graphics.fillRect(5, 7, 1, 1);
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage compassSprite(final int frame, final int frameCount, final boolean shelfCompass) {
        final BufferedImage image = image(16, 16);
        final Graphics2D graphics = pixelGraphics(image);
        final Color outline = new Color(0x12131B);
        final Color rim = shelfCompass ? new Color(0x6A5B8D) : new Color(0xA86D28);
        final Color rimLight = shelfCompass ? new Color(0xC1A4EE) : new Color(0xE6B654);
        graphics.setColor(outline);
        graphics.fillOval(0, 0, 16, 16);
        graphics.setComposite(java.awt.AlphaComposite.Clear);
        graphics.fillOval(2, 2, 12, 12);
        graphics.setComposite(java.awt.AlphaComposite.SrcOver);
        graphics.setColor(rim);
        graphics.drawOval(1, 1, 13, 13);
        graphics.drawOval(2, 2, 11, 11);
        graphics.setColor(rimLight);
        graphics.fillRect(7, 1, 2, 2);
        graphics.fillRect(13, 7, 2, 2);
        graphics.setColor(new Color(0x181A24));
        graphics.fillOval(3, 3, 10, 10);
        graphics.setColor(shelfCompass ? new Color(0xB45DE2) : new Color(0x67D5D0));
        final double angle = frame * Math.PI * 2.0 / frameCount - Math.PI / 2.0;
        final int tipX = 8 + (int) Math.round(Math.cos(angle) * 5.0);
        final int tipY = 8 + (int) Math.round(Math.sin(angle) * 5.0);
        graphics.drawLine(8, 8, tipX, tipY);
        graphics.setColor(shelfCompass
            ? new Color(0x5B, 0x70 + frame * 4, 0xE0 - frame * 3)
            : new Color(0xC6, 0x28 + frame, 0x48 + frame));
        final int tailX = 8 - (int) Math.round(Math.cos(angle) * 3.0);
        final int tailY = 8 - (int) Math.round(Math.sin(angle) * 3.0);
        graphics.drawLine(8, 8, tailX, tailY);
        graphics.fillRect(7, 7, 2, 2);
        if (shelfCompass) {
            graphics.setColor(new Color(0xE9DAA8));
            graphics.fillRect(4, 4, 1, 2);
            graphics.fillRect(11, 10, 1, 2);
        }
        graphics.dispose();
        return image;
    }

    private enum ToolShape {
        SWORD,
        AXE,
        PICKAXE,
        SHOVEL,
        HOE
    }

    private enum ArmorShape {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS
    }

    private enum ContractMark {
        SEAL,
        FLAME,
        HAND,
        SHIELD,
        INGOT,
        CHAIN
    }

    private enum BoltKind {
        SPLITTING,
        WOOD,
        BONE,
        SILVER
    }

    private enum GlyphMotif {
        HEART,
        RITUAL,
        INFERNAL,
        VEIL
    }

    private enum LabelMotif {
        DROP,
        HEART,
        FLAME,
        SPIRIT,
        HOUSE,
        VEIL,
        CHAIN,
        WORLD,
        EYE,
        VOID,
        SKULL,
        MOON,
        SUN,
        ACID,
        MOUTH,
        CROWN,
        SPROUT,
        LUCK,
        FUME,
        WING,
        HORN,
        GEM,
        WAVE
    }

    private record SpritePalette(
        Color outline,
        Color shadow,
        Color mid,
        Color bright,
        Color highlight,
        Color light
    ) {
        private SpritePalette(final Color[] colors) {
            this(colors[0], colors[1], colors[2], colors[3], colors[4], colors[5]);
        }

        private Color[] colors() {
            return new Color[]{outline, shadow, mid, bright, highlight, light};
        }
    }

    private static void writeHunterArmorTextures(final Path textureRoot) throws IOException {
        final List<String> layers = List.of("humanoid", "humanoid_baby", "humanoid_leggings");
        final List<String> variants = List.of("werewolf_hunter", "werewolf_hunter_silvered", "werewolf_hunter_dawn");
        for (final String layer : layers) {
            final Path directory = textureRoot.resolve("entity/equipment").resolve(layer);
            Files.createDirectories(directory);
            for (final String variant : variants) {
                ImageIO.write(hunterArmorTexture(variant, 64, 32), "png", directory.resolve(variant + ".png").toFile());
            }
        }
    }

    private static BufferedImage hunterArmorTexture(final String variant, final int width, final int height) {
        final BufferedImage image = image(width, height);
        final Graphics2D graphics = image.createGraphics();
        final Color leather = variant.endsWith("dawn") ? new Color(0x78352C) : new Color(0x4A3025);
        final Color leatherLight = variant.endsWith("dawn") ? new Color(0xA7563D) : new Color(0x76503A);
        final Color trim = variant.endsWith("silvered")
            ? new Color(0xC5D1D9)
            : variant.endsWith("dawn") ? new Color(0xD7AD55) : new Color(0x9A7043);
        graphics.setColor(new Color(0x1B1718));
        graphics.fillRect(0, 0, width, height);
        graphics.setComposite(java.awt.AlphaComposite.Clear);
        graphics.fillRect(24, 0, 16, 8);
        graphics.fillRect(56, 0, 8, 32);
        graphics.setComposite(java.awt.AlphaComposite.SrcOver);
        graphics.setColor(leather);
        graphics.fillRect(0, 8, 24, 16);
        graphics.fillRect(16, 20, 40, 12);
        graphics.setColor(leatherLight);
        graphics.fillRect(2, 10, 20, 5);
        graphics.fillRect(18, 22, 36, 5);
        graphics.setColor(trim);
        graphics.fillRect(10, 8, 3, 16);
        graphics.fillRect(31, 20, 3, 12);
        graphics.fillRect(16, 27, 40, 2);
        graphics.setColor(new Color(0xD8D0B5));
        graphics.fillRect(6, 16, 3, 3);
        graphics.fillRect(15, 16, 3, 3);
        graphics.dispose();
        return image;
    }

    private static BufferedImage soaringEffectTexture() {
        final BufferedImage image = image(18, 18);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x18202A));
        graphics.fillRect(7, 3, 4, 12);
        graphics.setColor(new Color(0x9B6A3E));
        graphics.fillRect(8, 4, 2, 10);
        graphics.setColor(new Color(0xC6EEF0));
        graphics.fillRect(2, 5, 5, 2);
        graphics.fillRect(3, 3, 3, 2);
        graphics.fillRect(4, 7, 3, 2);
        graphics.fillRect(11, 5, 5, 2);
        graphics.fillRect(12, 3, 3, 2);
        graphics.fillRect(11, 7, 3, 2);
        graphics.setColor(new Color(0x65D5C1));
        graphics.fillRect(6, 14, 6, 2);
        graphics.dispose();
        return image;
    }

    private static List<Path> visibleTextures(
        final Path root,
        final Path textureRoot,
        final Path entityRoot
    ) throws IOException {
        final Set<Path> textures = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(textureRoot)) {
            files.filter(path -> path.toString().endsWith(".png"))
                .filter(path -> !path.getParent().equals(entityRoot))
                .forEach(textures::add);
        }
        final Pattern reference = Pattern.compile("warlockery:(block|item)/([a-z0-9_./-]+)");
        final Path modelRoot = root.resolve("src/main/resources/assets/warlockery/models");
        try (Stream<Path> files = Files.walk(modelRoot)) {
            files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                try {
                    final String json = Files.readString(path);
                    final int texturesIndex = json.indexOf("\"textures\"");
                    if (texturesIndex < 0) {
                        return;
                    }
                    final Matcher matcher = reference.matcher(json.substring(texturesIndex));
                    while (matcher.find()) {
                        textures.add(textureRoot.resolve(matcher.group(1)).resolve(matcher.group(2) + ".png"));
                    }
                } catch (final IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
        }
        return textures.stream().sorted().toList();
    }

    private static Set<String> blockItemIds(final Path root) throws IOException {
        final Path definitions = root.resolve("src/main/resources/assets/warlockery/items");
        final Path blockStates = root.resolve("src/main/resources/assets/warlockery/blockstates");
        try (Stream<Path> files = Files.list(definitions)) {
            return files.filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .filter(id -> Files.isRegularFile(blockStates.resolve(id + ".json")))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private static int[] dimensions(final Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new int[]{16, 16};
        }
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
        final BufferedImage semantic = semanticBlockTexture(name.toLowerCase(), width, height);
        if (semantic != null) {
            return semantic;
        }
        final Color[] palette = palette(name);
        final String lower = name.toLowerCase();
        if (lower.contains("door") && (lower.contains("bottom") || lower.contains("top"))) {
            return doorTexture(name, width, height, palette);
        }
        if (lower.contains("sapling")) {
            return saplingTexture(name, width, height, palette);
        }
        if (lower.contains("leaves")) {
            return leavesTexture(name, width, height, palette);
        }
        final BufferedImage image = image(width, height);
        final RandomGenerator random = random(name);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int grain = Math.floorMod(x * 7 + y * 11 + name.hashCode(), 13);
                final int tone = grain < 2 ? 1 : grain < 6 ? 2 : 3;
                image.setRGB(x, y, palette[tone].getRGB());
            }
        }
        final Graphics2D graphics = image.createGraphics();
        if (lower.contains("plank") || lower.contains("wood") || lower.contains("coffin") || lower.contains("stockade")) {
            drawWood(graphics, width, height, palette, random);
        } else if (lower.contains("ore")) {
            drawOre(graphics, width, height, palette, random);
        } else if (lower.contains("grass") || lower.contains("leaf") || lower.contains("vine") || lower.contains("bramble")) {
            drawFoliage(graphics, width, height, palette, random);
        } else if (lower.contains("portal") || lower.contains("force") || lower.contains("light") || lower.contains("spirit")) {
            drawPortal(graphics, width, height, palette, random);
        } else if (lower.contains("statue") || lower.contains("altar") || lower.contains("pentacle")) {
            drawRunes(graphics, width, height, palette, random);
        } else if (lower.contains("glass") || lower.contains("mirror") || lower.contains("ice")) {
            drawGlass(graphics, width, height, palette);
        } else {
            drawMasonry(graphics, width, height, palette, random);
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage semanticBlockTexture(final String name, final int width, final int height) {
        final String id = name.replaceFirst("\\.png$", "");
        final boolean customMaterial = id.startsWith("altar") || id.startsWith("scarecrow")
            || id.startsWith("wicker") || id.startsWith("distillery") || id.startsWith("fumefunnel")
            || id.startsWith("filteredfumefunnel") || id.startsWith("daylightcollector")
            || id.startsWith("spinningwheel") || id.startsWith("paradox_egg")
            || id.startsWith("critter_snare") || id.startsWith("garlicgarland")
            || id.startsWith("mirror") || id.startsWith("shadedglass")
            || id.startsWith("artichoke_stage") || id.startsWith("dreamroot_stage")
            || id.startsWith("garlic_stage") || id.startsWith("mandrake_stage");
        if (!id.equals("pitdirt") && !id.equals("pitgrass_top") && !id.equals("pitgrass_side")
            && !id.startsWith("alluring_skull") && !id.startsWith("crystalball")
            && !id.startsWith("wolfhead") && !customMaterial) {
            return null;
        }
        final BufferedImage image = image(width, height);
        final Graphics2D graphics = image.createGraphics();
        final RandomGenerator random = random(id);
        if (id.startsWith("artichoke_stage") || id.startsWith("dreamroot_stage")
            || id.startsWith("garlic_stage") || id.startsWith("mandrake_stage")) {
            drawCropStageTexture(graphics, id, width, height);
        } else if (id.startsWith("altar")) {
            drawAltarMaterial(image, graphics, id, random);
        } else if (id.startsWith("scarecrow")) {
            drawScarecrowMaterial(image, graphics, id, random);
        } else if (id.startsWith("wicker")) {
            drawWickerMaterial(image, graphics, id, random);
        } else if (id.startsWith("distillery")) {
            drawDistilleryMaterial(image, graphics, id, random);
        } else if (id.startsWith("fumefunnel") || id.startsWith("filteredfumefunnel")) {
            drawFumeFunnelMaterial(image, graphics, id, random);
        } else if (id.startsWith("daylightcollector")) {
            drawDaylightCollectorMaterial(image, graphics, id, random);
        } else if (id.startsWith("spinningwheel")) {
            drawSpinningWheelMaterial(image, graphics, id, random);
        } else if (id.startsWith("paradox_egg")) {
            drawParadoxMaterial(image, graphics, id, random);
        } else if (id.startsWith("critter_snare")) {
            drawCritterSnareMaterial(image, graphics, id, random);
        } else if (id.startsWith("garlicgarland")) {
            drawGarlicGarlandMaterial(image, graphics, id, random);
        } else if (id.startsWith("mirror")) {
            drawMirrorMaterial(image, graphics, id, random);
        } else if (id.startsWith("shadedglass")) {
            drawShadedGlassMaterial(image, graphics, id);
        } else if (id.equals("pitdirt") || id.equals("pitgrass_side")) {
            final Color[] dirt = colors("2A1D16", "493223", "68462D", "805A36", "A27646", "B78B56", "D2AD74");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, dirt[2 + random.nextInt(3)].getRGB());
                }
            }
            graphics.setColor(dirt[1]);
            for (int index = 0; index < 10; index++) {
                graphics.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
            }
            if (id.equals("pitgrass_side")) {
                final Color[] grass = colors("142019", "24432A", "376A38", "4F873F", "70A94B", "92C65C", "C1DF7C");
                graphics.setColor(grass[3]);
                graphics.fillRect(0, 0, width, Math.max(2, height / 4));
                for (int x = 0; x < width; x++) {
                    final int fringe = 2 + Math.floorMod(x * 5 + id.hashCode(), 3);
                    graphics.fillRect(x, 0, 1, Math.min(height, fringe));
                }
                graphics.setColor(grass[5]);
                for (int index = 0; index < 8; index++) {
                    graphics.fillRect(random.nextInt(width), random.nextInt(Math.max(1, height / 4)), 1, 1);
                }
            }
        } else if (id.equals("pitgrass_top")) {
            final Color[] grass = colors("142019", "24432A", "376A38", "4F873F", "70A94B", "92C65C", "C1DF7C");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, grass[2 + random.nextInt(3)].getRGB());
                }
            }
            graphics.setColor(grass[5]);
            for (int index = 0; index < 18; index++) {
                graphics.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
            }
        } else if (id.startsWith("alluring_skull")) {
            final Color[] bone = colors("26231E", "5E584A", "8C836C", "B7AD91", "D8D0B5", "EEE8D1", "77DD52");
            final Color base = id.endsWith("eye") ? bone[6] : bone[4];
            graphics.setColor(base);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(id.endsWith("eye") ? new Color(0xB5F47A) : bone[2]);
            for (int index = 0; index < 20; index++) {
                graphics.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
            }
        } else if (id.startsWith("crystalball")) {
            final Color base = id.endsWith("base") ? new Color(0x4B2C25) : new Color(0x6247A4);
            graphics.setColor(base);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(id.endsWith("base") ? new Color(0x9A6A42) : new Color(0x9C79E6));
            for (int index = 0; index < 18; index++) {
                graphics.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
            }
            if (!id.endsWith("base")) {
                graphics.setColor(new Color(0xC7F4F1));
                graphics.fillRect(width / 4, height / 5, Math.max(1, width / 6), Math.max(2, height / 3));
            }
        } else {
            final Color base = id.endsWith("eye") ? new Color(0xB6EAF2) : new Color(0x535C61);
            graphics.setColor(base);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(id.endsWith("eye") ? new Color(0x50778B) : new Color(0x30373B));
            for (int index = 0; index < 20; index++) {
                graphics.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
            }
        }
        graphics.dispose();
        return image;
    }

    private static void fillMaterial(
        final BufferedImage image,
        final RandomGenerator random,
        final Color[] material
    ) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int index = 1 + random.nextInt(Math.max(1, material.length - 2));
                image.setRGB(x, y, material[index].getRGB());
            }
        }
    }

    private static void drawCropStageTexture(
        final Graphics2D graphics,
        final String id,
        final int width,
        final int height
    ) {
        final int stage = Character.digit(id.charAt(id.length() - 1), 10);
        final int growth = Math.max(3, Math.min(height - 2, 3 + stage * 2));
        final Color stem = id.startsWith("dreamroot") ? new Color(0x5267A7)
            : id.startsWith("garlic") ? new Color(0x6D9A4A)
            : id.startsWith("mandrake") ? new Color(0x738A42) : new Color(0x5A8B42);
        graphics.setColor(stem.darker());
        graphics.fillRect(width / 2 - 1, height - growth, 2, growth);
        graphics.setColor(stem);
        for (int y = height - growth + 2; y < height - 2; y += 3) {
            graphics.fillRect(Math.max(0, width / 2 - 4), y, 4, 2);
            graphics.fillRect(width / 2 + 1, Math.max(0, y - 1), 4, 2);
        }
        if (stage >= 3) {
            final Color bloom = id.startsWith("dreamroot") ? new Color(0xB6A4EA)
                : id.startsWith("garlic") ? new Color(0xE9E5C8)
                : id.startsWith("mandrake") ? new Color(0xB98755) : new Color(0x8EBC69);
            graphics.setColor(bloom);
            graphics.fillRect(width / 2 - 3, height - growth - 1, 6, 3);
            graphics.fillRect(width / 2 - 1, height - growth - 3, 2, 7);
        }
    }

    private static void drawAltarMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        final Color[] material = id.contains("wood") ? colors("25170F", "4A2B1B", "6D4328", "8C6039", "BA8C56")
            : id.contains("wax") ? colors("4A3520", "9E7A45", "C8A564", "E2C985", "F3E3AF")
            : id.contains("rune") ? colors("17252A", "26434B", "386371", "4E8290", "75C6C3")
            : colors("202326", "41484D", "59636A", "737D82", "A5AEB0");
        fillMaterial(image, random, material);
        graphics.setColor(material[0]);
        for (int y = 3; y < image.getHeight(); y += 5) {
            graphics.drawLine(0, y, image.getWidth() - 1, y);
        }
        graphics.setColor(material[material.length - 1]);
        if (id.contains("rune") || id.contains("top")) {
            graphics.drawOval(3, 3, Math.max(2, image.getWidth() - 7), Math.max(2, image.getHeight() - 7));
            graphics.drawLine(image.getWidth() / 2, 2, image.getWidth() / 2, image.getHeight() - 3);
        }
    }

    private static void drawScarecrowMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        final Color[] material = id.contains("straw") ? colors("3A2916", "846033", "A77E43", "C49B57", "E0C276")
            : id.contains("wood") ? colors("281910", "51301E", "70462A", "93613A", "BA8953")
            : colors("271D1B", "5D3A31", "7B5040", "9D7155", "C29C73");
        fillMaterial(image, random, material);
        graphics.setColor(material[0]);
        for (int line = 2; line < image.getHeight(); line += 4) {
            graphics.drawLine(0, line, image.getWidth() - 1, Math.min(image.getHeight() - 1, line + 2));
        }
        if (!id.contains("straw") && !id.contains("wood")) {
            graphics.setColor(new Color(0xA64A39));
            graphics.fillRect(2, image.getHeight() / 2, image.getWidth() - 4, 2);
        }
    }

    private static void drawWickerMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        final Color[] material = colors("342313", "775229", "98713A", "B88D4D", "D6B36D");
        fillMaterial(image, random, material);
        graphics.setColor(material[0]);
        for (int y = 1; y < image.getHeight(); y += 4) graphics.drawLine(0, y, image.getWidth() - 1, y);
        for (int x = 2; x < image.getWidth(); x += 4) graphics.drawLine(x, 0, x, image.getHeight() - 1);
        graphics.setColor(material[4]);
        for (int offset = 0; offset < image.getWidth(); offset += 6) {
            graphics.drawLine(offset, 0, Math.min(image.getWidth() - 1, offset + 5), 5);
        }
        if (id.contains("bloodied")) {
            graphics.setColor(new Color(0x7A2527));
            graphics.fillRect(image.getWidth() / 3, image.getHeight() / 3, image.getWidth() / 3, 2);
        }
    }

    private static void drawDistilleryMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        if (id.contains("glass")) {
            final boolean burning = id.contains("burning");
            graphics.setColor(burning ? new Color(214, 111, 45, 160) : new Color(110, 183, 190, 150));
            graphics.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1);
            graphics.setColor(burning ? new Color(255, 213, 112, 230) : new Color(191, 239, 240, 220));
            graphics.drawLine(2, image.getHeight() - 3, image.getWidth() - 3, 2);
            if (burning) {
                graphics.fillRect(image.getWidth() / 2 - 1, image.getHeight() / 2, 2, image.getHeight() / 3);
            }
            return;
        }
        final Color[] material = id.contains("burning") ? colors("2B1913", "6A3421", "93502D", "B86A37", "E49C4B")
            : colors("202327", "3E474E", "58656C", "73818A", "AEBBC0");
        fillMaterial(image, random, material);
        graphics.setColor(new Color(0xA76A37));
        graphics.fillRect(2, 3, Math.max(1, image.getWidth() - 4), 2);
        graphics.fillRect(4, 10, Math.max(1, image.getWidth() - 8), 2);
        graphics.setColor(material[4]);
        graphics.fillRect(3, 2, 2, 2);
    }

    private static void drawFumeFunnelMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        final Color[] material = colors("1D2223", "394244", "515D60", "6C797C", "A1B0B1");
        fillMaterial(image, random, material);
        graphics.setColor(material[0]);
        for (int y = 3; y < image.getHeight(); y += 5) graphics.drawLine(0, y, image.getWidth() - 1, y);
        graphics.setColor(new Color(0xA8753D));
        graphics.fillRect(2, 5, Math.max(1, image.getWidth() - 4), 2);
        if (id.contains("filter")) {
            graphics.setColor(new Color(0xD0B887));
            for (int x = 1; x < image.getWidth(); x += 3) graphics.drawLine(x, 0, x, image.getHeight() - 1);
        }
    }

    private static void drawDaylightCollectorMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        if (id.contains("lens")) {
            graphics.setColor(new Color(248, 223, 121, 115));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(255, 248, 197, 230));
            graphics.drawOval(1, 1, image.getWidth() - 3, image.getHeight() - 3);
            graphics.drawLine(3, image.getHeight() - 4, image.getWidth() - 4, 3);
            return;
        }
        final Color[] material = colors("302313", "735326", "9E7735", "C59A48", "E6C36B");
        fillMaterial(image, random, material);
        graphics.setColor(material[0]);
        graphics.drawRect(1, 1, image.getWidth() - 3, image.getHeight() - 3);
        graphics.setColor(new Color(0xF4D96F));
        graphics.fillOval(4, 4, Math.max(2, image.getWidth() - 8), Math.max(2, image.getHeight() - 8));
        graphics.setColor(new Color(0xFFF1A6));
        graphics.fillRect(image.getWidth() / 2 - 1, 2, 2, image.getHeight() - 4);
        graphics.fillRect(2, image.getHeight() / 2 - 1, image.getWidth() - 4, 2);
    }

    private static void drawSpinningWheelMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        if (id.contains("thread")) {
            graphics.setColor(new Color(0xD7D0B8));
            for (int y = 1; y < image.getHeight(); y += 3) {
                graphics.drawLine(0, y, image.getWidth() - 1, Math.min(image.getHeight() - 1, y + 2));
            }
            return;
        }
        final Color[] material = colors("281A10", "53331E", "744A2A", "96643A", "C08A50");
        fillMaterial(image, random, material);
        graphics.setColor(material[0]);
        graphics.drawOval(2, 2, image.getWidth() - 5, image.getHeight() - 5);
        graphics.drawLine(image.getWidth() / 2, 1, image.getWidth() / 2, image.getHeight() - 2);
        graphics.drawLine(1, image.getHeight() / 2, image.getWidth() - 2, image.getHeight() / 2);
    }

    private static void drawParadoxMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        final Color[] material = colors("111E23", "183942", "245B61", "347E77", "75C8A9");
        fillMaterial(image, random, material);
        graphics.setColor(new Color(0xD1B65D));
        graphics.drawLine(1, 1, image.getWidth() - 2, image.getHeight() - 2);
        graphics.drawLine(image.getWidth() - 2, 1, 1, image.getHeight() - 2);
        graphics.setColor(new Color(0xD7F1E4));
        graphics.fillRect(image.getWidth() / 2 - 1, image.getHeight() / 2 - 1, 3, 3);
        if (id.contains("rune")) {
            graphics.setColor(new Color(0xE4C75F));
            graphics.drawOval(2, 2, image.getWidth() - 5, image.getHeight() - 5);
        }
    }

    private static void drawCritterSnareMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        final Color[] material = colors("251A12", "4C3421", "6F4E2E", "916C42", "B99462");
        fillMaterial(image, random, material);
        graphics.setColor(material[0]);
        graphics.drawRect(1, 1, image.getWidth() - 3, image.getHeight() - 3);
        graphics.drawLine(1, 1, image.getWidth() - 2, image.getHeight() - 2);
        graphics.drawLine(image.getWidth() - 2, 1, 1, image.getHeight() - 2);
        if (!id.contains("empty")) {
            final Color captive = id.contains("bat") ? new Color(0x25202A)
                : id.contains("magma") ? new Color(0xD45B32)
                : id.contains("slime") ? new Color(0x6FA64B) : new Color(0xA9ADB1);
            graphics.setColor(captive);
            graphics.fillOval(4, 5, Math.max(2, image.getWidth() - 8), Math.max(2, image.getHeight() - 9));
        }
    }

    private static void drawGarlicGarlandMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        if (id.contains("cord")) {
            graphics.setColor(new Color(0x795433));
            for (int x = 0; x < image.getWidth(); x++) {
                graphics.fillRect(x, Math.floorMod(x * 3, Math.max(1, image.getHeight())), 1, 3);
            }
            return;
        }
        final Color[] material = colors("3D382E", "8C846A", "B8AF8D", "DBD2AE", "F0E9CF");
        fillMaterial(image, random, material);
        graphics.setColor(new Color(0xECE5CC));
        graphics.fillOval(2, 4, 6, 9);
        graphics.fillOval(8, 2, 6, 9);
        graphics.setColor(new Color(0x5D7A3E));
        graphics.fillRect(5, 0, 2, 5);
        graphics.fillRect(10, 0, 2, 4);
    }

    private static void drawMirrorMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id,
        final RandomGenerator random
    ) {
        if (id.contains("glass")) {
            final Color tint = id.startsWith("mirrorblock2") ? new Color(127, 108, 174, 135)
                : id.startsWith("mirrorwall") ? new Color(88, 142, 159, 125)
                : new Color(107, 159, 174, 130);
            graphics.setColor(tint);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(215, 243, 244, 220));
            graphics.drawLine(2, image.getHeight() - 3, image.getWidth() - 3, 2);
            if (id.startsWith("mirrorblock2")) {
                graphics.drawLine(2, 2, image.getWidth() - 3, image.getHeight() - 3);
            } else if (id.startsWith("mirrorwall")) {
                graphics.drawLine(1, image.getHeight() / 2, image.getWidth() - 2, image.getHeight() / 2);
            }
            return;
        }
        final Color[] material = colors("202226", "444A50", "626B73", "89949B", "CAD4D6");
        fillMaterial(image, random, material);
        graphics.setColor(material[4]);
        graphics.drawRect(1, 1, image.getWidth() - 3, image.getHeight() - 3);
        graphics.setColor(new Color(0x8FC3CE));
        graphics.fillRect(3, 3, Math.max(1, image.getWidth() - 6), Math.max(1, image.getHeight() - 6));
        graphics.setColor(new Color(0xE4F6F4));
        graphics.drawLine(4, image.getHeight() - 5, image.getWidth() - 5, 4);
    }

    private static void drawShadedGlassMaterial(
        final BufferedImage image,
        final Graphics2D graphics,
        final String id
    ) {
        final String family = id.startsWith("shadedglassoff") ? "shadedglassoff" : "shadedglass";
        final String colorName = id.substring(family.length()).replaceFirst("^_", "");
        final Color tint = switch (colorName) {
            case "white" -> new Color(0xD9E1DE);
            case "orange" -> new Color(0xD8843E);
            case "magenta" -> new Color(0xA84E9A);
            case "light_blue" -> new Color(0x63A9C8);
            case "yellow" -> new Color(0xD4B849);
            case "lime" -> new Color(0x7FAD3D);
            case "pink" -> new Color(0xD17D97);
            case "gray" -> new Color(0x656A70);
            case "silver" -> new Color(0x969FA1);
            case "cyan" -> new Color(0x3D9296);
            case "purple" -> new Color(0x774B98);
            case "blue" -> new Color(0x456AAB);
            case "brown" -> new Color(0x79543C);
            case "green" -> new Color(0x4E7E48);
            case "red" -> new Color(0xA84A4A);
            case "black" -> new Color(0x30343A);
            default -> new Color(0x4F879A);
        };
        graphics.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), id.contains("off") ? 95 : 150));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(189, 229, 230, 210));
        graphics.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1);
        graphics.drawLine(2, image.getHeight() - 3, image.getWidth() - 3, 2);
        if (id.contains("active")) {
            graphics.setColor(new Color(232, 255, 243, 225));
            graphics.drawLine(2, 2, image.getWidth() - 3, image.getHeight() - 3);
        }
    }

    private static BufferedImage itemTexture(
        final String name,
        final int width,
        final int height,
        final boolean blockItem
    ) {
        if (width > 32 || height > Math.max(64, width * 2)) {
            return entityTexture(name, Math.max(width, height));
        }
        final BufferedImage image = image(width, height);
        final Color[] palette = palette(name);
        final String lower = name.toLowerCase();
        final Graphics2D graphics = image.createGraphics();
        graphics.setBackground(TRANSPARENT);
        graphics.clearRect(0, 0, width, height);
        final int scale = Math.max(1, Math.min(width, height) / 16);
        if (blockItem) {
            drawBlockItem(graphics, name, width, height, scale, palette);
        } else if (drawSemanticItem(graphics, lower, scale, palette)) {
        } else if (lower.contains("delvealloy")) {
            drawDelvealloyItem(graphics, lower, width, height, scale);
        } else if (lower.contains("spawn_egg")) {
            drawSpawnEgg(graphics, name, width, height, scale, palette);
        } else if (lower.contains("brew") || lower.contains("potion") || lower.contains("vial")
            || lower.contains("bottle") || lower.contains("goblet") || lower.contains("jar")) {
            drawBottle(graphics, name, width, height, scale, palette);
        } else if (lower.contains("door")) {
            drawDoorItem(graphics, width, height, scale, palette);
        } else if (lower.contains("doll")) {
            drawDoll(graphics, width, height, scale, palette);
        } else if (lower.contains("book") || lower.contains("note")) {
            drawBook(graphics, width, height, scale, palette);
        } else if (isEquipment(lower)) {
            drawEquipment(graphics, lower, width, height, scale, palette);
        } else if (isTool(lower)) {
            drawTool(graphics, lower, width, height, scale, palette);
        } else if (isPlantIngredient(lower)) {
            drawHerb(graphics, name, width, height, scale, palette);
        } else {
            drawFallbackItem(graphics, name, width, height, scale, palette);
        }
        graphics.dispose();
        return image;
    }

    private static boolean drawSemanticItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        final String id = name.replaceFirst("\\.png$", "");
        if (id.equals("vbook")) {
            drawClosedBook(graphics, scale, INFERNAL, true);
            return true;
        }
        if (id.equals("ingredient_vbook_page")) {
            drawTornObservationPage(graphics, scale);
            return true;
        }
        if (id.equals("biomebook2") || id.equals("bookcauldron") || id.startsWith("ingredient_book_")) {
            drawManualBook(graphics, scale, id);
            return true;
        }
        if (id.equals("boline")) {
            drawBoline(graphics, scale);
            return true;
        }
        if (id.equals("ritual_knife")) {
            drawRitualKnife(graphics, scale);
            return true;
        }
        if (id.equals("silversword")) {
            drawSilverSword(graphics, scale);
            return true;
        }
        if (id.equals("ingredient_tormented_twine")) {
            drawTormentedTwine(graphics, scale);
            return true;
        }
        if (id.startsWith("ingredient_waystone")) {
            drawWaystone(graphics, scale, id.contains("bound"), id.contains("creature"));
            return true;
        }
        if (id.equals("ingredient_brew_soaring")) {
            drawSoaringBrew(graphics, scale);
            return true;
        }
        if (id.equals("ingredient_brew_hitchcock") || id.equals("ingredient_brew_murder_of_crows")) {
            drawFlockBrew(graphics, scale, id);
            return true;
        }
        if (id.startsWith("playercompass")) {
            drawPlayerCompass(graphics, scale, id);
            return true;
        }
        return drawReleaseSemanticItem(graphics, id, scale, palette);
    }

    private static boolean drawReleaseSemanticItem(
        final Graphics2D graphics,
        final String id,
        final int scale,
        final Color[] palette
    ) {
        switch (id) {
            case "ingredient_clay_jar_soft" -> drawClayJar(graphics, scale, false);
            case "ingredient_clay_jar" -> drawClayJar(graphics, scale, true);
            case "wolftoken", "vbat" -> drawBatToken(graphics, scale);
            case "vampirelegs" -> drawVampirePants(graphics, scale, false);
            case "vampirelegs_kilt" -> drawVampirePants(graphics, scale, true);
            case "vampirecoat", "vampirecoat_first" -> drawVampireCoat(graphics, scale, false);
            case "vampirecoat_female" -> drawVampireCoat(graphics, scale, true);
            case "vampirechaincoat", "vampirechaincoat_female" ->
                drawVampireChainCoat(graphics, scale, id.endsWith("_female"));
            case "witchhat" -> drawWarlockHat(graphics, scale);
            case "witchrobe" -> drawWarlockRobes(graphics, scale);
            case "forgewardens_girdle" -> drawForgewardenGirdle(graphics, scale);
            case "stonebrokers_quiver" -> drawStonebrokerQuiver(graphics, scale);
            case "archfiends_urn" -> drawArchfiendsUrn(graphics, scale);
            case "circletalisman" -> drawCircleTalisman(graphics, scale);
            case "hornofthehunt" -> drawHornOfTheHunt(graphics, scale);
            case "mooncharm" -> drawMoonCharm(graphics, scale);
            case "mysticbranch" -> drawMysticBranch(graphics, scale);
            case "raw_delvealloy" -> drawMetalChunk(graphics, scale, GOBLINITE.colors());
            case "raw_silver" -> drawMetalChunk(graphics, scale, colors("151A20", "394550", "637482", "9AABB7", "D9E5E9", "F7FCFC"));
            case "silver_ingot" -> drawMetalIngot(graphics, scale, colors("171C22", "495865", "7D8F9C", "B8C8D1", "E8F1F2"));
            case "canesword" -> drawCaneSword(graphics, scale);
            case "replication_staff" -> drawReplicationStaff(graphics, scale);
            case "thorn_spear" -> drawThornSpear(graphics, scale);
            case "twisting_band" -> drawTwistingBand(graphics, scale);
            case "sungrenade" -> drawSunGrenade(graphics, scale);
            case "stewraw" -> drawMeatyStew(graphics, scale);
            case "stew" -> drawCookedMeatyStew(graphics, scale);
            case "spectralstone" -> drawSpectralStone(graphics, scale);
            case "devils_tongue_charm", "silver_tongue_charm" -> drawTongueCharm(graphics, scale);
            case "replication_charge" -> drawReplicationCharge(graphics, scale);
            case "mutator" -> drawMutatingSprig(graphics, scale);
            case "mirror" -> drawHandMirror(graphics, scale);
            case "louse" -> drawLouse(graphics, scale);
            case "earmuffs" -> drawEarmuffs(graphics, scale);
            case "deathshand" -> drawDeathHand(graphics, scale);
            case "deathsfeet" -> drawDeathBoots(graphics, scale);
            case "deathscowl" -> drawDeathHood(graphics, scale);
            case "bucket_spirit", "bucketspirit" -> drawMagicBucket(graphics, scale, new Color(0x66DCCB), LabelMotif.SPIRIT);
            case "bucket_hollowtears", "buckethollowtears" -> drawMagicBucket(graphics, scale, new Color(0x8ACBE8), LabelMotif.DROP);
            case "polynesia_charm", "beast_speech_charm" -> drawBeastCharm(graphics, scale);
            case "ingredient_warm_blood" -> drawLabeledJar(graphics, scale, new Color(0xA62A38), LabelMotif.HEART);
            case "ingredient_tear_of_the_goddess" -> drawLabeledJar(graphics, scale, new Color(0x78CBE2), LabelMotif.DROP);
            case "ingredient_subdued_spirit" -> drawLabeledJar(graphics, scale, new Color(0x72D8C8), LabelMotif.SPIRIT);
            case "ingredient_subdued_spirit_village" -> drawLabeledJar(graphics, scale, new Color(0xD5B15C), LabelMotif.HOUSE);
            case "ingredient_stake" -> drawWoodenStake(graphics, scale);
            case "ingredient_spirit_of_the_veil" -> drawLabeledJar(graphics, scale, new Color(0x67B9CF), LabelMotif.VEIL);
            case "ingredient_soul_of_torment" -> drawSoulRelic(graphics, scale, new Color(0x9C47C5), LabelMotif.CHAIN);
            case "ingredient_soul_of_the_world" -> drawSoulRelic(graphics, scale, new Color(0x67B85B), LabelMotif.WORLD);
            case "ingredient_seer_stone" -> drawRunedStone(graphics, scale, new Color(0x7D5AC0), LabelMotif.EYE);
            case "ingredient_rock" -> drawRockItem(graphics, scale, new Color(0x72777C));
            case "ingredient_refined_evil" -> drawLabeledJar(graphics, scale, new Color(0x412047), LabelMotif.VOID);
            case "ingredient_reek_of_misfortune" -> drawLabeledJar(graphics, scale, new Color(0x6B7E39), LabelMotif.SKULL);
            case "ingredient_pentacle", "pentacle" -> drawPentacleItem(graphics, scale);
            case "ingredient_quicklime" -> drawPowderPile(graphics, scale, colors("35352D", "777761", "B7B78D", "E5E4BE"));
            case "ingredient_quartz_sphere" -> drawQuartzSphere(graphics, scale);
            case "ingredient_purified_milk" -> drawLabeledJar(graphics, scale, new Color(0xEEF3DC), LabelMotif.MOON);
            case "ingredient_owlets_wing" -> drawOwletWing(graphics, scale);
            case "ingredient_oil_of_vitriol" -> drawLabeledJar(graphics, scale, new Color(0xB5C944), LabelMotif.ACID);
            case "ingredient_odour_of_purity" -> drawLabeledJar(graphics, scale, new Color(0xD8F5E8), LabelMotif.SUN);
            case "ingredient_nullifiedleather" -> drawLeather(graphics, scale, new Color(0x454451), true);
            case "ingredient_nullcatalyst" -> drawNullCatalyst(graphics, scale);
            case "ingredient_necro_stone" -> drawRunedStone(graphics, scale, new Color(0x423342), LabelMotif.SKULL);
            case "ingredient_mysticunguent" -> drawLabeledJar(graphics, scale, new Color(0x8D56A8), LabelMotif.MOON);
            case "ingredient_mellifluous_hunger" -> drawLabeledJar(graphics, scale, new Color(0xA98C43), LabelMotif.MOUTH);
            case "ingredient_matriarchs_blood" -> drawLabeledJar(graphics, scale, new Color(0x7D1027), LabelMotif.CROWN);
            case "ingredient_infusion_base" -> drawBrewBottle(graphics, scale, new Color(0xA86FD0), LabelMotif.SPIRIT);
            case "ingredient_infernal_blood" -> drawLabeledJar(graphics, scale, new Color(0x9E2432), LabelMotif.FLAME);
            case "ingredient_infernal_animus" -> drawSoulRelic(graphics, scale, new Color(0xE15839), LabelMotif.FLAME);
            case "ingredient_impregnated_leather" -> drawLeather(graphics, scale, new Color(0x6E7041), false);
            case "ingredient_icy_needle" -> drawIcyNeedle(graphics, scale);
            case "ingredient_hint_of_rebirth" -> drawLabeledJar(graphics, scale, new Color(0x8DCA65), LabelMotif.SPROUT);
            case "ingredient_heartwood_splinter" -> drawHeartwoodSplinter(graphics, scale);
            case "ingredient_heartofgold" -> drawHeartItem(graphics, scale, new Color(0xE7B949), new Color(0xFFF0A2));
            case "ingredient_happenstance_oil" -> drawLabeledJar(graphics, scale, new Color(0xDBB847), LabelMotif.LUCK);
            case "ingredient_gypsum" -> drawPowderPile(graphics, scale, colors("37363A", "77747B", "C7C1CA", "F0EAF1"));
            case "ingredient_golden_thread" -> drawThreadBundle(graphics, scale, new Color(0xD8A733), new Color(0xFFE78A));
            case "ingredient_ghost_of_the_light" -> drawLabeledJar(graphics, scale, new Color(0xE8F7C1), LabelMotif.SPIRIT);
            case "ingredient_fume_filter" -> drawFumeFilter(graphics, scale);
            case "ingredient_frozen_heart" -> drawHeartItem(graphics, scale, new Color(0x4D9CB5), new Color(0xC9FAFA));
            case "ingredient_foul_fume" -> drawLabeledJar(graphics, scale, new Color(0x657B3D), LabelMotif.FUME);
            case "ingredient_fool_skull" -> drawFoolSkull(graphics, scale);
            case "hellhound_head" -> drawHellhoundHead(graphics, scale);
            case "ingredient_focused_will" -> drawFocusedWill(graphics, scale);
            case "ingredient_flying_ointment" -> drawLabeledJar(graphics, scale, new Color(0x7DBE83), LabelMotif.WING);
            case "ingredient_fanciful_thread" -> drawThreadBundle(graphics, scale, new Color(0x8A54AB), new Color(0xE7A5F2));
            case "ingredient_exhale_of_the_horned_one" -> drawLabeledJar(graphics, scale, new Color(0x8B6F47), LabelMotif.HORN);
            case "ingredient_ender_dew" -> drawLabeledJar(graphics, scale, new Color(0x8550A5), LabelMotif.EYE);
            case "ingredient_drop_of_luck" -> drawLabeledJar(graphics, scale, new Color(0x77B951), LabelMotif.LUCK);
            case "ingredient_dog_tongue" -> drawDogTongue(graphics, scale);
            case "ingredient_disturbed_cotton" -> drawDisturbedCotton(graphics, scale);
            case "ingredient_diamond_vapour" -> drawLabeledJar(graphics, scale, new Color(0x68D5D8), LabelMotif.GEM);
            case "arcane_focus" -> drawArcaneFocus(graphics, scale);
            case "diviner_water", "divinerwater" -> drawDiviner(graphics, scale, new Color(0x55BCE5));
            case "diviner_lava", "divinerlava" -> drawDiviner(graphics, scale, new Color(0xF06B36));
            case "seedswormwood" -> drawSeedPacket(graphics, scale);
            case "seedsartichoke", "seedsbelladonna", "seedsdreamroot", "seedsmandrake",
                "seedssnowbell", "seedswolfsbane" -> drawNamedSeeds(graphics, scale, id);
            case "ingredient_annointing_paste" -> drawAnointingPaste(graphics, scale);
            case "ingredient_apple_wormy" -> drawWormyApple(graphics, scale);
            case "ingredient_ash_wood" ->
                drawPowderPile(graphics, scale, colors("252525", "5E5C57", "96938B", "D6D1C5"));
            case "ingredient_bat_ball" -> drawBatBall(graphics, scale);
            case "ingredient_bat_wool" -> drawBatWool(graphics, scale);
            case "ingredient_berries_rowan" -> drawRowanBerries(graphics, scale);
            case "ingredient_broom" -> drawBroom(graphics, scale, false);
            case "ingredient_broom_enchanted" -> drawBroom(graphics, scale, true);
            case "ingredient_candelabra" -> drawCandelabra(graphics, scale);
            case "ingredient_charm_disrupted_dreams" -> drawFancifulCharm(graphics, scale);
            case "ingredient_condensed_fear" ->
                drawLabeledJar(graphics, scale, new Color(0x332541), LabelMotif.EYE);
            case "ingredient_contract" -> drawDemonicContract(graphics, scale, ContractMark.SEAL);
            case "ingredient_contract_blaze" -> drawDemonicContract(graphics, scale, ContractMark.FLAME);
            case "ingredient_contract_fiery_touch" -> drawDemonicContract(graphics, scale, ContractMark.HAND);
            case "ingredient_contract_resist_fire" -> drawDemonicContract(graphics, scale, ContractMark.SHIELD);
            case "ingredient_contract_smelting" -> drawDemonicContract(graphics, scale, ContractMark.INGOT);
            case "ingredient_contract_torment" -> drawDemonicContract(graphics, scale, ContractMark.CHAIN);
            case "ingredient_dark_cloth" -> drawDarkCloth(graphics, scale);
            case "ingredient_woven_cruor" -> drawWovenCruor(graphics, scale);
            case "ingredient_redstone_soup" -> drawRedstoneSoup(graphics, scale);
            case "ingredient_toe_of_frog" -> drawFrogToe(graphics, scale);
            case "ingredient_verdant_catalyst" -> drawVerdantCatalyst(graphics, scale, false);
            case "ingredient_verdant_catalyst_prime" -> drawVerdantCatalyst(graphics, scale, true);
            case "ingredient_bolt_splitting" -> drawBolt(graphics, scale, BoltKind.SPLITTING);
            case "ingredient_bolt_stake" -> drawBolt(graphics, scale, BoltKind.WOOD);
            case "ingredient_bolt_holy" -> drawBolt(graphics, scale, BoltKind.BONE);
            case "ingredient_bolt_silver" -> drawBolt(graphics, scale, BoltKind.SILVER);
            case "brew_endless_water", "brew_water" -> drawBrewBottle(graphics, scale, new Color(0x3F9FE0), LabelMotif.WAVE);
            case "brew_combustion", "brew_fuel" -> drawBrewBottle(graphics, scale, new Color(0xEE6637), LabelMotif.FLAME);
            case "biting_belt", "bitingbelt" -> drawBelt(graphics, scale, true);
            case "bark_belt", "barkbelt" -> drawBelt(graphics, scale, false);
            default -> {
                if (id.contains("belt") || id.contains("girdle")) {
                    drawBelt(graphics, scale, false);
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    private static void drawClayJar(final Graphics2D graphics, final int scale, final boolean fired) {
        final Color outline = fired ? new Color(0x472A20) : new Color(0x39434A);
        final Color shadow = fired ? new Color(0x7E4937) : new Color(0x67757E);
        final Color body = fired ? new Color(0xA96248) : new Color(0x82919A);
        final Color light = fired ? new Color(0xCA8060) : new Color(0xAAB5BB);
        graphics.setColor(outline);
        graphics.fillRect(5 * scale, scale, 6 * scale, 3 * scale);
        graphics.fillRect(3 * scale, 3 * scale, 10 * scale, 3 * scale);
        graphics.fillRect(2 * scale, 5 * scale, 12 * scale, 8 * scale);
        graphics.fillRect(4 * scale, 13 * scale, 8 * scale, 2 * scale);
        graphics.setColor(shadow);
        graphics.fillRect(4 * scale, 5 * scale, 8 * scale, 8 * scale);
        graphics.fillRect(5 * scale, 3 * scale, 6 * scale, 2 * scale);
        graphics.setColor(body);
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, 7 * scale);
        graphics.fillRect(5 * scale, 13 * scale, 6 * scale, scale);
        graphics.setColor(light);
        graphics.fillRect(6 * scale, 4 * scale, 4 * scale, scale);
        graphics.fillRect(5 * scale, 6 * scale, scale, 5 * scale);
        graphics.fillRect(6 * scale, 12 * scale, 4 * scale, scale);
    }

    private static void drawLabeledJar(
        final Graphics2D graphics,
        final int scale,
        final Color contents,
        final LabelMotif motif
    ) {
        drawClayJar(graphics, scale, true);
        graphics.setColor(contents.darker());
        graphics.fillRect(4 * scale, 5 * scale, 8 * scale, 3 * scale);
        graphics.setColor(contents);
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, 2 * scale);
        graphics.setColor(new Color(0xE7D29A));
        graphics.fillRect(5 * scale, 8 * scale, 6 * scale, 4 * scale);
        graphics.setColor(new Color(0x4A2B24));
        drawLabelMotif(graphics, scale, motif, 6, 8);
    }

    private static void drawBrewBottle(
        final Graphics2D graphics,
        final int scale,
        final Color liquid,
        final LabelMotif motif
    ) {
        graphics.setColor(new Color(0x18202A));
        graphics.fillRect(6 * scale, scale, 4 * scale, 4 * scale);
        graphics.fillRect(4 * scale, 4 * scale, 8 * scale, 3 * scale);
        graphics.fillRect(2 * scale, 6 * scale, 12 * scale, 7 * scale);
        graphics.fillRect(4 * scale, 13 * scale, 8 * scale, 2 * scale);
        graphics.setColor(new Color(0xD7EFF2));
        graphics.fillRect(7 * scale, 2 * scale, 2 * scale, 4 * scale);
        graphics.fillRect(4 * scale, 6 * scale, 8 * scale, scale);
        graphics.fillRect(3 * scale, 7 * scale, scale, 5 * scale);
        graphics.fillRect(12 * scale, 7 * scale, scale, 5 * scale);
        graphics.setColor(liquid.darker());
        graphics.fillRect(4 * scale, 9 * scale, 8 * scale, 4 * scale);
        graphics.setColor(liquid);
        graphics.fillRect(5 * scale, 8 * scale, 6 * scale, 4 * scale);
        graphics.setColor(new Color(0xF5E8BA));
        graphics.fillRect(6 * scale, 9 * scale, 4 * scale, 3 * scale);
        graphics.setColor(liquid.darker().darker());
        drawLabelMotif(graphics, scale, motif, 6, 9);
        graphics.setColor(new Color(0x8A5531));
        graphics.fillRect(6 * scale, scale, 4 * scale, 2 * scale);
    }

    private static void drawLabelMotif(
        final Graphics2D graphics,
        final int scale,
        final LabelMotif motif,
        final int x,
        final int y
    ) {
        switch (motif) {
            case DROP -> {
                graphics.fillRect((x + 1) * scale, y * scale, scale, scale);
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, 2 * scale);
            }
            case HEART -> {
                graphics.fillRect(x * scale, y * scale, scale, scale);
                graphics.fillRect((x + 2) * scale, y * scale, scale, scale);
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, scale);
                graphics.fillRect((x + 1) * scale, (y + 2) * scale, scale, scale);
            }
            case FLAME -> {
                graphics.fillRect((x + 1) * scale, y * scale, scale, scale);
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, 2 * scale);
            }
            case SPIRIT -> {
                graphics.fillRect(x * scale, y * scale, 3 * scale, 2 * scale);
                graphics.fillRect((x + 1) * scale, (y + 2) * scale, 2 * scale, scale);
            }
            case HOUSE -> {
                graphics.fillRect((x + 1) * scale, y * scale, scale, scale);
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, 2 * scale);
            }
            case VEIL -> {
                graphics.drawLine(x * scale, (y + 1) * scale, (x + 1) * scale, y * scale);
                graphics.drawLine((x + 1) * scale, y * scale, (x + 2) * scale, (y + 1) * scale);
                graphics.drawLine((x + 2) * scale, (y + 1) * scale, (x + 1) * scale, (y + 2) * scale);
            }
            case CHAIN -> {
                graphics.drawRect(x * scale, y * scale, scale, scale);
                graphics.drawRect((x + 1) * scale, (y + 1) * scale, scale, scale);
            }
            case WORLD -> {
                graphics.drawOval(x * scale, y * scale, 2 * scale, 2 * scale);
                graphics.fillRect((x + 1) * scale, y * scale, scale, 3 * scale);
            }
            case EYE -> {
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, scale);
                graphics.fillRect((x + 1) * scale, y * scale, scale, 3 * scale);
            }
            case VOID -> graphics.drawRect(x * scale, y * scale, 2 * scale, 2 * scale);
            case SKULL -> {
                graphics.fillRect(x * scale, y * scale, 3 * scale, 2 * scale);
                graphics.fillRect((x + 1) * scale, (y + 2) * scale, scale, scale);
            }
            case MOON -> {
                graphics.fillRect(x * scale, y * scale, 2 * scale, 3 * scale);
                graphics.setComposite(java.awt.AlphaComposite.Clear);
                graphics.fillRect((x + 1) * scale, y * scale, 2 * scale, 2 * scale);
                graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            }
            case SUN -> {
                graphics.fillRect((x + 1) * scale, y * scale, scale, 3 * scale);
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, scale);
            }
            case ACID -> {
                graphics.fillRect(x * scale, y * scale, 3 * scale, scale);
                graphics.fillRect((x + 1) * scale, (y + 1) * scale, scale, 2 * scale);
            }
            case MOUTH -> {
                graphics.fillRect(x * scale, y * scale, 3 * scale, 3 * scale);
                graphics.setColor(new Color(0xF5E8BA));
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, scale);
            }
            case CROWN -> {
                graphics.fillRect(x * scale, (y + 2) * scale, 3 * scale, scale);
                graphics.fillRect(x * scale, y * scale, scale, 2 * scale);
                graphics.fillRect((x + 2) * scale, y * scale, scale, 2 * scale);
            }
            case SPROUT -> {
                graphics.fillRect((x + 1) * scale, (y + 1) * scale, scale, 2 * scale);
                graphics.fillRect(x * scale, y * scale, 2 * scale, scale);
                graphics.fillRect((x + 2) * scale, y * scale, scale, scale);
            }
            case LUCK -> {
                graphics.fillRect(x * scale, y * scale, scale, scale);
                graphics.fillRect((x + 2) * scale, y * scale, scale, scale);
                graphics.fillRect((x + 1) * scale, (y + 1) * scale, scale, 2 * scale);
            }
            case FUME -> {
                graphics.drawLine(x * scale, (y + 2) * scale, (x + 2) * scale, y * scale);
                graphics.fillRect(x * scale, y * scale, scale, scale);
            }
            case WING -> {
                graphics.fillRect(x * scale, y * scale, 3 * scale, scale);
                graphics.fillRect(x * scale, (y + 1) * scale, 2 * scale, scale);
                graphics.fillRect(x * scale, (y + 2) * scale, scale, scale);
            }
            case HORN -> {
                graphics.drawLine(x * scale, (y + 2) * scale, (x + 1) * scale, y * scale);
                graphics.drawLine((x + 1) * scale, y * scale, (x + 2) * scale, (y + 2) * scale);
            }
            case GEM -> {
                graphics.fillRect((x + 1) * scale, y * scale, scale, scale);
                graphics.fillRect(x * scale, (y + 1) * scale, 3 * scale, scale);
                graphics.fillRect((x + 1) * scale, (y + 2) * scale, scale, scale);
            }
            case WAVE -> {
                graphics.drawLine(x * scale, (y + 1) * scale, (x + 1) * scale, y * scale);
                graphics.drawLine((x + 1) * scale, y * scale, (x + 2) * scale, (y + 1) * scale);
                graphics.fillRect(x * scale, (y + 2) * scale, 3 * scale, scale);
            }
        }
    }

    private static void drawBatToken(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2A172D));
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(new Color(0xC99746));
        graphics.drawOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0x15101C));
        graphics.fillRect(7 * scale, 6 * scale, 2 * scale, 5 * scale);
        graphics.fillPolygon(new Polygon(new int[]{7, 2, 4, 1, 7}, new int[]{7, 4, 9, 8, 11}, 5));
        graphics.fillPolygon(new Polygon(new int[]{9, 14, 12, 15, 9}, new int[]{7, 4, 9, 8, 11}, 5));
        graphics.setColor(new Color(0xD78A5A));
        graphics.fillRect(7 * scale, 6 * scale, scale, scale);
        graphics.fillRect(9 * scale, 6 * scale, scale, scale);
    }

    private static void drawVampirePants(final Graphics2D graphics, final int scale, final boolean skirted) {
        graphics.setColor(new Color(0x160E18));
        graphics.fillRect(3 * scale, 2 * scale, 10 * scale, 6 * scale);
        if (skirted) {
            graphics.fillPolygon(new Polygon(new int[]{3, 13, 12, 10, 9, 7, 6, 4}, new int[]{7, 7, 12, 12, 15, 15, 12, 12}, 8));
        } else {
            graphics.fillRect(3 * scale, 7 * scale, 4 * scale, 8 * scale);
            graphics.fillRect(9 * scale, 7 * scale, 4 * scale, 8 * scale);
        }
        graphics.setColor(new Color(0x3D233F));
        graphics.fillRect(4 * scale, 3 * scale, 8 * scale, 3 * scale);
        graphics.fillRect(4 * scale, 8 * scale, 2 * scale, 5 * scale);
        graphics.fillRect(10 * scale, 8 * scale, 2 * scale, 5 * scale);
        graphics.setColor(new Color(0xB33B50));
        graphics.fillRect(4 * scale, 6 * scale, 8 * scale, scale);
        graphics.setColor(new Color(0xD8B66A));
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 2 * scale);
    }

    private static void drawVampireCoat(
        final Graphics2D graphics,
        final int scale,
        final boolean fitted
    ) {
        graphics.setColor(new Color(0x120C13));
        graphics.fillRect((fitted ? 5 : 4) * scale, 2 * scale, (fitted ? 6 : 8) * scale, 10 * scale);
        graphics.fillRect(2 * scale, 4 * scale, 3 * scale, 7 * scale);
        graphics.fillRect(11 * scale, 4 * scale, 3 * scale, 7 * scale);
        if (fitted) {
            graphics.fillPolygon(new Polygon(
                new int[]{5 * scale, 11 * scale, 13 * scale, 9 * scale, 8 * scale, 7 * scale, 3 * scale},
                new int[]{9 * scale, 9 * scale, 15 * scale, 15 * scale, 12 * scale, 15 * scale, 15 * scale},
                7
            ));
        } else {
            graphics.fillRect(3 * scale, 10 * scale, 4 * scale, 5 * scale);
            graphics.fillRect(9 * scale, 10 * scale, 4 * scale, 5 * scale);
        }
        graphics.setColor(new Color(0x43202F));
        graphics.fillRect((fitted ? 6 : 5) * scale, 3 * scale, (fitted ? 4 : 6) * scale, 8 * scale);
        graphics.fillRect(3 * scale, 5 * scale, scale, 5 * scale);
        graphics.fillRect(12 * scale, 5 * scale, scale, 5 * scale);
        graphics.fillRect((fitted ? 5 : 4) * scale, 11 * scale, 2 * scale, 3 * scale);
        graphics.fillRect((fitted ? 9 : 10) * scale, 11 * scale, 2 * scale, 3 * scale);
        graphics.setColor(new Color(0x77273B));
        graphics.drawLine((fitted ? 6 : 5) * scale, 3 * scale, 8 * scale, 7 * scale);
        graphics.drawLine((fitted ? 9 : 10) * scale, 3 * scale, 8 * scale, 7 * scale);
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 7 * scale);
        graphics.setColor(new Color(0xC8B27E));
        graphics.fillRect((fitted ? 8 : 7) * scale, 5 * scale, scale, scale);
        graphics.fillRect((fitted ? 8 : 9) * scale, 7 * scale, scale, scale);
        graphics.fillRect((fitted ? 8 : 7) * scale, 9 * scale, scale, scale);
        graphics.setColor(new Color(0xE8D9C0));
        graphics.fillRect(7 * scale, 3 * scale, 2 * scale, 2 * scale);
    }

    private static void drawVampireChainCoat(
        final Graphics2D graphics,
        final int scale,
        final boolean ladies
    ) {
        graphics.setColor(new Color(0x111116));
        graphics.fillRect(4 * scale, 2 * scale, 8 * scale, 11 * scale);
        graphics.fillRect(2 * scale, 4 * scale, 3 * scale, 7 * scale);
        graphics.fillRect(11 * scale, 4 * scale, 3 * scale, 7 * scale);
        graphics.fillRect((ladies ? 5 : 4) * scale, 12 * scale, 3 * scale, 3 * scale);
        graphics.fillRect(9 * scale, 12 * scale, (ladies ? 2 : 3) * scale, 3 * scale);
        graphics.setColor(new Color(0x38212E));
        graphics.fillRect(5 * scale, 3 * scale, 6 * scale, 9 * scale);
        graphics.setColor(new Color(0x686D73));
        for (int y = 4; y < 11; y += 2) {
            graphics.fillRect((5 + (y & 2) / 2) * scale, y * scale, 2 * scale, scale);
            graphics.fillRect((9 - (y & 2) / 2) * scale, y * scale, 2 * scale, scale);
        }
        graphics.setColor(new Color(0x8A2941));
        graphics.fillRect(7 * scale, 3 * scale, 2 * scale, 9 * scale);
        graphics.setColor(new Color(0xCFB36C));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
        if (ladies) {
            graphics.setColor(new Color(0xA13A54));
            graphics.fillRect(6 * scale, 11 * scale, 4 * scale, scale);
        }
    }

    private static void drawWarlockHat(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x11101A));
        graphics.fillRect(scale, 12 * scale, 14 * scale, 3 * scale);
        graphics.fillRect(4 * scale, 10 * scale, 9 * scale, 3 * scale);
        graphics.fillRect(5 * scale, 7 * scale, 7 * scale, 3 * scale);
        graphics.fillRect(7 * scale, 4 * scale, 5 * scale, 3 * scale);
        graphics.fillRect(9 * scale, scale, 3 * scale, 4 * scale);
        graphics.setColor(new Color(0x3A2754));
        graphics.fillRect(3 * scale, 12 * scale, 10 * scale, scale);
        graphics.fillRect(6 * scale, 8 * scale, 5 * scale, 2 * scale);
        graphics.fillRect(8 * scale, 5 * scale, 3 * scale, 2 * scale);
        graphics.setColor(new Color(0x7C3E75));
        graphics.fillRect(5 * scale, 10 * scale, 7 * scale, 2 * scale);
        graphics.setColor(new Color(0xD2B45A));
        graphics.fillRect(8 * scale, 10 * scale, 2 * scale, 2 * scale);
    }

    private static void drawWarlockRobes(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x101018));
        graphics.fillRect(4 * scale, 2 * scale, 8 * scale, 12 * scale);
        graphics.fillRect(2 * scale, 4 * scale, 3 * scale, 8 * scale);
        graphics.fillRect(11 * scale, 4 * scale, 3 * scale, 8 * scale);
        graphics.fillRect(3 * scale, 12 * scale, 4 * scale, 3 * scale);
        graphics.fillRect(9 * scale, 12 * scale, 4 * scale, 3 * scale);
        graphics.setColor(new Color(0x342451));
        graphics.fillRect(5 * scale, 3 * scale, 6 * scale, 10 * scale);
        graphics.fillRect(3 * scale, 5 * scale, scale, 6 * scale);
        graphics.fillRect(12 * scale, 5 * scale, scale, 6 * scale);
        graphics.setColor(new Color(0x6C3D88));
        graphics.drawLine(5 * scale, 3 * scale, 8 * scale, 7 * scale);
        graphics.drawLine(10 * scale, 3 * scale, 8 * scale, 7 * scale);
        graphics.fillRect(7 * scale, 8 * scale, 2 * scale, 6 * scale);
        graphics.setColor(new Color(0x66D5C5));
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(5 * scale, 11 * scale, scale, scale);
        graphics.fillRect(10 * scale, 11 * scale, scale, scale);
    }

    private static void drawForgewardenGirdle(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x171519));
        graphics.fillRect(scale, 6 * scale, 14 * scale, 5 * scale);
        graphics.setColor(new Color(0x63432D));
        graphics.fillRect(2 * scale, 7 * scale, 12 * scale, 3 * scale);
        graphics.setColor(new Color(0xA4A9A6));
        graphics.fillRect(6 * scale, 5 * scale, 5 * scale, 7 * scale);
        graphics.setColor(new Color(0x2D3135));
        graphics.fillRect(7 * scale, 6 * scale, 3 * scale, 5 * scale);
        graphics.setColor(new Color(0xF19A3A));
        graphics.fillRect(8 * scale, 7 * scale, scale, 3 * scale);
    }

    private static void drawStonebrokerQuiver(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x17171B));
        graphics.fillPolygon(new Polygon(new int[]{3, 8, 13, 9, 5, 2}, new int[]{3, 1, 6, 15, 14, 6}, 6));
        graphics.setColor(new Color(0x5A4D43));
        graphics.fillPolygon(new Polygon(new int[]{4, 8, 11, 8, 5, 3}, new int[]{4, 3, 6, 13, 13, 6}, 6));
        graphics.setColor(new Color(0x85878D));
        graphics.fillRect(6 * scale, scale, scale, 5 * scale);
        graphics.fillRect(9 * scale, 2 * scale, scale, 5 * scale);
        graphics.setColor(new Color(0xB69754));
        graphics.fillRect(5 * scale, 5 * scale, 5 * scale, scale);
        graphics.fillRect(5 * scale, 11 * scale, 4 * scale, scale);
    }

    private static void drawCaneSword(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x111217));
        graphics.drawLine(2 * scale, 14 * scale, 12 * scale, 4 * scale);
        graphics.drawLine(3 * scale, 15 * scale, 13 * scale, 5 * scale);
        graphics.setColor(new Color(0xC7D5DE));
        graphics.drawLine(3 * scale, 13 * scale, 11 * scale, 5 * scale);
        graphics.setColor(new Color(0xF4FAFC));
        graphics.drawLine(4 * scale, 12 * scale, 10 * scale, 6 * scale);
        graphics.setColor(new Color(0x55332A));
        graphics.fillRect(11 * scale, 3 * scale, 3 * scale, 3 * scale);
        graphics.fillRect(12 * scale, scale, 3 * scale, 3 * scale);
        graphics.setColor(new Color(0xB8A25F));
        graphics.fillRect(11 * scale, 4 * scale, 3 * scale, scale);
        graphics.fillRect(13 * scale, scale, scale, 2 * scale);
    }

    private static void drawReplicationStaff(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x15141C));
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 10 * scale);
        graphics.fillRect(5 * scale, 2 * scale, 2 * scale, 5 * scale);
        graphics.fillRect(9 * scale, 2 * scale, 2 * scale, 5 * scale);
        graphics.setColor(new Color(0x574A43));
        graphics.fillRect(8 * scale, 6 * scale, scale, 8 * scale);
        graphics.setColor(new Color(0x7860A7));
        graphics.fillPolygon(new Polygon(new int[]{8, 5, 6, 8, 10, 11}, new int[]{1, 4, 7, 9, 7, 4}, 6));
        graphics.setColor(new Color(0xC7F4EE));
        graphics.fillRect(7 * scale, 3 * scale, 2 * scale, 4 * scale);
        graphics.setColor(new Color(0x65D5C1));
        graphics.fillRect(7 * scale, 9 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0xB79C55));
        graphics.fillRect(6 * scale, 13 * scale, 4 * scale, 2 * scale);
    }

    private static void drawThornSpear(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x171712));
        graphics.drawLine(2 * scale, 14 * scale, 11 * scale, 5 * scale);
        graphics.drawLine(3 * scale, 15 * scale, 12 * scale, 6 * scale);
        graphics.setColor(new Color(0x4E5E31));
        graphics.drawLine(3 * scale, 14 * scale, 11 * scale, 6 * scale);
        graphics.fillRect(5 * scale, 10 * scale, 3 * scale, scale);
        graphics.fillRect(8 * scale, 7 * scale, scale, 3 * scale);
        graphics.setColor(new Color(0x7C9948));
        graphics.fillRect(5 * scale, 9 * scale, scale, scale);
        graphics.fillRect(9 * scale, 7 * scale, scale, scale);
        graphics.setColor(new Color(0xA9B8A6));
        graphics.fillPolygon(new Polygon(new int[]{10, 13, 15, 14, 11, 9}, new int[]{5, 1, 0, 4, 7, 7}, 6));
        graphics.setColor(new Color(0xE1EADF));
        graphics.drawLine(12 * scale, 4 * scale, 14 * scale, scale);
    }

    private static void drawArchfiendsUrn(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x211016));
        graphics.fillRect(5 * scale, scale, 6 * scale, 3 * scale);
        graphics.fillRect(3 * scale, 3 * scale, 10 * scale, 3 * scale);
        graphics.fillRect(4 * scale, 6 * scale, 8 * scale, 8 * scale);
        graphics.fillRect(5 * scale, 14 * scale, 6 * scale, scale);
        graphics.setColor(new Color(0x6A2430));
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, 8 * scale);
        graphics.setColor(new Color(0xD45A38));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 4 * scale);
        graphics.fillRect(6 * scale, 9 * scale, 4 * scale, 2 * scale);
        graphics.setColor(new Color(0xF5B34F));
        graphics.fillRect(7 * scale, 7 * scale, scale, 2 * scale);
    }

    private static void drawCircleTalisman(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x23182C));
        graphics.drawOval(6 * scale, 0, 4 * scale, 4 * scale);
        graphics.fillRect(7 * scale, 13 * scale, 2 * scale, 3 * scale);
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(new Color(0xC29B4F));
        graphics.drawOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0x6BD6C3));
        graphics.drawOval(5 * scale, 5 * scale, 6 * scale, 6 * scale);
        graphics.drawLine(8 * scale, 3 * scale, 8 * scale, 13 * scale);
        graphics.drawLine(3 * scale, 8 * scale, 13 * scale, 8 * scale);
        graphics.setColor(new Color(0xE9DCA3));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawHornOfTheHunt(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x1B1814));
        graphics.fillPolygon(new Polygon(new int[]{2, 5, 10, 14, 13, 9, 4}, new int[]{4, 2, 4, 9, 13, 11, 7}, 7));
        graphics.setColor(new Color(0x9B7B4D));
        graphics.fillPolygon(new Polygon(new int[]{3, 5, 9, 12, 12, 9, 5}, new int[]{4, 3, 5, 8, 11, 10, 7}, 7));
        graphics.setColor(new Color(0xD7BC80));
        graphics.fillRect(4 * scale, 4 * scale, 2 * scale, scale);
        graphics.setColor(new Color(0x4C6938));
        graphics.fillRect(8 * scale, 9 * scale, 4 * scale, scale);
    }

    private static void drawMoonCharm(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x1A1726));
        graphics.fillOval(3 * scale, 2 * scale, 10 * scale, 11 * scale);
        graphics.setColor(new Color(0xD9D6C8));
        graphics.fillOval(4 * scale, 3 * scale, 8 * scale, 9 * scale);
        graphics.setColor(new Color(0x49405F));
        graphics.fillOval(7 * scale, 2 * scale, 7 * scale, 9 * scale);
        graphics.setColor(new Color(0x9A73C5));
        graphics.fillRect(7 * scale, 12 * scale, 2 * scale, 3 * scale);
        graphics.setColor(new Color(0x66D8CE));
        graphics.fillRect(6 * scale, 13 * scale, 4 * scale, scale);
    }

    private static void drawMysticBranch(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x211811));
        graphics.drawLine(3 * scale, 14 * scale, 11 * scale, 3 * scale);
        graphics.drawLine(4 * scale, 14 * scale, 12 * scale, 3 * scale);
        graphics.setColor(new Color(0x765032));
        graphics.drawLine(4 * scale, 13 * scale, 11 * scale, 4 * scale);
        graphics.drawLine(7 * scale, 9 * scale, 4 * scale, 7 * scale);
        graphics.drawLine(9 * scale, 6 * scale, 13 * scale, 7 * scale);
        graphics.setColor(new Color(0x64B763));
        graphics.fillRect(3 * scale, 6 * scale, 3 * scale, 2 * scale);
        graphics.fillRect(11 * scale, 6 * scale, 3 * scale, 2 * scale);
        graphics.setColor(new Color(0x74DBD0));
        graphics.fillRect(8 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawMetalChunk(final Graphics2D graphics, final int scale, final Color[] metal) {
        graphics.setColor(metal[0]);
        graphics.fillPolygon(new Polygon(new int[]{2, 5, 10, 14, 13, 9, 4, 1}, new int[]{7, 3, 2, 6, 12, 15, 14, 11}, 8));
        graphics.setColor(metal[Math.min(2, metal.length - 1)]);
        graphics.fillPolygon(new Polygon(new int[]{3, 6, 10, 12, 12, 9, 4, 2}, new int[]{7, 4, 3, 6, 11, 13, 13, 10}, 8));
        graphics.setColor(metal[Math.min(4, metal.length - 1)]);
        graphics.fillRect(5 * scale, 5 * scale, 4 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 8 * scale, 2 * scale, 2 * scale);
        graphics.setColor(metal[metal.length - 1]);
        graphics.fillRect(6 * scale, 5 * scale, 2 * scale, scale);
    }

    private static void drawMetalIngot(final Graphics2D graphics, final int scale, final Color[] metal) {
        graphics.setColor(metal[0]);
        graphics.fillPolygon(new Polygon(new int[]{3, 6, 13, 15, 12, 4, 1}, new int[]{5, 2, 3, 8, 12, 13, 10}, 7));
        graphics.setColor(metal[Math.min(2, metal.length - 1)]);
        graphics.fillPolygon(new Polygon(new int[]{4, 7, 12, 13, 11, 5, 3}, new int[]{6, 4, 5, 8, 10, 11, 9}, 7));
        graphics.setColor(metal[Math.min(3, metal.length - 1)]);
        graphics.fillRect(6 * scale, 5 * scale, 5 * scale, 2 * scale);
        graphics.setColor(metal[metal.length - 1]);
        graphics.fillRect(7 * scale, 5 * scale, 3 * scale, scale);
    }

    private static void drawTwistingBand(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x24182D));
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(new Color(0xB08AC8));
        graphics.drawOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0x5BD6C5));
        graphics.drawLine(4 * scale, 10 * scale, 11 * scale, 4 * scale);
        graphics.drawLine(5 * scale, 12 * scale, 13 * scale, 5 * scale);
        graphics.setColor(new Color(0xE8D5A1));
        graphics.fillRect(11 * scale, 3 * scale, 2 * scale, 2 * scale);
    }

    private static void drawSunGrenade(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x542315));
        graphics.fillRect(7 * scale, scale, 2 * scale, 14 * scale);
        graphics.fillRect(scale, 7 * scale, 14 * scale, 2 * scale);
        graphics.fillOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0xE27B2F));
        graphics.fillOval(4 * scale, 4 * scale, 8 * scale, 8 * scale);
        graphics.setColor(new Color(0xFFE28A));
        graphics.fillRect(6 * scale, 5 * scale, 4 * scale, 5 * scale);
        graphics.fillRect(5 * scale, 6 * scale, 6 * scale, 3 * scale);
        graphics.setColor(new Color(0x80512D));
        graphics.fillRect(7 * scale, 0, 3 * scale, 3 * scale);
    }

    private static void drawMeatyStew(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x251817));
        graphics.fillOval(2 * scale, 5 * scale, 12 * scale, 8 * scale);
        graphics.fillRect(4 * scale, 10 * scale, 8 * scale, 4 * scale);
        graphics.setColor(new Color(0x8A3B33));
        graphics.fillOval(3 * scale, 6 * scale, 10 * scale, 5 * scale);
        graphics.setColor(new Color(0xD47B5A));
        graphics.fillRect(4 * scale, 7 * scale, 3 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 6 * scale, 3 * scale, 2 * scale);
        graphics.setColor(new Color(0xD4B367));
        graphics.fillRect(7 * scale, 8 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0x8B5C42));
        graphics.fillRect(4 * scale, 11 * scale, 8 * scale, 2 * scale);
    }

    private static void drawCookedMeatyStew(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x241915));
        graphics.fillOval(2 * scale, 5 * scale, 12 * scale, 8 * scale);
        graphics.fillRect(4 * scale, 10 * scale, 8 * scale, 4 * scale);
        graphics.setColor(new Color(0x6F3B25));
        graphics.fillOval(3 * scale, 6 * scale, 10 * scale, 5 * scale);
        graphics.setColor(new Color(0xA9623E));
        graphics.fillRect(4 * scale, 7 * scale, 3 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 6 * scale, 3 * scale, 2 * scale);
        graphics.setColor(new Color(0xD28A3E));
        graphics.fillRect(7 * scale, 8 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0x6EA04A));
        graphics.fillRect(5 * scale, 6 * scale, scale, scale);
        graphics.fillRect(10 * scale, 9 * scale, scale, scale);
        graphics.setColor(new Color(0xA97A51));
        graphics.fillRect(4 * scale, 11 * scale, 8 * scale, 2 * scale);
    }

    private static void drawNamedSeeds(final Graphics2D graphics, final int scale, final String id) {
        final Color seed = switch (id) {
            case "seedsartichoke" -> new Color(0xB28B55);
            case "seedsbelladonna" -> new Color(0x7250A1);
            case "seedsdreamroot" -> new Color(0x567BC0);
            case "seedsmandrake" -> new Color(0xC19A52);
            case "seedssnowbell" -> new Color(0xD9EBE5);
            case "seedswolfsbane" -> new Color(0x6B83C8);
            default -> new Color(0x8D7847);
        };
        graphics.setColor(new Color(0x2A2117));
        graphics.fillRect(3 * scale, 3 * scale, 10 * scale, 11 * scale);
        graphics.setColor(new Color(0xC8A46A));
        graphics.fillRect(4 * scale, 4 * scale, 8 * scale, 9 * scale);
        graphics.setColor(new Color(0xE3C98F));
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, 4 * scale);
        graphics.setColor(seed.darker());
        graphics.fillOval(5 * scale, 7 * scale, 3 * scale, 4 * scale);
        graphics.fillOval(8 * scale, 8 * scale, 3 * scale, 3 * scale);
        graphics.setColor(seed);
        graphics.fillRect(6 * scale, 8 * scale, scale, 2 * scale);
        graphics.fillRect(9 * scale, 9 * scale, scale, scale);
        graphics.setColor(new Color(0x4F7A3E));
        graphics.fillRect(7 * scale, 4 * scale, 2 * scale, 3 * scale);
    }

    private static void drawAnointingPaste(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x211B17));
        graphics.fillOval(2 * scale, 7 * scale, 12 * scale, 7 * scale);
        graphics.fillRect(4 * scale, 11 * scale, 8 * scale, 3 * scale);
        graphics.setColor(new Color(0x6A7250));
        graphics.fillOval(3 * scale, 8 * scale, 10 * scale, 4 * scale);
        graphics.setColor(new Color(0xA6C85F));
        graphics.fillOval(4 * scale, 8 * scale, 8 * scale, 3 * scale);
        graphics.setColor(new Color(0xD6E78A));
        graphics.fillRect(6 * scale, 8 * scale, 3 * scale, scale);
        graphics.setColor(new Color(0x7B5031));
        graphics.drawLine(8 * scale, 8 * scale, 13 * scale, 3 * scale);
        graphics.drawLine(9 * scale, 8 * scale, 14 * scale, 3 * scale);
        graphics.setColor(new Color(0xD5B66D));
        graphics.fillRect(5 * scale, 10 * scale, scale, scale);
        graphics.fillRect(10 * scale, 9 * scale, scale, scale);
    }

    private static void drawWormyApple(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x251713));
        graphics.fillRect(7 * scale, scale, 2 * scale, 4 * scale);
        graphics.fillRect(3 * scale, 4 * scale, 10 * scale, 9 * scale);
        graphics.fillRect(5 * scale, 13 * scale, 6 * scale, 2 * scale);
        graphics.setColor(new Color(0x8DAD43));
        graphics.fillRect(4 * scale, 5 * scale, 8 * scale, 7 * scale);
        graphics.fillRect(6 * scale, 12 * scale, 4 * scale, scale);
        graphics.setColor(new Color(0xBFD761));
        graphics.fillRect(5 * scale, 5 * scale, 2 * scale, 5 * scale);
        graphics.setColor(new Color(0x4E7A35));
        graphics.fillRect(9 * scale, 2 * scale, 4 * scale, 2 * scale);
        graphics.setColor(new Color(0xE8DFC2));
        graphics.fillRect(8 * scale, 8 * scale, 4 * scale, 2 * scale);
        graphics.fillRect(10 * scale, 9 * scale, 2 * scale, 3 * scale);
        graphics.setColor(new Color(0xB85C4E));
        graphics.fillRect(11 * scale, 10 * scale, scale, scale);
    }

    private static void drawBatBall(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x121016));
        graphics.fillPolygon(new Polygon(
            new int[]{scale, 5 * scale, 6 * scale, 3 * scale},
            new int[]{5 * scale, 3 * scale, 8 * scale, 10 * scale},
            4
        ));
        graphics.fillPolygon(new Polygon(
            new int[]{15 * scale, 11 * scale, 10 * scale, 13 * scale},
            new int[]{5 * scale, 3 * scale, 8 * scale, 10 * scale},
            4
        ));
        graphics.fillPolygon(new Polygon(
            new int[]{5 * scale, 7 * scale, 8 * scale},
            new int[]{4 * scale, scale, 5 * scale},
            3
        ));
        graphics.fillPolygon(new Polygon(
            new int[]{8 * scale, 9 * scale, 11 * scale},
            new int[]{5 * scale, scale, 4 * scale},
            3
        ));
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(new Color(0x30283A));
        graphics.fillOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0x584765));
        graphics.drawLine(4 * scale, 6 * scale, 11 * scale, 10 * scale);
        graphics.drawLine(5 * scale, 11 * scale, 10 * scale, 4 * scale);
        graphics.drawOval(5 * scale, 5 * scale, 6 * scale, 6 * scale);
        graphics.setColor(new Color(0xD47866));
        graphics.fillRect(5 * scale, 6 * scale, scale, scale);
        graphics.fillRect(10 * scale, 6 * scale, scale, scale);
    }

    private static void drawRowanBerries(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x26351E));
        graphics.drawLine(8 * scale, scale, 8 * scale, 6 * scale);
        graphics.drawLine(8 * scale, 4 * scale, 4 * scale, 7 * scale);
        graphics.drawLine(8 * scale, 4 * scale, 12 * scale, 7 * scale);
        graphics.setColor(new Color(0x557B3A));
        graphics.fillPolygon(new Polygon(
            new int[]{8 * scale, 12 * scale, 14 * scale, 10 * scale},
            new int[]{3 * scale, 2 * scale, 4 * scale, 5 * scale},
            4
        ));
        graphics.setColor(new Color(0x4A101B));
        graphics.fillOval(2 * scale, 6 * scale, 7 * scale, 7 * scale);
        graphics.fillOval(7 * scale, 7 * scale, 7 * scale, 7 * scale);
        graphics.fillOval(5 * scale, 10 * scale, 7 * scale, 6 * scale);
        graphics.setColor(new Color(0xB52D3B));
        graphics.fillOval(3 * scale, 7 * scale, 5 * scale, 5 * scale);
        graphics.fillOval(8 * scale, 8 * scale, 5 * scale, 5 * scale);
        graphics.fillOval(6 * scale, 11 * scale, 5 * scale, 4 * scale);
        graphics.setColor(new Color(0xF08A73));
        graphics.fillRect(4 * scale, 8 * scale, scale, scale);
        graphics.fillRect(9 * scale, 9 * scale, scale, scale);
    }

    private static void drawBatWool(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x0F0F13));
        graphics.fillRect(3 * scale, 4 * scale, 10 * scale, 8 * scale);
        graphics.fillRect(5 * scale, 2 * scale, 6 * scale, 12 * scale);
        graphics.setColor(new Color(0x27232D));
        graphics.fillRect(4 * scale, 5 * scale, 3 * scale, 3 * scale);
        graphics.fillRect(9 * scale, 4 * scale, 3 * scale, 3 * scale);
        graphics.fillRect(6 * scale, 9 * scale, 4 * scale, 3 * scale);
        graphics.setColor(new Color(0x4A4052));
        graphics.fillRect(5 * scale, 4 * scale, 2 * scale, scale);
        graphics.fillRect(9 * scale, 9 * scale, 2 * scale, scale);
    }

    private static void drawBroom(final Graphics2D graphics, final int scale, final boolean enchanted) {
        graphics.setColor(new Color(0x241912));
        graphics.drawLine(3 * scale, 13 * scale, 12 * scale, 3 * scale);
        graphics.drawLine(4 * scale, 14 * scale, 13 * scale, 4 * scale);
        graphics.setColor(new Color(0x8A6038));
        graphics.drawLine(4 * scale, 13 * scale, 12 * scale, 4 * scale);
        graphics.setColor(new Color(0x5C4025));
        graphics.fillPolygon(new Polygon(new int[]{2, 4, 8, 10, 7, 3}, new int[]{9, 8, 11, 15, 15, 13}, 6));
        graphics.setColor(new Color(0xB58A4C));
        graphics.drawLine(4 * scale, 9 * scale, 8 * scale, 14 * scale);
        graphics.drawLine(3 * scale, 11 * scale, 6 * scale, 15 * scale);
        if (enchanted) {
            graphics.setColor(new Color(0x65D5C1));
            graphics.fillRect(7 * scale, 9 * scale, 2 * scale, 2 * scale);
            graphics.fillRect(11 * scale, 3 * scale, 2 * scale, 2 * scale);
            graphics.setColor(new Color(0xB36DDF));
            graphics.fillRect(5 * scale, 11 * scale, scale, 2 * scale);
            graphics.fillRect(9 * scale, 7 * scale, scale, 2 * scale);
        }
    }

    private static void drawCandelabra(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x251B13));
        graphics.fillRect(3 * scale, 13 * scale, 10 * scale, 2 * scale);
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 9 * scale);
        graphics.fillRect(3 * scale, 8 * scale, 10 * scale, 2 * scale);
        graphics.setColor(new Color(0xB58A3F));
        graphics.fillRect(4 * scale, 4 * scale, 2 * scale, 5 * scale);
        graphics.fillRect(7 * scale, 2 * scale, 2 * scale, 7 * scale);
        graphics.fillRect(10 * scale, 4 * scale, 2 * scale, 5 * scale);
        graphics.fillRect(8 * scale, 9 * scale, scale, 5 * scale);
        graphics.setColor(new Color(0xF3CB66));
        graphics.fillRect(4 * scale, 2 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(7 * scale, 0, 2 * scale, 2 * scale);
        graphics.fillRect(10 * scale, 2 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0xFFEEA3));
        graphics.fillRect(5 * scale, 2 * scale, scale, scale);
        graphics.fillRect(8 * scale, 0, scale, scale);
        graphics.fillRect(11 * scale, 2 * scale, scale, scale);
    }

    private static void drawFancifulCharm(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x21172A));
        graphics.drawOval(3 * scale, scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0xC5A45B));
        graphics.drawOval(4 * scale, 2 * scale, 8 * scale, 8 * scale);
        graphics.setColor(new Color(0x8460B0));
        graphics.fillPolygon(new Polygon(new int[]{8, 11, 10, 8, 6, 5}, new int[]{3, 6, 10, 14, 10, 6}, 6));
        graphics.setColor(new Color(0xE4A8E7));
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 5 * scale);
        graphics.setColor(new Color(0x69D8C5));
        graphics.fillRect(9 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawDemonicContract(
        final Graphics2D graphics,
        final int scale,
        final ContractMark mark
    ) {
        graphics.setColor(new Color(0x2A1716));
        graphics.fillRect(3 * scale, 2 * scale, 10 * scale, 12 * scale);
        graphics.setColor(new Color(0xD2B784));
        graphics.fillRect(4 * scale, 3 * scale, 8 * scale, 10 * scale);
        graphics.setColor(new Color(0x6E4B38));
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, scale);
        graphics.fillRect(5 * scale, 7 * scale, 4 * scale, scale);
        graphics.setColor(new Color(0x9B2434));
        switch (mark) {
            case SEAL -> graphics.fillOval(7 * scale, 9 * scale, 4 * scale, 4 * scale);
            case FLAME -> {
                graphics.fillRect(7 * scale, 9 * scale, 3 * scale, 3 * scale);
                graphics.fillRect(8 * scale, 7 * scale, scale, 3 * scale);
            }
            case HAND -> {
                graphics.fillRect(7 * scale, 9 * scale, 3 * scale, 3 * scale);
                graphics.fillRect(6 * scale, 8 * scale, scale, 3 * scale);
                graphics.fillRect(10 * scale, 8 * scale, scale, 3 * scale);
            }
            case SHIELD -> graphics.fillPolygon(new Polygon(new int[]{7, 11, 10, 9, 8, 6}, new int[]{8, 8, 12, 13, 13, 10}, 6));
            case INGOT -> {
                graphics.fillRect(6 * scale, 9 * scale, 5 * scale, 3 * scale);
                graphics.fillRect(7 * scale, 8 * scale, 3 * scale, scale);
            }
            case CHAIN -> {
                graphics.drawOval(6 * scale, 8 * scale, 3 * scale, 4 * scale);
                graphics.drawOval(9 * scale, 9 * scale, 3 * scale, 4 * scale);
            }
        }
        graphics.setColor(new Color(0xF0A044));
        graphics.fillRect(8 * scale, 9 * scale, scale, scale);
    }

    private static void drawDarkCloth(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x111018));
        graphics.fillPolygon(new Polygon(
            new int[]{2 * scale, 10 * scale, 14 * scale, 11 * scale, 3 * scale},
            new int[]{4 * scale, 2 * scale, 6 * scale, 14 * scale, 13 * scale},
            5
        ));
        graphics.setColor(new Color(0x292536));
        graphics.fillPolygon(new Polygon(
            new int[]{3 * scale, 9 * scale, 12 * scale, 10 * scale, 4 * scale},
            new int[]{5 * scale, 3 * scale, 6 * scale, 12 * scale, 12 * scale},
            5
        ));
        graphics.setColor(new Color(0x514A63));
        graphics.drawLine(4 * scale, 6 * scale, 10 * scale, 11 * scale);
        graphics.drawLine(5 * scale, 11 * scale, 11 * scale, 7 * scale);
        graphics.setColor(new Color(0x807591));
        graphics.fillRect(5 * scale, 5 * scale, 2 * scale, scale);
    }

    private static void drawWovenCruor(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x1E1016));
        graphics.fillPolygon(new Polygon(new int[]{2, 10, 14, 11, 3}, new int[]{4, 2, 6, 14, 13}, 5));
        graphics.setColor(new Color(0x5E1F32));
        graphics.fillPolygon(new Polygon(new int[]{3, 9, 12, 10, 4}, new int[]{5, 3, 6, 12, 12}, 5));
        graphics.setColor(new Color(0xA33349));
        graphics.drawLine(4 * scale, 6 * scale, 10 * scale, 11 * scale);
        graphics.drawLine(6 * scale, 4 * scale, 11 * scale, 9 * scale);
        graphics.drawLine(4 * scale, 10 * scale, 10 * scale, 5 * scale);
        graphics.setColor(new Color(0xD98679));
        graphics.fillRect(8 * scale, 7 * scale, scale, scale);
    }

    private static void drawRedstoneSoup(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x251516));
        graphics.fillOval(2 * scale, 5 * scale, 12 * scale, 8 * scale);
        graphics.fillRect(4 * scale, 10 * scale, 8 * scale, 4 * scale);
        graphics.setColor(new Color(0x761C27));
        graphics.fillOval(3 * scale, 6 * scale, 10 * scale, 5 * scale);
        graphics.setColor(new Color(0xD33D42));
        graphics.fillRect(5 * scale, 7 * scale, 3 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 8 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0xFF7770));
        graphics.fillRect(6 * scale, 7 * scale, scale, scale);
        graphics.setColor(new Color(0x8B5C42));
        graphics.fillRect(4 * scale, 11 * scale, 8 * scale, 2 * scale);
    }

    private static void drawFrogToe(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x1A1E12));
        graphics.fillPolygon(new Polygon(new int[]{3, 6, 11, 13, 12, 9, 5, 2}, new int[]{12, 5, 2, 4, 7, 9, 14, 14}, 8));
        graphics.setColor(new Color(0x65773A));
        graphics.fillPolygon(new Polygon(new int[]{4, 7, 10, 11, 10, 8, 5, 3}, new int[]{12, 6, 3, 5, 7, 8, 13, 13}, 8));
        graphics.setColor(new Color(0xA6B95B));
        graphics.fillRect(8 * scale, 5 * scale, 2 * scale, 2 * scale);
        graphics.fillOval(10 * scale, 2 * scale, 4 * scale, 4 * scale);
        graphics.setColor(new Color(0xD1D986));
        graphics.fillRect(11 * scale, 3 * scale, scale, scale);
    }

    private static void drawVerdantCatalyst(final Graphics2D graphics, final int scale, final boolean prime) {
        graphics.setColor(new Color(0x172019));
        graphics.fillOval(2 * scale, 3 * scale, 12 * scale, 11 * scale);
        graphics.setColor(prime ? new Color(0x7FCB55) : new Color(0x4F8A43));
        graphics.fillOval(3 * scale, 4 * scale, 10 * scale, 9 * scale);
        graphics.setColor(prime ? new Color(0xD8EC73) : new Color(0x93C95E));
        graphics.fillRect(7 * scale, 2 * scale, 2 * scale, 8 * scale);
        graphics.fillRect(4 * scale, 5 * scale, 4 * scale, 2 * scale);
        graphics.fillRect(8 * scale, 4 * scale, 4 * scale, 2 * scale);
        graphics.setColor(new Color(0xE7D6A0));
        graphics.fillRect(5 * scale, 8 * scale, 2 * scale, 2 * scale);
        if (prime) {
            graphics.setColor(new Color(0x67D8C4));
            graphics.fillRect(9 * scale, 9 * scale, 2 * scale, 2 * scale);
            graphics.fillRect(7 * scale, 11 * scale, 2 * scale, 2 * scale);
        }
    }

    private static void drawBolt(final Graphics2D graphics, final int scale, final BoltKind kind) {
        final Color shaft = switch (kind) {
            case WOOD -> new Color(0x8C5F35);
            case BONE -> new Color(0xD9D2B2);
            case SILVER -> new Color(0xB9D3E1);
            case SPLITTING -> new Color(0x80613B);
        };
        graphics.setColor(new Color(0x17171A));
        graphics.drawLine(3 * scale, 13 * scale, 12 * scale, 4 * scale);
        graphics.drawLine(4 * scale, 14 * scale, 13 * scale, 5 * scale);
        graphics.setColor(shaft);
        graphics.drawLine(4 * scale, 13 * scale, 12 * scale, 5 * scale);
        graphics.setColor(kind == BoltKind.SILVER ? new Color(0xEEF8FA) : new Color(0xEEE3C2));
        if (kind == BoltKind.SPLITTING) {
            graphics.drawLine(11 * scale, 5 * scale, 14 * scale, 2 * scale);
            graphics.drawLine(11 * scale, 5 * scale, 14 * scale, 6 * scale);
        } else {
            graphics.fillPolygon(new Polygon(new int[]{11, 15, 13, 10}, new int[]{4, 1, 6, 6}, 4));
        }
        graphics.setColor(kind == BoltKind.BONE ? new Color(0xA69D7D) : new Color(0x55372A));
        graphics.drawLine(3 * scale, 12 * scale, 2 * scale, 9 * scale);
        graphics.drawLine(4 * scale, 13 * scale, 7 * scale, 14 * scale);
    }

    private static void drawSpectralStone(final Graphics2D graphics, final int scale) {
        drawRunedStone(graphics, scale, new Color(0x55CBB6), LabelMotif.SPIRIT);
        graphics.setColor(new Color(0xD4FFF3));
        graphics.fillRect(6 * scale, 4 * scale, 2 * scale, 2 * scale);
    }

    private static void drawTongueCharm(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x26313A));
        graphics.drawOval(3 * scale, scale, 10 * scale, 9 * scale);
        graphics.setColor(new Color(0xD8E8ED));
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, 5 * scale);
        graphics.fillRect(6 * scale, 9 * scale, 4 * scale, 4 * scale);
        graphics.fillRect(7 * scale, 12 * scale, 2 * scale, 3 * scale);
        graphics.setColor(new Color(0xA75C76));
        graphics.fillRect(7 * scale, 7 * scale, 3 * scale, 5 * scale);
        graphics.setColor(new Color(0xF4F7F5));
        graphics.fillRect(6 * scale, 5 * scale, 3 * scale, scale);
    }

    private static void drawReplicationCharge(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x18172A));
        graphics.fillPolygon(new Polygon(new int[]{5, 8, 11, 10, 6, 4}, new int[]{2, 1, 4, 9, 10, 6}, 6));
        graphics.fillPolygon(new Polygon(new int[]{8, 11, 14, 13, 9, 7}, new int[]{6, 5, 8, 13, 14, 10}, 6));
        graphics.setColor(new Color(0x64CFCB));
        graphics.fillPolygon(new Polygon(new int[]{6, 8, 10, 9, 6, 5}, new int[]{3, 2, 4, 8, 9, 6}, 6));
        graphics.setColor(new Color(0xA278D0));
        graphics.fillPolygon(new Polygon(new int[]{9, 11, 13, 12, 9, 8}, new int[]{7, 6, 8, 12, 13, 10}, 6));
        graphics.setColor(new Color(0xE9F5EF));
        graphics.drawLine(5 * scale, 12 * scale, 12 * scale, 5 * scale);
        graphics.fillRect(4 * scale, 11 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(11 * scale, 4 * scale, 2 * scale, 2 * scale);
    }

    private static void drawMutatingSprig(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x493121));
        graphics.drawLine(3 * scale, 14 * scale, 12 * scale, 2 * scale);
        graphics.drawLine(4 * scale, 14 * scale, 13 * scale, 2 * scale);
        graphics.setColor(new Color(0x5A9A45));
        graphics.fillOval(3 * scale, 7 * scale, 6 * scale, 4 * scale);
        graphics.fillOval(8 * scale, 3 * scale, 6 * scale, 4 * scale);
        graphics.setColor(new Color(0xBA65CC));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(11 * scale, 2 * scale, 2 * scale, 2 * scale);
    }

    private static void drawHandMirror(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2B2432));
        graphics.fillOval(2 * scale, scale, 11 * scale, 11 * scale);
        graphics.fillRect(9 * scale, 10 * scale, 3 * scale, 5 * scale);
        graphics.setColor(new Color(0xB68D4E));
        graphics.drawOval(3 * scale, 2 * scale, 9 * scale, 9 * scale);
        graphics.fillRect(10 * scale, 10 * scale, scale, 4 * scale);
        graphics.setColor(new Color(0x9FE0E3));
        graphics.fillOval(4 * scale, 3 * scale, 7 * scale, 7 * scale);
        graphics.setColor(new Color(0xE7FFFF));
        graphics.fillRect(5 * scale, 4 * scale, 2 * scale, 4 * scale);
    }

    private static void drawLouse(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x1B1620));
        graphics.fillOval(5 * scale, 3 * scale, 6 * scale, 10 * scale);
        for (int y = 5; y <= 11; y += 3) {
            graphics.drawLine(5 * scale, y * scale, 2 * scale, (y - 2) * scale);
            graphics.drawLine(10 * scale, y * scale, 13 * scale, (y - 2) * scale);
        }
        graphics.setColor(new Color(0x8B6A46));
        graphics.fillOval(6 * scale, 4 * scale, 4 * scale, 7 * scale);
        graphics.setColor(new Color(0xCFB878));
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 2 * scale);
    }

    private static void drawEarmuffs(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x24202A));
        graphics.drawArc(3 * scale, 2 * scale, 10 * scale, 10 * scale, 0, 180);
        graphics.drawArc(4 * scale, 3 * scale, 8 * scale, 8 * scale, 0, 180);
        graphics.fillRect(2 * scale, 7 * scale, 4 * scale, 6 * scale);
        graphics.fillRect(10 * scale, 7 * scale, 4 * scale, 6 * scale);
        graphics.setColor(new Color(0x76518D));
        graphics.fillRect(3 * scale, 8 * scale, 2 * scale, 4 * scale);
        graphics.fillRect(11 * scale, 8 * scale, 2 * scale, 4 * scale);
        graphics.setColor(new Color(0xC59BE0));
        graphics.fillRect(4 * scale, 8 * scale, scale, 2 * scale);
        graphics.fillRect(11 * scale, 8 * scale, scale, 2 * scale);
    }

    private static void drawDeathHand(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x15151A));
        graphics.fillRect(5 * scale, 7 * scale, 7 * scale, 6 * scale);
        graphics.fillRect(7 * scale, 12 * scale, 5 * scale, 3 * scale);
        graphics.fillRect(3 * scale, 3 * scale, 2 * scale, 7 * scale);
        graphics.fillRect(6 * scale, scale, 2 * scale, 8 * scale);
        graphics.fillRect(9 * scale, 2 * scale, 2 * scale, 7 * scale);
        graphics.fillRect(12 * scale, 4 * scale, 2 * scale, 6 * scale);
        graphics.setColor(new Color(0xC6C5B6));
        graphics.fillRect(6 * scale, 8 * scale, 5 * scale, 3 * scale);
        graphics.fillRect(7 * scale, 12 * scale, 4 * scale, scale);
    }

    private static void drawDeathBoots(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x15141A));
        graphics.fillRect(2 * scale, 3 * scale, 5 * scale, 9 * scale);
        graphics.fillRect(scale, 10 * scale, 7 * scale, 4 * scale);
        graphics.fillRect(9 * scale, 3 * scale, 5 * scale, 9 * scale);
        graphics.fillRect(8 * scale, 10 * scale, 7 * scale, 4 * scale);
        graphics.setColor(new Color(0x4B4552));
        graphics.fillRect(3 * scale, 4 * scale, 3 * scale, 6 * scale);
        graphics.fillRect(10 * scale, 4 * scale, 3 * scale, 6 * scale);
        graphics.setColor(new Color(0xB7B8AC));
        graphics.fillRect(2 * scale, 12 * scale, 5 * scale, scale);
        graphics.fillRect(9 * scale, 12 * scale, 5 * scale, scale);
    }

    private static void drawDeathHood(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x111116));
        graphics.fillPolygon(new Polygon(new int[]{8, 3, 2, 4, 12, 14, 13}, new int[]{1, 4, 11, 15, 15, 11, 4}, 7));
        graphics.setColor(new Color(0x3E3946));
        graphics.fillPolygon(new Polygon(new int[]{8, 4, 4, 6, 10, 12, 12}, new int[]{2, 5, 11, 14, 14, 11, 5}, 7));
        graphics.setColor(new Color(0x0B0A0E));
        graphics.fillOval(5 * scale, 5 * scale, 6 * scale, 7 * scale);
        graphics.setColor(new Color(0xC8C9B7));
        graphics.fillRect(6 * scale, 7 * scale, scale, scale);
        graphics.fillRect(9 * scale, 7 * scale, scale, scale);
    }

    private static void drawMagicBucket(
        final Graphics2D graphics,
        final int scale,
        final Color liquid,
        final LabelMotif motif
    ) {
        graphics.setColor(new Color(0x26313A));
        graphics.drawArc(3 * scale, scale, 10 * scale, 10 * scale, 0, 180);
        graphics.fillPolygon(new Polygon(new int[]{2, 14, 12, 4}, new int[]{5, 5, 15, 15}, 4));
        graphics.setColor(new Color(0x91A2AC));
        graphics.fillPolygon(new Polygon(new int[]{4, 12, 11, 5}, new int[]{6, 6, 13, 13}, 4));
        graphics.setColor(liquid);
        graphics.fillRect(4 * scale, 6 * scale, 8 * scale, 3 * scale);
        graphics.setColor(new Color(0xE8D9A5));
        graphics.fillRect(6 * scale, 9 * scale, 4 * scale, 3 * scale);
        graphics.setColor(liquid.darker());
        drawLabelMotif(graphics, scale, motif, 6, 9);
    }

    private static void drawBeastCharm(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2C211A));
        graphics.drawOval(3 * scale, scale, 10 * scale, 10 * scale);
        graphics.fillOval(4 * scale, 5 * scale, 8 * scale, 8 * scale);
        graphics.setColor(new Color(0xC28D55));
        graphics.fillOval(6 * scale, 7 * scale, 4 * scale, 4 * scale);
        graphics.fillOval(4 * scale, 5 * scale, 3 * scale, 3 * scale);
        graphics.fillOval(9 * scale, 5 * scale, 3 * scale, 3 * scale);
        graphics.fillOval(5 * scale, 10 * scale, 3 * scale, 3 * scale);
        graphics.fillOval(9 * scale, 10 * scale, 3 * scale, 3 * scale);
        graphics.setColor(new Color(0xE7C984));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawWoodenStake(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2B1A13));
        graphics.fillPolygon(new Polygon(new int[]{2, 4, 14, 15, 13, 3}, new int[]{12, 14, 4, 1, 2, 11}, 6));
        graphics.setColor(new Color(0x8B5632));
        graphics.fillPolygon(new Polygon(new int[]{4, 5, 13, 14, 12, 3}, new int[]{12, 13, 4, 2, 3, 11}, 6));
        graphics.setColor(new Color(0xD3A160));
        graphics.drawLine(5 * scale, 11 * scale, 12 * scale, 4 * scale);
    }

    private static void drawSoulRelic(
        final Graphics2D graphics,
        final int scale,
        final Color glow,
        final LabelMotif motif
    ) {
        graphics.setColor(new Color(0x17151E));
        graphics.fillPolygon(new Polygon(
            new int[]{5 * scale, 7 * scale, 8 * scale},
            new int[]{5 * scale, scale, 5 * scale},
            3
        ));
        graphics.fillPolygon(new Polygon(
            new int[]{8 * scale, 10 * scale, 12 * scale},
            new int[]{5 * scale, 2 * scale, 6 * scale},
            3
        ));
        graphics.fillPolygon(new Polygon(
            new int[]{5 * scale, 8 * scale, 11 * scale},
            new int[]{11 * scale, 15 * scale, 11 * scale},
            3
        ));
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(glow.darker());
        graphics.fillOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(glow);
        graphics.fillOval(5 * scale, 5 * scale, 6 * scale, 6 * scale);
        graphics.setColor(new Color(0xE8F6E8));
        drawLabelMotif(graphics, scale, motif, 7, 6);
    }

    private static void drawRunedStone(
        final Graphics2D graphics,
        final int scale,
        final Color stone,
        final LabelMotif motif
    ) {
        graphics.setColor(new Color(0x17171B));
        graphics.fillPolygon(new Polygon(new int[]{5, 10, 14, 13, 9, 4, 2, 3}, new int[]{2, 1, 5, 11, 14, 14, 10, 5}, 8));
        graphics.setColor(stone.darker());
        graphics.fillPolygon(new Polygon(new int[]{6, 10, 12, 12, 9, 5, 4, 4}, new int[]{3, 2, 5, 10, 13, 12, 9, 5}, 8));
        graphics.setColor(stone);
        graphics.fillRect(6 * scale, 4 * scale, 5 * scale, 7 * scale);
        graphics.setColor(new Color(0xE7F4DD));
        drawLabelMotif(graphics, scale, motif, 7, 6);
    }

    private static void drawRockItem(final Graphics2D graphics, final int scale, final Color stone) {
        graphics.setColor(stone.darker().darker());
        graphics.fillPolygon(new Polygon(new int[]{2, 5, 11, 14, 13, 9, 4}, new int[]{10, 4, 3, 8, 13, 15, 14}, 7));
        graphics.setColor(stone);
        graphics.fillPolygon(new Polygon(new int[]{4, 6, 10, 12, 11, 8, 4}, new int[]{10, 5, 5, 8, 12, 13, 13}, 7));
        graphics.setColor(stone.brighter());
        graphics.fillRect(6 * scale, 5 * scale, 4 * scale, 2 * scale);
    }

    private static void drawPentacleItem(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2D2116));
        graphics.fillOval(scale, scale, 14 * scale, 14 * scale);
        graphics.setColor(new Color(0xD2A84E));
        graphics.drawOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        final int[] x = {8, 10, 14, 11, 12, 8, 4, 5, 2, 6};
        final int[] y = {2, 6, 6, 9, 13, 11, 13, 9, 6, 6};
        final Polygon star = new Polygon(
            java.util.Arrays.stream(x).map(value -> value * scale).toArray(),
            java.util.Arrays.stream(y).map(value -> value * scale).toArray(),
            x.length
        );
        graphics.drawPolygon(star);
        graphics.setColor(new Color(0xFFF0A3));
        graphics.fillRect(7 * scale, 2 * scale, 2 * scale, scale);
    }

    private static void drawPowderPile(final Graphics2D graphics, final int scale, final Color[] powder) {
        graphics.setColor(powder[0]);
        graphics.fillPolygon(new Polygon(new int[]{1, 4, 6, 9, 12, 15, 14, 2}, new int[]{13, 10, 8, 6, 9, 13, 15, 15}, 8));
        graphics.setColor(powder[1]);
        graphics.fillPolygon(new Polygon(new int[]{3, 6, 9, 12, 14, 13, 4}, new int[]{13, 9, 8, 10, 13, 14, 14}, 7));
        graphics.setColor(powder[2]);
        graphics.fillRect(6 * scale, 10 * scale, 6 * scale, 3 * scale);
        graphics.setColor(powder[3]);
        graphics.fillRect(8 * scale, 8 * scale, 2 * scale, 2 * scale);
    }

    private static void drawQuartzSphere(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x29303B));
        graphics.fillOval(2 * scale, scale, 12 * scale, 12 * scale);
        graphics.fillRect(4 * scale, 12 * scale, 8 * scale, 3 * scale);
        graphics.setColor(new Color(0xC8C0E2));
        graphics.fillOval(3 * scale, 2 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0xF5F0FF));
        graphics.fillRect(5 * scale, 3 * scale, 3 * scale, 4 * scale);
        graphics.setColor(new Color(0x756B91));
        graphics.fillRect(5 * scale, 13 * scale, 6 * scale, scale);
    }

    private static void drawOwletWing(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2C211B));
        graphics.fillPolygon(new Polygon(new int[]{3, 8, 14, 12, 9, 5, 2}, new int[]{3, 2, 5, 9, 13, 14, 9}, 7));
        graphics.setColor(new Color(0x9B754D));
        graphics.fillPolygon(new Polygon(new int[]{4, 8, 12, 10, 8, 5, 3}, new int[]{4, 3, 5, 8, 12, 12, 8}, 7));
        graphics.setColor(new Color(0xE2CC9C));
        graphics.drawLine(5 * scale, 5 * scale, 10 * scale, 8 * scale);
        graphics.drawLine(4 * scale, 7 * scale, 9 * scale, 10 * scale);
    }

    private static void drawLeather(
        final Graphics2D graphics,
        final int scale,
        final Color leather,
        final boolean nullified
    ) {
        graphics.setColor(new Color(0x21191A));
        graphics.fillPolygon(new Polygon(new int[]{3, 6, 10, 14, 12, 13, 9, 5, 2, 3}, new int[]{2, 3, 2, 5, 8, 12, 14, 13, 10, 7}, 10));
        graphics.setColor(leather);
        graphics.fillPolygon(new Polygon(new int[]{4, 6, 10, 12, 11, 12, 9, 5, 3, 4}, new int[]{3, 4, 3, 5, 8, 11, 13, 12, 10, 7}, 10));
        graphics.setColor(nullified ? new Color(0x17151D) : new Color(0x9AA05A));
        graphics.fillRect(6 * scale, 6 * scale, 5 * scale, 2 * scale);
        graphics.fillRect(5 * scale, 9 * scale, 3 * scale, 2 * scale);
        graphics.setColor(nullified ? new Color(0x8C82A0) : new Color(0xC4C77A));
        graphics.fillRect(7 * scale, 6 * scale, 2 * scale, scale);
    }

    private static void drawNullCatalyst(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x111117));
        graphics.fillPolygon(new Polygon(
            new int[]{8 * scale, 10 * scale, 14 * scale, 11 * scale, 8 * scale, 5 * scale, 2 * scale, 6 * scale},
            new int[]{scale, 5 * scale, 8 * scale, 10 * scale, 15 * scale, 11 * scale, 8 * scale, 5 * scale},
            8
        ));
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(new Color(0x4B425F));
        graphics.drawOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0x9E86C6));
        graphics.drawLine(5 * scale, 5 * scale, 11 * scale, 11 * scale);
        graphics.drawLine(11 * scale, 5 * scale, 5 * scale, 11 * scale);
        graphics.setColor(new Color(0x060609));
        graphics.fillOval(6 * scale, 6 * scale, 5 * scale, 5 * scale);
    }

    private static void drawIcyNeedle(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x1C3444));
        graphics.fillPolygon(new Polygon(new int[]{2, 4, 15, 13}, new int[]{13, 15, 2, 1}, 4));
        graphics.setColor(new Color(0x79C8D9));
        graphics.fillPolygon(new Polygon(new int[]{4, 5, 14, 13}, new int[]{13, 14, 3, 2}, 4));
        graphics.setColor(new Color(0xE0FFFF));
        graphics.drawLine(6 * scale, 11 * scale, 12 * scale, 4 * scale);
    }

    private static void drawHeartwoodSplinter(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2B1914));
        graphics.fillPolygon(new Polygon(new int[]{2, 4, 14, 12}, new int[]{13, 15, 2, 3}, 4));
        graphics.setColor(new Color(0x8A4F31));
        graphics.fillPolygon(new Polygon(new int[]{4, 5, 13, 12}, new int[]{13, 14, 3, 4}, 4));
        graphics.setColor(new Color(0xC54D4F));
        graphics.fillRect(7 * scale, 7 * scale, scale, scale);
        graphics.fillRect(9 * scale, 7 * scale, scale, scale);
        graphics.fillRect(8 * scale, 8 * scale, scale, 2 * scale);
    }

    private static void drawHeartItem(
        final Graphics2D graphics,
        final int scale,
        final Color body,
        final Color highlight
    ) {
        graphics.setColor(new Color(0x28161A));
        graphics.fillRect(3 * scale, 3 * scale, 4 * scale, 4 * scale);
        graphics.fillRect(9 * scale, 3 * scale, 4 * scale, 4 * scale);
        graphics.fillPolygon(new Polygon(new int[]{2, 14, 8}, new int[]{6, 6, 15}, 3));
        graphics.setColor(body);
        graphics.fillRect(4 * scale, 4 * scale, 3 * scale, 3 * scale);
        graphics.fillRect(9 * scale, 4 * scale, 3 * scale, 3 * scale);
        graphics.fillPolygon(new Polygon(new int[]{3, 13, 8}, new int[]{6, 6, 13}, 3));
        graphics.setColor(highlight);
        graphics.fillRect(5 * scale, 4 * scale, 2 * scale, 2 * scale);
    }

    private static void drawThreadBundle(
        final Graphics2D graphics,
        final int scale,
        final Color thread,
        final Color highlight
    ) {
        graphics.setColor(new Color(0x211921));
        graphics.fillOval(2 * scale, 3 * scale, 12 * scale, 10 * scale);
        graphics.setColor(thread);
        graphics.drawOval(3 * scale, 4 * scale, 10 * scale, 8 * scale);
        graphics.drawOval(4 * scale, 3 * scale, 8 * scale, 10 * scale);
        graphics.drawLine(3 * scale, 12 * scale, 13 * scale, 3 * scale);
        graphics.drawLine(3 * scale, 4 * scale, 13 * scale, 12 * scale);
        graphics.setColor(highlight);
        graphics.fillRect(5 * scale, 4 * scale, 2 * scale, scale);
        graphics.fillRect(10 * scale, 9 * scale, 2 * scale, scale);
    }

    private static void drawFumeFilter(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x28221D));
        graphics.fillRect(2 * scale, 3 * scale, 12 * scale, 10 * scale);
        graphics.setColor(new Color(0xB38A53));
        graphics.fillRect(3 * scale, 4 * scale, 10 * scale, 8 * scale);
        graphics.setColor(new Color(0x4A4035));
        for (int offset = 4; offset <= 12; offset += 2) {
            graphics.drawLine(3 * scale, offset * scale, 13 * scale, (offset - 4) * scale);
            graphics.drawLine((offset - 2) * scale, 4 * scale, 13 * scale, (16 - offset) * scale);
        }
    }

    private static void drawSkullItem(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2A241F));
        graphics.fillOval(2 * scale, scale, 12 * scale, 11 * scale);
        graphics.fillRect(5 * scale, 10 * scale, 6 * scale, 5 * scale);
        graphics.setColor(new Color(0xC5B791));
        graphics.fillOval(3 * scale, 2 * scale, 10 * scale, 9 * scale);
        graphics.fillRect(6 * scale, 9 * scale, 4 * scale, 4 * scale);
        graphics.setColor(new Color(0x302722));
        graphics.fillRect(5 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(7 * scale, 9 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0xE7D9AD));
        graphics.fillRect(5 * scale, 3 * scale, 3 * scale, 2 * scale);
    }

    private static void drawFoolSkull(final Graphics2D graphics, final int scale) {
        drawSkullItem(graphics, scale);
        graphics.setColor(new Color(0x3A173F));
        graphics.fillRect(3 * scale, scale, 10 * scale, 2 * scale);
        graphics.fillRect(3 * scale, 2 * scale, 3 * scale, 3 * scale);
        graphics.fillRect(10 * scale, 2 * scale, 3 * scale, 3 * scale);
        graphics.setColor(new Color(0xB34C70));
        graphics.fillRect(4 * scale, 2 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(10 * scale, 2 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0xE5B94E));
        graphics.fillRect(3 * scale, 4 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(11 * scale, 4 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(7 * scale, 12 * scale, scale, 2 * scale);
    }

    private static void drawHellhoundHead(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x1B1112));
        graphics.fillPolygon(scale(new Polygon(
            new int[]{2, 5, 8, 11, 14, 13, 11, 10, 6, 5, 3},
            new int[]{1, 3, 2, 3, 1, 9, 12, 15, 15, 12, 9},
            11
        ), scale));
        graphics.setColor(new Color(0x6C2527));
        graphics.fillPolygon(scale(new Polygon(
            new int[]{4, 6, 8, 10, 12, 11, 9, 7, 5, 4},
            new int[]{3, 4, 3, 4, 3, 9, 13, 13, 9, 6},
            10
        ), scale));
        graphics.setColor(new Color(0xE18B39));
        graphics.fillRect(5 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0x261619));
        graphics.fillRect(7 * scale, 9 * scale, 3 * scale, 2 * scale);
        graphics.setColor(new Color(0xEFE2C0));
        graphics.fillRect(6 * scale, 11 * scale, scale, 2 * scale);
        graphics.fillRect(10 * scale, 11 * scale, scale, 2 * scale);
    }

    private static void drawFocusedWill(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x181421));
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(new Color(0x8454B5));
        graphics.drawOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.drawOval(5 * scale, 5 * scale, 6 * scale, 6 * scale);
        graphics.setColor(new Color(0xD8B5F2));
        graphics.fillRect(7 * scale, 2 * scale, 2 * scale, 12 * scale);
        graphics.fillRect(2 * scale, 7 * scale, 12 * scale, 2 * scale);
        graphics.setColor(new Color(0x63D9C8));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawDogTongue(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x382126));
        graphics.fillPolygon(new Polygon(new int[]{5, 11, 12, 10, 8, 6, 4}, new int[]{2, 2, 10, 14, 15, 14, 10}, 7));
        graphics.setColor(new Color(0xB95F75));
        graphics.fillPolygon(new Polygon(new int[]{6, 10, 11, 9, 8, 6, 5}, new int[]{3, 3, 10, 13, 14, 13, 10}, 7));
        graphics.setColor(new Color(0xE99CAD));
        graphics.drawLine(8 * scale, 4 * scale, 8 * scale, 12 * scale);
    }

    private static void drawDisturbedCotton(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x38313B));
        graphics.fillOval(2 * scale, 5 * scale, 7 * scale, 7 * scale);
        graphics.fillOval(6 * scale, 2 * scale, 7 * scale, 8 * scale);
        graphics.fillOval(8 * scale, 7 * scale, 6 * scale, 6 * scale);
        graphics.setColor(new Color(0xD4CDD8));
        graphics.fillOval(3 * scale, 6 * scale, 5 * scale, 5 * scale);
        graphics.fillOval(7 * scale, 3 * scale, 5 * scale, 6 * scale);
        graphics.fillOval(9 * scale, 8 * scale, 4 * scale, 4 * scale);
        graphics.setColor(new Color(0x8A4FA0));
        graphics.drawLine(3 * scale, 13 * scale, 13 * scale, 2 * scale);
        graphics.fillRect(5 * scale, 10 * scale, 2 * scale, 2 * scale);
    }

    private static void drawArcaneFocus(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x171523));
        graphics.fillPolygon(new Polygon(
            new int[]{8, 11, 13, 14, 13, 10, 8, 6, 3, 2, 3, 5},
            new int[]{1, 2, 4, 8, 12, 14, 15, 14, 12, 8, 4, 2},
            12
        ));
        graphics.setColor(new Color(0x7D4BB3));
        graphics.fillPolygon(new Polygon(
            new int[]{8, 11, 12, 13, 12, 10, 8, 6, 4, 3, 4, 5},
            new int[]{2, 3, 5, 8, 11, 13, 14, 13, 11, 8, 5, 3},
            12
        ));
        graphics.setColor(new Color(0x244D55));
        graphics.fillPolygon(new Polygon(
            new int[]{8, 11, 12, 11, 8, 5, 4, 5},
            new int[]{4, 5, 8, 11, 12, 11, 8, 5},
            8
        ));
        graphics.setColor(new Color(0x15212A));
        graphics.fillPolygon(new Polygon(new int[]{4, 8, 12, 8}, new int[]{8, 5, 8, 11}, 4));
        graphics.setColor(new Color(0x68DCD2));
        graphics.fillPolygon(new Polygon(new int[]{5, 8, 11, 8}, new int[]{8, 6, 8, 10}, 4));
        graphics.setColor(new Color(0xEAF7D4));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawDiviner(final Graphics2D graphics, final int scale, final Color magic) {
        graphics.setColor(new Color(0x322117));
        graphics.drawLine(8 * scale, 14 * scale, 8 * scale, 6 * scale);
        graphics.drawLine(8 * scale, 7 * scale, 3 * scale, 2 * scale);
        graphics.drawLine(8 * scale, 7 * scale, 13 * scale, 2 * scale);
        graphics.drawLine(9 * scale, 14 * scale, 9 * scale, 6 * scale);
        graphics.setColor(new Color(0x9A6438));
        graphics.drawLine(8 * scale, 13 * scale, 8 * scale, 7 * scale);
        graphics.drawLine(8 * scale, 6 * scale, 4 * scale, 2 * scale);
        graphics.drawLine(9 * scale, 6 * scale, 12 * scale, 2 * scale);
        graphics.setColor(magic);
        graphics.fillRect(7 * scale, 12 * scale, 3 * scale, 3 * scale);
        graphics.setColor(magic.brighter());
        graphics.fillRect(8 * scale, 12 * scale, scale, scale);
    }

    private static void drawSeedPacket(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x2B241B));
        graphics.fillRect(3 * scale, 2 * scale, 10 * scale, 13 * scale);
        graphics.setColor(new Color(0xC8A765));
        graphics.fillRect(4 * scale, 3 * scale, 8 * scale, 11 * scale);
        graphics.setColor(new Color(0x5C873A));
        graphics.fillRect(7 * scale, 6 * scale, 2 * scale, 6 * scale);
        graphics.fillOval(4 * scale, 5 * scale, 4 * scale, 3 * scale);
        graphics.fillOval(8 * scale, 7 * scale, 4 * scale, 3 * scale);
        graphics.setColor(new Color(0xD8E7A4));
        graphics.fillRect(5 * scale, 5 * scale, 2 * scale, scale);
        graphics.setColor(new Color(0x4A3120));
        graphics.fillRect(4 * scale, 3 * scale, 8 * scale, scale);
    }

    private static void drawBelt(final Graphics2D graphics, final int scale, final boolean biting) {
        graphics.setColor(new Color(0x241711));
        graphics.fillPolygon(new Polygon(new int[]{1, 3, 15, 14, 2, 0}, new int[]{6, 4, 8, 12, 9, 8}, 6));
        graphics.setColor(biting ? new Color(0x6F2A2D) : new Color(0x6D4B2A));
        graphics.fillPolygon(new Polygon(new int[]{2, 4, 14, 13, 3, 1}, new int[]{6, 5, 8, 11, 9, 8}, 6));
        graphics.setColor(new Color(0xD4A34F));
        graphics.fillRect(6 * scale, 6 * scale, 5 * scale, 5 * scale);
        graphics.setColor(new Color(0x2A1B16));
        graphics.fillRect(7 * scale, 7 * scale, 3 * scale, 3 * scale);
        if (biting) {
            graphics.setColor(new Color(0xEFE3C2));
            graphics.fillRect(7 * scale, 7 * scale, scale, scale);
            graphics.fillRect(9 * scale, 7 * scale, scale, scale);
            graphics.fillRect(8 * scale, 9 * scale, scale, scale);
            graphics.setColor(new Color(0xB34B4D));
            graphics.fillRect(8 * scale, 8 * scale, 2 * scale, scale);
        } else {
            graphics.setColor(new Color(0x80A34E));
            graphics.fillRect(8 * scale, 7 * scale, scale, 3 * scale);
        }
    }

    private static void drawClosedBook(
        final Graphics2D graphics,
        final int scale,
        final Color[] colors,
        final boolean bloodMoon
    ) {
        graphics.setColor(colors[0]);
        graphics.fillRect(2 * scale, scale, 12 * scale, 14 * scale);
        graphics.setColor(colors[2]);
        graphics.fillRect(3 * scale, 2 * scale, 10 * scale, 12 * scale);
        graphics.setColor(colors[1]);
        graphics.fillRect(3 * scale, 2 * scale, 2 * scale, 12 * scale);
        graphics.fillRect(5 * scale, 3 * scale, 7 * scale, scale);
        graphics.fillRect(5 * scale, 12 * scale, 7 * scale, scale);
        graphics.setColor(colors[5]);
        graphics.fillRect(3 * scale, 2 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(11 * scale, 2 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(3 * scale, 12 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(11 * scale, 12 * scale, 2 * scale, 2 * scale);
        graphics.setColor(colors[6]);
        if (bloodMoon) {
            graphics.fillRect(6 * scale, 5 * scale, 5 * scale, 6 * scale);
            graphics.setColor(colors[2]);
            graphics.fillRect(8 * scale, 5 * scale, 4 * scale, 4 * scale);
            graphics.setColor(colors[4]);
            graphics.fillRect(10 * scale, 9 * scale, 2 * scale, 2 * scale);
            graphics.fillRect(11 * scale, 11 * scale, scale, scale);
        }
    }

    private static void drawTornObservationPage(final Graphics2D graphics, final int scale) {
        final Polygon outline = new Polygon(
            new int[]{4, 12, 12, 11, 10, 8, 6, 4},
            new int[]{1, 1, 12, 12, 14, 13, 14, 12},
            8
        );
        final Polygon parchment = new Polygon(
            new int[]{5, 11, 11, 10, 9, 8, 6, 5},
            new int[]{2, 2, 11, 11, 13, 12, 13, 11},
            8
        );
        graphics.setColor(INFERNAL[0]);
        graphics.fillPolygon(scale(outline, scale));
        graphics.setColor(new Color(0xE6D0A2));
        graphics.fillPolygon(scale(parchment, scale));
        graphics.setColor(INFERNAL[3]);
        graphics.fillRect(7 * scale, 4 * scale, 3 * scale, 3 * scale);
        graphics.setColor(new Color(0xE6D0A2));
        graphics.fillRect(8 * scale, 4 * scale, 2 * scale, 2 * scale);
        graphics.setColor(INFERNAL[2]);
        graphics.fillRect(6 * scale, 8 * scale, 5 * scale, scale);
        graphics.fillRect(6 * scale, 10 * scale, 4 * scale, scale);
    }

    private static Polygon scale(final Polygon polygon, final int scale) {
        final int[] x = java.util.stream.IntStream.range(0, polygon.npoints)
            .map(index -> polygon.xpoints[index] * scale)
            .toArray();
        final int[] y = java.util.stream.IntStream.range(0, polygon.npoints)
            .map(index -> polygon.ypoints[index] * scale)
            .toArray();
        return new Polygon(x, y, polygon.npoints);
    }

    private static void drawManualBook(final Graphics2D graphics, final int scale, final String id) {
        final Color[] colors = switch (id) {
            case "biomebook2", "ingredient_book_biomes", "ingredient_book_herbology" -> VERDANT;
            case "ingredient_book_burning" -> INFERNAL;
            case "ingredient_book_distilling" -> FROST;
            case "ingredient_book_oven" -> WOOD;
            case "ingredient_book_wands" -> SILVER;
            default -> ARCANE;
        };
        drawClosedBook(graphics, scale, colors, false);
        graphics.setColor(colors[6]);
        switch (id) {
            case "bookcauldron" -> {
                graphics.fillRect(5 * scale, 7 * scale, 6 * scale, scale);
                graphics.fillRect(6 * scale, 8 * scale, 4 * scale, 3 * scale);
                graphics.fillRect(5 * scale, 10 * scale, scale, scale);
                graphics.fillRect(10 * scale, 10 * scale, scale, scale);
            }
            case "biomebook2" -> {
                graphics.fillRect(5 * scale, 6 * scale, 6 * scale, 5 * scale);
                graphics.setColor(colors[2]);
                graphics.fillRect(6 * scale, 7 * scale, 4 * scale, 3 * scale);
                graphics.setColor(colors[5]);
                graphics.fillRect(8 * scale, 5 * scale, scale, 7 * scale);
                graphics.fillRect(4 * scale, 8 * scale, 8 * scale, scale);
            }
            case "ingredient_book_biomes" -> {
                graphics.fillRect(5 * scale, 9 * scale, 6 * scale, 2 * scale);
                graphics.fillRect(7 * scale, 6 * scale, 4 * scale, 3 * scale);
                graphics.fillRect(8 * scale, 5 * scale, 2 * scale, 6 * scale);
            }
            case "ingredient_book_oven" -> drawBookFlame(graphics, scale);
            case "ingredient_book_distilling" -> {
                graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 2 * scale);
                graphics.fillRect(6 * scale, 7 * scale, 4 * scale, scale);
                graphics.fillRect(5 * scale, 8 * scale, 6 * scale, 3 * scale);
                graphics.setColor(colors[4]);
                graphics.fillRect(6 * scale, 9 * scale, 4 * scale, 2 * scale);
            }
            case "ingredient_book_circle_magic" -> {
                graphics.fillRect(6 * scale, 5 * scale, 4 * scale, scale);
                graphics.fillRect(5 * scale, 6 * scale, scale, 4 * scale);
                graphics.fillRect(10 * scale, 6 * scale, scale, 4 * scale);
                graphics.fillRect(6 * scale, 10 * scale, 4 * scale, scale);
                graphics.fillRect(8 * scale, 7 * scale, scale, 2 * scale);
            }
            case "ingredient_book_infusions" -> {
                graphics.fillRect(8 * scale, 5 * scale, scale, 6 * scale);
                graphics.fillRect(5 * scale, 8 * scale, 7 * scale, scale);
                graphics.fillRect(6 * scale, 6 * scale, scale, scale);
                graphics.fillRect(10 * scale, 10 * scale, scale, scale);
            }
            case "ingredient_book_herbology" -> {
                graphics.fillRect(8 * scale, 5 * scale, scale, 6 * scale);
                graphics.fillRect(6 * scale, 6 * scale, 2 * scale, 3 * scale);
                graphics.fillRect(9 * scale, 7 * scale, 2 * scale, 3 * scale);
            }
            case "ingredient_book_wands" -> {
                java.util.stream.IntStream.range(0, 6)
                    .forEach(offset -> graphics.fillRect((5 + offset) * scale, (10 - offset) * scale, scale, scale));
                graphics.fillRect(10 * scale, 5 * scale, 2 * scale, scale);
                graphics.fillRect(11 * scale, 4 * scale, scale, 3 * scale);
            }
            case "ingredient_book_burning" -> drawBookFlame(graphics, scale);
            default -> {
                graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 6 * scale);
                graphics.fillRect(5 * scale, 7 * scale, 6 * scale, 2 * scale);
                graphics.setColor(colors[0]);
                graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
            }
        }
    }

    private static void drawBookFlame(final Graphics2D graphics, final int scale) {
        graphics.fillRect(8 * scale, 5 * scale, scale, 2 * scale);
        graphics.fillRect(7 * scale, 7 * scale, 3 * scale, scale);
        graphics.fillRect(6 * scale, 8 * scale, 5 * scale, 2 * scale);
        graphics.fillRect(7 * scale, 10 * scale, 3 * scale, scale);
    }

    private static void drawSoaringBrew(final Graphics2D graphics, final int scale) {
        graphics.setColor(FROST[5]);
        graphics.fillRect(scale, 7 * scale, 4 * scale, scale);
        graphics.fillRect(2 * scale, 8 * scale, 3 * scale, scale);
        graphics.fillRect(3 * scale, 9 * scale, 2 * scale, scale);
        graphics.fillRect(11 * scale, 7 * scale, 4 * scale, scale);
        graphics.fillRect(11 * scale, 8 * scale, 3 * scale, scale);
        graphics.fillRect(11 * scale, 9 * scale, 2 * scale, scale);
        drawBottle(graphics, "ingredient_brew_soaring", 16 * scale, 16 * scale, scale, FROST);
    }

    private static void drawFlockBrew(final Graphics2D graphics, final int scale, final String id) {
        graphics.setColor(ARCANE[0]);
        graphics.fillRect(scale, 6 * scale, 4 * scale, scale);
        graphics.fillRect(2 * scale, 7 * scale, 4 * scale, scale);
        graphics.fillRect(3 * scale, 8 * scale, 3 * scale, scale);
        graphics.fillRect(11 * scale, 6 * scale, 4 * scale, scale);
        graphics.fillRect(10 * scale, 7 * scale, 4 * scale, scale);
        graphics.fillRect(10 * scale, 8 * scale, 3 * scale, scale);
        drawBottle(graphics, "ingredient_brew_hitchcock", 16 * scale, 16 * scale, scale, ARCANE);
        if (id.equals("ingredient_brew_murder_of_crows")) {
            graphics.setColor(INFERNAL[4]);
            graphics.fillRect(5 * scale, 5 * scale, 2 * scale, scale);
            graphics.fillRect(6 * scale, 6 * scale, 2 * scale, scale);
            graphics.fillRect(10 * scale, 5 * scale, 2 * scale, scale);
            graphics.fillRect(9 * scale, 6 * scale, 2 * scale, scale);
        } else {
            graphics.setColor(INFERNAL[6]);
            graphics.fillRect(7 * scale, 10 * scale, scale, scale);
            graphics.fillRect(9 * scale, 10 * scale, scale, scale);
        }
    }

    private static void drawBoline(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x241511));
        graphics.fillPolygon(new Polygon(
            new int[]{1 * scale, 3 * scale, 10 * scale, 11 * scale, 8 * scale, 2 * scale},
            new int[]{13 * scale, 15 * scale, 9 * scale, 7 * scale, 7 * scale, 12 * scale},
            6
        ));
        graphics.setColor(new Color(0x8F572E));
        graphics.fillPolygon(new Polygon(
            new int[]{3 * scale, 4 * scale, 9 * scale, 10 * scale, 8 * scale, 3 * scale},
            new int[]{13 * scale, 14 * scale, 9 * scale, 8 * scale, 8 * scale, 12 * scale},
            6
        ));
        graphics.setColor(new Color(0xC6964D));
        graphics.fillRect(8 * scale, 8 * scale, 3 * scale, 2 * scale);
        graphics.setColor(new Color(0x27313A));
        graphics.fillPolygon(new Polygon(
            new int[]{8 * scale, 9 * scale, 12 * scale, 15 * scale, 15 * scale, 13 * scale, 10 * scale},
            new int[]{8 * scale, 4 * scale, 1 * scale, 1 * scale, 4 * scale, 7 * scale, 10 * scale},
            7
        ));
        graphics.setColor(new Color(0xC8D6DD));
        graphics.fillPolygon(new Polygon(
            new int[]{9 * scale, 10 * scale, 12 * scale, 14 * scale, 14 * scale, 12 * scale, 10 * scale},
            new int[]{8 * scale, 4 * scale, 2 * scale, 2 * scale, 4 * scale, 6 * scale, 9 * scale},
            7
        ));
        graphics.setColor(new Color(0xF4FFFF));
        graphics.fillRect(12 * scale, 2 * scale, 2 * scale, scale);
        graphics.fillRect(10 * scale, 4 * scale, scale, 3 * scale);
    }

    private static void drawRitualKnife(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x24131A));
        graphics.fillPolygon(new Polygon(
            new int[]{1 * scale, 3 * scale, 9 * scale, 10 * scale, 7 * scale, 2 * scale},
            new int[]{13 * scale, 15 * scale, 9 * scale, 7 * scale, 7 * scale, 12 * scale},
            6
        ));
        graphics.setColor(new Color(0x6C3A2A));
        graphics.fillPolygon(new Polygon(
            new int[]{3 * scale, 4 * scale, 8 * scale, 9 * scale, 7 * scale, 3 * scale},
            new int[]{13 * scale, 14 * scale, 9 * scale, 8 * scale, 8 * scale, 12 * scale},
            6
        ));
        graphics.setColor(new Color(0xB87A42));
        graphics.fillRect(7 * scale, 7 * scale, 5 * scale, 2 * scale);
        graphics.setColor(new Color(0x262B34));
        graphics.fillPolygon(new Polygon(
            new int[]{8 * scale, 12 * scale, 15 * scale, 15 * scale, 13 * scale, 8 * scale},
            new int[]{8 * scale, 2 * scale, 1 * scale, 4 * scale, 6 * scale, 10 * scale},
            6
        ));
        graphics.setColor(new Color(0x7D8B99));
        graphics.fillPolygon(new Polygon(
            new int[]{9 * scale, 12 * scale, 14 * scale, 14 * scale, 12 * scale, 9 * scale},
            new int[]{8 * scale, 3 * scale, 2 * scale, 4 * scale, 6 * scale, 9 * scale},
            6
        ));
        graphics.setColor(new Color(0xE5F1F4));
        graphics.drawLine(10 * scale, 7 * scale, 14 * scale, 2 * scale);
        graphics.setColor(new Color(0x9D263E));
        graphics.fillRect(10 * scale, 5 * scale, scale, 2 * scale);
    }

    private static void drawSilverSword(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x192532));
        graphics.fillPolygon(new Polygon(
            new int[]{1 * scale, 3 * scale, 9 * scale, 10 * scale, 7 * scale, 2 * scale},
            new int[]{13 * scale, 15 * scale, 9 * scale, 7 * scale, 7 * scale, 12 * scale},
            6
        ));
        graphics.setColor(new Color(0x6E4830));
        graphics.fillPolygon(new Polygon(
            new int[]{3 * scale, 4 * scale, 8 * scale, 9 * scale, 7 * scale, 3 * scale},
            new int[]{13 * scale, 14 * scale, 9 * scale, 8 * scale, 8 * scale, 12 * scale},
            6
        ));
        graphics.setColor(new Color(0x75B8D0));
        graphics.fillRect(7 * scale, 7 * scale, 5 * scale, 2 * scale);
        graphics.setColor(new Color(0x1C3C54));
        graphics.fillPolygon(new Polygon(
            new int[]{8 * scale, 12 * scale, 15 * scale, 15 * scale, 13 * scale, 8 * scale},
            new int[]{8 * scale, 2 * scale, 0, 4 * scale, 6 * scale, 10 * scale},
            6
        ));
        graphics.setColor(new Color(0x86CDE4));
        graphics.fillPolygon(new Polygon(
            new int[]{9 * scale, 12 * scale, 14 * scale, 14 * scale, 12 * scale, 9 * scale},
            new int[]{8 * scale, 3 * scale, 1 * scale, 4 * scale, 6 * scale, 9 * scale},
            6
        ));
        graphics.setColor(new Color(0xD8F7FF));
        graphics.drawLine(10 * scale, 7 * scale, 14 * scale, 2 * scale);
    }

    private static void drawTormentedTwine(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x17111D));
        graphics.fillOval(2 * scale, 3 * scale, 9 * scale, 8 * scale);
        graphics.fillOval(5 * scale, 6 * scale, 9 * scale, 7 * scale);
        graphics.setColor(new Color(0x5D416E));
        graphics.drawOval(3 * scale, 4 * scale, 7 * scale, 6 * scale);
        graphics.drawOval(6 * scale, 7 * scale, 7 * scale, 5 * scale);
        graphics.drawLine(3 * scale, 12 * scale, 13 * scale, 3 * scale);
        graphics.setColor(new Color(0xB866E8));
        graphics.fillRect(2 * scale, 2 * scale, scale, 2 * scale);
        graphics.fillRect(13 * scale, 5 * scale, 2 * scale, scale);
        graphics.fillRect(3 * scale, 13 * scale, scale, 2 * scale);
    }

    private static void drawWaystone(
        final Graphics2D graphics,
        final int scale,
        final boolean bound,
        final boolean creatureBound
    ) {
        graphics.setColor(new Color(0x191B21));
        graphics.fillRect(4 * scale, 2 * scale, 8 * scale, 13 * scale);
        graphics.fillRect(3 * scale, 5 * scale, 10 * scale, 9 * scale);
        graphics.setColor(new Color(0x77786F));
        graphics.fillRect(5 * scale, 3 * scale, 6 * scale, 10 * scale);
        graphics.setColor(creatureBound ? new Color(0x9E334C) : bound ? new Color(0x65D5C1) : new Color(0xA65BD4));
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 6 * scale);
        graphics.fillRect(5 * scale, 7 * scale, 6 * scale, 2 * scale);
        graphics.setColor(new Color(0xD9D2B6));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0x376B42));
        graphics.fillRect(3 * scale, 12 * scale, 3 * scale, 3 * scale);
        graphics.fillRect(10 * scale, 11 * scale, 3 * scale, 4 * scale);
    }

    private static void drawPlayerCompass(final Graphics2D graphics, final int scale, final String name) {
        graphics.setColor(new Color(0x15131B));
        graphics.fillOval(scale, scale, 14 * scale, 14 * scale);
        graphics.setColor(new Color(0xB9853E));
        graphics.drawOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.drawOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.fillRect(7 * scale, scale, 2 * scale, 3 * scale);
        graphics.fillRect(7 * scale, 12 * scale, 2 * scale, 3 * scale);
        graphics.fillRect(scale, 7 * scale, 3 * scale, 2 * scale);
        graphics.fillRect(12 * scale, 7 * scale, 3 * scale, 2 * scale);
        final int frame = Integer.parseInt(name.replaceAll("\\D", "").isEmpty()
            ? "0" : name.replaceAll("\\D", ""));
        final double angle = frame * Math.PI * 2.0 / 33.0;
        final int tipX = 8 + (int) Math.round(Math.cos(angle) * 4.0);
        final int tipY = 8 + (int) Math.round(Math.sin(angle) * 4.0);
        graphics.setColor(new Color(0x70D8D0));
        graphics.drawLine(8 * scale, 8 * scale, tipX * scale, tipY * scale);
        graphics.setColor(new Color(0xB52F4A));
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static BufferedImage entityTexture(final String name, final int size) {
        if ("nami".equals(name) && size == 64) {
            return namiSkin();
        }
        if ("naamah".equals(name) && size == 64) {
            return naamahSkin();
        }
        if ("goblin".equals(name) && size == 64) {
            return penguinGoblinTexture(false);
        }
        if ("hobgoblin".equals(name) && size == 64) {
            return penguinGoblinTexture(true);
        }
        final BufferedImage image = image(size, size);
        final Color[] palette = entityPalette(name);
        final RandomGenerator random = random(name);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(palette[1]);
        graphics.fillRect(0, 0, size, size);
        final int unit = Math.max(1, size / 64);
        drawCreatureMaterial(graphics, random, unit, palette);
        final String lower = name.toLowerCase();
        drawCreatureSkin(graphics, unit, palette);
        drawConceptMaterial(graphics, lower, unit, palette);
        if (lower.contains("lycan_villager")) {
            drawVillagerClothes(graphics, unit, palette, true, false);
        } else if (lower.contains("hobgoblin") || lower.equals("goblin")
            || lower.contains("stonebroker") || lower.contains("forgewarden")) {
            drawVillagerClothes(graphics, unit, palette, false, true);
        } else if (lower.equals("imp")) {
            drawImpSkin(graphics, unit, palette);
        } else if (lower.contains("storm_simian")) {
            drawSimianSkin(graphics, unit, palette);
        }
        drawCreatureMotif(graphics, lower, unit, palette);
        graphics.dispose();
        return image;
    }

    private static BufferedImage penguinGoblinTexture(final boolean hobgoblin) {
        final BufferedImage image = image(64, 64);
        final Graphics2D graphics = image.createGraphics();
        final Color shadow = new Color(hobgoblin ? 0x352115 : 0x16341D);
        final Color skin = new Color(hobgoblin ? 0x704729 : 0x3F7B38);
        final Color light = new Color(hobgoblin ? 0xA16C3E : 0x70A84F);
        final Color belly = new Color(hobgoblin ? 0xC9A06B : 0xB5BD70);
        final Color charcoal = new Color(0x242A2D);
        final Color slate = new Color(0x46525A);
        final Color leather = new Color(0x684426);
        final Color leatherLight = new Color(0x987044);
        final Color lamp = new Color(0xF4B942);
        final Color eye = new Color(0x101315);
        final Color eyeLight = new Color(0xF3EEDB);

        graphics.setColor(shadow);
        graphics.fillRect(0, 0, 64, 64);
        paint(graphics, skin, 0, 0, 30, 14, 0, 16, 32, 20, 32, 12, 30, 18);
        paint(graphics, light, 7, 7, 8, 6, 22, 7, 8, 6, 6, 22, 10, 10, 22, 22, 10, 10);
        paint(graphics, belly, 8, 23, 6, 9, 24, 23, 6, 9);
        paint(graphics, eye, 9, 9, 2, 2, 13, 9, 2, 2);
        paint(graphics, eyeLight, 9, 9, 1, 1, 13, 9, 1, 1);
        paint(graphics, lamp, 30, 0, 12, 8, 44, 12, 18, 10);
        paint(graphics, charcoal, 32, 32, 32, 10);
        paint(graphics, slate, 32, 42, 32, 12);
        paint(graphics, leather, 32, 54, 32, 10);
        paint(graphics, leatherLight, 36, 56, 6, 6, 50, 56, 6, 6);
        paint(graphics, lamp, 34, 34, 4, 4);
        graphics.dispose();
        return image;
    }

    private static BufferedImage namiSkin() {
        final BufferedImage image = image(64, 64);
        final Graphics2D graphics = image.createGraphics();
        final Color skin = new Color(0xE7B58D);
        final Color skinLight = new Color(0xF4CBA7);
        final Color skinShadow = new Color(0xC9856F);
        final Color hair = new Color(0x29170F);
        final Color hairMid = new Color(0x4B2B1B);
        final Color hairLight = new Color(0x75462A);
        final Color ivory = new Color(0xEADDB9);
        final Color ivoryLight = new Color(0xF7EDCF);
        final Color ivoryShadow = new Color(0xC8B68F);
        final Color burgundy = new Color(0x742A38);
        final Color burgundyLight = new Color(0x9D4050);
        final Color burgundyDark = new Color(0x42171F);
        final Color leather = new Color(0x3A251A);
        final Color leatherLight = new Color(0x745033);
        final Color leaf = new Color(0x66774A);
        final Color eye = new Color(0x251610);
        final Color iris = new Color(0x7A5032);
        final Color eyeWhite = new Color(0xF8EEE0);
        final Color rose = new Color(0xA95259);

        paint(graphics, hair,
            8, 0, 8, 8, 16, 0, 8, 8, 0, 8, 8, 8, 16, 8, 8, 8, 24, 8, 8, 8);
        paint(graphics, hairMid, 10, 1, 4, 6, 18, 1, 4, 6, 1, 10, 6, 5, 18, 9, 4, 6, 25, 9, 6, 6);
        paint(graphics, hairLight, 11, 1, 2, 5, 19, 2, 2, 4, 2, 10, 2, 4, 19, 10, 2, 4, 27, 10, 2, 4);
        paint(graphics, skin, 8, 8, 8, 8);
        paint(graphics, skinLight, 10, 10, 4, 4);
        paint(graphics, skinShadow, 8, 13, 1, 3, 15, 13, 1, 3, 10, 15, 4, 1);
        paint(graphics, hair, 8, 8, 8, 2, 8, 10, 1, 6, 15, 10, 1, 6, 9, 10, 2, 1, 14, 10, 1, 2);
        paint(graphics, eye, 9, 11, 2, 1, 13, 11, 2, 1);
        paint(graphics, eyeWhite, 9, 12, 1, 1, 14, 12, 1, 1);
        paint(graphics, iris, 10, 12, 1, 1, 13, 12, 1, 1);
        paint(graphics, skinShadow, 11, 13, 1, 1);
        paint(graphics, rose, 11, 14, 2, 1);

        paint(graphics, hairMid,
            41, 0, 6, 2, 40, 2, 2, 5, 46, 2, 2, 5,
            32, 9, 2, 6, 38, 10, 2, 5, 40, 8, 8, 1, 40, 9, 2, 6, 46, 9, 2, 6,
            48, 9, 2, 6, 54, 10, 2, 5, 56, 8, 8, 2, 56, 10, 2, 6, 62, 10, 2, 6);
        paint(graphics, hairLight, 42, 1, 2, 4, 33, 10, 1, 4, 41, 10, 1, 4, 46, 11, 1, 3, 49, 10, 1, 4, 58, 10, 1, 5);
        paint(graphics, burgundy, 59, 10, 2, 5, 57, 11, 2, 3, 61, 11, 2, 3);
        paint(graphics, burgundyLight, 59, 11, 1, 2);

        paint(graphics, ivoryLight, 20, 16, 8, 4);
        paint(graphics, ivoryShadow, 28, 16, 8, 4, 16, 20, 4, 12, 28, 20, 4, 12, 32, 20, 8, 12);
        paint(graphics, ivory, 20, 20, 8, 12);
        paint(graphics, skin, 22, 20, 4, 2);
        paint(graphics, skinShadow, 22, 21, 1, 1, 25, 21, 1, 1);
        paint(graphics, burgundy, 23, 22, 2, 4, 21, 30, 6, 2, 17, 30, 3, 2, 28, 30, 4, 2, 33, 30, 6, 2);
        paint(graphics, burgundyLight, 23, 22, 1, 2);
        paint(graphics, leather, 20, 26, 8, 4, 16, 27, 4, 3, 28, 27, 4, 3, 32, 26, 8, 4);
        paint(graphics, leatherLight, 22, 26, 1, 4, 25, 26, 1, 4, 35, 26, 1, 4);
        paint(graphics, ivoryLight, 21, 36, 6, 10);
        paint(graphics, burgundy, 20, 32, 8, 4, 20, 36, 1, 12, 27, 36, 1, 12, 32, 36, 8, 12);
        paint(graphics, burgundyDark, 28, 32, 8, 4, 16, 36, 4, 12, 28, 36, 4, 12);
        paint(graphics, leaf, 22, 42, 1, 3, 25, 42, 1, 3, 23, 44, 2, 1, 34, 43, 1, 3, 37, 42, 1, 3, 35, 45, 2, 1);
        paint(graphics, burgundyLight, 22, 45, 1, 1, 25, 44, 1, 1, 36, 43, 1, 1);

        paint(graphics, ivoryLight, 44, 16, 3, 4, 36, 48, 3, 4);
        paint(graphics, ivoryShadow, 47, 16, 3, 4, 39, 48, 3, 4);
        paint(graphics, ivoryShadow, 40, 20, 4, 12, 47, 20, 4, 12, 32, 52, 4, 12, 39, 52, 4, 12);
        paint(graphics, ivory, 44, 20, 3, 12, 51, 20, 3, 12, 36, 52, 3, 12, 43, 52, 3, 12);
        paint(graphics, skin, 40, 29, 14, 3, 32, 61, 14, 3);
        paint(graphics, skinLight, 44, 29, 2, 3, 36, 61, 2, 3);
        paint(graphics, ivoryLight,
            44, 32, 3, 4, 40, 36, 4, 8, 44, 36, 3, 9, 47, 36, 4, 8, 51, 36, 3, 9,
            52, 48, 3, 4, 48, 52, 4, 8, 52, 52, 3, 9, 55, 52, 4, 8, 59, 52, 3, 9);
        paint(graphics, ivoryShadow, 47, 32, 3, 4, 55, 48, 3, 4, 40, 43, 14, 2, 48, 59, 14, 2);

        paint(graphics, burgundy, 4, 16, 4, 4, 20, 48, 4, 4, 4, 20, 4, 12, 20, 52, 4, 12);
        paint(graphics, burgundyDark,
            8, 16, 4, 4, 0, 20, 4, 12, 8, 20, 4, 12, 12, 20, 4, 12,
            24, 48, 4, 4, 16, 52, 4, 12, 24, 52, 4, 12, 28, 52, 4, 12);
        paint(graphics, burgundyLight, 5, 21, 1, 8, 21, 53, 1, 8, 13, 22, 1, 7, 29, 54, 1, 7);
        paint(graphics, leather, 0, 29, 16, 3, 16, 61, 16, 3);
        paint(graphics, leatherLight, 4, 29, 2, 1, 20, 61, 2, 1);
        paint(graphics, burgundy,
            4, 32, 4, 4, 4, 36, 1, 9, 7, 36, 1, 9, 12, 36, 4, 9,
            4, 48, 4, 4, 4, 52, 1, 9, 7, 52, 1, 9, 12, 52, 4, 9);
        paint(graphics, ivoryLight, 5, 36, 2, 9, 5, 52, 2, 9);
        paint(graphics, leaf, 5, 41, 1, 3, 6, 42, 1, 1, 5, 57, 1, 3, 6, 58, 1, 1);
        paint(graphics, burgundyLight, 5, 44, 1, 1, 5, 60, 1, 1);
        graphics.dispose();
        return image;
    }

    private static BufferedImage naamahSkin() {
        final BufferedImage image = image(64, 64);
        final Graphics2D graphics = image.createGraphics();
        final Color skin = new Color(0xE2B0A2);
        final Color skinLight = new Color(0xF3C8B8);
        final Color skinShadow = new Color(0xB86F70);
        final Color hair = new Color(0x100A12);
        final Color hairMid = new Color(0x2A1223);
        final Color hairCrimson = new Color(0x681527);
        final Color hairEmber = new Color(0xD23A27);
        final Color black = new Color(0x17131B);
        final Color charcoal = new Color(0x2B222C);
        final Color crimson = new Color(0x741629);
        final Color red = new Color(0xAA2A3C);
        final Color gold = new Color(0xC88B3A);
        final Color goldLight = new Color(0xF1C05B);
        final Color ember = new Color(0xF06427);
        final Color flame = new Color(0xFFB23A);
        final Color eye = new Color(0x26070C);
        final Color iris = new Color(0xDD382F);
        final Color eyeGlow = new Color(0xFFD071);
        final Color lip = new Color(0x84213E);

        paint(graphics, hair,
            8, 0, 8, 8, 16, 0, 8, 8, 0, 8, 8, 8, 16, 8, 8, 8, 24, 8, 8, 8);
        paint(graphics, hairMid, 10, 1, 5, 6, 18, 1, 4, 6, 1, 10, 6, 5, 18, 9, 4, 6, 25, 9, 6, 6);
        paint(graphics, hairCrimson, 12, 5, 2, 2, 20, 4, 2, 3, 3, 12, 2, 3, 19, 12, 2, 3, 27, 11, 2, 4);
        paint(graphics, skin, 8, 8, 8, 8);
        paint(graphics, skinLight, 10, 10, 4, 4);
        paint(graphics, skinShadow, 8, 13, 1, 3, 15, 13, 1, 3, 10, 15, 4, 1);
        paint(graphics, hair, 8, 8, 8, 2, 8, 10, 1, 6, 15, 10, 1, 6, 9, 10, 2, 1, 14, 10, 1, 2);
        paint(graphics, eye, 9, 11, 2, 1, 13, 11, 2, 1);
        paint(graphics, iris, 9, 12, 2, 1, 13, 12, 2, 1);
        paint(graphics, eyeGlow, 10, 12, 1, 1, 13, 12, 1, 1);
        paint(graphics, skinShadow, 11, 13, 1, 1);
        paint(graphics, lip, 11, 14, 2, 1);

        paint(graphics, hairMid,
            41, 0, 6, 2, 40, 2, 2, 5, 46, 2, 2, 5,
            32, 9, 2, 6, 38, 10, 2, 5, 40, 8, 8, 1, 40, 9, 2, 6, 46, 9, 2, 6,
            48, 9, 2, 6, 54, 10, 2, 5, 56, 8, 8, 2, 56, 10, 2, 6, 62, 10, 2, 6);
        paint(graphics, hairCrimson, 42, 2, 2, 4, 33, 11, 1, 4, 41, 11, 1, 4, 46, 10, 1, 5, 49, 11, 1, 4, 58, 10, 2, 6, 61, 12, 1, 4);
        paint(graphics, hairEmber, 43, 5, 1, 2, 41, 14, 1, 2, 46, 14, 1, 2, 59, 14, 1, 2, 62, 14, 1, 2);

        paint(graphics, black, 20, 16, 8, 4, 28, 16, 8, 4, 16, 20, 4, 12, 28, 20, 4, 12, 32, 20, 8, 12);
        paint(graphics, charcoal, 20, 20, 8, 12);
        paint(graphics, skin, 22, 20, 4, 3, 22, 26, 4, 3);
        paint(graphics, skinLight, 23, 20, 2, 2, 23, 26, 2, 2);
        paint(graphics, crimson, 20, 23, 3, 4, 25, 23, 3, 4, 32, 22, 8, 8);
        paint(graphics, red, 21, 23, 1, 3, 26, 23, 1, 3, 35, 23, 1, 6);
        paint(graphics, gold, 22, 23, 1, 4, 25, 23, 1, 4, 21, 29, 6, 1, 32, 29, 8, 1);
        paint(graphics, goldLight, 23, 23, 1, 1, 24, 29, 1, 1);
        paint(graphics, black, 20, 29, 8, 3, 16, 29, 4, 3, 28, 29, 4, 3, 32, 30, 8, 2);
        paint(graphics, crimson, 20, 32, 8, 4, 20, 36, 2, 12, 26, 36, 2, 12, 32, 36, 8, 12);
        paint(graphics, black, 28, 32, 8, 4, 16, 36, 4, 12, 28, 36, 4, 12, 22, 36, 4, 12);
        paint(graphics, gold, 21, 36, 1, 10, 26, 36, 1, 10, 33, 37, 1, 9, 38, 37, 1, 9);
        paint(graphics, red, 23, 40, 2, 3, 35, 39, 2, 4);
        paint(graphics, ember, 23, 41, 2, 1, 35, 42, 2, 1);

        paint(graphics, black,
            44, 16, 3, 4, 47, 16, 3, 4, 40, 20, 4, 12, 44, 20, 3, 12, 47, 20, 4, 12, 51, 20, 3, 12,
            36, 48, 3, 4, 39, 48, 3, 4, 32, 52, 4, 12, 36, 52, 3, 12, 39, 52, 4, 12, 43, 52, 3, 12);
        paint(graphics, charcoal, 44, 20, 1, 8, 51, 20, 1, 8, 36, 52, 1, 8, 43, 52, 1, 8);
        paint(graphics, crimson, 40, 25, 14, 5, 32, 57, 14, 5);
        paint(graphics, red, 44, 26, 3, 3, 36, 58, 3, 3);
        paint(graphics, ember, 40, 29, 14, 2, 32, 61, 14, 2);
        paint(graphics, flame, 44, 30, 3, 1, 36, 62, 3, 1);
        paint(graphics, skin, 40, 31, 14, 1, 32, 63, 14, 1);
        paint(graphics, skinLight, 44, 31, 2, 1, 36, 63, 2, 1);
        paint(graphics, black,
            44, 32, 3, 4, 47, 32, 3, 4, 40, 36, 4, 8, 44, 36, 3, 9, 47, 36, 4, 8, 51, 36, 3, 9,
            52, 48, 3, 4, 55, 48, 3, 4, 48, 52, 4, 8, 52, 52, 3, 9, 55, 52, 4, 8, 59, 52, 3, 9);
        paint(graphics, crimson, 40, 42, 14, 2, 48, 58, 14, 2);
        paint(graphics, ember, 40, 44, 3, 1, 45, 44, 2, 1, 51, 44, 3, 1, 48, 60, 3, 1, 53, 60, 2, 1, 59, 60, 3, 1);
        paint(graphics, flame, 41, 45, 2, 1, 45, 45, 1, 1, 52, 45, 1, 1, 49, 61, 2, 1, 53, 61, 1, 1, 60, 61, 1, 1);
        paint(graphics, gold, 40, 36, 4, 1, 47, 36, 4, 1, 48, 52, 4, 1, 55, 52, 4, 1);

        paint(graphics, black,
            4, 16, 4, 4, 8, 16, 4, 4, 0, 20, 4, 12, 4, 20, 4, 12, 8, 20, 4, 12, 12, 20, 4, 12,
            20, 48, 4, 4, 24, 48, 4, 4, 16, 52, 4, 12, 20, 52, 4, 12, 24, 52, 4, 12, 28, 52, 4, 12);
        paint(graphics, charcoal, 5, 20, 1, 9, 21, 52, 1, 9, 13, 20, 1, 9, 29, 52, 1, 9);
        paint(graphics, crimson, 8, 20, 4, 9, 16, 52, 4, 9, 24, 52, 4, 9);
        paint(graphics, red, 9, 21, 1, 7, 17, 53, 1, 7, 25, 53, 1, 7);
        paint(graphics, gold, 0, 29, 16, 1, 16, 61, 16, 1);
        paint(graphics, charcoal, 0, 30, 16, 2, 16, 62, 16, 2);
        paint(graphics, crimson,
            4, 32, 4, 4, 4, 36, 2, 9, 6, 36, 2, 9, 12, 36, 4, 9,
            4, 48, 4, 4, 4, 52, 2, 9, 6, 52, 2, 9, 12, 52, 4, 9);
        paint(graphics, black, 0, 36, 4, 9, 8, 36, 4, 9, 0, 52, 4, 9, 8, 52, 4, 9);
        paint(graphics, gold, 5, 36, 1, 8, 5, 52, 1, 8);
        paint(graphics, ember, 4, 44, 4, 1, 4, 60, 4, 1);
        paint(graphics, flame, 5, 45, 2, 1, 5, 61, 2, 1);
        graphics.dispose();
        return image;
    }

    private static void paint(final Graphics2D graphics, final Color color, final int... rectangles) {
        graphics.setColor(color);
        for (int index = 0; index < rectangles.length; index += 4) {
            graphics.fillRect(rectangles[index], rectangles[index + 1], rectangles[index + 2], rectangles[index + 3]);
        }
    }

    private static BufferedImage doorTexture(
        final String name,
        final int width,
        final int height,
        final Color[] palette
    ) {
        final BufferedImage image = image(width, height);
        final Graphics2D graphics = image.createGraphics();
        graphics.setBackground(TRANSPARENT);
        graphics.clearRect(0, 0, width, height);
        graphics.setColor(palette[1]);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(palette[4]);
        graphics.fillRect(2, 1, Math.max(1, width - 4), Math.max(1, height - 2));
        graphics.setColor(palette[2]);
        graphics.fillRect(width / 2 - 1, 1, 2, height - 2);
        graphics.fillRect(1, height / 2 - 1, width - 2, 2);
        if (name.toLowerCase().contains("top")) {
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(4, 4, 3, 5);
            graphics.fillRect(width - 7, 4, 3, 5);
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            graphics.setColor(palette[0]);
            graphics.drawRect(3, 3, 4, 6);
            graphics.drawRect(width - 8, 3, 4, 6);
        } else {
            graphics.setColor(palette[0]);
            graphics.fillRect(width - 4, height / 2, 2, 2);
            graphics.setColor(palette[6]);
            graphics.fillRect(width - 3, height / 2, 1, 1);
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage saplingTexture(
        final String name,
        final int width,
        final int height,
        final Color[] palette
    ) {
        final BufferedImage image = image(width, height);
        final Graphics2D graphics = image.createGraphics();
        graphics.setBackground(TRANSPARENT);
        graphics.clearRect(0, 0, width, height);
        graphics.setColor(palette[1]);
        graphics.fillRect(width / 2 - 1, height / 3, 2, height * 2 / 3);
        graphics.setColor(palette[3]);
        graphics.fillRect(width / 2 - 4, height / 3, 4, 3);
        graphics.fillRect(width / 2, height / 4, 5, 3);
        graphics.fillRect(width / 2 - 3, height / 2, 7, 3);
        graphics.setColor(palette[5]);
        graphics.fillRect(width / 2 - 2, height / 4 - 1, 3, 2);
        graphics.dispose();
        return image;
    }

    private static BufferedImage leavesTexture(
        final String name,
        final int width,
        final int height,
        final Color[] palette
    ) {
        final BufferedImage image = image(width, height);
        final RandomGenerator random = random(name);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Math.floorMod(x * 13 + y * 17 + name.hashCode(), 11) < 2) {
                    image.setRGB(x, y, TRANSPARENT.getRGB());
                } else {
                    image.setRGB(x, y, palette[2 + random.nextInt(3)].getRGB());
                }
            }
        }
        final Graphics2D graphics = image.createGraphics();
        graphics.dispose();
        return image;
    }

    private static BufferedImage equipmentLayerTexture(
        final String name,
        final int width,
        final int height
    ) {
        final BufferedImage image = image(width, height);
        final Graphics2D graphics = image.createGraphics();
        graphics.setBackground(TRANSPARENT);
        graphics.clearRect(0, 0, width, height);
        final int unit = Math.max(1, width / 64);
        graphics.setColor(VERDANT[0]);
        graphics.fillRect(8 * unit, 8 * unit, 16 * unit, 8 * unit);
        graphics.fillRect(16 * unit, 20 * unit, 24 * unit, 12 * unit);
        graphics.setColor(VERDANT[3]);
        graphics.fillRect(10 * unit, 9 * unit, 12 * unit, 5 * unit);
        graphics.fillRect(18 * unit, 22 * unit, 20 * unit, 8 * unit);
        graphics.setColor(VERDANT[5]);
        graphics.fillRect(20 * unit, 23 * unit, 3 * unit, 6 * unit);
        graphics.fillRect(33 * unit, 23 * unit, 3 * unit, 6 * unit);
        graphics.setColor(VERDANT[6]);
        graphics.fillRect(27 * unit, 23 * unit, 2 * unit, 2 * unit);
        graphics.dispose();
        return image;
    }

    private static void drawDelvealloyItem(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale
    ) {
        graphics.setColor(VERDANT[0]);
        if (isTool(name)) {
            for (int index = 3; index < 13; index++) {
                graphics.fillRect(index * scale, (15 - index) * scale, 2 * scale, 2 * scale);
            }
            graphics.setColor(VERDANT[5]);
            graphics.fillRect(2 * scale, 2 * scale, 9 * scale, 3 * scale);
        } else if (name.contains("boot")) {
            graphics.fillRect(4 * scale, 3 * scale, 5 * scale, 9 * scale);
            graphics.fillRect(7 * scale, 10 * scale, 6 * scale, 3 * scale);
        } else if (name.contains("helm")) {
            graphics.fillRect(3 * scale, 5 * scale, 10 * scale, 7 * scale);
            graphics.fillRect(2 * scale, 11 * scale, 12 * scale, 2 * scale);
        } else {
            graphics.fillRect(4 * scale, 3 * scale, 8 * scale, 11 * scale);
            graphics.fillRect(2 * scale, 4 * scale, 3 * scale, 7 * scale);
            graphics.fillRect(11 * scale, 4 * scale, 3 * scale, 7 * scale);
        }
        graphics.setColor(VERDANT[3]);
        graphics.drawLine(5 * scale, 5 * scale, 10 * scale, 10 * scale);
        graphics.setColor(VERDANT[6]);
        graphics.fillRect(7 * scale, 6 * scale, 2 * scale, 2 * scale);
    }

    private static void drawWood(
        final Graphics2D graphics,
        final int width,
        final int height,
        final Color[] palette,
        final RandomGenerator random
    ) {
        graphics.setColor(palette[0]);
        for (int y = 3; y < height; y += 4) graphics.drawLine(0, y, width - 1, y);
        graphics.setColor(palette[5]);
        for (int y = 1; y < height; y += 4) {
            final int start = random.nextInt(Math.max(1, width / 2));
            graphics.drawLine(start, y, Math.min(width - 1, start + width / 3), y);
        }
    }

    private static void drawOre(
        final Graphics2D graphics,
        final int width,
        final int height,
        final Color[] palette,
        final RandomGenerator random
    ) {
        graphics.setColor(palette[5]);
        for (int index = 0; index < Math.max(5, width / 2); index++) {
            final int x = random.nextInt(width);
            final int y = random.nextInt(height);
            graphics.fillRect(x, y, 2, 2);
        }
        graphics.setColor(palette[6]);
        graphics.fillRect(width / 2, height / 3, 1, Math.max(1, height / 4));
    }

    private static void drawFoliage(
        final Graphics2D graphics,
        final int width,
        final int height,
        final Color[] palette,
        final RandomGenerator random
    ) {
        graphics.setColor(palette[4]);
        for (int index = 0; index < Math.max(8, width); index++) {
            graphics.fillRect(random.nextInt(width), random.nextInt(height), 2, 2);
        }
        graphics.setColor(palette[6]);
        graphics.drawLine(width / 2, 1, width / 2, height - 2);
    }

    private static void drawPortal(
        final Graphics2D graphics,
        final int width,
        final int height,
        final Color[] palette,
        final RandomGenerator random
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(1, 1, width - 2, height - 2);
        graphics.setColor(palette[5]);
        for (int index = 0; index < Math.max(6, width / 2); index++) {
            graphics.fillRect(random.nextInt(width), random.nextInt(height), 1, 2);
        }
        graphics.setColor(palette[6]);
        graphics.drawOval(3, 2, Math.max(1, width - 7), Math.max(1, height - 5));
    }

    private static void drawRunes(
        final Graphics2D graphics,
        final int width,
        final int height,
        final Color[] palette,
        final RandomGenerator random
    ) {
        graphics.setColor(palette[0]);
        graphics.drawRect(1, 1, width - 3, height - 3);
        graphics.setColor(palette[5]);
        graphics.drawOval(3, 3, Math.max(1, width - 7), Math.max(1, height - 7));
        graphics.drawLine(width / 2, 3, width / 2, height - 4);
        graphics.drawLine(3, height / 2, width - 4, height / 2);
        graphics.setColor(palette[6]);
        graphics.fillRect(random.nextInt(Math.max(1, width - 2)) + 1, height / 2, 2, 1);
    }

    private static void drawGlass(
        final Graphics2D graphics,
        final int width,
        final int height,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.drawRect(0, 0, width - 1, height - 1);
        graphics.setColor(palette[5]);
        graphics.drawLine(2, height - 3, width - 3, 2);
        graphics.setColor(palette[6]);
        graphics.drawLine(3, height - 3, width - 3, 3);
    }

    private static void drawMasonry(
        final Graphics2D graphics,
        final int width,
        final int height,
        final Color[] palette,
        final RandomGenerator random
    ) {
        graphics.setColor(palette[0]);
        for (int y = 4; y < height; y += 4) graphics.drawLine(0, y, width - 1, y);
        for (int y = 0; y < height; y += 4) {
            final int offset = (y / 4 & 1) == 0 ? 3 : 7;
            for (int x = offset; x < width; x += 8) graphics.drawLine(x, y, x, Math.min(height - 1, y + 3));
        }
        graphics.setColor(palette[4]);
        graphics.fillRect(random.nextInt(width), random.nextInt(height), 2, 1);
    }

    private static void drawSpawnEgg(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        final Polygon egg = new Polygon(
            new int[]{width / 2, width - 4 * scale, width - 2 * scale, width - 3 * scale, 3 * scale, 2 * scale, 4 * scale},
            new int[]{scale, 3 * scale, 7 * scale, height - 2 * scale, height - 2 * scale, 7 * scale, 3 * scale},
            7
        );
        graphics.setColor(new Color(0x17151C));
        graphics.fillPolygon(egg);
        graphics.setColor(palette[2]);
        graphics.fillOval(3 * scale, 2 * scale, Math.max(scale, width - 6 * scale), Math.max(scale, height - 4 * scale));
        graphics.setColor(palette[4]);
        final RandomGenerator random = random(name);
        for (int index = 0; index < 7; index++) {
            graphics.fillRect((4 + random.nextInt(8)) * scale, (3 + random.nextInt(10)) * scale, scale, scale);
        }
        graphics.setColor(palette[0]);
        graphics.fillRect(5 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(7 * scale, 10 * scale, 2 * scale, scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(6 * scale, 6 * scale, scale, scale);
        graphics.fillRect(10 * scale, 6 * scale, scale, scale);
    }

    private static void drawBottle(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(new Color(0x171A24));
        graphics.fillRect(6 * scale, scale, 4 * scale, 4 * scale);
        graphics.fillRect(4 * scale, 4 * scale, 8 * scale, 3 * scale);
        graphics.fillRect(2 * scale, 6 * scale, 12 * scale, 7 * scale);
        graphics.fillRect(4 * scale, 13 * scale, 8 * scale, 2 * scale);
        graphics.setColor(new Color(0xD9EAF0));
        graphics.fillRect(7 * scale, 3 * scale, 2 * scale, 3 * scale);
        graphics.fillRect(4 * scale, 6 * scale, 8 * scale, scale);
        graphics.fillRect(3 * scale, 7 * scale, scale, 5 * scale);
        graphics.fillRect(12 * scale, 7 * scale, scale, 5 * scale);
        graphics.fillRect(5 * scale, 13 * scale, 6 * scale, scale);
        graphics.setColor(palette[3]);
        graphics.fillRect(4 * scale, 9 * scale, 8 * scale, 4 * scale);
        graphics.fillRect(5 * scale, 8 * scale, 6 * scale, scale);
        graphics.setColor(palette[5]);
        graphics.fillRect(5 * scale, 9 * scale, 2 * scale, scale);
        graphics.fillRect((7 + Math.floorMod(name.hashCode(), 3)) * scale, 11 * scale, scale, scale);
        graphics.setColor(new Color(0x9A6235));
        graphics.fillRect(6 * scale, scale, 4 * scale, 2 * scale);
    }

    private static void drawDoorItem(
        final Graphics2D graphics,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(3 * scale, scale, 10 * scale, 14 * scale);
        graphics.setColor(palette[4]);
        graphics.fillRect(4 * scale, 2 * scale, 8 * scale, 12 * scale);
        graphics.setColor(palette[2]);
        graphics.drawRect(5 * scale, 3 * scale, 5 * scale, 4 * scale);
        graphics.drawRect(5 * scale, 9 * scale, 5 * scale, 3 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(10 * scale, 8 * scale, scale, scale);
    }

    private static void drawBlockItem(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        final String id = name.toLowerCase().replaceFirst("\\.png$", "");
        if (id.equals("pitgrass")) {
            drawGrassBlockItem(graphics, scale);
        } else if (id.equals("alluringskull")) {
            drawAlluringSkullItem(graphics, scale);
        } else if (id.equals("crystalball")) {
            drawCrystalBallItem(graphics, scale);
        } else if (id.equals("wolfhead")) {
            drawWolfHeadItem(graphics, scale);
        } else if (id.contains("door")) {
            drawDoorItem(graphics, width, height, scale, palette);
        } else if (id.contains("statue")) {
            drawStatueItem(graphics, id, scale, palette);
        } else if (id.contains("altar")) {
            drawAltarItem(graphics, id, scale, palette);
        } else if (id.contains("cauldron") || id.contains("kettle") || id.contains("crucible")
            || id.contains("silvervat") || id.equals("chalice")) {
            drawVesselItem(graphics, id, scale, palette);
        } else if (id.contains("trap")) {
            drawTrapItem(graphics, scale, palette);
        } else if (id.contains("skull") || id.contains("wolfhead")) {
            drawHeadItem(graphics, id, scale, palette);
        } else if (id.contains("crystalball") || id.contains("glowglobe") || id.contains("demonheart")) {
            drawRelicItem(graphics, id, scale, palette);
        } else if (id.contains("paradox_egg")) {
            drawParadoxEggItem(graphics, scale, palette);
        } else if (id.contains("mirror")) {
            drawMirrorItem(graphics, scale, palette);
        } else if (id.contains("portal")) {
            drawPortalItem(graphics, scale, palette);
        } else if (id.contains("scarecrow") || id.equals("trent")) {
            drawEffigyItem(graphics, id, scale, palette);
        } else if (id.contains("dreamcatcher") || id.contains("spinningwheel")) {
            drawWheelItem(graphics, id, scale, palette);
        } else if (id.contains("candelabra") || id.contains("brazier")) {
            drawLightItem(graphics, id, scale, palette);
        } else if (id.contains("oven") || id.contains("distillery") || id.contains("funnel")
            || id.contains("collector")) {
            drawMachineItem(graphics, id, scale, palette);
        } else if (id.contains("shelf") || id.contains("chest") || id.contains("coffin")) {
            drawContainerItem(graphics, id, scale, palette);
        } else if (isPlantIngredient(id) || id.contains("sapling") || id.contains("vine")
            || id.contains("moss") || id.contains("bramble") || id.equals("web")) {
            drawHerb(graphics, id, width, height, scale, palette);
        } else {
            drawIsometricBlock(graphics, id, scale, palette);
        }
    }

    private static void drawGrassBlockItem(final Graphics2D graphics, final int scale) {
        final Color[] grass = colors("18261A", "3A2B20", "5B3E28", "386B35", "5C9A43", "83BE56", "B0D979");
        drawIsometricBlock(graphics, "grass", scale, grass);
        graphics.setColor(grass[5]);
        graphics.fillRect(3 * scale, 6 * scale, 5 * scale, 2 * scale);
        graphics.fillRect(8 * scale, 6 * scale, 5 * scale, 2 * scale);
        graphics.setColor(grass[1]);
        graphics.fillRect(4 * scale, 9 * scale, 2 * scale, 3 * scale);
        graphics.fillRect(10 * scale, 9 * scale, 2 * scale, 3 * scale);
    }

    private static void drawAlluringSkullItem(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x24221F));
        graphics.fillRect(3 * scale, 3 * scale, 10 * scale, 9 * scale);
        graphics.fillRect(5 * scale, 12 * scale, 6 * scale, 3 * scale);
        graphics.setColor(new Color(0xD8D0B5));
        graphics.fillRect(4 * scale, 4 * scale, 8 * scale, 7 * scale);
        graphics.fillRect(6 * scale, 11 * scale, 4 * scale, 3 * scale);
        graphics.setColor(new Color(0x17211A));
        graphics.fillRect(5 * scale, 6 * scale, 2 * scale, 3 * scale);
        graphics.fillRect(9 * scale, 6 * scale, 2 * scale, 3 * scale);
        graphics.fillRect(7 * scale, 9 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0x72D44A));
        graphics.fillRect(6 * scale, 7 * scale, scale, scale);
        graphics.fillRect(10 * scale, 7 * scale, scale, scale);
        graphics.setColor(new Color(0x8A8069));
        graphics.fillRect(6 * scale, 12 * scale, scale, 2 * scale);
        graphics.fillRect(8 * scale, 12 * scale, scale, 2 * scale);
        graphics.fillRect(10 * scale, 12 * scale, scale, 2 * scale);
    }

    private static void drawCrystalBallItem(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x17151F));
        graphics.fillRect(3 * scale, 13 * scale, 10 * scale, 2 * scale);
        graphics.fillRect(6 * scale, 10 * scale, 4 * scale, 3 * scale);
        graphics.fillOval(2 * scale, scale, 12 * scale, 12 * scale);
        graphics.setColor(new Color(0x493B68));
        graphics.fillOval(3 * scale, 2 * scale, 10 * scale, 10 * scale);
        graphics.setColor(new Color(0x7953C6));
        graphics.fillOval(5 * scale, 4 * scale, 6 * scale, 6 * scale);
        graphics.setColor(new Color(0xB9ECF2));
        graphics.fillRect(5 * scale, 3 * scale, 2 * scale, 3 * scale);
        graphics.setColor(new Color(0x7A4B2D));
        graphics.fillRect(4 * scale, 13 * scale, 8 * scale, scale);
    }

    private static void drawWolfHeadItem(final Graphics2D graphics, final int scale) {
        graphics.setColor(new Color(0x161A1D));
        graphics.fillRect(3 * scale, 4 * scale, 10 * scale, 9 * scale);
        graphics.fillRect(2 * scale, scale, 4 * scale, 6 * scale);
        graphics.fillRect(10 * scale, scale, 4 * scale, 6 * scale);
        graphics.setColor(new Color(0x535C61));
        graphics.fillRect(4 * scale, 4 * scale, 8 * scale, 7 * scale);
        graphics.fillRect(6 * scale, 10 * scale, 5 * scale, 4 * scale);
        graphics.setColor(new Color(0xA9B2B3));
        graphics.fillRect(5 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(10 * scale, 6 * scale, 2 * scale, 2 * scale);
        graphics.setColor(new Color(0x151418));
        graphics.fillRect(8 * scale, 11 * scale, 2 * scale, 2 * scale);
    }

    private static void drawIsometricBlock(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        final Polygon top = new Polygon(
            new int[]{2 * scale, 8 * scale, 14 * scale, 8 * scale},
            new int[]{5 * scale, 2 * scale, 5 * scale, 8 * scale},
            4
        );
        final Polygon left = new Polygon(
            new int[]{2 * scale, 8 * scale, 8 * scale, 2 * scale},
            new int[]{5 * scale, 8 * scale, 15 * scale, 12 * scale},
            4
        );
        final Polygon right = new Polygon(
            new int[]{8 * scale, 14 * scale, 14 * scale, 8 * scale},
            new int[]{8 * scale, 5 * scale, 12 * scale, 15 * scale},
            4
        );
        graphics.setColor(palette[4]);
        graphics.fillPolygon(top);
        graphics.setColor(palette[2]);
        graphics.fillPolygon(left);
        graphics.setColor(palette[3]);
        graphics.fillPolygon(right);
        graphics.setColor(palette[0]);
        graphics.drawPolygon(top);
        graphics.drawPolygon(left);
        graphics.drawPolygon(right);
        graphics.setColor(palette[6]);
        final int motif = Math.floorMod(name.hashCode(), 4);
        graphics.fillRect((4 + motif) * scale, (8 + motif) * scale, 2 * scale, 2 * scale);
    }

    private static void drawStatueItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(2 * scale, 13 * scale, 12 * scale, 2 * scale);
        graphics.fillRect(4 * scale, 11 * scale, 8 * scale, 2 * scale);
        graphics.setColor(palette[2]);
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, 7 * scale);
        graphics.fillRect(6 * scale, 2 * scale, 4 * scale, 4 * scale);
        graphics.fillRect(3 * scale, 6 * scale, 3 * scale, 5 * scale);
        graphics.fillRect(10 * scale, 6 * scale, 3 * scale, 5 * scale);
        graphics.setColor(palette[5]);
        if (name.contains("broken")) {
            graphics.drawLine(4 * scale, 4 * scale, 11 * scale, 11 * scale);
            graphics.drawLine(9 * scale, 3 * scale, 7 * scale, 8 * scale);
        } else if (name.contains("occluded")) {
            graphics.fillRect(6 * scale, 7 * scale, 4 * scale, 3 * scale);
            graphics.fillRect(7 * scale, 3 * scale, 2 * scale, 2 * scale);
        } else if (name.contains("goddess")) {
            graphics.fillRect(3 * scale, 3 * scale, 2 * scale, 5 * scale);
            graphics.fillRect(11 * scale, 3 * scale, 2 * scale, 5 * scale);
        } else {
            graphics.fillRect(3 * scale, 3 * scale, 3 * scale, 2 * scale);
            graphics.fillRect(10 * scale, 3 * scale, 3 * scale, 2 * scale);
        }
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawAltarItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(scale, 12 * scale, 14 * scale, 3 * scale);
        graphics.setColor(palette[2]);
        graphics.fillRect(2 * scale, 7 * scale, 12 * scale, 6 * scale);
        graphics.setColor(palette[4]);
        graphics.fillRect(scale, 5 * scale, 14 * scale, 3 * scale);
        graphics.setColor(palette[0]);
        graphics.fillRect(5 * scale, 4 * scale, 6 * scale, 2 * scale);
        graphics.setColor(palette[5]);
        graphics.fillRect(6 * scale, 4 * scale, 4 * scale, scale);
        graphics.fillRect(2 * scale, 2 * scale, 2 * scale, 4 * scale);
        graphics.fillRect(12 * scale, 2 * scale, 2 * scale, 4 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(2 * scale, scale, 2 * scale, scale);
        graphics.fillRect(12 * scale, scale, 2 * scale, scale);
        if (name.contains("wolf")) {
            graphics.fillRect(7 * scale, 8 * scale, 2 * scale, 3 * scale);
        }
    }

    private static void drawVesselItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(2 * scale, 4 * scale, 12 * scale, 3 * scale);
        graphics.fillRect(3 * scale, 6 * scale, 10 * scale, 7 * scale);
        graphics.fillRect(4 * scale, 13 * scale, 3 * scale, 2 * scale);
        graphics.fillRect(9 * scale, 13 * scale, 3 * scale, 2 * scale);
        graphics.setColor(palette[3]);
        graphics.fillRect(4 * scale, 6 * scale, 8 * scale, 5 * scale);
        graphics.setColor(palette[5]);
        graphics.fillRect(4 * scale, 5 * scale, 8 * scale, 2 * scale);
        if (name.contains("chalice")) {
            graphics.clearRect(3 * scale, 10 * scale, 10 * scale, 5 * scale);
            graphics.setColor(palette[0]);
            graphics.fillRect(7 * scale, 9 * scale, 2 * scale, 4 * scale);
            graphics.fillRect(4 * scale, 13 * scale, 8 * scale, 2 * scale);
        }
    }

    private static void drawTrapItem(
        final Graphics2D graphics,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(scale, 11 * scale, 14 * scale, 3 * scale);
        graphics.fillRect(scale, 5 * scale, 2 * scale, 7 * scale);
        graphics.fillRect(13 * scale, 5 * scale, 2 * scale, 7 * scale);
        graphics.setColor(palette[4]);
        for (int x = 3; x <= 11; x += 4) {
            graphics.fillPolygon(new Polygon(
                new int[]{x * scale, (x + 2) * scale, (x + 1) * scale},
                new int[]{11 * scale, 11 * scale, 7 * scale},
                3
            ));
        }
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 11 * scale, 2 * scale, 2 * scale);
    }

    private static void drawHeadItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(3 * scale, 4 * scale, 10 * scale, 9 * scale);
        graphics.fillRect(5 * scale, 12 * scale, 6 * scale, 3 * scale);
        if (name.contains("wolf")) {
            graphics.fillRect(2 * scale, 2 * scale, 4 * scale, 5 * scale);
            graphics.fillRect(10 * scale, 2 * scale, 4 * scale, 5 * scale);
        }
        graphics.setColor(palette[5]);
        graphics.fillRect(5 * scale, 7 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(10 * scale, 7 * scale, 2 * scale, 2 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 10 * scale, 3 * scale, 2 * scale);
    }

    private static void drawRelicItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(3 * scale, 13 * scale, 10 * scale, 2 * scale);
        graphics.fillRect(6 * scale, 10 * scale, 4 * scale, 3 * scale);
        graphics.setColor(palette[3]);
        if (name.contains("heart")) {
            graphics.fillRect(3 * scale, 4 * scale, 5 * scale, 5 * scale);
            graphics.fillRect(8 * scale, 4 * scale, 5 * scale, 5 * scale);
            graphics.fillPolygon(new Polygon(
                new int[]{3 * scale, 13 * scale, 8 * scale},
                new int[]{8 * scale, 8 * scale, 13 * scale},
                3
            ));
        } else {
            graphics.fillOval(3 * scale, 2 * scale, 10 * scale, 10 * scale);
            graphics.setColor(palette[5]);
            graphics.fillRect(5 * scale, 4 * scale, 2 * scale, 4 * scale);
        }
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 6 * scale, 2 * scale, 2 * scale);
    }

    private static void drawParadoxEggItem(
        final Graphics2D graphics,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(3 * scale, 13 * scale, 10 * scale, 2 * scale);
        graphics.setColor(palette[3]);
        graphics.fillRect(5 * scale, 5 * scale, 6 * scale, 8 * scale);
        graphics.fillRect(6 * scale, 2 * scale, 4 * scale, 3 * scale);
        graphics.fillRect(4 * scale, 8 * scale, 8 * scale, 4 * scale);
        graphics.setColor(palette[5]);
        graphics.drawLine(5 * scale, 6 * scale, 10 * scale, 11 * scale);
        graphics.drawLine(10 * scale, 5 * scale, 7 * scale, 9 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawMirrorItem(
        final Graphics2D graphics,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(2 * scale, scale, 12 * scale, 14 * scale);
        graphics.setColor(palette[4]);
        graphics.fillRect(4 * scale, 3 * scale, 8 * scale, 10 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(5 * scale, 4 * scale, 2 * scale, 5 * scale);
        graphics.fillRect(7 * scale, 4 * scale, 3 * scale, 2 * scale);
        graphics.setColor(palette[2]);
        graphics.fillRect(7 * scale, 14 * scale, 2 * scale, 2 * scale);
    }

    private static void drawPortalItem(
        final Graphics2D graphics,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(2 * scale, 13 * scale, 12 * scale, 2 * scale);
        graphics.fillRect(2 * scale, 2 * scale, 3 * scale, 12 * scale);
        graphics.fillRect(11 * scale, 2 * scale, 3 * scale, 12 * scale);
        graphics.fillRect(4 * scale, scale, 8 * scale, 3 * scale);
        graphics.setColor(palette[3]);
        graphics.fillRect(5 * scale, 4 * scale, 6 * scale, 9 * scale);
        graphics.setColor(palette[5]);
        graphics.fillRect(6 * scale, 5 * scale, scale, 6 * scale);
        graphics.fillRect(9 * scale, 6 * scale, scale, 5 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 7 * scale, 2 * scale, 2 * scale);
    }

    private static void drawEffigyItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        if (name.equals("trent")) {
            graphics.setColor(new Color(0x281B12));
            graphics.fillRect(6 * scale, 3 * scale, 4 * scale, 12 * scale);
            graphics.fillRect(2 * scale, 6 * scale, 12 * scale, 3 * scale);
            graphics.setColor(new Color(0x765033));
            graphics.fillRect(7 * scale, 4 * scale, 2 * scale, 10 * scale);
            graphics.fillRect(3 * scale, 7 * scale, 10 * scale, scale);
            graphics.setColor(new Color(0x4D783B));
            graphics.fillRect(scale, 3 * scale, 5 * scale, 4 * scale);
            graphics.fillRect(10 * scale, 2 * scale, 5 * scale, 5 * scale);
            graphics.setColor(new Color(0xA7C963));
            graphics.fillRect(2 * scale, 3 * scale, 2 * scale, 2 * scale);
            graphics.fillRect(12 * scale, 3 * scale, 2 * scale, 2 * scale);
            return;
        }
        graphics.setColor(new Color(0x392516));
        graphics.fillRect(7 * scale, 3 * scale, 2 * scale, 12 * scale);
        graphics.fillRect(2 * scale, 7 * scale, 12 * scale, 2 * scale);
        graphics.setColor(new Color(0xB78C4C));
        graphics.fillRect(5 * scale, 3 * scale, 6 * scale, 5 * scale);
        graphics.fillRect(4 * scale, 8 * scale, 8 * scale, 5 * scale);
        graphics.setColor(new Color(0xE0C174));
        graphics.fillRect(3 * scale, 2 * scale, 10 * scale, 2 * scale);
        graphics.fillRect(5 * scale, scale, 6 * scale, 2 * scale);
        graphics.setColor(new Color(0x3B2D20));
        graphics.fillRect(6 * scale, 5 * scale, scale, scale);
        graphics.fillRect(9 * scale, 5 * scale, scale, scale);
        graphics.setColor(new Color(0xA33C32));
        graphics.fillRect(4 * scale, 8 * scale, 8 * scale, 2 * scale);
    }

    private static void drawWheelItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.drawOval(2 * scale, scale, 12 * scale, 12 * scale);
        graphics.drawOval(3 * scale, 2 * scale, 10 * scale, 10 * scale);
        graphics.drawLine(8 * scale, 2 * scale, 8 * scale, 12 * scale);
        graphics.drawLine(3 * scale, 7 * scale, 13 * scale, 7 * scale);
        graphics.drawLine(4 * scale, 3 * scale, 12 * scale, 11 * scale);
        graphics.drawLine(12 * scale, 3 * scale, 4 * scale, 11 * scale);
        graphics.fillRect(2 * scale, 13 * scale, 12 * scale, 2 * scale);
        graphics.setColor(palette[5]);
        graphics.fillRect(7 * scale, 6 * scale, 3 * scale, 3 * scale);
        if (name.contains("dream")) {
            graphics.fillRect(4 * scale, 12 * scale, scale, 4 * scale);
            graphics.fillRect(11 * scale, 12 * scale, scale, 4 * scale);
        }
    }

    private static void drawLightItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(3 * scale, 13 * scale, 10 * scale, 2 * scale);
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 9 * scale);
        graphics.fillRect(3 * scale, 8 * scale, 10 * scale, 2 * scale);
        graphics.setColor(palette[4]);
        if (name.contains("candelabra")) {
            graphics.fillRect(2 * scale, 3 * scale, 3 * scale, 6 * scale);
            graphics.fillRect(7 * scale, scale, 3 * scale, 7 * scale);
            graphics.fillRect(12 * scale, 3 * scale, 3 * scale, 6 * scale);
        } else {
            graphics.fillRect(3 * scale, 7 * scale, 10 * scale, 4 * scale);
        }
        graphics.setColor(palette[6]);
        graphics.fillRect(3 * scale, 2 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(8 * scale, 0, 2 * scale, 2 * scale);
        graphics.fillRect(13 * scale, 2 * scale, 2 * scale, 2 * scale);
    }

    private static void drawMachineItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(2 * scale, 4 * scale, 12 * scale, 11 * scale);
        graphics.setColor(palette[2]);
        graphics.fillRect(3 * scale, 5 * scale, 10 * scale, 8 * scale);
        graphics.setColor(palette[5]);
        graphics.fillRect(5 * scale, 7 * scale, 6 * scale, 4 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(4 * scale, 3 * scale, 2 * scale, 2 * scale);
        graphics.fillRect(10 * scale, 2 * scale, 2 * scale, 3 * scale);
        graphics.fillRect((3 + Math.floorMod(name.hashCode(), 7)) * scale, 12 * scale, 2 * scale, scale);
    }

    private static void drawContainerItem(
        final Graphics2D graphics,
        final String name,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        if (name.contains("coffin")) {
            graphics.fillPolygon(new Polygon(
                new int[]{5 * scale, 11 * scale, 14 * scale, 11 * scale, 5 * scale, 2 * scale},
                new int[]{scale, 3 * scale, 8 * scale, 14 * scale, 15 * scale, 8 * scale},
                6
            ));
            graphics.setColor(palette[4]);
            graphics.fillRect(7 * scale, 4 * scale, 2 * scale, 8 * scale);
            graphics.fillRect(5 * scale, 7 * scale, 6 * scale, 2 * scale);
            return;
        }
        graphics.fillRect(2 * scale, 5 * scale, 12 * scale, 10 * scale);
        graphics.setColor(palette[3]);
        graphics.fillRect(3 * scale, 6 * scale, 10 * scale, 7 * scale);
        graphics.setColor(palette[5]);
        graphics.fillRect(scale, 3 * scale, 14 * scale, 4 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 8 * scale, 2 * scale, 3 * scale);
    }

    private static void drawDoll(
        final Graphics2D graphics,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillOval(5 * scale, scale, 6 * scale, 6 * scale);
        graphics.fillRect(5 * scale, 6 * scale, 6 * scale, 6 * scale);
        graphics.fillRect(3 * scale, 7 * scale, 2 * scale, 6 * scale);
        graphics.fillRect(11 * scale, 7 * scale, 2 * scale, 6 * scale);
        graphics.fillRect(5 * scale, 12 * scale, 2 * scale, 3 * scale);
        graphics.fillRect(9 * scale, 12 * scale, 2 * scale, 3 * scale);
        graphics.setColor(palette[5]);
        graphics.drawLine(5 * scale, 7 * scale, 10 * scale, 11 * scale);
        graphics.drawLine(10 * scale, 7 * scale, 5 * scale, 11 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 3 * scale, scale, scale);
    }

    private static void drawBook(
        final Graphics2D graphics,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        graphics.fillRect(2 * scale, 3 * scale, 12 * scale, 10 * scale);
        graphics.setColor(palette[3]);
        graphics.fillRect(3 * scale, 4 * scale, 5 * scale, 8 * scale);
        graphics.fillRect(8 * scale, 4 * scale, 5 * scale, 8 * scale);
        graphics.setColor(palette[6]);
        graphics.drawLine(8 * scale, 4 * scale, 8 * scale, 12 * scale);
        graphics.fillRect(5 * scale, 6 * scale, scale, 3 * scale);
    }

    private static boolean isEquipment(final String name) {
        return Stream.of("hat", "helm", "robe", "coat", "boot", "legging", "belt", "girdle", "slipper", "shoe", "quiver")
            .anyMatch(name::contains);
    }

    private static boolean isTool(final String name) {
        return Stream.of("sword", "knife", "boline", "axe", "pickaxe", "shovel", "hoe", "spear", "staff", "wand")
            .anyMatch(name::contains);
    }

    private static boolean isPlantIngredient(final String name) {
        return Stream.of("seed", "root", "herb", "leaf", "flower", "garlic", "belladonna", "mandrake", "wormwood", "wolfsbane", "artichoke", "berry")
            .anyMatch(name::contains);
    }

    private static void drawEquipment(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        if (name.startsWith("werewolf_hunter_")) {
            drawWerewolfHunterEquipment(graphics, name, scale);
            return;
        }
        graphics.setColor(palette[0]);
        if (name.contains("boot") || name.contains("shoe") || name.contains("slipper")) {
            graphics.fillRect(4 * scale, 3 * scale, 5 * scale, 9 * scale);
            graphics.fillRect(7 * scale, 10 * scale, 6 * scale, 3 * scale);
        } else if (name.contains("hat") || name.contains("helm")) {
            graphics.fillRect(3 * scale, 5 * scale, 10 * scale, 7 * scale);
            graphics.fillRect(2 * scale, 11 * scale, 12 * scale, 2 * scale);
        } else {
            graphics.fillRect(4 * scale, 3 * scale, 8 * scale, 11 * scale);
            graphics.fillRect(2 * scale, 4 * scale, 3 * scale, 7 * scale);
            graphics.fillRect(11 * scale, 4 * scale, 3 * scale, 7 * scale);
        }
        graphics.setColor(palette[4]);
        graphics.drawLine(5 * scale, 5 * scale, 10 * scale, 10 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 6 * scale, 2 * scale, 2 * scale);
    }

    private static void drawWerewolfHunterEquipment(
        final Graphics2D graphics,
        final String name,
        final int scale
    ) {
        final boolean silvered = name.contains("silvered");
        final boolean dawn = name.contains("dawn") || name.contains("garlicked");
        final Color leather = dawn ? new Color(0x7A3127) : new Color(0x4A3025);
        final Color trim = silvered ? new Color(0xC5D1D9) : dawn ? new Color(0xD7AD55) : new Color(0x8E6740);
        graphics.setColor(new Color(0x1C1718));
        if (name.contains("boot")) {
            graphics.fillRect(4 * scale, 3 * scale, 5 * scale, 9 * scale);
            graphics.fillRect(7 * scale, 10 * scale, 6 * scale, 3 * scale);
            graphics.setColor(leather);
            graphics.fillRect(5 * scale, 4 * scale, 3 * scale, 7 * scale);
            graphics.fillRect(8 * scale, 11 * scale, 4 * scale, scale);
        } else if (name.contains("hat")) {
            graphics.fillRect(3 * scale, 4 * scale, 10 * scale, 8 * scale);
            graphics.fillRect(scale, 11 * scale, 14 * scale, 2 * scale);
            graphics.setColor(leather);
            graphics.fillRect(4 * scale, 5 * scale, 8 * scale, 6 * scale);
            graphics.fillRect(2 * scale, 11 * scale, 12 * scale, scale);
        } else if (name.contains("legging")) {
            graphics.fillRect(4 * scale, 2 * scale, 8 * scale, 6 * scale);
            graphics.fillRect(4 * scale, 7 * scale, 3 * scale, 8 * scale);
            graphics.fillRect(9 * scale, 7 * scale, 3 * scale, 8 * scale);
            graphics.setColor(leather);
            graphics.fillRect(5 * scale, 3 * scale, 6 * scale, 4 * scale);
            graphics.fillRect(5 * scale, 8 * scale, 2 * scale, 6 * scale);
            graphics.fillRect(9 * scale, 8 * scale, 2 * scale, 6 * scale);
        } else {
            graphics.fillRect(3 * scale, 2 * scale, 10 * scale, 13 * scale);
            graphics.fillRect(scale, 4 * scale, 3 * scale, 8 * scale);
            graphics.fillRect(12 * scale, 4 * scale, 3 * scale, 8 * scale);
            graphics.setColor(leather);
            graphics.fillRect(4 * scale, 3 * scale, 8 * scale, 11 * scale);
            graphics.fillRect(2 * scale, 5 * scale, 2 * scale, 6 * scale);
            graphics.fillRect(12 * scale, 5 * scale, 2 * scale, 6 * scale);
        }
        graphics.setColor(trim);
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, 7 * scale);
        graphics.fillRect(5 * scale, 8 * scale, 6 * scale, scale);
    }

    private static void drawTool(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[0]);
        for (int index = 3; index < 13; index++) {
            graphics.fillRect(index * scale, (15 - index) * scale, 2 * scale, 2 * scale);
        }
        graphics.setColor(palette[5]);
        if (name.contains("pickaxe") || name.contains("axe")) {
            graphics.fillRect(2 * scale, 2 * scale, 9 * scale, 3 * scale);
        } else {
            graphics.fillPolygon(new Polygon(
                new int[]{2 * scale, 5 * scale, 12 * scale, 10 * scale},
                new int[]{2 * scale, 3 * scale, 13 * scale, 14 * scale},
                4
            ));
        }
        graphics.setColor(palette[6]);
        graphics.fillRect(8 * scale, 8 * scale, scale, scale);
    }

    private static void drawHerb(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        graphics.setColor(palette[2]);
        graphics.fillRect(7 * scale, 4 * scale, 2 * scale, 10 * scale);
        graphics.setColor(palette[4]);
        graphics.fillOval(3 * scale, 3 * scale, 5 * scale, 4 * scale);
        graphics.fillOval(8 * scale, 6 * scale, 5 * scale, 4 * scale);
        graphics.fillOval(3 * scale, 9 * scale, 5 * scale, 4 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect((4 + Math.floorMod(name.hashCode(), 7)) * scale, 4 * scale, scale, scale);
    }

    private static void drawFallbackItem(
        final Graphics2D graphics,
        final String name,
        final int width,
        final int height,
        final int scale,
        final Color[] palette
    ) {
        final String id = name.toLowerCase().replaceFirst("\\.png$", "");
        if (Stream.of("dust", "powder", "ash", "salt", "lime", "gypsum").anyMatch(id::contains)) {
            drawPowderPile(graphics, scale, new Color[]{palette[0], palette[2], palette[4], palette[6]});
            return;
        }
        if (Stream.of("thread", "twine", "cotton", "wool", "web", "silk").anyMatch(id::contains)) {
            drawThreadBundle(graphics, scale, palette[3], palette[6]);
            return;
        }
        if (id.contains("skull") || id.contains("head")) {
            drawSkullItem(graphics, scale);
            return;
        }
        if (id.contains("heart")) {
            drawHeartItem(graphics, scale, palette[3], palette[6]);
            return;
        }
        if (id.contains("leather") || id.contains("hide")) {
            drawLeather(graphics, scale, palette[3], false);
            return;
        }
        if (Stream.of("stone", "rock", "quartz", "gem", "crystal", "diamond").anyMatch(id::contains)) {
            drawRunedStone(graphics, scale, palette[3], LabelMotif.GEM);
            return;
        }
        if (Stream.of(
            "blood", "fume", "vapour", "vapor", "oil", "dew", "milk", "tear", "spirit", "soul",
            "essence", "breath", "exhale", "odour", "odor", "evil", "unguent", "ointment", "hunger",
            "will", "animus", "magic", "purity", "rebirth", "luck"
        ).anyMatch(id::contains)) {
            final LabelMotif motif = id.contains("blood") || id.contains("heart") ? LabelMotif.HEART
                : id.contains("spirit") || id.contains("soul") ? LabelMotif.SPIRIT
                : id.contains("fume") || id.contains("vap") || id.contains("odour") ? LabelMotif.FUME
                : id.contains("luck") ? LabelMotif.LUCK : LabelMotif.DROP;
            drawLabeledJar(graphics, scale, palette[3], motif);
            return;
        }
        if (id.contains("wing") || id.contains("feather")) {
            drawOwletWing(graphics, scale);
            return;
        }
        if (Stream.of("bone", "fang", "tooth", "claw", "needle", "splinter").anyMatch(id::contains)) {
            drawBoneShard(graphics, scale, palette);
            return;
        }
        if (Stream.of("meat", "flesh", "liver", "tongue", "pork", "mutton").anyMatch(id::contains)) {
            drawOrganicIngredient(graphics, scale, palette);
            return;
        }
        drawRelicCoin(graphics, scale, palette, Math.floorMod(name.hashCode(), LabelMotif.values().length));
    }

    private static void drawBoneShard(final Graphics2D graphics, final int scale, final Color[] palette) {
        graphics.setColor(palette[0]);
        graphics.fillPolygon(new Polygon(new int[]{2, 4, 13, 15, 13, 3}, new int[]{12, 14, 5, 2, 1, 11}, 6));
        graphics.setColor(palette[5]);
        graphics.fillPolygon(new Polygon(new int[]{4, 5, 13, 14, 12, 3}, new int[]{12, 13, 5, 3, 2, 11}, 6));
        graphics.setColor(palette[6]);
        graphics.drawLine(5 * scale, 11 * scale, 12 * scale, 4 * scale);
    }

    private static void drawOrganicIngredient(final Graphics2D graphics, final int scale, final Color[] palette) {
        graphics.setColor(palette[0]);
        graphics.fillPolygon(new Polygon(new int[]{3, 6, 11, 14, 13, 9, 5, 2}, new int[]{4, 2, 3, 7, 12, 14, 13, 9}, 8));
        graphics.setColor(palette[3]);
        graphics.fillPolygon(new Polygon(new int[]{4, 7, 10, 12, 12, 9, 5, 3}, new int[]{5, 3, 4, 7, 11, 13, 12, 9}, 8));
        graphics.setColor(palette[5]);
        graphics.fillRect(6 * scale, 5 * scale, 4 * scale, 2 * scale);
        graphics.setColor(palette[6]);
        graphics.fillRect(7 * scale, 5 * scale, 2 * scale, scale);
    }

    private static void drawRelicCoin(
        final Graphics2D graphics,
        final int scale,
        final Color[] palette,
        final int motifIndex
    ) {
        graphics.setColor(palette[0]);
        graphics.fillOval(2 * scale, 2 * scale, 12 * scale, 12 * scale);
        graphics.setColor(palette[3]);
        graphics.fillOval(3 * scale, 3 * scale, 10 * scale, 10 * scale);
        graphics.setColor(palette[1]);
        graphics.drawOval(4 * scale, 4 * scale, 8 * scale, 8 * scale);
        graphics.setColor(palette[6]);
        drawLabelMotif(graphics, scale, LabelMotif.values()[motifIndex], 7, 6);
        graphics.setColor(palette[5]);
        graphics.fillRect(5 * scale, 4 * scale, 2 * scale, scale);
    }

    private static void drawConceptMaterial(
        final Graphics2D graphics,
        final String name,
        final int unit,
        final Color[] palette
    ) {
        final RandomGenerator random = random(name + ":material");
        graphics.setColor(palette[2]);
        graphics.fillRect(32 * unit, 32 * unit, 32 * unit, 32 * unit);
        for (int index = 0; index < 34; index++) {
            final int x = 32 + random.nextInt(32);
            final int y = 32 + random.nextInt(32);
            final int width = 1 + random.nextInt(4);
            final int height = 1 + random.nextInt(3);
            graphics.setColor(index % 5 == 0 ? palette[6] : index % 2 == 0 ? palette[3] : palette[1]);
            graphics.fillRect(x * unit, y * unit, width * unit, height * unit);
        }
        graphics.setColor(palette[0]);
        for (int offset = 34 + Math.floorMod(name.hashCode(), 5); offset < 64; offset += 7) {
            graphics.fillRect(offset * unit, 32 * unit, unit, 32 * unit);
        }
    }

    private static void drawCreatureMaterial(
        final Graphics2D graphics,
        final RandomGenerator random,
        final int unit,
        final Color[] palette
    ) {
        for (int index = 0; index < 120; index++) {
            final int x = random.nextInt(64);
            final int y = random.nextInt(64);
            final int width = 1 + random.nextInt(3);
            final int height = 1 + random.nextInt(2);
            graphics.setColor(index % 11 == 0 ? palette[4] : index % 3 == 0 ? palette[2] : palette[0]);
            graphics.fillRect(x * unit, y * unit, width * unit, height * unit);
        }
        graphics.setColor(palette[3]);
        graphics.fillRect(0, 0, 64 * unit, unit);
        graphics.fillRect(0, 0, unit, 64 * unit);
        graphics.setColor(palette[0]);
        graphics.fillRect(0, 63 * unit, 64 * unit, unit);
        graphics.fillRect(63 * unit, 0, unit, 64 * unit);
    }

    private static void drawCreatureMotif(
        final Graphics2D graphics,
        final String name,
        final int unit,
        final Color[] palette
    ) {
        graphics.setColor(palette[6]);
        switch (name) {
            case "circle_mage" -> {
                drawRune(graphics, unit, 42, 45);
                graphics.fillRect(18 * unit, 20 * unit, 3 * unit, 20 * unit);
            }
            case "hedge_crone" -> {
                graphics.fillRect(8 * unit, 8 * unit, 8 * unit, 2 * unit);
                graphics.fillRect(18 * unit, 22 * unit, 2 * unit, 16 * unit);
            }
            case "vampire" -> {
                graphics.fillRect(20 * unit, 20 * unit, 8 * unit, 3 * unit);
                graphics.fillPolygon(new Polygon(
                    new int[]{22 * unit, 26 * unit, 24 * unit},
                    new int[]{23 * unit, 23 * unit, 29 * unit},
                    3
                ));
            }
            case "blood_thrall" -> drawChains(graphics, unit, 20, 21);
            case "corpse" -> {
                graphics.fillRect(8 * unit, 13 * unit, 8 * unit, 2 * unit);
                graphics.fillRect(20 * unit, 24 * unit, 8 * unit, 3 * unit);
            }
            case "glass_doppelganger" -> drawPrism(graphics, unit, 8, 8);
            case "werewolf_hunter" -> {
                graphics.fillRect(8 * unit, 6 * unit, 9 * unit, 2 * unit);
                graphics.fillRect(20 * unit, 20 * unit, 8 * unit, 2 * unit);
                drawCross(graphics, unit, 43, 43);
            }
            case "lycan_villager" -> drawRune(graphics, unit, 42, 46);
            case "banshee" -> drawVerticalWisps(graphics, unit, 34, 34);
            case "umbral_sigil" -> drawRune(graphics, unit, 42, 42);
            case "eldritch_watcher" -> drawEyes(graphics, unit, 34, 35, 4, 3);
            case "spectral_familiar" -> {
                graphics.fillRect(20 * unit, 20 * unit, 8 * unit, 2 * unit);
                drawRune(graphics, unit, 43, 44);
            }
            case "poltergeist" -> drawDebris(graphics, unit);
            case "spectre" -> {
                graphics.fillRect(8 * unit, 8 * unit, 8 * unit, 2 * unit);
                drawVerticalWisps(graphics, unit, 34, 34);
            }
            case "spirit" -> drawRune(graphics, unit, 43, 43);
            case "lost_soul" -> drawFlame(graphics, unit, 40, 39);
            case "echo_shade" -> {
                drawVerticalWisps(graphics, unit, 34, 34);
                graphics.drawRect(40 * unit, 40 * unit, 12 * unit, 14 * unit);
                graphics.drawRect(44 * unit, 43 * unit, 12 * unit, 14 * unit);
            }
            case "werewolf" -> drawClawMarks(graphics, unit, 34, 36);
            case "feral_lycan" -> {
                drawClawMarks(graphics, unit, 36, 39);
                drawChains(graphics, unit, 44, 48);
            }
            case "hellhound" -> drawFlame(graphics, unit, 39, 38);
            case "pale_steed" -> drawRune(graphics, unit, 43, 43);
            case "nightmare" -> {
                drawFlame(graphics, unit, 38, 36);
                drawFlame(graphics, unit, 50, 46);
            }
            case "familiar_cat" -> drawRune(graphics, unit, 43, 44);
            case "owl" -> drawEyes(graphics, unit, 9, 10, 2, 1);
            case "toad" -> graphics.fillOval(36 * unit, 42 * unit, 18 * unit, 10 * unit);
            case "hex_bat" -> drawRune(graphics, unit, 43, 44);
            case "parasytic_louse" -> {
                graphics.fillOval(38 * unit, 40 * unit, 14 * unit, 10 * unit);
                graphics.fillRect(43 * unit, 46 * unit, 4 * unit, 8 * unit);
            }
            case "demon" -> drawArmorSeams(graphics, unit);
            case "emberhorn_archfiend" -> {
                drawArmorSeams(graphics, unit);
                drawFlame(graphics, unit, 42, 40);
            }
            case "naamah" -> {
                drawArmorSeams(graphics, unit);
                drawEyes(graphics, unit, 36, 36, 3, 2);
            }
            case "abyssal_regent" -> {
                drawEyes(graphics, unit, 35, 36, 3, 2);
                drawVerticalWisps(graphics, unit, 42, 44);
            }
            case "death" -> {
                graphics.fillRect(8 * unit, 8 * unit, 8 * unit, 8 * unit);
                drawCross(graphics, unit, 42, 44);
            }
            case "ironbound_sentinel" -> {
                drawArmorSeams(graphics, unit);
                drawRune(graphics, unit, 43, 43);
            }
            case "ent" -> drawBranches(graphics, unit, 34, 35);
            case "mandrake" -> {
                drawBranches(graphics, unit, 36, 35);
                graphics.fillRect(10 * unit, 13 * unit, 4 * unit, 3 * unit);
            }
            case "dreamroot" -> {
                drawBranches(graphics, unit, 35, 36);
                drawRune(graphics, unit, 43, 44);
            }
            case "bramble_colossus" -> {
                drawBranches(graphics, unit, 34, 35);
                drawClawMarks(graphics, unit, 46, 38);
            }
            case "thorned_pursuer" -> {
                drawBranches(graphics, unit, 35, 36);
                graphics.drawLine(35 * unit, 56 * unit, 54 * unit, 37 * unit);
            }
            case "hobgoblin" -> drawCross(graphics, unit, 43, 43);
            case "goblin" -> drawDebris(graphics, unit);
            case "stonebroker" -> drawPrism(graphics, unit, 39, 39);
            case "forgewarden" -> {
                drawArmorSeams(graphics, unit);
                drawFlame(graphics, unit, 43, 42);
            }
            case "illusion_creeper" -> drawPrism(graphics, unit, 39, 38);
            case "illusion_spider" -> {
                drawPrism(graphics, unit, 38, 39);
                drawEyes(graphics, unit, 8, 10, 4, 1);
            }
            case "illusion_zombie" -> {
                drawPrism(graphics, unit, 40, 39);
                graphics.fillRect(12 * unit, 8 * unit, 4 * unit, 8 * unit);
            }
            case "imp" -> drawFlame(graphics, unit, 42, 42);
            case "storm_simian" -> {
                drawRune(graphics, unit, 43, 43);
                graphics.fillRect(34 * unit, 50 * unit, 22 * unit, 2 * unit);
            }
        }
    }

    private static void drawRune(final Graphics2D graphics, final int unit, final int x, final int y) {
        graphics.drawRect(x * unit, y * unit, 10 * unit, 10 * unit);
        graphics.drawLine(x * unit, (y + 5) * unit, (x + 5) * unit, y * unit);
        graphics.drawLine((x + 5) * unit, y * unit, (x + 10) * unit, (y + 5) * unit);
        graphics.drawLine((x + 10) * unit, (y + 5) * unit, (x + 5) * unit, (y + 10) * unit);
        graphics.drawLine((x + 5) * unit, (y + 10) * unit, x * unit, (y + 5) * unit);
    }

    private static void drawCross(final Graphics2D graphics, final int unit, final int x, final int y) {
        graphics.fillRect((x + 4) * unit, y * unit, 2 * unit, 12 * unit);
        graphics.fillRect(x * unit, (y + 4) * unit, 10 * unit, 2 * unit);
    }

    private static void drawChains(final Graphics2D graphics, final int unit, final int x, final int y) {
        for (int index = 0; index < 6; index++) {
            graphics.drawRect((x + index * 2) * unit, (y + index * 3) * unit, 3 * unit, 4 * unit);
        }
    }

    private static void drawPrism(final Graphics2D graphics, final int unit, final int x, final int y) {
        for (int offset = 0; offset < 18; offset += 4) {
            graphics.drawLine((x + offset) * unit, y * unit, x * unit, (y + offset) * unit);
            graphics.drawLine((x + 18) * unit, (y + offset) * unit, (x + offset) * unit, (y + 18) * unit);
        }
    }

    private static void drawEyes(
        final Graphics2D graphics,
        final int unit,
        final int x,
        final int y,
        final int columns,
        final int rows
    ) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                graphics.fillRect((x + column * 5) * unit, (y + row * 5) * unit, 3 * unit, 2 * unit);
            }
        }
    }

    private static void drawVerticalWisps(
        final Graphics2D graphics,
        final int unit,
        final int x,
        final int y
    ) {
        for (int index = 0; index < 6; index++) {
            graphics.fillRect((x + index * 5) * unit, (y + index % 2 * 4) * unit, 2 * unit, (18 - index) * unit);
        }
    }

    private static void drawDebris(final Graphics2D graphics, final int unit) {
        graphics.fillRect(36 * unit, 37 * unit, 5 * unit, 4 * unit);
        graphics.fillRect(51 * unit, 39 * unit, 6 * unit, 6 * unit);
        graphics.fillRect(42 * unit, 49 * unit, 4 * unit, 7 * unit);
        graphics.fillRect(55 * unit, 55 * unit, 3 * unit, 3 * unit);
    }

    private static void drawFlame(final Graphics2D graphics, final int unit, final int x, final int y) {
        graphics.fillRect((x + 5) * unit, y * unit, 4 * unit, 4 * unit);
        graphics.fillRect((x + 2) * unit, (y + 4) * unit, 10 * unit, 5 * unit);
        graphics.fillRect(x * unit, (y + 9) * unit, 14 * unit, 8 * unit);
    }

    private static void drawClawMarks(
        final Graphics2D graphics,
        final int unit,
        final int x,
        final int y
    ) {
        for (int index = 0; index < 3; index++) {
            graphics.drawLine((x + index * 5) * unit, y * unit, (x + 6 + index * 5) * unit, (y + 16) * unit);
        }
    }

    private static void drawArmorSeams(final Graphics2D graphics, final int unit) {
        graphics.drawRect(35 * unit, 35 * unit, 24 * unit, 24 * unit);
        graphics.drawLine(35 * unit, 47 * unit, 59 * unit, 47 * unit);
        graphics.drawLine(47 * unit, 35 * unit, 47 * unit, 59 * unit);
    }

    private static void drawBranches(
        final Graphics2D graphics,
        final int unit,
        final int x,
        final int y
    ) {
        graphics.fillRect((x + 10) * unit, y * unit, 3 * unit, 24 * unit);
        graphics.drawLine((x + 11) * unit, (y + 8) * unit, x * unit, (y + 1) * unit);
        graphics.drawLine((x + 12) * unit, (y + 12) * unit, (x + 24) * unit, (y + 3) * unit);
        graphics.drawLine((x + 11) * unit, (y + 17) * unit, (x + 3) * unit, (y + 24) * unit);
    }

    private static void drawVillagerClothes(
        final Graphics2D graphics,
        final int unit,
        final Color[] palette,
        final boolean lycan,
        final boolean miner
    ) {
        graphics.setColor(lycan ? new Color(120, 105, 91) : new Color(116, 88, 54));
        graphics.fillRect(8 * unit, 8 * unit, 8 * unit, 8 * unit);
        graphics.setColor(palette[0]);
        graphics.fillRect(10 * unit, 11 * unit, 2 * unit, unit);
        graphics.fillRect(14 * unit, 11 * unit, 2 * unit, unit);
        graphics.setColor(miner ? new Color(61, 75, 83) : new Color(74, 52, 67));
        graphics.fillRect(16 * unit, 20 * unit, 24 * unit, 20 * unit);
        graphics.setColor(miner ? new Color(173, 116, 53) : new Color(139, 49, 56));
        graphics.fillRect(16 * unit, 28 * unit, 24 * unit, 4 * unit);
        graphics.fillRect(20 * unit, 20 * unit, 4 * unit, 20 * unit);
        graphics.setColor(palette[6]);
        graphics.fillRect(27 * unit, 29 * unit, 3 * unit, 3 * unit);
        if (miner) {
            graphics.setColor(new Color(64, 51, 42));
            graphics.fillRect(8 * unit, 6 * unit, 8 * unit, 3 * unit);
            graphics.fillRect(6 * unit, 8 * unit, 12 * unit, 2 * unit);
            graphics.setColor(new Color(235, 192, 82));
            graphics.fillRect(11 * unit, 6 * unit, 2 * unit, 2 * unit);
        } else {
            graphics.setColor(new Color(54, 47, 50));
            graphics.fillRect(8 * unit, 6 * unit, 8 * unit, 2 * unit);
        }
    }

    private static void drawImpSkin(final Graphics2D graphics, final int unit, final Color[] palette) {
        drawCreatureSkin(graphics, unit, palette);
        graphics.setColor(new Color(166, 54, 48));
        graphics.fillRect(4 * unit, 20 * unit, 8 * unit, 12 * unit);
        graphics.fillRect(44 * unit, 20 * unit, 8 * unit, 12 * unit);
        graphics.setColor(new Color(224, 103, 55));
        graphics.fillRect(6 * unit, 26 * unit, 4 * unit, 5 * unit);
        graphics.fillRect(46 * unit, 26 * unit, 4 * unit, 5 * unit);
    }

    private static void drawSimianSkin(final Graphics2D graphics, final int unit, final Color[] palette) {
        graphics.setColor(new Color(43, 62, 82));
        graphics.fillRect(0, 0, 64 * unit, 64 * unit);
        graphics.setColor(new Color(153, 145, 128));
        graphics.fillRect(8 * unit, 8 * unit, 8 * unit, 8 * unit);
        graphics.setColor(new Color(103, 189, 207));
        graphics.fillRect(20 * unit, 20 * unit, 8 * unit, 12 * unit);
        graphics.fillRect(4 * unit, 20 * unit, 4 * unit, 12 * unit);
        graphics.fillRect(44 * unit, 20 * unit, 4 * unit, 12 * unit);
        graphics.setColor(new Color(230, 226, 207));
        graphics.fillRect(32 * unit, 16 * unit, 12 * unit, 8 * unit);
        graphics.setColor(new Color(31, 42, 57));
        graphics.fillRect(10 * unit, 11 * unit, 2 * unit, unit);
        graphics.fillRect(14 * unit, 11 * unit, 2 * unit, unit);
    }

    private static void drawCreatureSkin(final Graphics2D graphics, final int unit, final Color[] palette) {
        graphics.setColor(palette[4]);
        graphics.fillRect(8 * unit, 8 * unit, 8 * unit, 8 * unit);
        graphics.setColor(palette[0]);
        graphics.fillRect(9 * unit, 10 * unit, 3 * unit, 3 * unit);
        graphics.fillRect(14 * unit, 10 * unit, 3 * unit, 3 * unit);
        graphics.setColor(palette[6]);
        graphics.fillRect(10 * unit, 11 * unit, unit, unit);
        graphics.fillRect(15 * unit, 11 * unit, unit, unit);
        graphics.setColor(palette[0]);
        graphics.fillRect(11 * unit, 14 * unit, 4 * unit, unit);
        graphics.setColor(palette[5]);
        graphics.fillRect(20 * unit, 20 * unit, 8 * unit, 12 * unit);
        graphics.fillRect(44 * unit, 20 * unit, 4 * unit, 12 * unit);
        graphics.fillRect(4 * unit, 20 * unit, 4 * unit, 12 * unit);
        graphics.setColor(palette[3]);
        graphics.fillRect(20 * unit, 20 * unit, 8 * unit, unit);
        graphics.fillRect(4 * unit, 20 * unit, 4 * unit, unit);
        graphics.fillRect(44 * unit, 20 * unit, 4 * unit, unit);
        graphics.setColor(palette[6]);
        graphics.fillRect(23 * unit, 23 * unit, 2 * unit, 6 * unit);
    }

    private static BufferedImage guiTexture(final String name, final int width, final int height) {
        final BufferedImage image = image(width, height);
        final Color[] palette = palette(name);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(palette[0]);
        graphics.fillRect(0, 0, width, height);
        final int step = Math.max(4, Math.min(width, height) / 16);
        graphics.setColor(palette[1]);
        for (int y = 0; y < height; y += step) graphics.drawLine(0, y, width - 1, y);
        graphics.setColor(palette[4]);
        graphics.drawRect(1, 1, Math.max(0, width - 3), Math.max(0, height - 3));
        graphics.setColor(palette[5]);
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

    private static Color[] entityPalette(final String name) {
        final Color[] concept = switch (name) {
            case "circle_mage" -> colors("120f20", "241939", "49306d", "7450a1", "d6c29a", "3a2855", "9f72e5");
            case "hedge_crone" -> colors("101510", "25291c", "42462a", "697047", "c9bea0", "343329", "91a65b");
            case "vampire" -> colors("0d0b10", "1b1119", "35151f", "5e1f2c", "d9c6bc", "17141a", "b53c4c");
            case "blood_thrall" -> colors("190b0d", "351113", "621c20", "8e2e30", "c28c7e", "6a2022", "dc4949");
            case "corpse" -> colors("111312", "232724", "3e443e", "61665d", "aaa99b", "393a36", "818f78");
            case "glass_doppelganger" -> colors("0d1821", "183042", "2c5267", "5a8ca2", "c9e4e9", "7cb2c2", "ecfbff");
            case "werewolf_hunter" -> colors("11100f", "26201c", "45372b", "76563b", "b98c63", "342b25", "cbd4d8");
            case "lycan_villager" -> colors("11161a", "28323a", "465460", "71808b", "9e765c", "4a4038", "70b5e8");
            case "banshee" -> colors("0c131a", "172633", "294459", "5f849a", "d9eef1", "a8cbd3", "ecfbff");
            case "umbral_sigil", "echo_shade" -> colors("090813", "171129", "2d1d4c", "50317c", "231a38", "160f25", "a253e5");
            case "eldritch_watcher" -> colors("071717", "0e2c2b", "14504b", "237c72", "1b4948", "0b3635", "59f0d7");
            case "spectral_familiar", "spirit" -> colors("102326", "1c4144", "337075", "61aaa8", "d7e7df", "85c8bd", "dffcf0");
            case "poltergeist" -> colors("101a1e", "213037", "3e565e", "6c8389", "896c52", "4b7180", "75e0ec");
            case "spectre" -> colors("0b0b12", "171623", "29263a", "49445d", "c8c9c3", "2a2935", "76c7d1");
            case "lost_soul" -> colors("17100b", "2f2014", "594022", "916733", "6f5534", "47321d", "f1b54d");
            case "werewolf" -> colors("0e1114", "22282d", "3c464d", "64717a", "8e9699", "39434a", "bddae5");
            case "feral_lycan" -> colors("170e0d", "321b18", "5c2e28", "83463d", "8f6255", "4b2c29", "d37e5c");
            case "hellhound" -> colors("100c0b", "211715", "3b2925", "5c3a2f", "292523", "191515", "f0642f");
            case "pale_steed" -> colors("101820", "21313d", "465765", "788994", "d8d8cc", "9ea9aa", "78cbe9");
            case "nightmare" -> colors("0c0b0b", "171515", "292524", "443936", "1c1a1a", "292221", "f06a2a");
            case "familiar_cat" -> colors("0c1020", "141d36", "21305a", "354b7a", "23335d", "172342", "e4c45b");
            case "owl" -> colors("17130f", "30261b", "57432c", "8b6945", "d6c29b", "74543a", "e6b950");
            case "toad" -> colors("12160c", "283014", "495524", "71813a", "6d783a", "46521f", "d4b84d");
            case "hex_bat" -> colors("0c0a14", "181129", "2b1d46", "4f3473", "201733", "160e25", "a84fe3");
            case "parasytic_louse" -> colors("171311", "312723", "57423a", "80665c", "d7c5b6", "9e8a7b", "a7423b");
            case "demon" -> colors("0d0d0d", "181718", "2b292b", "444047", "211e20", "171517", "c88b32");
            case "emberhorn_archfiend" -> colors("110a08", "26100d", "491913", "71251a", "241b18", "17110f", "ff5e22");
            case "naamah" -> colors("16080d", "35101b", "631629", "8d203c", "511523", "2b0e16", "e64865");
            case "abyssal_regent" -> colors("071518", "0e2b30", "15515a", "237883", "17464f", "0d3339", "5adbd1");
            case "death" -> colors("08090b", "121419", "23262c", "3a3e45", "d3cbbb", "181a1e", "a7d5d9");
            case "ironbound_sentinel" -> colors("0e1013", "22262b", "3e464d", "626e75", "343a40", "272c31", "9b58e3");
            case "ent" -> colors("10150e", "202c1a", "384a2a", "586c3d", "70533a", "3d3324", "4fc9b2");
            case "mandrake" -> colors("15150c", "2c3014", "505923", "7b8738", "a17a46", "60462b", "a7d94f");
            case "dreamroot" -> colors("0d1020", "1d2140", "333b70", "575f9d", "3f3b72", "29284e", "d2cc69");
            case "bramble_colossus" -> colors("10120c", "252819", "3f4527", "606637", "51402d", "332a20", "83a844");
            case "thorned_pursuer" -> colors("0d120d", "1d2a1d", "334832", "4e6847", "3d352a", "29251f", "75a958");
            case "hobgoblin" -> colors("17110d", "302015", "5a3820", "8a542c", "a16c3e", "654124", "f4b942");
            case "goblin" -> colors("0e1f12", "16341d", "28592d", "3f7b38", "70a84f", "304d2a", "f4b942");
            case "stonebroker" -> colors("101114", "24272c", "42474e", "666d73", "5e6060", "393b40", "a47bd8");
            case "forgewarden" -> colors("0d0e10", "1d2024", "34383e", "52585e", "272a2e", "1a1c1f", "f3a33d");
            case "illusion_creeper", "illusion_spider", "illusion_zombie" ->
                colors("090d17", "111c30", "203456", "365a83", "233d62", "172844", "9b55e8");
            case "imp" -> colors("16090d", "32101a", "60192a", "91263b", "a13a3c", "6b202a", "ed7b3c");
            case "storm_simian" -> colors("0c1720", "172d3d", "264b61", "3d7188", "827a6e", "304f61", "70d2df");
            default -> palette(name);
        };
        return personalize(concept, name.hashCode());
    }

    private static Color[] palette(final String name) {
        final String lower = name.toLowerCase();
        if (lower.endsWith("_spawn_egg.png")) {
            return entityPalette(lower.replaceFirst("_spawn_egg\\.png$", ""));
        }
        final Color[] base;
        if (lower.contains("altar") && !lower.contains("wolf")) {
            base = ARCANE;
        } else if (lower.contains("ice") || lower.contains("snow") || lower.contains("frost")
            || lower.contains("storm_simian")) {
            base = FROST;
        } else if (lower.contains("alder") || lower.contains("rowan") || lower.contains("hawthorn")
            || lower.contains("door") || lower.contains("plank") || lower.contains("coffin")
            || lower.contains("stockade")) {
            base = WOOD;
        } else if (lower.contains("silver") || lower.contains("iron") || lower.contains("hunter")
            || lower.contains("wolf") || lower.contains("lycan")) {
            base = SILVER;
        } else if (lower.contains("blood") || lower.contains("vamp") || lower.contains("demon")
            || lower.contains("infernal") || lower.contains("imp")) {
            base = INFERNAL;
        } else if (lower.contains("wood") || lower.contains("plant") || lower.contains("leaf")
            || lower.contains("grass") || lower.contains("vine") || lower.contains("bramble")
            || lower.contains("ent") || lower.contains("delve") || lower.contains("goblin")
            || lower.contains("stonebroker") || lower.contains("forgewarden")) {
            base = VERDANT;
        } else if (lower.contains("statue") || lower.contains("stone") || lower.contains("ore")) {
            base = SILVER;
        } else if (lower.contains("hitchcock") || lower.contains("flock") || lower.contains("crow")) {
            base = ARCANE;
        } else {
            base = PALETTES.get(Math.floorMod(name.hashCode(), PALETTES.size()));
        }
        return lower.contains("delvealloy") ? base : personalize(base, name.hashCode());
    }

    private static Color[] personalize(final Color[] base, final int hash) {
        final int redShift = Math.floorMod(hash, 19) - 9;
        final int greenShift = Math.floorMod(hash >>> 7, 17) - 8;
        final int blueShift = Math.floorMod(hash >>> 13, 19) - 9;
        return Arrays.stream(base)
            .map(color -> new Color(
                Math.clamp(color.getRed() + redShift, 0, 255),
                Math.clamp(color.getGreen() + greenShift, 0, 255),
                Math.clamp(color.getBlue() + blueShift, 0, 255),
                color.getAlpha()
            ))
            .toArray(Color[]::new);
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
