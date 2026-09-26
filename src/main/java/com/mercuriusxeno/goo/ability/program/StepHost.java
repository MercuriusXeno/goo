package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;

/**
 * The seam a step program reaches its world through (decision
 * host-agnostic-runtime). The runtime holds a host, never a level or a
 * block entity, so the same program runs on the marker block, the struck
 * entity or the potion holder. Reads answer through {@link Variables} by
 * name and through the typed accessors; world actions are host methods,
 * so a test drives a program against a mock with no level behind it.
 *
 * <p>This is the core every host has. Each {@link HostCapability} is an
 * interface extending it, and a host implements the ones it provides, so
 * {@link HostKind#capabilities()} is read off the host's type and
 * {@link ProgramBehavior#forHost} refuses at load a step whose capability
 * the host does not implement (decision capability-interfaces-derive-host-kind).
 */
public interface StepHost extends Variables {

    /**
     * Returns which kind of host this is, which names its capabilities
     * and variables.
     *
     * @return the host kind
     */
    HostKind kind();

    /**
     * Returns the block the program acts from: the marker block, or the
     * block the struck entity stands in.
     *
     * @return the anchor position
     */
    BlockPos position();

    /**
     * Spawns a burst of particles at the host's anchor. A step anchoring
     * at {@link FxAnchor#TARGET} needs {@link HostCapability#TARGET}, whose
     * host anchors at its target.
     *
     * @param burst the evaluated burst
     */
    void spawnParticles(ParticleBurst burst);

    /**
     * Plays a sound at the host's anchor. A step anchoring at
     * {@link FxAnchor#TARGET} needs {@link HostCapability#TARGET}, whose
     * host anchors at its target.
     *
     * @param cue the evaluated sound
     */
    void playSound(SoundCue cue);
}
