package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;

/**
 * A host keeping an agitate step's countdown across ticks, and the level it
 * stirs monsters in (capability {@link HostCapability#AGITATE}).
 * agitator-prism-quickens-until-a-spawn
 */
public interface AgitateHost extends StepHost {

    /**
     * Returns the server level the host stands in.
     *
     * @return the level
     */
    ServerLevel level();

    /**
     * Returns the countdown the host keeps for a running agitate step.
     *
     * @return the live state, mutated in place by the step
     */
    AgitationState agitation();
}
