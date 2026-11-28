package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarSeam;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Reads and writes a lunar block past the seam on the server as the block it wraps to, so a block a player places or
 * breaks across the seam lands on the real chunk at the other edge, the chunk the player sees there.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(Level.class)
public abstract class LevelLunarSeamMixin {

    @Shadow
    public abstract boolean isClientSide();

    @Shadow
    public abstract ResourceKey<Level> dimension();

    /**
     * Reads the block past the seam as the one it wraps to.
     *
     * @param pos the block asked for
     * @return the block as the moon reads it
     */
    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockPos goo$readsTheBlockAcrossTheSeam(BlockPos pos) {
        return goo$wrapped(pos);
    }

    /**
     * Reads the fluid past the seam as the one it wraps to.
     *
     * @param pos the block asked for
     * @return the block as the moon reads it
     */
    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true)
    private BlockPos goo$readsTheFluidAcrossTheSeam(BlockPos pos) {
        return goo$wrapped(pos);
    }

    /**
     * Reads the block entity past the seam as the one it wraps to.
     *
     * @param pos the block asked for
     * @return the block as the moon reads it
     */
    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true)
    private BlockPos goo$readsTheBlockEntityAcrossTheSeam(BlockPos pos) {
        return goo$wrapped(pos);
    }

    /**
     * Sets a block past the seam as the block it wraps to.
     *
     * @param pos the block asked for
     * @return the block as the moon reads it
     */
    @ModifyVariable(method = "setBlock(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), argsOnly = true)
    private BlockPos goo$setsTheBlockAcrossTheSeam(BlockPos pos) {
        return goo$wrapped(pos);
    }

    @Unique
    private BlockPos goo$wrapped(BlockPos pos) {
        return isClientSide() || dimension() != Lunar.LEVEL ? pos : LunarSeam.reach(pos);
    }
}
