package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.particle.HexWispParticle;
import com.mercuriusxeno.goo.network.LeechPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Lifetap's look, which Drain shares: life drawn from a victim to the one it
 * heals as hex wisps. Lifetap splashes once per point of health healed, each
 * splash a burst of wisps scattering off the victim before they curl home,
 * the splashes a beat apart; Drain streams its wisps from the drained mob's
 * chest into the player in one tight, steady stream, and bursts a few on the
 * victim at each strike.
 * lifetap-trades-regen-for-leech
 * drain-field-heals-with-the-lifetap-visuals
 * drained-mobs-show-stream-burst-and-tether
 */
public final class LeechWisps {

    /** Wisps in one splash. */
    static final int WISPS_PER_SPLASH = 9;
    /** Ticks between one splash and the next. */
    static final int SPLASH_STAGGER = 3;
    /** The most splashes one heal plays. */
    static final int MOST_SPLASHES = 8;
    /** Wisps in one trickle, enough over its spread to read as one stream from the mob to the caster. */
    static final int WISPS_PER_TRICKLE = 16;
    /** Wisps a strike bursts on the victim's body, scattering and fading there. */
    static final int WISPS_PER_BURST = 6;
    /** Ticks over which a trickle's wisps set off, a strike's interval in a held field. */
    static final int TRICKLE_SPREAD = 10;
    private static final double BURST_SPEED = 0.09;
    private static final int BURST_TICKS = 6;
    private static final int BURST_VARIANCE = 4;
    private static final double SCATTER_SPEED = 0.16;
    private static final double SCATTER_LIFT = 0.06;
    private static final int SCATTER_TICKS = 5;
    /** The slowest a wisp scatters, as a share of the scatter speed. */
    private static final double SLOWEST_SCATTER = 0.5;
    private static final int SCATTER_VARIANCE = 4;
    private static final int BASE_FLIGHT = 14;
    private static final int FLIGHT_VARIANCE = 10;
    private static final double BODY_CENTER = 0.5;
    private static final double CHEST = 0.7;
    private static final double SPAWN_SPREAD = 0.3;

    /** Blocks around the drained mob's chest a streamed wisp may leave from, so the stream starts as one. */
    static final double STREAM_SPREAD = 0.12;
    /** Ticks every streamed wisp takes from the mob to the player, alike so the stream holds together. */
    private static final int STREAM_FLIGHT = 12;

    private LeechWisps() {
    }

    /**
     * The tick a trickle's wisp sets off, the wisps one after another evenly
     * across the strike's interval, so the stream runs steady.
     * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
     *
     * @param wisp the wisp's place in its trickle
     * @return ticks it waits before it sets off
     */
    static int streamDelay(int wisp) {
        return wisp * TRICKLE_SPREAD / WISPS_PER_TRICKLE;
    }

    /**
     * Where a trickle's wisp leaves from: close around the drained mob's
     * chest, so the stream leaves it as one.
     * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
     *
     * @param chest  the drained mob's chest
     * @param random the random source
     * @return the point it leaves from
     */
    static Vec3 streamStart(Vec3 chest, RandomSource random) {
        Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
        return chest.add(way.scale(STREAM_SPREAD * random.nextDouble()));
    }

    /**
     * The splashes a heal plays: one per point of health healed, at least
     * one, at most MOST_SPLASHES.
     *
     * @param healed the health healed
     * @return the splash count
     */
    static int splashesFor(float healed) {
        return Math.clamp(Math.round(healed), 1, MOST_SPLASHES);
    }

    /**
     * Plays a leech's wisps on the client thread.
     *
     * @param payload the leech payload
     * @param context the network context
     */
    public static void handle(LeechPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || !(level.getEntity(payload.victimId()) instanceof Entity victim)
                    || !(level.getEntity(payload.healedId()) instanceof Entity healed)) {
                return;
            }
            // touched-mobs-show-hearts-or-a-count-over-max
            TouchedMobs.CLIENT.touch(victim.getId(), level.getGameTime());
            Vec3 from = victim.position().add(0, victim.getBbHeight() * BODY_CENTER, 0);
            if (payload.splash()) {
                for (int splash = 0; splash < splashesFor(payload.healed()); splash++) {
                    splash(mc, level.getRandom(), from, healed, splash * SPLASH_STAGGER);
                }
            } else {
                trickle(mc, level.getRandom(), victim.position().add(0, victim.getBbHeight() * CHEST, 0), healed);
                burst(mc, level.getRandom(), victim);
            }
        });
    }

    /**
     * Bursts wisps over the victim's body at a strike, so the hit itself shows.
     * drained-mobs-show-stream-burst-and-tether
     *
     * @param mc     the client
     * @param random the random source
     * @param victim the mob struck
     */
    private static void burst(Minecraft mc, RandomSource random, Entity victim) {
        for (int wisp = 0; wisp < WISPS_PER_BURST; wisp++) {
            Vec3 at = victim.position().add((random.nextDouble() - BODY_CENTER) * victim.getBbWidth(),
                    random.nextDouble() * victim.getBbHeight(), (random.nextDouble() - BODY_CENTER) * victim.getBbWidth());
            Vec3 scatter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .normalize().scale(BURST_SPEED * (SLOWEST_SCATTER + random.nextDouble()));
            if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), at.x, at.y, at.z, 0, 0, 0)
                    instanceof HexWispParticle spark) {
                spark.burst(scatter, BURST_TICKS + random.nextInt(BURST_VARIANCE));
            }
        }
    }

    private static void splash(Minecraft mc, RandomSource random, Vec3 from, Entity healed, int delay) {
        for (int wisp = 0; wisp < WISPS_PER_SPLASH; wisp++) {
            Vec3 scatter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .normalize().scale(SCATTER_SPEED * (SLOWEST_SCATTER + random.nextDouble())).add(0, SCATTER_LIFT, 0);
            launch(mc, random, from, healed, delay, scatter, SCATTER_TICKS + random.nextInt(SCATTER_VARIANCE));
        }
    }

    /**
     * Streams a strike's wisps from the drained mob's chest into the player:
     * one after another, from close together, each on the same flight, so
     * they run as one tight stream of life going to the player.
     * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
     *
     * @param mc     the client
     * @param random the random source
     * @param chest  the drained mob's chest
     * @param healed the player the life goes to
     */
    private static void trickle(Minecraft mc, RandomSource random, Vec3 chest, Entity healed) {
        for (int wisp = 0; wisp < WISPS_PER_TRICKLE; wisp++) {
            Vec3 at = streamStart(chest, random);
            if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), at.x, at.y, at.z, 0, 0, 0)
                    instanceof HexWispParticle streamed) {
                streamed.stream(streamDelay(wisp), STREAM_FLIGHT,
                        () -> healed.position().add(0, healed.getBbHeight() * CHEST, 0));
            }
        }
    }

    private static void launch(Minecraft mc, RandomSource random, Vec3 from, Entity healed, int delay,
                               Vec3 scatter, int scatterFor) {
        Vec3 at = from.add(random.nextGaussian() * SPAWN_SPREAD, random.nextGaussian() * SPAWN_SPREAD,
                random.nextGaussian() * SPAWN_SPREAD);
        if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), at.x, at.y, at.z, 0, 0, 0)
                instanceof HexWispParticle wisp) {
            wisp.launch(delay, scatter, scatterFor, BASE_FLIGHT + random.nextInt(FLIGHT_VARIANCE),
                    () -> healed.position().add(0, healed.getBbHeight() * CHEST, 0));
        }
    }
}
