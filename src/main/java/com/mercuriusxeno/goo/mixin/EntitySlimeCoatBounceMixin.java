package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A landing on a slime coat bounces as the coat bounces it. Minecraft 26.2
 * dropped the block's say over an entity's motion after it falls on the
 * block and bounces a landing by the block's restitution alone, which
 * cannot ramp a held jump; the coat takes the motion its lander fell with
 * once the game's restitution has stilled it, as 26.1's fall-on hook handed
 * it the lander.
 * bounce-ramps-jump-height-while-jump-is-held
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
@Mixin(Entity.class)
public abstract class EntitySlimeCoatBounceMixin {

    @Shadow
    public boolean verticalCollision;

    @Unique
    private Vec3 goo$landingMotion = Vec3.ZERO;

    @Shadow
    public abstract Vec3 getDeltaMovement();

    /**
     * Keeps the motion the entity collided with, before the game's restitution changes it.
     *
     * @param effectPos   the block the landing acts on
     * @param effectState that block's state
     * @param xCollision  whether it collided along x
     * @param zCollision  whether it collided along z
     * @param movement    the movement it asked for
     * @param ci          the callback
     */
    @Inject(method = "restituteMovementAfterCollisions(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"))
    private void goo$keepLandingMotion(BlockPos effectPos, BlockState effectState, boolean xCollision,
                                       boolean zCollision, Vec3 movement, CallbackInfo ci) {
        goo$landingMotion = getDeltaMovement();
    }

    /**
     * Hands a slime coat the landing it caught, once the game's restitution has run.
     *
     * @param effectPos   the block the landing acts on
     * @param effectState that block's state
     * @param xCollision  whether it collided along x
     * @param zCollision  whether it collided along z
     * @param movement    the movement it asked for
     * @param ci          the callback
     */
    @Inject(method = "restituteMovementAfterCollisions(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V",
            at = @At("TAIL"))
    private void goo$bounceOffTheCoat(BlockPos effectPos, BlockState effectState, boolean xCollision,
                                      boolean zCollision, Vec3 movement, CallbackInfo ci) {
        if (verticalCollision && effectState.getBlock() instanceof VolumeCellBlock cell) {
            cell.bounceLanding((Entity) (Object) this, goo$landingMotion);
        }
    }
}
