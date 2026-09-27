package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.SurfaceRipple;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the crucible lays the items its pool holds: the dissolving head broken into shards
 * that start whole at the center of the fill's top and break off rim first to drift to
 * their own seeded spots across the open basin, each bobbing on the ripple at its own spot,
 * and the stacks waiting behind it in the basin's corners, riding just above the highest
 * crest the ripple reaches so no wave humps over them; all on the floor while nothing has
 * melted (decisions dissolve-shader-on-item, tiles-break-off-as-dissolve-advances,
 * each-tile-bobs-with-the-ripple).
 */
final class CrucibleItemLayout {

    /** The dissolving item's width across the basin, in blocks. */
    static final float HEAD_SIZE = 0.26f;
    /** A block's width across the basin, smaller than a flat item's so its shards fit the open basin at rest. */
    static final float BLOCK_HEAD_SIZE = 0.16f;
    /** The span of the dissolve fraction a shard takes to drift from home to its resting spot. */
    static final float DRIFT_SPAN = 0.25f;
    /** A waiting item's width, in blocks. */
    static final float WAITING_SIZE = 0.14f;
    /** The waiting items the basin shows, one per corner. */
    static final int WAITING_SLOTS = 4;
    /** Lift above the ripple's crest, so an item never fights the surface for depth. */
    static final float FLOAT_LIFT = 1f / 256f;
    /** Gap between an item and the basin wall. */
    private static final float WALL_GAP = 1f / 64f;
    /** Keeps the rest shifts' draw apart from the shard cut's, on the same item seed. */
    private static final long SPOT_SALT = 0x6A09E667F3BCC909L;

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
     * One shard as the layout sees it, in blocks relative to the head's center with the item
     * lying as it rests: where its centroid sits in the whole item, how far its lowest cell
     * lies under the centroid, and the texel cells it covers on the basin floor.
     *
     * @param homeX     the centroid's X offset from the head's center
     * @param homeY     the centroid's height relative to the head's center
     * @param homeZ     the centroid's Z offset from the head's center
     * @param floorY    its lowest cell's height relative to the head's center
     * @param footprint the lattice cells it covers seen from above, each a column and a row
     * @param reach     how far its cells reach from its centroid across the surface, which
     *                  sizes its lift over the wave
     */
    record ShardPiece(float homeX, float homeY, float homeZ, float floorY, int[][] footprint, float reach) {
    }

    /**
     * The whole head as the layout sees it: the texel lattice its shards' footprints are
     * counted on, and how far its lowest point lies under its center.
     *
     * @param originX the lattice's low X edge, in blocks relative to the head's center
     * @param originZ the lattice's low Z edge, in blocks relative to the head's center
     * @param cell    one lattice cell's width, one texel of the item, in blocks
     * @param bottomY the head's lowest point relative to its center
     */
    record HeadFrame(float originX, float originZ, float cell, float bottomY) {
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
     * Places each shard of the dissolving item: at home, its centroid where it lies in the
     * whole item standing on the fill's top at the basin center, until the fraction reaches
     * its break-off, then easing out by its rest shift over {@link #DRIFT_SPAN} and down onto
     * the surface, so the item is whole at zero and scattered near one, each shard riding the
     * wave at its own spot (decisions tiles-of-the-items-image, tiles-break-off-as-dissolve-advances,
     * each-tile-bobs-with-the-ripple).
     *
     * @param surface   the drawn surface, or null while nothing has melted
     * @param amplitude the ripple amplitude the surface undulates at, in blocks
     * @param fraction  how far the item has dissolved, zero to one
     * @param ripple    the wave over this crucible's block at this frame
     * @param shards    the item's shards, in shard order
     * @param frame     the head's lattice and bottom
     * @param shifts    each shard's rest shift in lattice cells, as {@link #restShifts} answers
     * @return one placement per shard, its centroid; its size the width the whole head scales to
     */
    static List<ItemPlacement> headShards(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude,
                                          float fraction, SurfaceRipple.Field ripple, List<ShardPiece> shards,
                                          HeadFrame frame, int[][] shifts) {
        float[] breakOffs = breakOffs(shards);
        List<ItemPlacement> placements = new ArrayList<>(shards.size());
        for (int i = 0; i < shards.size(); i++) {
            ShardPiece shard = shards.get(i);
            float drift = easeInOut((fraction - breakOffs[i]) / DRIFT_SPAN);
            float x = CENTER + shard.homeX() + shifts[i][0] * frame.cell() * drift;
            float z = CENTER + shard.homeZ() + shifts[i][1] * frame.cell() * drift;
            float rise = shard.homeY() - frame.bottomY() + (frame.bottomY() - shard.floorY()) * drift;
            placements.add(new ItemPlacement(x, shardY(surface, amplitude, ripple, x, z, shard.reach()) + rise, z,
                    HEAD_SIZE));
        }
        return placements;
    }

    /**
     * Returns each shard's break-off fraction: ranked by its centroid's distance from the
     * head's center, farthest first, spaced evenly so the last still reaches its spot by one.
     *
     * @param shards the item's shards
     * @return each shard's break-off fraction, in shard order
     */
    static float[] breakOffs(List<ShardPiece> shards) {
        List<Integer> byDistance = new ArrayList<>(shards.size());
        for (int i = 0; i < shards.size(); i++) {
            byDistance.add(i);
        }
        byDistance.sort(Comparator.comparingDouble((Integer i) -> distanceFromCenter(shards.get(i))).reversed());
        float[] breakOffs = new float[shards.size()];
        for (int rank = 0; rank < byDistance.size(); rank++) {
            breakOffs[byDistance.get(rank)] = rank * (1f - DRIFT_SPAN) / shards.size();
        }
        return breakOffs;
    }

    /**
     * @param shard a shard
     * @return its centroid's distance from the head's center
     */
    static double distanceFromCenter(ShardPiece shard) {
        return Math.sqrt(shard.homeX() * shard.homeX() + shard.homeY() * shard.homeY()
                + shard.homeZ() * shard.homeZ());
    }

    /**
     * Returns each shard's rest shift, whole lattice cells from its home, drawn from the
     * item's seed: the shards covering the most cells placed first, each where all its cells
     * lie inside the basin footprint by the wall gap, clear of the four corner slots and of
     * every cell a shard placed before it covers, so no two shards' pixels overlap at rest.
     * The shift is whole cells, so every shard's pixels stay on the one lattice.
     *
     * @param shards the item's shards
     * @param frame  the head's lattice
     * @param seed   the item's seed
     * @return each shard's shift, a column and a row, in shard order
     */
    static int[][] restShifts(List<ShardPiece> shards, HeadFrame frame, long seed) {
        RestLattice lattice = new RestLattice(frame);
        List<Integer> bySize = new ArrayList<>(shards.size());
        for (int i = 0; i < shards.size(); i++) {
            bySize.add(i);
        }
        bySize.sort(Comparator.comparingInt((Integer i) -> shards.get(i).footprint().length).reversed());
        ItemShardCutter.SeedStream stream = new ItemShardCutter.SeedStream(seed ^ SPOT_SALT);
        int[][] shifts = new int[shards.size()][];
        for (int i : bySize) {
            shifts[i] = lattice.place(shards.get(i).footprint(), stream);
        }
        return shifts;
    }

    /**
     * The lattice rows and columns a footprint reaches.
     *
     * @param lowRow     its lowest row
     * @param highRow    its highest row
     * @param lowColumn  its lowest column
     * @param highColumn its highest column
     */
    private record FootprintReach(int lowRow, int highRow, int lowColumn, int highColumn) {

        static FootprintReach of(int[][] footprint) {
            int lowRow = Integer.MAX_VALUE;
            int highRow = Integer.MIN_VALUE;
            int lowColumn = Integer.MAX_VALUE;
            int highColumn = Integer.MIN_VALUE;
            for (int[] cell : footprint) {
                lowRow = Math.min(lowRow, cell[1]);
                highRow = Math.max(highRow, cell[1]);
                lowColumn = Math.min(lowColumn, cell[0]);
                highColumn = Math.max(highColumn, cell[0]);
            }
            return new FootprintReach(lowRow, highRow, lowColumn, highColumn);
        }
    }

    /**
     * The basin floor counted in the head's lattice cells: which cells a resting shard may
     * cover, and which it already does.
     */
    private static final class RestLattice {
        private final int lowColumn;
        private final int lowRow;
        private final int columns;
        private final int rows;
        private final boolean[] blocked;

        RestLattice(HeadFrame frame) {
            float low = CrucibleBasin.FOOTPRINT_MIN + WALL_GAP - CENTER;
            float high = CrucibleBasin.FOOTPRINT_MAX - WALL_GAP - CENTER;
            lowColumn = (int) Math.ceil((low - frame.originX()) / frame.cell());
            lowRow = (int) Math.ceil((low - frame.originZ()) / frame.cell());
            columns = (int) Math.floor((high - frame.originX()) / frame.cell()) - lowColumn;
            rows = (int) Math.floor((high - frame.originZ()) / frame.cell()) - lowRow;
            blocked = new boolean[Math.max(columns, 0) * Math.max(rows, 0)];
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    float x = CENTER + frame.originX() + (lowColumn + column) * frame.cell();
                    float z = CENTER + frame.originZ() + (lowRow + row) * frame.cell();
                    blocked[row * columns + column] = inCorner(x, z, frame.cell());
                }
            }
        }

        private static boolean inCorner(float x, float z, float cell) {
            float reach = WAITING_SIZE * HALF;
            for (float[] corner : CORNERS) {
                if (overlaps(x, cell, corner[0], reach) && overlaps(z, cell, corner[1], reach)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * @param low    a cell's low edge along an axis
         * @param cell   the cell's width
         * @param center a slot's center along the axis
         * @param reach  the slot's half width
         * @return true if the cell and the slot share some of the axis
         */
        private static boolean overlaps(float low, float cell, float center, float reach) {
            return low < center + reach && low + cell > center - reach;
        }

        /**
         * Places one footprint at the first clear shift of a seeded order over every shift
         * that keeps it on the floor, and marks its cells covered.
         *
         * @param footprint the shard's cells at home
         * @param stream    the item's seeded stream
         * @return the shift, a column and a row; none when nothing is clear
         */
        int[] place(int[][] footprint, ItemShardCutter.SeedStream stream) {
            int[] best = null;
            int bestContact = 0;
            for (int[] shift : shuffledShifts(footprint, stream)) {
                if (!fits(footprint, shift)) {
                    continue;
                }
                int contact = contact(footprint, shift);
                if (best == null || contact > bestContact) {
                    best = shift;
                    bestContact = contact;
                }
            }
            if (best == null) {
                return new int[] {0, 0};
            }
            cover(footprint, best);
            return best;
        }

        /**
         * Lists every shift that keeps a footprint's reach on the lattice, in the seeded order
         * that breaks ties between equally snug spots.
         *
         * @param footprint the shard's cells at home
         * @param stream    the item's seeded stream
         * @return the shifts, each a column and a row
         */
        private List<int[]> shuffledShifts(int[][] footprint, ItemShardCutter.SeedStream stream) {
            FootprintReach reach = FootprintReach.of(footprint);
            List<int[]> shifts = new ArrayList<>();
            for (int row = lowRow - reach.lowRow(); row < lowRow + rows - reach.highRow(); row++) {
                for (int column = lowColumn - reach.lowColumn(); column < lowColumn + columns - reach.highColumn();
                     column++) {
                    shifts.add(new int[] {column, row});
                }
            }
            for (int i = shifts.size() - 1; i > 0; i--) {
                int j = stream.nextInt(i + 1);
                int[] swap = shifts.get(i);
                shifts.set(i, shifts.get(j));
                shifts.set(j, swap);
            }
            return shifts;
        }

        /**
         * Counts the sides of a placed footprint's cells that touch a wall, a corner slot or a
         * shard already placed, so each shard settles against the others and the open floor
         * stays in one piece for the shards still to come.
         *
         * @param footprint the shard's cells at home
         * @param shift     the candidate shift
         * @return the touching sides
         */
        private int contact(int[][] footprint, int[] shift) {
            int touching = 0;
            for (int[] cell : footprint) {
                int column = cell[0] + shift[0] - lowColumn;
                int row = cell[1] + shift[1] - lowRow;
                touching += closed(column - 1, row) + closed(column + 1, row)
                        + closed(column, row - 1) + closed(column, row + 1);
            }
            return touching;
        }

        private int closed(int column, int row) {
            return open(column, row) ? 0 : 1;
        }

        private boolean open(int column, int row) {
            return onLattice(column, row) && !blocked[row * columns + column];
        }

        private boolean onLattice(int column, int row) {
            return column >= 0 && row >= 0 && column < columns && row < rows;
        }

        private boolean fits(int[][] footprint, int[] shift) {
            for (int[] cell : footprint) {
                if (!open(cell[0] + shift[0] - lowColumn, cell[1] + shift[1] - lowRow)) {
                    return false;
                }
            }
            return true;
        }

        private void cover(int[][] footprint, int[] shift) {
            for (int[] cell : footprint) {
                blocked[(cell[1] + shift[1] - lowRow) * columns + cell[0] + shift[0] - lowColumn] = true;
            }
        }
    }

    /**
     * Returns the height a shard's centroid rides at before its rise over the surface: the
     * fill's surface plus the wave at the shard's spot plus {@link #shardLift}, or the still
     * floor while nothing has melted (decision each-tile-bobs-with-the-ripple).
     *
     * @param surface   the drawn surface, or null while nothing has melted
     * @param amplitude the ripple amplitude the surface undulates at, in blocks
     * @param ripple    the wave over this crucible's block at this frame
     * @param x         the shard's block-relative X
     * @param z         the shard's block-relative Z
     * @param reach     how far the shard reaches from its centroid across the surface, in blocks
     * @return the Y in block-relative coords
     */
    static float shardY(CrucibleBasin.@Nullable DrawnSurface surface, float amplitude, SurfaceRipple.Field ripple,
                        float x, float z, float reach) {
        if (surface == null) {
            return CrucibleBasin.FLOOR_Y + FLOAT_LIFT;
        }
        float scale = Math.max(amplitude, 0f);
        return surface.surfaceY() + scale * ripple.at(x, z) + shardLift(scale, reach);
    }

    /**
     * Returns a shard's lift over the wave at its centroid: the most the ripple rises from the
     * centroid to the shard's farthest reach near a crest at the highest wavenumber, so no
     * crest humps over its edge; a wide shard sits higher than a narrow one, and no lift
     * passes the wave's full rise of two amplitudes.
     *
     * @param amplitude the ripple amplitude, in blocks
     * @param reach     how far the shard reaches from its centroid, in blocks
     * @return the lift, in blocks
     */
    static float shardLift(float amplitude, float reach) {
        double phase = Math.min(SurfaceRipple.SECONDARY_WAVENUMBER * reach, Math.PI);
        return amplitude * (float) (1.0 - Math.cos(phase)) + FLOAT_LIFT;
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
