package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The embers each standing Tartarus rift gives off, rising from inside it:
 * one hot flame and one nether portal mote a client tick for each rift the
 * last frame drew.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class TartarusEmbers {

    /** How fast an ember rises, in blocks a tick. */
    private static final double RISES = 0.02;
    /** How high up a rift its embers start, at most, as a share of its height. */
    private static final double STARTS_UP_TO = 0.8;
    private static final double STARTS_ABOVE = 0.1;

    /** Each rift the last frame drew, kept for the tick that follows it. */
    private static final List<Seen> SEEN = new ArrayList<>();
    private static double seenAt = Double.NaN;

    private TartarusEmbers() {
    }

    /**
     * A rift as a frame drew it.
     *
     * @param foot   the middle of its foot, in the world
     * @param across the unit way across it
     * @param rift   its shape
     */
    private record Seen(Vec3 foot, Vec3 across, RiftShape rift) {
    }

    /**
     * Keeps a rift a frame drew, dropping those an earlier frame drew.
     *
     * @param time   the frame's client game tick and partial tick
     * @param foot   the middle of its foot, in the world
     * @param across the unit way across it
     * @param rift   its shape
     */
    static void see(double time, Vec3 foot, Vec3 across, RiftShape rift) {
        if (time != seenAt) {
            SEEN.clear();
            seenAt = time;
        }
        SEEN.add(new Seen(foot, across, rift));
    }

    /**
     * Gives off this tick's embers from each rift the last frame drew.
     *
     * @param event the client post-tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            SEEN.clear();
            return;
        }
        RandomSource random = level.getRandom();
        for (Seen seen : SEEN) {
            emberFrom(level, random, seen, true);
            emberFrom(level, random, seen, false);
        }
        SEEN.clear();
    }

    private static void emberFrom(ClientLevel level, RandomSource random, Seen seen, boolean hot) {
        double up = seen.rift().height() * (STARTS_ABOVE + random.nextDouble() * (STARTS_UP_TO - STARTS_ABOVE));
        double half = seen.rift().halfWidthAt(up);
        Vec3 at = seen.foot().add(seen.across().scale(Mth.nextDouble(random, -half, half))).add(0, up, 0);
        level.addParticle(hot ? ParticleTypes.SMALL_FLAME : ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 0,
                RISES, 0);
    }
}
