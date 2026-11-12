package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.deep.SculkerHearing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a Sculker hears echoes off of. Every sound heard sends two rings
 * out of its source a quarter second apart, each sweeping eight blocks a
 * second, and every block open to air that a ring crosses is lit, full
 * at the source and weaker the farther the ring has travelled, to nothing at
 * the end of its reach. A sound's vibration rank alone sets how strongly its
 * rings light and how far they reach, rank 15 the strongest and farthest and
 * nothing ringing below rank 5, whatever volume the game plays it at;
 * a rank 10 sound lights full and reaches sixteen blocks. Sounds that meet on a face add up, each sound once
 * however many of its rings cross it, to twice a full light at most, and a
 * face's light falls at a steady rate, a full light within three seconds,
 * so a block fades by how recently and how strongly it was lit: it glows as
 * bright as the sound was as it reached it and decays from there. A mob that
 * makes a sound outlines for as long as its rank holds, three seconds at
 * rank 10. What is heard draws fainter the farther the player stands from it,
 * down to a fifth at the edge of render distance and not at all past it;
 * the brew's end forgets every face. The faces whose light runs out soonest
 * give way once the memory holds its most, so a loud dive never outgrows it
 * and weak sounds never push out what a strong one lit.
 * decision sculker-sight-persists-where-echoes-have-touched
 * decision sculker-echo-intensity-follows-vibration-rank
 */
public final class SculkerEchoes {

    /** How fast a ring sweeps out of its source, in blocks per tick: eight blocks a second. */
    static final double RING_SPEED = 0.4;
    /** The rings each sound sends out. */
    static final int RINGS = 2;
    /** Ticks between one ring of a sound and its next, a quarter second. */
    static final int RING_GAP_TICKS = 5;
    /** How far a rank 10 sound's rings reach, in blocks; reach grows with rank in step. */
    static final double ORDINARY_RANGE = 16;
    /** The rank whose rings light full, reach sixteen blocks and outline for three seconds. */
    static final int ORDINARY_RANK = 10;
    /** The lowest rank a sound rings at, as the operator ruled it: one ranked below rings as this. */
    public static final int FLOOR_RANK = SculkerHearing.FLOOR_RANK;
    /** The highest vibration rank. */
    public static final int LOUDEST_RANK = 15;
    /** Ticks a full light takes to fall to nothing with no new sound, three seconds. */
    static final int FADE_TICKS = 60;
    /** The most light a face holds, however many sounds meet on it: twice a full light. */
    static final float MOST_LIGHT = 2f;
    /** Ticks a mob that made a rank 10 sound stays outlined, three seconds. */
    static final int OUTLINE_TICKS = 60;
    /** How strongly what is heard at the edge of render distance draws, as a share of beside the player. */
    static final float FARTHEST_SHARE = 0.2f;

    /** The most faces the memory holds before the faintest give way. */
    static final int MOST_FACES = 32_768;
    /** The most rings growing at once; a sound past them starts none. */
    static final int MOST_RIPPLES = 192;

    private final List<Ripple> ripples = new ArrayList<>();
    private final int mostFaces;
    private final Map<BlockPos, Lit> remembered = new LinkedHashMap<>();
    /**
     * The same faces grouped by the chunk section each stands in, so a frame
     * passes over the sections in view alone.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     */
    private final Map<Long, Map<BlockPos, Lit>> bySection = new HashMap<>();

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
     * @param swept     how far out it has lit already, so a ring first grown late still lights from its source
     */
    record Ripple(Vec3 source, long startTick, double reach, float strength, boolean adds, double swept) {

        /**
         * @param now the game time
         * @return how far the front stands from the source then
         */
        double radiusAt(long now) {
            return Math.min(reach, Math.max(0, now - startTick) * RING_SPEED);
        }

        /**
         * @param now the game time it has grown to
         * @return the ring, lit out to its front then, or as it stands where it has yet to set out
         */
        Ripple grownTo(long now) {
            return startTick < now ? new Ripple(source, startTick, reach, strength, adds, radiusAt(now)) : this;
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
     * Sends a sound's rings out of its source a quarter second apart, their
     * strength and reach set by its rank, their light falling evenly from
     * full at the source to nothing at their reach.
     *
     * @param at        where the sound played
     * @param startTick the game time it played, which its first ring sets out at
     * @param rank      its vibration rank, 1 to 15; below rank 5 it rings as 5
     */
    public void hear(Vec3 at, long startTick, int rank) {
        ring(at, startTick, strengthFor(rank), reachFor(rank));
    }

    /**
     * How strongly a rank's rings light a face at their source: a full light
     * at rank 10, in step with rank, and no less than rank 5's.
     *
     * @param rank the sound's vibration rank
     * @return the light at the source
     */
    public static float strengthFor(int rank) {
        return (float) ringRank(rank) / ORDINARY_RANK;
    }

    /**
     * How far a rank's rings reach: sixteen blocks at rank 10, in step with
     * rank, and no shorter than rank 5's.
     *
     * @param rank the sound's vibration rank
     * @return the reach, in blocks
     */
    public static double reachFor(int rank) {
        return ORDINARY_RANGE * ringRank(rank) / ORDINARY_RANK;
    }

    /**
     * How long a mob that made a sound stays outlined: three seconds at rank
     * 10, in step with rank, and no shorter than rank 5's.
     *
     * @param rank the sound's vibration rank
     * @return the ticks its outline lasts
     */
    public static int outlineTicksFor(int rank) {
        return OUTLINE_TICKS * ringRank(rank) / ORDINARY_RANK;
    }

    /**
     * The rank a sound rings at: its own, raised to rank 5 and held to 15.
     *
     * @param rank the sound's vibration rank
     * @return the rank its rings take
     */
    static int ringRank(int rank) {
        return Math.clamp(rank, FLOOR_RANK, LOUDEST_RANK);
    }

    /**
     * Sends a sound's rings out at a strength and across a reach.
     *
     * @param at        where the sound played
     * @param startTick the game time its first ring sets out
     * @param strength  how strongly its rings light a face at the source, up to twice a full light
     * @param reach     how far its rings sweep, in blocks
     */
    void ring(Vec3 at, long startTick, float strength, double reach) {
        if (ripples.size() + RINGS > MOST_RIPPLES) {
            return;
        }
        float held = Math.min(MOST_LIGHT, strength);
        for (int ring = 0; ring < RINGS; ring++) {
            ripples.add(new Ripple(at, startTick + (long) ring * RING_GAP_TICKS, reach, held, ring == 0, 0));
        }
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
        advanceEchoing(now, cell -> lights.test(cell) && besideOpenSpace(cell, passesSound));
    }

    /**
     * Grows every ring to the game time given, lights each block its front
     * crossed since the tick before that an echo lights, lets the rings that
     * have reached their reach die, and forgets the blocks that have faded.
     *
     * @param now       the game time
     * @param echoesOff whether an echo lights a block: one that lights, beside a block sound passes through
     */
    void advanceEchoing(long now, Predicate<BlockPos> echoesOff) {
        for (Ripple ripple : ripples) {
            if (ripple.startTick() >= now) {
                continue;
            }
            for (BlockPos cell : ScryReveal.shell(ripple.source(), ripple.swept(), ripple.radiusAt(now))) {
                if (echoesOff.test(cell)) {
                    light(cell.immutable(), now, ripple.lightAt(cell), ripple.adds());
                }
            }
        }
        ripples.replaceAll(ripple -> ripple.grownTo(now));
        ripples.removeIf(ripple -> ripple.radiusAt(now) >= ripple.reach());
        remembered.entrySet().removeIf(face -> now >= face.getValue().fadedAt() && leftItsSection(face.getKey()));
        forgetTheFaintest();
    }

    /**
     * Takes a face out of its section's group, as it leaves the memory.
     *
     * @param face the face
     * @return true, so a sweep of the memory drops it there too
     */
    private boolean leftItsSection(BlockPos face) {
        long section = SectionPos.asLong(face);
        Map<BlockPos, Lit> faces = bySection.get(section);
        if (faces != null) {
            faces.remove(face);
            if (faces.isEmpty()) {
                bySection.remove(section);
            }
        }
        return true;
    }

    private void forget(BlockPos face) {
        remembered.remove(face);
        leftItsSection(face);
    }

    private void remember(BlockPos face, Lit lit) {
        remembered.put(face, lit);
        bySection.computeIfAbsent(SectionPos.asLong(face), section -> new HashMap<>()).put(face, lit);
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
                .forEach(this::forget);
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
        remember(face, new Lit(now, held));
    }

    /**
     * @return each lit face and its lighting
     */
    public Map<BlockPos, Lit> remembered() {
        return Collections.unmodifiableMap(remembered);
    }

    /**
     * @return each chunk section holding a lit face, by its packed section position, and its faces' lighting
     */
    Map<Long, Map<BlockPos, Lit>> bySection() {
        return Collections.unmodifiableMap(bySection);
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
        bySection.clear();
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
