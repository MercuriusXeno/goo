package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.ScanWavePayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deep Click's sound wave, as the operator ruled it: the sculk shrieker's
 * shriek rings, held square to the line rather than flat and rising, travelling
 * the straight line from the glove to the landing over
 * the server's delay, arriving on the tick the click sounds there, with the
 * click itself trailing it in a fading reverb all the way to the target,
 * quietly (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SoundWaves {

    /** The most times the trailing click echoes on the way, as the operator ruled it: three or four. */
    static final int MOST_ECHOES = 4;
    /** The fewest ticks between one echo and the next, so the echoes read spaced apart. */
    static final int LEAST_ECHO_GAP = 4;
    /** The trailing click's volume as it leaves the glove, already quiet. */
    static final float REVERB_LOUDEST = 0.3f;
    /** The trailing click's volume as it reaches the target, barely there. */
    static final float REVERB_QUIETEST = 0.05f;
    /** The trailing click's pitch, falling a little as it travels, as an echo does. */
    static final float REVERB_PITCH = 1.2f;
    static final float REVERB_PITCH_FALL = 0.25f;

    private static final List<Travelling> TRAVELLING = new ArrayList<>();

    private SoundWaves() {
    }

    /** One wave on its way to its landing, and how many of its echoes have sounded. */
    static final class Travelling {
        private final ScanWavePayload wave;
        private final long start;
        private int echoesSounded;

        /**
         * @param wave  the wave the server sent
         * @param start the client game tick it left the glove
         */
        Travelling(ScanWavePayload wave, long start) {
            this.wave = wave;
            this.start = start;
        }

        ScanWavePayload wave() {
            return wave;
        }

        long start() {
            return start;
        }
    }

    /**
     * Starts a wave leaving the glove.
     *
     * @param wave     the wave the server sent
     * @param gameTime the client game tick it arrived on
     */
    static void start(ScanWavePayload wave, long gameTime) {
        TRAVELLING.add(new Travelling(wave, gameTime));
    }

    /**
     * Shrieks each travelling wave at the point it has reached this tick,
     * trails its click there, and lets the arrived ones go.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TRAVELLING.clear();
            return;
        }
        if (TRAVELLING.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        long now = level.getGameTime();
        for (Travelling travelling : TRAVELLING) {
            travel(level, travelling, Math.max(0, now - travelling.start()));
        }
        // the last echo sounds before its wave goes, however far the clock jumped past the arrival
        TRAVELLING.removeIf(travelling -> now - travelling.start() >= travelling.wave().ticks());
    }

    private static void travel(ClientLevel level, Travelling travelling, long age) {
        ScanWavePayload wave = travelling.wave();
        Vec3 at = ScanWaveProgress.at(wave.from(), wave.to(), wave.ticks(), age);
        // the shriek ring held square to the line, its spawn speed naming the line it faces along
        Vec3 along = wave.to().subtract(wave.from());
        level.addParticle(GooParticles.CLICK_WAVE.get(), at.x, at.y, at.z, along.x, along.y, along.z);
        int due = echoesDue(age, wave.ticks());
        if (due > travelling.echoesSounded) {
            travelling.echoesSounded = due;
            float share = wave.ticks() <= 0 ? 1f : Math.min(1f, (float) age / wave.ticks());
            wave.trail().flatMap(SoundWaves::soundOf).ifPresent(sound -> level.playLocalSound(at.x, at.y, at.z,
                    sound, SoundSource.BLOCKS, reverbVolume(share), REVERB_PITCH - REVERB_PITCH_FALL * share, false));
        }
    }

    /**
     * How many echoes of the trailing click are due by an age of the wave's
     * travel: at most {@link #MOST_ECHOES}, at least {@link #LEAST_ECHO_GAP}
     * ticks apart, spread evenly from the glove to the target, the first as it
     * leaves. Counting what is due, rather than matching an echo's own tick,
     * lets a clock that skips a tick, as the client's does when the server
     * re-syncs it, sound the echo a tick late instead of not at all.
     *
     * @param age   the ticks since the wave left the glove
     * @param ticks the ticks the wave takes to arrive
     * @return the echoes due by that age
     */
    static int echoesDue(long age, int ticks) {
        int echoes = Math.max(1, Math.min(MOST_ECHOES, ticks / LEAST_ECHO_GAP + 1));
        double gap = echoes == 1 ? 0 : (double) ticks / (echoes - 1);
        int due = 0;
        for (int echo = 0; echo < echoes; echo++) {
            if (Math.round(echo * gap) <= age) {
                due++;
            }
        }
        return due;
    }

    /**
     * How loud the trailing click echoes at a share of the way to the target:
     * quiet as it leaves, fading to barely there as it arrives.
     *
     * @param share how far along the line the wave stands, 0 to 1
     * @return the echo's volume
     */
    static float reverbVolume(float share) {
        return REVERB_LOUDEST + (REVERB_QUIETEST - REVERB_LOUDEST) * share;
    }

    private static Optional<SoundEvent> soundOf(Identifier id) {
        return BuiltInRegistries.SOUND_EVENT.get(id).map(Holder::value);
    }
}
