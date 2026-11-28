package com.mercuriusxeno.goo.client.sound;

import org.lwjgl.openal.AL10;

/**
 * Sets an OpenAL source's gain, as a sound channel's volume reaches it. A
 * source clamps its gain to its AL_MAX_GAIN, one by default, so a gain
 * lifted past one raises that ceiling to itself first.
 * drain-plays-its-chosen-sounds-as-loud-as-blaze-fire
 */
public final class ChannelGain {

    private static final float VANILLA_MAX_GAIN = 1f;

    private ChannelGain() {
    }

    /**
     * Sets a source's gain, and its ceiling to that gain where it passes one.
     *
     * @param source the OpenAL source
     * @param gain   the gain
     */
    public static void apply(int source, float gain) {
        AL10.alSourcef(source, AL10.AL_MAX_GAIN, Math.max(VANILLA_MAX_GAIN, gain));
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
    }
}
