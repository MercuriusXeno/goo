package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.BlockEffect;
import com.mercuriusxeno.goo.ability.BlockEffectType;
import com.mercuriusxeno.goo.ability.LayerAudioType;
import com.mercuriusxeno.goo.ability.LayerVisualsType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The layer walk previews layer i on tick i and strikes it on tick
 * i + preview_delay through the host, with the rock invariants: every
 * tunnel layer lies perpendicular to blastDir = placedFace.getOpposite()
 * and layer i is centered on origin.relative(blastDir, i + 1).
 */
class ProgressiveAreaStepTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -20);
    private static final int DELAY = 8;
    private static final String SILK_BREAK = "silk_break";
    private static final String ROCK_DUST = "rock_dust";
    private static final String STONE_BREAK = "stone_break";
    private static final BlockEffect SILK = BlockEffectType.byName(SILK_BREAK);
    /** Stacks giving a tunnel three layers deep with a 3x3 footprint. */
    private static final int DEEP_STACKS = 5;
    private static final int DEEP_LAYERS = 3;
    private static final int FOOTPRINT_3X3 = 9;
    /** Stacks giving a one-block footprint. */
    private static final int ONE_STACK = 1;

    private static ProgressiveAreaStep step(AreaShape shape) {
        return new ProgressiveAreaStep(shape, SILK_BREAK, ROCK_DUST, STONE_BREAK, Expr.literal(DELAY));
    }

    private static StepHost host(Direction placedFace, int stacks) {
        StepHost host = mock(StepHost.class);
        when(host.position()).thenReturn(ORIGIN);
        when(host.placedFace()).thenReturn(placedFace);
        when(host.stackCount()).thenReturn(stacks);
        when(host.applyBlockEffect(any(), any())).thenReturn(true);
        return host;
    }

    private static ProgramBehavior run(StepHost host, AreaShape shape, int ticks) {
        ProgramBehavior program = new ProgramBehavior(List.of(step(shape)));
        for (int i = 0; i < ticks; i++) {
            program.tick(host);
        }
        return program;
    }

    private static List<BlockPos> struckCells(StepHost host) {
        ArgumentCaptor<BlockPos> cells = ArgumentCaptor.forClass(BlockPos.class);
        verify(host, atLeast(0)).applyBlockEffect(eq(SILK), cells.capture());
        return cells.getAllValues();
    }

    private static int along(BlockPos pos, Direction.Axis axis) {
        return pos.get(axis);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void tunnelLayerIsCenteredOneStepPastTheLayerIndexAlongTheBlastDirection(Direction placedFace) {
        StepHost host = host(placedFace, DEEP_STACKS);
        Direction blastDir = placedFace.getOpposite();

        run(host, AreaShape.TUNNEL, DELAY + DEEP_LAYERS);

        List<BlockPos> cells = struckCells(host);
        assertEquals(FOOTPRINT_3X3 * DEEP_LAYERS, cells.size());
        for (int layer = 0; layer < DEEP_LAYERS; layer++) {
            BlockPos center = ORIGIN.relative(blastDir, layer + 1);
            List<BlockPos> layerCells = cells.subList(layer * FOOTPRINT_3X3, (layer + 1) * FOOTPRINT_3X3);
            assertTrue(layerCells.contains(center), "layer " + layer + " holds its center " + center);
            for (BlockPos cell : layerCells) {
                assertEquals(along(center, blastDir.getAxis()), along(cell, blastDir.getAxis()),
                        "layer " + layer + " lies perpendicular to " + blastDir);
                assertTrue(cell.distManhattan(center) <= 2, cell + " is within the 3x3 around " + center);
            }
        }
    }

    @Test
    void layerZeroIsTheStruckBlockAndNeverTheMarker() {
        StepHost host = host(Direction.SOUTH, ONE_STACK);

        run(host, AreaShape.TUNNEL, DELAY + 1);

        assertEquals(List.of(ORIGIN.north()), struckCells(host));
    }

    @Test
    void previewLeadsTheStrikeByTheDelayAndTheWalkEndsWithTheLastLayer() {
        StepHost host = host(Direction.SOUTH, DEEP_STACKS);
        ProgramBehavior program = new ProgramBehavior(List.of(step(AreaShape.TUNNEL)));

        for (int tick = 0; tick < DELAY; tick++) {
            program.tick(host);
        }
        verify(host).previewLayer(LayerVisualsType.byName(ROCK_DUST), 0);
        verify(host).previewLayer(LayerVisualsType.byName(ROCK_DUST), DEEP_LAYERS - 1);
        verify(host, never()).applyBlockEffect(any(), any());
        verify(host, never()).reportMinedLayers(anyInt());

        program.tick(host);
        verify(host, times(FOOTPRINT_3X3)).applyBlockEffect(eq(SILK), any());
        verify(host).strikeLayerFx(LayerVisualsType.byName(ROCK_DUST), LayerAudioType.byName(STONE_BREAK), 0,
                FOOTPRINT_3X3);
        verify(host).reportMinedLayers(1);
        assertTrue(program.isActive());

        program.tick(host);
        program.tick(host);
        InOrder order = inOrder(host);
        order.verify(host).reportMinedLayers(2);
        order.verify(host).reportMinedLayers(DEEP_LAYERS);
        assertFalse(program.isActive());
    }

    @Test
    void struckCountScalesTheFxByTheCellsTheEffectChanged() {
        StepHost host = host(Direction.SOUTH, DEEP_STACKS);
        when(host.applyBlockEffect(eq(SILK), any())).thenReturn(true, false, false, true, true, false, false,
                false, false);

        run(host, AreaShape.TUNNEL, DELAY + 1);

        verify(host).strikeLayerFx(any(), any(), eq(0), eq(3));
    }

    @Test
    void flatCircleWalksRingsOneBlockIntoTheWall() {
        StepHost host = host(Direction.UP, 2);

        run(host, AreaShape.FLAT_CIRCLE, DELAY + 1);

        BlockPos center = ORIGIN.below();
        assertEquals(Set.of(center, center.north(), center.south(), center.east(), center.west()),
                Set.copyOf(struckCells(host)));
    }

    @Test
    void sphereWalksShellsAroundTheBlockPastTheMarker() {
        StepHost host = host(Direction.SOUTH, ONE_STACK);
        int shells = AreaLayers.layerCount(AreaShape.SPHERE, ONE_STACK);

        run(host, AreaShape.SPHERE, DELAY + shells);

        BlockPos center = ORIGIN.north();
        List<BlockPos> cells = struckCells(host);
        assertEquals(center, cells.get(0));
        Set<Integer> distances = cells.stream()
                .map(cell -> (int) Math.ceil(Math.sqrt(center.distSqr(cell))))
                .collect(Collectors.toSet());
        assertTrue(distances.stream().allMatch(distance -> distance < shells), distances.toString());
        verify(host).reportMinedLayers(shells);
    }

    @Test
    void walkAsksForTheLayerWalkStacksFaceAndTicking() {
        assertEquals(Set.of(HostCapability.LAYER_WALK, HostCapability.STACKS, HostCapability.PLACED_FACE,
                HostCapability.TICKING), step(AreaShape.TUNNEL).requires());
    }
}
