package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A host frost can pulse from: the level it stands in and the point a nova
 * spreads out of, around which the host's entity scan selects. The player
 * releasing Nova and the block a frost tap drips onto are both such hosts
 * (decisions nova-ring-grows-with-the-hold, nova-drip-pulses-a-short-lasting-freeze).
 */
public interface FrostHost extends EntityScanHost {

    /**
     * Returns the level the frost spreads in.
     *
     * @return the server level
     */
    ServerLevel level();

    /**
     * Returns the point the frost spreads out of.
     *
     * @return the center in world coordinates
     */
    Vec3 frostCenter();
}
