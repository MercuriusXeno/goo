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
 * the splashes a beat apart; Drain trickles a few wisps at a time, staggered
 * so a held field keeps a steady stream.
 * lifetap-trades-regen-for-leech
 * drain-field-heals-with-the-lifetap-visuals
 */
public final class LeechWisps {

    /** Wisps in one splash. */
    static final int WISPS_PER_SPLASH = 9;
    /** Ticks between one splash and the next. */
    static final int SPLASH_STAGGER = 3;
    /** The most splashes one heal plays. */
    static final int MOST_SPLASHES = 8;
    /** Wisps in one trickle. */
    static final int WISPS_PER_TRICKLE = 5;
    /** Ticks over which a trickle's wisps set off, a strike's interval in a held field. */
    static final int TRICKLE_SPREAD = 10;
    private static final double SCATTER_SPEED = 0.16;
    private static final double SCATTER_LIFT = 0.06;
    private static final int SCATTER_TICKS = 5;
    /** The slowest a wisp scatters, as a share of the scatter speed. */
    private static final double SLOWEST_SCATTER = 0.5;
    private static final int SCATTER_VARIANCE = 4;
    private static final double TRICKLE_DRIFT = 0.04;
    private static final int BASE_FLIGHT = 14;
    private static final int FLIGHT_VARIANCE = 10;
    private static final double BODY_CENTER = 0.5;
    private static final double CHEST = 0.7;
    private static final double SPAWN_SPREAD = 0.3;

    private LeechWisps() {
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
            Vec3 from = victim.position().add(0, victim.getBbHeight() * BODY_CENTER, 0);
            if (payload.splash()) {
                for (int splash = 0; splash < splashesFor(payload.healed()); splash++) {
                    splash(mc, level.getRandom(), from, healed, splash * SPLASH_STAGGER);
                }
            } else {
                trickle(mc, level.getRandom(), from, healed);
            }
        });
    }

    private static void splash(Minecraft mc, RandomSource random, Vec3 from, Entity healed, int delay) {
        for (int wisp = 0; wisp < WISPS_PER_SPLASH; wisp++) {
            Vec3 scatter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .normalize().scale(SCATTER_SPEED * (SLOWEST_SCATTER + random.nextDouble())).add(0, SCATTER_LIFT, 0);
            launch(mc, random, from, healed, delay, scatter, SCATTER_TICKS + random.nextInt(SCATTER_VARIANCE));
        }
    }

    private static void trickle(Minecraft mc, RandomSource random, Vec3 from, Entity healed) {
        for (int wisp = 0; wisp < WISPS_PER_TRICKLE; wisp++) {
            Vec3 drift = new Vec3(random.nextGaussian(), random.nextDouble(), random.nextGaussian())
                    .scale(TRICKLE_DRIFT);
            launch(mc, random, from, healed, random.nextInt(TRICKLE_SPREAD), drift, random.nextInt(SCATTER_VARIANCE));
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
