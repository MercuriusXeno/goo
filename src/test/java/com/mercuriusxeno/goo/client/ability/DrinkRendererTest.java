package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The renderer keeps a drink's skin moving between meshes and meshing
 * within its budget: a vertex is carried on along its flow at the liquid's
 * pace for the time since the mesh, as far as it rides the liquid and no
 * further than the glove, and with the glove by the cube of its nearness to
 * the hand; the grid's cell coarsens when a mesh overruns and refines when
 * one runs well under (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkRendererTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(new BlockPos(7, 2, 3), CENTER, 1, 0, 100);
    private static final double RADIUS = 0.1;
    private static final double SPEED = 0.4;
    private static final double TWO_TICKS = 2;
    private static final double LONG = 1000;
    private static final double OVERRUN_MS = 40;
    private static final double QUICK_MS = 10;
    private static final double NEAR_BUDGET_MS = 30;

    private static DrinkTree.Stream stream() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(BLOCK.pos()), GLOVE);
        return DrinkTree.build(List.of(BLOCK), layout, GLOVE, EAST, 0).getFirst();
    }

    private static DrinkMesher.Vertex vertex(double carry) {
        DrinkStream.Ring ring = new DrinkStream.Ring(CENTER, EAST, RADIUS, 0, 0.5, SPEED, carry);
        DrinkTree.Stream stream = stream();
        return new DrinkMesher.Vertex(CENTER, UP, new DrinkField.Skeleton(stream, List.of(ring, ring), null), ring,
                carry);
    }

    @Nested
    class CarriedOn {

        @Test
        void aVertexRidesItsFlowAtTheLiquidsPaceForTheTimeSinceTheMesh() {
            Vec3 carried = DrinkRenderer.carriedOn(vertex(1), new DrinkRenderer.Motion(Vec3.ZERO, TWO_TICKS));

            assertEquals(0, carried.distanceTo(CENTER.add(EAST.scale(SPEED * TWO_TICKS))), DELTA);
        }

        @Test
        void aVertexOnTheStandingBlockStays() {
            Vec3 carried = DrinkRenderer.carriedOn(vertex(0), new DrinkRenderer.Motion(Vec3.ZERO, TWO_TICKS));

            assertEquals(CENTER, carried);
        }

        @Test
        void aVertexIsCarriedNoFurtherThanTheRestOfItsRoute() {
            DrinkMesher.Vertex vertex = vertex(1);
            double remaining = 0.5 * vertex.skeleton().stream().routeLength();
            Vec3 carried = DrinkRenderer.carriedOn(vertex, new DrinkRenderer.Motion(Vec3.ZERO, LONG));

            assertEquals(0, carried.distanceTo(CENTER.add(EAST.scale(remaining))), DELTA);
        }

        @Test
        void aVertexMovesWithTheGloveByTheCubeOfItsNearnessToTheHand() {
            Vec3 carried = DrinkRenderer.carriedOn(vertex(1), new DrinkRenderer.Motion(UP, 0));

            assertEquals(0, carried.distanceTo(CENTER.add(UP.scale(0.125))), DELTA);
        }
    }

    @Nested
    class Cell {

        @Test
        void anOverrunCoarsensTheCellUpToTheCoarsest() {
            assertEquals(DrinkMesher.CELL * DrinkRenderer.COARSEN, DrinkRenderer.cellAfter(DrinkMesher.CELL,
                    OVERRUN_MS), DELTA);
            assertEquals(DrinkMesher.CELL * DrinkRenderer.COARSEST, DrinkRenderer.cellAfter(DrinkMesher.CELL
                    * DrinkRenderer.COARSEST, OVERRUN_MS), DELTA);
        }

        @Test
        void aQuickMeshRefinesTheCellDownToTheFinestAndOneNearTheBudgetHolds() {
            double coarse = DrinkMesher.CELL * DrinkRenderer.COARSEN;

            assertEquals(DrinkMesher.CELL, DrinkRenderer.cellAfter(coarse, QUICK_MS), DELTA);
            assertEquals(DrinkMesher.CELL, DrinkRenderer.cellAfter(DrinkMesher.CELL, QUICK_MS), DELTA);
            assertEquals(coarse, DrinkRenderer.cellAfter(coarse, NEAR_BUDGET_MS), DELTA);
        }
    }
}
