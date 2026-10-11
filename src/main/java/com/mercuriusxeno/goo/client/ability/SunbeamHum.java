package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Sunbeam's hum while its ray shows: a looping beacon tone that starts
 * pitched up as the hold begins and glides down to a low, steady thrum,
 * following the caster, and stops the tick the ray stops showing
 * (operator ruling 2026-10-09).
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
public final class SunbeamHum extends AbstractTickableSoundInstance {

    /** The hum's pitch as the hold begins. */
    static final float START_PITCH = 1.8f;
    /** The low, steady thrum it settles to. */
    static final float THRUM_PITCH = 0.6f;
    /** Ticks the glide down takes. */
    static final int GLIDE_TICKS = 20;
    private static final float VOLUME = 0.8f;

    private final Entity caster;
    private int age;

    /**
     * Creates the hum on a caster.
     *
     * @param caster the player holding the ray
     */
    public SunbeamHum(Entity caster) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.caster = caster;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.pitch = START_PITCH;
        follow();
    }

    @Override
    public void tick() {
        if (caster.isRemoved() || !SunbeamVisual.isShowing(caster.getId())) {
            stop();
            return;
        }
        age++;
        pitch = pitchAt(age);
        follow();
    }

    /**
     * The hum's pitch a number of ticks into the hold: easing from the start
     * pitch down to the thrum, then holding there.
     *
     * @param ticks ticks since the hold began
     * @return the pitch
     */
    static float pitchAt(int ticks) {
        float glide = Mth.clamp((float) ticks / GLIDE_TICKS, 0f, 1f);
        float eased = 1f - (1f - glide) * (1f - glide);
        return Mth.lerp(eased, START_PITCH, THRUM_PITCH);
    }

    private void follow() {
        x = caster.getX();
        y = caster.getEyeY();
        z = caster.getZ();
    }
}
