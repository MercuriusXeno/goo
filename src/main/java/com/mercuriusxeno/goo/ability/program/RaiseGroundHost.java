package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.BuildGrowth;

/**
 * A host holding the stone a Build grows: the footprint the caster submitted
 * and the server planned, at the rise the caster's goo paid for (capability
 * {@link HostCapability#RAISE_GROUND}).
 * decision spire-grows-stone-out-of-the-top
 */
public interface RaiseGroundHost extends AnchoredWorldHost {

    /**
     * @return the planned growth, its columns and its paid rise
     */
    BuildGrowth growth();
}
