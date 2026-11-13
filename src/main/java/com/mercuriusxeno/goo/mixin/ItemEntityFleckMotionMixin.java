package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.item.FleckCling;
import com.mercuriusxeno.goo.item.FleckHost;
import com.mercuriusxeno.goo.item.FleckMotion;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * A dropped goo item moves as a fleck of goo: its tick's move is a clinging
 * fleck's cling step and hands the velocity it struck with to the fleck's
 * motion, which rebounds it or sticks it by its type's elasticity and
 * viscosity; on a floor its viscosity damps it in place of the block's
 * friction and vanilla's half bounce.
 * decision flecks-move-by-flexible-goo-physics-and-stay-items
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityFleckMotionMixin implements FleckHost {

    private static final String SET_DELTA_MOVEMENT =
            "Lnet/minecraft/world/entity/item/ItemEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V";

    /** The face the fleck clings to, or null for none. */
    @Unique
    private FleckCling.@Nullable Cling goo$cling;

    @Override
    public FleckCling.@Nullable Cling goo$cling() {
        return goo$cling;
    }

    @Override
    public void goo$setCling(FleckCling.@Nullable Cling cling) {
        goo$cling = cling;
    }

    /**
     * Moves the item as the fleck's motion says, then lets it answer each
     * face the move struck.
     *
     * @param self  the item entity
     * @param type  the mover
     * @param delta vanilla's movement
     * @param move  vanilla's move
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;move(Lnet/minecraft/world/entity/MoverType;"
                    + "Lnet/minecraft/world/phys/Vec3;)V"))
    private void goo$movesAsAFleck(ItemEntity self, MoverType type, Vec3 delta, Operation<Void> move) {
        Vec3 moving = FleckMotion.beforeMove(self, delta);
        Vec3 before = self.getDeltaMovement();
        move.call(self, type, moving);
        FleckMotion.afterMove(self, before);
    }

    /**
     * Keeps a goo item's own floor damping in place of vanilla's friction.
     *
     * @param self     the item entity
     * @param dragged  vanilla's velocity after its drag
     * @param original vanilla's setter
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = SET_DELTA_MOVEMENT, ordinal = 0))
    private void goo$dragsAsAFleck(ItemEntity self, Vec3 dragged, Operation<Void> original) {
        original.call(self, FleckMotion.afterDrag(self, dragged));
    }

    /**
     * Keeps a goo item's own rebound in place of vanilla's half bounce on the ground.
     *
     * @param self     the item entity
     * @param bounced  vanilla's bounced velocity
     * @param original vanilla's setter
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = SET_DELTA_MOVEMENT, ordinal = 1))
    private void goo$landsAsAFleck(ItemEntity self, Vec3 bounced, Operation<Void> original) {
        original.call(self, FleckMotion.groundBounce(self, bounced));
    }
}
