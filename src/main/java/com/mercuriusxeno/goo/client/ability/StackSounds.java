package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.FormSounds;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.item.GooCasters;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

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
     * Plays a cue at a player, for that player to hear.
     *
     * @param player the player, or null for none
     * @param cue    the cue
     */
    static void playAt(@Nullable Player player, SoundCue cue) {
        if (player != null) {
            playAt(player, player.position(), cue);
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
        BuiltInRegistries.SOUND_EVENT.get(cue.sound()).ifPresent(sound -> player.level().playLocalSound(
                at.x, at.y, at.z, sound.value(), SoundSource.PLAYERS, cue.volume(), cue.pitch(), false));
    }
}
