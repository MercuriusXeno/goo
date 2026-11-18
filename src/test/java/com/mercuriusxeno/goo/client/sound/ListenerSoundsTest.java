package com.mercuriusxeno.goo.client.sound;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundKind;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sound at the local player's own head plays at the listener, unpanned and
 * unattenuated
 * (decisions diagnose-and-correct-the-spray-squelch-playing-in-one-ear,
 * goo-sound-at-the-listener-reaches-both-ears).
 */
class ListenerSoundsTest {

    private static final float EPSILON = 1e-6f;
    /** Goop's squelch, a stream sound with loop false. */
    private static final SoundCue GOOP_SQUELCH = new SoundCue(Identifier.parse("minecraft:block.honey_block.slide"),
            SoundKind.PLAYERS, 1f, 1.05f);

    @Test
    void aSquelchAtTheHoldersHeadPlaysRelativeAndUnattenuated() {
        SimpleSoundInstance heard = ListenerSounds.atListener(GOOP_SQUELCH);
        assertTrue(heard.isRelative());
        assertEquals(SoundInstance.Attenuation.NONE, heard.getAttenuation());
        assertEquals(0, heard.getX(), EPSILON);
        assertEquals(0, heard.getY(), EPSILON);
        assertEquals(0, heard.getZ(), EPSILON);
        assertEquals(SoundSource.PLAYERS, heard.getSource());
    }
}
