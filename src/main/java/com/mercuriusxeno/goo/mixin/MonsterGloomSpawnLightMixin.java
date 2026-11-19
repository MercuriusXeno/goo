package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.GloomDark;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A spot on Gloom is dark enough for a hostile mob to spawn. Monster's spawn
 * placement rules ask isDarkEnoughToSpawn after the difficulty and before
 * the rest of the mob's rules; vanilla still answers first, drawing its
 * random numbers as it would, and Gloom turns a refusal into a pass.
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 */
@Mixin(Monster.class)
public abstract class MonsterGloomSpawnLightMixin {

    /**
     * Passes the light check where Gloom lies at the spot.
     *
     * @param level  the level the mob spawns in
     * @param pos    the block its feet stand in
     * @param random the spawner's random
     * @param cir    vanilla's answer, replaced by Gloom's
     */
    @Inject(method = "isDarkEnoughToSpawn", at = @At("RETURN"), cancellable = true)
    private static void goo$gloomIsDarkEnough(ServerLevelAccessor level, BlockPos pos, RandomSource random,
                                             CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(GloomDark.spawnLightPasses(level, pos, cir.getReturnValueZ()));
    }
}
