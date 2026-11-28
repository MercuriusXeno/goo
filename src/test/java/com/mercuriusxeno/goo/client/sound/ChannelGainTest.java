package com.mercuriusxeno.goo.client.sound;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.openal.SOFTOutputLimiter;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Covers the lifted gain reaching OpenAL Soft's mix: a source given the
 * gain goo's client answers for Drain's wind loop renders louder than at
 * gain 1.0 by that gain, on a loopback device rendering to a buffer with no
 * audio hardware and no output limiter.
 * drain-lifts-its-chosen-sounds-past-the-volume-cap
 */
class ChannelGainTest {

    private static final Identifier SOUL_SAND_VALLEY_LOOP =
            Identifier.withDefaultNamespace("ambient.soul_sand_valley.loop");
    /** Vanilla's sounds.json entry volume for the wind loop. */
    private static final float LOOP_ENTRY_VOLUME = 0.85f;
    private static final int RATE = 48_000;
    private static final int STEREO = 2;
    private static final double TONE_HERTZ = 440;
    /** Quiet enough that the lifted tone stays under full scale. */
    private static final double TONE_AMPLITUDE = 0.01;
    /** Frames rendered and thrown away while the source's gain settles. */
    private static final int SETTLE_FRAMES = 4_800;
    private static final int MEASURED_FRAMES = 9_600;
    private static final double WITHIN_DECIBELS = 1;

    private long device;
    private long context;

    @BeforeEach
    void openLoopbackDevice() {
        ALC.createCapabilities(MemoryUtil.NULL);
        device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer) null);
        assertNotEquals(MemoryUtil.NULL, device, "OpenAL Soft should open a loopback device");
        ALCCapabilities capabilities = ALC.createCapabilities(device);
        int[] attributes = {
            ALC10.ALC_FREQUENCY, RATE,
            SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
            SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_FLOAT_SOFT,
            SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT, ALC10.ALC_FALSE,
            0,
        };
        context = ALC10.alcCreateContext(device, attributes);
        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(capabilities);
    }

    @AfterEach
    void closeLoopbackDevice() {
        ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
    }

    @Test
    void drainsLiftedLoopRendersLouderByItsLift() {
        float lifted = LiftedSoundGain.gainFor(SOUL_SAND_VALLEY_LOOP, LOOP_ENTRY_VOLUME, 1f, 1f);
        double louder = renderedDecibels(lifted) - renderedDecibels(1f);
        assertEquals(20 * Math.log10(lifted), louder, WITHIN_DECIBELS,
                "a source given gain " + lifted + " should render " + 20 * Math.log10(lifted) + " dB louder");
    }

    /** One looping tone through a fresh source at a gain, its rendered RMS in dBFS. */
    private double renderedDecibels(float gain) {
        int buffer = AL10.alGenBuffers();
        int source = AL10.alGenSources();
        ShortBuffer tone = MemoryUtil.memAllocShort(RATE);
        FloatBuffer rendered = MemoryUtil.memAllocFloat(MEASURED_FRAMES * STEREO);
        try {
            for (int frame = 0; frame < RATE; frame++) {
                tone.put(frame, (short) (Short.MAX_VALUE * TONE_AMPLITUDE
                        * Math.sin(2 * Math.PI * TONE_HERTZ * frame / RATE)));
            }
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, tone, RATE);
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
            ChannelGain.apply(source, gain);
            AL10.alSourcePlay(source);
            FloatBuffer settle = MemoryUtil.memAllocFloat(SETTLE_FRAMES * STEREO);
            SOFTLoopback.alcRenderSamplesSOFT(device, settle, SETTLE_FRAMES);
            MemoryUtil.memFree(settle);
            SOFTLoopback.alcRenderSamplesSOFT(device, rendered, MEASURED_FRAMES);
            double sumOfSquares = 0;
            for (int at = 0; at < MEASURED_FRAMES * STEREO; at++) {
                sumOfSquares += rendered.get(at) * rendered.get(at);
            }
            return 10 * Math.log10(sumOfSquares / (MEASURED_FRAMES * STEREO));
        } finally {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffer);
            MemoryUtil.memFree(tone);
            MemoryUtil.memFree(rendered);
        }
    }
}
