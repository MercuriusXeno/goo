package com.mercuriusxeno.goo.client.model;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Sweeps seeded random piles of firm goo on a solid floor, each column
 * stacked as full blocks under a partial top, for a pile that draws closed:
 * the shells of two neighbouring blocks meet along their shared edge with no
 * gap and no overlap, every shell quad lies on the pile's outer surface, and
 * every core quad lies inside its block's shell by the core's margin.
 * Distances are in model pixels, 16 to a block, from the pile's origin.
 * full-goo-block-under-goo-is-a-whole-cube
 * diagnose-and-correct-bad-quad-placement-beside-a-tall-goo-stack
 */
class TallPileClosedTest {

    private static final int FULL = 15;
    private static final float WHOLE = 16f;
    private static final float PIXELS_PER_LAYER = WHOLE / FULL;
    private static final int SIDE = 3;
    private static final int PILES = 500;
    private static final long SEED = 11L;
    private static final int TALLEST = 3 * FULL;
    private static final float EPSILON = 1e-3f;
    /** How far a shell quad's middle is nudged off it either way to read what stands each side, in model pixels. */
    private static final float NUDGE = 1e-2f;
    /** The core's margin in from each side its shell walls, in model pixels. */
    private static final float SIDE_MARGIN = 2f;
    private static final float CORE_TOP = 7f / 8;
    /** Where along a shared edge the shells are compared, in model pixels: inside each patch the shell splits into. */
    private static final float[] ALONG = {1f, 3f, 8f, 13f, 15f};
    private static final int EXAMPLES = 6;
    /** The sprite records no call, since the sweep bakes millions of vertices against it. */
    private static final BakedQuad.MaterialInfo MATERIAL = new BakedQuad.MaterialInfo(
            mock(TextureAtlasSprite.class, withSettings().stubOnly()), ChunkSectionLayer.TRANSLUCENT, null, null, null,
            SlimeCoreModel.SHELL_TINT, null, 0, false);

    /**
     * Along every edge two goo blocks of one level share, the higher of
     * the two tops meets the lower through exactly one wall, from either
     * block, standing from the lower top to the higher, and none where the
     * tops meet.
     */
    @Test
    void neighbouringBlocksShellsMeetAcrossRandomTallPiles() {
        Random random = new Random(SEED);
        Map<Integer, Integer> pairs = new TreeMap<>();
        Map<Integer, Integer> missing = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        for (int trial = 0; trial < PILES; trial++) {
            Pile pile = Pile.laid(random);
            for (Block block : pile.blocks()) {
                for (Direction edge : List.of(Direction.EAST, Direction.SOUTH)) {
                    Block beside = pile.blockAt(block.x() + edge.getStepX(), block.y(), block.z() + edge.getStepZ());
                    if (beside == null) {
                        continue;
                    }
                    int apart = Math.abs(pile.topLevelOf(block) - pile.topLevelOf(beside));
                    pairs.merge(apart, 1, Integer::sum);
                    String miss = missAlong(block, beside, edge);
                    if (miss != null) {
                        missing.merge(apart, 1, Integer::sum);
                        if (examples.size() < EXAMPLES) {
                            examples.add("pile " + trial + " " + pile.columnsOf(block, beside) + ": " + miss);
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "pairs checked by top levels apart " + pairs + ", missing pairs by levels apart "
                + missing + "; e.g. " + examples);
    }

    /**
     * Every shell quad has the pile's goo on its one side and none on its
     * other, and every core quad stands inside its block: under 7/8 of the
     * top its block draws, 2 px in from each wall its block's shell draws,
     * on no face its block shares, and with each side 2 px in from a face
     * of its block.
     */
    @Test
    void noQuadStandsOffThePileOrInsideIt() {
        Random random = new Random(SEED);
        Map<String, Integer> faults = new TreeMap<>();
        Map<String, String> examples = new TreeMap<>();
        int quads = 0;
        for (int trial = 0; trial < PILES; trial++) {
            Pile pile = Pile.laid(random);
            for (Block block : pile.blocks()) {
                for (BakedQuad quad : block.shell()) {
                    quads++;
                    tally(faults, examples, "shell", offTheSurface(pile, quad, block), trial, block);
                }
                for (BakedQuad quad : block.core()) {
                    quads++;
                    tally(faults, examples, "core", outsideItsShell(pile, block, quad), trial, block);
                }
            }
        }
        assertTrue(faults.isEmpty(), "of " + quads + " quads, those standing off the pile or inside it by fault "
                + faults + "; e.g. " + examples.values());
    }

    /**
     * Counts a fault by its kind, keeping the first of each kind as its example.
     *
     * @param fault the fault as kind and detail, or null for none
     */
    private static void tally(Map<String, Integer> faults, Map<String, String> examples, String part,
                              Fault fault, int trial, Block block) {
        if (fault == null) {
            return;
        }
        String kind = part + " " + fault.kind();
        faults.merge(kind, 1, Integer::sum);
        examples.putIfAbsent(kind, kind + " in pile " + trial + " block " + block + ": " + fault.detail());
    }

    /**
     * A quad standing wrong.
     *
     * @param kind   what is wrong, the same words for every quad wrong that way
     * @param detail where the quad stands
     */
    private record Fault(String kind, String detail) {
    }

    /**
     * Where two neighbours' shells fail to meet along the edge they share.
     *
     * @param near   the block on the edge's low side
     * @param far    the block across it
     * @param toward the edge, from the near block: EAST or SOUTH
     * @return the first miss, or null where they meet everywhere sampled
     */
    private static String missAlong(Block near, Block far, Direction toward) {
        for (float along : ALONG) {
            float nearTop = near.topLine(toward, along);
            float farTop = far.topLine(toward.getOpposite(), along);
            float low = Math.min(nearTop, farTop);
            float high = Math.max(nearTop, farTop);
            List<float[]> walls = new ArrayList<>(near.wallsAt(toward, along));
            walls.addAll(far.wallsAt(toward.getOpposite(), along));
            float sum = 0;
            boolean outside = false;
            for (float[] wall : walls) {
                sum += wall[1] - wall[0];
                outside |= wall[0] < low - EPSILON || wall[1] > high + EPSILON;
            }
            if (outside || Math.abs(sum - (high - low)) > EPSILON || Math.abs(unionOf(walls) - (high - low)) > EPSILON) {
                return toward + " edge at " + along + ": tops " + nearTop + " | " + farTop + ", walls "
                        + walls.stream().map(wall -> wall[0] + ".." + wall[1]).toList();
            }
        }
        return null;
    }

    private static float unionOf(List<float[]> walls) {
        List<float[]> sorted = new ArrayList<>(walls);
        sorted.sort((one, other) -> Float.compare(one[0], other[0]));
        float union = 0;
        float reached = Float.NEGATIVE_INFINITY;
        for (float[] wall : sorted) {
            float from = Math.max(wall[0], reached);
            if (wall[1] > from) {
                union += wall[1] - from;
                reached = wall[1];
            }
        }
        return union;
    }

    /**
     * @return how a shell quad stands off the pile's outer surface, or null where it lies on it
     */
    private static Fault offTheSurface(Pile pile, BakedQuad quad, Block block) {
        Vector3f middle = new Vector3f();
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            middle.add(block.pixelsOf(quad.position(vertex)));
        }
        middle.div(BakedQuad.VERTEX_COUNT);
        Vector3f normal = normalOf(quad);
        if (normal.lengthSquared() < EPSILON) {
            return null;
        }
        boolean behind = pile.gooAt(new Vector3f(middle).fma(-NUDGE, normal));
        boolean ahead = pile.gooAt(new Vector3f(middle).fma(NUDGE, normal));
        if (behind && !ahead) {
            return null;
        }
        return new Fault(behind ? "inside the goo" : "apart from the goo", quad.direction() + " at " + middle);
    }

    /**
     * @return how a core quad stands outside its block's shell or inside the pile, or null where it stands right
     */
    private static Fault outsideItsShell(Pile pile, Block block, BakedQuad quad) {
        List<Vector3f> corners = new ArrayList<>();
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            corners.add(new Vector3f(quad.position(vertex)).mul(WHOLE));
        }
        for (Vector3f at : corners) {
            if (at.x() < -EPSILON || at.y() < -EPSILON || at.z() < -EPSILON || at.x() > WHOLE + EPSILON
                    || at.y() > WHOLE + EPSILON || at.z() > WHOLE + EPSILON) {
                return new Fault("outside its block", quad.direction() + " vertex " + at);
            }
            if (!block.top().covered() && at.y() > block.topAt(at.x(), at.z()) * CORE_TOP + EPSILON) {
                return new Fault("over 7/8 of its top", quad.direction() + " vertex " + at + " under a top at "
                        + block.topAt(at.x(), at.z()));
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (inFrom(side, at) < SIDE_MARGIN - EPSILON && block.wallCovers(side, at)) {
                    return new Fault("within 2 px of a wall", quad.direction() + " vertex " + at + " by its " + side
                            + " wall");
                }
            }
        }
        for (Direction face : Direction.values()) {
            if (corners.stream().allMatch(at -> inFrom(face, at) < EPSILON)
                    && (face.getAxis().isHorizontal() || pile.drawsTheSameCoreFace(block, face, quad))) {
                return new Fault("on its block's face", quad.direction() + " on the " + face + " face");
            }
        }
        Direction facing = quad.direction();
        if (facing.getAxis().isHorizontal() && corners.stream().anyMatch(at ->
                Math.abs(inFrom(facing, at) - SIDE_MARGIN) > EPSILON)) {
            return new Fault("side inside the pile", facing + " at " + corners);
        }
        return null;
    }

    /** How far in from a face of its block a point stands, in model pixels. */
    private static float inFrom(Direction face, Vector3fc at) {
        return switch (face) {
            case WEST -> at.x();
            case EAST -> WHOLE - at.x();
            case NORTH -> at.z();
            case SOUTH -> WHOLE - at.z();
            case DOWN -> at.y();
            case UP -> WHOLE - at.y();
        };
    }

    /** A quad's unit normal, turned the way it faces. */
    private static Vector3f normalOf(BakedQuad quad) {
        Vector3f one = new Vector3f(quad.position(2)).sub(quad.position(0));
        Vector3f other = new Vector3f(quad.position(3)).sub(quad.position(1));
        Vector3f normal = one.cross(other);
        if (normal.lengthSquared() < 1e-12f) {
            return new Vector3f();
        }
        normal.normalize();
        Vector3f facing = new Vector3f(quad.direction().getUnitVec3f());
        return normal.dot(facing) < 0 ? normal.negate() : normal;
    }

    /**
     * The vertical span a quad in a face's plane covers at one point along that face.
     *
     * @param quad  the quad, in block units
     * @param face  the face whose plane it lies in
     * @param along where along the face, in model pixels
     * @return its low and high, in model pixels, or null where it misses the point
     */
    private static float[] spanAt(BakedQuad quad, Direction face, float along) {
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3f from = new Vector3f(quad.position(vertex)).mul(WHOLE);
            Vector3f to = new Vector3f(quad.position((vertex + 1) % BakedQuad.VERTEX_COUNT)).mul(WHOLE);
            float a = alongOf(face, from);
            float b = alongOf(face, to);
            if (Math.abs(b - a) < EPSILON || along < Math.min(a, b) || along > Math.max(a, b)) {
                continue;
            }
            float y = from.y() + (to.y() - from.y()) * (along - a) / (b - a);
            low = Math.min(low, y);
            high = Math.max(high, y);
        }
        return high - low > EPSILON ? new float[] {low, high} : null;
    }

    private static float alongOf(Direction face, Vector3fc at) {
        return face.getAxis() == Direction.Axis.X ? at.z() : at.x();
    }

    private static boolean inFace(BakedQuad quad, Direction face) {
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            if (inFrom(face, new Vector3f(quad.position(vertex)).mul(WHOLE)) > EPSILON) {
                return false;
            }
        }
        return true;
    }

    /**
     * One goo block of a pile and what it draws.
     *
     * @param x     east of the pile's origin
     * @param y     above the floor
     * @param z     south of the pile's origin
     * @param top   its floor slab
     * @param shell its shell's quads: its top, its rims and the steps of its sides
     * @param core  its core's quads
     * @param faces the shell's quads lying in each face of the block
     */
    private record Block(int x, int y, int z, SlimeTop top, List<BakedQuad> shell, List<BakedQuad> core,
                         Map<Direction, List<BakedQuad>> faces) {

        static Block of(SameGooCells cells, int x, int y, int z) {
            LayersAround around = cells.shiftedBy(x, y, z);
            SlimeTop top = SlimeTop.of(around);
            List<BakedQuad> shell = new ArrayList<>(SlimeTopQuads.topOf(top, MATERIAL));
            SlimeTopQuads.rimsOf(top, MATERIAL).values().forEach(shell::addAll);
            shell.addAll(SlimeTopQuads.joinsOf(top, MATERIAL));
            shell.addAll(SlimeTopQuads.stepsOf(top, MATERIAL));
            Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
            for (Direction face : Direction.Plane.HORIZONTAL) {
                faces.put(face, shell.stream().filter(quad -> inFace(quad, face)).toList());
            }
            return new Block(x, y, z, top, shell, SlimeCoreQuads.quadsOf(around, MATERIAL), faces);
        }

        /** Where a point of this block stands from the pile's origin, in model pixels. */
        Vector3f pixelsOf(Vector3fc blockUnits) {
            return new Vector3f(blockUnits).add(x, y, z).mul(WHOLE);
        }

        /** The top this block's goo stands at over a point, in model pixels, its whole block where goo covers it. */
        float topAt(float first, float second) {
            if (top.covered()) {
                return WHOLE;
            }
            return Math.clamp((float) top.heightAtPixel(first, second) * PIXELS_PER_LAYER, 0, WHOLE);
        }

        /**
         * The line this block's shell top draws along one of its edges, read
         * from the quads its top draws, its block's top where goo covers it.
         *
         * @param edge  the edge
         * @param along where along it, in model pixels
         * @return the height there, in model pixels over the block's floor
         */
        float topLine(Direction edge, float along) {
            if (top.covered()) {
                return WHOLE;
            }
            float line = Float.NaN;
            for (BakedQuad quad : shell) {
                if (quad.direction() != Direction.UP) {
                    continue;
                }
                List<Vector3f> onEdge = new ArrayList<>();
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    Vector3f at = new Vector3f(quad.position(vertex)).mul(WHOLE);
                    if (inFrom(edge, at) < EPSILON) {
                        onEdge.add(at);
                    }
                }
                if (onEdge.size() == 2) {
                    float a = alongOf(edge, onEdge.get(0));
                    float b = alongOf(edge, onEdge.get(1));
                    if (along >= Math.min(a, b) && along <= Math.max(a, b)) {
                        line = onEdge.get(0).y() + (onEdge.get(1).y() - onEdge.get(0).y()) * (along - a) / (b - a);
                    }
                }
            }
            return line;
        }

        /** The spans of every shell quad this block draws in one face's plane at a point along it. */
        List<float[]> wallsAt(Direction face, float along) {
            List<float[]> walls = new ArrayList<>();
            for (BakedQuad quad : faces.get(face)) {
                float[] span = spanAt(quad, face, along);
                if (span != null) {
                    walls.add(span);
                }
            }
            return walls;
        }

        /** Whether a shell wall this block draws on a face stands beside a point of the block. */
        boolean wallCovers(Direction face, Vector3fc at) {
            for (float[] wall : wallsAt(face, alongOf(face, at))) {
                if (at.y() > wall[0] + EPSILON && at.y() < wall[1] - EPSILON) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return x + "," + y + "," + z;
        }
    }

    /**
     * A seeded pile: a 3 by 3 field of columns on a solid floor, each 0 to
     * 45 layers stacked as full blocks under a partial top, as firm goo lays them.
     *
     * @param columns each column's layers
     * @param cells   the goo's cells and the floor
     * @param blocks  every goo block's drawing
     */
    private record Pile(int[][] columns, SameGooCells cells, List<Block> blocks) {

        static Pile laid(Random random) {
            int[][] columns = new int[SIDE][SIDE];
            SameGooCells cells = new SameGooCells();
            for (int x = -1; x <= SIDE; x++) {
                for (int z = -1; z <= SIDE; z++) {
                    cells.solid(x, -1, z);
                }
            }
            for (int x = 0; x < SIDE; x++) {
                for (int z = 0; z < SIDE; z++) {
                    columns[x][z] = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(TALLEST);
                    for (int y = 0, left = columns[x][z]; left > 0; y++, left -= FULL) {
                        cells.at(x, y, z, Math.min(FULL, left));
                    }
                }
            }
            List<Block> blocks = new ArrayList<>();
            for (int x = 0; x < SIDE; x++) {
                for (int z = 0; z < SIDE; z++) {
                    for (int y = 0; cells.layersAt(x, y, z) > 0; y++) {
                        blocks.add(Block.of(cells, x, y, z));
                    }
                }
            }
            return new Pile(columns, cells, blocks);
        }

        Block blockAt(int x, int y, int z) {
            for (Block block : blocks) {
                if (block.x() == x && block.y() == y && block.z() == z) {
                    return block;
                }
            }
            return null;
        }

        int topLevelOf(Block block) {
            return (columns[block.x()][block.z()] - 1) / FULL;
        }

        String columnsOf(Block one, Block other) {
            return "level " + one.y() + " blocks " + one + " | " + other + " columns " + columns[one.x()][one.z()]
                    + " | " + columns[other.x()][other.z()] + " layers";
        }

        /**
         * Whether the block across a top or floor face of a block draws a
         * core face of its own where a core quad of this block lies on that
         * face, so the two cores wall each other off there.
         */
        boolean drawsTheSameCoreFace(Block block, Direction face, BakedQuad quad) {
            Block across = blockAt(block.x(), block.y() + face.getStepY(), block.z());
            if (across == null) {
                return false;
            }
            Vector3f middle = new Vector3f();
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                middle.add(quad.position(vertex));
            }
            middle.div(BakedQuad.VERTEX_COUNT).mul(WHOLE);
            for (BakedQuad theirs : across.core()) {
                Vector3f low = new Vector3f(Float.MAX_VALUE);
                Vector3f high = new Vector3f(-Float.MAX_VALUE);
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    Vector3f at = new Vector3f(theirs.position(vertex)).mul(WHOLE);
                    low.min(at);
                    high.max(at);
                }
                boolean onTheirFace = high.y() - low.y() < EPSILON && inFrom(face.getOpposite(), low) < EPSILON;
                if (onTheirFace && middle.x() > low.x() && middle.x() < high.x() && middle.z() > low.z()
                        && middle.z() < high.z()) {
                    return true;
                }
            }
            return false;
        }

        /** Whether a point, in model pixels from the pile's origin, stands inside the pile's goo. */
        boolean gooAt(Vector3fc at) {
            int x = Math.floorDiv((int) Math.floor(at.x()), (int) WHOLE);
            int y = Math.floorDiv((int) Math.floor(at.y()), (int) WHOLE);
            int z = Math.floorDiv((int) Math.floor(at.z()), (int) WHOLE);
            Block block = blockAt(x, y, z);
            if (block == null) {
                return false;
            }
            return at.y() - y * WHOLE < block.topAt(at.x() - x * WHOLE, at.z() - z * WHOLE);
        }
    }
}
