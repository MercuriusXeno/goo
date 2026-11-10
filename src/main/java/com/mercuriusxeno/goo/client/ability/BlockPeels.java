package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;

/**
 * The blocks the client sees crumbling into chunks of their own faces: a peel
 * is kept from the frame it begins until its last chunk has left.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class BlockPeels {

    /** The client's peels. */
    public static final BlockPeels CLIENT = new BlockPeels();

    private final List<Peel> peels = new ArrayList<>();

    /**
     * How one face of a block looks, worked out once as it begins to peel.
     *
     * @param cells the face texture's cells, by {@link BlockBreakup.FaceCell#across()} times
     *              {@link BlockBreakup#CELLS_ACROSS} plus {@link BlockBreakup.FaceCell#down()}
     * @param tint  the ARGB the face is tinted by, opaque white for none
     */
    public record FaceLook(SpriteCrop[] cells, int tint) {

        /**
         * @param sprite the face texture's window of the atlas
         * @param tint   the ARGB the face is tinted by
         * @return the face's look, its texture cut into its cells
         */
        public static FaceLook of(SpriteCrop sprite, int tint) {
            SpriteCrop[] cells = new SpriteCrop[BlockBreakup.CELLS_ACROSS * BlockBreakup.CELLS_ACROSS];
            for (int across = 0; across < BlockBreakup.CELLS_ACROSS; across++) {
                for (int down = 0; down < BlockBreakup.CELLS_ACROSS; down++) {
                    cells[across * BlockBreakup.CELLS_ACROSS + down] = SpriteCrop.cellOf(sprite.u0(), sprite.u1(),
                            sprite.v0(), sprite.v1(), across, down, BlockBreakup.CELLS_ACROSS);
                }
            }
            return new FaceLook(cells, tint);
        }

        /**
         * @param cell a chunk face's cell
         * @return the texture window it wears
         */
        public SpriteCrop at(BlockBreakup.FaceCell cell) {
            return cells[cell.across() * BlockBreakup.CELLS_ACROSS + cell.down()];
        }
    }

    /**
     * One block peeling.
     *
     * @param pos     the cell it crumbles in
     * @param breakup how it breaks up into chunks
     * @param faces   how each of its faces looks, by {@link Direction#get3DDataValue()}
     * @param until   the game time its last chunk has left
     */
    public record Peel(BlockPos pos, BlockBreakup.Breakup breakup, FaceLook[] faces, double until) {
    }

    /**
     * @param peel a block beginning to peel
     */
    public void begin(Peel peel) {
        peels.add(peel);
    }

    /**
     * @param now the game time, with the partial tick
     * @return the peels still drawn, dropping each whose last chunk has left
     */
    public List<Peel> live(double now) {
        peels.removeIf(peel -> now >= peel.until());
        return peels;
    }
}
