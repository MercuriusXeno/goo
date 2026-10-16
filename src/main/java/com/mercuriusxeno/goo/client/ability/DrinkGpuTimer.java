package com.mercuriusxeno.goo.client.ability;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The GPU time the Unmake drink's passes take, read back from timer queries
 * a frame or more after each pass, so the debug log can say what the drink
 * costs the GPU rather than leaving the frame rate to be guessed at. It runs
 * only while the drink's debug line is logged.
 * decision unmake-streams-collapse-into-a-central-trunk
 */
final class DrinkGpuTimer {

    private static final double MILLIS_PER_NANO = 1e-6;
    private static final int MOST_PENDING = 16;
    private static final Deque<Integer> PENDING = new ArrayDeque<>();
    private static final Deque<Integer> FREE = new ArrayDeque<>();
    private static double lastMillis;
    /** No query running. */
    private static final int NONE = -1;
    private static int running = NONE;

    private DrinkGpuTimer() {
    }

    /** Starts timing a pass, unless too many earlier passes are still unread. */
    static void begin() {
        if (running >= 0 || PENDING.size() >= MOST_PENDING) {
            return;
        }
        running = FREE.isEmpty() ? GL15.glGenQueries() : FREE.pop();
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, running);
    }

    /** Ends timing the pass begun. */
    static void end() {
        if (running < 0) {
            return;
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        PENDING.add(running);
        running = NONE;
    }

    /**
     * @return the mean GPU milliseconds of a pass among those read back since the last call, or the last such
     *         reading while none has come back
     */
    static double millis() {
        double read = 0;
        int count = 0;
        while (!PENDING.isEmpty() && GL15.glGetQueryObjecti(PENDING.peek(), GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            int query = PENDING.pop();
            read += GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT) * MILLIS_PER_NANO;
            FREE.push(query);
            count++;
        }
        if (count > 0) {
            lastMillis = read / count;
        }
        return lastMillis;
    }
}
