package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blocks the client sees peeling into chunks, and each drink's
 * convergence point their chunks fly to: a peel is kept from the frame its
 * square lands until its last chunk can have reached the point.
 * decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand
 * unmake-converges-near-the-unmade-blocks-into-a-goo-item
 */
public final class DrinkPeels {

    /** The client's peels. */
    public static final DrinkPeels CLIENT = new DrinkPeels();
    /** The low half of a packed light, its block light. */
    private static final int LIGHT_HALF = 0xFFFF;
    /** How far up a packed light holds its sky light. */
    private static final int LIGHT_SHIFT = 16;

    private final List<Peel> peels = new ArrayList<>();
    private final Map<Integer, Vec3> destinations = new HashMap<>();

    /**
     * How one face of a block looks, worked out once as it begins to peel.
     *
     * @param cells the face texture's cells, by {@link DrinkRide.FaceCell#across()} times
     *              {@link DrinkRide#CHUNKS_ACROSS} plus {@link DrinkRide.FaceCell#down()}
     * @param tint  the ARGB the face is tinted by, opaque white for none
     */
    public record FaceLook(SpriteCrop[] cells, int tint) {

        /**
         * @param sprite the face texture's window of the atlas
         * @param tint   the ARGB the face is tinted by
         * @return the face's look, its texture cut into its cells
         */
        public static FaceLook of(SpriteCrop sprite, int tint) {
            SpriteCrop[] cells = new SpriteCrop[DrinkRide.CELLS_ACROSS * DrinkRide.CELLS_ACROSS];
            for (int across = 0; across < DrinkRide.CELLS_ACROSS; across++) {
                for (int down = 0; down < DrinkRide.CELLS_ACROSS; down++) {
                    cells[across * DrinkRide.CELLS_ACROSS + down] = SpriteCrop.cellOf(sprite.u0(), sprite.u1(),
                            sprite.v0(), sprite.v1(), across, down, DrinkRide.CELLS_ACROSS);
                }
            }
            return new FaceLook(cells, tint);
        }

        /**
         * @param cell a chunk face's cell
         * @return the texture window it wears
         */
        public SpriteCrop at(DrinkRide.FaceCell cell) {
            return cells[cell.across() * DrinkRide.CELLS_ACROSS + cell.down()];
        }
    }

    /**
     * One block peeling.
     *
     * @param drinker the drinker whose convergence point its chunks fly to
     * @param pos     where it stood
     * @param breakup how it breaks up into chunks
     * @param faces   how each of its faces looks, by {@link Direction#get3DDataValue()}
     * @param until   the game time its last chunk can have reached the point
     */
    public record Peel(int drinker, BlockPos pos, DrinkRide.Breakup breakup, FaceLook[] faces, double until) {
    }

    /**
     * @param peel a block beginning to peel
     */
    public void begin(Peel peel) {
        peels.add(peel);
    }

    /**
     * @param drinker a drinker
     * @param point   their drink's convergence point this frame
     */
    public void destination(int drinker, Vec3 point) {
        destinations.put(drinker, point);
    }

    /**
     * @param drinker a drinker
     * @return their drink's convergence point as last placed, or null while they drink nothing
     */
    public @Nullable Vec3 destinationOf(int drinker) {
        return destinations.get(drinker);
    }

    /**
     * @param drinkers the drinkers whose drinks still show; every other's point is let go
     */
    public void keepDestinationsOf(Set<Integer> drinkers) {
        destinations.keySet().retainAll(drinkers);
    }

    /**
     * @param one     a packed light, block light in its low half and sky light in its high half
     * @param another another packed light
     * @return the brighter of the two in each half
     */
    public static int brighterOf(int one, int another) {
        int block = Math.max(one & LIGHT_HALF, another & LIGHT_HALF);
        int sky = Math.max(one >>> LIGHT_SHIFT, another >>> LIGHT_SHIFT);
        return block | sky << LIGHT_SHIFT;
    }

    /**
     * @param now the game time, with the partial tick
     * @return the peels still drawn, dropping each whose last chunk can have reached the hand
     */
    public List<Peel> live(double now) {
        peels.removeIf(peel -> now >= peel.until());
        return peels;
    }
}
