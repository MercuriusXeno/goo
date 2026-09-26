package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the crucible lays the items its pool holds: the dissolving head flat at the
 * center of the fill's top, the stacks waiting behind it in the basin's corners, all
 * riding the fill height, or the floor while nothing has melted (decision dissolve-shader-on-item).
 */
final class CrucibleItemLayout {

    /** The dissolving item's width across the basin, in blocks. */
    static final float HEAD_SIZE = 0.26f;
    /** A waiting item's width, in blocks. */
    static final float WAITING_SIZE = 0.14f;
    /** The waiting items the basin shows, one per corner. */
    static final int WAITING_SLOTS = 4;
    /** Lift above the fill, so an item never fights the surface for depth. */
    static final float FLOAT_LIFT = 1f / 256f;
    /** Gap between a waiting item and the basin wall. */
    private static final float WALL_GAP = 1f / 64f;

    private static final float HALF = 0.5f;
    private static final float CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) * HALF;
    private static final float NEAR_CORNER = CrucibleBasin.FOOTPRINT_MIN + WALL_GAP + WAITING_SIZE * HALF;
    private static final float FAR_CORNER = CrucibleBasin.FOOTPRINT_MAX - WALL_GAP - WAITING_SIZE * HALF;
    private static final float[][] CORNERS = {
        {NEAR_CORNER, NEAR_CORNER}, {FAR_CORNER, FAR_CORNER}, {NEAR_CORNER, FAR_CORNER}, {FAR_CORNER, NEAR_CORNER},
    };

    private CrucibleItemLayout() {
    }

    /**
     * Where one item lies, its center in block-relative coords and its width.
     *
     * @param x    the center X
     * @param y    the Y it lies at
     * @param z    the center Z
     * @param size the width it is scaled to, in blocks
     */
    record ItemPlacement(float x, float y, float z, float size) {
    }

    /**
     * Places the dissolving item flat at the center of the fill's top.
     *
     * @param surface the drawn surface, or null while nothing has melted
     * @return the head's placement
     */
    static ItemPlacement head(CrucibleBasin.@Nullable DrawnSurface surface) {
        return new ItemPlacement(CENTER, restingY(surface), CENTER, HEAD_SIZE);
    }

    /**
     * Places the waiting items in the basin's corners, as many as there are corners.
     *
     * @param surface      the drawn surface, or null while nothing has melted
     * @param waitingCount the stacks waiting behind the head
     * @return one placement per waiting item shown, oldest first
     */
    static List<ItemPlacement> waiting(CrucibleBasin.@Nullable DrawnSurface surface, int waitingCount) {
        int shown = Math.min(waitingCount, WAITING_SLOTS);
        float y = restingY(surface) + FLOAT_LIFT;
        List<ItemPlacement> placements = new ArrayList<>(shown);
        for (int i = 0; i < shown; i++) {
            placements.add(new ItemPlacement(CORNERS[i][0], y, CORNERS[i][1], WAITING_SIZE));
        }
        return placements;
    }

    /**
     * Returns the height an item rests at: just above the fill, or the floor.
     *
     * @param surface the drawn surface, or null while nothing has melted
     * @return the Y in block-relative coords
     */
    private static float restingY(CrucibleBasin.@Nullable DrawnSurface surface) {
        return (surface == null ? CrucibleBasin.FLOOR_Y : surface.surfaceY()) + FLOAT_LIFT;
    }
}
