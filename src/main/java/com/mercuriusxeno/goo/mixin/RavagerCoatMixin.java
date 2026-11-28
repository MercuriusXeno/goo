package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.durable.CoatGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * A ravager pushing through leaves breaks none that Coat stands against, a
 * break vanilla asks of no hook.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
@Mixin(Ravager.class)
public abstract class RavagerCoatMixin {

    /**
     * Breaks the leaves the ravager pushes into unless Coat wraps them.
     *
     * @param level    the ravager's level
     * @param pos      the leaves
     * @param drop     whether the leaves drop
     * @param breaker  the ravager
     * @param original vanilla's break
     * @return whether the leaves broke
     */
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;Z"
                    + "Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean goo$sparesCoatedLeaves(ServerLevel level, BlockPos pos, boolean drop, Entity breaker,
                                           Operation<Boolean> original) {
        return !CoatGuard.wraps(level, pos) && original.call(level, pos, drop, breaker);
    }
}
