package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.SurfaceRipple;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the crucible lays the items its pool holds: the dissolving head cut into tiles
 * that start whole at the center of the fill's top and break off in turn to drift across
 * the open basin, each bobbing on the ripple at its own spot, and the stacks waiting
 * behind it in the basin's corners, riding just above the highest crest the ripple
 * reaches so no wave humps over them; all on the floor while nothing has melted
 * (decisions dissolve-shader-on-item, tiles-break-off-as-dissolve-advances,
 * each-tile-bobs-with-the-ripple).
 */
final class CrucibleItemLayout {

    /** The dissolving item's width across the basin, in blocks. */
    static final float HEAD_SIZE = 0.26f;
    /** The tiles along each side of the grid the dissolving item is cut into, four texture pixels each. */
    static final int TILE_GRID = 4;
    /** The tiles the dissolving item is cut into. */
    static final int TILE_COUNT = TILE_GRID * TILE_GRID;
    /** A tile's width, in blocks. */
    static final float TILE_SIZE = HEAD_SIZE / TILE_GRID;
    /** The span of the dissolve fraction a tile takes to drift from the grid to its resting spot. */
    static final float DRIFT_SPAN = 0.25f;
    /** A waiting item's width, in blocks. */
    static final float WAITING_SIZE = 0.14f;
    /** The waiting items the basin shows, one per corner. */
    static final int WAITING_SLOTS = 4;
    /** Lift above the ripple's crest, so an item never fights the surface for depth. */
    static final float FLOAT_LIFT = 1f / 256f;
    /** Gap between an item and the basin wall. */
    private static final float WALL_GAP = 1f / 64f;

    private static final float HALF = 0.5f;
    /** The cubic smoothstep 3t² - 2t³ written as t²(3 - 2t). */
    private static final float SMOOTHSTEP_RISE = 3f;
    private static final float SMOOTHSTEP_FALL = 2f;
    private static final float CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) * HALF;
    private static final float NEAR_CORNER = CrucibleBasin.FOOTPRINT_MIN + WALL_GAP + WAITING_SIZE * HALF;
    private static final float FAR_CORNER = CrucibleBasin.FOOTPRINT_MAX - WALL_GAP - WAITING_SIZE * HALF;
    private static final float[][] CORNERS = {
        {NEAR_CORNER, NEAR_CORNER}, {FAR_CORNER, FAR_CORNER}, {NEAR_CORNER, FAR_CORNER}, {FAR_CORNER, NEAR_CORNER},
    };
    /**
     * How far from the center the inner resting spots on each arm of the open basin lie:
     * a tile's width clear of the center, so no two arms' inner tiles overlap.
     */
    private static final float ARM_INNER = TILE_SIZE + TILE_SIZE * HALF;
    /**
     * How far the outer resting spots lie, a tile's width past the inner, their centers
     * inside the surface's rippling interior rather than its still rim cell.
     */
    private static final float ARM_OUTER = ARM_INNER + TILE_SIZE;
    /**
     * A tile's lift over the wave at its center, per block of amplitude: the most the ripple
     * rises from a tile's center to its corner near a crest at the highest wavenumber, so no
     * crest humps over a tile's edge (decision each-tile-bobs-with-the-ripple).
     */
    private static final float TILE_SAG_PER_AMPLITUDE =
            (float) (1.0 - Math.cos(SurfaceRipple.SECONDARY_WAVENUMBER * TILE_SIZE * HALF * Math.sqrt(2.0)));
    /** The four directions the open basin's arms run from the center, between the corners. */
    private static final float[][] ARM_DIRECTIONS = {{1f, 0f}, {0f, 1f}, {-1f, 0f}, {0f, -1f}};

    private static final List<Spot> GRID_SPOTS = gridSpots();
    private static final List<Spot> RESTING_SPOTS = restingSpotsByGridAngle();
    private static final float[] BREAK_OFF = rimFirstBreakOffs();

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
     * A spot on the basin's floor plan.
     *
     * @param x the X
     * @param z the Z
     */
    private record Spot(float x, float z) {
    }

    /**
     * Places the dissolving item whole and flat at the center of the fill's top, riding the
     * crest, the rest its handoff from the item entity eases into (decision consume-at-rest-in-place).
     *
     * @param surface   the drawn surface, or null while nothing has melted
     * @param amplitude the ripple amplitude the surface undulates at, in blocks
     * @return the head's placement
     */
    static ItemPlacement head(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude) {
        return new ItemPlacement(CENTER, crestY(surface, amplitude), CENTER, HEAD_SIZE);
    }

    /**
     * Places each shard of the dissolving item at its home, its centroid where it lies in the
     * whole item flat at the center of the fill's top, so the shards together draw the whole
     * item, each riding the wave at its own spot (decisions tiles-of-the-items-image,
     * each-tile-bobs-with-the-ripple).
     *
     * @param surface   the drawn surface, or null while nothing has melted
     * @param amplitude the ripple amplitude the surface undulates at, in blocks
     * @param ripple    the wave over this crucible's block at this frame
     * @param offsets   each shard centroid's X and Y offset from the face's center, in widths
     *                  of the face's larger side, Y running along the model's Y
     * @return one placement per shard, its size the width the whole face scales to
     */
    static List<ItemPlacement> shardHomes(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude,
                                          SurfaceRipple.Field ripple, List<float[]> offsets) {
        List<ItemPlacement> homes = new ArrayList<>(offsets.size());
        for (float[] offset : offsets) {
            float x = CENTER + offset[0] * HEAD_SIZE;
            float z = CENTER - offset[1] * HEAD_SIZE;
            homes.add(new ItemPlacement(x, tileY(surface, amplitude, ripple, x, z), z, HEAD_SIZE));
        }
        return homes;
    }

    /**
     * Places each tile of the dissolving item: in the grid centered on the fill's top until
     * the fraction reaches its break-off, then easing out to its resting spot in the open
     * basin over {@link #DRIFT_SPAN}, so the item is whole at zero and scattered near one,
     * each bobbing with the ripple at its own spot (decisions tiles-of-the-items-image,
     * tiles-break-off-as-dissolve-advances, each-tile-bobs-with-the-ripple).
     *
     * @param surface   the drawn surface, or null while nothing has melted
     * @param amplitude the ripple amplitude the surface undulates at, in blocks
     * @param fraction  how far the item has dissolved, zero to one
     * @param ripple    the wave over this crucible's block at this frame
     * @return one placement per tile, in {@link ItemTileClipper.TileGrid#tile} index order
     */
    static List<ItemPlacement> headTiles(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude,
                                         float fraction, SurfaceRipple.Field ripple) {
        List<ItemPlacement> tiles = new ArrayList<>(TILE_COUNT);
        for (int i = 0; i < TILE_COUNT; i++) {
            float drift = easeInOut((fraction - BREAK_OFF[i]) / DRIFT_SPAN);
            Spot grid = GRID_SPOTS.get(i);
            Spot rest = RESTING_SPOTS.get(i);
            float x = grid.x() + (rest.x() - grid.x()) * drift;
            float z = grid.z() + (rest.z() - grid.z()) * drift;
            tiles.add(new ItemPlacement(x, tileY(surface, amplitude, ripple, x, z), z, TILE_SIZE));
        }
        return tiles;
    }

    /**
     * Returns the height a tile rests at: the fill's surface plus the wave at the tile's
     * spot plus {@link #tileLift}, or the still floor while nothing has melted.
     *
     * @param surface   the drawn surface, or null while nothing has melted
     * @param amplitude the ripple amplitude the surface undulates at, in blocks
     * @param ripple    the wave over this crucible's block at this frame
     * @param x         the tile's block-relative X
     * @param z         the tile's block-relative Z
     * @return the Y in block-relative coords
     */
    static float tileY(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude, SurfaceRipple.Field ripple,
                       float x, float z) {
        if (surface == null) {
            return CrucibleBasin.FLOOR_Y + FLOAT_LIFT;
        }
        float scale = Math.max(amplitude, 0f);
        return surface.surfaceY() + scale * ripple.at(x, z) + tileLift(scale);
    }

    /**
     * Returns a tile's lift over the wave at its center.
     *
     * @param amplitude the ripple amplitude, in blocks
     * @return the lift, in blocks
     */
    static float tileLift(float amplitude) {
        return amplitude * TILE_SAG_PER_AMPLITUDE + FLOAT_LIFT;
    }

    /**
     * @return each tile's spot in the grid around the center, in tile index order
     */
    private static List<Spot> gridSpots() {
        List<Spot> spots = new ArrayList<>(TILE_COUNT);
        for (int row = 0; row < TILE_GRID; row++) {
            for (int column = 0; column < TILE_GRID; column++) {
                spots.add(new Spot(CENTER + ((column + HALF) / TILE_GRID - HALF) * HEAD_SIZE,
                        CENTER - ((row + HALF) / TILE_GRID - HALF) * HEAD_SIZE));
            }
        }
        return spots;
    }

    /**
     * Returns each tile's resting spot: four spots on each arm of the open basin between
     * the corner slots, two abreast and two deep, paired to the tiles by their angle around
     * the center so each tile drifts outward on its own side.
     *
     * @return each tile's resting spot, in tile index order
     */
    private static List<Spot> restingSpotsByGridAngle() {
        List<Spot> spots = new ArrayList<>(TILE_COUNT);
        for (float[] arm : ARM_DIRECTIONS) {
            for (float along : new float[] {ARM_INNER, ARM_OUTER}) {
                for (float abreast : new float[] {-TILE_SIZE * HALF, TILE_SIZE * HALF}) {
                    spots.add(new Spot(CENTER + arm[0] * along - arm[1] * abreast,
                            CENTER + arm[1] * along + arm[0] * abreast));
                }
            }
        }
        spots.sort(Comparator.comparingDouble(CrucibleItemLayout::angleAroundCenter));
        List<Integer> tilesByAngle = new ArrayList<>(TILE_COUNT);
        for (int i = 0; i < TILE_COUNT; i++) {
            tilesByAngle.add(i);
        }
        tilesByAngle.sort(Comparator.comparingDouble(i -> angleAroundCenter(GRID_SPOTS.get(i))));
        Spot[] byTile = new Spot[TILE_COUNT];
        for (int rank = 0; rank < TILE_COUNT; rank++) {
            byTile[tilesByAngle.get(rank)] = spots.get(rank);
        }
        return List.of(byTile);
    }

    private static double angleAroundCenter(Spot spot) {
        return Math.atan2(spot.z() - CENTER, spot.x() - CENTER);
    }

    /**
     * Returns each tile's break-off fraction: the rim tiles first, then the inner ones,
     * spaced evenly so the last one still reaches its spot by fraction one.
     *
     * @return each tile's break-off fraction, in tile index order
     */
    private static float[] rimFirstBreakOffs() {
        float[] breakOffs = new float[TILE_COUNT];
        int rank = 0;
        for (boolean rim : new boolean[] {true, false}) {
            for (int i = 0; i < TILE_COUNT; i++) {
                if (onRim(i) == rim) {
                    breakOffs[i] = rank * (1f - DRIFT_SPAN) / TILE_COUNT;
                    rank++;
                }
            }
        }
        return breakOffs;
    }

    private static boolean onRim(int index) {
        int column = index % TILE_GRID;
        int row = index / TILE_GRID;
        return column == 0 || row == 0 || column == TILE_GRID - 1 || row == TILE_GRID - 1;
    }

    /**
     * Eases a drift's progress in and out, clamped to its ends.
     *
     * @param progress the drift's linear progress
     * @return the eased progress, zero to one
     */
    private static float easeInOut(float progress) {
        float t = Math.clamp(progress, 0f, 1f);
        return t * t * (SMOOTHSTEP_RISE - SMOOTHSTEP_FALL * t);
    }

    /**
     * Places the waiting items in the basin's corners, as many as there are corners.
     *
     * @param surface      the drawn surface, or null while nothing has melted
     * @param amplitude    the ripple amplitude the surface undulates at, in blocks
     * @param waitingCount the stacks waiting behind the head
     * @return one placement per waiting item shown, oldest first
     */
    static List<ItemPlacement> waiting(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude,
                                       int waitingCount) {
        int shown = Math.min(waitingCount, WAITING_SLOTS);
        float y = crestY(surface, amplitude);
        List<ItemPlacement> placements = new ArrayList<>(shown);
        for (int i = 0; i < shown; i++) {
            placements.add(new ItemPlacement(CORNERS[i][0], y, CORNERS[i][1], WAITING_SIZE));
        }
        return placements;
    }

    /**
     * Returns the height a waiting item rests at: just above the ripple's highest crest,
     * which the surface shader lifts a full amplitude over the fill height, since a whole
     * item is wide enough for the wave to hump over, or the still floor.
     *
     * @param surface   the drawn surface, or null while nothing has melted
     * @param amplitude the ripple amplitude the surface undulates at, in blocks
     * @return the Y in block-relative coords
     */
    private static float crestY(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude) {
        if (surface == null) {
            return CrucibleBasin.FLOOR_Y + FLOAT_LIFT;
        }
        return surface.surfaceY() + Math.max(amplitude, 0f) + FLOAT_LIFT;
    }
}
