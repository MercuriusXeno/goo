package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.nether.CeilingGround;
import com.mercuriusxeno.goo.ability.nether.CeilingStep;
import com.mercuriusxeno.goo.ability.nether.Invert;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.Optional;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * An entity whose gravity Invert turns over takes the ceiling for its ground
 * wherever vanilla reads the ground under its feet: a move up the ceiling
 * stops grounds it, the block it stands on and walks on is the one over its
 * top, a fall up counts toward its landing, and it steps down onto a block
 * hanging from the ceiling as an upright entity steps up onto a slab.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@Mixin(Entity.class)
public abstract class EntityInvertMixin {

    /** Vanilla's offset for the block an entity stands on itself, under which it answers that block. */
    @Unique
    private static final float GOO_SUPPORTING_OFFSET = 1.0E-5F;

    /**
     * Grounds an inverted entity on the ceiling: the collision below that
     * grounds vanilla's entity is, for it, the collision above.
     *
     * @param self     the entity
     * @param below    vanilla's answer
     * @param original the field write
     * @param asked    the move asked for
     */
    @WrapOperation(method = "move", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/Entity;verticalCollisionBelow:Z", opcode = Opcodes.PUTFIELD))
    private void goo$groundsOnTheCeiling(Entity self, boolean below, Operation<Void> original,
                                         @Local(argsOnly = true) Vec3 asked) {
        original.call(self, Invert.isInverted(self)
                ? CeilingGround.contact(self.verticalCollision, asked.y, true) == CeilingGround.Contact.GROUNDED
                : below);
    }

    /**
     * Counts an inverted entity's moves up toward its fall, as vanilla counts moves down.
     *
     * @param ya the vertical move
     * @return the move as the entity reads it
     */
    @ModifyArg(method = {"move", "doCheckFallDamage"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"),
            index = 0)
    private double goo$fallsUp(double ya) {
        return CeilingGround.towardGround(ya, Invert.isInverted((Entity) (Object) this));
    }

    /**
     * Finds an inverted entity's supporting block over its top.
     *
     * @param probe vanilla's probe under the feet
     * @return the probe the entity reads its support through
     */
    @ModifyArg(method = "checkSupportingBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;findSupportingBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/Optional;"),
            index = 1)
    private AABB goo$supportedFromAbove(AABB probe) {
        Entity self = (Entity) (Object) this;
        return Invert.isInverted(self) ? CeilingGround.mirrorAbove(probe, self.getBoundingBox()) : probe;
    }

    /**
     * Answers the block over an inverted entity's top wherever vanilla reads
     * the block under its feet: the block stepped on, walked on for friction
     * and speed, landed on and jumped from.
     *
     * @param onPos  vanilla's block under the feet
     * @param offset how far into the ground the reading reaches
     * @return the block the entity stands on at that reach
     */
    @ModifyReturnValue(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("RETURN"))
    private BlockPos goo$standsOnTheCeiling(BlockPos onPos, @Local(argsOnly = true) float offset) {
        Entity self = (Entity) (Object) this;
        if (!Invert.isInverted(self)) {
            return onPos;
        }
        Optional<BlockPos> supporting = self.mainSupportingBlockPos;
        if (supporting.isPresent() && offset <= GOO_SUPPORTING_OFFSET) {
            return supporting.get();
        }
        return new BlockPos(Mth.floor(self.getX()), CeilingGround.groundBlockY(self.getBoundingBox().maxY, offset),
                Mth.floor(self.getZ()));
    }

    /**
     * Leaves an inverted entity no step up off the floor, its step being taken down from the ceiling.
     *
     * @param maxUpStep vanilla's step height
     * @return the step height vanilla's step up reads
     */
    @ModifyExpressionValue(method = "collide", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;maxUpStep()F"))
    private float goo$noStepUpOffTheFloor(float maxUpStep) {
        return Invert.isInverted((Entity) (Object) this) ? 0F : maxUpStep;
    }

    /**
     * Takes an inverted entity's step down from the ceiling.
     *
     * @param asked    the move asked for
     * @param original vanilla's collision
     * @return the move to make
     */
    @WrapMethod(method = "collide")
    private Vec3 goo$stepsAlongTheCeiling(Vec3 asked, Operation<Vec3> original) {
        Vec3 collided = original.call(asked);
        Entity self = (Entity) (Object) this;
        return Invert.isInverted(self) ? CeilingStep.step(self, asked, collided) : collided;
    }
}
