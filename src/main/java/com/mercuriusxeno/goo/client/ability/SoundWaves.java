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

    /** Ticks between one echo of the trailing click and the next. */
    static final int REVERB_EVERY = 2;
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

    /**
     * One wave on its way to its landing.
     *
     * @param wave  the wave the server sent
     * @param start the client game tick it left the glove
     */
    record Travelling(ScanWavePayload wave, long start) {
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
        TRAVELLING.removeIf(travelling -> now - travelling.start() > travelling.wave().ticks());
        for (Travelling travelling : TRAVELLING) {
            travel(level, travelling.wave(), now - travelling.start());
        }
    }

    private static void travel(ClientLevel level, ScanWavePayload wave, long age) {
        Vec3 at = ScanWaveProgress.at(wave.from(), wave.to(), wave.ticks(), age);
        // the shriek ring held square to the line, its spawn speed naming the line it faces along
        Vec3 along = wave.to().subtract(wave.from());
        level.addParticle(GooParticles.CLICK_WAVE.get(), at.x, at.y, at.z, along.x, along.y, along.z);
        if (age % REVERB_EVERY == 0) {
            float share = wave.ticks() <= 0 ? 1f : Math.min(1f, (float) age / wave.ticks());
            wave.trail().flatMap(SoundWaves::soundOf).ifPresent(sound -> level.playLocalSound(at.x, at.y, at.z,
                    sound, SoundSource.BLOCKS, reverbVolume(share), REVERB_PITCH - REVERB_PITCH_FALL * share, false));
        }
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
