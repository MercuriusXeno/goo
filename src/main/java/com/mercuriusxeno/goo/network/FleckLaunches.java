package com.mercuriusxeno.goo.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The flecks a slinging release has yet to send from the hand, each on its
 * own tick, so a sweep's flecks leave one after another. Each server holds
 * one, so a sweep still leaving ends with the server
 * (decision type-package-and-per-server-holders).
 * decision shards-sling-then-morph-to-flechettes
 */
public final class FleckLaunches {

    private final List<Pending> pending = new ArrayList<>();

    /**
     * Queues a fleck to leave the hand on a tick.
     *
     * @param launchTick the server tick it leaves on
     * @param launch     what sends it
     */
    public void enqueue(int launchTick, Runnable launch) {
        pending.add(new Pending(launchTick, launch));
    }

    /**
     * Whether any fleck has yet to leave.
     *
     * @return true while a sweep is leaving
     */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /**
     * Sends every fleck whose tick has come, in the order they were queued.
     *
     * @param currentTick the current server tick
     */
    public void drainArrived(int currentTick) {
        List<Runnable> ready = new ArrayList<>();
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending next = it.next();
            if (currentTick >= next.launchTick) {
                ready.add(next.launch);
                it.remove();
            }
        }
        ready.forEach(Runnable::run);
    }

    /** Drops every fleck yet to leave, as a server stop does. */
    public void clear() {
        pending.clear();
    }

    private record Pending(int launchTick, Runnable launch) {
    }
}
