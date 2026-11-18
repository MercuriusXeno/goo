package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' and Dart's sounds reach both ears where they sound beside the
 * listener's head: the forming sound at the local player and the throw at the
 * slot it leaves from play at the listener, unattenuated, and a throw farther
 * off plays at its point.
 * decision goo-sound-at-the-listener-reaches-both-ears
 */
class StackSoundsTest {

    private static final Vec3 EYE = new Vec3(12.3, 65.62, -7.9);
    private final SoundCue clink = AbilityJson.decode("crystal_knives").delivery().formSounds().form().orElseThrow();

    @Test
    void aCueBesideTheListenersHeadPlaysAtTheListener() {
        SimpleSoundInstance sound = StackSounds.instanceOf(EYE.add(0.4, -0.2, 0.6), EYE, clink);
        assertTrue(sound.isRelative(), "a cue beside the head should play at the listener");
        assertEquals(SoundInstance.Attenuation.NONE, sound.getAttenuation());
    }

    @Test
    void aCueFartherOffPlaysAtItsPoint() {
        SimpleSoundInstance sound = StackSounds.instanceOf(EYE.add(6, 0, 0), EYE, clink);
        assertFalse(sound.isRelative(), "a cue 6 blocks off should play where it sounds");
        assertEquals(SoundInstance.Attenuation.LINEAR, sound.getAttenuation());
    }
}