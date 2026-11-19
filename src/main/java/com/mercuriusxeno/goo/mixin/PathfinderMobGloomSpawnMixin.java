package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.GloomDark;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * A monster spawning on Gloom is not refused for the light. A spawning mob's
 * own rules ask that its walk target value at its spot stand at zero or
 * more, and a monster's value falls below zero as the light rises; on Gloom
 * the value the spawn reads is no lower than zero. The walk target value the
 * monster's pathing reads is untouched.
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 */
@Mixin(PathfinderMob.class)
public abstract class PathfinderMobGloomSpawnMixin {

    /**
     * Reads a monster's walk target value at its spawn spot as dark where Gloom lies there.
     *
     * @param value vanilla's walk target value at the spot
     * @param level the level the mob spawns in
     * @return the value the spawn rule compares against zero
     */
    @ModifyExpressionValue(method = "checkSpawnRules", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/PathfinderMob;getWalkTargetValue"
                    + "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/LevelReader;)F"))
    private float goo$gloomReadsDarkToASpawningMonster(float value, @Local(argsOnly = true) LevelAccessor level) {
        PathfinderMob self = (PathfinderMob) (Object) this;
        return self instanceof Monster ? GloomDark.spawnWalkTargetValue(level, self.blockPosition(), value) : value;
    }
}
