package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The goo label craft's match and assemble arithmetic over a crafting grid read
 * as cells, so it runs without a live grid.
 */
public final class GooLabelCraft {

    /** The volume one label costs. */
    public static final int LABEL_COST = 1;

    private GooLabelCraft() {
    }

    /** What a grid cell holds, as far as the label craft reads it. */
    public enum Kind { EMPTY, PAPER, GOO, OTHER }

    /**
     * One grid cell.
     *
     * @param kind   what the cell holds
     * @param type   the goo type, for a goo cell
     * @param volume the goo volume in mB, for a goo cell
     */
    public record Cell(Kind kind, @Nullable ResourceKey<GooTypeDefinition> type, int volume) {
        public static final Cell EMPTY = new Cell(Kind.EMPTY, null, 0);
        public static final Cell PAPER = new Cell(Kind.PAPER, null, 0);
        public static final Cell OTHER = new Cell(Kind.OTHER, null, 0);

        /**
         * @param type   the goo type
         * @param volume the goo volume in mB
         * @return a goo cell
         */
        public static Cell goo(@Nullable ResourceKey<GooTypeDefinition> type, int volume) {
            return new Cell(Kind.GOO, type, volume);
        }
    }

    /**
     * A matched craft.
     *
     * @param labelType       the type the crafted label carries
     * @param gooCell         the grid index of the goo cell
     * @param remainingVolume the goo volume left in that cell; 0 leaves the cell empty
     */
    public record Craft(ResourceKey<GooTypeDefinition> labelType, int gooCell, int remainingVolume) {
    }

    /**
     * Matches one paper beside one goo in any shape.
     *
     * @param cells the grid, one cell per slot
     * @return the craft, or empty where the grid is not one paper beside one goo holding at least 1 mB
     */
    public static Optional<Craft> match(List<Cell> cells) {
        List<Integer> gooCells = cellsHolding(cells, Kind.GOO);
        if (!cellsHolding(cells, Kind.OTHER).isEmpty() || cellsHolding(cells, Kind.PAPER).size() != 1
                || gooCells.size() != 1) {
            return Optional.empty();
        }
        int gooCell = gooCells.getFirst();
        return craftFrom(cells.get(gooCell), gooCell);
    }

    private static List<Integer> cellsHolding(List<Cell> cells, Kind kind) {
        return IntStream.range(0, cells.size()).filter(i -> cells.get(i).kind() == kind).boxed().toList();
    }

    private static Optional<Craft> craftFrom(Cell goo, int gooCell) {
        ResourceKey<GooTypeDefinition> type = goo.type();
        if (type == null || goo.volume() < LABEL_COST) {
            return Optional.empty();
        }
        return Optional.of(new Craft(type, gooCell, goo.volume() - LABEL_COST));
    }
}
