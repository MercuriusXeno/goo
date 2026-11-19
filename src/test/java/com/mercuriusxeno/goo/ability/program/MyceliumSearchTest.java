package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The direct search joins mycelial blocks at faces, edges and corners, parts
 * a line at a gap of one block, and reaches no further than its range from
 * the destination (decision transmit-reaches-only-connected-mycelial-blocks-you-touch).
 */
class MyceliumSearchTest {

    private static final double RANGE = 64;

    private static Set<BlockPos> line(int fromX, int toX) {
        Set<BlockPos> blocks = new HashSet<>();
        for (int x = fromX; x <= toX; x++) {
            blocks.add(new BlockPos(x, 0, 0));
        }
        return blocks;
    }

    @Nested
    class Joins {

        @Test
        void anUnbrokenLineJoinsItsEnds() {
            Set<BlockPos> line = line(0, 15);
            assertTrue(MyceliumSearch.connects(line::contains, List.of(BlockPos.ZERO), new BlockPos(15, 0, 0), RANGE));
        }

        @Test
        void aStepUpAShapeJoinsAtTheEdge() {
            Set<BlockPos> slope = Set.of(BlockPos.ZERO, new BlockPos(1, 1, 0), new BlockPos(2, 2, 0));
            assertTrue(MyceliumSearch.connects(slope::contains, List.of(BlockPos.ZERO), new BlockPos(2, 2, 0), RANGE));
        }

        @Test
        void blocksMeetingAtACornerJoin() {
            Set<BlockPos> corner = Set.of(BlockPos.ZERO, new BlockPos(1, 1, 1));
            assertTrue(MyceliumSearch.connects(corner::contains, List.of(BlockPos.ZERO), new BlockPos(1, 1, 1), RANGE));
        }

        @Test
        void anyTouchedOriginJoinedToTheDestinationCounts() {
            Set<BlockPos> line = line(5, 9);
            List<BlockPos> touched = List.of(new BlockPos(0, 5, 0), new BlockPos(5, 0, 0));
            assertTrue(MyceliumSearch.connects(line::contains, touched, new BlockPos(9, 0, 0), RANGE));
        }
    }

    @Nested
    class Parts {

        @Test
        void oneMissingBlockPartsALine() {
            Set<BlockPos> line = line(0, 15);
            line.remove(new BlockPos(7, 0, 0));
            assertFalse(MyceliumSearch.connects(line::contains, List.of(BlockPos.ZERO), new BlockPos(15, 0, 0), RANGE));
        }

        @Test
        void anOriginThatIsNotMycelialJoinsNothing() {
            Set<BlockPos> line = line(1, 15);
            assertFalse(MyceliumSearch.connects(line::contains, List.of(BlockPos.ZERO), new BlockPos(15, 0, 0), RANGE));
        }

        @Test
        void aPathRunningPastTheRangeFromTheDestinationDoesNotJoin() {
            Set<BlockPos> line = line(0, 20);
            assertFalse(MyceliumSearch.connects(line::contains, List.of(BlockPos.ZERO), new BlockPos(20, 0, 0), 10));
        }
    }
}
