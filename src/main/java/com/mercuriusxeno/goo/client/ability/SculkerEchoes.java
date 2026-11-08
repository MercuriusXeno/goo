package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a Sculker hears echoes off of. Every sound heard sends two rings
 * out of its source a quarter second apart, each sweeping eight blocks a
 * second, and every block open to air that a ring crosses is lit, full
 * at the source and weaker the farther the ring has travelled, to nothing at
 * the end of the sound's range, sixteen blocks for an ordinary sound and
 * farther for a louder one, a louder sound lighting stronger. Sounds that meet on a face add up, each sound once
 * however many of its rings cross it, to twice a full light at most, and a
 * face's light falls at a steady rate, a full light within three seconds,
 * so a block fades by how recently and how strongly it was lit: it glows as
 * bright as the sound was as it reached it and decays from there. A mob that
 * makes a sound outlines for three seconds, longer for a louder one up to
 * six. What is heard draws fainter the farther the player stands from it,
 * down to a fifth at the edge of render distance and not at all past it;
 * the brew's end forgets every face. The faces whose light runs out soonest
 * give way once the memory holds its most, so a loud dive never outgrows it
 * and weak sounds never push out what a strong one lit.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerEchoes {

    /** How fast a ring sweeps out of its source, in blocks per tick: eight blocks a second. */
    static final double RING_SPEED = 0.4;
    /** The rings each sound sends out. */
    static final int RINGS = 2;
    /** Ticks between one ring of a sound and its next, a quarter second. */
    static final int RING_GAP_TICKS = 5;
    /** How far an ordinary sound carries, in blocks, as Minecraft attenuates it; a louder one carries farther. */
    static final double ORDINARY_RANGE = 16;
    /** Ticks a full light takes to fall to nothing with no new sound, three seconds. */
    static final int FADE_TICKS = 60;
    /** The most light a face holds, however many sounds meet on it: twice a full light. */
    static final float MOST_LIGHT = 2f;
    /** Ticks a mob that made a sound of ordinary loudness stays outlined, three seconds. */
    static final int OUTLINE_TICKS = 60;
    /** The longest any mob stays outlined, however loud its sound, six seconds. */
    static final int LONGEST_OUTLINE_TICKS = 120;
    /** How strongly what is heard at the edge of render distance draws, as a share of beside the player. */
    static final float FARTHEST_SHARE = 0.2f;
    /** The quietest and loudest a sound reads, as a share of ordinary loudness. */
    static final float QUIETEST = 0.25f;
    static final float LOUDEST = 4f;

    /** The most faces the memory holds before the faintest give way. */
    static final int MOST_FACES = 32_768;
    /** The most rings growing at once; a sound past them starts none. */
    static final int MOST_RIPPLES = 192;

    private final List<Ripple> ripples = new ArrayList<>();
    private final int mostFaces;
    private final Map<BlockPos, Lit> remembered = new LinkedHashMap<>();

    /** A memory holding the most faces it ever needs. */
    public SculkerEchoes() {
        this(MOST_FACES);
    }

    /**
     * @param mostFaces the most faces the memory holds before the faintest give way
     */
    SculkerEchoes(int mostFaces) {
        this.mostFaces = mostFaces;
    }

    /**
     * How a block stands lit: the light it held when a ring last reached it,
     * which falls at a steady rate from then.
     *
     * @param litAt the game time a ring last reached it
     * @param light the light it held then, up to twice a full light
     */
    public record Lit(long litAt, float light) {

        /**
         * @param now the game time
         * @return the light left on the block, falling a full light every three seconds, 0 once faded
         */
        float levelAt(long now) {
            return Math.max(0f, light - (float) (now - litAt) / FADE_TICKS);
        }

        /**
         * @return the game time the block has faded out by
         */
        long fadedAt() {
            return litAt + (long) Math.ceil(light * FADE_TICKS);
        }
    }

    /**
     * One ring sweeping out of a sound's source.
     *
     * @param source    where the sound played
     * @param startTick the game time it sets out
     * @param reach     how far it sweeps before it dies, where its light has fallen to nothing
     * @param strength  how strongly it lights a face at the source
     * @param adds      whether it adds its light to the blocks it crosses; a sound's first ring does
     *                  and its later rings do not, so each sound counts once
     */
    record Ripple(Vec3 source, long startTick, double reach, float strength, boolean adds) {

        /**
         * @param now the game time
         * @return how far the front stands from the source then
         */
        double radiusAt(long now) {
            return Math.min(reach, Math.max(0, now - startTick) * RING_SPEED);
        }

        /**
         * How strongly the ring lights a cell it crosses: full at the
         * source, falling evenly to nothing at its reach.
         *
         * @param cell the cell
         * @return its light there
         */
        float lightAt(BlockPos cell) {
            return (float) Math.max(0, strength * (1 - Vec3.atCenterOf(cell).distanceTo(source) / reach));
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
     * Sends a sound's rings out across the range Minecraft carries a sound
     * of its loudness.
     *
     * @param at       where the sound played
     * @param tick     the game time it played
     * @param loudness the sound's loudness, 1 for an ordinary sound
     */
    public void hear(Vec3 at, long tick, float loudness) {
        hear(at, tick, loudness, rangeFor(loudness));
    }

    /**
     * How far Minecraft carries a sound of a loudness with its ordinary
     * attenuation: sixteen blocks, times its loudness past ordinary.
     *
     * @param loudness the sound's loudness, 1 for an ordinary sound
     * @return its range, in blocks
     */
    public static double rangeFor(float loudness) {
        return ORDINARY_RANGE * Math.max(1f, loudness);
    }

    /**
     * Sends a sound's rings out of its source a quarter second apart, across
     * its whole range, their power falling evenly from full at the source to
     * nothing at the range's end, a louder sound lighting stronger.
     *
     * @param at       where the sound played
     * @param tick     the game time it played
     * @param loudness the sound's loudness, 1 for an ordinary sound
     * @param range    how far the sound carries, in blocks
     */
    public void hear(Vec3 at, long tick, float loudness, double range) {
        if (ripples.size() + RINGS > MOST_RIPPLES) {
            return;
        }
        double reach = range;
        float strength = Math.min(MOST_LIGHT, share(loudness));
        for (int ring = 0; ring < RINGS; ring++) {
            ripples.add(new Ripple(at, tick + (long) ring * RING_GAP_TICKS, reach, strength, ring == 0));
        }
    }

    /**
     * How long a mob that made a sound stays outlined: three seconds for an
     * ordinary sound, longer for a louder one up to six.
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

    private static float share(float loudness) {
        return Math.clamp(loudness, QUIETEST, LOUDEST);
    }

    /**
     * Grows every ring to the game time given, lights each face its front
     * crossed since the tick before, lets the rings that have reached their
     * reach die, and forgets the faces that have faded.
     *
     * @param now   the game time
     * @param isAir whether a cell holds air
     */
    public void advance(long now, Predicate<BlockPos> isAir) {
        advance(now, isAir.negate(), isAir);
    }

    /**
     * Grows every ring to the game time given, lights each block its front
     * crossed since the tick before that can light and stands beside a
     * block sound passes through, lets the rings that have reached their
     * reach die, and forgets the blocks that have faded.
     *
     * @param now         the game time
     * @param lights      whether a block lights where an echo reaches it: one with a collision shape
     * @param passesSound whether sound passes through a block: air, or a block that does not fill its cell
     */
    public void advance(long now, Predicate<BlockPos> lights, Predicate<BlockPos> passesSound) {
        for (Ripple ripple : ripples) {
            if (ripple.startTick() >= now) {
                continue;
            }
            for (BlockPos cell : ScryReveal.shell(ripple.source(), ripple.radiusAt(now - 1), ripple.radiusAt(now))) {
                float light = ripple.lightAt(cell);
                if (lights.test(cell) && besideOpenSpace(cell, passesSound)) {
                    light(cell.immutable(), now, light, ripple.adds());
                }
            }
        }
        ripples.removeIf(ripple -> ripple.radiusAt(now) >= ripple.reach());
        remembered.values().removeIf(lit -> now >= lit.fadedAt());
        forgetTheFaintest();
    }

    private static boolean besideOpenSpace(BlockPos cell, Predicate<BlockPos> passesSound) {
        for (Direction side : Direction.values()) {
            if (passesSound.test(cell.relative(side))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the memory to its most: past it, the faces whose light runs out
     * soonest give way, so a stream of weak sounds never pushes out a face a
     * strong one lit, however long ago that was.
     */
    private void forgetTheFaintest() {
        int over = remembered.size() - mostFaces;
        if (over <= 0) {
            return;
        }
        remembered.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().fadedAt()))
                .limit(over)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(remembered::remove);
    }

    /**
     * Lights a block a ring crossed: a sound's first ring adds its light to
     * what is left there, up to twice a full light, and its later rings
     * leave it as it stands.
     *
     * @param face  the block the ring crossed
     * @param now   the game time
     * @param light the ring's light there
     * @param adds  whether the ring adds its light
     */
    private void light(BlockPos face, long now, float light, boolean adds) {
        Lit standing = remembered.remove(face);
        float left = standing == null ? 0f : standing.levelAt(now);
        float held = adds ? Math.min(MOST_LIGHT, left + light) : left;
        remembered.put(face, new Lit(now, held));
    }

    /**
     * @return each lit face and its lighting
     */
    public Map<BlockPos, Lit> remembered() {
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
     * How strongly a lit block draws now, for a player standing at a point.
     *
     * @param block  the block
     * @param lit    its lighting
     * @param player the player's position
     * @param now    the game time
     * @param reach  the render distance, in blocks
     * @return the distance strength, times the face's glow
     */
    public static float strength(BlockPos block, Lit lit, Vec3 player, long now, double reach) {
        return strengthAt(Vec3.atCenterOf(block).distanceTo(player), reach) * glowAt(lit, now);
    }

    /**
     * How brightly a lit block glows now: as bright as the light left on it,
     * so it starts as bright as the sound was as it reached it and decays
     * steadily from there, a block holding more than a full light glowing
     * full until it falls back below one.
     *
     * @param lit its lighting
     * @param now the game time
     * @return the glow, zero to one
     */
    public static float glowAt(Lit lit, long now) {
        return Math.min(1f, lit.levelAt(now));
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
