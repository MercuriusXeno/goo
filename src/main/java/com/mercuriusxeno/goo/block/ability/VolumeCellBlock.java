package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.ability.LoadedAbilityCost;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.ability.program.MoltenStep;
import com.mercuriusxeno.goo.ability.program.ShatterStep;
import com.mercuriusxeno.goo.ability.program.SlickStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.WispShed;
import com.mercuriusxeno.goo.ability.program.WispsStep;
import com.mercuriusxeno.goo.block.ability.SlimeFlow.Side;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.FallingDripPayload;
import com.mercuriusxeno.goo.network.RedHotPayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.DripFall;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The volume cell, the one block every goo type's world goo stands on,
 * bound to a goo type by a CellBinding: a plain block holding one to fifteen
 * layers in its block state, with no block entity, drawn as height like snow
 * layers, laid on a block's top with the block under it unchanged and
 * showing through, or, for a goo its binding pools on lava, on a lava
 * source's surface. Each landed drip adds a layer, and a cell taller than a
 * side neighbour of its own goo pours a layer into it, and off a ledge
 * downward, until level, waiting between pours as its goo type's viscosity
 * sets. A layer is priced at a fifteenth of the laying ability's cost; the
 * cell stands until something destroys it, then drops its goo type worth
 * that price per layer. What it does to things on it is its surface's: slime
 * breaks a fall and bounces, slick ramps a thing trying to move at any height,
 * leech slows a thing it catches.
 * slime-and-slick-are-one-volume-cell-tech
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 * slime-is-a-layered-cell-that-levels-out
 * slick-is-a-layered-cell-that-flows-thinner-and-faster-than-slime
 * breaking-slime-drops-weird-goo-worth-its-layers
 * breaking-slick-drops-kinetic-goo-worth-its-film
 * bounce-ramps-jump-height-while-jump-is-held
 * slick-coats-the-ground-with-kinetic-speed
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 */
public class VolumeCellBlock extends Block {

    /** What the cell holds against its north wall. */
    public static final EnumProperty<SideCoat> NORTH = EnumProperty.create("north", SideCoat.class);
    /** What the cell holds against its east wall. */
    public static final EnumProperty<SideCoat> EAST = EnumProperty.create("east", SideCoat.class);
    /** What the cell holds against its south wall. */
    public static final EnumProperty<SideCoat> SOUTH = EnumProperty.create("south", SideCoat.class);
    /** What the cell holds against its west wall. */
    public static final EnumProperty<SideCoat> WEST = EnumProperty.create("west", SideCoat.class);
    /**
     * Whether the block's goo is attached to the underside of the block above
     * it, its ceiling, keeping no layer of its own.
     * goo-cell-holds-a-fill-and-a-state-per-side
     */
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");
    /** The most layers one cell holds: a whole block of goo. */
    public static final int MAX_LAYERS = 15;
    /** The adhesion of a goo that holds tops, every goo's rule before adhesion was read. */
    private static final int HOLDS_TOPS = 1;
    /** Half a block, the middle of a cell across. */
    private static final double HALF = 0.5;
    /** How far a wall run's drip stands off its wall, as the runny streak stands: a tenth of a model pixel. */
    static final double WALL_DRIP_OFF = 0.1 / 16;
    /**
     * The block's volume, all the goo it holds, each layer a fifteenth of a
     * block of goo; no side keeps layers of its own.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * world-goo-fills-a-block-space-in-one-to-fifteen-layers
     */
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, MAX_LAYERS);

    private static final Map<Direction, EnumProperty<SideCoat>> COATS = Map.of(Direction.NORTH, NORTH,
            Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST);
    /** The surfaces a block's goo attaches to: its four sides and its ceiling. */
    private static final List<Direction> ATTACHABLE = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST, Direction.UP);
    /** Each fill's box, indexed by the fill, none at 0. */
    private static final VoxelShape[] LAYER_BOXES = new VoxelShape[MAX_LAYERS + 1];

    static {
        LAYER_BOXES[0] = Shapes.empty();
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            LAYER_BOXES[layers] = layerBox(layers);
        }
    }

    /** The goo type, laying ability and surface this cell is bound to. */
    private final CellBinding binding;

    /**
     * Creates a volume cell bound to a goo type's world goo.
     *
     * @param properties the block properties
     * @param binding    the goo type it holds, the ability laying it and what it does to things on it
     */
    public VolumeCellBlock(Properties properties, CellBinding binding) {
        super(properties);
        this.binding = binding;
        registerDefaultState(stateDefinition.any().setValue(NORTH, SideCoat.BARE).setValue(EAST, SideCoat.BARE)
                .setValue(SOUTH, SideCoat.BARE).setValue(WEST, SideCoat.BARE).setValue(CEILING, false)
                .setValue(LAYERS, 1));
    }

    /**
     * The state property holding what a cell holds against one side's wall.
     *
     * @param side a horizontal side
     * @return the property
     */
    public static EnumProperty<SideCoat> coatProperty(Direction side) {
        return COATS.get(side);
    }

    /**
     * What a cell holds against one side's wall.
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param state the cell's state
     * @param side  a horizontal side
     * @return the side's coat, bare for anything but a volume cell
     */
    public static SideCoat coatOn(BlockState state, Direction side) {
        if (!(state.getBlock() instanceof VolumeCellBlock)) {
            return SideCoat.BARE;
        }
        if (side == Direction.UP) {
            return Boolean.TRUE.equals(state.getValue(CEILING)) ? SideCoat.ATTACHED : SideCoat.BARE;
        }
        return state.getValue(coatProperty(side));
    }

    /**
     * A block's state with one surface coated: a side by its coat, the ceiling attached or bare.
     *
     * @param state the block's state
     * @param side  a horizontal side, or UP for the ceiling
     * @param coat  what the surface holds
     * @return the coated state
     */
    static BlockState withCoat(BlockState state, Direction side, SideCoat coat) {
        return side == Direction.UP ? state.setValue(CEILING, coat == SideCoat.ATTACHED)
                : state.setValue(coatProperty(side), coat);
    }

    /**
     * The volume a block holds once a drip sticks to one of its sides: the
     * drip's layer joins the volume of the block beside the struck face, a
     * block of volume 1 standing where that space was empty.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param volumeBeside the volume the block beside the face holds, 0 for an empty space
     * @return the volume it holds once the drip sticks
     */
    public static int volumeOnceStuck(int volumeBeside) {
        return volumeBeside + 1;
    }

    /**
     * The most a block with no floor holds: one layer plus one per point of
     * its goo's cohesion for each side it is attached to, a whole block at
     * the most; goo past that slips.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param cohesion      the goo's cohesion
     * @param attachedSides the sides the block is attached to
     * @return the layers it holds at the most
     */
    public static int holdOf(int cohesion, int attachedSides) {
        return Math.min(MAX_LAYERS, (1 + cohesion) * attachedSides);
    }

    /**
     * What a block becomes when a drip sticks to one of its sides: the
     * drip's layer joins its volume and the side is attached, an empty space
     * standing a block of volume 1 there; a full block, or anything but this
     * goo, takes nothing.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param standing the block's state
     * @param cell     the goo's volume cell block
     * @param side     the side the drip sticks to
     * @return the stuck state, empty where the block takes no layer
     */
    public static Optional<BlockState> stuck(BlockState standing, Block cell, Direction side) {
        if (standing.isAir()) {
            return Optional.of(withCoat(cell.defaultBlockState().setValue(LAYERS, volumeOnceStuck(0)), side,
                    SideCoat.ATTACHED));
        }
        return standing.is(cell) && standing.getValue(LAYERS) < MAX_LAYERS
                ? Optional.of(withCoat(standing.setValue(LAYERS, volumeOnceStuck(standing.getValue(LAYERS))), side,
                SideCoat.ATTACHED)) : Optional.empty();
    }

    /**
     * Whether a block stands: a block on a floor stands, and a block with no
     * floor stands on the walls it is attached to, every one with a face to
     * hold it, and while a runny layer passes through it on its way down.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param onAFloor          whether the block stands on a floor
     * @param attachedClinging  for each side it is attached to, whether its wall has a face to hold it
     * @param holdsARunnyLayer  whether a side holds a runny layer
     * @return true where the block stands
     */
    static boolean stands(boolean onAFloor, List<Boolean> attachedClinging, boolean holdsARunnyLayer) {
        if (onAFloor) {
            return true;
        }
        boolean holdsGoo = holdsARunnyLayer || !attachedClinging.isEmpty();
        return holdsGoo && attachedClinging.stream().allMatch(Boolean::booleanValue);
    }

    /**
     * Whether a block stands on a floor: the block under it has a sturdy
     * top, a lava source for a goo pooling on lava, or is a full block of its goo.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param level the level
     * @param pos   the block
     * @return true where the block stands on a floor
     */
    public boolean onAFloor(BlockGetter level, BlockPos pos) {
        BlockState under = level.getBlockState(pos.below());
        return liesOn(level, pos) || under.is(this) && under.getValue(LAYERS) == MAX_LAYERS;
    }

    /**
     * How a block's volume divides between the surfaces it is attached to.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param state    the block's state
     * @param onAFloor whether it stands on a floor
     * @param cohesion its goo's cohesion
     * @return each surface's share
     */
    public static GooShares sharesOf(BlockState state, boolean onAFloor, int cohesion) {
        return GooShares.of(state.getValue(LAYERS), onAFloor, Set.copyOf(filmedSides(state)), cohesion);
    }

    /**
     * The sides a block is attached to.
     *
     * @param state the block's state
     * @return how many
     */
    public static int attachedSidesOf(BlockState state) {
        return filmedSides(state).size();
    }

    /**
     * This block's goo's cohesion, read through a level holding the goo type
     * registry, 0 where none is to hand.
     * cohesion-is-the-step-the-goo-holds
     *
     * @param level a level holding the registries, or null
     * @return the cohesion
     */
    public int cohesionIn(@Nullable BlockGetter level) {
        return level instanceof LevelReader reader ? cohesionAt(reader) : 0;
    }

    /**
     * The sides a cell holds a film on.
     *
     * @param state the cell's state
     * @return each side holding a film
     */
    static List<Direction> filmedSides(BlockState state) {
        return ATTACHABLE.stream().filter(side -> coatOn(state, side) == SideCoat.ATTACHED).toList();
    }

    /**
     * The sides a cell holds a runny layer on.
     *
     * @param state the cell's state
     * @return each side holding a runny layer
     */
    static List<Direction> runnySides(BlockState state) {
        return SIDES.stream().filter(side -> coatOn(state, side) == SideCoat.RUNNY).toList();
    }

    /**
     * Whether a side's wall can hold a film: the block beside has a sturdy
     * face toward the cell and is no goo, since goo clings to goo no more
     * than to air and flows on to it.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param level the level
     * @param pos   the cell
     * @param side  the side the wall stands on
     * @return true where a film clings there
     */
    public static boolean clingsTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos wall = pos.relative(side);
        BlockState beside = level.getBlockState(wall);
        return !(beside.getBlock() instanceof VolumeCellBlock) && beside.isFaceSturdy(level, wall, side.getOpposite());
    }

    /**
     * Every layer a block holds: its volume, whichever sides are attached or
     * runny, since no side keeps layers of its own.
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param state the block's state
     * @return its volume
     */
    public static int layersHeld(BlockState state) {
        return state.getValue(LAYERS);
    }

    /**
     * @return the goo type, laying ability and surface this cell is bound to
     */
    public CellBinding binding() {
        return binding;
    }

    /**
     * @return what this cell does to things on it
     */
    public CellSurface surface() {
        return binding.surface();
    }

    /**
     * Whether a block is a volume cell whose surface acts on what stands on
     * it the way a surface asks.
     *
     * @param block   the block
     * @param surface the surface asked for
     * @return true for a volume cell bound to that surface
     */
    public static boolean hasSurface(Block block, CellSurface surface) {
        return block instanceof VolumeCellBlock cell && cell.surface() == surface;
    }

    /**
     * The box a cell fills: a full block across, its layers' fifteenths of a
     * block high.
     *
     * @param layers the layers the cell holds
     * @return the box
     */
    static VoxelShape layerBox(int layers) {
        return Shapes.box(0, 0, 0, 1, (double) layers / MAX_LAYERS, 1);
    }

    /**
     * One layer's price: a fifteenth of the cost of a whole block of goo,
     * whatever that cost is tuned to.
     * world-goo-fills-a-block-space-in-one-to-fifteen-layers
     *
     * @param blockCost the cost of a whole block of the goo, its laying ability's cost
     * @return the exact price in mb
     */
    public static GooFraction layerPrice(GooFraction blockCost) {
        return blockCost.dividedBy(MAX_LAYERS);
    }

    /**
     * The goo a broken cell gives back: what its layers cost, the whole mb of it.
     * breaking-slime-drops-weird-goo-worth-its-layers
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param layers    the layers the cell held
     * @param blockCost the cost of a whole block of the goo
     * @return the goo in mb
     */
    static int reclaimed(int layers, GooFraction blockCost) {
        return layerPrice(blockCost).times(layers).floor();
    }

    /**
     * The goo a broken cell gives back, by type and amount.
     *
     * @param gooType the goo type given back
     * @param mb      the goo in mb
     */
    public record Reclaim(ResourceKey<GooTypeDefinition> gooType, int mb) {
    }

    /**
     * What a broken cell bound to a world goo gives back: its goo type, worth
     * what its layers cost, nothing where that comes to no whole mb.
     * breaking-slime-drops-weird-goo-worth-its-layers
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param binding   the world goo the cell is bound to
     * @param layers    the layers the cell held
     * @param blockCost the cost of a whole block of the goo
     * @return the goo given back, empty where it comes to none
     */
    static Optional<Reclaim> reclaimedBy(CellBinding binding, int layers, GooFraction blockCost) {
        int mb = reclaimed(layers, blockCost);
        return mb > 0 ? Optional.of(new Reclaim(binding.gooType(), mb)) : Optional.empty();
    }

    /**
     * Gives back the goo the cell's layers cost, as the glow crystal gives
     * back its goo, whatever breaks the cell, Vacuum included; a cell broken
     * where its laying ability's definition does not load gives back nothing.
     * breaking-slime-drops-weird-goo-worth-its-layers
     * breaking-slick-drops-kinetic-goo-worth-its-film
     */
    @Override
    protected @NonNull List<ItemStack> getDrops(@NonNull BlockState state, LootParams.@NonNull Builder builder) {
        return LoadedAbilityCost.of(builder.getLevel(), binding.layingAbility())
                .flatMap(cost -> reclaimedBy(binding, layersHeld(state), cost))
                .map(reclaim -> List.of(GooStacks.createForOutput(reclaim.gooType(), reclaim.mb())))
                .orElse(List.of());
    }

    /**
     * The layers a cell holds.
     *
     * @param state the cell's state
     * @return its layers, 0 for anything but a volume cell
     */
    public static int layersOf(BlockState state) {
        return state.getBlock() instanceof VolumeCellBlock ? state.getValue(LAYERS) : 0;
    }

    /**
     * What a cell becomes when one more layer of a goo lands in its fill:
     * air takes a cell of one layer, a cell of that goo one layer more up to
     * a whole block of fifteen, its films kept, anything else nothing.
     * drip-creates-a-volume-cell-immediately
     * drip-on-a-standing-cell-increases-it
     *
     * @param standing the cell's state
     * @param cell     the goo's volume cell block
     * @return the thickened state, empty where the cell can take no layer
     */
    public static Optional<BlockState> thickened(BlockState standing, Block cell) {
        if (standing.isAir()) {
            return Optional.of(cell.defaultBlockState());
        }
        if (!standing.is(cell) || standing.getValue(LAYERS) >= MAX_LAYERS) {
            return Optional.empty();
        }
        return Optional.of(standing.setValue(LAYERS, standing.getValue(LAYERS) + 1));
    }

    /**
     * What a cell becomes when a layer of a goo lands as a film against one
     * of its walls: air takes a cell of fill 0 holding that film, a cell of
     * that goo whose side stands bare takes the film, anything else nothing.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param standing the cell's state
     * @param cell     the goo's volume cell block
     * @param side     the side whose wall the film clings to
     * @return the filmed state, empty where the side can take no film
     */
    public static Optional<BlockState> filmed(BlockState standing, Block cell, Direction side) {
        return coated(standing, cell, side, SideCoat.ATTACHED);
    }

    /**
     * What a block becomes when a layer of a goo lands on one of its sides:
     * the layer joins the block's volume and the side takes the coat, an
     * empty space standing a block of volume 1 there; a block of that goo
     * whose side is not bare, or which is full, or anything else takes nothing.
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param standing the cell's state
     * @param cell     the goo's volume cell block
     * @param side     the side the layer lands on
     * @param coat     what the layer stands as there, a film or a runny layer
     * @return the coated state, empty where the side can take no layer
     */
    static Optional<BlockState> coated(BlockState standing, Block cell, Direction side, SideCoat coat) {
        if (standing.isAir()) {
            return Optional.of(cell.defaultBlockState().setValue(LAYERS, volumeOnceStuck(0))
                    .setValue(coatProperty(side), coat));
        }
        boolean takes = standing.is(cell) && coatOn(standing, side) == SideCoat.BARE
                && standing.getValue(LAYERS) < MAX_LAYERS;
        return takes ? Optional.of(standing.setValue(LAYERS, volumeOnceStuck(standing.getValue(LAYERS)))
                .setValue(coatProperty(side), coat)) : Optional.empty();
    }

    /**
     * Where a layer lands, and the state it leaves that cell in, or the open
     * cell it falls from to the first top below, landing when its drip arrives.
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param pos   the cell
     * @param state its state with the layer landed, empty where the layer falls from it
     */
    public record Placement(BlockPos pos, Optional<BlockState> state) {

        static Placement at(BlockPos pos, BlockState state) {
            return new Placement(pos, Optional.of(state));
        }

        static Placement fallingFrom(BlockPos pos) {
            return new Placement(pos, Optional.empty());
        }
    }

    /**
     * Where a falling layer's drawn drip starts, the surface it lands on and
     * the ticks it falls, the one launch a fall hands the visuals.
     *
     * @param from     the point the layer leaves: the middle of the cell it falls from, at the height it leaves
     * @param landingY the surface of the fill it lands on
     * @param ticks    the ticks a drip takes to fall that far
     */
    record FallStart(Vec3 from, double landingY, int ticks) {
    }

    /**
     * The launch a fall hands the visuals as it starts.
     * goo-moving-downward-falls-as-drips
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param start         the open cell the layer falls from
     * @param fromY         the height it leaves at
     * @param landing       the cell it lands in
     * @param landingLayers the fill that cell holds
     * @return the launch
     */
    static FallStart fallStart(BlockPos start, double fromY, BlockPos landing, int landingLayers) {
        double landingY = landing.getY() + (double) landingLayers / MAX_LAYERS;
        return new FallStart(new Vec3(start.getX() + HALF, fromY, start.getZ() + HALF), landingY,
                DripFall.ticksToFall(fromY - landingY));
    }

    /**
     * Starts a layer falling from an open cell to the first top below: the
     * server carries it, its chunk's watchers draw its drip, and it joins the
     * fill there when the drip arrives; a fall finding no top below starts nothing.
     * falling-layer-lands-when-its-drip-arrives
     * goo-moving-downward-falls-as-drips
     *
     * @param level  the level
     * @param start  the open cell the layer falls from
     * @param fromY  the height it leaves at
     * @param placer the player who placed the layer, whom the cell it lands in credits, or null for none
     * @return true where the fall started
     */
    public boolean beginFall(ServerLevel level, BlockPos start, double fromY, @Nullable UUID placer) {
        Optional<BlockPos> landing = landingBelow(level, start);
        if (landing.isEmpty()) {
            return false;
        }
        FallStart launch = fallStart(start, fromY, landing.get(), layersOf(level.getBlockState(landing.get())));
        GooServerState.of(level.getServer()).layerFalls().start(new LayerFalls.Fall(level.dimension(),
                start.immutable(), this, level.getServer().getTickCount() + launch.ticks(), placer));
        BlockVisuals.sendToWatchers(level, start, new FallingDripPayload(launch.from(), launch.landingY(),
                launch.ticks(), colourOf(level, GooColors::highlight), colourOf(level, GooColors::bright), false));
        return true;
    }

    /**
     * One of this cell's goo type's colours.
     *
     * @param level  the level, whose registries hold the goo type
     * @param colour which colour of the type
     * @return the colour, black where the type's definition does not load
     */
    private int colourOf(LevelReader level, ToIntFunction<GooTypeDefinition> colour) {
        return level.registryAccess().get(binding.gooType()).map(type -> colour.applyAsInt(type.value())).orElse(0);
    }

    /**
     * Lands a falling layer on the first top below the cell it fell from,
     * read again on its arrival, since the world may have changed under it,
     * and spreads the cell it joins as a landed drip does.
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param level  the level
     * @param start  the open cell the layer fell from
     * @param placer the player who placed the layer, or null for none
     * @return true where the layer joined a fill, false where it found no room
     */
    public boolean landFall(ServerLevel level, BlockPos start, @Nullable UUID placer) {
        Optional<Placement> landing = landingBelow(level, start)
                .flatMap(cell -> thickened(level.getBlockState(cell), this).map(into -> Placement.at(cell, into)));
        landing.ifPresent(placed -> {
            level.setBlock(placed.pos(), placed.state().orElseThrow(), UPDATE_ALL);
            creditPlacer(level, placed.pos(), placer);
            spreadAtOnce(level, placed.pos());
        });
        return landing.isPresent();
    }

    /**
     * Lands a falling layer at once: as fill where it finds room, and as its
     * goo given back where the cell it fell from stands where it finds none,
     * so the layer is never lost.
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param level  the level
     * @param start  the open cell the layer fell from
     * @param placer the player who placed the layer, or null for none
     */
    public void landFallNow(ServerLevel level, BlockPos start, @Nullable UUID placer) {
        if (!landFall(level, start, placer)) {
            LoadedAbilityCost.of(level, binding.layingAbility()).map(cost -> reclaimed(1, cost)).filter(mb -> mb > 0)
                    .ifPresent(mb -> popResource(level, start, GooStacks.createForOutput(binding.gooType(), mb)));
        }
    }

    /**
     * Lands a runny layer a strike placed, drawing its first drip from the
     * cell the strike fed where its run takes it on at once from there.
     * goo-moving-downward-falls-as-drips
     *
     * @param host    the caster's host
     * @param fed     the cell the strike fed
     * @param side    the side whose wall the layer runs down
     * @param landing where the layer lands
     */
    public void placeRunny(ChannelHost host, BlockPos fed, Direction side, Placement landing) {
        if (!landing.pos().equals(fed)) {
            BlockState standing = host.stateAt(fed);
            wallDripOf(stepFor(host.level(), fed, standing, side), fed, side, layersOf(host.stateAt(fed.below())))
                    .ifPresent(drip -> drawWallDrip(host.level(), fed, drip));
        }
        place(host, landing);
    }

    /**
     * Lands a layer a strike placed: into the cell it names, or falling from it.
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param host    the caster's host
     * @param landing where the layer lands
     */
    public void place(ChannelHost host, Placement landing) {
        landing.state().ifPresentOrElse(state -> {
            host.transformBlock(landing.pos(), state);
            creditPlacer(host.level(), landing.pos(), host.placingPlayer());
        }, () -> beginFall(host.level(), landing.pos(), landing.pos().getY() + 1.0, host.placingPlayer()));
    }

    /**
     * Whether a hit this cell deals credits whoever placed it, so the cell
     * remembers its placer: a cutting cell's shards and a smiting cell's
     * holy fire; a cell that hurts nothing remembers none.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @return true where the cell remembers its placer
     */
    protected boolean creditsItsPlacer() {
        return surface().cuts() || surface().smites();
    }

    /**
     * The player a hit this cell deals credits: whoever placed it, while that player is on the server.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param level the level
     * @param pos   the cell
     * @return the player, or null where no player placed it, the cell credits no placer or the player is away
     */
    protected @Nullable ServerPlayer creditedPlayerAt(ServerLevel level, BlockPos pos) {
        return GooHitCredit.placerOf(level, placerAt(level, pos));
    }

    /**
     * Records the player who placed goo in a cell that credits its placer;
     * the last player to place there is the one it credits.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param level  the level
     * @param pos    the cell
     * @param player the player, or null where no player placed it
     */
    public void creditPlacer(ServerLevel level, BlockPos pos, @Nullable UUID player) {
        if (player != null && creditsItsPlacer()) {
            CellPlacers.of(level).place(pos, player);
        }
    }

    /**
     * The player a cell that credits its placer credits.
     *
     * @param level the level
     * @param pos   the cell
     * @return the player's id, or null where no player placed it or the cell credits no placer
     */
    private @Nullable UUID placerAt(ServerLevel level, BlockPos pos) {
        return creditsItsPlacer() ? CellPlacers.of(level).placerAt(pos).orElse(null) : null;
    }

    /**
     * Hands the cell a moving layer lands in the placer of the cell it left.
     * operator ruling 2026-11-17: a poured cell inherits its source's placer
     *
     * @param level the level
     * @param from  the cell the layer leaves
     * @param to    the cell the layer lands in
     */
    private void carryPlacer(ServerLevel level, BlockPos from, BlockPos to) {
        if (creditsItsPlacer()) {
            CellPlacers.of(level).carry(from, to);
        }
    }

    /** What a runny layer does on its cell's step. */
    enum RunStep {
        /** It joins the fill of its own cell, which lies on a top. */
        JOIN_HERE,
        /** It joins the fill of the cell below. */
        JOIN_BELOW,
        /** It moves to the same side of the cell below, the wall going on beside it. */
        RUN_DOWN,
        /** The wall ends: it falls to the first top below, as a layer poured off a ledge. */
        FALL,
        /** It waits on its side, the way down full or taken. */
        WAIT
    }

    /** What the cell below a runny layer holds for it. */
    enum Below {
        /** A top under it, or fill of this goo with room for a layer. */
        FILL_ROOM,
        /** Fill of this goo, a whole block of it. */
        FILL_FULL,
        /** An open cell with the wall going on beside it and that side bare. */
        WALL_GOES_ON,
        /** An open cell with the wall going on beside it and that side taken. */
        SIDE_TAKEN,
        /** An open cell with no wall beside it. */
        WALL_ENDS,
        /** Any other block. */
        BLOCKED
    }

    /**
     * A runny layer's step down its wall: in a cell lying on a top it joins
     * that cell's fill; else it joins the fill of the cell below, moves to
     * the same side of the cell below while the wall goes on beside it, or
     * falls where the wall ends; a full fill or a taken side holds it waiting.
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param hereOnATop whether its cell lies on a top
     * @param hereFull   whether its cell holds a whole block of fill
     * @param below      what the cell below holds for it
     * @return the step
     */
    static RunStep runStep(boolean hereOnATop, boolean hereFull, Below below) {
        if (hereOnATop) {
            return hereFull ? RunStep.WAIT : RunStep.JOIN_HERE;
        }
        return stepOver(below);
    }

    /**
     * A runny layer's step from a cell lying on no top, by what the cell below holds for it.
     *
     * @param below what the cell below holds for the layer
     * @return the step
     */
    private static RunStep stepOver(Below below) {
        return switch (below) {
            case FILL_ROOM -> RunStep.JOIN_BELOW;
            case WALL_GOES_ON -> RunStep.RUN_DOWN;
            case WALL_ENDS -> RunStep.FALL;
            case FILL_FULL, SIDE_TAKEN, BLOCKED -> RunStep.WAIT;
        };
    }

    /**
     * Whether raising a cell to a height would lift its top over the feet of
     * a living thing standing in it: the cell would rise around them, leaving
     * their feet inside it, and their next move off it collides with the
     * cells beside rather than stepping up, turning and slowing them.
     * operator report 2026-11-14: hitting goo redirects the runner off the track and hitches it
     *
     * @param level  the level
     * @param pos    the cell
     * @param from   the layers it holds, 0 for an open cell
     * @param to     the layers it would hold
     * @return true where a living thing's feet stand inside the risen layers
     */
    public static boolean wouldRiseAroundFeet(LevelReader level, BlockPos pos, int from, int to) {
        if (to <= from || !(level instanceof Level world)) {
            return false;
        }
        AABB risen = new AABB(pos.getX(), pos.getY() + (double) from / MAX_LAYERS, pos.getZ(),
                pos.getX() + 1, pos.getY() + (double) to / MAX_LAYERS, pos.getZ() + 1);
        return !world.getEntitiesOfClass(LivingEntity.class, risen,
                living -> living.isAlive() && !living.isSpectator() && living.getY() < risen.maxY).isEmpty();
    }

    /**
     * Whether a neighbour continues this cell's film: a cell of the same
     * goo, so the two read as one film with no seam between and no drip at
     * the side they share, as slime blocks do toward slime blocks.
     * diagnose-and-correct-the-slick-film-seams
     *
     * @param neighbour the state of the cell beside
     * @return true where the neighbour is a cell of this goo
     */
    boolean continuesInto(BlockState neighbour) {
        return neighbour.is(this);
    }

    /**
     * Hides a face toward a cell of its goo standing at least as tall, so a
     * taller cell still draws the side it shows above a lower neighbour.
     */
    @Override
    protected boolean skipRendering(@NonNull BlockState state, @NonNull BlockState neighbour,
                                    @NonNull Direction direction) {
        boolean covered = direction.getAxis().isVertical() || layersOf(neighbour) >= layersOf(state);
        return continuesInto(neighbour) && covered || super.skipRendering(state, neighbour, direction);
    }

    /**
     * Whether a cell can hold this world goo: the block under it has a
     * sturdy top, or, for a goo pooling on lava, is the source of a lava
     * type, lava or the molten iron Molten melts iron core into.
     * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
     * molten-melts-by-a-meltable-group-and-its-parallel-melted-group
     *
     * @param level the level
     * @param pos   the cell
     * @return true where this world goo can lie
     */
    public boolean liesOn(BlockGetter level, BlockPos pos) {
        BlockPos under = pos.below();
        BlockState footing = level.getBlockState(under);
        return footing.isFaceSturdy(level, under, Direction.UP)
                || binding.fluidFooting().liesOn(footing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, CEILING, LAYERS);
    }

    /**
     * The block's box: its floor's share as a fill, and each attached side's
     * share as a slab standing out from that wall.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param shares how the block's volume divides between its surfaces
     * @return the box
     */
    static VoxelShape boxOf(GooShares shares) {
        VoxelShape box = shares.floor() > 0 ? Shapes.box(0, 0, 0, 1, shares.floor() / MAX_LAYERS, 1) : Shapes.empty();
        for (Map.Entry<Direction, Double> side : shares.sides().entrySet()) {
            box = Shapes.or(box, slabOn(side.getKey(), side.getValue() / MAX_LAYERS));
        }
        return box;
    }

    /**
     * A slab standing out from one side's wall.
     *
     * @param side  the side whose wall it stands on
     * @param depth its depth from the wall, in blocks
     * @return the slab
     */
    private static VoxelShape slabOn(Direction side, double depth) {
        return switch (side) {
            case NORTH -> Shapes.box(0, 0, 0, 1, 1, depth);
            case SOUTH -> Shapes.box(0, 0, 1 - depth, 1, 1, 1);
            case WEST -> Shapes.box(0, 0, 0, depth, 1, 1);
            case UP -> Shapes.box(0, 1 - depth, 0, 1, 1, 1);
            default -> Shapes.box(1 - depth, 0, 0, 1, 1, 1);
        };
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return boxOf(sharesOf(state, onAFloor(level, pos), cohesionIn(level)));
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return boxOf(sharesOf(state, onAFloor(level, pos), cohesionIn(level)));
    }

    /**
     * A cell holding fill stands on a top; a cell of fill 0 stands on its
     * films, each clinging to a wall with a face to hold it.
     * goo-cell-holds-a-fill-and-a-state-per-side
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        return stands(onAFloor(level, pos),
                filmedSides(state).stream().map(side -> clingsTo(level, pos, side)).toList(),
                !runnySides(state).isEmpty());
    }

    /**
     * Refuses a placed block the cell, so building on a world goo sets the
     * block over it rather than deleting the goo its layers hold; the cell
     * stays replaceable for the landing steps and the rules that read
     * BlockState.canBeReplaced().
     * breaking-slime-drops-weird-goo-worth-its-layers
     * breaking-slick-drops-kinetic-goo-worth-its-film
     */
    @Override
    protected boolean canBeReplaced(@NonNull BlockState state, @NonNull BlockPlaceContext context) {
        return false;
    }

    /**
     * Starts a cell laid straight into its block space, as a drip's is,
     * flowing; a cell laid where it cannot stand breaks, giving back its goo.
     * slime-is-a-layered-cell-that-levels-out
     */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!canSurvive(state, level, pos)) {
            fallWhole(level, pos, state);
            return;
        }
        level.scheduleTick(pos, this, pourTicksAt(level));
    }

    /**
     * Melts the block below where its laying ability melts, else sheds each
     * film whose wall is gone and steps each runny layer, then breaks a cell
     * left unable to stand, giving back its goo, else pours one layer into the
     * side the flow rule picks, which places the cell anew and so schedules
     * the next pour after its goo type's viscosity's ticks, until the cell
     * stands level; a level cell whose laying ability sheds wisps sheds one
     * toward the dark.
     * slime-is-a-layered-cell-that-levels-out
     * spread-decreases-the-cell-it-leaves
     * goo-cell-holds-a-fill-and-a-state-per-side
     * wisps-goo-sheds-a-wisp-toward-nearby-dark
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        if (meltBelow(level, pos, state)) {
            return;
        }
        shedLooseFilms(level, pos, state);
        // shedding a wall places the block anew, which lets a block left unable to stand fall whole at once
        BlockState shed = level.getBlockState(pos);
        if (!shed.is(this)) {
            return;
        }
        BlockState held = runRunnyLayers(level, pos, adhereToWalls(level, pos, shed));
        if (held.is(this) && !drainsDown(level, pos, held)) {
            settle(level, pos, held);
        }
    }

    /**
     * Lets a block that holds its goo stand or fall: a block unable to stand
     * falls whole, and one standing pours a layer, or sheds a wisp where it
     * pours none.
     *
     * @param level the level
     * @param pos   the block
     * @param state the block's state
     */
    private void settle(ServerLevel level, BlockPos pos, BlockState state) {
        if (!canSurvive(state, level, pos)) {
            fallWhole(level, pos, state);
            return;
        }
        if (!pourOnce(level, pos, state)) {
            shedWisp(level, pos, state);
        }
    }

    /**
     * Sheds a wisp from a level cell whose laying ability names a wisps
     * step, and schedules the next shed after the step's cadence. The next
     * shed is scheduled first: a layer the shed spends places the cell anew,
     * whose pour tick then waits on the shed's.
     * wisps-goo-sheds-a-wisp-toward-nearby-dark
     *
     * @param level the level
     * @param pos   the cell
     * @param state the cell's state
     */
    private void shedWisp(ServerLevel level, BlockPos pos, BlockState state) {
        WispsStep.in(layingProgram(level)).ifPresent(wisps -> {
            level.scheduleTick(pos, this, wisps.shedEvery());
            WispShed.shed(level, pos, state, wisps);
        });
    }

    /**
     * Melts the block under a cell whose laying ability names a meltable tag,
     * where the melt would spend layers of it: the block heats red hot over
     * the molten step's heat, the cell resting still meanwhile, then becomes
     * a lava source; a cell the melt spends nothing of stays pooled, and one
     * whose block stopped melting part way cools it as it was.
     * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
     * stone-goes-red-hot-before-molten-turns-it-to-lava
     *
     * @param level the level
     * @param pos   the cell
     * @param state the cell's state
     * @return true where the block under the cell is heating or melted
     */
    private boolean meltBelow(ServerLevel level, BlockPos pos, BlockState state) {
        Optional<MoltenStep> molten = MoltenStep.in(layingProgram(level));
        Optional<BlockState> melted = molten.flatMap(step -> step.meltedFormOf(level.getBlockState(pos.below())));
        int spent = MoltenMelt.layersSpent(state.getValue(LAYERS), melted.isPresent());
        if (spent == 0) {
            coolBelow(level, pos);
            return false;
        }
        MoltenHeats heats = GooServerState.of(level.getServer()).moltenHeats();
        int heatTicks = molten.orElseThrow().heatTicks();
        OptionalLong started = heats.startedAt(level, pos);
        if (started.isEmpty()) {
            heats.start(level, pos, level.getGameTime());
            BlockVisuals.sendToWatchers(level, pos.below(), new RedHotPayload(pos.below(), heatTicks));
        }
        long left = started.orElse(level.getGameTime()) + heatTicks - level.getGameTime();
        if (left > 0) {
            level.scheduleTick(pos, this, (int) left);
        } else {
            heats.end(level, pos);
            meltInto(level, pos, state, spent, melted.orElseThrow());
        }
        return true;
    }

    /**
     * Turns the block under a cell into the source block it melts into, lava
     * for stone and molten iron for iron core, the cell resting on it lower by
     * the layers the melt spends and gone where that empties it.
     * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block
     * molten-melts-by-a-meltable-group-and-its-parallel-melted-group
     *
     * @param level  the level
     * @param pos    the cell
     * @param state  the cell's state
     * @param spent  the layers the melt spends
     * @param melted the source block the block below melts into
     */
    private static void meltInto(ServerLevel level, BlockPos pos, BlockState state, int spent, BlockState melted) {
        level.setBlock(pos.below(), melted, UPDATE_ALL);
        int left = state.getValue(LAYERS) - spent;
        level.setBlock(pos, left > 0 ? state.setValue(LAYERS, left) : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
    }

    /**
     * Ends a cell's heat with no layer spent, the block below cooling as it
     * was, where the cell was heating it.
     * stone-goes-red-hot-before-molten-turns-it-to-lava
     *
     * @param level the level
     * @param pos   the cell
     */
    private static void coolBelow(ServerLevel level, BlockPos pos) {
        if (GooServerState.of(level.getServer()).moltenHeats().end(level, pos)) {
            BlockVisuals.sendToWatchers(level, pos.below(), new RedHotPayload(pos.below(), 0));
        }
    }

    /**
     * A cell broken or carried off while it heats the block below cools it
     * as it was, no layer spent.
     * stone-goes-red-hot-before-molten-turns-it-to-lava
     */
    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean movedByPiston) {
        coolBelow(level, pos);
        if (creditsItsPlacer()) {
            CellPlacers.of(level).forget(pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /**
     * Steps each runny layer a cell holds one cell down its wall, or into the
     * fill it ends in; a cell left with fill 0 and every side bare stands open.
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param level the level
     * @param pos   the cell
     * @param state the cell's state
     * @return the cell's state once its runny layers have stepped
     */
    private BlockState runRunnyLayers(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState left = state;
        for (Direction side : runnySides(state)) {
            if (!left.is(this)) {
                break;
            }
            RunStep stepping = stepFor(level, pos, left, side);
            Optional<Placement> step = runFrom(level, pos, left, side);
            if (step.isPresent()) {
                wallDripOf(stepping, pos, side, layersOf(level.getBlockState(pos.below())))
                        .ifPresent(drip -> drawWallDrip(level, pos, drip));
                left = stepRunnyLayer(level, pos, left, side, step.get());
            }
        }
        if (left != state) {
            level.setBlock(pos, left, UPDATE_ALL);
        }
        return left;
    }

    /**
     * Moves one runny layer to where its step lands it, clearing its side.
     *
     * @param level   the level
     * @param pos     the cell the layer leaves
     * @param state   the cell's state
     * @param side    the side the layer ran down
     * @param landing where its step lands it
     * @return the cell's state with the layer gone from its side
     */
    private BlockState stepRunnyLayer(ServerLevel level, BlockPos pos, BlockState state, Direction side,
                                      Placement landing) {
        BlockState bare = state.setValue(coatProperty(side), SideCoat.BARE);
        if (landing.pos().equals(pos)) {
            // the running layer is the block's own, so joining its own fill leaves the volume as it was
            return bare;
        }
        if (landing.state().isEmpty()) {
            // falling-layer-lands-when-its-drip-arrives: where the wall ends the layer falls, landing on arrival
            return beginFall(level, landing.pos(), landing.pos().getY() + 1.0, placerAt(level, pos))
                    ? lessALayer(bare) : state;
        }
        level.setBlock(landing.pos(), landing.state().get(), UPDATE_ALL);
        carryPlacer(level, pos, landing.pos());
        return lessALayer(bare);
    }

    /**
     * A block's state once a layer of its volume leaves it, open where that was its last.
     *
     * @param state the block's state
     * @return the state it is left in
     */
    private static BlockState lessALayer(BlockState state) {
        int layers = state.getValue(LAYERS);
        return layers > 1 ? state.setValue(LAYERS, layers - 1) : Blocks.AIR.defaultBlockState();
    }

    /**
     * Where a runny layer struck against a cell's side lands: on that side
     * where it stands bare, or, the side already holding its layer, wherever
     * the layer's first step down the wall takes it at once, so the struck
     * layer stands somewhere it can be seen and given back.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param level the level
     * @param pos   the cell beside the struck wall
     * @param side  the cell's side facing that wall
     * @return where the layer lands, empty where it can find no room
     */
    public Optional<Placement> runnyLanding(ServerLevel level, BlockPos pos, Direction side) {
        BlockState standing = level.getBlockState(pos);
        Optional<BlockState> onTheSide = coated(standing, this, side, SideCoat.RUNNY);
        return onTheSide.isPresent() ? onTheSide.map(state -> Placement.at(pos, state))
                : runFrom(level, pos, standing, side);
    }

    /**
     * Where a runny layer on a cell's side lands on its step down the wall.
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param level the level
     * @param here  the cell holding the layer
     * @param state that cell's state
     * @param side  the side the layer runs down
     * @return where it lands and what that cell becomes, empty where it waits
     */
    private Optional<Placement> runFrom(LevelReader level, BlockPos here, BlockState state, Direction side) {
        BlockPos below = here.below();
        BlockState under = level.getBlockState(below);
        return switch (stepFor(level, here, state, side)) {
            case JOIN_HERE -> thickened(state, this).map(into -> Placement.at(here, into));
            case JOIN_BELOW -> thickened(under, this).map(into -> Placement.at(below, into));
            case RUN_DOWN -> coated(under, this, side, SideCoat.RUNNY).map(into -> Placement.at(below, into));
            case FALL -> landingBelow(level, below).map(cell -> Placement.fallingFrom(below));
            case WAIT -> Optional.empty();
        };
    }

    /**
     * The step a runny layer on a cell's side takes now.
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param level the level
     * @param here  the cell holding the layer
     * @param state that cell's state
     * @param side  the side the layer runs down
     * @return the step
     */
    private RunStep stepFor(LevelReader level, BlockPos here, BlockState state, Direction side) {
        return runStep(liesOn(level, here), layersOf(state) >= MAX_LAYERS, belowFor(level, here.below(), side));
    }

    /**
     * A drip a runny layer's step down its wall draws, from the wall face
     * of the cell it leaves, at the top of that cell, to its end.
     *
     * @param from where the drip starts, set off the wall as the streak is
     * @param toY  where it ends: the top of the cell below, or the surface of the fill it joins
     */
    record WallDrip(Vec3 from, double toY) {
    }

    /**
     * The drip a runny layer's step draws: a step to the same side of the
     * cell below ends on that face one cell down, a step into the fill of the
     * cell below ends on that fill's surface, and any other step draws none,
     * a fall drawing its own drip.
     * goo-moving-downward-falls-as-drips
     *
     * @param step      the step
     * @param here      the cell the layer leaves
     * @param side      the side whose wall it runs down
     * @param belowFill the fill the cell below holds
     * @return the drip, empty for a step drawing none
     */
    static Optional<WallDrip> wallDripOf(RunStep step, BlockPos here, Direction side, int belowFill) {
        double toY = switch (step) {
            case RUN_DOWN -> here.getY();
            case JOIN_BELOW -> here.getY() - 1 + (double) belowFill / MAX_LAYERS;
            default -> Double.NaN;
        };
        if (Double.isNaN(toY)) {
            return Optional.empty();
        }
        Vec3 middle = Vec3.atCenterOf(here);
        double off = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? HALF - WALL_DRIP_OFF
                : -(HALF - WALL_DRIP_OFF);
        Vec3 onTheWall = middle.with(side.getAxis(), side.getAxis().choose(middle.x, middle.y, middle.z) + off);
        return Optional.of(new WallDrip(new Vec3(onTheWall.x, here.getY() + 1.0, onTheWall.z), toY));
    }

    /**
     * Sends a cell's chunk watchers the drip a runny layer's step draws, moving at the run's pace.
     * goo-moving-downward-falls-as-drips
     *
     * @param level the level
     * @param pos   the cell the layer leaves
     * @param drip  the drip
     */
    private void drawWallDrip(ServerLevel level, BlockPos pos, WallDrip drip) {
        BlockVisuals.sendToWatchers(level, pos, new FallingDripPayload(drip.from(), drip.toY(), pourTicksAt(level),
                colourOf(level, GooColors::highlight), colourOf(level, GooColors::bright), true));
    }

    /**
     * What the cell below a runny layer holds for it.
     *
     * @param level the level
     * @param below the cell below the layer's cell
     * @param side  the side the layer runs down
     * @return what that cell holds for the layer
     */
    private Below belowFor(LevelReader level, BlockPos below, Direction side) {
        BlockState under = level.getBlockState(below);
        if (under.is(this)) {
            // a block with no floor takes no layer past its hold, the goo past it slipping
            int room = onAFloor(level, below) ? MAX_LAYERS : holdOf(cohesionAt(level), attachedSidesOf(under));
            return under.getValue(LAYERS) < room ? Below.FILL_ROOM : Below.FILL_FULL;
        }
        return opens(under) ? openBelowFor(level, below, under, side) : Below.BLOCKED;
    }

    /**
     * What an open cell below a runny layer, air or a cell of this goo's
     * films alone, holds for it: a top under it takes the layer's fill, a
     * wall going on beside it carries the layer down where that side is
     * bare, and no wall lets it fall.
     *
     * @param level the level
     * @param below the open cell below the layer's cell
     * @param under that cell's state
     * @param side  the side the layer runs down
     * @return what that cell holds for the layer
     */
    private Below openBelowFor(LevelReader level, BlockPos below, BlockState under, Direction side) {
        if (liesOn(level, below)) {
            return Below.FILL_ROOM;
        }
        if (!clingsTo(level, below, side)) {
            return Below.WALL_ENDS;
        }
        return coatOn(under, side) == SideCoat.BARE ? Below.WALL_GOES_ON : Below.SIDE_TAKEN;
    }

    /**
     * Drains the block's goo down into goo of its type below with room, as
     * much as that block takes, a whole block on a floor and its hold with
     * none, whatever walls hold this one: goo piles onto goo
     * and joins it, and never hangs over it with a gap between.
     * operator UAT 2026-11-17: goo needs to pile and connect with goo below it
     * stacked-goo-cells-share-one-slime-core
     *
     * @param level the level
     * @param pos   the block
     * @param state the block's state
     * @return true where goo drained down, the block changed or gone
     */
    private boolean drainsDown(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (!under.is(this)) {
            return false;
        }
        // a block with no floor takes no layer past its hold, as a runny layer reaching it finds
        int room = onAFloor(level, below) ? MAX_LAYERS : holdOf(cohesionAt(level), attachedSidesOf(under));
        if (under.getValue(LAYERS) >= room) {
            return false;
        }
        int moved = Math.min(state.getValue(LAYERS), room - under.getValue(LAYERS));
        int left = state.getValue(LAYERS) - moved;
        level.setBlock(pos, left > 0 ? state.setValue(LAYERS, left) : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        level.setBlock(below, under.setValue(LAYERS, under.getValue(LAYERS) + moved), UPDATE_ALL);
        return true;
    }

    /**
     * Attaches the block's goo to each wall beside it its adhesion holds, as
     * a drip striking that wall would: at 3 and up every side with a face to
     * hold it, at 2 only over goo of its type, and at 4 its ceiling too, so
     * goo resting against a wall clings to it in place of lying beside it.
     * operator UAT 2026-11-17: the wall has horizontally laying goo instead of vertically adhered goo
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * adhesion-is-a-ladder-of-holds
     *
     * @param level the level
     * @param pos   the block
     * @param state the block's state
     * @return the block's state with each held wall attached
     */
    private BlockState adhereToWalls(ServerLevel level, BlockPos pos, BlockState state) {
        int adhesion = adhesionAt(level);
        boolean overItsGoo = level.getBlockState(pos.below()).is(this);
        BlockState adhered = state;
        for (Direction side : ATTACHABLE) {
            boolean bare = coatOn(adhered, side) == SideCoat.BARE;
            if (bare && holdsTheSide(adhesion, side, overItsGoo) && clingsTo(level, pos, side)) {
                adhered = withCoat(adhered, side, SideCoat.ATTACHED);
            }
        }
        if (adhered != state) {
            level.setBlock(pos, adhered, UPDATE_ALL);
        }
        return adhered;
    }

    /**
     * Whether the adhesion ladder holds a block's surface.
     * adhesion-is-a-ladder-of-holds
     *
     * @param adhesion   the rung the goo clings at
     * @param side       a horizontal side, or UP for the ceiling
     * @param overItsGoo whether goo of its type stands in the cell below
     * @return true where the goo clings there
     */
    static boolean holdsTheSide(int adhesion, Direction side, boolean overItsGoo) {
        if (side == Direction.UP) {
            return adhesion >= GooTypeDefinition.HOLDS_AN_UNDERSIDE;
        }
        boolean holdsOverGoo = adhesion == GooTypeDefinition.HOLDS_WHERE_IT_STRIKES - 1 && overItsGoo;
        return adhesion >= GooTypeDefinition.HOLDS_WHERE_IT_STRIKES || holdsOverGoo;
    }

    /**
     * Lets go of each wall that has lost the face holding the block's goo,
     * that side left bare; the side kept no layers of its own, so the
     * volume stays whole, and a block left unable to stand falls whole.
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param level the level
     * @param pos   the cell
     * @param state the cell's state
     * @return the cell's state with its loose films shed
     */
    private BlockState shedLooseFilms(ServerLevel level, BlockPos pos, BlockState state) {
        List<Direction> loose = filmedSides(state).stream().filter(side -> !clingsTo(level, pos, side)).toList();
        BlockState shed = state;
        for (Direction side : loose) {
            shed = withCoat(shed, side, SideCoat.BARE);
        }
        if (loose.isEmpty()) {
            return state;
        }
        level.setBlock(pos, shed, UPDATE_ALL);
        return shed;
    }

    /**
     * A block left with no floor and no wall to cling to falls whole: its
     * space stands open and each layer of its volume falls to the first top
     * below, landing there when its drip arrives, or comes back as its goo
     * where no top stands below.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param level the level
     * @param pos   the block
     * @param state the block's state
     */
    private void fallWhole(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        @Nullable UUID placer = placerAt(server, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        for (int layer = 0; layer < layersHeld(state); layer++) {
            if (!beginFall(server, pos, pos.getY() + HALF, placer)) {
                LoadedAbilityCost.of(server, binding.layingAbility()).map(cost -> reclaimed(1, cost))
                        .filter(mb -> mb > 0)
                        .ifPresent(mb -> popResource(server, pos, GooStacks.createForOutput(binding.gooType(), mb)));
            }
        }
    }

    /**
     * Spreads a cell a drip just raised, at once: it pours into its side
     * cells, and off its ledges, until none stands two layers below it, in
     * the tick the drip lands; the side cells that took a layer spread on at
     * their goo type's viscosity's pace.
     * spread-decreases-the-cell-it-leaves
     * operator ruling 2026-11-14: spread the blob when it updates, immediately
     *
     * @param level the level
     * @param pos   the raised cell
     */
    public void spreadAtOnce(ServerLevel level, BlockPos pos) {
        // each pour moves a layer the cell holds, so a cell runs out of pours within its fifteen layers
        for (int pour = 0; pour < MAX_LAYERS; pour++) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(this) || !pourOnce(level, pos, state)) {
                return;
            }
        }
    }

    /**
     * Pours one layer into the side the flow rule picks; a side whose pour
     * cannot land, one that would rise around a living thing's feet among
     * them, stands blocked and the rule picks again among the rest, so one
     * refused side never stops the cell spreading and stacking it into a
     * pillar.
     * operator report 2026-11-14: hitting goo redirects the runner off the track and hitches it
     *
     * @param level the level
     * @param pos   the pouring cell
     * @param state the pouring cell's state
     * @return true where the cell gave a layer
     */
    private boolean pourOnce(ServerLevel level, BlockPos pos, BlockState state) {
        List<Side> sides = new ArrayList<>(SIDES.stream().map(side -> sideOf(level, pos.relative(side), side))
                .toList());
        int cohesion = cohesionAt(level);
        int adhesion = adhesionAt(level);
        for (int tried = 0; tried < SIDES.size(); tried++) {
            Optional<Direction> toward = SlimeFlow.pour(state.getValue(LAYERS), cohesion, adhesion, sides);
            if (toward.isEmpty()) {
                return false;
            }
            pourToward(level, pos, state, toward.get());
            if (level.getBlockState(pos) != state) {
                return true;
            }
            sides.replaceAll(side -> side.direction() == toward.get() ? Side.blocked(side.direction()) : side);
        }
        return false;
    }

    /**
     * What a side neighbour's cell can do with a poured layer: a cell of this
     * goo holds it, a cell of another goo or any other block takes none.
     *
     * @param level the level
     * @param cell  the neighbour's cell
     * @param side  the side it stands on
     * @return the side, its layers and its footing
     */
    private Side sideOf(LevelReader level, BlockPos cell, Direction side) {
        BlockState standing = level.getBlockState(cell);
        if (standing.is(this)) {
            return Side.holds(side, standing.getValue(LAYERS));
        }
        return opens(standing) ? openSideOf(level, cell, side) : Side.blocked(side);
    }

    /**
     * What an open side neighbour, air or a cell of this goo's films alone,
     * can do with a poured layer: over an open cell or unfilled goo the layer
     * falls through it as off a ledge, over a top that holds a world goo it
     * stays, and over anything else it takes none.
     *
     * @param level the level
     * @param cell  the neighbour's cell
     * @param side  the side it stands on
     * @return the side, its layers and its footing
     */
    private Side openSideOf(LevelReader level, BlockPos cell, Direction side) {
        BlockState under = level.getBlockState(cell.below());
        if (opens(under) || unfilled(under)) {
            return Side.ledge(side);
        }
        return liesOn(level, cell) ? Side.holds(side, 0) : Side.blocked(side);
    }

    /**
     * Whether a cell stands open to a poured layer's fill: air, or a cell of
     * this goo holding fill 0, its goo in films on its walls.
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param state the cell's state
     * @return true where the cell holds no fill
     */
    private boolean opens(BlockState state) {
        return state.isAir();
    }

    private boolean unfilled(BlockState state) {
        return state.is(this) && state.getValue(LAYERS) < MAX_LAYERS;
    }

    /**
     * The ticks this cell waits between pours, read from its goo type's
     * viscosity; a cell whose goo type's definition does not load pours at
     * water's pace.
     * spread-decreases-the-cell-it-leaves
     *
     * @param level the level, whose registries hold the goo type
     * @return the ticks between pours
     */
    private int pourTicksAt(LevelReader level) {
        int viscosity = level.registryAccess().get(binding.gooType()).map(type -> type.value().viscosity())
                .orElse(SlimeFlow.WATER_VISCOSITY);
        return SlimeFlow.pourTicks(viscosity);
    }

    /**
     * The step in layers this cell holds over a neighbour before pouring,
     * read from its goo type's cohesion; a cell whose goo type's definition
     * does not load levels flat.
     * cohesion-is-the-step-the-goo-holds
     *
     * @param level the level, whose registries hold the goo type
     * @return the cohesion
     */
    public int cohesionAt(LevelReader level) {
        return level.registryAccess().get(binding.gooType()).map(type -> type.value().cohesion()).orElse(0);
    }

    /**
     * The rung of the adhesion ladder this cell clings at, read from its goo
     * type's adhesion; a cell whose goo type's definition does not load holds
     * tops, keeping its last layer where it was laid.
     * adhesion-is-a-ladder-of-holds
     *
     * @param level the level, whose registries hold the goo type
     * @return the adhesion
     */
    public int adhesionAt(LevelReader level) {
        return level.registryAccess().get(binding.gooType()).map(type -> type.value().adhesion())
                .orElse(HOLDS_TOPS);
    }

    /**
     * Moves one whole layer from a cell toward a side: into the neighbour, an
     * open cell over a top that can hold a world goo becoming a one-layer
     * cell, or down off the ledge to the first top below it; the cell loses
     * the layer the landing takes.
     * spread-decreases-the-cell-it-leaves
     *
     * @param level the level
     * @param pos   the pouring cell
     * @param state the pouring cell's state
     * @param side  the side it pours toward
     */
    private void pourToward(ServerLevel level, BlockPos pos, BlockState state, Direction side) {
        BlockPos toward = pos.relative(side);
        BlockState beside = level.getBlockState(toward);
        if (opens(beside) && !liesOn(level, toward)) {
            pourOffTheLedge(level, pos, state, toward);
        } else {
            pourInto(level, pos, state, toward);
        }
    }

    /**
     * Pours one layer off a ledge: the cell loses it at once, and it falls
     * through the open cell beside to the first top below, landing there
     * when its drip arrives; a ledge with no top below takes no layer.
     * spread-decreases-the-cell-it-leaves
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param level the level
     * @param pos   the pouring cell
     * @param state the pouring cell's state
     * @param ledge the open cell beside it the layer falls through
     */
    private void pourOffTheLedge(ServerLevel level, BlockPos pos, BlockState state, BlockPos ledge) {
        if (landingBelow(level, ledge).isEmpty()) {
            return;
        }
        int layers = state.getValue(LAYERS);
        @Nullable UUID placer = placerAt(level, pos);
        level.setBlock(pos, lessALayer(state), UPDATE_ALL);
        beginFall(level, ledge, pos.getY() + (double) layers / MAX_LAYERS, placer);
    }

    /**
     * Pours one layer from a cell into the cell its layer lands in, where
     * that cell is open or holds this goo short of a whole block, a film
     * cell keeping its films; a cell pouring its last layer away keeps what
     * its walls hold, or stands open, the layer moved and never removed.
     * spread-decreases-the-cell-it-leaves
     * adhesion-is-a-ladder-of-holds
     *
     * @param level   the level
     * @param pos     the pouring cell
     * @param state   the pouring cell's state
     * @param landing the cell the layer lands in
     */
    private void pourInto(ServerLevel level, BlockPos pos, BlockState state, BlockPos landing) {
        BlockState taking = level.getBlockState(landing);
        SlimeFlow.Poured poured = SlimeFlow.poured(state.getValue(LAYERS), layersOf(taking));
        if (takesThePour(level, landing, taking, poured)) {
            BlockState into = taking.isAir() ? defaultBlockState() : taking;
            level.setBlock(landing, into.setValue(LAYERS, poured.taker()), UPDATE_ALL);
            carryPlacer(level, pos, landing);
            level.setBlock(pos, lessALayer(state), UPDATE_ALL);
        }
    }

    /**
     * Whether a landing cell takes a poured layer: it is open or holds this
     * goo, stays within a whole block, and rises around no living thing's feet.
     *
     * @param level   the level
     * @param landing the cell the layer lands in
     * @param taking  the landing cell's state
     * @param poured  what the pour leaves the two cells holding
     * @return true where the layer lands
     */
    private boolean takesThePour(ServerLevel level, BlockPos landing, BlockState taking, SlimeFlow.Poured poured) {
        boolean holdsLayers = taking.isAir() || taking.is(this);
        return holdsLayers && poured.taker() <= MAX_LAYERS
                && !wouldRiseAroundFeet(level, landing, layersOf(taking), poured.taker());
    }

    /**
     * Where a layer falling through a ledge lands: in the first unfilled
     * cell of this goo below it, or in the last open cell over the first
     * sturdy top.
     *
     * @param level the level
     * @param ledge the open cell the layer falls through
     * @return the landing cell, empty where it finds no top
     */
    public Optional<BlockPos> landingBelow(LevelReader level, BlockPos ledge) {
        BlockPos open = ledge;
        BlockPos below = ledge.below();
        while (below.getY() >= level.getMinY() && opens(level.getBlockState(below))) {
            open = below;
            below = below.below();
        }
        if (below.getY() >= level.getMinY() && unfilled(level.getBlockState(below))) {
            return Optional.of(below);
        }
        return liesOn(level, open) ? Optional.of(open) : Optional.empty();
    }

    /**
     * Breaks whatever fall lands on a bouncing cell; any other cell lets the
     * fall harm as its ground would.
     * bounce-ramps-jump-height-while-jump-is-held
     */
    @Override
    public void fallOn(@NonNull Level level, @NonNull BlockState state, @NonNull BlockPos pos,
                       @NonNull Entity entity, double fallDistance) {
        if (surface().bounces()) {
            entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
        } else {
            super.fallOn(level, state, pos, entity, fallDistance);
        }
    }

    /**
     * Bounces what lands on a bouncing cell, ramping while jump is held.
     * bounce-ramps-jump-height-while-jump-is-held
     */
    @Override
    public void updateEntityMovementAfterFallOn(@NonNull BlockGetter level, @NonNull Entity entity) {
        if (surface().bounces() && entity.getDeltaMovement().y < 0) {
            SlimeCoatBounce.bounce(entity);
        } else {
            super.updateEntityMovementAfterFallOn(level, entity);
        }
    }

    /**
     * Ramps the horizontal velocity of a thing trying to move on a ramping
     * cell, the same at any height, by the ramp the laying ability's slick
     * step names; on a cutting cell, the shards over it cut a thing moving
     * through them, on the server, by the laying ability's shatter step; a
     * smiting cell burns an undead standing on it; a slowing cell catches
     * what stands on it.
     * slick-coats-the-ground-with-kinetic-speed
     * slick-is-a-layered-cell-that-flows-thinner-and-faster-than-slime
     * each-shatter-cut-has-a-chance-to-deplete-a-layer
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        if (surface().ramps()) {
            SlickStep.in(layingProgram(level)).ifPresent(slick -> SlickBuild.slide(entity, slick));
        }
        if (surface().cuts() && level instanceof ServerLevel server) {
            ShatterStep.in(layingProgram(level)).ifPresent(shatter -> ShatterCut.cut(server, pos, state, entity,
                    shatter));
        }
        smite(level, pos, state, entity);
        catchIn(level, entity);
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Smites an undead inside a smiting cell's space, where a cell too thin
     * to stand on as the block under its feet still touches it, and catches a
     * thing inside a slowing cell's block space, wading in it or standing on
     * a cell short of a whole block.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        smite(level, pos, state, entity);
        catchIn(level, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * Burns an undead touching a smiting cell and deals it holy damage by
     * the cell's layers, as the laying ability's wisps step names, crediting
     * the player who placed the cell.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param level  the level the cell stands in
     * @param pos    the cell
     * @param state  the cell's state
     * @param entity the entity touching the cell
     */
    private void smite(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (surface().smites() && level instanceof ServerLevel server) {
            WispsStep.in(layingProgram(level)).ifPresent(wisps -> WispsSmite.touch(server, entity,
                    state.getValue(LAYERS), wisps, creditedPlayerAt(server, pos)));
        }
    }

    /**
     * Catches a thing on a slowing cell by its laying ability's leech goo
     * step: slows its horizontal motion by the step's speed factor once a
     * tick, as soul sand does, momentum carried and a fall harming as on any
     * block, on both sides since the client moves its own player. The goo's
     * spirits deal no damage.
     * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
     * leech-goo-slows-the-way-soul-sand-does
     *
     * @param level  the level the cell stands in
     * @param entity the thing caught
     */
    private void catchIn(Level level, Entity entity) {
        if (surface().slows()) {
            LeechGooStep.in(layingProgram(level)).ifPresent(leech -> LeechSlow.slowOnce(level, entity, leech));
        }
    }

    /**
     * The steps the laying ability runs, from the server's registry or the
     * client's synced abilities, since the client moves its own player.
     *
     * @param level the level the cell stands in
     * @return the steps, empty where the ability is not loaded
     */
    protected List<Step> layingProgram(Level level) {
        if (level.isClientSide()) {
            return ISidedProxy.get().syncedAbilityProgram(binding.layingAbility().toString());
        }
        return Optional.ofNullable(AbilityRegistry.of(level).getAbility(binding.layingAbility()))
                .map(AbilityDefinition::behaviors).orElse(List.of());
    }

    /**
     * Wakes the cell, since a changed neighbour may open a pour, take a
     * film's wall, or take the top it lies on; its tick sheds a loose film
     * or breaks a cell left unable to stand, giving back its goo, so no
     * layer is removed with the block it rested on.
     * slime-is-a-layered-cell-that-levels-out
     * goo-cell-holds-a-fill-and-a-state-per-side
     */
    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        ticks.scheduleTick(pos, this, pourTicksAt(level));
        return state;
    }
}
