package com.mercuriusxeno.goo.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A looping sound kept alive by whatever makes it: each tick it is fed it
 * swells toward its full volume, and once the feeding stops it fades to
 * silence and stops, so a buzz never cuts off mid-clip. Decay's gnats and a
 * hive prism's swarm buzz with one (decision decay-gnats-degrade-each-block-once,
 * decision hive-prism-pillar-eats-the-living).
 */
public final class FadingLoopSound extends AbstractTickableSoundInstance {

    /** The ticks the loop takes to swell from silence to full. */
    static final int FADE_IN_TICKS = 6;
    /** The ticks the loop takes to fade from full to silence once unfed. */
    static final int FADE_OUT_TICKS = 20;
    /** The ticks the loop holds its volume past its last feeding, so a late tick does not dip it. */
    static final int FED_GRACE_TICKS = 2;

    private final float fullVolume;
    private int age;
    private int lastFed;

    /**
     * A loop starting silent at a point.
     *
     * @param sound      the looped sound
     * @param source     the mixer channel it plays on
     * @param fullVolume the volume it swells to while fed
     * @param pitch      its pitch
     * @param at         where it plays
     */
    FadingLoopSound(SoundEvent sound, SoundSource source, float fullVolume, float pitch, Vec3 at) {
        super(sound, source, SoundInstance.createUnseededRandom());
        this.fullVolume = fullVolume;
        this.pitch = pitch;
        this.volume = 0f;
        this.looping = true;
        this.delay = 0;
        moveTo(at);
    }

    /**
     * Keeps the loop sounding one more tick, at a point.
     *
     * @param at where it plays
     */
    void feed(Vec3 at) {
        moveTo(at);
        lastFed = age;
    }

    @Override
    public void tick() {
        age++;
        boolean fed = age - lastFed <= FED_GRACE_TICKS;
        volume = nextVolume(volume, fullVolume, fed);
        if (!fed && volume <= 0f) {
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /**
     * The loop's volume one tick on: swelling toward full while fed, fading
     * toward silence once not.
     *
     * @param volume     the volume now
     * @param fullVolume the volume the loop swells to
     * @param fed        whether the loop is still fed
     * @return the next tick's volume
     */
    static float nextVolume(float volume, float fullVolume, boolean fed) {
        return fed ? Math.min(fullVolume, volume + fullVolume / FADE_IN_TICKS)
                : Math.max(0f, volume - fullVolume / FADE_OUT_TICKS);
    }

    private void moveTo(Vec3 at) {
        x = at.x;
        y = at.y;
        z = at.z;
    }
}
