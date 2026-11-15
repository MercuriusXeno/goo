package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The model every volume cell's layers draw through, for slime, slick and
 * every later world goo alike: the layered shell its variant names, and
 * inside it the goo slime core the cell holds, drawn from what the model
 * reads as the cell's section meshes, so the core adds nothing to the block
 * state. The core samples the shell's own fluid sprite, the goo type's.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * slime-core-keeps-a-two-layer-gap-and-grows-with-fill
 *
 * @param shell the layered shell the cell's layers draw as
 * @param core  the core's material: the shell's fluid sprite, untinted, on the solid layer
 */
public record SlimeCoreModel(BlockStateModel shell, BakedQuad.MaterialInfo core) implements DynamicBlockStateModel {

    /** The type a block state file names to draw a cell's layers through this model. */
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(Goo.MODID, "slime_core");

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        shell.collectParts(level, pos, state, random, parts);
        List<BakedQuad> quads = SlimeCoreQuads.quadsOf(SlimeCoreBox.of(VolumeCellBlock.layersOf(state)), core);
        if (!quads.isEmpty()) {
            parts.add(new CorePart(quads, shell.particleMaterial(level, pos, state)));
        }
    }

    @Override
    @Deprecated
    public Material.Baked particleMaterial() {
        return shell.particleMaterial();
    }

    @Override
    @Deprecated
    public int materialFlags() {
        return shell.materialFlags() | core.flags();
    }

    /**
     * The core's quads as a model part, drawn whatever its neighbours, since
     * the core never reaches a face its block shares.
     *
     * @param quads    the core's quads
     * @param particle the shell's particle material
     */
    private record CorePart(List<BakedQuad> quads, Material.Baked particle) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? quads : List.of();
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        public int materialFlags() {
            return quads.isEmpty() ? 0 : quads.getFirst().materialInfo().flags();
        }
    }

    /**
     * The model as a block state file names it: the shell variant whose
     * layers the core sits inside.
     *
     * @param shell the shell's variant
     */
    public record Unbaked(Variant shell) implements CustomUnbakedBlockStateModel {

        /** Reads {@code {"type": "goo:slime_core", "shell": {"model": ...}}}. */
        public static final MapCodec<Unbaked> MAP_CODEC = Variant.CODEC.fieldOf("shell")
                .xmap(Unbaked::new, Unbaked::shell);

        @Override
        public MapCodec<Unbaked> codec() {
            return MAP_CODEC;
        }

        // baking reads the shell's particle with no level to hand it, which only the deprecated overload takes
        @SuppressWarnings("deprecation")
        @Override
        public BlockStateModel bake(ModelBaker baker) {
            BlockStateModel layers = new SingleVariant.Unbaked(shell).bake(baker);
            BakedQuad.MaterialInfo core = baker.interner().materialInfo(BakedQuad.MaterialInfo.of(
                    layers.particleMaterial(), Transparency.NONE, SlimeCoreQuads.NO_TINT, true, 0, false));
            return new SlimeCoreModel(layers, core);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            shell.resolveDependencies(resolver);
        }
    }
}
