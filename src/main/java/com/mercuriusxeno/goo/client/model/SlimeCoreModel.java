package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Quadrant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The model every volume cell's layers draw through, for slime, slick and
 * every later world goo alike: the layered shell its variant names, and
 * inside it the goo slime core the cell holds, drawn from what the model
 * reads as the cell's section meshes, so the core adds nothing to the block
 * state. The core samples the shell's own fluid sprite, the goo type's, and
 * stands at every fill inside a margin of one size: 2 px at the sides, 0.4 px under and over.
 * The model also draws the cell's films, one slab on each wall a side holds
 * a film against, read from the block state, and its fill's drips over the
 * edge of the block under it on each side no cell of its goo continues,
 * read from the level as the core is, so the drips add nothing to the
 * block state either.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * residual-always-draws-a-core-inside-a-one-eighth-margin
 * slime-core-keeps-a-two-layer-gap-and-grows-with-fill
 * goo-cell-holds-a-fill-and-a-state-per-side
 *
 * @param shell the layered shell the cell's layers draw as
 * @param core  the core's material: the shell's fluid sprite at 50% opacity, on the translucent layer
 * @param film  the films' material: the shell's fluid sprite, under the shell tint, translucent
 * @param drips the fill's drips over each side's edge, keyed by the side, empty for a cell drawing none
 */
public record SlimeCoreModel(BlockStateModel shell, BakedQuad.MaterialInfo core, BakedQuad.MaterialInfo film,
                             Map<Direction, BlockStateModel> drips) implements DynamicBlockStateModel {

    /** The type a block state file names to draw a cell's layers through this model. */
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(Goo.MODID, "slime_core");
    /** The tint index the shell, its drips and its films draw under. */
    static final int SHELL_TINT = 0;
    /** Each side's drips' turn about the vertical, the north drips' model turned to face it. */
    private static final Map<Direction, Quadrant> DRIP_TURNS = Map.of(Direction.NORTH, Quadrant.R0,
            Direction.EAST, Quadrant.R90, Direction.SOUTH, Quadrant.R180, Direction.WEST, Quadrant.R270);

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        List<BakedQuad> coats = new ArrayList<>();
        if (sidesHolding(state, SideCoat.ATTACHED).isEmpty()) {
            collectFill(level, pos, state, random, parts);
        } else {
            // attached-goo-draws-per-attachment-and-divides-its-volume: the volume divides between its surfaces
            boolean onAFloor = state.getBlock() instanceof VolumeCellBlock goo && goo.onAFloor(level, pos);
            coats.addAll(SlimeFilmQuads.quadsOf(VolumeCellBlock.sharesOf(state, onAFloor), film));
        }
        coats.addAll(SlimeFilmQuads.streaksOf(sidesHolding(state, SideCoat.RUNNY), film));
        if (!coats.isEmpty()) {
            parts.add(new CorePart(coats, shell.particleMaterial(level, pos, state)));
        }
    }

    /**
     * Draws a block attached to no side as its fill: the layered shell, the
     * drips over each edge no block of its goo continues, and the core inside.
     *
     * @param level  the level the section meshes from
     * @param pos    the block
     * @param state  the block's state
     * @param random the random source
     * @param parts  the parts drawn
     */
    private void collectFill(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        List<BlockStateModelPart> layers = new ArrayList<>();
        shell.collectParts(level, pos, state, random, layers);
        layers.forEach(part -> parts.add(new BandedShellPart(part)));
        drips.forEach((side, drip) -> {
            if (!level.getBlockState(pos.relative(side)).is(state.getBlock())) {
                drip.collectParts(level, pos, state, random, parts);
            }
        });
        List<BakedQuad> quads = SlimeCoreQuads.quadsOf(layersAround(level, pos, state), core);
        if (!quads.isEmpty()) {
            parts.add(new CorePart(quads, shell.particleMaterial(level, pos, state)));
        }
    }

    private static List<Direction> sidesHolding(BlockState state, SideCoat coat) {
        return Direction.Plane.HORIZONTAL.stream().filter(side -> VolumeCellBlock.coatOn(state, side) == coat).toList();
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
     * The shell's layers with their top cut into the core's bands.
     * slime-core-draws-translucent-at-the-shells-opacity
     *
     * @param layers the shell's own part
     */
    private record BandedShellPart(BlockStateModelPart layers) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            List<BakedQuad> quads = new ArrayList<>();
            for (BakedQuad quad : layers.getQuads(direction)) {
                if (quad.direction() == Direction.UP) {
                    quads.addAll(SlimeCoreQuads.bandedTop(quad));
                } else {
                    quads.add(quad);
                }
            }
            return quads;
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return layers.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return layers.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return layers.materialFlags();
        }
    }

    /**
     * The model as a block state file names it: the shell variant whose
     * layers the core sits inside, and the north drips' variant the fill's
     * drips turn from, left out for a cell of fill 0.
     *
     * @param shell the shell's variant
     * @param drips the drips' variant over the north edge
     */
    public record Unbaked(Variant shell, Optional<Variant> drips) implements CustomUnbakedBlockStateModel {

        /** Reads {@code {"type": "goo:slime_core", "shell": {"model": ...}, "drips": {"model": ...}}}. */
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Variant.CODEC.fieldOf("shell").forGetter(Unbaked::shell),
                Variant.CODEC.optionalFieldOf("drips").forGetter(Unbaked::drips)
        ).apply(instance, Unbaked::new));

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
            BakedQuad.MaterialInfo film = baker.interner().materialInfo(BakedQuad.MaterialInfo.of(
                    layers.particleMaterial(), Transparency.TRANSLUCENT, SHELL_TINT, true, 0, false));
            Map<Direction, BlockStateModel> turned = new EnumMap<>(Direction.class);
            drips.ifPresent(north -> DRIP_TURNS.forEach((side, turn) ->
                    turned.put(side, new SingleVariant.Unbaked(north.withYRot(turn)).bake(baker))));
            return new SlimeCoreModel(layers, core, film, turned);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            shell.resolveDependencies(resolver);
            drips.ifPresent(north -> north.resolveDependencies(resolver));
        }
    }
}
