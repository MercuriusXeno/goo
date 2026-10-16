package com.mercuriusxeno.goo.client.ability;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The GPU time one of the Unmake drink's passes takes, read back from timer
 * queries a frame or more after each pass, so the debug log can say what each
 * of the splat, the blur and the shade costs the GPU rather than leaving the
 * frame rate to be guessed at. It runs only while the drink's debug line is
 * logged.
 * decision unmake-streams-collapse-into-a-central-trunk
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
final class DrinkGpuTimer {

    /** The splat pass's time. */
    static final DrinkGpuTimer SPLAT = new DrinkGpuTimer();
    /** The blur pass's time. */
    static final DrinkGpuTimer BLUR = new DrinkGpuTimer();
    /** The shade pass's time. */
    static final DrinkGpuTimer SHADE = new DrinkGpuTimer();

    private static final double MILLIS_PER_NANO = 1e-6;
    private static final int MOST_PENDING = 16;
    /** No query running. */
    private static final int NONE = -1;
    private final Deque<Integer> pending = new ArrayDeque<>();
    private final Deque<Integer> free = new ArrayDeque<>();
    private double lastMillis;
    private int running = NONE;

    private DrinkGpuTimer() {
    }

    /** Starts timing a pass, unless too many earlier passes are still unread. */
    void begin() {
        if (running >= 0 || pending.size() >= MOST_PENDING) {
            return;
        }
        running = free.isEmpty() ? GL15.glGenQueries() : free.pop();
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, running);
    }

    /** Ends timing the pass begun. */
    void end() {
        if (running < 0) {
            return;
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        pending.add(running);
        running = NONE;
    }

    /**
     * @return the mean GPU milliseconds of a pass among those read back since the last call, or the last such
     *         reading while none has come back
     */
    double millis() {
        double read = 0;
        int count = 0;
        while (!pending.isEmpty() && GL15.glGetQueryObjecti(pending.peek(), GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            int query = pending.pop();
            read += GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT) * MILLIS_PER_NANO;
            free.push(query);
            count++;
        }
        if (count > 0) {
            lastMillis = read / count;
        }
        return lastMillis;
    }
}
