package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.FormSounds;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.client.sound.ListenerSounds;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.network.StackFormedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Plays the sounds a held throw's stack makes on this client, Knives' and
 * Dart's: the form cue at the local player as each one forms, and the throw
 * cue where each one leaves.
 * decision knives-and-javelins-sound-as-each-forms-and-is-thrown
 */
final class StackSounds {

    /** The entity id no player holds, for a client with no local player. */
    static final int NO_ONE = -1;

    private StackSounds() {
    }

    /** Takes a count the server sent for a caster: Knives' or Dart's own record of it. */
    @FunctionalInterface
    interface Counter {
        void counted(int casterId, int formed, long now, int localId, FormSounds sounds, Consumer<SoundCue> played,
                     boolean released);
    }

    /**
     * Takes a count the server sent, on the client's thread, with the local
     * player's selected sounds played at the local player.
     *
     * @param payload the count
     * @param context the network context
     * @param counter the stack taking it
     */
    static void take(StackFormedPayload payload, IPayloadContext context, Counter counter) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LocalPlayer local = mc.player;
                counter.counted(payload.casterId(), payload.formed(), mc.level.getGameTime(),
                        local == null ? NO_ONE : local.getId(), selectedBy(local), cue -> playAt(local, cue),
                        payload.released());
            }
        });
    }

    /**
     * Plays the form cue once where a count rose for the local player, and
     * none where it held or fell, or rose for another caster.
     * decision knives-and-javelins-sound-as-each-forms-and-is-thrown
     *
     * @param casterId the caster's entity id
     * @param localId  the local player's entity id
     * @param before   the count held before
     * @param formed   the count held now
     * @param sounds   the local player's selected stack sounds
     * @param played   plays a cue at the local player
     */
    static void soundForming(int casterId, int localId, int before, int formed, FormSounds sounds,
                             Consumer<SoundCue> played) {
        if (casterId == localId && formed > before) {
            sounds.form().ifPresent(played);
        }
    }

    /**
     * The stack sounds a player's glove selection names.
     *
     * @param player the player, or null for none
     * @return the sounds, none for a player selecting none
     */
    static FormSounds selectedBy(@Nullable Player player) {
        GloveSelection selection = player == null ? null : GooCasters.heldSelection(player);
        return selection == null ? FormSounds.NONE
                : GloveThrowSender.selectedDelivery(selection.abilityId()).formSounds();
    }

    /**
     * The instance a cue plays by for a listener: at the listener, both ears,
     * where it sounds beside the listener's head, and at its point otherwise.
     * decision goo-sound-at-the-listener-reaches-both-ears
     *
     * @param at  where the cue sounds
     * @param eye the listener's eye
     * @param cue the cue
     * @return the instance
     */
    static SimpleSoundInstance instanceOf(Vec3 at, Vec3 eye, SoundCue cue) {
        if (ListenerSounds.besideHead(at, eye)) {
            return ListenerSounds.atListener(cue);
        }
        return new SimpleSoundInstance(cue.sound(), SoundPlays.sourceOf(cue.source()), cue.volume(), cue.pitch(),
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z,
                false);
    }

    /**
     * Plays a cue at a player, for that player to hear.
     *
     * @param player the player, or null for none
     * @param cue    the cue
     */
    static void playAt(@Nullable Player player, SoundCue cue) {
        if (player != null) {
            playAt(player, player.getEyePosition(), cue);
        }
    }

    /**
     * Plays a cue at a point in a player's level.
     *
     * @param player the player whose level hears it
     * @param at     where it plays
     * @param cue    the cue
     */
    static void playAt(Player player, Vec3 at, SoundCue cue) {
        // goo-sound-at-the-listener-reaches-both-ears: a cue beside the listener's head reaches both ears
        ListenerSounds.play(instanceOf(at, player.getEyePosition(), cue));
    }
}
