package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mesher turns a drink's field into one closed skin: a sphere's field
 * meshes to vertices settled onto the sphere within half a cell with normals
 * pointing out, every quad wound to face out, two near bodies mesh as one
 * surface, a coarser cell meshes fewer quads, each vertex reads how fast the
 * skin moves along its normal from the field a tick ahead, still for a sphere
 * that stays and outward for one that grows, and each quad places its
 * vertices on the texture in one frame, the block's world axes on the block
 * and round the stream on the stream (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkMesherTest {

    private static final double DELTA = 1e-9;
    private static final double RADIUS = 0.3;
    /** How much a growing sphere's radius grows in the tick ahead. */
    private static final double GROWTH = 0.03;
    /** A gap between two surfaces well within the reach, which the field fills. */
    private static final double NEAR_GAP = 0.15;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(new BlockPos(7, 2, 3), CENTER, 1, 0, 0, 100);
    private static final double COARSE = 2;
    private static final double STILL = 1e-9;
    /** How far the read pace may miss the growth, the falloff bending over it. */
    private static final double PACE_SLACK = 0.01;
    private static final double TWO_BLOCKS = 2;
    private static final double HALF_BLOCK = 0.5;

    private static DrinkTree.Stream stream() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(BLOCK.pos()), GLOVE);
        return DrinkTree.build(List.of(BLOCK), layout, GLOVE, EAST, 0).getFirst();
    }

    private static DrinkStream.Ring ring(Vec3 center, double radius) {
        return new DrinkStream.Ring(center, EAST, radius, 0, 0.5, DrinkStream.FLOW);
    }

    private static DrinkField.Skeleton sphere(Vec3 center, double radius) {
        DrinkStream.Ring ring = ring(center, radius);
        return new DrinkField.Skeleton(stream(), List.of(ring, ring), null);
    }

    private static List<DrinkMesher.Quad> still(DrinkField.Skeleton... skeletons) {
        return DrinkMesher.mesh(List.of(skeletons), List.of(skeletons), DrinkMesher.CELL);
    }

    private static boolean isACoordinateOf(double value, Vec3 point) {
        return Math.abs(value - point.x) < DELTA || Math.abs(value - point.y) < DELTA || Math.abs(value - point.z) < DELTA;
    }

    @Test
    void aSpheresFieldMeshesToASkinSettledOnTheSphereFacingOutAndStill() {
        List<DrinkMesher.Quad> quads = still(sphere(CENTER, RADIUS));

        assertFalse(quads.isEmpty());
        for (DrinkMesher.Quad quad : quads) {
            for (DrinkMesher.Vertex vertex : quad.vertices()) {
                Vec3 out = vertex.point().subtract(CENTER);
                assertEquals(RADIUS, out.length(), DrinkMesher.CELL / 2, "a vertex settles onto the sphere");
                assertTrue(vertex.normal().dot(out.normalize()) > 0.9, "a normal points out");
                assertEquals(0, vertex.velocity(), STILL, "a sphere that stays reads still");
            }
            Vec3 a = quad.vertices()[1].point().subtract(quad.vertices()[0].point());
            Vec3 b = quad.vertices()[2].point().subtract(quad.vertices()[0].point());
            Vec3 facing = a.cross(b);
            Vec3 middle = quad.vertices()[0].point().add(quad.vertices()[2].point()).scale(0.5).subtract(CENTER);
            assertTrue(facing.dot(middle) >= 0, "a quad is wound to face out");
        }
    }

    @Test
    void aGrowingSpheresSkinReadsMovingOutwardByItsGrowth() {
        List<DrinkMesher.Quad> quads = DrinkMesher.mesh(List.of(sphere(CENTER, RADIUS)),
                List.of(sphere(CENTER, RADIUS + GROWTH)), DrinkMesher.CELL);

        assertFalse(quads.isEmpty());
        for (DrinkMesher.Quad quad : quads) {
            for (DrinkMesher.Vertex vertex : quad.vertices()) {
                assertEquals(GROWTH, vertex.velocity(), PACE_SLACK, "the skin moves out by the growth a tick");
            }
        }
    }

    @Test
    void twoNearSpheresMeshAsOneSkinWithAWaistBetween() {
        Vec3 other = CENTER.add(0, 2 * RADIUS + NEAR_GAP, 0);
        List<DrinkMesher.Quad> quads = still(sphere(CENTER, RADIUS), sphere(other, RADIUS));
        Vec3 between = CENTER.lerp(other, 0.5);
        boolean skinBetween = false;
        for (DrinkMesher.Quad quad : quads) {
            for (DrinkMesher.Vertex vertex : quad.vertices()) {
                skinBetween |= Math.abs(vertex.point().y - between.y) < DrinkMesher.CELL
                        && vertex.point().subtract(between).horizontalDistance() > 0;
            }
        }

        assertTrue(skinBetween, "the skin runs through the gap, one body");
    }

    @Test
    void aCoarserCellMeshesFewerQuadsOnTheSameSphere() {
        List<DrinkField.Skeleton> sphere = List.of(sphere(CENTER, RADIUS));
        int fine = DrinkMesher.mesh(sphere, sphere, DrinkMesher.CELL).size();
        int coarse = DrinkMesher.mesh(sphere, sphere, DrinkMesher.CELL * COARSE).size();

        assertTrue(coarse > 0);
        assertTrue(coarse < fine / 2, "a cell twice as big meshes under half the quads, " + coarse + " to " + fine);
    }

    @Test
    void aQuadOnTheBlockReadsWorldAxesAndOneOnTheStreamReadsRoundTheStream() {
        Vec3 boxCenter = CENTER.add(0, TWO_BLOCKS, 0);
        DrinkStream.Ring ring = ring(CENTER, RADIUS);
        DrinkField.Skeleton skeleton = new DrinkField.Skeleton(stream(), List.of(ring, ring),
                new DrinkBody.Box(boxCenter, HALF_BLOCK, 0));
        boolean onBlock = false;
        boolean onStream = false;
        for (DrinkMesher.Quad quad : still(skeleton)) {
            boolean nearBox = quad.vertices()[0].point().distanceTo(boxCenter) < quad.vertices()[0].point()
                    .distanceTo(CENTER);
            for (int corner = 0; corner < quad.vertices().length; corner++) {
                Vec3 point = quad.vertices()[corner].point();
                if (nearBox) {
                    assertTrue(isACoordinateOf(quad.along()[corner], point) && isACoordinateOf(quad.around()[corner],
                            point), "a block quad reads world axes at " + point);
                } else {
                    assertTrue(Math.abs(quad.around()[corner]) <= Math.PI * RADIUS + DrinkMesher.CELL,
                            "a stream quad reads its arc round the stream at " + point);
                }
            }
            onBlock |= nearBox;
            onStream |= !nearBox;
        }

        assertTrue(onBlock && onStream, "both the block and the stream are skinned");
    }

    @Test
    void theGridKeysEveryCellApart() {
        assertEquals(DrinkMesher.key(1, 2, 3), DrinkMesher.key(1, 2, 3));
        assertTrue(DrinkMesher.key(1, 2, 3) != DrinkMesher.key(3, 2, 1));
        assertTrue(DrinkMesher.key(-1, 0, 0) != DrinkMesher.key(0, 0, -1));
        assertEquals(-1, DrinkMesher.cellOf(-0.01, DrinkMesher.CELL));
        assertEquals(16, DrinkMesher.cellOf(1.0 + DrinkMesher.CELL / 2, DrinkMesher.CELL));
        assertEquals(8, DrinkMesher.cellOf(1.0 + DrinkMesher.CELL, DrinkMesher.CELL * COARSE));
    }
}
