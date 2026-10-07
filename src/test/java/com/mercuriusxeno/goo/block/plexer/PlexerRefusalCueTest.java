package com.mercuriusxeno.goo.block.plexer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers where the refusal cue's smoke puffs: the cutaway's world center for each horizontal
 * facing. The smoke and sound themselves are registry constants, proven by the gametest
 * ix_plexer_refuses_unlearned.
 */
class PlexerRefusalCueTest {

    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final double EPSILON = 1e-9;
    private static final double CENTER_Y = 10.5 / 16.0;
    private static final double NEAR_EDGE = 2.0 / 16.0;

    /** The cutaway's center sits at the block's horizontal middle, two pixels in from the facing side. */
    static Stream<Arguments> facings() {
        return Stream.of(
                Arguments.of(Direction.SOUTH, new Vec3(0.5, CENTER_Y, NEAR_EDGE)),
                Arguments.of(Direction.NORTH, new Vec3(0.5, CENTER_Y, 1.0 - NEAR_EDGE)),
                Arguments.of(Direction.EAST, new Vec3(NEAR_EDGE, CENTER_Y, 0.5)),
                Arguments.of(Direction.WEST, new Vec3(1.0 - NEAR_EDGE, CENTER_Y, 0.5)));
    }

    @ParameterizedTest
    @MethodSource("facings")
    void smokePuffsAtTheCutawaysWorldCenter(Direction facing, Vec3 local) {
        Vec3 center = CutawayInteractionHelper.cutawayWorldCenter(POS, facing);

        assertEquals(POS.getX() + local.x, center.x, EPSILON);
        assertEquals(POS.getY() + local.y, center.y, EPSILON);
        assertEquals(POS.getZ() + local.z, center.z, EPSILON);
    }

    @ParameterizedTest
    @MethodSource("facings")
    void cutawayCenterLiesInsideTheCutawayAClickHits(Direction facing, Vec3 local) {
        double modelX = CutawayInteractionHelper.toModelX(facing, local.x, local.z);
        double modelZ = CutawayInteractionHelper.toModelZ(facing, local.x, local.z);

        assertEquals(0.5, modelX, EPSILON);
        assertEquals(NEAR_EDGE, modelZ, EPSILON);
    }
}
