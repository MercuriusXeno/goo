package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How a drained block peels into chunks that fly to the hand: the block's
 * outline cut into a grid of chunks, each wearing its own cell of the block's
 * sprite and standing in its place, so the block still stands whole; the
 * chunks leave corner by corner from the corner nearest the hand, their turns
 * spread over the drain from the square landing to the block drained, so the
 * block is seen coming apart and the last chunk leaves before the drain ends;
 * each then flies straight from its place toward the glove at the stream's
 * base pace, following the glove as it moves, and is done at the glove.
 * decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand
 * decision unmake-drink-is-a-stream-of-break-particles
 */
public final class DrinkRide {

    /** Chunks along each edge of the block, and cells along each edge of its sprite. */
    public static final int CHUNKS_ACROSS = 4;
    private static final double HALF = 0.5;

    private DrinkRide() {
    }

    /**
     * One chunk of a peeling block.
     *
     * @param place   its middle while it stands in the block
     * @param width   its edge across, in blocks
     * @param height  its edge up, in blocks
     * @param across  the column of the block's sprite it wears, 0 to {@link #CHUNKS_ACROSS} less one
     * @param down    the row of the block's sprite it wears, 0 to {@link #CHUNKS_ACROSS} less one
     * @param release the game time its turn comes and it leaves the block
     */
    public record Chunk(Vec3 place, double width, double height, int across, int down, double release) {
    }

    /**
     * @param block   the block peeling
     * @param outline the block's outline, within its cell
     * @param glove   the drinker's glove as the square lands
     * @return the block's chunks, filling its outline, their turns ordered by their distance from the outline's
     *         corner nearest the glove and spread over the drain, every one before the block is drained
     */
    public static List<Chunk> chunksOf(DrinkTree.Block block, AABB outline, Vec3 glove) {
        BlockPos pos = block.pos();
        AABB box = outline.move(pos);
        Vec3 corner = nearestCorner(box, glove);
        double width = box.getXsize() / CHUNKS_ACROSS;
        double height = box.getYsize() / CHUNKS_ACROSS;
        double depth = box.getZsize() / CHUNKS_ACROSS;
        List<Vec3> places = placesIn(box);
        places.sort(Comparator.comparingDouble(place -> place.distanceToSqr(corner)));
        double drain = Math.max(1, block.end() - block.start());
        List<Chunk> chunks = new ArrayList<>(places.size());
        for (int turn = 0; turn < places.size(); turn++) {
            Vec3 place = places.get(turn);
            int column = cellOf(place.x - box.minX, width) + cellOf(place.z - box.minZ, depth);
            int row = CHUNKS_ACROSS - 1 - cellOf(place.y - box.minY, height);
            chunks.add(new Chunk(place, width, height, column % CHUNKS_ACROSS, row,
                    block.start() + drain * turn / places.size()));
        }
        return chunks;
    }

    /**
     * @param box an outline in the world
     * @return the middle of every chunk of the grid cutting it, {@link #CHUNKS_ACROSS} along each edge
     */
    private static List<Vec3> placesIn(AABB box) {
        double width = box.getXsize() / CHUNKS_ACROSS;
        double height = box.getYsize() / CHUNKS_ACROSS;
        double depth = box.getZsize() / CHUNKS_ACROSS;
        List<Vec3> places = new ArrayList<>();
        for (int x = 0; x < CHUNKS_ACROSS; x++) {
            for (int y = 0; y < CHUNKS_ACROSS; y++) {
                for (int z = 0; z < CHUNKS_ACROSS; z++) {
                    places.add(new Vec3(box.minX + width * (x + HALF), box.minY + height * (y + HALF),
                            box.minZ + depth * (z + HALF)));
                }
            }
        }
        return places;
    }

    /**
     * @param offset a chunk's middle, from the outline's least edge along one axis
     * @param edge   a chunk's edge along that axis
     * @return the chunk's place in the grid along that axis
     */
    private static int cellOf(double offset, double edge) {
        return edge <= 0 ? 0 : Math.clamp((int) Math.floor(offset / edge), 0, CHUNKS_ACROSS - 1);
    }

    /**
     * @param box   an outline in the world
     * @param point a point
     * @return the outline's corner nearest the point
     */
    private static Vec3 nearestCorner(AABB box, Vec3 point) {
        Vec3 middle = box.getCenter();
        return new Vec3(point.x < middle.x ? box.minX : box.maxX, point.y < middle.y ? box.minY : box.maxY,
                point.z < middle.z ? box.minZ : box.maxZ);
    }

    /**
     * @param chunks a block's chunks
     * @param now    the game time, with the partial tick
     * @return how many of them still stand in the block, a chunk standing until the moment after its turn
     */
    public static int standingAt(List<Chunk> chunks, double now) {
        int standing = 0;
        for (Chunk chunk : chunks) {
            if (now <= chunk.release()) {
                standing++;
            }
        }
        return standing;
    }

    /**
     * @param release the game time its turn comes
     * @param now     the game time, with the partial tick
     * @return blocks it has flown from its place: none before its turn, then as far as the base pace carries it in
     *         the time since
     */
    public static double distanceOf(double release, double now) {
        return now < release ? 0 : DrinkStream.FLOW * (now - release);
    }

    /**
     * @param from     where its flight leaves from
     * @param glove    the drinker's glove
     * @param distance blocks it has flown
     * @return whether it has reached the glove
     */
    public static boolean done(Vec3 from, Vec3 glove, double distance) {
        return distance >= from.distanceTo(glove);
    }

    /**
     * @param chunk the chunk
     * @param glove the drinker's glove now
     * @param now   the game time, with the partial tick
     * @return where it stands: in its place before its turn, then on the line from there to the glove; or null once
     *         it has reached the glove
     */
    public static @Nullable Vec3 pointOf(Chunk chunk, Vec3 glove, double now) {
        Vec3 from = chunk.place();
        double distance = distanceOf(chunk.release(), now);
        if (done(from, glove, distance)) {
            return null;
        }
        return from.lerp(glove, distance / from.distanceTo(glove));
    }
}
