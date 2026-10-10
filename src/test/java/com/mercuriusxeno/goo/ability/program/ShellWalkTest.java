package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityMath;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A black hole takes its sphere core outward a shell at a time from a
 * cursor: every cell but the marker's own exactly once, no shell before the
 * one inside it, and no tick past a budget and a column's work however big
 * the hole (decision black-hole-leaves-a-compression-sphere).
 */
class ShellWalkTest {

    private static final BlockPos CENTER = new BlockPos(10, 64, -3);
    private static final int RADIUS = 6;
    private static final int SMALL_BUDGET = 37;
    private static final int BUDGET = 2048;
    private static final int HUGE_RADIUS = 100;

    @Test
    void aWalkInSmallStepsVisitsTheWholeSphereButTheMarkersOwnOnce() {
        List<BlockPos> visited = walkAll(RADIUS, SMALL_BUDGET);
        Set<BlockPos> sphere = new HashSet<>();
        AbilityMath.forEachInSphere(CENTER, RADIUS, cell -> sphere.add(cell.immutable()));
        sphere.remove(CENTER);
        assertEquals(sphere.size(), visited.size());
        assertEquals(sphere, new HashSet<>(visited));
        assertFalse(visited.contains(CENTER));
    }

    @Test
    void theWalkRunsCoreOutwardShellByShell() {
        List<BlockPos> visited = walkAll(RADIUS, SMALL_BUDGET);
        for (int index = 1; index < visited.size(); index++) {
            assertTrue(shellOf(visited.get(index - 1)) <= shellOf(visited.get(index)),
                    "cell " + index + " lies in a shell nearer the core than the one before it");
        }
        assertEquals(1, shellOf(visited.getFirst()));
    }

    @Test
    void aHugeHoleSpendsNoMoreThanItsBudgetAndAColumnOnItsFirstTick() {
        int[] visits = new int[1];
        ShellWalk.Cursor next = ShellWalk.walk(CENTER, HUGE_RADIUS, ShellWalk.START, BUDGET, cell -> visits[0]++);
        assertNotNull(next);
        int longestColumn = 2 * ShellWalk.floorSqrt(2 * HUGE_RADIUS + 1) + 1;
        assertTrue(visits[0] <= BUDGET + longestColumn, "the first tick visited " + visits[0] + " cells");
    }

    private static List<BlockPos> walkAll(int radius, int budget) {
        List<BlockPos> visited = new ArrayList<>();
        ShellWalk.Cursor cursor = ShellWalk.START;
        while (cursor != null) {
            cursor = ShellWalk.walk(CENTER, radius, cursor, budget, cell -> visited.add(cell.immutable()));
        }
        assertNull(cursor);
        return visited;
    }

    private static int shellOf(BlockPos cell) {
        return ShellWalk.floorSqrt((int) cell.distSqr(CENTER));
    }
}
