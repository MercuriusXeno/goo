package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.ScanWavePayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Bulb's dart of light: light motes stream along the straight line from the
 * lifted palm blob to the place over the dart's ticks, and on arrival a puff
 * of motes bursts out where the crystal forms.
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LightDarts {

    /** Motes the dart sheds along its line each tick. */
    static final int MOTES_PER_TICK = 6;
    /** Motes the puff bursts out on arrival. */
    static final int PUFF_MOTES = 14;
    /** The fastest a puffed mote leaves the place, blocks per tick. */
    static final double PUFF_SPEED = 0.08;

    private static final List<Darting> DARTING = new ArrayList<>();

    private LightDarts() {
    }

    /**
     * One dart on its way and the tick it left.
     *
     * @param wave  the dart the server sent
     * @param start the client game tick it left the palm blob
     */
    private record Darting(ScanWavePayload wave, long start) {
    }

    /**
     * Starts a dart leaving the palm blob.
     *
     * @param wave     the dart the server sent
     * @param gameTime the client game tick it arrived on
     */
    static void start(ScanWavePayload wave, long gameTime) {
        DARTING.add(new Darting(wave, gameTime));
    }

    /**
     * Streams each dart's motes along the stretch it covered this tick, puffs
     * the arrived ones out at their place and lets them go.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            DARTING.clear();
            return;
        }
        if (DARTING.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        long now = level.getGameTime();
        DARTING.removeIf(darting -> fly(level, darting, Math.max(0, now - darting.start())));
    }

    /**
     * Sheds a dart's motes from where it stood a tick ago to where it stands
     * now, and bursts its puff once it has arrived.
     *
     * @param level the client level
     * @param darting the dart
     * @param age   the ticks since it left
     * @return true once it has arrived and puffed
     */
    private static boolean fly(ClientLevel level, Darting darting, long age) {
        ScanWavePayload wave = darting.wave();
        Vec3 from = ScanWaveProgress.at(wave.from(), wave.to(), wave.ticks(), Math.max(0, age - 1));
        Vec3 to = ScanWaveProgress.at(wave.from(), wave.to(), wave.ticks(), age);
        for (int i = 0; i < MOTES_PER_TICK; i++) {
            Vec3 at = from.lerp(to, (i + 1) / (double) MOTES_PER_TICK);
            level.addParticle(GooParticles.LIGHT_MOTE.get(), at.x, at.y, at.z, 0, 0, 0);
        }
        if (age < wave.ticks()) {
            return false;
        }
        puff(level, wave.to(), level.getRandom());
        return true;
    }

    private static void puff(ClientLevel level, Vec3 place, RandomSource random) {
        for (int i = 0; i < PUFF_MOTES; i++) {
            level.addParticle(GooParticles.LIGHT_MOTE.get(), place.x, place.y, place.z,
                    either(random) * PUFF_SPEED, random.nextDouble() * PUFF_SPEED, either(random) * PUFF_SPEED);
        }
    }

    /**
     * A random share either way, from minus one to one.
     *
     * @param random the client's random
     * @return the share
     */
    private static double either(RandomSource random) {
        return random.nextDouble() - random.nextDouble();
    }
}
