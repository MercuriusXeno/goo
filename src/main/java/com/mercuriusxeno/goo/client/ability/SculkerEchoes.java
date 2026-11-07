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
 * What a Sculker hears echoes off of. Every sound heard starts a ripple at
 * its source whose front grows outward a block and a half a tick, the way
 * Scry's sweep grows, and every block face open to air that the front
 * crosses is lit. A louder sound's ripple reaches farther and the faces it
 * lights last longer before they fade, a mob's sound alike; every living mob
 * within hearing gives off a dull ambient pulse, a quiet ripple every couple
 * of seconds. A lit face fades out over about ten seconds unless a new echo
 * lights it again, drawn weaker the farther the player stands from it and not at
 * all past the sight's reach; the brew's end forgets every face. The oldest
 * faces give way once the memory holds its most, so a loud dive never
 * outgrows it.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerEchoes {

    /** How fast a ripple's front grows, in blocks per tick. */
    static final double RIPPLE_SPEED = 1.5;
    /** How far a sound of ordinary loudness ripples before it dies, in blocks. */
    static final double RIPPLE_REACH = 12;
    /** How far any ripple reaches at the most, however loud its sound. */
    static final double LOUDEST_REACH = 32;
    /** How far from the player a lit face still shows, in blocks; it fades to nothing there. */
    static final double SIGHT_REACH = 32;
    /** Ticks a face lit by a sound of ordinary loudness takes to fade out with no new echo, about ten seconds. */
    static final int FADE_TICKS = 200;
    /** The longest any lit face lasts, however loud the sound that lit it. */
    static final int LONGEST_FADE_TICKS = 400;
    /** The quietest and loudest a sound reads, as a share of ordinary loudness. */
    static final float QUIETEST = 0.25f;
    static final float LOUDEST = 4f;
    /** Ticks between one ambient pulse of a mob within hearing and its next, about two seconds. */
    static final int PULSE_TICKS = 40;
    /** The loudness of a mob's ambient pulse, a quarter of an ordinary sound's. */
    static final float PULSE_LOUDNESS = 0.25f;
    /** How far from the player a mob's ambient pulse is heard, in blocks. */
    static final double HEARING_REACH = 32;
    /** Ticks each mob's pulse clock runs apart from the next mob's, so mobs pulse on their own beats. */
    private static final int PULSE_STAGGER = 7;

    /** The most faces the memory holds before the oldest give way. */
    static final int MOST_FACES = 32_768;
    /** The most ripples growing at once; a sound past them starts none. */
    static final int MOST_RIPPLES = 64;

    private final List<Ripple> ripples = new ArrayList<>();
    private final Map<Face, Lit> remembered = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Face, Lit> eldest) {
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
     * When an echo last lit a face and how long that lighting lasts.
     *
     * @param litAt     the game time it was lit
     * @param fadeTicks the ticks it takes to fade out
     */
    public record Lit(long litAt, int fadeTicks) {

        /**
         * @param now the game time
         * @return what is left of the lighting, 1 as it is lit, 0 once faded
         */
        float leftAt(long now) {
            return (float) Math.max(0, 1 - (double) (now - litAt) / fadeTicks);
        }

        /**
         * @return the game time the lighting has faded out by
         */
        long fadedAt() {
            return litAt + fadeTicks;
        }
    }

    /**
     * A ripple growing out of a sound's source.
     *
     * @param source    where the sound played
     * @param startTick the game time it played
     * @param reach     how far the front grows before it dies
     * @param fadeTicks the ticks each face it lights takes to fade
     */
    record Ripple(Vec3 source, long startTick, double reach, int fadeTicks) {

        /**
         * @param now the game time
         * @return how far the front stands from the source then
         */
        double radiusAt(long now) {
            return Math.min(reach, Math.max(0, now - startTick) * RIPPLE_SPEED);
        }
    }

    /**
     * Starts a ripple at the source of a sound of ordinary loudness.
     *
     * @param at   where the sound played
     * @param tick the game time it played
     */
    public void hear(Vec3 at, long tick) {
        hear(at, tick, 1f);
    }

    /**
     * Starts a ripple at a sound's source, reaching farther and lighting
     * faces for longer the louder the sound.
     *
     * @param at       where the sound played
     * @param tick     the game time it played
     * @param loudness the sound's loudness, 1 for an ordinary sound
     */
    public void hear(Vec3 at, long tick, float loudness) {
        if (ripples.size() >= MOST_RIPPLES) {
            return;
        }
        double reach = Math.min(LOUDEST_REACH, RIPPLE_REACH * share(loudness));
        ripples.add(new Ripple(at, tick, reach, fadeTicksFor(loudness)));
    }

    /**
     * Whether a mob gives off its ambient pulse on a tick.
     *
     * @param now      the game time
     * @param mobId    the mob's entity id
     * @param distance how far the mob stands from the player, in blocks
     * @return true on the mob's pulse tick while it is within hearing
     */
    public static boolean pulses(long now, int mobId, double distance) {
        return distance <= HEARING_REACH && Math.floorMod(now + (long) mobId * PULSE_STAGGER, PULSE_TICKS) == 0;
    }

    /**
     * How long what a sound reveals lasts before it fades: longer the louder the sound.
     *
     * @param loudness the sound's loudness, 1 for an ordinary sound
     * @return the ticks its reveal takes to fade
     */
    public static int fadeTicksFor(float loudness) {
        return Math.min(LONGEST_FADE_TICKS, Math.round(FADE_TICKS * share(loudness)));
    }

    private static float share(float loudness) {
        return Math.clamp(loudness, QUIETEST, LOUDEST);
    }

    /**
     * Grows every ripple to the game time given, lights each face its front
     * crossed since the tick before, lets the ripples that have reached their
     * reach die, and forgets the faces that have faded.
     *
     * @param now   the game time
     * @param isAir whether a cell holds air
     */
    public void advance(long now, Predicate<BlockPos> isAir) {
        for (Ripple ripple : ripples) {
            Lit lit = new Lit(now, ripple.fadeTicks());
            for (BlockPos cell : ScryReveal.shell(ripple.source(), ripple.radiusAt(now - 1), ripple.radiusAt(now))) {
                for (Direction side : ScryReveal.exposedFaces(isAir, cell)) {
                    light(new Face(cell.immutable(), side), lit);
                }
            }
        }
        ripples.removeIf(ripple -> ripple.radiusAt(now) >= ripple.reach());
        remembered.values().removeIf(lit -> now >= lit.fadedAt());
    }

    /**
     * Lights a face, unless it already stands lit for longer; a face lit
     * again takes its place as the newest.
     *
     * @param face the face an echo's front crossed
     * @param lit  the lighting
     */
    private void light(Face face, Lit lit) {
        Lit standing = remembered.get(face);
        if (standing == null || lit.fadedAt() >= standing.fadedAt()) {
            remembered.remove(face);
            remembered.put(face, lit);
        }
    }

    /**
     * @return each lit face and its lighting
     */
    public Map<Face, Lit> remembered() {
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
     * How strongly a lit face draws now, for a player standing at a point.
     *
     * @param face   the face
     * @param lit    its lighting
     * @param player the player's position
     * @param now    the game time
     * @return the distance strength, times what is left of its fade
     */
    public static float strength(Face face, Lit lit, Vec3 player, long now) {
        return strengthAt(Vec3.atCenterOf(face.pos()).distanceTo(player)) * lit.leftAt(now);
    }

    /**
     * How strongly something heard draws at a distance from the player.
     *
     * @param distance how far from the player it stands, in blocks
     * @return 1 at the player, falling to 0 at the sight's reach and past it
     */
    public static float strengthAt(double distance) {
        return (float) Math.max(0, 1 - distance / SIGHT_REACH);
    }
}
