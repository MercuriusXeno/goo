package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * A dropped goo item moving as a fleck of goo: after vanilla moves it, each
 * block face it struck sends it back off by its type's elasticity, where
 * vanilla would stop it dead or bounce it by its own half. It stays an item
 * entity throughout, so pickup, Vacuum, Grab and the goo merge reach it as
 * they reach any item.
 * decision flecks-move-by-flexible-goo-physics-and-stay-items
 * decision each-goo-type-carries-elasticity-and-viscosity
 */
public final class FleckMotion {

    /**
     * The slowest strike, in blocks a tick, a fleck rebounds from; slower
     * than this it settles on the face, so a resting fleck's tick of gravity
     * never hops it.
     */
    static final double MIN_REBOUND_SPEED = 0.1;

    private FleckMotion() {
    }

    /**
     * Rebounds a goo item off each face its last move struck. Any other item
     * moves as vanilla moves it.
     *
     * @param self   the item entity vanilla just moved
     * @param before its velocity before the move
     */
    public static void afterMove(ItemEntity self, Vec3 before) {
        ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(self.getItem());
        if (type == null) {
            return;
        }
        Optional<Float> elasticity = self.level().registryAccess().get(type)
                .map(holder -> holder.value().elasticity());
        elasticity.ifPresent(share -> reboundOffStruckFaces(self, before, share));
    }

    private static void reboundOffStruckFaces(ItemEntity self, Vec3 before, float elasticity) {
        Vec3 velocity = self.getDeltaMovement();
        boolean struck = false;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double approach = before.get(axis);
            if (velocity.get(axis) == 0 && Math.abs(approach) > MIN_REBOUND_SPEED) {
                Vec3 normal = Vec3.ZERO.with(axis, 1);
                velocity = FleckRebound.rebound(velocity.with(axis, approach), normal, elasticity);
                struck = true;
            }
        }
        if (!struck) {
            return;
        }
        self.setDeltaMovement(velocity);
        if (velocity.y > 0) {
            // vanilla skips moving an item that stands on the ground with no sideways speed, which would eat the rebound
            self.setOnGround(false);
        }
    }
}
