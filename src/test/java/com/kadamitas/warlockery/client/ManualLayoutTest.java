package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class ManualLayoutTest {
    private static final List<Resolution> COMMON_SCALED_RESOLUTIONS = List.of(
        new Resolution(320, 240),
        new Resolution(426, 240),
        new Resolution(640, 360),
        new Resolution(854, 480),
        new Resolution(960, 540)
    );

    @TestFactory
    Stream<DynamicTest> controlsStayReadableAndInsideTheBookAtCommonGuiScales() {
        return COMMON_SCALED_RESOLUTIONS.stream().map(resolution -> DynamicTest.dynamicTest(
            resolution.width() + "x" + resolution.height(),
            () -> verifyLayout(resolution)
        ));
    }

    @Test
    void narrowReadersStackTheCloseButtonWhileWideReadersUseOneRow() {
        assertEquals(2, ManualLayout.calculate(320, 240).controlRows());
        assertEquals(2, ManualLayout.calculate(426, 240).controlRows());
        assertEquals(1, ManualLayout.calculate(640, 360).controlRows());
    }

    private static void verifyLayout(final Resolution resolution) {
        final ManualLayout layout = ManualLayout.calculate(resolution.width(), resolution.height());
        assertTrue(layout.left() >= 0);
        assertTrue(layout.top() >= 0);
        assertTrue(layout.right() <= resolution.width());
        assertTrue(layout.bottom() <= resolution.height());
        assertTrue(layout.navigationWidth() >= 88);
        assertTrue(layout.contentWidth() >= 198);
        assertTrue(layout.bodyLineCapacity() >= 9);
        assertTrue(layout.sectionRows() >= 5);

        final List<ManualLayout.Bounds> controls = layout.controls();
        assertEquals(3, controls.size());
        controls.forEach(control -> {
            assertTrue(control.x() >= layout.contentLeft());
            assertTrue(control.y() >= layout.top());
            assertTrue(control.right() <= layout.contentRight());
            assertTrue(control.bottom() <= layout.bottom());
            assertTrue(control.width() >= 80);
            assertEquals(20, control.height());
        });
        IntStream.range(0, controls.size()).forEach(left ->
            IntStream.range(left + 1, controls.size()).forEach(right ->
                assertFalse(controls.get(left).overlaps(controls.get(right)))
            )
        );

        final int finalChapterBottom = layout.sectionListTop() + (layout.sectionRows() - 1) * 24 + 20;
        assertTrue(finalChapterBottom < layout.bottom());
    }

    private record Resolution(int width, int height) {
    }
}
