package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.GooShares;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Quadrant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
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
 * The model every volume cell's goo draws through, for slime, slick and
 * every later world goo alike: the goo on its floor as a top sloped to the
 * average with the blocks around it and a rim down each side no goo of its
 * type continues past, both built from the level as the block meshes, and
 * inside it the goo slime core the cell holds, so neither adds anything to
 * the block state. The core samples the shell's own fluid sprite, the goo
 * type's, and stands at every fill inside a margin of one size: 2 px at the
 * sides, 0.4 px under and over. The model also draws each slab of goo a
 * block clings to a wall or a ceiling with, read from the block state, and
 * its fill's drips over the edge of the block under it on each side no cell
 * of its goo continues.
 * connected-goo-draws-as-one-layered-mass-with-a-core
 * residual-always-draws-a-core-inside-a-one-eighth-margin
 * slime-core-keeps-a-two-layer-gap-and-grows-with-fill
 * goo-cell-holds-a-fill-and-a-state-per-side
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 *
 * @param shell the shell's model, naming the goo's material and the particle it breaks into
 * @param core  the core's material: the shell's fluid sprite at 50% opacity, on the translucent layer
 * @param film  the material the top, its rims and the slabs draw in: the shell's fluid sprite, under the shell
 *              tint, translucent
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
        LayersAround around = layersAround(level, pos, state);
        List<BakedQuad> drawn = new ArrayList<>();
        Map<Direction, List<BakedQuad>> rims = new EnumMap<>(Direction.class);
        SlimeTop floor = SlimeTop.of(around);
        if (around.floorShareAt(0, 0, 0) > 0) {
            // goo-block-top-slopes-to-the-average-with-the-adjoining-block
            addSlab(floor, drawn, rims);
        }
        if (VolumeCellBlock.attachedSidesOf(state) == 0) {
            collectDrips(new Placement(level, pos, state, random), floor, parts);
            List<BakedQuad> inside = SlimeCoreQuads.quadsOf(around, core);
            if (!inside.isEmpty()) {
                parts.add(new CorePart(inside, shell.particleMaterial(level, pos, state)));
            }
        } else {
            // attached-goo-draws-per-attachment-and-divides-its-volume: each surface draws its share as a slab
            sharesOf(level, pos, state).sides().keySet()
                    .forEach(surface -> addSlab(SlimeTop.onSurface(around, surface), drawn, rims));
        }
        drawn.addAll(SlimeFilmQuads.streaksOf(sidesHolding(state, SideCoat.RUNNY), film));
        if (!drawn.isEmpty() || !rims.isEmpty()) {
            parts.add(new ShellPart(drawn, rims, shell.particleMaterial(level, pos, state)));
        }
    }

    /**
     * Adds one sloped slab's patches and rims to what the shell draws.
     *
     * @param slab  the slab's heights
     * @param drawn the quads the shell draws whatever its neighbours
     * @param rims  the rims the shell draws under each side
     */
    private void addSlab(SlimeTop slab, List<BakedQuad> drawn, Map<Direction, List<BakedQuad>> rims) {
        drawn.addAll(SlimeTopQuads.topOf(slab, film));
        drawn.addAll(SlimeTopQuads.joinsOf(slab, film));
        SlimeTopQuads.rimsOf(slab, film).forEach((side, quads) ->
                rims.computeIfAbsent(side, any -> new ArrayList<>()).addAll(quads));
    }

    /**
     * How an attached block's volume divides between its floor and the surfaces it clings to.
     *
     * @param level the level the section meshes from
     * @param pos   the block
     * @param state the block's state
     * @return each surface's share
     */
    private static GooShares sharesOf(BlockGetter level, BlockPos pos, BlockState state) {
        VolumeCellBlock goo = state.getBlock() instanceof VolumeCellBlock cell ? cell : null;
        boolean onAFloor = goo != null && goo.onAFloor(level, pos);
        int cohesion = goo == null ? 0 : goo.cohesionIn(clientLevel());
        return VolumeCellBlock.sharesOf(state, onAFloor, cohesion);
    }

    /**
     * The client's level, whose registries hold the goo types, where a client runs.
     *
     * @return the level, null with no client or no level loaded
     */
    private static @Nullable BlockGetter clientLevel() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.level;
    }

    /**
     * Draws a block's drips over each edge no block of its goo continues
     * past, each hanging from the sloped top's edge on its side: the drips'
     * model stands at the block's flat layer height, so it drops by what the
     * slope leaves that edge short of it.
     * goo-block-top-slopes-to-the-average-with-the-adjoining-block
     *
     * @param at    the block being drawn
     * @param floor the block's floor slab's heights
     * @param parts the parts drawn
     */
    private void collectDrips(Placement at, SlimeTop floor, List<BlockStateModelPart> parts) {
        float layers = VolumeCellBlock.layersOf(at.state());
        drips.forEach((side, drip) -> {
            if (!at.level().getBlockState(at.pos().relative(side)).is(at.state().getBlock())) {
                List<BlockStateModelPart> hung = new ArrayList<>();
                drip.collectParts(at.level(), at.pos(), at.state(), at.random(), hung);
                float drop = (float) (edgeOf(floor, side) - layers) / VolumeCellBlock.MAX_LAYERS;
                hung.forEach(part -> parts.add(new LoweredPart(part, drop)));
            }
        });
    }

    /**
     * @param floor a floor slab's heights
     * @param side  a side of the block
     * @return the slab's height along the middle of that side's edge, in layers
     */
    private static double edgeOf(SlimeTop floor, Direction side) {
        return switch (side) {
            case NORTH -> floor.heightAt(1, 0);
            case SOUTH -> floor.heightAt(1, SlimeTop.GRID.length - 1);
            case WEST -> floor.heightAt(0, 1);
            default -> floor.heightAt(SlimeTop.GRID.length - 1, 1);
        };
    }

    /**
     * The block a model draws, with what it is read through.
     *
     * @param level  the level the section meshes from
     * @param pos    the block
     * @param state  the block's state
     * @param random the random source
     */
    private record Placement(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
    }

    /**
     * A part drawn moved up or down.
     *
     * @param part the part
     * @param rise how far it moves up, in blocks, down where below 0
     */
    private record LoweredPart(BlockStateModelPart part, float rise) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return part.getQuads(direction).stream().map(quad -> SlimeTopQuads.risen(quad, rise)).toList();
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return part.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return part.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return part.materialFlags();
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
        return new LevelCells(level, pos, state);
    }

    /**
     * The cells around a block read from the level as its section meshes.
     *
     * @param level the level the section meshes from
     * @param pos   the block
     * @param state the block's state
     */
    private record LevelCells(BlockGetter level, BlockPos pos, BlockState state) implements LayersAround {

        @Override
        public int layersAt(int dx, int dy, int dz) {
            BlockState beside = stateAt(dx, dy, dz);
            return beside.is(state.getBlock()) ? VolumeCellBlock.layersOf(beside) : 0;
        }

        @Override
        public boolean solidAt(int dx, int dy, int dz) {
            BlockState beside = stateAt(dx, dy, dz);
            return !(beside.getBlock() instanceof VolumeCellBlock)
                    && beside.isCollisionShapeFullBlock(level, pos.offset(dx, dy, dz));
        }

        @Override
        public double floorShareAt(int dx, int dy, int dz) {
            BlockState beside = stateAt(dx, dy, dz);
            if (!beside.is(state.getBlock())) {
                return 0;
            }
            return VolumeCellBlock.attachedSidesOf(beside) == 0 ? VolumeCellBlock.layersOf(beside)
                    : sharesOf(level, pos.offset(dx, dy, dz), beside).floor();
        }

        @Override
        public double shareOn(int dx, int dy, int dz, Direction surface) {
            if (surface == Direction.DOWN) {
                return floorShareAt(dx, dy, dz);
            }
            BlockState beside = stateAt(dx, dy, dz);
            if (!beside.is(state.getBlock()) || VolumeCellBlock.attachedSidesOf(beside) == 0) {
                return 0;
            }
            return sharesOf(level, pos.offset(dx, dy, dz), beside).sides().getOrDefault(surface, 0.0);
        }

        private BlockState stateAt(int dx, int dy, int dz) {
            return dx == 0 && dy == 0 && dz == 0 ? state : level.getBlockState(pos.offset(dx, dy, dz));
        }
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
        return shell.materialFlags() | core.flags() | film.flags();
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
     * The shell built from the level: the sloped top, the films and the
     * streaks drawn whatever the neighbours, and each rim under its side, so
     * a neighbour hiding that side culls it as a block model's side face is.
     * goo-block-top-slopes-to-the-average-with-the-adjoining-block
     *
     * @param quads    the top, the films and the streaks
     * @param rims     each rim side's quads
     * @param particle the shell's particle material
     */
    private record ShellPart(List<BakedQuad> quads, Map<Direction, List<BakedQuad>> rims, Material.Baked particle)
            implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? quads : rims.getOrDefault(direction, List.of());
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
            int flags = 0;
            for (BakedQuad quad : quads) {
                flags |= quad.materialInfo().flags();
            }
            for (List<BakedQuad> side : rims.values()) {
                for (BakedQuad quad : side) {
                    flags |= quad.materialInfo().flags();
                }
            }
            return flags;
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
