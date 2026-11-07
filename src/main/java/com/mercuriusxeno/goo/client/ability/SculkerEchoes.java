package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a Sculker remembers hearing echoes off of. Every sound heard starts
 * a ripple at its source whose front grows outward a block and a half a
 * tick, the way Scry's sweep grows, and every block face open to air that
 * the front crosses is lit and remembered with the tick it was lit. A lit
 * face stays remembered while the brew stands, long after its ripple dies,
 * drawn weaker the farther the player stands from it and not at all past
 * the sight's reach; the brew's end forgets every face. The oldest faces
 * give way once the memory holds its most, so a long dive never outgrows it.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerEchoes {

    /** How fast a ripple's front grows, in blocks per tick. */
    static final double RIPPLE_SPEED = 1.5;
    /** How far a ripple's front reaches before it dies, in blocks. */
    static final double RIPPLE_REACH = 12;
    /** How far from the player a remembered face still shows, in blocks; it fades to nothing there. */
    static final double SIGHT_REACH = 32;
    /** The most faces the memory holds before the oldest give way. */
    static final int MOST_FACES = 32_768;
    /** The most ripples growing at once; a sound past them starts none. */
    static final int MOST_RIPPLES = 64;

    private final List<Ripple> ripples = new ArrayList<>();
    private final Map<Face, Long> remembered = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Face, Long> eldest) {
            return size() > MOST_FACES;
        }
    };

    /**
     * One block face an echo has touched.
     *
     * @param pos  the block
     * @param side the side of it open to air
     */
    public record Face(BlockPos pos, Direction side) {
    }

    /**
     * A ripple growing out of a sound's source.
     *
     * @param source    where the sound played
     * @param startTick the game time it played
     */
    record Ripple(Vec3 source, long startTick) {

        /**
         * @param now the game time
         * @return how far the front stands from the source then
         */
        double radiusAt(long now) {
            return Math.min(RIPPLE_REACH, Math.max(0, now - startTick) * RIPPLE_SPEED);
        }
    }

    /**
     * Starts a ripple at a sound's source.
     *
     * @param at   where the sound played
     * @param tick the game time it played
     */
    public void hear(Vec3 at, long tick) {
        if (ripples.size() < MOST_RIPPLES) {
            ripples.add(new Ripple(at, tick));
        }
    }

    /**
     * Grows every ripple to the game time given, remembers each face its
     * front crossed since the tick before, and lets the ripples that have
     * reached their reach die.
     *
     * @param now   the game time
     * @param isAir whether a cell holds air
     */
    public void advance(long now, Predicate<BlockPos> isAir) {
        for (Ripple ripple : ripples) {
            for (BlockPos cell : ScryReveal.shell(ripple.source(), ripple.radiusAt(now - 1), ripple.radiusAt(now))) {
                for (Direction side : ScryReveal.exposedFaces(isAir, cell)) {
                    remembered.putIfAbsent(new Face(cell.immutable(), side), now);
                }
            }
        }
        ripples.removeIf(ripple -> ripple.radiusAt(now) >= RIPPLE_REACH);
    }

    /**
     * @return each remembered face and the game time an echo first lit it
     */
    public Map<Face, Long> remembered() {
        return Collections.unmodifiableMap(remembered);
    }

    /**
     * @return the ripples growing now
     */
    List<Ripple> ripples() {
        return Collections.unmodifiableList(ripples);
    }

    /** Forgets every face and ripple, as the brew's end does. */
    public void clear() {
        remembered.clear();
        ripples.clear();
    }

    /**
     * How strongly a remembered face draws for a player standing at a point.
     *
     * @param face   the face
     * @param player the player's position
     * @return 1 at the player, falling to 0 at the sight's reach and past it
     */
    public static float strength(Face face, Vec3 player) {
        return strengthAt(Vec3.atCenterOf(face.pos()).distanceTo(player));
    }

    /**
     * How strongly something remembered draws at a distance from the player.
     *
     * @param distance how far from the player it stands, in blocks
     * @return 1 at the player, falling to 0 at the sight's reach and past it
     */
    public static float strengthAt(double distance) {
        return (float) Math.max(0, 1 - distance / SIGHT_REACH);
    }
}
