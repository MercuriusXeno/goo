package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mercuriusxeno.goo.client.SurfaceRipple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the crucible lays its melting items: the head's shards whole at the center of the
 * fill's top, breaking off rim first to rest at their own seeded spots across the open basin
 * with no two shards' pixels overlapping, each riding the ripple at its own spot, the waiting
 * items in its corners on the crest (decisions dissolve-shader-on-item,
 * tiles-break-off-as-dissolve-advances, each-tile-bobs-with-the-ripple).
 */
class CrucibleItemLayoutTest {

    private static final float EPSILON = 1e-6f;
    /** Float rounding across a sum taken in another order, far under a texture pixel. */
    private static final float FLOAT_ROUNDING = 1e-5f;
    private static final long MELTED = 16_000;
    private static final float RESTING = RenderContext.RESTING_RIPPLE_AMPLITUDE;
    private static final float FULLY_AGITATED = RESTING + SurfaceAgitation.AGITATION_CEILING;
    private static final float CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2f;
    private static final float MID_DISSOLVE = 0.5f;
    /** The wave over a crucible at the world origin at the start of the day. */
    private static final SurfaceRipple.Field STILL = new SurfaceRipple.Field(0, 0, 0f);
    /** Half the primary wave's wavelength, in blocks. */
    private static final float HALF_PRIMARY_WAVELENGTH = (float) (Math.PI / SurfaceRipple.PRIMARY_WAVENUMBER);
    private static final String ITEM = "minecraft:iron_ingot";
    private static final List<String> SAMPLE_ITEMS = List.of("minecraft:iron_ingot", "minecraft:diamond",
            "minecraft:apple", "minecraft:stick", "minecraft:redstone", "minecraft:bone", "goo:gasket",
            "minecraft:ender_pearl", "minecraft:blaze_rod", "minecraft:emerald", "minecraft:coal",
            "minecraft:gold_nugget", "minecraft:feather", "minecraft:string", "minecraft:paper", "goo:crucible");

    private static HeadShards flatHead(String itemId) {
        return HeadShards.of(ShardModels.flatFor(itemId), ItemShardCutter.seedOf(itemId));
    }

    private static HeadShards cubeHead(String itemId) {
        return HeadShards.of(ShardModels.cubeFor(itemId), ItemShardCutter.seedOf(itemId));
    }

    private static List<CrucibleItemLayout.ItemPlacement> shardsAt(HeadShards head, float fraction) {
        return CrucibleItemLayout.headShards(null, RESTING, fraction, STILL, head.pieces(), head.frame(),
                head.shifts());
    }

    private static void assertInsideBasin(CrucibleItemLayout.ItemPlacement placement) {
        float half = placement.size() / 2f;
        assertTrue(placement.x() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low X");
        assertTrue(placement.x() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high X");
        assertTrue(placement.z() - half >= CrucibleBasin.FOOTPRINT_MIN, placement + " leaves the basin at low Z");
        assertTrue(placement.z() + half <= CrucibleBasin.FOOTPRINT_MAX, placement + " leaves the basin at high Z");
    }

    /**
     * Asserts every shard's footprint at rest lies inside the footprint, outside the four
     * corner slots, and shares no lattice cell with another shard's.
     */
    private static void assertRestingClear(String label, HeadShards head) {
        CrucibleItemLayout.HeadFrame frame = head.frame();
        List<CrucibleItemLayout.ItemPlacement> corners =
                CrucibleItemLayout.waiting(null, RESTING, CrucibleItemLayout.WAITING_SLOTS);
        Set<Long> claimed = new HashSet<>();
        for (int i = 0; i < head.pieces().size(); i++) {
            for (int[] cell : head.pieces().get(i).footprint()) {
                int column = cell[0] + head.shifts()[i][0];
                int row = cell[1] + head.shifts()[i][1];
                float x = CENTER + frame.originX() + column * frame.cell();
                float z = CENTER + frame.originZ() + row * frame.cell();
                assertTrue(x >= CrucibleBasin.FOOTPRINT_MIN - EPSILON
                        && x + frame.cell() <= CrucibleBasin.FOOTPRINT_MAX + EPSILON
                        && z >= CrucibleBasin.FOOTPRINT_MIN - EPSILON
                        && z + frame.cell() <= CrucibleBasin.FOOTPRINT_MAX + EPSILON,
                        label + " shard " + i + " leaves the basin");
                for (CrucibleItemLayout.ItemPlacement corner : corners) {
                    float reach = corner.size() / 2f;
                    boolean apart = x >= corner.x() + reach - EPSILON || x + frame.cell() <= corner.x() - reach + EPSILON
                            || z >= corner.z() + reach - EPSILON || z + frame.cell() <= corner.z() - reach + EPSILON;
                    assertTrue(apart, label + " shard " + i + " overlaps the corner slot " + corner);
                }
                assertTrue(claimed.add(((long) column << Integer.SIZE) | Integer.toUnsignedLong(row)),
                        label + " shard " + i + " overlaps another shard's pixels");
            }
        }
    }

    @Nested
    class Scatter {

        /**
         * At fraction zero every shard sits at its home, its centroid within the whole item at
         * the basin center, and the shards' footprints together cover the whole head.
         */
        @Test
        void wholeAtFractionZero() {
            HeadShards head = flatHead(ITEM);
            List<CrucibleItemLayout.ItemPlacement> shards = shardsAt(head, 0f);

            Set<Long> covered = new HashSet<>();
            for (int i = 0; i < shards.size(); i++) {
                CrucibleItemLayout.ShardPiece piece = head.pieces().get(i);
                assertEquals(CENTER + piece.homeX(), shards.get(i).x(), EPSILON);
                assertEquals(CENTER + piece.homeZ(), shards.get(i).z(), EPSILON);
                for (int[] cell : piece.footprint()) {
                    covered.add(((long) cell[0] << Integer.SIZE) | Integer.toUnsignedLong(cell[1]));
                }
            }
            assertEquals(ShardModels.TEXELS * ShardModels.TEXELS, covered.size(), "the head's texels, all covered");
            assertEquals(CrucibleItemLayout.HEAD_SIZE, head.frame().cell() * ShardModels.TEXELS, EPSILON);
        }

        /** At fraction one every shard of a flat item rests inside the basin, clear of the corners and each other. */
        @Test
        void flatItemsRestClear() {
            for (String itemId : SAMPLE_ITEMS) {
                assertRestingClear(itemId, flatHead(itemId));
            }
        }

        /** Every shard of a block, top, bottom and sides, rests clear of the corners and each other. */
        @Test
        void blocksRestClear() {
            for (String itemId : SAMPLE_ITEMS) {
                assertRestingClear(itemId + " block", cubeHead(itemId));
            }
        }

        /** A shard farther from the head's center breaks off no later than a nearer one. */
        @Test
        void rimShardsBreakOffFirst() {
            for (HeadShards head : List.of(flatHead(ITEM), cubeHead(ITEM))) {
                List<CrucibleItemLayout.ShardPiece> pieces = head.pieces();
                float[] breakOffs = CrucibleItemLayout.breakOffs(pieces);
                for (int i = 0; i < pieces.size(); i++) {
                    for (int j = 0; j < pieces.size(); j++) {
                        if (CrucibleItemLayout.distanceFromCenter(pieces.get(i))
                                > CrucibleItemLayout.distanceFromCenter(pieces.get(j))) {
                            assertTrue(breakOffs[i] <= breakOffs[j], "shard " + i + " breaks after nearer shard " + j);
                        }
                    }
                }
            }
        }

        /**
         * Midway, some shards still sit at home and some have moved, each moved shard on the
         * segment from its home to its resting spot.
         */
        @Test
        void shardsBreakOffInTurn() {
            HeadShards head = flatHead(ITEM);
            List<CrucibleItemLayout.ItemPlacement> home = shardsAt(head, 0f);
            List<CrucibleItemLayout.ItemPlacement> mid = shardsAt(head, MID_DISSOLVE);
            List<CrucibleItemLayout.ItemPlacement> rest = shardsAt(head, 1f);

            int unmoved = 0;
            int moved = 0;
            for (int i = 0; i < mid.size(); i++) {
                float dx = mid.get(i).x() - home.get(i).x();
                float dz = mid.get(i).z() - home.get(i).z();
                if (Math.abs(dx) < EPSILON && Math.abs(dz) < EPSILON) {
                    unmoved++;
                    continue;
                }
                moved++;
                float spanX = rest.get(i).x() - home.get(i).x();
                float spanZ = rest.get(i).z() - home.get(i).z();
                assertEquals(0f, dx * spanZ - dz * spanX, FLOAT_ROUNDING, "shard " + i + " leaves its drift line");
                float along = (dx * spanX + dz * spanZ) / (spanX * spanX + spanZ * spanZ);
                assertTrue(along > 0f && along <= 1f + FLOAT_ROUNDING, "shard " + i + " passes its ends: " + along);
            }
            assertTrue(unmoved > 0, "every shard has broken off midway");
            assertTrue(moved > 0, "no shard has broken off midway");
        }

        /** A block's shards stand on the surface at home and settle onto it at rest. */
        @Test
        void blockShardsSettleOntoTheSurface() {
            HeadShards head = cubeHead(ITEM);
            List<CrucibleItemLayout.ItemPlacement> home = shardsAt(head, 0f);
            List<CrucibleItemLayout.ItemPlacement> rest = shardsAt(head, 1f);
            float floor = CrucibleBasin.FLOOR_Y + CrucibleItemLayout.FLOAT_LIFT;
            for (int i = 0; i < home.size(); i++) {
                CrucibleItemLayout.ShardPiece piece = head.pieces().get(i);
                assertEquals(floor + piece.homeY() - head.frame().bottomY(), home.get(i).y(), FLOAT_ROUNDING);
                assertEquals(floor + piece.homeY() - piece.floorY(), rest.get(i).y(), FLOAT_ROUNDING);
            }
        }
    }

    @Nested
    class Bobbing {

        /**
         * Each shard rests at the surface plus the amplitude times the wave at its own spot
         * plus the tile lift, raised by its height over the surface, at home and at rest.
         */
        @Test
        void shardRestsOnTheWaveAtItsSpot() {
            CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
            SurfaceRipple.Field field = new SurfaceRipple.Field(-37, 112, 0.3f);
            HeadShards head = flatHead(ITEM);

            for (float fraction : new float[] {0f, 1f}) {
                List<CrucibleItemLayout.ItemPlacement> shards = CrucibleItemLayout.headShards(surface, FULLY_AGITATED,
                        fraction, field, head.pieces(), head.frame(), head.shifts());
                for (int i = 0; i < shards.size(); i++) {
                    CrucibleItemLayout.ItemPlacement shard = shards.get(i);
                    CrucibleItemLayout.ShardPiece piece = head.pieces().get(i);
                    float rise = piece.homeY() - (fraction == 0f ? head.frame().bottomY() : piece.floorY());
                    float wave = SurfaceRipple.at(-37 + shard.x(), 112 + shard.z(), 0.3f);
                    assertEquals(surface.surfaceY() + FULLY_AGITATED * wave
                            + CrucibleItemLayout.tileLift(FULLY_AGITATED) + rise, shard.y(), FLOAT_ROUNDING);
                }
            }
        }

        /** A tile on the primary crest and one half a wavelength on, in its trough, ride the wave out of step. */
        @Test
        void tilesHalfAWavelengthApartDifferInHeight() {
            CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
            float crest = (float) (2.5 * Math.PI / (SurfaceRipple.PRIMARY_WAVENUMBER
                    * (SurfaceRipple.PRIMARY_DIRECTION_X + SurfaceRipple.PRIMARY_DIRECTION_Z)));
            float farX = crest + SurfaceRipple.PRIMARY_DIRECTION_X * HALF_PRIMARY_WAVELENGTH;
            float farZ = crest + SurfaceRipple.PRIMARY_DIRECTION_Z * HALF_PRIMARY_WAVELENGTH;

            float near = CrucibleItemLayout.tileY(surface, FULLY_AGITATED, STILL, crest, crest);
            float far = CrucibleItemLayout.tileY(surface, FULLY_AGITATED, STILL, farX, farZ);

            assertTrue(Math.abs(near - far) > FULLY_AGITATED / 4f, near + " vs " + far);
        }

        /** The tile lift grows with the amplitude and never drops below the float lift. */
        @Test
        void tileLiftCoversTheSagAcrossATile() {
            float halfDiagonal = CrucibleItemLayout.TILE_SIZE / 2f * (float) Math.sqrt(2.0);
            float sag = (float) (1.0 - Math.cos(SurfaceRipple.SECONDARY_WAVENUMBER * halfDiagonal));

            assertEquals(CrucibleItemLayout.FLOAT_LIFT, CrucibleItemLayout.tileLift(0f), EPSILON);
            assertEquals(FULLY_AGITATED * sag + CrucibleItemLayout.FLOAT_LIFT,
                    CrucibleItemLayout.tileLift(FULLY_AGITATED), EPSILON);
        }

        /** A waiting item keeps the crest rule: the fill, a full amplitude and the float lift. */
        @Test
        void waitingItemsRideTheCrest() {
            CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));

            for (CrucibleItemLayout.ItemPlacement placement : CrucibleItemLayout.waiting(surface, FULLY_AGITATED, 3)) {
                assertEquals(surface.surfaceY() + FULLY_AGITATED + CrucibleItemLayout.FLOAT_LIFT, placement.y(),
                        EPSILON);
            }
        }
    }

    /** Three waiting stacks lie in three distinct corners inside the basin, above the fill. */
    @Test
    void threeWaitingItemsLieInsideTheBasin() {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, 0));
        List<CrucibleItemLayout.ItemPlacement> waiting = CrucibleItemLayout.waiting(surface, RESTING, 3);

        assertEquals(3, waiting.size());
        for (CrucibleItemLayout.ItemPlacement placement : waiting) {
            assertInsideBasin(placement);
            assertTrue(placement.y() > surface.surfaceY());
        }
        assertNotEquals(waiting.get(0), waiting.get(1));
        assertNotEquals(waiting.get(1), waiting.get(2));
        assertNotEquals(waiting.get(0), waiting.get(2));
    }

    /** More stacks waiting than the basin has corners show one per corner. */
    @Test
    void waitingItemsCapAtTheCorners() {
        assertEquals(CrucibleItemLayout.WAITING_SLOTS, CrucibleItemLayout.waiting(null, RESTING, 9).size());
    }
}
