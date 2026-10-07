package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;

/**
 * A one-tick host anchored at a point that can hand a sprayed-floor host
 * for each open floor around its anchor (capability
 * {@link HostCapability#FLOOR_SCAN}); Colonize buds the ground around a
 * landing on no network this way (decision colonize-blob-grows-the-network).
 */
public interface FloorScanHost extends StepHost {

    /**
     * Returns the server level the host acts in.
     *
     * @return the level
     */
    ServerLevel level();

    /**
     * Returns the point the floors are counted from.
     *
     * @return the anchor
     */
    Vec3 anchor();

    /**
     * Hands a sprayed-floor host for each open floor within the radius of the anchor.
     *
     * @param radius the reach in blocks
     * @param body   what to run on each floor's host
     */
    default void forEachFloorWithin(double radius, Consumer<StepHost> body) {
        Vec3 from = anchor();
        for (var floor : FloorReach.inSphere(from, radius, cell -> FloorReach.isOpenFloor(level(), cell))) {
            body.accept(new SurfaceHost(level(), floor, Vec3.atCenterOf(floor.above()).distanceTo(from)));
        }
    }
}
