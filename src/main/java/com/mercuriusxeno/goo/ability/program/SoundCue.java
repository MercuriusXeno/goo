package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;

/**
 * One evaluated sound play, what a {@link SoundStep} hands its host once
 * its expressions are read.
 *
 * @param sound  the sound event id
 * @param source the category the sound plays under
 * @param volume the volume
 * @param pitch  the pitch
 */
public record SoundCue(Identifier sound, SoundKind source, float volume, float pitch) {
}
