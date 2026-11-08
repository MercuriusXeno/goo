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
 * What a Sculker hears echoes off of. Every sound heard sends three rings
 * out of its source a quarter second apart, each sweeping eight blocks a
 * second, and every block face open to air that a ring crosses is lit. An
 * ordinary sound's rings reach four blocks and the loudest's twelve, and
 * the faces a louder sound lights last longer before they fade. A face
 * shows bright as a ring crosses it, then faintly in its wake, fading out
 * over about three seconds, six for the loudest, unless a new ring lights
 * it again. A mob that makes a sound outlines for about a second, longer
 * for a louder one. What is heard draws fainter the farther the player
 * stands from it, down to a fifth at the edge of render distance and not
 * at all past it; the brew's end forgets every face. The oldest faces give
 * way once the memory holds its most, so a loud dive never outgrows it.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerEchoes {

    /** How fast a ring sweeps out of its source, in blocks per tick: eight blocks a second. */
    static final double RING_SPEED = 0.4;
    /** The rings each sound sends out. */
    static final int RINGS = 3;
    /** Ticks between one ring of a sound and its next, a quarter second. */
    static final int RING_GAP_TICKS = 5;
    /** How far a sound of ordinary loudness ripples before it dies, in blocks. */
    static final double RIPPLE_REACH = 4;
    /** How far any ripple reaches at the most, however loud its sound. */
    static final double LOUDEST_REACH = 12;
    /** Ticks a face lit by a sound of ordinary loudness takes to fade out with no new ring, about three seconds. */
    static final int FADE_TICKS = 60;
    /** The longest any lit face lasts, however loud the sound that lit it, about six seconds. */
    static final int LONGEST_FADE_TICKS = 120;
    /** Ticks a face shows bright after a ring crosses it, before it sinks to its wake. */
    static final float RING_TICKS = 3f;
    /** How brightly a face glows in a ring's wake, as a share of the ring's own glow. */
    static final float WAKE_SHARE = 0.3f;
    /** Ticks a mob that made a sound of ordinary loudness stays outlined, a second. */
    static final int OUTLINE_TICKS = 20;
    /** The longest any mob stays outlined, however loud its sound, two seconds. */
    static final int LONGEST_OUTLINE_TICKS = 40;
    /** How strongly what is heard at the edge of render distance draws, as a share of beside the player. */
    static final float FARTHEST_SHARE = 0.2f;
    /** The quietest and loudest a sound reads, as a share of ordinary loudness. */
    static final float QUIETEST = 0.25f;
    static final float LOUDEST = 4f;

    /** The most faces the memory holds before the oldest give way. */
    static final int MOST_FACES = 32_768;
    /** The most rings growing at once; a sound past them starts none. */
    static final int MOST_RIPPLES = 192;

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
     * One ring sweeping out of a sound's source.
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
            return Math.min(reach, Math.max(0, now - startTick) * RING_SPEED);
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
     * Sends a sound's rings out of its source a quarter second apart,
     * reaching farther and lighting faces for longer the louder the sound.
     *
     * @param at       where the sound played
     * @param tick     the game time it played
     * @param loudness the sound's loudness, 1 for an ordinary sound
     */
    public void hear(Vec3 at, long tick, float loudness) {
        if (ripples.size() + RINGS > MOST_RIPPLES) {
            return;
        }
        double reach = Math.min(LOUDEST_REACH, RIPPLE_REACH * share(loudness));
        for (int ring = 0; ring < RINGS; ring++) {
            ripples.add(new Ripple(at, tick + (long) ring * RING_GAP_TICKS, reach, fadeTicksFor(loudness)));
        }
    }

    /**
     * How long a mob that made a sound stays outlined: a second for an
     * ordinary sound, longer for a louder one up to two.
     *
     * @param loudness the sound's loudness, 1 for an ordinary sound
     * @return the ticks its outline lasts
     */
    public static int outlineTicksFor(float loudness) {
        return Math.min(LONGEST_OUTLINE_TICKS, Math.round(OUTLINE_TICKS * share(loudness)));
    }

    /**
     * What is left of a mob's outline: full as it makes its sound, fading to nothing.
     *
     * @param heardAt the game time its sound played
     * @param ticks   how long its outline lasts
     * @param now     the game time
     * @return 1 as it is heard, falling to 0 once its outline has faded
     */
    public static float outlineLeftAt(long heardAt, int ticks, long now) {
        return (float) Math.clamp(1 - (double) (now - heardAt) / ticks, 0, 1);
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
            if (ripple.startTick() >= now) {
                continue;
            }
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
     * @return the rings growing now, and those yet to set out
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
     * @param reach  the render distance, in blocks
     * @return the distance strength, times the face's glow
     */
    public static float strength(Face face, Lit lit, Vec3 player, long now, double reach) {
        return strengthAt(Vec3.atCenterOf(face.pos()).distanceTo(player), reach) * glowAt(lit, now);
    }

    /**
     * How brightly a lit face glows now: full as a ring crosses it, sinking
     * over a few ticks to its faint wake, which fades out with the lighting.
     *
     * @param lit its lighting
     * @param now the game time
     * @return the glow, 1 on the ring, 0 once faded
     */
    public static float glowAt(Lit lit, long now) {
        float ring = Math.max(0f, 1f - (now - lit.litAt()) / RING_TICKS);
        return Math.max(ring, WAKE_SHARE * lit.leftAt(now));
    }

    /**
     * How strongly something heard draws at a distance from the player.
     *
     * @param distance how far from the player it stands, in blocks
     * @param reach    the render distance, in blocks
     * @return 1 at the player, falling evenly to a fifth at the edge of render distance, 0 past it
     */
    public static float strengthAt(double distance, double reach) {
        if (distance > reach) {
            return 0f;
        }
        return (float) (1 - (1 - FARTHEST_SHARE) * distance / reach);
    }
}
