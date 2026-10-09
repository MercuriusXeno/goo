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
 * meshes to vertices on the sphere with normals pointing out, every quad
 * wound to face out, and two near bodies mesh as one surface
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkMesherTest {

    private static final double RADIUS = 0.3;
    /** A gap between two surfaces well within the reach, which the field fills. */
    private static final double NEAR_GAP = 0.15;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(new BlockPos(7, 2, 3), CENTER, 1, 0, 100);

    private static DrinkTree.Stream stream() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(BLOCK.pos()), GLOVE);
        return DrinkTree.build(List.of(BLOCK), layout, GLOVE, EAST, 0).getFirst();
    }

    private static DrinkField.Skeleton sphere(Vec3 center, double radius) {
        DrinkStream.Ring ring = new DrinkStream.Ring(center, EAST, radius, 0, 0.5);
        return new DrinkField.Skeleton(stream(), List.of(ring, ring), null);
    }

    @Test
    void aSpheresFieldMeshesToASkinOnTheSphereFacingOut() {
        List<DrinkMesher.Quad> quads = DrinkMesher.mesh(List.of(sphere(CENTER, RADIUS)));

        assertFalse(quads.isEmpty());
        for (DrinkMesher.Quad quad : quads) {
            for (DrinkMesher.Vertex vertex : quad.vertices()) {
                Vec3 out = vertex.point().subtract(CENTER);
                assertEquals(RADIUS, out.length(), DrinkMesher.CELL, "a vertex sits on the sphere");
                assertTrue(vertex.normal().dot(out.normalize()) > 0.9, "a normal points out");
            }
            Vec3 a = quad.vertices()[1].point().subtract(quad.vertices()[0].point());
            Vec3 b = quad.vertices()[2].point().subtract(quad.vertices()[0].point());
            Vec3 facing = a.cross(b);
            Vec3 middle = quad.vertices()[0].point().add(quad.vertices()[2].point()).scale(0.5).subtract(CENTER);
            assertTrue(facing.dot(middle) >= 0, "a quad is wound to face out");
        }
    }

    @Test
    void twoNearSpheresMeshAsOneSkinWithAWaistBetween() {
        Vec3 other = CENTER.add(0, 2 * RADIUS + NEAR_GAP, 0);
        List<DrinkMesher.Quad> quads = DrinkMesher.mesh(List.of(sphere(CENTER, RADIUS), sphere(other, RADIUS)));
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
    void theGridKeysEveryCellApart() {
        assertEquals(DrinkMesher.key(1, 2, 3), DrinkMesher.key(1, 2, 3));
        assertTrue(DrinkMesher.key(1, 2, 3) != DrinkMesher.key(3, 2, 1));
        assertTrue(DrinkMesher.key(-1, 0, 0) != DrinkMesher.key(0, 0, -1));
        assertEquals(-1, DrinkMesher.cellOf(-0.01));
        assertEquals(12, DrinkMesher.cellOf(1.0 + DrinkMesher.CELL / 2));
    }
}
