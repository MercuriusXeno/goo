package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Decay paints the one block under the crosshair, never the blocks beside it
 * (decision decay-paints-the-one-block-under-the-crosshair); the world is a
 * mocked level whose stone wall stands at x = 3.
 */
class DegradeStepTest {

    private static final double WALL_X = 3;
    private static final double INTO_THE_WALL = 0.01;
    private static final double RANGE = 6;
    private static final double CONE_DEGREES = 10;
    private static final Vec3 EYE = new Vec3(0.5, 1.5, 0.5);
    /** On the wall's face a twentieth of a block short of the seam between two stones. */
    private static final Vec3 NEAR_THE_SEAM = new Vec3(WALL_X, 1.5, 0.95);

    private final Block stone = mock(Block.class);
    private final Block cobblestone = mock(Block.class);
    private final ChannelHost host = mock(ChannelHost.class);

    DegradeStepTest() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.clip(any(ClipContext.class))).thenAnswer(call -> wallHit(call.getArgument(0)));
        when(host.level()).thenReturn(level);
        when(host.eye()).thenReturn(EYE);
        when(host.airAt(any())).thenAnswer(call -> call.<BlockPos>getArgument(0).getX() < WALL_X);
        when(host.blockAt(any())).thenReturn(stone);
        when(host.stateAt(any())).thenReturn(mock(BlockState.class));
    }

    private static BlockHitResult wallHit(ClipContext clip) {
        Vec3 from = clip.getFrom();
        Vec3 to = clip.getTo();
        if (to.x < WALL_X) {
            return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
        }
        Vec3 struck = from.add(to.subtract(from).scale((WALL_X - from.x) / (to.x - from.x)));
        return new BlockHitResult(struck, Direction.WEST,
                BlockPos.containing(struck.x + INTO_THE_WALL, struck.y, struck.z), false);
    }

    @Test
    void aimNearASeamPaintsOnlyTheStoneUnderTheCrosshair() {
        Vec3 aimPoint = EYE.add(NEAR_THE_SEAM.subtract(EYE).normalize().scale(RANGE));
        HoldMarks marks = new HoldMarks();

        DegradeStep.paintAimed(host, new ChannelAim(aimPoint, null, CONE_DEGREES, 1, true),
                block -> block == stone ? Optional.of(cobblestone) : Optional.empty(), marks);

        assertEquals(Set.of(new BlockPos(3, 1, 0)), marks.painted().keySet());
    }
}
