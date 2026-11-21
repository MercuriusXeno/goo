package com.mercuriusxeno.goo.client.model;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * The heights a goo block's sloped top stands at, in layers above the
 * block's floor, at the 4 by 4 grid of points 0, 4, 12 and 16 texels in
 * along each axis: grid index 0 the west or north edge, 3 the east or south.
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 */
class SlimeTopTest {

    private static final double EPSILON = 1e-9;
    private static final long PILE_SEED = 23L;
    private static final int PILES = 200;
    /** The sprite records no call, since the reach sweep bakes every quad of thousands of blocks against it. */
    private static final BakedQuad.MaterialInfo MATERIAL = new BakedQuad.MaterialInfo(
            mock(TextureAtlasSprite.class, withSettings().stubOnly()), ChunkSectionLayer.TRANSLUCENT, null, null, null,
            SlimeCoreModel.SHELL_TINT, null, 0, false);
    private static final int WEST = 0;
    private static final int NORTH = 0;
    private static final int EAST = 3;
    private static final int SOUTH = 3;
    private static final int[] MIDDLE = {1, 2};
    /** A wall slab's grid index at its foot. */
    private static final int FOOT = 0;
    /** A wall slab's grid index at its top edge. */
    private static final int HEAD = 3;

    /**
     * A block of 12 beside a block of 6 to its east, on a floor with air
     * elsewhere: each middle stands at its own volume, the edge they share
     * at 9, each edge to air at half its volume, the corners on the shared
     * edge at 4.5 and each outer corner at a quarter of its volume.
     */
    @Test
    void aTopSlopesToTheAverageWithTheBlockBesideIt() {
        SameGooCells cells = new SameGooCells().at(0, 0, 0, 12).at(1, 0, 0, 6);
        SlimeTop twelve = SlimeTop.of(cells);
        SlimeTop six = SlimeTop.of(cells.shiftedBy(1, 0, 0));

        assertMiddle(twelve, 12);
        assertMiddle(six, 6);
        assertEdge(twelve, EAST, 9, "the edge the 12 shares");
        assertEdge(six, WEST, 9, "the edge the 6 shares");
        assertEdge(twelve, WEST, 6, "the 12's edge to air");
        assertEquals(6, twelve.heightAt(1, NORTH), EPSILON, "the 12's north edge to air");
        assertEquals(3, six.heightAt(1, SOUTH), EPSILON, "the 6's south edge to air");
        assertEdge(six, EAST, 3, "the 6's edge to air");
        assertEquals(4.5, twelve.heightAt(EAST, NORTH), EPSILON);
        assertEquals(4.5, twelve.heightAt(EAST, SOUTH), EPSILON);
        assertEquals(4.5, six.heightAt(WEST, NORTH), EPSILON);
        assertEquals(3, twelve.heightAt(WEST, NORTH), EPSILON, "the 12's outer corner");
        assertEquals(3, twelve.heightAt(WEST, SOUTH), EPSILON, "the 12's outer corner");
        assertEquals(1.5, six.heightAt(EAST, NORTH), EPSILON, "the 6's outer corner");
        assertEquals(1.5, six.heightAt(EAST, SOUTH), EPSILON, "the 6's outer corner");
    }

    @Test
    void aWholeBlockAloneSlopesToHalfAtItsEdgesAndAQuarterAtItsCorners() {
        SlimeTop alone = SlimeTop.of(LayersAround.alone(15));

        assertMiddle(alone, 15);
        assertEdge(alone, EAST, 7.5, "an edge");
        assertEdge(alone, WEST, 7.5, "an edge");
        assertEquals(7.5, alone.heightAt(1, NORTH), EPSILON);
        assertEquals(3.75, alone.heightAt(WEST, NORTH), EPSILON);
        assertEquals(3.75, alone.heightAt(EAST, SOUTH), EPSILON);
    }

    /**
     * A block of 8 with solid blocks to its north, west, north-west and
     * north-east and air to its east and south: against a solid block the
     * edge keeps its own volume, and a solid block stays out of a corner's
     * average.
     */
    @Test
    void aSolidBlockLeavesTheEdgeAtTheBlocksOwnShare() {
        SameGooCells cells = new SameGooCells().at(0, 0, 0, 8).solid(0, 0, -1).solid(-1, 0, 0).solid(-1, 0, -1)
                .solid(1, 0, -1);
        SlimeTop top = SlimeTop.of(cells);

        assertEquals(8, top.heightAt(1, NORTH), EPSILON, "the north edge");
        assertEdge(top, WEST, 8, "the west edge");
        assertEquals(8, top.heightAt(WEST, NORTH), EPSILON, "the north-west corner");
        assertEdge(top, EAST, 4, "the east edge");
        assertEquals(4, top.heightAt(1, SOUTH), EPSILON, "the south edge");
        assertEquals(4, top.heightAt(EAST, NORTH), EPSILON, "the north-east corner");
        assertEquals(2, top.heightAt(EAST, SOUTH), EPSILON, "the south-east corner");
    }

    /**
     * In a pillar of 15, 15 and 6 layers standing in a film of 1, each of
     * the two full blocks under goo is a whole cube: every point of its top
     * stands at 15.
     * full-goo-block-under-goo-is-a-whole-cube
     */
    @Test
    void aFullBlockUnderGooIsAWholeCube() {
        SameGooCells pillar = SameGooCells.pillar(true);
        for (int y : new int[] {0, 1}) {
            SlimeTop cube = SlimeTop.of(pillar.shiftedBy(0, y, 0));
            for (int a = WEST; a <= EAST; a++) {
                for (int b = NORTH; b <= SOUTH; b++) {
                    assertEquals(15, cube.heightAt(a, b), EPSILON, "block " + y + " at " + a + ", " + b);
                }
            }
        }
    }

    /**
     * The pillar's top block of 6, with air beside it, slopes to half its
     * fill at its edges and stands nowhere under its own floor.
     * goo-block-top-slopes-to-the-average-with-the-adjoining-block
     * full-goo-block-under-goo-is-a-whole-cube
     */
    @Test
    void aPillarsTopBlockTapersToHalfItsFillBesideAir() {
        SlimeTop top = SlimeTop.of(SameGooCells.pillar(true).shiftedBy(0, 2, 0));

        assertMiddle(top, 6);
        assertEdge(top, WEST, 3, "the west edge");
        assertEdge(top, EAST, 3, "the east edge");
        for (int a : MIDDLE) {
            assertEquals(3, top.heightAt(a, NORTH), EPSILON, "the north edge at " + a);
            assertEquals(3, top.heightAt(a, SOUTH), EPSILON, "the south edge at " + a);
        }
        for (int a = WEST; a <= EAST; a++) {
            for (int b = NORTH; b <= SOUTH; b++) {
                assertTrue(top.heightAt(a, b) >= 0, "the top at " + a + ", " + b + " stands under its floor");
            }
        }
    }

    /**
     * The film block of 1 east of the pillar's foot, a whole cube, rises to
     * 8 along the edge they share, the average of its 1 and the cube's 15.
     * full-goo-block-under-goo-is-a-whole-cube
     */
    @Test
    void aLowBlockRisesToTheAverageWithTheCubeBesideIt() {
        SlimeTop film = SlimeTop.of(SameGooCells.pillar(true).shiftedBy(1, 0, 0));

        assertMiddle(film, 1);
        assertEdge(film, WEST, 8, "the edge it shares with the cube");
    }

    /**
     * Seeded random piles, each a 3 by 3 field of columns of 0 to 45
     * layers on a solid floor: changing any one cell standing two blocks
     * from a block on some axis, emptying it where goo stands and filling it
     * where none does, leaves every quad that block draws as it stood, its
     * shell's and its core's alike, so the client's re-mesh of the blocks
     * within one of a change keeps every block's drawing current.
     * full-goo-block-under-goo-is-a-whole-cube
     */
    @Test
    void aBlocksShapeReadsNothingPastOneBlock() {
        Random random = new Random(PILE_SEED);
        for (int trial = 0; trial < PILES; trial++) {
            int[][] columns = new int[3][3];
            for (int[] row : columns) {
                for (int z = 0; z < row.length; z++) {
                    row[z] = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(45);
                }
            }
            int x = random.nextInt(3);
            int z = random.nextInt(3);
            int y = random.nextInt(Math.max(1, (columns[x][z] + 14) / 15));
            SameGooCells pile = pileOf(columns);
            List<String> before = quadsDrawnBy(pile.shiftedBy(x, y, z));
            for (int[] far : cellsTwoBlocksFrom(x, y, z)) {
                int layers = pile.layersAt(far[0], far[1], far[2]) > 0 ? 0 : 15;
                List<String> after = quadsDrawnBy(pileOf(columns).at(far[0], far[1], far[2], layers)
                        .shiftedBy(x, y, z));
                assertEquals(before, after, "trial " + trial + " block " + x + "," + y + "," + z + " after "
                        + layers + " layers at " + far[0] + "," + far[1] + "," + far[2]);
            }
        }
    }

    /**
     * Every quad a block draws on its floor, its shell's and its core's,
     * each keyed by its direction and its vertex positions, in a fixed order.
     */
    private static List<String> quadsDrawnBy(LayersAround around) {
        SlimeTop top = SlimeTop.of(around);
        List<BakedQuad> quads = new ArrayList<>(SlimeTopQuads.topOf(top, MATERIAL));
        SlimeTopQuads.rimsOf(top, MATERIAL).values().forEach(quads::addAll);
        quads.addAll(SlimeTopQuads.joinsOf(top, MATERIAL));
        quads.addAll(SlimeTopQuads.stepsOf(top, MATERIAL));
        quads.addAll(SlimeCoreQuads.quadsOf(around, MATERIAL));
        List<String> keys = new ArrayList<>();
        for (BakedQuad quad : quads) {
            StringBuilder key = new StringBuilder(quad.direction().toString());
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                key.append(' ').append(quad.position(vertex));
            }
            keys.add(key.toString());
        }
        keys.sort(null);
        return keys;
    }

    /** Every cell standing exactly two blocks from a block on its farthest axis. */
    private static List<int[]> cellsTwoBlocksFrom(int x, int y, int z) {
        List<int[]> cells = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) == 2) {
                        cells.add(new int[] {x + dx, y + dy, z + dz});
                    }
                }
            }
        }
        return cells;
    }

    /** A 3 by 3 field of columns on a solid floor, each stacked as full blocks under a partial top. */
    private static SameGooCells pileOf(int[][] columns) {
        SameGooCells pile = new SameGooCells();
        for (int x = -3; x <= 5; x++) {
            for (int z = -3; z <= 5; z++) {
                pile.solid(x, -1, z);
            }
        }
        for (int x = 0; x < columns.length; x++) {
            for (int z = 0; z < columns[x].length; z++) {
                for (int y = 0, left = columns[x][z]; left > 0; y++, left -= 15) {
                    pile.at(x, y, z, Math.min(15, left));
                }
            }
        }
        return pile;
    }

    /**
     * Two blocks on one north wall with no floor, the upper holding 3 and
     * the lower 9: each slab's middle stands at its share, the edge they
     * share at 6, the upper's top edge at 1.5 and the lower's foot at 4.5.
     * On a wall the grid runs east along index x and up along index z.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     */
    @Test
    void aWallSlabSlopesToTheSlabContinuingOnItsWall() {
        SameGooCells cells = new SameGooCells().at(0, 0, 0, 3).share(0, 0, 0, Direction.NORTH, 3)
                .at(0, -1, 0, 9).share(0, -1, 0, Direction.NORTH, 9);
        SlimeTop upper = SlimeTop.onSurface(cells, Direction.NORTH);
        SlimeTop lower = SlimeTop.onSurface(cells.shiftedBy(0, -1, 0), Direction.NORTH);

        assertMiddle(upper, 3);
        assertMiddle(lower, 9);
        assertEquals(6, upper.heightAt(1, FOOT), EPSILON, "the edge the upper shares");
        assertEquals(6, lower.heightAt(1, HEAD), EPSILON, "the edge the lower shares");
        assertEquals(1.5, upper.heightAt(1, HEAD), EPSILON, "the upper's top edge");
        assertEquals(4.5, lower.heightAt(1, FOOT), EPSILON, "the lower's foot");
    }

    @Test
    void aCeilingSlabAloneSlopesToHalfAtEveryEdge() {
        SlimeTop slab = SlimeTop.onSurface(new SameGooCells().at(0, 0, 0, 6).share(0, 0, 0, Direction.UP, 6),
                Direction.UP);

        assertMiddle(slab, 6);
        assertEdge(slab, WEST, 3, "the west edge");
        assertEdge(slab, EAST, 3, "the east edge");
        assertEquals(3, slab.heightAt(1, NORTH), EPSILON, "the north edge");
        assertEquals(3, slab.heightAt(2, SOUTH), EPSILON, "the south edge");
    }

    /**
     * A block of 8 on a floor in a nook, attached to its north and west
     * walls, of a goo with cohesion 0: its floor slab keeps 6 along both
     * walls and in the corner between them, and each wall slab keeps 1
     * along its foot and along the edge where the two walls meet, so the
     * slabs meet square in the inside corners.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     */
    @Test
    void slabsInANookMeetSquareInTheInsideCorners() {
        SameGooCells nook = new SameGooCells().at(0, 0, 0, 8).share(0, 0, 0, Direction.DOWN, 6)
                .share(0, 0, 0, Direction.NORTH, 1).share(0, 0, 0, Direction.WEST, 1)
                .solid(0, 0, -1).solid(-1, 0, 0).solid(-1, 0, -1).solid(0, -1, 0);
        SlimeTop floor = SlimeTop.of(nook);
        SlimeTop north = SlimeTop.onSurface(nook, Direction.NORTH);
        SlimeTop west = SlimeTop.onSurface(nook, Direction.WEST);

        assertEquals(6, floor.heightAt(1, NORTH), EPSILON, "the floor along the north wall");
        assertEdge(floor, WEST, 6, "the floor along the west wall");
        assertEquals(6, floor.heightAt(WEST, NORTH), EPSILON, "the floor in the corner");
        assertEquals(1, north.heightAt(1, FOOT), EPSILON, "the north slab's foot");
        assertEquals(1, north.heightAt(WEST, 1), EPSILON, "the north slab where the walls meet");
        assertEquals(1, west.heightAt(1, FOOT), EPSILON, "the west slab's foot");
        assertEquals(1, west.heightAt(NORTH, 1), EPSILON, "the west slab where the walls meet");
    }

    private static void assertMiddle(SlimeTop top, double expected) {
        for (int x : MIDDLE) {
            for (int z : MIDDLE) {
                assertEquals(expected, top.heightAt(x, z), EPSILON, "the middle at " + x + ", " + z);
            }
        }
    }

    private static void assertEdge(SlimeTop top, int x, double expected, String edge) {
        for (int z : MIDDLE) {
            assertEquals(expected, top.heightAt(x, z), EPSILON, edge + " at " + z);
        }
    }
}
