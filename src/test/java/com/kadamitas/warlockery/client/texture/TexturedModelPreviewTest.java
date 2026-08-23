package com.kadamitas.warlockery.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.junit.jupiter.api.Test;

/** Close-up textured software previews must expose actual paint and use a useful inspection scale. */
final class TexturedModelPreviewTest {
    @Test
    void frontAndSidePreviewsSampleTheRealUvAtlasAndFillTheInspectionFrame() {
        final ModelPart root = cubeRoot();
        final ConceptTextureBaker.ConceptViews views = new ConceptTextureBaker.ConceptViews(
            concept(new Color(202, 48, 43)),
            concept(new Color(46, 168, 74)),
            concept(new Color(52, 74, 198)),
            concept(new Color(220, 171, 42))
        );
        final BufferedImage atlas = ConceptTextureBaker.bake(root, 96, 96, views);

        final BufferedImage front = TexturedModelPreview.render(
            root, atlas, TexturedModelPreview.View.FRONT, 320
        );
        final BufferedImage side = TexturedModelPreview.render(
            root, atlas, TexturedModelPreview.View.LEFT, 320
        );

        assertSubjectFrame(front);
        assertSubjectFrame(side);
        assertDominant(front.getRGB(160, 160), 16, "front preview must show front-view red paint");
        assertDominant(side.getRGB(160, 160), 8, "side preview must show left-view green paint");
    }

    @Test
    void previewRasterizationIsDeterministic() {
        final ModelPart root = cubeRoot();
        final BufferedImage source = concept(new Color(112, 54, 89));
        final BufferedImage atlas = ConceptTextureBaker.bake(
            root,
            96,
            96,
            new ConceptTextureBaker.ConceptViews(source, source, source, source)
        );

        final BufferedImage first = TexturedModelPreview.render(
            root, atlas, TexturedModelPreview.View.THREE_QUARTER, 320
        );
        final BufferedImage second = TexturedModelPreview.render(
            root, atlas, TexturedModelPreview.View.THREE_QUARTER, 320
        );

        assertArrayEquals(pixels(first), pixels(second));
    }

    private static ModelPart cubeRoot() {
        final MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -24.0F, -4.0F, 16.0F, 24.0F, 8.0F),
            PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 96, 96).bakeRoot();
    }

    private static BufferedImage concept(final Color color) {
        final BufferedImage image = new BufferedImage(48, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final boolean subject = x >= 10 && x < 38 && y >= 6 && y < 58;
                final int delta = subject && ((x / 3 + y / 4) & 1) == 0 ? 12 : 0;
                image.setRGB(x, y, subject
                    ? new Color(
                        Math.min(255, color.getRed() + delta),
                        Math.min(255, color.getGreen() + delta),
                        Math.min(255, color.getBlue() + delta)
                    ).getRGB()
                    : new Color(187, 184, 180).getRGB());
            }
        }
        return image;
    }

    private static void assertSubjectFrame(final BufferedImage image) {
        int minimumY = image.getHeight();
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getAlpha() != 0 && colorDistance(pixel, new Color(192, 188, 182)) > 18.0) {
                    minimumY = Math.min(minimumY, y);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        final double occupied = (maximumY - minimumY + 1) / (double) image.getHeight();
        assertTrue(occupied >= 0.68 && occupied <= 0.88,
            "subject must occupy a close-up inspection scale, was " + occupied);
    }

    private static void assertDominant(final int argb, final int shift, final String message) {
        final Color color = new Color(argb, true);
        final int selected = (argb >>> shift) & 0xFF;
        final int first = shift == 16 ? color.getGreen() : color.getRed();
        final int second = shift == 0 ? color.getGreen() : color.getBlue();
        assertTrue(selected > first * 1.4 && selected > second * 1.4, message + ": " + color);
    }

    private static double colorDistance(final Color first, final Color second) {
        final int red = first.getRed() - second.getRed();
        final int green = first.getGreen() - second.getGreen();
        final int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    private static int[] pixels(final BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
