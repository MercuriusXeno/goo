package com.mercuriusxeno.goo.client.sound;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.network.ListenerSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The client side of a sound whose source sits at the local player's own
 * head: it plays at the listener, unpanned and unattenuated, since a source
 * placed a fraction of a block off the ears pans wholly to one side.
 * goo-sound-at-the-listener-reaches-both-ears
 */
public final class ListenerSounds {

    /** How far from the local player's eye a source sits at their head, in blocks. */
    static final double HEAD_REACH = 1.0;

    private ListenerSounds() {
    }

    /**
     * Plays a sound the server placed at the local player's own head.
     *
     * @param payload the sound
     * @param context the network context
     */
    public static void handle(ListenerSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> play(atListener(payload.cue())));
    }

    /**
     * Plays a sound instance.
     *
     * @param sound the instance
     */
    public static void play(SoundInstance sound) {
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /**
     * The instance the local player hears a sound at their own head by.
     *
     * @param cue the sound
     * @return the instance, relative to the listener and unattenuated
     */
    public static SimpleSoundInstance atListener(SoundCue cue) {
        return atListener(cue.sound(), SoundPlays.sourceOf(cue.source()), cue.volume(), cue.pitch());
    }

    /**
     * The instance a sound plays by, at the listener or at its point.
     *
     * @param sound      the sound
     * @param source     the mixer channel it plays on
     * @param volume     its volume
     * @param pitch      its pitch
     * @param at         where it plays for a listener it is not at
     * @param atListener whether it sits at the local player's head
     * @return the instance
     */
    public static SimpleSoundInstance heardAt(SoundEvent sound, SoundSource source, float volume, float pitch,
                                              Vec3 at, boolean atListener) {
        if (atListener) {
            return atListener(sound.location(), source, volume, pitch);
        }
        return new SimpleSoundInstance(sound.location(), source, volume, pitch, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
    }

    /**
     * Whether a source sits at or beside a head.
     *
     * @param at  the source
     * @param eye the head's eye
     * @return true within a block of the eye
     */
    public static boolean besideHead(Vec3 at, Vec3 eye) {
        return at.distanceToSqr(eye) <= HEAD_REACH * HEAD_REACH;
    }

    private static SimpleSoundInstance atListener(Identifier sound, SoundSource source, float volume, float pitch) {
        return new SimpleSoundInstance(sound, source, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }
}
