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
import java.util.ArrayList;
import java.util.List;

/**
 * Drained life's look, which Drain and Leech goo share: life drawn from a
 * victim to the one it heals as hex wisps. Every leech bursts one fixed set
 * of wisps off the victim's body at once, whatever it healed and whichever
 * drew it; they scatter, slow to a hover around the mob, then set off home
 * a tick or so apart, each on its own bend and speeding up as it closes in,
 * the last landing on the healed one's chest before Drain's next strike.
 * lifetap-trades-regen-for-leech
 * drain-field-heals-with-the-lifetap-visuals
 * drained-mobs-show-stream-burst-and-tether
 * drained-life-arcs-home-speeding-up
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 */
public final class LeechWisps {

    /** Wisps in one burst, the same for every leech. */
    static final int WISPS_PER_BURST = 12;
    /** Ticks a burst wisp scatters off the body before it hovers. */
    static final int SCATTER_TICKS = 2;
    /** The fewest ticks a burst wisp hovers before it sets off home. */
    static final int FEWEST_HOVER_TICKS = 1;
    /** Ticks across which a burst's wisps set off home, one after another. */
    static final int SET_OFF_SPREAD = 3;
    /** Ticks a burst wisp's flight home takes. */
    static final int FLIGHT_TICKS = 5;
    private static final double SCATTER_SPEED = 0.22;
    private static final double SCATTER_LIFT = 0.05;
    /** The slowest a wisp scatters, as a share of the scatter speed. */
    private static final double SLOWEST_SCATTER = 0.5;
    private static final double BODY_CENTER = 0.5;
    /** How fast a shed wisp sets off upward at the least, blocks a tick; it sets off up to twice this. */
    private static final double SHED_RISE = 0.04;
    /** How far a shed wisp sways aside as it rises, blocks a tick, a gaussian's spread. */
    private static final double SHED_SWAY = 0.01;
    /** The fewest ticks a shed wisp drifts before it has faded. */
    private static final int SHED_TICKS = 14;
    /** The ticks beyond the fewest a shed wisp may drift. */
    private static final int SHED_VARIANCE = 8;
    private static final double CHEST = 0.7;

    private LeechWisps() {
    }

    /**
     * One wisp of a burst's schedule, in ticks from the leech's arrival.
     *
     * @param spawnDelay   ticks before it shows
     * @param scatterTicks ticks it scatters off the body
     * @param hoverTicks   ticks it hovers before it sets off home
     * @param flightTicks  ticks its flight home takes
     */
    record BurstWisp(int spawnDelay, int scatterTicks, int hoverTicks, int flightTicks) {
    }

    /**
     * The burst a leech plays: one fixed count of wisps, Drain's and Leech
     * goo's alike at any heal, every one showing on the arrival tick and
     * setting off home one after another across SET_OFF_SPREAD ticks.
     * drained-mobs-show-stream-burst-and-tether
     *
     * @param payload the leech payload
     * @return each wisp's schedule
     */
    static List<BurstWisp> burstOf(LeechPayload payload) {
        List<BurstWisp> burst = new ArrayList<>(WISPS_PER_BURST);
        for (int wisp = 0; wisp < WISPS_PER_BURST; wisp++) {
            int lateBy = wisp * SET_OFF_SPREAD / WISPS_PER_BURST;
            burst.add(new BurstWisp(0, SCATTER_TICKS, FEWEST_HOVER_TICKS + lateBy, FLIGHT_TICKS));
        }
        return burst;
    }

    /**
     * Plays a leech's burst on the client thread.
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
            burst(mc, level.getRandom(), victim, healed, burstOf(payload));
        });
    }

    /**
     * Whether a living thing sheds a hex wisp this tick: where it stands in
     * Leech goo and the shed interval comes due on its tick count.
     * hex-wisps-drift-off-mobs-standing-in-leech-goo
     *
     * @param standsInLeech whether the thing stands in Leech goo
     * @param tickCount     the thing's tick count
     * @param interval      the ticks between the wisps it sheds
     * @return true where it sheds a wisp this tick
     */
    static boolean sheds(boolean standsInLeech, int tickCount, int interval) {
        return standsInLeech && tickCount % interval == 0;
    }

    /**
     * Sheds one hex wisp off a living thing standing in Leech goo, from a
     * point on its body, drifting up and fading as it rises.
     * hex-wisps-drift-off-mobs-standing-in-leech-goo
     *
     * @param mc     the client
     * @param random the random source
     * @param living the thing standing in the goo
     */
    static void shed(Minecraft mc, RandomSource random, Entity living) {
        Vec3 at = living.position().add((random.nextDouble() - BODY_CENTER) * living.getBbWidth(),
                random.nextDouble() * living.getBbHeight(), (random.nextDouble() - BODY_CENTER) * living.getBbWidth());
        if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), at.x, at.y, at.z, 0, 0, 0)
                instanceof HexWispParticle wisp) {
            wisp.burst(new Vec3(random.nextGaussian() * SHED_SWAY, SHED_RISE + random.nextDouble() * SHED_RISE,
                    random.nextGaussian() * SHED_SWAY), SHED_TICKS + random.nextInt(SHED_VARIANCE));
        }
    }

    /**
     * Bursts a leech's wisps off the victim's body, each scattering outward,
     * hovering and flying home to the healed one's chest on its schedule.
     * drained-mobs-show-stream-burst-and-tether
     * drained-life-arcs-home-speeding-up
     *
     * @param mc     the client
     * @param random the random source
     * @param victim the mob drained
     * @param healed the one the life goes to
     * @param burst  each wisp's schedule
     */
    private static void burst(Minecraft mc, RandomSource random, Entity victim, Entity healed,
                              List<BurstWisp> burst) {
        for (BurstWisp scheduled : burst) {
            Vec3 at = victim.position().add((random.nextDouble() - BODY_CENTER) * victim.getBbWidth(),
                    random.nextDouble() * victim.getBbHeight(), (random.nextDouble() - BODY_CENTER) * victim.getBbWidth());
            Vec3 scatter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                    .scale(SCATTER_SPEED * (SLOWEST_SCATTER + random.nextDouble())).add(0, SCATTER_LIFT, 0);
            if (mc.particleEngine.createParticle(GooParticles.HEX_WISP.get(), at.x, at.y, at.z, 0, 0, 0)
                    instanceof HexWispParticle wisp) {
                wisp.drainHome(scatter, scheduled.scatterTicks(), scheduled.hoverTicks(), scheduled.flightTicks(),
                        () -> healed.position().add(0, healed.getBbHeight() * CHEST, 0));
            }
        }
    }
}
