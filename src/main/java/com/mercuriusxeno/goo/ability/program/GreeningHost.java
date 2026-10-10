package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A host that greens the world around it tick after tick (capability
 * {@link HostCapability#GREENING}): the prism a verdant combo runs on, which
 * turns the blocks in its reach green and nudges its crops.
 * verdant-prism-greens-blocks-slowly
 */
public interface GreeningHost extends StepHost {

    /**
     * Returns the server level the host greens.
     *
     * @return the level
     */
    ServerLevel level();

    /**
     * Returns the point the greening reaches out from and its breeze puffs from.
     *
     * @return the center
     */
    Vec3 center();
}
