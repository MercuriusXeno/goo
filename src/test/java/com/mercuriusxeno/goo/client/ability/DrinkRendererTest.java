package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The renderer keeps a drink's skin moving between meshes: a vertex is
 * carried on along its normal at the pace the skin was moving there for the
 * time since the mesh, no longer than the extrapolation, and with the glove
 * by the cube of its nearness to the hand
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkRendererTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(new BlockPos(7, 2, 3), CENTER, 1, 0, 0, 100);
    private static final double RADIUS = 0.1;
    private static final double OUTWARD = 0.05;
    private static final double HALF_TICK = 0.5;
    private static final double LONG = 1000;

    private static DrinkTree.Stream stream() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(BLOCK.pos()), GLOVE);
        return DrinkTree.build(List.of(BLOCK), layout, GLOVE, EAST, 0).getFirst();
    }

    private static DrinkMesher.Vertex vertex(double velocity) {
        DrinkStream.Ring ring = new DrinkStream.Ring(CENTER, EAST, RADIUS, 0, 0.5, DrinkStream.FLOW);
        return new DrinkMesher.Vertex(CENTER, UP, new DrinkField.Skeleton(stream(), List.of(ring, ring), null), ring,
                velocity);
    }

    @Nested
    class CarriedOn {

        @Test
        void aVertexMovesAlongItsNormalAtTheSkinsPaceForTheTimeSinceTheMesh() {
            Vec3 carried = DrinkRenderer.carriedOn(vertex(OUTWARD), new DrinkRenderer.Motion(Vec3.ZERO, HALF_TICK));

            assertEquals(0, carried.distanceTo(CENTER.add(UP.scale(OUTWARD * HALF_TICK))), DELTA);
        }

        @Test
        void aStillVertexStays() {
            Vec3 carried = DrinkRenderer.carriedOn(vertex(0), new DrinkRenderer.Motion(Vec3.ZERO, HALF_TICK));

            assertEquals(CENTER, carried);
        }

        @Test
        void aVertexIsCarriedNoLongerThanTheExtrapolation() {
            Vec3 carried = DrinkRenderer.carriedOn(vertex(-OUTWARD), new DrinkRenderer.Motion(Vec3.ZERO, LONG));

            assertEquals(0, carried.distanceTo(CENTER.subtract(UP.scale(OUTWARD * DrinkRenderer.EXTRAPOLATE))), DELTA);
        }

        @Test
        void aVertexMovesWithTheGloveByTheCubeOfItsNearnessToTheHand() {
            Vec3 carried = DrinkRenderer.carriedOn(vertex(0), new DrinkRenderer.Motion(EAST, 0));

            assertEquals(0, carried.distanceTo(CENTER.add(EAST.scale(0.125))), DELTA);
        }
    }
}
