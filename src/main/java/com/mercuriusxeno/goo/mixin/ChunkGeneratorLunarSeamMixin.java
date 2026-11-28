package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarSeam;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Keeps lunar's generated features and structures clear of the seam, so none places a block across it: a chunk whose
 * features could write across the seam goes undecorated, and a structure within a feature's reach of it never starts.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLunarSeamMixin {

    /**
     * Leaves a lunar chunk beside the seam undecorated.
     *
     * @param level            the level being decorated
     * @param chunk            the chunk being decorated
     * @param structureManager the structure manager
     * @param ci               the callback
     */
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void goo$leavesTheSeamUndecorated(WorldGenLevel level, ChunkAccess chunk,
                                              StructureManager structureManager, CallbackInfo ci) {
        if (level.getLevel().dimension() == Lunar.LEVEL && !LunarSeam.featuresMayDecorate(chunk.getPos())) {
            ci.cancel();
        }
    }

    /**
     * Refuses a lunar structure standing within a feature's reach of the seam.
     *
     * @param start the structure's start as it generated
     * @param level the level it generates in
     * @return the start, or no start beside the seam
     */
    @ModifyExpressionValue(method = "tryGenerateStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/Structure;generate(Lnet/minecraft/core/Holder;"
                    + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;"
                    + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;"
                    + "Lnet/minecraft/world/level/biome/Climate$Sampler;Lnet/minecraft/world/level/levelgen/RandomState;"
                    + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;"
                    + "JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;"
                    + "Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
    private StructureStart goo$refusesAStructureAtTheSeam(StructureStart start,
                                                          @Local(argsOnly = true) ResourceKey<Level> level) {
        if (level == Lunar.LEVEL && start.isValid() && !LunarSeam.structureMayStand(start.getBoundingBox())) {
            return StructureStart.INVALID_START;
        }
        return start;
    }
}
