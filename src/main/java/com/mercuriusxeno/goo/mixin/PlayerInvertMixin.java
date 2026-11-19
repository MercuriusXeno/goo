package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.nether.CeilingGround;
import com.mercuriusxeno.goo.ability.nether.Invert;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * A player whose gravity Invert turns over sneaks to the ceiling's edge and
 * holds there, as vanilla's sneak holds at a floor's edge, and changes pose
 * from its top down, so standing up from a crouch on the ceiling reads the
 * room below the body rather than the ceiling above it.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@Mixin(Player.class)
public abstract class PlayerInvertMixin {

    /** Vanilla's margin off each side of the probe a sneak's edge check reads. */
    @Unique
    private static final double GOO_EDGE_MARGIN = 1.0E-7;

    /**
     * Holds an inverted player's sneak at the ceiling's edge: vanilla's
     * edge check runs on the move turned over, its fall reading the ceiling.
     *
     * @param delta     the move asked for
     * @param moverType what moves the player
     * @param original  vanilla's edge check
     * @return the move held at the edge
     */
    @WrapMethod(method = "maybeBackOffFromEdge")
    private Vec3 goo$holdsAtTheCeilingsEdge(Vec3 delta, MoverType moverType, Operation<Vec3> original) {
        if (!Invert.isInverted((Player) (Object) this)) {
            return original.call(delta, moverType);
        }
        Vec3 held = original.call(new Vec3(delta.x, -delta.y, delta.z), moverType);
        return new Vec3(held.x, -held.y, held.z);
    }

    /**
     * Reads whether an inverted player would fall off the ceiling at an
     * offset, through the probe over its top.
     *
     * @param deltaX    the offset east
     * @param deltaZ    the offset south
     * @param minHeight the fall that counts
     * @param original  vanilla's probe under the feet
     * @return true where nothing stands over the probe
     */
    @WrapMethod(method = "canFallAtLeast")
    private boolean goo$canFallOffTheCeiling(double deltaX, double deltaZ, double minHeight,
                                             Operation<Boolean> original) {
        Player self = (Player) (Object) this;
        if (!Invert.isInverted(self)) {
            return original.call(deltaX, deltaZ, minHeight);
        }
        AABB body = self.getBoundingBox();
        AABB under = new AABB(body.minX + GOO_EDGE_MARGIN + deltaX, body.minY - minHeight - GOO_EDGE_MARGIN,
                body.minZ + GOO_EDGE_MARGIN + deltaZ, body.maxX - GOO_EDGE_MARGIN + deltaX, body.minY,
                body.maxZ - GOO_EDGE_MARGIN + deltaZ);
        return self.level().noCollision(self, CeilingGround.mirrorAbove(under, body));
    }

    /**
     * Fits an inverted player's new pose hanging from its top, where its top
     * stays as the pose changes.
     *
     * @param position the player's position
     * @param newPose  the pose fitted
     * @return the position the new pose's box stands at
     */
    @ModifyArg(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityDimensions;makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;"))
    private Vec3 goo$fitsFromTheTop(Vec3 position, @Local(argsOnly = true) Pose newPose) {
        Player self = (Player) (Object) this;
        if (!Invert.isInverted(self)) {
            return position;
        }
        return position.add(0, self.getBbHeight() - self.getDimensions(newPose).height(), 0);
    }
}
