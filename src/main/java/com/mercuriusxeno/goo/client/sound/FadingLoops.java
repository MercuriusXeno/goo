package com.mercuriusxeno.goo.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/**
 * The client's live fading loops, one per thing that buzzes: feeding a key
 * keeps its loop sounding, starting one when none sounds, and a key left
 * unfed fades out on its own.
 */
public final class FadingLoops {

    private static final Map<Object, FadingLoopSound> LIVE = new HashMap<>();

    private FadingLoops() {
    }

    /**
     * Keeps a key's loop sounding one more tick at a point, starting it when
     * none sounds for the key.
     *
     * @param key    what the loop belongs to
     * @param sound  the looped sound's id
     * @param source the mixer channel it plays on
     * @param volume the volume it swells to
     * @param pitch  its pitch, used when it starts
     * @param at     where it plays
     */
    public static void keepAlive(Object key, Identifier sound, SoundSource source, float volume, float pitch,
                                 Vec3 at) {
        FadingLoopSound live = LIVE.get(key);
        if (live == null || live.isStopped()) {
            live = start(key, new FadingLoopSound(SoundEvent.createVariableRangeEvent(sound), source, volume, pitch,
                    at));
        }
        live.feed(at);
    }

    /**
     * Keeps a key's loop sounding one more tick at the listener's ears,
     * unpanned, starting it when none sounds for the key: the holder's own
     * loop of a held stream (decision broil-heat-wave-ring-cooks-ahead).
     *
     * @param key    what the loop belongs to
     * @param sound  the looped sound's id
     * @param source the mixer channel it plays on
     * @param volume the volume it swells to
     * @param pitch  its pitch, used when it starts
     */
    public static void keepAliveAtListener(Object key, Identifier sound, SoundSource source, float volume,
                                           float pitch) {
        FadingLoopSound live = LIVE.get(key);
        if (live == null || live.isStopped()) {
            live = start(key, FadingLoopSound.atListener(SoundEvent.createVariableRangeEvent(sound), source, volume,
                    pitch));
        }
        live.feed(Vec3.ZERO);
    }

    private static FadingLoopSound start(Object key, FadingLoopSound loop) {
        LIVE.values().removeIf(FadingLoopSound::isStopped);
        LIVE.put(key, loop);
        Minecraft.getInstance().getSoundManager().play(loop);
        return loop;
    }
}
