package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.GloomDark;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Gloom's goo is no obstruction to a mob spawning in it. A natural spawn
 * refuses a spot whose mob box meets any collision, and a thin cell's box
 * lies across the feet of a mob standing on its floor; the goo is a
 * pseudo-liquid, so where the feet stand in Gloom the test reruns with the
 * goo's own cells left out, and every other collision still refuses it.
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 * operator ruling 2026-11-18: Gloom's goo is no obstruction to a spawn
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerGloomSpaceMixin {

    /**
     * Frees a spawn box that only Gloom's cells obstruct.
     *
     * @param level    the level the mob spawns in
     * @param box      the mob's spawn box
     * @param original vanilla's collision test
     * @return true where the box is free for the mob
     */
    @WrapOperation(method = "isValidSpawnPostitionForType", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/phys/AABB;)Z"))
    private static boolean goo$gloomIsNoObstruction(ServerLevel level, AABB box, Operation<Boolean> original) {
        return GloomDark.spawnSpaceIsFree(level, box, original.call(level, box));
    }
}
