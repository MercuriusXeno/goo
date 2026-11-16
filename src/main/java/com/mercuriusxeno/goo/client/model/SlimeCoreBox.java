package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A box of a volume cell's goo slime core, in 120ths of a block, the unit
 * that holds every bound whole (a layer is 8, the side margin 15), and the
 * rule turning a cell and the cells of its goo around it into its core: the
 * core keeps 1/8 of its goo as margin wherever the goo ends, the ground, the
 * surface, an open side or a wall: 2 px at the sides, and above and below
 * 1/8 of its column's height, never more than 2 px, so a cell holds a core at
 * every fill and the core grows with each layer; and the core runs through
 * its margin to the face it shares with a cell of its goo whose core stands
 * across it, over the height both cores span, since only margin parts them there.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * residual-always-draws-a-core-inside-a-one-eighth-margin
 * slime-core-keeps-a-two-layer-gap-and-grows-with-fill
 * slime-cores-connect-where-only-padding-parts-them
 * stacked-goo-cells-share-one-slime-core
 *
 * @param minX the west bound
 * @param minY the floor
 * @param minZ the north bound
 * @param maxX the east bound
 * @param maxY the top
 * @param maxZ the south bound
 */
public record SlimeCoreBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** A whole block, in the box's units. */
    static final int WHOLE = 120;
    /** The layers a full cell holds. */
    private static final int FULL_LAYERS = VolumeCellBlock.MAX_LAYERS;
    /** One layer, in the box's units. */
    private static final int UNITS_PER_LAYER = WHOLE / FULL_LAYERS;
    /** The share of its goo the core keeps as margin on each axis: one part in this many. */
    private static final int MARGIN_PARTS = 8;
    /** The widest margin, 2 px: 1/8 of a block, kept at every side and capping the margin above and below. */
    private static final int MARGIN = WHOLE / MARGIN_PARTS;
    /** The offset toward the west, the north or below: the low side of an axis. */
    private static final int LOW_SIDE = -1;
    /** The offsets along one axis: the cell's own column and the cells to either side. */
    private static final int[] OFFSETS = {LOW_SIDE, 0, 1};

    /**
     * The core a cell read alone holds.
     *
     * @param layers the layers the cell holds
     * @return the core's box, empty for a cell of no layers
     */
    public static Optional<SlimeCoreBox> of(int layers) {
        return boxesOf(LayersAround.alone(layers)).stream().findFirst();
    }

    /**
     * The boxes a cell's core fills, read from the cells of its goo around
     * it. The cell's plan splits into three bands along each axis, the
     * margin toward each side and the middle; each of the nine columns this
     * makes fills over the height every core meeting at it spans: the middle
     * column the cell's own core, a side column the height the cell's core
     * shares with the core across that side, and a corner column the height
     * it shares with both sides and the diagonal cell.
     *
     * @param around the layers the cells of the goo hold around the cell
     * @return the core's boxes, none where the cell holds no core
     */
    public static List<SlimeCoreBox> boxesOf(LayersAround around) {
        List<SlimeCoreBox> boxes = new ArrayList<>();
        for (int dx : OFFSETS) {
            for (int dz : OFFSETS) {
                columnBox(around, dx, dz).ifPresent(boxes::add);
            }
        }
        return boxes;
    }

    /**
     * Whether this box's side lies on its block's boundary.
     *
     * @param face the side
     * @return true where the side stands on the block's own face
     */
    public boolean onBoundary(Direction face) {
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return face.getAxis().choose(maxX, maxY, maxZ) == WHOLE;
        }
        return face.getAxis().choose(minX, minY, minZ) == 0;
    }

    /**
     * Whether the core of the cell across a boundary side covers that side,
     * so the two read as one core with no face between them: a cell across
     * whose core falls short of the shared face leaves this side to draw.
     *
     * @param face   a side of this box on its block's boundary
     * @param around the layers the cells of the goo hold around this box's cell
     * @return true where the core across meets the whole side
     */
    public boolean joinsAcross(Direction face, LayersAround around) {
        int towardX = face.getStepX() == 0 ? bandOf(minX, maxX) : -bandOf(minX, maxX);
        int towardZ = face.getStepZ() == 0 ? bandOf(minZ, maxZ) : -bandOf(minZ, maxZ);
        LayersAround across = around.shiftedBy(face.getStepX(), face.getStepY(), face.getStepZ());
        return columnBox(across, towardX, towardZ)
                .filter(theirs -> theirs.onBoundary(face.getOpposite()) && theirs.spansAcross(this, face.getAxis()))
                .isPresent();
    }

    /**
     * Whether this box reaches at least as far as another along both axes but one.
     */
    private boolean spansAcross(SlimeCoreBox other, Direction.Axis skipped) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != skipped && (axis.choose(minX, minY, minZ) > axis.choose(other.minX, other.minY, other.minZ)
                    || axis.choose(maxX, maxY, maxZ) < axis.choose(other.maxX, other.maxY, other.maxZ))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The box a column of a cell's plan fills, over the height every core meeting at it spans.
     */
    private static Optional<SlimeCoreBox> columnBox(LayersAround around, int dx, int dz) {
        return sharedSpan(around, dx, dz).map(span -> new SlimeCoreBox(
                bandStart(dx), span.floor(), bandStart(dz), bandEnd(dx), span.top(), bandEnd(dz)));
    }

    /**
     * The band a box's bounds along one axis stand in.
     */
    private static int bandOf(int min, int max) {
        return min == 0 ? LOW_SIDE : max == WHOLE ? 1 : 0;
    }

    /**
     * The height every core meeting at a column spans: the cell's own, and
     * the cells across each side the column leans toward.
     */
    private static Optional<Span> sharedSpan(LayersAround around, int dx, int dz) {
        Optional<Span> shared = spanAt(around, 0, 0);
        if (dx != 0) {
            shared = shared.flatMap(span -> span.overlap(spanAt(around, dx, 0)));
        }
        if (dz != 0) {
            shared = shared.flatMap(span -> span.overlap(spanAt(around, 0, dz)));
        }
        if (dx != 0 && dz != 0) {
            shared = shared.flatMap(span -> span.overlap(spanAt(around, dx, dz)));
        }
        return shared;
    }

    /**
     * The height a cell's own core spans, its margin kept under and over it
     * where its column of goo ends: a cell lying on a full cell of its goo is
     * the same column, so it keeps no margin under its core and its column
     * stands taller than a block, and a full cell under a cell of its goo
     * keeps under its top only what the upper cell's layers leave short of
     * the margin.
     */
    private static Optional<Span> spanAt(LayersAround around, int dx, int dz) {
        int layers = around.layersAt(dx, 0, dz);
        boolean onItsGoo = around.layersAt(dx, LOW_SIDE, dz) == FULL_LAYERS;
        int above = layers == FULL_LAYERS ? around.layersAt(dx, 1, dz) : 0;
        int columnLayers = layers + above + (onItsGoo ? FULL_LAYERS : 0);
        int margin = marginOf(columnLayers);
        Span span = new Span(onItsGoo ? 0 : margin,
                layers * UNITS_PER_LAYER - Math.max(0, margin - above * UNITS_PER_LAYER));
        return span.holdsALayer() ? Optional.of(span) : Optional.empty();
    }

    /**
     * The margin a column keeps above and below: 1/8 of its height, never more than 2 px.
     * residual-always-draws-a-core-inside-a-one-eighth-margin
     */
    private static int marginOf(int columnLayers) {
        return Math.min(MARGIN, columnLayers * UNITS_PER_LAYER / MARGIN_PARTS);
    }

    private static int bandStart(int offset) {
        return offset < 0 ? 0 : offset == 0 ? MARGIN : WHOLE - MARGIN;
    }

    private static int bandEnd(int offset) {
        return offset < 0 ? MARGIN : offset == 0 ? WHOLE - MARGIN : WHOLE;
    }

    /**
     * A height a core spans, in 120ths of a block.
     *
     * @param floor its bottom
     * @param top   its top
     */
    private record Span(int floor, int top) {

        boolean holdsALayer() {
            return top > floor;
        }

        Optional<Span> overlap(Optional<Span> other) {
            return other.map(span -> new Span(Math.max(floor, span.floor()), Math.min(top, span.top())))
                    .filter(Span::holdsALayer);
        }
    }
}
