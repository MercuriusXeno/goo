package com.mercuriusxeno.goo.network;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a mob-first stream's motes fly: from the glove into the mob its
 * swarm bites, or into the block under the crosshair where it bites none,
 * each mote to its own point inside, so the swarm converges on its target
 * (decision decay-gnats-degrade-each-block-once).
 */
final class SwarmMotes {

    private SwarmMotes() {
    }

    /**
     * The box the motes fly into this tick.
     *
     * @param bitten    the mob the swarm bites, alone, or none
     * @param crosshair the block the crosshair lands on, or a miss at the stream's reach
     * @return the bitten mob's box, else the crosshair block's cell, else the point at the stream's reach
     */
    static AABB moteTarget(List<LivingEntity> bitten, BlockHitResult crosshair) {
        if (!bitten.isEmpty()) {
            return bitten.getFirst().getBoundingBox();
        }
        if (crosshair.getType() == HitResult.Type.BLOCK) {
            return new AABB(crosshair.getBlockPos());
        }
        return new AABB(crosshair.getLocation(), crosshair.getLocation());
    }

    /**
     * Each mote's flight from the glove, a vector from the apex to the point
     * inside the target it comes to rest at.
     *
     * @param apex   the glove hand
     * @param target the box the motes fly into
     * @param count  how many motes
     * @param random the level's random
     * @return the flights, one a mote
     */
    static List<Vec3> moteFlights(Vec3 apex, AABB target, int count, RandomSource random) {
        List<Vec3> flights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vec3 rest = new Vec3(target.minX + random.nextDouble() * target.getXsize(),
                    target.minY + random.nextDouble() * target.getYsize(),
                    target.minZ + random.nextDouble() * target.getZsize());
            flights.add(rest.subtract(apex));
        }
        return flights;
    }
}
