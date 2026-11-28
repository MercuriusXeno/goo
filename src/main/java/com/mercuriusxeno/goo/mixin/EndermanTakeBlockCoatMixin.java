package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.durable.CoatGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * An enderman takes no block Coat stands against, a take vanilla asks of no
 * hook: the block reads as one it cannot hold.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockCoatMixin {

    /**
     * Reads a coated block as one no enderman holds.
     *
     * @param state    the block the enderman reaches for
     * @param tag      the holdable tag
     * @param original vanilla's read
     * @param pos      the block's position
     * @param level    the enderman's level
     * @return whether the enderman may take the block
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean goo$holdsNoCoatedBlock(BlockState state, TagKey<Block> tag, Operation<Boolean> original,
                                           @Local BlockPos pos, @Local Level level) {
        return original.call(state, tag) && !CoatGuard.wraps(level, pos);
    }
}
