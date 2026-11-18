package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A Dowsing cell as a lens onto ore: the ore it may show, an ore Glimmer or
 * Magnet detects, a block tagged goo:gem_ores or goo:metal_ores, any part of
 * which lies behind the cell's goo from the eye, within its patch's depth
 * in blocks past the goo: the layers of the thickest cell in the connected
 * patch it belongs to. A one layer patch reaches the block behind it, and a
 * patch holding a whole block of fifteen layers fifteen blocks deep. The lens
 * answers candidates whole, so turning the camera pops no ore in or out; the
 * cut at the goo's edge on screen is the draw's, through the window mask.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * dowsing-shows-ore-one-block-deep-per-layer
 * dowsing-goo-is-a-window-onto-the-ore-behind-it
 */
public final class DowsingLens {

    /** The gem ores Glimmer detects. */
    static final TagKey<Block> GEM_ORES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Goo.MODID, "gem_ores"));
    /** The metal ores Magnet detects. */
    static final TagKey<Block> METAL_ORES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Goo.MODID, "metal_ores"));
    /** The nearest depth in front of the eye a corner may stand at and still project onto the view plane. */
    private static final double IN_FRONT = 1e-3;
    /** How steeply up or down the look may point before the view plane's sideways axis leans on east, not up. */
    private static final double STEEP_LOOK = 0.9;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** The most cells one patch walk reads in a tick, a patch of 32 by 32 cells. */
    private static final int PATCH_WALK_LIMIT = 1024;

    private DowsingLens() {
    }

    /**
     * One Dowsing cell's goo and its patch's depth.
     *
     * @param goo    the cell's goo, in world coordinates
     * @param layers the layers of the thickest cell in its patch, its reach in blocks past the goo
     */
    public record Lens(AABB goo, int layers) {
    }

    /**
     * Each cell's patch depth: the layers of the thickest Dowsing cell in
     * the connected patch it belongs to, two cells joining where they share
     * a face. The walk follows each patch out past the given cells, so the
     * depth holds steady as the eye moves along a patch larger than the lens
     * reach, and stops after a fixed count of cells per patch, so a vast
     * patch costs a bounded amount each tick.
     * dowsing-patch-shows-ore-as-deep-as-its-thickest-cell
     * dowsing-shows-ore-one-block-deep-per-layer
     *
     * @param cells    the Dowsing cells the lens reach gathered
     * @param layersAt the layers of the Dowsing cell at a position, 0 where none stands
     * @return each given cell's patch depth
     */
    public static Map<BlockPos, Integer> patchDepths(Set<BlockPos> cells, ToIntFunction<BlockPos> layersAt) {
        Map<BlockPos, Integer> depths = new HashMap<>();
        for (BlockPos cell : cells) {
            if (!depths.containsKey(cell)) {
                walkPatch(cell, layersAt, cells, depths);
            }
        }
        return depths;
    }

    // Walks one patch from a cell across shared faces, then gives every gathered cell it reached the patch's depth.
    private static void walkPatch(BlockPos start, ToIntFunction<BlockPos> layersAt, Set<BlockPos> cells,
                                  Map<BlockPos, Integer> depths) {
        Set<BlockPos> reached = new HashSet<>(List.of(start));
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>(List.of(start));
        int deepest = layersAt.applyAsInt(start);
        while (!frontier.isEmpty() && reached.size() < PATCH_WALK_LIMIT) {
            BlockPos at = frontier.poll();
            for (Direction face : Direction.values()) {
                BlockPos next = at.relative(face);
                int layers = layersAt.applyAsInt(next);
                if (layers > 0 && reached.add(next)) {
                    deepest = Math.max(deepest, layers);
                    frontier.add(next);
                }
            }
        }
        int depth = deepest;
        reached.stream().filter(cells::contains).forEach(cell -> depths.put(cell, depth));
        depths.putIfAbsent(start, depth);
    }

    /**
     * The ore each layer count's cells may show, so the draw masks each
     * group by its own goo and a thin cell never shows a thick cell's deeper
     * ore through its own pixels.
     * dowsing-shows-ore-one-block-deep-per-layer
     *
     * @param eye    where the eye looks from
     * @param lenses the Dowsing cells
     * @param blocks the block standing at each position
     * @return the ore candidates by the layer count of the cells showing them
     */
    public static Map<Integer, Set<BlockPos>> oreShownByLayers(Vec3 eye, List<Lens> lenses,
                                                               Function<BlockPos, BlockState> blocks) {
        Map<Integer, Set<BlockPos>> grouped = new TreeMap<>();
        for (Lens lens : lenses) {
            grouped.computeIfAbsent(lens.layers(), layers -> new HashSet<>())
                    .addAll(oreShown(eye, lens.goo(), lens.layers(), blocks));
        }
        return grouped;
    }

    /**
     * The ore one Dowsing cell may show the eye.
     *
     * @param eye    where the eye looks from
     * @param goo    the cell's goo, in world coordinates
     * @param layers the layers the cell holds, its reach in blocks past the goo
     * @param blocks the block standing at each position
     * @return the ore blocks any part of which lies behind the goo within its reach
     */
    public static Set<BlockPos> oreShown(Vec3 eye, AABB goo, int layers, Function<BlockPos, BlockState> blocks) {
        Set<BlockPos> shown = new HashSet<>();
        AABB reach = goo.inflate(layers);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(reach.minX), Mth.floor(reach.minY),
                Mth.floor(reach.minZ), Mth.ceil(reach.maxX) - 1, Mth.ceil(reach.maxY) - 1,
                Mth.ceil(reach.maxZ) - 1)) {
            AABB block = new AABB(pos);
            if (!block.intersects(goo) && distanceBetween(goo, block) < layers
                    && behindFromTheEye(eye, goo, block) && isOre(blocks.apply(pos))) {
                shown.add(pos.immutable());
            }
        }
        return shown;
    }

    /**
     * The shortest distance between two boxes, nothing where they touch.
     *
     * @param first  one box
     * @param second the other
     * @return the distance in blocks
     */
    static double distanceBetween(AABB first, AABB second) {
        double dx = Math.max(0, Math.max(first.minX - second.maxX, second.minX - first.maxX));
        double dy = Math.max(0, Math.max(first.minY - second.maxY, second.minY - first.maxY));
        double dz = Math.max(0, Math.max(first.minZ - second.maxZ, second.minZ - first.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Whether some part of a block may lie behind the goo from the eye: the
     * two boxes' outlines, seen from the eye on a plane square to the look at
     * the goo, overlap, and the block reaches farther from the eye than the
     * goo's nearest corner. The test keeps every block truly behind the goo
     * and a few beside its outline, which the window mask cuts away; a goo
     * or block reaching the eye's own plane is kept, since its outline
     * cannot be read there.
     *
     * @param eye   where the eye looks from
     * @param goo   the goo
     * @param block the block
     * @return true where the block may show through the goo
     */
    static boolean behindFromTheEye(Vec3 eye, AABB goo, AABB block) {
        Vec3 look = goo.getCenter().subtract(eye).normalize();
        Vec3 across = Math.abs(look.y) < STEEP_LOOK ? look.cross(UP).normalize() : look.cross(EAST).normalize();
        Vec3 up = across.cross(look);
        Outline gooOutline = Outline.of(eye, goo, look, across, up);
        Outline blockOutline = Outline.of(eye, block, look, across, up);
        if (gooOutline == null || blockOutline == null) {
            return true;
        }
        return gooOutline.overlaps(blockOutline) && blockOutline.farthest() > gooOutline.nearest();
    }

    /**
     * @param state a block
     * @return true for an ore Glimmer or Magnet detects
     */
    static boolean isOre(BlockState state) {
        return state.is(GEM_ORES) || state.is(METAL_ORES);
    }

    /**
     * A box's outline seen from the eye: the bounds of its corners projected
     * onto the plane square to the look, and its nearest and farthest depth.
     */
    private record Outline(double minAcross, double maxAcross, double minUp, double maxUp, double nearest,
                           double farthest) {

        /** Nothing seen yet: every bound stands open to the first corner. */
        static final Outline EMPTY = new Outline(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY);

        /**
         * The outline of a box seen from the eye.
         *
         * @param eye    where the eye looks from
         * @param box    the box
         * @param look   the look, a unit vector
         * @param across the view plane's sideways axis
         * @param up     the view plane's upward axis
         * @return the outline, null where a corner stands at or behind the eye's plane
         */
        static Outline of(Vec3 eye, AABB box, Vec3 look, Vec3 across, Vec3 up) {
            Outline outline = EMPTY;
            for (Vec3 corner : cornersOf(box)) {
                Vec3 toCorner = corner.subtract(eye);
                double depth = toCorner.dot(look);
                if (depth < IN_FRONT) {
                    return null;
                }
                outline = outline.reaching(toCorner.dot(across) / depth, toCorner.dot(up) / depth, depth);
            }
            return outline;
        }

        /**
         * This outline grown to take in one more projected corner.
         *
         * @param sideways the corner along the sideways axis
         * @param upward   the corner along the upward axis
         * @param depth    the corner's depth along the look
         * @return the grown outline
         */
        Outline reaching(double sideways, double upward, double depth) {
            return new Outline(Math.min(minAcross, sideways), Math.max(maxAcross, sideways),
                    Math.min(minUp, upward), Math.max(maxUp, upward), Math.min(nearest, depth),
                    Math.max(farthest, depth));
        }

        private static List<Vec3> cornersOf(AABB box) {
            return List.of(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                    new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                    new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                    new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ));
        }

        boolean overlaps(Outline other) {
            return minAcross < other.maxAcross && other.minAcross < maxAcross
                    && minUp < other.maxUp && other.minUp < maxUp;
        }
    }
}
