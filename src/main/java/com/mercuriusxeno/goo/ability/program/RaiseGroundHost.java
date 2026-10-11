package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.SpireLift;

/**
 * A host holding the ground a Spire lifts: the footprint the caster submitted
 * and the server planned, at the rise the caster's goo paid for (capability
 * {@link HostCapability#RAISE_GROUND}).
 * decision spire-rips-walls-and-platforms
 */
public interface RaiseGroundHost extends AnchoredWorldHost {

    /**
     * @return the planned lift, its columns and its paid rise
     */
    SpireLift lift();
}
