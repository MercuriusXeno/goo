package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HeldSweepPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;

/**
 * A held step that sweeps: Scry, Glitter and Magnet each pulse at the one
 * shared pace and send the caster one payload a tick, which the caster's
 * client draws through the one held-sweep visual.
 * decision held-sweeps-share-one-fast-shell-visual
 */
public interface HeldSweepStep extends Step {

    /**
     * The sweep's pace and shell.
     *
     * @return the pace
     */
    HeldSweepPace pace();

    /**
     * The goo type whose colors tint the sweep.
     *
     * @return the goo type
     */
    ResourceKey<GooTypeDefinition> goo();

    /**
     * The payload drawing one tick of the sweep.
     *
     * @param origin    where the pulse began
     * @param pulseTick the pulse's tick, 1 on its first
     * @return the payload for the caster's client
     */
    default HeldSweepPayload sweepAt(Vec3 origin, int pulseTick) {
        HeldSweepPace pace = pace();
        return new HeldSweepPayload(origin, (float) pace.radiusAt(pulseTick), (float) pace.range(),
                pace.strengthAt(pulseTick), goo());
    }
}
