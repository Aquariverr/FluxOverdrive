package com.aquariverr.fluxod.client.gui.miniature;

import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridViewport.Hit;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridViewport.Rect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniatureGridViewportTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    void threeByThreeHasNineEditableCellsAndAnAdditionalOutputColumn() {
        MiniatureGridViewport viewport = compact(3);
        assertEquals(new Rect(10, 20, 28, 28), viewport.cellBounds(0, 0));
        assertEquals(new Rect(70, 80, 28, 28), viewport.cellBounds(2, 2));
        assertEquals(new Rect(100, 80, 28, 28), viewport.cellBounds(3, 2));
        int editable = 0;
        int output = 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x <= 3; x++) {
                Hit hit = centerHit(viewport, x, y);
                assertEquals(x, hit.x());
                assertEquals(y, hit.y());
                if (hit.editable()) editable++;
                if (hit.output()) output++;
            }
        }
        assertEquals(9, editable);
        assertEquals(3, output);
    }

    @Test
    void cellEdgesAndSpacingUseHalfOpenRectangles() {
        MiniatureGridViewport viewport = compact(3);
        assertEquals(Optional.of(new Hit(0, 0, false)), viewport.hitTest(10, 20));
        assertEquals(Optional.of(new Hit(0, 0, false)), viewport.hitTest(Math.nextDown(38.0), 34));
        assertTrue(viewport.hitTest(38, 34).isEmpty());
        assertTrue(viewport.hitTest(39, 34).isEmpty());
        assertEquals(Optional.of(new Hit(1, 0, false)), viewport.hitTest(40, 34));
        assertTrue(viewport.hitTest(24, 48).isEmpty());
        assertTrue(viewport.hitTest(24, 49).isEmpty());
        assertEquals(Optional.of(new Hit(0, 1, false)), viewport.hitTest(24, 50));
    }

    @Test
    void adjacentCellsWithoutSpacingAssignTheSharedEdgeToTheNextCell() {
        MiniatureGridViewport viewport = new MiniatureGridViewport(3, new Rect(10, 20, 112, 84),
                28, 0, 0.25, 4);
        assertEquals(Optional.of(new Hit(1, 0, false)), viewport.hitTest(38, 34));
        assertEquals(Optional.of(new Hit(0, 1, false)), viewport.hitTest(24, 48));
        assertTrue(viewport.hitTest(122, 34).isEmpty());
    }

    @Test
    void clipEdgesAndNonFiniteMousePositionsNeverHit() {
        MiniatureGridViewport viewport = compact(3);
        assertTrue(viewport.hitTest(Math.nextDown(10.0), 34).isEmpty());
        assertTrue(viewport.hitTest(24, Math.nextDown(20.0)).isEmpty());
        assertTrue(viewport.hitTest(128, 34).isEmpty());
        assertTrue(viewport.hitTest(24, 108).isEmpty());
        assertTrue(viewport.hitTest(Double.NaN, 34).isEmpty());
        assertTrue(viewport.hitTest(24, Double.POSITIVE_INFINITY).isEmpty());
    }

    @Test
    void elevenByElevenRejectsHiddenCellsAndCanPanToItsOutputColumn() {
        MiniatureGridViewport viewport = compact(11);
        assertEquals(new Rect(-110, -100, 28, 28), viewport.cellBounds(0, 0));
        assertTrue(viewport.hitTest(-96, -86).isEmpty());
        assertEquals(new Hit(4, 4, false), centerHit(viewport, 4, 4));

        viewport.panBy(120, 120);
        assertEquals(new Hit(0, 0, false), centerHit(viewport, 0, 0));
        assertTrue(viewport.hitTest(144, 34).isEmpty());

        viewport.panBy(-240, 0);
        assertEquals(new Rect(100, 20, 28, 28), viewport.cellBounds(11, 0));
        assertEquals(new Hit(11, 0, true), centerHit(viewport, 11, 0));
        assertFalse(centerHit(viewport, 11, 0).editable());
        assertEquals(new Rect(10, 20, 118, 88), viewport.clip());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5, 7, 9, 11})
    void fitMakesEveryCellAndOutputVisibleWithoutChangingLogicalCoordinates(int size) {
        MiniatureGridViewport viewport = compact(size);
        viewport.fit();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x <= size; x++) {
                Rect bounds = viewport.cellBounds(x, y);
                assertTrue(bounds.x() >= viewport.clip().x() - EPSILON);
                assertTrue(bounds.y() >= viewport.clip().y() - EPSILON);
                assertTrue(bounds.right() <= viewport.clip().right() + EPSILON);
                assertTrue(bounds.bottom() <= viewport.clip().bottom() + EPSILON);
                assertEquals(new Hit(x, y, x == size), centerHit(viewport, x, y));
            }
        }
    }

    @Test
    void zoomKeepsTheMouseAnchorOnTheSamePositionWithinTheCell() {
        MiniatureGridViewport viewport = roomy(3);
        Rect before = viewport.cellBounds(1, 1);
        double anchorX = before.x() + 7;
        double anchorY = before.y() + 21;
        assertTrue(viewport.zoomAt(anchorX, anchorY, 2));
        Rect after = viewport.cellBounds(1, 1);
        assertEquals(14, anchorX - after.x(), EPSILON);
        assertEquals(42, anchorY - after.y(), EPSILON);
        assertEquals(Optional.of(new Hit(1, 1, false)), viewport.hitTest(anchorX, anchorY));
    }

    @Test
    void panUsesScreenPixelsIndependentlyOfZoom() {
        MiniatureGridViewport viewport = roomy(3);
        viewport.zoomAt(210, 170, 1.5);
        Rect before = viewport.cellBounds(1, 1);
        viewport.panBy(17, -12);
        Rect after = viewport.cellBounds(1, 1);
        assertEquals(before.x() + 17, after.x(), EPSILON);
        assertEquals(before.y() - 12, after.y(), EPSILON);
        assertEquals(before.width(), after.width());
        assertEquals(new Hit(1, 1, false), centerHit(viewport, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 11})
    void projectedCornersCentersAndGapsStayConsistentAfterFractionalTransforms(int size) {
        MiniatureGridViewport viewport = roomy(size);
        viewport.fit();
        viewport.zoomAt(210, 170, 0.81);
        viewport.panBy(3.25, -2.75);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x <= size; x++) {
                Rect bounds = viewport.cellBounds(x, y);
                assertEquals(Optional.of(new Hit(x, y, x == size)), viewport.hitTest(bounds.x(), bounds.y()));
                assertEquals(new Hit(x, y, x == size), centerHit(viewport, x, y));
                assertTrue(viewport.hitTest(bounds.right(), bounds.y() + bounds.height() / 2).isEmpty());
                assertTrue(viewport.hitTest(bounds.x() + bounds.width() / 2, bounds.bottom()).isEmpty());
            }
        }
    }

    @Test
    void zoomLimitsAndOutsideAnchorsDoNotMoveTheGrid() {
        MiniatureGridViewport viewport = roomy(3);
        assertTrue(viewport.zoomAt(210, 170, 100));
        assertEquals(4, viewport.scale());
        Rect atMaximum = viewport.cellBounds(1, 1);
        assertFalse(viewport.zoomAt(210, 170, 2));
        assertEquals(atMaximum, viewport.cellBounds(1, 1));
        assertFalse(viewport.zoomAt(410, 170, 0.5));
        assertEquals(atMaximum, viewport.cellBounds(1, 1));
        assertTrue(viewport.zoomAt(210, 170, 0.0001));
        assertEquals(0.25, viewport.scale());
        Rect atMinimum = viewport.cellBounds(1, 1);
        assertFalse(viewport.zoomAt(210, 170, 0.5));
        assertEquals(atMinimum, viewport.cellBounds(1, 1));
    }

    @Test
    void resetRestoresCenteredNaturalScaleAfterPanningAndZooming() {
        MiniatureGridViewport viewport = compact(11);
        Rect original = viewport.cellBounds(0, 0);
        viewport.fit();
        viewport.panBy(-50, 17);
        viewport.zoomAt(60, 60, 2.5);
        viewport.reset();
        assertEquals(1, viewport.scale());
        assertEquals(original, viewport.cellBounds(0, 0));
    }

    @Test
    void boundsRejectCoordinatesBeyondTheGridAndSingleOutputColumn() {
        MiniatureGridViewport viewport = compact(3);
        assertThrows(IllegalArgumentException.class, () -> viewport.cellBounds(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> viewport.cellBounds(4, 0));
        assertThrows(IllegalArgumentException.class, () -> viewport.cellBounds(0, -1));
        assertThrows(IllegalArgumentException.class, () -> viewport.cellBounds(0, 3));
    }

    @Test
    void invalidTransformsAreRejectedWithoutChangingTheViewport() {
        MiniatureGridViewport viewport = compact(3);
        Rect original = viewport.cellBounds(0, 0);
        assertThrows(IllegalArgumentException.class, () -> viewport.panBy(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> viewport.panBy(1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> viewport.zoomAt(24, 34, 0));
        assertThrows(IllegalArgumentException.class, () -> viewport.zoomAt(24, 34, -1));
        assertThrows(IllegalArgumentException.class, () -> viewport.zoomAt(24, 34, Double.NaN));
        assertEquals(original, viewport.cellBounds(0, 0));
        assertEquals(1, viewport.scale());
    }

    @Test
    void constructionRejectsUnsupportedGridsAndInvalidGeometry() {
        Rect clip = new Rect(10, 20, 118, 88);
        assertThrows(IllegalArgumentException.class, () -> compact(4));
        assertThrows(IllegalArgumentException.class, () -> new Rect(0, 0, 0, 88));
        assertThrows(IllegalArgumentException.class, () -> new Rect(0, Double.NaN, 118, 88));
        assertThrows(IllegalArgumentException.class,
                () -> new MiniatureGridViewport(3, clip, 0, 2, 0.25, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new MiniatureGridViewport(3, clip, 28, -1, 0.25, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new MiniatureGridViewport(3, clip, 28, 2, 0, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new MiniatureGridViewport(3, clip, 28, 2, 4, 0.25));
    }

    private static MiniatureGridViewport compact(int size) {
        return new MiniatureGridViewport(size, new Rect(10, 20, 118, 88), 28, 2, 0.25, 4);
    }

    private static MiniatureGridViewport roomy(int size) {
        return new MiniatureGridViewport(size, new Rect(10, 20, 400, 300), 28, 2, 0.25, 4);
    }

    private static Hit centerHit(MiniatureGridViewport viewport, int x, int y) {
        Rect bounds = viewport.cellBounds(x, y);
        return viewport.hitTest(bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2).orElseThrow();
    }
}
