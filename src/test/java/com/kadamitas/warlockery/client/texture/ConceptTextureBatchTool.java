package com.kadamitas.warlockery.client.texture;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/** CLI wrapper for deterministic, concept-projected creature atlas generation. */
public final class ConceptTextureBatchTool {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path DEFAULT_CATALOG = Path.of(
        "tools/creature_models/concept_texture_sources.json"
    );
    private static final Path RESOURCE_ROOT = Path.of(
        "src/main/resources/assets/warlockery/textures/entity"
    );
    private static final Path DEFAULT_OUTPUT_ROOT = Path.of(
        "build/reports/visual-audit/concept-textures"
    );

    private ConceptTextureBatchTool() {
    }

    public static void main(final String[] arguments) throws Exception {
        final Options options = Options.parse(arguments);
        final Catalog catalog = readCatalog(options.catalog());
        final List<SourceSpec> selected = catalog.sources().stream()
            .filter(source -> options.atlasId() == null || source.atlasId().equals(options.atlasId()))
            .sorted(Comparator.comparing(SourceSpec::atlasId))
            .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("no catalog entry matched " + options.atlasId());
        }
        final Path outputRoot = options.install() ? RESOURCE_ROOT : options.outputRoot();
        Files.createDirectories(resolveLocal(outputRoot));
        final List<String> report = new ArrayList<>();
        for (final SourceSpec source : selected) {
            report.add(bake(source, outputRoot, options.checkOnly()));
        }
        System.out.println(String.join(System.lineSeparator(), report));
    }

    private static Catalog readCatalog(final Path catalogPath) throws IOException {
        final Path resolved = resolveLocal(catalogPath);
        try (Reader reader = Files.newBufferedReader(resolved)) {
            final Catalog catalog = new Gson().fromJson(reader, Catalog.class);
            if (catalog == null || catalog.schemaVersion() != 1 || catalog.sources() == null) {
                throw new JsonParseException("concept texture catalog must use schemaVersion 1");
            }
            return catalog;
        }
    }

    private static String bake(
        final SourceSpec source,
        final Path outputRoot,
        final boolean checkOnly
    ) throws Exception {
        final GeneratedAtlas result = generate(source);
        final BufferedImage generated = result.image();
        final Path output = resolveLocal(outputRoot.resolve(source.atlasFile()));
        if (checkOnly) {
            final BufferedImage expected = ImageIO.read(output.toFile());
            if (expected == null || !samePixels(expected, generated)) {
                throw new IllegalStateException("generated atlas differs from " + output);
            }
        } else {
            Files.createDirectories(output.getParent());
            ImageIO.write(generated, "PNG", output.toFile());
            writeComparisonSheet(source.atlasId(), result.root(), result.views(), generated);
        }
        return source.atlasId() + " -> " + output + " "
            + generated.getWidth() + "x" + generated.getHeight()
            + " sha256=" + sha256(output);
    }

    static BufferedImage bakeForTest(final String atlasId) throws Exception {
        final SourceSpec source = readCatalog(DEFAULT_CATALOG).sources().stream()
            .filter(candidate -> candidate.atlasId().equals(atlasId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown concept atlas " + atlasId));
        return generate(source).image();
    }

    private static GeneratedAtlas generate(final SourceSpec source) throws Exception {
        validate(source);
        final Path boardPath = resolveLocal(Path.of(source.board()));
        final BufferedImage board = ImageIO.read(boardPath.toFile());
        if (board == null) {
            throw new IOException("unreadable concept board " + boardPath);
        }
        if (board.getWidth() != source.boardWidth() || board.getHeight() != source.boardHeight()) {
            throw new IllegalStateException("board dimensions changed for " + source.atlasId());
        }
        final String actualHash = sha256(boardPath);
        if (!actualHash.equals(source.boardSha256().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("board hash changed for " + source.atlasId()
                + ": expected " + source.boardSha256() + " actual " + actualHash);
        }
        final Path installed = resolveLocal(RESOURCE_ROOT.resolve(source.atlasFile()));
        final BufferedImage current = ImageIO.read(installed.toFile());
        if (current == null) {
            throw new IOException("missing current atlas dimension authority " + installed);
        }
        final ModelPart root = bakeRoot(source.modelClass());
        final Predicate<String> includedPath = includedPath(source.atlasId());
        configureVariantPreview(root, source.atlasId());
        final ConceptTextureBaker.ConceptViews views = new ConceptTextureBaker.ConceptViews(
            crop(board, source.views().front()),
            crop(board, source.views().left()),
            crop(board, source.views().back()),
            crop(board, source.views().right())
        );
        final BufferedImage generated = ConceptTextureBaker.bake(
            root,
            current.getWidth(),
            current.getHeight(),
            views,
            includedPath
        );
        return new GeneratedAtlas(root, views, generated);
    }

    private static Predicate<String> includedPath(final String atlasId) {
        return switch (atlasId) {
            case "vampire_masculine" -> path -> !path.contains("/feminine_variant/");
            case "vampire_feminine" -> path -> !path.contains("/masculine_variant/");
            default -> path -> true;
        };
    }

    private static void configureVariantPreview(final ModelPart root, final String atlasId) {
        if (atlasId.equals("vampire_masculine")) {
            collapse(root.getChild("feminine_variant"));
        } else if (atlasId.equals("vampire_feminine")) {
            collapse(root.getChild("masculine_variant"));
        }
    }

    private static void collapse(final ModelPart part) {
        part.visible = false;
        part.xScale = 0.0F;
        part.yScale = 0.0F;
        part.zScale = 0.0F;
    }

    private static void writeComparisonSheet(
        final String atlasId,
        final ModelPart root,
        final ConceptTextureBaker.ConceptViews views,
        final BufferedImage atlas
    ) throws IOException {
        final int columnWidth = 400;
        final int conceptHeight = 330;
        final int renderHeight = 390;
        final int width = columnWidth * 4;
        final int height = conceptHeight + renderHeight;
        final BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(33, 32, 31));
            graphics.fillRect(0, 0, width, height);
            final BufferedImage[] concepts = {views.front(), views.left(), views.back(), views.right()};
            final TexturedModelPreview.View[] renderedViews = {
                TexturedModelPreview.View.FRONT,
                TexturedModelPreview.View.LEFT,
                TexturedModelPreview.View.BACK,
                TexturedModelPreview.View.RIGHT
            };
            final String[] labels = {"FRONT", "LEFT", "BACK", "RIGHT"};
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            for (int column = 0; column < 4; column++) {
                final int left = column * columnWidth;
                graphics.setColor(new Color(49, 47, 45));
                graphics.fillRect(left + 8, 8, columnWidth - 16, conceptHeight - 16);
                drawFit(graphics, concepts[column], left + 18, 34, columnWidth - 36, conceptHeight - 52);
                graphics.setColor(Color.WHITE);
                graphics.drawString("CONCEPT " + labels[column], left + 20, 28);
                final BufferedImage preview = TexturedModelPreview.render(root, atlas, renderedViews[column], 360);
                graphics.drawImage(preview, left + 20, conceptHeight + 10, null);
                graphics.drawString("MODEL " + labels[column], left + 20, conceptHeight + 32);
            }
        } finally {
            graphics.dispose();
        }
        final Path comparison = resolveLocal(DEFAULT_OUTPUT_ROOT.resolve(
            "comparisons/" + atlasId + "-concept-vs-model.png"
        ));
        Files.createDirectories(comparison.getParent());
        ImageIO.write(sheet, "PNG", comparison.toFile());
    }

    private static void drawFit(
        final Graphics2D graphics,
        final BufferedImage image,
        final int x,
        final int y,
        final int width,
        final int height
    ) {
        final double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        final int drawnWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        final int drawnHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        graphics.drawImage(
            image,
            x + (width - drawnWidth) / 2,
            y + (height - drawnHeight) / 2,
            drawnWidth,
            drawnHeight,
            null
        );
    }

    private static ModelPart bakeRoot(final String simpleClassName) throws ReflectiveOperationException {
        final Class<?> modelClass = Class.forName(
            "com.kadamitas.warlockery.client.model." + simpleClassName
        );
        final LayerDefinition layer = (LayerDefinition) modelClass.getMethod("createBodyLayer").invoke(null);
        return layer.bakeRoot();
    }

    private static BufferedImage crop(final BufferedImage source, final Crop crop) {
        if (crop == null || crop.x0() < 0 || crop.y0() < 0
            || crop.x1() <= crop.x0() || crop.y1() <= crop.y0()
            || crop.x1() > source.getWidth() || crop.y1() > source.getHeight()) {
            throw new IllegalArgumentException("invalid concept crop " + crop);
        }
        final int width = crop.x1() - crop.x0();
        final int height = crop.y1() - crop.y0();
        final BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                copy.setRGB(x, y, source.getRGB(crop.x0() + x, crop.y0() + y));
            }
        }
        return copy;
    }

    private static void validate(final SourceSpec source) {
        Objects.requireNonNull(source.atlasId(), "atlasId");
        Objects.requireNonNull(source.atlasFile(), "atlasFile");
        Objects.requireNonNull(source.modelClass(), "modelClass");
        Objects.requireNonNull(source.board(), "board");
        Objects.requireNonNull(source.boardSha256(), "boardSha256");
        Objects.requireNonNull(source.views(), "views");
        if (!source.atlasFile().equals(source.atlasId() + ".png")) {
            throw new IllegalArgumentException("atlas id/file mismatch for " + source.atlasId());
        }
        if (!source.board().replace('\\', '/').startsWith(
            "docs/art-source/creature-concepts/"
        )) {
            throw new IllegalArgumentException("only first-party creature concept boards are allowed");
        }
        if (!source.boardSha256().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("invalid board SHA-256 for " + source.atlasId());
        }
    }

    private static Path resolveLocal(final Path path) {
        final Path resolved = (path.isAbsolute() ? path : PROJECT_ROOT.resolve(path)).normalize();
        if (!resolved.startsWith(PROJECT_ROOT)) {
            throw new IllegalArgumentException("path escapes the project: " + path);
        }
        return resolved;
    }

    private static String sha256(final Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean samePixels(final BufferedImage first, final BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return false;
        }
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private record Catalog(int schemaVersion, String coordinateSystem, List<SourceSpec> sources) {
    }

    private record SourceSpec(
        String atlasId,
        String atlasFile,
        String modelClass,
        String board,
        int boardWidth,
        int boardHeight,
        String boardSha256,
        int rowIndex,
        int rowCount,
        Views views
    ) {
    }

    private record Views(Crop front, Crop left, Crop back, Crop right) {
    }

    private record Crop(int x0, int y0, int x1, int y1) {
    }

    private record GeneratedAtlas(
        ModelPart root,
        ConceptTextureBaker.ConceptViews views,
        BufferedImage image
    ) {
    }

    private record Options(
        Path catalog,
        Path outputRoot,
        String atlasId,
        boolean install,
        boolean checkOnly
    ) {
        static Options parse(final String[] arguments) {
            Path catalog = DEFAULT_CATALOG;
            Path output = DEFAULT_OUTPUT_ROOT;
            String atlasId = null;
            boolean install = false;
            boolean check = false;
            for (int index = 0; index < arguments.length; index++) {
                switch (arguments[index]) {
                    case "--catalog" -> catalog = Path.of(requireValue(arguments, ++index, "--catalog"));
                    case "--output-root" -> output = Path.of(requireValue(arguments, ++index, "--output-root"));
                    case "--entity" -> atlasId = requireValue(arguments, ++index, "--entity");
                    case "--install" -> install = true;
                    case "--check" -> check = true;
                    default -> throw new IllegalArgumentException("unknown argument " + arguments[index]);
                }
            }
            if (install && check) {
                throw new IllegalArgumentException("--install and --check are mutually exclusive");
            }
            return new Options(catalog, output, atlasId, install, check);
        }

        private static String requireValue(
            final String[] arguments,
            final int index,
            final String option
        ) {
            if (index >= arguments.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return arguments[index];
        }
    }
}
