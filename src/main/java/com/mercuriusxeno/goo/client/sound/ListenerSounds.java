package com.mercuriusxeno.goo.client.sound;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.network.ListenerSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The client side of a sound whose source sits at the local player's own
 * head: it plays at the listener, unpanned and unattenuated, since a source
 * placed a fraction of a block off the ears pans wholly to one side.
 * goo-sound-at-the-listener-reaches-both-ears
 */
public final class ListenerSounds {

    private ListenerSounds() {
    }

    /**
     * Plays a sound the server placed at the local player's own head.
     *
     * @param payload the sound
     * @param context the network context
     */
    public static void handle(ListenerSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> play(payload.cue()));
    }

    /**
     * Plays a sound at the local player's ears.
     *
     * @param cue the sound
     */
    public static void play(SoundCue cue) {
        Minecraft.getInstance().getSoundManager().play(atListener(cue));
    }

    /**
     * The instance the local player hears a sound at their own head by.
     *
     * @param cue the sound
     * @return the instance, relative to the listener and unattenuated
     */
    public static SimpleSoundInstance atListener(SoundCue cue) {
        return new SimpleSoundInstance(cue.sound(), SoundPlays.sourceOf(cue.source()), cue.volume(), cue.pitch(),
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }
}
