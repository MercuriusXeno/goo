package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.HexMistStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.particle.HexMistParticle;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Drain's cast look: while a stream naming {@link HexMistStep} is held, with
 * or without a mob in its cone, a hex mist fills the flat cone on the
 * horizontal plane through the glove hand and drifts toward the hand, fading
 * as it arrives. The mist is rough rather than clean lines: each puff's edge
 * and reach are jittered, so the cone's rim is ragged, and its density swells
 * and thins in a slow wave rolling toward the hand.
 * drain-casts-a-hex-mist-cone-toward-the-hand
 */
public final class HexMist {

    /** Mist puffs spawned into the cone each held tick. */
    static final int MIST_PER_TICK = 10;
    /** The nearest a puff spawns to the hand, as a share of the range, so each has a way to drift. */
    static final double NEAREST_SHARE = 0.08;
    /** How far a puff's own edge may fall inside the cone's, as a share of the half angle. */
    static final double EDGE_RAG = 0.3;
    /** How far a puff's own reach may fall short of the range, as a share of it. */
    static final double REACH_RAG = 0.25;
    /** Density waves along the cone's length, and how fast they roll toward the hand, in radians a tick. */
    static final double WAVES_ALONG = 1.5;
    static final double ROLL_PER_TICK = 0.15;
    /** The thinnest the wave leaves the mist, as a share of its fullest. */
    static final double THINNEST = 0.25;
    /** Draws a puff takes at most to land where the wave is dense; the last draw stands. */
    private static final int MOST_DRAWS = 4;
    private static final double HALF = 0.5;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double BOTH_SIDES = 2;

    private HexMist() {
    }

    /**
     * Where a mist puff spawns and how it drifts.
     *
     * @param at       the spawn point
     * @param velocity its drift, blocks per tick, reaching the hand over the puff's flight
     */
    record MistSpawn(Vec3 at, Vec3 velocity) {
    }

    /**
     * Fills this held tick's share of the mist for the local player's stream
     * when its ability names the mist.
     *
     * @param player    the streaming player
     * @param abilityId the streamed ability
     * @param delivery  the ability's delivery, whose range and cone the mist fills
     * @param hand      the glove hand the cone opens from
     */
    public static void drift(Player player, String abilityId, Delivery delivery, Vec3 hand) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        if (ability == null) {
            return;
        }
        Vec3 flatAim = Vec3.directionFromRotation(0, player.getYRot());
        for (MistSpawn spawn : spawnsFor(ability.behaviors(), hand, flatAim, delivery.range(),
                delivery.coneDegrees(), player.level().getGameTime(), player.getRandom())) {
            player.level().addParticle(GooParticles.HEX_MIST.get(), spawn.at().x, spawn.at().y, spawn.at().z,
                    spawn.velocity().x, spawn.velocity().y, spawn.velocity().z);
        }
    }

    /**
     * The puffs a held tick spawns: MIST_PER_TICK for a stream naming the
     * mist, whatever its cone holds, and none for any other.
     *
     * @param behaviors   the streamed ability's behaviors
     * @param hand        the glove hand
     * @param flatAim     the horizontal way the player faces
     * @param range       the stream's range, in blocks
     * @param coneDegrees the stream's full cone angle, rim to rim
     * @param time        the game time, in ticks
     * @param random      the random source
     * @return the spawns
     */
    static List<MistSpawn> spawnsFor(List<Step> behaviors, Vec3 hand, Vec3 flatAim, double range, double coneDegrees,
                                     double time, RandomSource random) {
        if (behaviors.stream().flatMap(HexMist::withDescendants).noneMatch(HexMistStep.class::isInstance)) {
            return List.of();
        }
        return IntStream.range(0, MIST_PER_TICK)
                .mapToObj(puff -> spawnAt(hand, flatAim, range, coneDegrees, time, random)).toList();
    }

    /**
     * One mist puff: on the horizontal plane through the hand, inside the
     * range and within half the cone of the flattened aim, drifting straight
     * at the hand so it lands there as its flight ends.
     *
     * @param hand        the glove hand
     * @param aim         the aim, flattened onto the horizontal plane here
     * @param range       the stream's range, in blocks
     * @param coneDegrees the stream's full cone angle, rim to rim
     * @param time        the game time, in ticks
     * @param random      the random source
     * @return the spawn
     */
    static MistSpawn spawnAt(Vec3 hand, Vec3 aim, double range, double coneDegrees, double time,
                             RandomSource random) {
        Vec3 flatAim = new Vec3(aim.x, 0, aim.z).normalize();
        double halfCone = Math.toRadians(coneDegrees * HALF);
        double along = 0;
        double angle = 0;
        for (int draw = 0; draw < MOST_DRAWS; draw++) {
            // a square root spreads the puffs evenly over the sector's widening area
            double reach = 1 - REACH_RAG * random.nextDouble();
            along = (NEAREST_SHARE + (1 - NEAREST_SHARE) * Math.sqrt(random.nextDouble())) * reach;
            angle = (random.nextDouble() * BOTH_SIDES - 1) * halfCone * (1 - EDGE_RAG * random.nextDouble());
            if (random.nextDouble() < density(along, time)) {
                break;
            }
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 heading = new Vec3(flatAim.x * cos - flatAim.z * sin, 0, flatAim.x * sin + flatAim.z * cos);
        Vec3 at = hand.add(heading.scale(along * range));
        return new MistSpawn(at, hand.subtract(at).scale(1.0 / HexMistParticle.FLIGHT_TICKS));
    }

    /**
     * The mist's density at a share of the cone's length: a wave between
     * THINNEST and full whose crests roll toward the hand as time passes.
     *
     * @param along the share of the length, zero to one
     * @param time  the game time, in ticks
     * @return the density, THINNEST to one
     */
    static double density(double along, double time) {
        double wave = HALF + HALF * Math.sin(TWO_PI * WAVES_ALONG * along + ROLL_PER_TICK * time);
        return THINNEST + (1 - THINNEST) * wave;
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(HexMist::withDescendants));
    }
}
