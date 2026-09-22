package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;

/**
 * One evaluated particle spawn, what a {@link ParticlesStep} hands its
 * host once its expressions are read. Spread is split by axis: along is
 * the host's own axis, vertical on an entity and the placed-face normal
 * on a marker, and across is the two axes beside it.
 *
 * @param particle     the particle type id
 * @param count        how many particles
 * @param spreadAlong  the spread along the host's axis, in blocks
 * @param spreadAcross the spread across the host's axis, in blocks
 * @param speed        the particle speed
 * @param lift         how far above the anchor the burst centers, in blocks
 */
public record ParticleBurst(Identifier particle, int count, double spreadAlong, double spreadAcross,
                            double speed, double lift) {
}
