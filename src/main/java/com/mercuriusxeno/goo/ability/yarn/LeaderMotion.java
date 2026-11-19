package com.mercuriusxeno.goo.ability.yarn;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Each leader's horizontal travel as the server sees it: the position change
 * per tick, smoothed over a few ticks, read once a tick however many of its
 * minions ask. A player's own delta movement reads nothing useful on the
 * server, so the travel is read from where the leader stands tick to tick.
 * unengaged-minions-move-where-you-move
 */
final class LeaderMotion {

    /** Each leader's last sighting, held weakly so a leader gone from the world drops out. */
    private static final Map<Entity, Sighting> SIGHTINGS = new WeakHashMap<>();

    private LeaderMotion() {
    }

    /**
     * The leader's smoothed velocity this tick, folding in its step since the
     * last tick it was read; the first read of a leader answers standing.
     *
     * @param leader  the leader
     * @param gameTime the level's game time this tick
     * @return the smoothed velocity, in blocks a tick
     */
    static Vec3 velocityOf(Entity leader, long gameTime) {
        Vec3 position = leader.position();
        Sighting last = SIGHTINGS.get(leader);
        if (last != null && last.gameTime() == gameTime) {
            return last.velocity();
        }
        Vec3 velocity = last == null || gameTime < last.gameTime() ? Vec3.ZERO
                : LeadFollow.smoothVelocity(last.velocity(),
                        position.subtract(last.position()).scale(1.0 / (gameTime - last.gameTime())));
        SIGHTINGS.put(leader, new Sighting(position, velocity, gameTime));
        return velocity;
    }

    /**
     * Where a leader stood when last read, its smoothed velocity then, and the tick.
     *
     * @param position the leader's position
     * @param velocity the smoothed velocity, in blocks a tick
     * @param gameTime the level's game time of the read
     */
    private record Sighting(Vec3 position, Vec3 velocity, long gameTime) {
    }
}
