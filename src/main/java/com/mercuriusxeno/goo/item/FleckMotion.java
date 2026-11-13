package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * A dropped goo item moving as a fleck of goo, wired into vanilla's item
 * tick: each face its move strikes sends it back off by its type's
 * elasticity, or, once it has spent its bounce on a wall or a ceiling, holds
 * it there clinging by its viscosity; on a floor it rolls on, damped by its
 * viscosity, where vanilla would stop it by the block's friction and bounce
 * it by its own half. It stays an item entity throughout, so pickup, Vacuum,
 * Grab and the goo merge reach it as they reach any item. Any other item
 * moves as vanilla moves it.
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
    /**
     * The slowest strike, in blocks a tick, that sticks a fleck to a wall or
     * a ceiling; a clinging fleck's own push against its face stays under it.
     */
    static final double MIN_CLING_STRIKE = 0.02;
    /** How far past its box, in blocks, a clinging fleck feels for the face behind it. */
    private static final double FACE_PROBE = 0.05;
    /** Vanilla's vertical drag on an item each tick. */
    private static final double VERTICAL_DRAG = 0.98;

    private FleckMotion() {
    }

    /**
     * The movement a goo item makes this tick: a clinging fleck's cling step,
     * else vanilla's.
     *
     * @param self  the item entity about to move
     * @param delta vanilla's movement
     * @return the movement to make
     */
    public static Vec3 beforeMove(ItemEntity self, Vec3 delta) {
        FleckCling.Cling cling = ((FleckHost) self).goo$cling();
        if (cling == null) {
            return delta;
        }
        Optional<GooTypeDefinition> type = typeOf(self);
        if (type.isEmpty()) {
            ((FleckHost) self).goo$setCling(null);
            return delta;
        }
        FleckCling.Step step = FleckCling.stepCling(cling, self.getDeltaMovement(), type.get().viscosity(),
                faceHolds(self, cling.face()));
        ((FleckHost) self).goo$setCling(step.cling());
        self.setDeltaMovement(step.velocity());
        return step.cling() == null ? delta : step.velocity();
    }

    /**
     * Answers each face a goo item's last move struck: a floor rebounds it
     * by its elasticity, a wall or a ceiling rebounds it or, once it has
     * spent its bounce, takes its cling. A fleck that reached the floor lets
     * go of any face.
     *
     * @param self   the item entity vanilla just moved
     * @param before its velocity before the move
     */
    public static void afterMove(ItemEntity self, Vec3 before) {
        typeOf(self).ifPresent(type -> answerStruckFaces(self, before, type.elasticity()));
        if (self.onGround()) {
            ((FleckHost) self).goo$setCling(null);
        }
    }

    /**
     * The velocity a goo item keeps after vanilla's drag: on a floor it rolls
     * on, damped by its viscosity in place of the block's friction.
     *
     * @param self    the item entity
     * @param dragged vanilla's velocity after its drag and friction
     * @return the velocity it keeps
     */
    public static Vec3 afterDrag(ItemEntity self, Vec3 dragged) {
        if (!self.onGround()) {
            return dragged;
        }
        return typeOf(self).map(type -> {
            Vec3 rolled = FleckCling.rollOn(self.getDeltaMovement(), type.viscosity());
            return rolled.with(Direction.Axis.Y, rolled.y * VERTICAL_DRAG);
        }).orElse(dragged);
    }

    /**
     * The velocity a goo item keeps where vanilla would bounce an item on the
     * ground by half its fall: its own rebound already answered the floor.
     *
     * @param self    the item entity
     * @param bounced vanilla's bounced velocity
     * @return the velocity it keeps
     */
    public static Vec3 groundBounce(ItemEntity self, Vec3 bounced) {
        return typeOf(self).isPresent() ? self.getDeltaMovement() : bounced;
    }

    private static Optional<GooTypeDefinition> typeOf(ItemEntity self) {
        ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(self.getItem());
        if (type == null) {
            return Optional.empty();
        }
        return self.level().registryAccess().get(type).map(holder -> holder.value());
    }

    private static boolean faceHolds(ItemEntity self, Direction face) {
        Vec3 into = Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(FACE_PROBE);
        return !self.level().noCollision(self, self.getBoundingBox().move(into));
    }

    private static void answerStruckFaces(ItemEntity self, Vec3 before, float elasticity) {
        Vec3 velocity = self.getDeltaMovement();
        boolean changed = false;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double approach = before.get(axis);
            if (velocity.get(axis) != 0 || Math.abs(approach) <= MIN_CLING_STRIKE) {
                continue;
            }
            Direction face = Direction.fromAxisAndDirection(axis,
                    approach > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            if (face != Direction.DOWN && FleckCling.spentItsBounce(approach * elasticity)) {
                if (((FleckHost) self).goo$cling() == null) {
                    ((FleckHost) self).goo$setCling(new FleckCling.Cling(face, 0));
                }
            } else if (Math.abs(approach) > MIN_REBOUND_SPEED) {
                velocity = FleckRebound.rebound(velocity.with(axis, approach), Vec3.ZERO.with(axis, 1), elasticity);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        self.setDeltaMovement(velocity);
        if (velocity.y > 0) {
            // vanilla skips moving an item that stands on the ground with no sideways speed, which would eat the rebound
            self.setOnGround(false);
        }
    }
}
