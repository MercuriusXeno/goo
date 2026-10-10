package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Walks the cells of a sphere core outward a shell at a time, resuming from
 * a cursor, so a walk of any radius does about a budget's work a tick: shell
 * k holds the cells whose squared distance from the center lies in
 * [k squared, (k+1) squared), and each shell is walked column by column, each
 * column's cells found from its distance rather than by scanning the cube
 * (decision black-hole-leaves-a-compression-sphere). The center's own cell,
 * shell 0, is never walked.
 */
public final class ShellWalk {

    /** The cursor a walk starts from: the first column of shell 1. */
    public static final Cursor START = new Cursor(1, -1, -1);

    private ShellWalk() {
    }

    /**
     * Where a walk stands: the next column to visit.
     *
     * @param shell the shell being walked
     * @param x     the column's x offset from the center
     * @param y     the column's y offset from the center
     */
    public record Cursor(int shell, int x, int y) {
    }

    /**
     * Visits cells of the sphere from the cursor on, column by column, until
     * the work spent reaches the budget or the sphere is done; a visited
     * column and each cell visited count one unit of work each, and the
     * column the budget runs out in is finished, so a call does at most a
     * budget and one column's work.
     *
     * @param center the sphere center
     * @param radius the sphere radius in whole blocks
     * @param from   the cursor to resume from
     * @param budget the units of work this call spends
     * @param visit  what each cell visited is handed to
     * @return the cursor to resume from, or null once every cell is visited
     */
    public static @Nullable Cursor walk(BlockPos center, int radius, Cursor from, int budget,
                                        Consumer<BlockPos> visit) {
        int shell = from.shell();
        int x = from.x();
        int y = from.y();
        int spent = 0;
        while (shell <= radius && spent < budget) {
            spent += 1 + visitColumn(center, radius, shell, x, y, visit);
            y++;
            if (y > shell) {
                y = -shell;
                x++;
            }
            if (x > shell) {
                shell++;
                x = -shell;
                y = -shell;
            }
        }
        return shell > radius ? null : new Cursor(shell, x, y);
    }

    /**
     * Visits one column's cells in a shell: every z whose cell's squared
     * distance lies in the shell and within the sphere.
     *
     * @param center the sphere center
     * @param radius the sphere radius in whole blocks
     * @param shell  the shell walked
     * @param x      the column's x offset from the center
     * @param y      the column's y offset from the center
     * @param visit  what each cell visited is handed to
     * @return the cells visited
     */
    private static int visitColumn(BlockPos center, int radius, int shell, int x, int y, Consumer<BlockPos> visit) {
        int across = x * x + y * y;
        int lowest = shell * shell - across;
        int highest = Math.min((shell + 1) * (shell + 1) - 1, radius * radius) - across;
        if (highest < 0) {
            return 0;
        }
        int nearZ = lowest <= 0 ? 0 : ceilSqrt(lowest);
        int farZ = floorSqrt(highest);
        int visited = 0;
        for (int z = nearZ; z <= farZ; z++) {
            visit.accept(center.offset(x, y, z));
            visited++;
            if (z != 0) {
                visit.accept(center.offset(x, y, -z));
                visited++;
            }
        }
        return visited;
    }

    /**
     * The largest whole number whose square is at most n.
     *
     * @param n a non-negative number
     * @return its floor square root
     */
    static int floorSqrt(int n) {
        int root = (int) Math.sqrt(n);
        while ((long) (root + 1) * (root + 1) <= n) {
            root++;
        }
        while ((long) root * root > n) {
            root--;
        }
        return root;
    }

    private static int ceilSqrt(int n) {
        int root = floorSqrt(n);
        return root * root == n ? root : root + 1;
    }
}
