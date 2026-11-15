package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A box of a volume cell's goo slime core, in fifteenths of a block, the
 * unit a cell's layers count in, and the rule turning a cell and the cells
 * of its goo around it into its core: the core keeps a padding of goo
 * between itself and wherever the goo ends, the ground, the surface, an open
 * side or a wall, so a lone cell shows no core until its layers outgrow two
 * paddings and each layer after adds one to the core's height; and the core
 * runs through its padding to the face it shares with a cell of its goo
 * whose core stands across it, over the height both cores span, since only
 * padding parts them there.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * slime-core-keeps-a-two-layer-gap-and-grows-with-fill
 * slime-cores-connect-where-only-padding-parts-them
 *
 * @param minX the west bound
 * @param minY the floor
 * @param minZ the north bound
 * @param maxX the east bound
 * @param maxY the top
 * @param maxZ the south bound
 */
public record SlimeCoreBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** The layers of goo standing between the core and wherever the goo ends: the tuning a thicker shell turns. */
    public static final int PADDING_LAYERS = 2;
    /** A whole block, in fifteenths. */
    static final int WHOLE = VolumeCellBlock.MAX_LAYERS;
    /** The offsets along one axis: the cell's own column and the cells to either side. */
    private static final int[] OFFSETS = {-1, 0, 1};

    /**
     * The core a cell read alone holds.
     *
     * @param layers the layers the cell holds
     * @return the core's box, empty where the padding leaves no room for one
     */
    public static Optional<SlimeCoreBox> of(int layers) {
        return boxesOf(LayersAround.alone(layers)).stream().findFirst();
    }

    /**
     * The boxes a cell's core fills, read from the cells of its goo around
     * it. The cell's plan splits into three bands along each axis, the
     * padding toward each side and the middle; each of the nine columns this
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
                sharedSpan(around, dx, dz).ifPresent(span -> boxes.add(new SlimeCoreBox(
                        bandStart(dx), span.floor(), bandStart(dz), bandEnd(dx), span.top(), bandEnd(dz))));
            }
        }
        return boxes;
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
     * The height a cell's own core spans, its padding kept under and over it.
     */
    private static Optional<Span> spanAt(LayersAround around, int dx, int dz) {
        Span span = new Span(PADDING_LAYERS, around.layersAt(dx, 0, dz) - PADDING_LAYERS);
        return span.holdsALayer() ? Optional.of(span) : Optional.empty();
    }

    private static int bandStart(int offset) {
        return offset < 0 ? 0 : offset == 0 ? PADDING_LAYERS : WHOLE - PADDING_LAYERS;
    }

    private static int bandEnd(int offset) {
        return offset < 0 ? PADDING_LAYERS : offset == 0 ? WHOLE - PADDING_LAYERS : WHOLE;
    }

    /**
     * A height a core spans, in fifteenths.
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
