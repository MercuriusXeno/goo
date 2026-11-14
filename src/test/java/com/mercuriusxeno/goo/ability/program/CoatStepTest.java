package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coat step reads the ticks of a stream's hold, which needs the channel,
 * and refuses a step naming none (decision bounce-leaves-the-mod-and-goop-stands-alone);
 * a drip's strike feeds the cell over a struck top or the slime it struck,
 * and a struck side feeds nothing (decision goop-and-slick-coat-lands-where-each-drip-arc-strikes).
 */
class CoatStepTest {

    @Test
    void ticksDecodeAndNeedTheChannel() {
        assertEquals(Set.of(HostCapability.CHANNEL), decoded("{\"ticks\": 20}").getOrThrow().requires());
    }

    @Test
    void noTicksRefuse() {
        assertTrue(decoded("{}").isError(), "a coat step naming no ticks should refuse");
    }

    private static DataResult<CoatStep> decoded(String json) {
        return CoatStep.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Nested
    class StrikeFeedsACell {

        private static final BlockPos FLOOR = new BlockPos(8, 0, 2);
        private static final BlockPos SLIME = new BlockPos(5, 1, 2);
        private static final BlockPos WALL = new BlockPos(3, 1, 2);

        /** Open cells stand over every top but the slime's; the slime cell alone thickens. */
        private final CoatStep.CoatCells cells = new CoatStep.CoatCells() {
            @Override
            public boolean thickens(BlockPos pos) {
                return pos.equals(SLIME);
            }

            @Override
            public boolean takesACoat(BlockPos pos) {
                return !pos.equals(SLIME) && !pos.equals(SLIME.above());
            }
        };

        private static CoatRay.BlockStrike strike(BlockPos block, Direction face) {
            return new CoatRay.BlockStrike(Vec3.atCenterOf(block).relative(face, 0.5), block, face);
        }

        @Test
        void aStrikeOnAFloorsTopFeedsTheCellOverIt() {
            assertEquals(Optional.of(FLOOR.above()), CoatStep.cellFedBy(strike(FLOOR, Direction.UP), cells));
        }

        @Test
        void aStrikeOnSlimeFeedsThatSlime() {
            assertEquals(Optional.of(SLIME), CoatStep.cellFedBy(strike(SLIME, Direction.UP), cells));
        }

        @Test
        void aStrikeOnAWallsSideFeedsNothing() {
            assertEquals(Optional.empty(), CoatStep.cellFedBy(strike(WALL, Direction.WEST), cells));
        }
    }
}
