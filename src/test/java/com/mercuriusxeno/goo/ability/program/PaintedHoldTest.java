package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A painted block steps through the finish its stream names, whether the
 * hold fills it or it is left to finish alone past nine tenths, a block
 * stepping to the empty block included
 * (decision blocks-that-cook-into-items-cook-like-blocks), and a ray crosses
 * each cell on its line once, nearest first.
 */
class PaintedHoldTest {

    private static final float RATE = 0.05f;

    private final ChannelHost host = mock(ChannelHost.class);
    private final BlockPos pos = new BlockPos(3, 1, 2);
    private final Block log = mock(Block.class);
    private final Block empty = mock(Block.class);
    private final BlockState emptyState = mock(BlockState.class);
    private final BlockState logState = mock(BlockState.class);
    private final PaintedHold.CellFinish finish = mock(PaintedHold.CellFinish.class);
    private final HoldMarks marks = new HoldMarks();

    PaintedHoldTest() {
        when(empty.defaultBlockState()).thenReturn(emptyState);
        when(emptyState.isAir()).thenReturn(true);
        when(emptyState.getBlock()).thenReturn(empty);
        when(logState.getBlock()).thenReturn(log);
        when(host.stateAt(pos)).thenReturn(logState);
        marks.paint(pos, logState);
    }

    @Test
    void aBlockPastNineTenthsTowardTheEmptyBlockFinishesAloneThroughItsFinish() {
        when(host.exposeBlock(pos, emptyState, RATE)).thenReturn(0.95f);

        PaintedHold.buildPainted(host, raw -> Optional.of(empty), marks, RATE, OptionalInt.empty(), finish);

        ArgumentCaptor<Runnable> alone = ArgumentCaptor.forClass(Runnable.class);
        verify(host).finishBlockAlone(eq(pos), eq(RATE), alone.capture());
        alone.getValue().run();
        verify(finish).finish(pos, log, empty);
    }

    @Test
    void aBlockTheHoldFillsStepsThroughItsFinish() {
        when(host.exposeBlock(pos, emptyState, RATE)).thenReturn(1f);

        PaintedHold.buildPainted(host, raw -> Optional.of(empty), marks, RATE, OptionalInt.empty(), finish);

        verify(finish).finish(pos, log, empty);
    }

    @Test
    void theLineCrossesEachCellItRunsThroughOnceNearestFirst() {
        List<BlockPos> cells = PaintedHold.cellsAlong(new Vec3(0.5, 0.5, 0.5), new Vec3(3.5, 0.5, 0.5));
        assertEquals(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
                new BlockPos(3, 0, 0)), cells);
    }

    @Test
    void aSlantedLineStepsDownThroughTheCellsBelow() {
        List<BlockPos> cells = PaintedHold.cellsAlong(new Vec3(0.5, 2.5, 0.5), new Vec3(2.5, 0.5, 0.5));
        assertEquals(List.of(new BlockPos(0, 2, 0), new BlockPos(1, 1, 0), new BlockPos(2, 0, 0)), cells);
    }

    @Test
    void aLineWithNoLengthHoldsItsOwnCell() {
        Vec3 here = new Vec3(0.5, 0.5, 0.5);
        assertEquals(List.of(new BlockPos(0, 0, 0)), PaintedHold.cellsAlong(here, here));
    }

    /**
     * The spray's mote for a pair of tilt and turn shares flies to the end the
     * coat's ray takes for the same shares.
     * goop-and-slick-spray-follows-the-coat
     */
    @Nested
    class SprayFollowsTheCoat {

        private static final Vec3 EYE = new Vec3(0, 1.62, 0);
        private static final double CONE = 24;
        private static final double RANGE = 8;
        private static final double TOLERANCE = 1e-9;
        /** The floor's top face at y 0, the only block a ray meets. */
        private static final CoatRay.BlockClip FLOOR = (from, to) -> from.y > 0 && to.y <= 0
                ? Optional.of(from.add(to.subtract(from).scale(from.y / (from.y - to.y))))
                : Optional.empty();

        @Test
        void everyCoatRaysEndIsTheSprayEndForItsShares() {
            Vec3 look = new Vec3(0, -0.2, 1).normalize();
            List<Vec3> directions = PaintedHold.rayDirections(look, CONE);
            List<Vec3> coatEnds = PaintedHold.rayEnds(FLOOR, EYE, look.scale(RANGE), CONE);
            List<CoatRay.ConeShare> shares = PaintedHold.rayShares();
            assertEquals(directions.size(), coatEnds.size());
            assertEquals(directions.size(), shares.size());
            assertTrue(coatEnds.stream().anyMatch(end -> Math.abs(end.y) < 0.02), "some ray should meet the floor");
            assertTrue(coatEnds.stream().anyMatch(end -> Math.abs(EYE.distanceTo(end) - RANGE) < TOLERANCE),
                    "some ray should run to the range");
            for (int i = 0; i < directions.size(); i++) {
                Vec3 coatEnd = coatEnds.get(i);
                Vec3 sprayEnd = CoatRay.end(FLOOR, EYE, look, CONE, RANGE, shares.get(i));
                assertEquals(0, coatEnd.distanceTo(sprayEnd), TOLERANCE, "ray " + i);
                assertEquals(0, coatEnd.subtract(EYE).normalize().distanceTo(directions.get(i)), TOLERANCE,
                        "ray " + i + " should run along the coat's fan");
            }
        }
    }
}
