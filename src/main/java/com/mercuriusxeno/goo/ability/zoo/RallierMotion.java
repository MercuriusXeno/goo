package com.mercuriusxeno.goo.ability.zoo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Each rallier's horizontal travel as the server sees it: the position change
 * per tick, smoothed over a few ticks, read once a tick however many of its
 * minions ask. A player's own delta movement reads nothing useful on the
 * server, so the travel is read from where the rallier stands tick to tick.
 * unengaged-minions-move-where-you-move
 */
final class RallierMotion {

    /** Each rallier's last sighting, held weakly so a rallier gone from the world drops out. */
    private static final Map<Entity, Sighting> SIGHTINGS = new WeakHashMap<>();

    private RallierMotion() {
    }

    /**
     * The rallier's smoothed velocity this tick, folding in its step since the
     * last tick it was read; the first read of a rallier answers standing.
     *
     * @param rallier  the rallier
     * @param gameTime the level's game time this tick
     * @return the smoothed velocity, in blocks a tick
     */
    static Vec3 velocityOf(Entity rallier, long gameTime) {
        Vec3 position = rallier.position();
        Sighting last = SIGHTINGS.get(rallier);
        if (last != null && last.gameTime() == gameTime) {
            return last.velocity();
        }
        Vec3 velocity = last == null || gameTime < last.gameTime() ? Vec3.ZERO
                : RallyFollow.smoothVelocity(last.velocity(),
                        position.subtract(last.position()).scale(1.0 / (gameTime - last.gameTime())));
        SIGHTINGS.put(rallier, new Sighting(position, velocity, gameTime));
        return velocity;
    }

    /**
     * Where a rallier stood when last read, its smoothed velocity then, and the tick.
     *
     * @param position the rallier's position
     * @param velocity the smoothed velocity, in blocks a tick
     * @param gameTime the level's game time of the read
     */
    private record Sighting(Vec3 position, Vec3 velocity, long gameTime) {
    }
}
