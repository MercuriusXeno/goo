package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.item.FleckMotion;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * A dropped goo item moves as a fleck of goo: the move its tick makes hands
 * the velocity it struck with to the fleck's motion, which rebounds it off
 * each face by its type's elasticity.
 * decision flecks-move-by-flexible-goo-physics-and-stay-items
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityFleckMotionMixin {

    /**
     * Moves the item as vanilla does, then lets the fleck's motion answer
     * each face the move struck.
     *
     * @param self  the item entity
     * @param type  the mover
     * @param delta the movement
     * @param move  vanilla's move
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;move(Lnet/minecraft/world/entity/MoverType;"
                    + "Lnet/minecraft/world/phys/Vec3;)V"))
    private void goo$movesAsAFleck(ItemEntity self, MoverType type, Vec3 delta, Operation<Void> move) {
        Vec3 before = self.getDeltaMovement();
        move.call(self, type, delta);
        FleckMotion.afterMove(self, before);
    }
}
