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
import net.minecraft.world.level.BlockGetter;
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
 * state. The core samples the shell's own fluid sprite, the goo type's, and
 * stands at every fill inside a margin of one size: 2 px at the sides, 0.4 px under and over.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * residual-always-draws-a-core-inside-a-one-eighth-margin
 * slime-core-keeps-a-two-layer-gap-and-grows-with-fill
 *
 * @param shell the layered shell the cell's layers draw as
 * @param core  the core's material: the shell's fluid sprite at 50% opacity, on the translucent layer
 */
public record SlimeCoreModel(BlockStateModel shell, BakedQuad.MaterialInfo core) implements DynamicBlockStateModel {

    /** The type a block state file names to draw a cell's layers through this model. */
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(Goo.MODID, "slime_core");

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        shell.collectParts(level, pos, state, random, parts);
        List<BakedQuad> quads = SlimeCoreQuads.quadsOf(layersAround(level, pos, state), core);
        if (!quads.isEmpty()) {
            parts.add(new CorePart(quads, shell.particleMaterial(level, pos, state)));
        }
    }

    /**
     * The layers the cells of a cell's goo hold around it, read from the
     * level as its section meshes; a cell of another goo, any other block and
     * open air hold none. The client re-meshes a cell when any block within
     * one of it changes, diagonals included (LevelRenderer.setBlockDirty), so
     * reads within that reach stay current.
     * slime-cores-connect-where-only-padding-parts-them
     *
     * @param level the level the section meshes from
     * @param pos   the cell
     * @param state the cell's state
     * @return the neighbourhood the core rule reads
     */
    static LayersAround layersAround(BlockGetter level, BlockPos pos, BlockState state) {
        return (dx, dy, dz) -> {
            BlockState beside = dx == 0 && dy == 0 && dz == 0 ? state : level.getBlockState(pos.offset(dx, dy, dz));
            return beside.is(state.getBlock()) ? VolumeCellBlock.layersOf(beside) : 0;
        };
    }

    /**
     * The material a cell's core draws with: the shell's own fluid sprite on
     * the translucent layer, under the core's tint, 50% opaque in the
     * sprite's own colors.
     * slime-core-draws-translucent-at-the-shells-opacity
     *
     * @param fluid the shell's particle material, the goo type's fluid sprite
     * @return the core's material
     */
    static BakedQuad.MaterialInfo coreMaterial(Material.Baked fluid) {
        return BakedQuad.MaterialInfo.of(fluid, Transparency.TRANSLUCENT, VolumeCellTint.CORE_TINT_INDEX, true, 0,
                false);
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
            BakedQuad.MaterialInfo core = baker.interner().materialInfo(coreMaterial(layers.particleMaterial()));
            return new SlimeCoreModel(layers, core);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            shell.resolveDependencies(resolver);
        }
    }
}
