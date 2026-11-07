package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * Opens the blocks a linked gate lies on to a body passing into it, so it
 * walks through the gate's face into the space beyond. The shape is changed
 * only once the method and every other change to it have run, and only for
 * a body at a linked gate's face within its oval, so it composes with any
 * other mod's change to block collision.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateGatePassageMixin {

    /**
     * Answers no collision for a gate's block to a body passing into the
     * gate, and the shape as it stood to every other body.
     *
     * @param shape   the shape the method and every other change to it answered
     * @param level   the level
     * @param pos     the block
     * @param context the collision context
     * @return the shape the body collides with
     */
    @ModifyReturnValue(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/"
            + "BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"))
    private VoxelShape goo$openToAPassingBody(VoxelShape shape, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        boolean passing = context instanceof EntityCollisionContext bodies && bodies.getEntity() != null
                && GatePassage.passable(bodies.getEntity(), pos);
        return passing ? Shapes.empty() : shape;
    }
}
