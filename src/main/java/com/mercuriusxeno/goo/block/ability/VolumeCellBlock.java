package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.LoadedAbilityCost;
import com.mercuriusxeno.goo.ability.program.SlickStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.block.ability.SlimeFlow.Side;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The volume cell, the one block every goo type's world goo stands on,
 * bound to a goo type by a CellBinding: a plain block holding one to fifteen
 * layers in its block state, with no block entity, drawn as height like snow
 * layers, laid on a block's top with the block under it unchanged and
 * showing through. Each landed drip adds a layer, and a cell taller than a
 * side neighbour of its own goo pours a layer into it, and off a ledge
 * downward, until level, waiting between pours as its goo type's viscosity
 * sets. A layer is priced at a fifteenth of the laying ability's cost; the
 * cell stands until something destroys it, then drops its goo type worth
 * that price per layer. What it does to things on it is its surface's: slime
 * breaks a fall and bounces, slick ramps a thing trying to move at any height.
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
    /** The most layers one cell holds: a whole block of goo. */
    public static final int MAX_LAYERS = 15;
    /** The adhesion of a goo that holds tops, every goo's rule before adhesion was read. */
    private static final int HOLDS_TOPS = 1;
    /**
     * The fill the cell holds on its floor, each layer a fifteenth of a block
     * of goo; a cell of fill 0 holds its goo in films alone.
     * goo-cell-holds-a-fill-and-a-state-per-side
     */
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 0, MAX_LAYERS);

    private static final Map<Direction, EnumProperty<SideCoat>> COATS = Map.of(Direction.NORTH, NORTH,
            Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST);
    /** Each fill's box, indexed by the fill, none at 0. */
    private static final VoxelShape[] LAYER_BOXES = new VoxelShape[MAX_LAYERS + 1];
    /** Each side's film, a slab one layer thick against that side's wall. */
    private static final Map<Direction, VoxelShape> FILM_BOXES = Map.of(
            Direction.NORTH, Shapes.box(0, 0, 0, 1, 1, 1.0 / MAX_LAYERS),
            Direction.SOUTH, Shapes.box(0, 0, 1 - 1.0 / MAX_LAYERS, 1, 1, 1),
            Direction.WEST, Shapes.box(0, 0, 0, 1.0 / MAX_LAYERS, 1, 1),
            Direction.EAST, Shapes.box(1 - 1.0 / MAX_LAYERS, 0, 0, 1, 1, 1));

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
                .setValue(SOUTH, SideCoat.BARE).setValue(WEST, SideCoat.BARE).setValue(LAYERS, 1));
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
        return state.getBlock() instanceof VolumeCellBlock ? state.getValue(coatProperty(side)) : SideCoat.BARE;
    }

    /**
     * Every layer a cell holds: its fill and one for each side holding a layer.
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param fill  the fill on its floor
     * @param sides what its four sides hold
     * @return the layers
     */
    static int layersHeld(int fill, Collection<SideCoat> sides) {
        return fill + (int) sides.stream().filter(SideCoat::holdsALayer).count();
    }

    /**
     * Whether a cell stands: a cell holding fill lies on a top, and a cell of
     * fill 0 stands on its films, every one clinging to its wall, and while a
     * runny layer passes through it on its way down.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     *
     * @param fill            the fill on its floor
     * @param liesOnATop      whether the block under it has a sturdy top
     * @param filmsClinging   for each film it holds, whether its wall has a face to hold it
     * @param holdsARunnyLayer whether a side holds a runny layer
     * @return true where the cell stands
     */
    static boolean stands(int fill, boolean liesOnATop, List<Boolean> filmsClinging, boolean holdsARunnyLayer) {
        if (fill > 0) {
            return liesOnATop;
        }
        boolean holdsGoo = holdsARunnyLayer || !filmsClinging.isEmpty();
        return holdsGoo && filmsClinging.stream().allMatch(Boolean::booleanValue);
    }

    /**
     * The sides a cell holds a film on.
     *
     * @param state the cell's state
     * @return each side holding a film
     */
    static List<Direction> filmedSides(BlockState state) {
        return SIDES.stream().filter(side -> coatOn(state, side) == SideCoat.FILM).toList();
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
     * Whether a side's wall can hold a film: the block beside has a sturdy face toward the cell.
     *
     * @param level the level
     * @param pos   the cell
     * @param side  the side the wall stands on
     * @return true where a film clings there
     */
    public static boolean clingsTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos wall = pos.relative(side);
        return level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite());
    }

    /**
     * Every layer a cell holds, read from its state.
     *
     * @param state the cell's state
     * @return its fill and its sides' layers
     */
    public static int layersHeldIn(BlockState state) {
        return layersHeld(state.getValue(LAYERS), SIDES.stream().map(side -> coatOn(state, side)).toList());
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
     * Gives back the goo the cell's layers cost, as the glow crystal gives
     * back its goo, whatever breaks the cell, Vacuum included; a cell broken
     * where its laying ability's definition does not load gives back nothing.
     * breaking-slime-drops-weird-goo-worth-its-layers
     * breaking-slick-drops-kinetic-goo-worth-its-film
     */
    @Override
    protected @NonNull List<ItemStack> getDrops(@NonNull BlockState state, LootParams.@NonNull Builder builder) {
        int mb = LoadedAbilityCost.of(builder.getLevel(), binding.layingAbility())
                .map(cost -> reclaimed(layersHeldIn(state), cost)).orElse(0);
        return mb > 0 ? List.of(GooStacks.createForOutput(binding.gooType(), mb)) : List.of();
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
        return coated(standing, cell, side, SideCoat.FILM);
    }

    /**
     * What a cell becomes when a layer of a goo lands on one of its sides:
     * air takes a cell of fill 0 holding that layer there, a cell of that goo
     * whose side stands bare takes it, anything else nothing.
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
            return Optional.of(cell.defaultBlockState().setValue(LAYERS, 0).setValue(coatProperty(side), coat));
        }
        return standing.is(cell) && coatOn(standing, side) == SideCoat.BARE
                ? Optional.of(standing.setValue(coatProperty(side), coat)) : Optional.empty();
    }

    /**
     * Where a layer lands, and the state it leaves that cell in.
     *
     * @param pos   the cell
     * @param state its state with the layer landed
     */
    public record Placement(BlockPos pos, BlockState state) {
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
     * Whether a cell can hold a world goo: the block under it has a sturdy top.
     *
     * @param level the level
     * @param pos   the cell
     * @return true where a world goo can lie
     */
    public static boolean liesOn(LevelReader level, BlockPos pos) {
        BlockPos under = pos.below();
        return level.getBlockState(under).isFaceSturdy(level, under, Direction.UP);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, LAYERS);
    }

    /**
     * The cell's box: its fill's, and a slab one layer thick on each wall it holds a film on.
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param state the cell's state
     * @return the box
     */
    static VoxelShape boxOf(BlockState state) {
        VoxelShape box = LAYER_BOXES[state.getValue(LAYERS)];
        for (Direction side : filmedSides(state)) {
            box = Shapes.or(box, FILM_BOXES.get(side));
        }
        return box;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return boxOf(state);
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return boxOf(state);
    }

    /**
     * A cell holding fill stands on a top; a cell of fill 0 stands on its
     * films, each clinging to a wall with a face to hold it.
     * goo-cell-holds-a-fill-and-a-state-per-side
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        return stands(state.getValue(LAYERS), liesOn(level, pos),
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
            level.destroyBlock(pos, true);
            return;
        }
        level.scheduleTick(pos, this, pourTicksAt(level));
    }

    /**
     * Sheds each film whose wall is gone, then breaks a cell left unable to
     * stand, giving back its goo, else pours one layer into the side the flow
     * rule picks, which places the cell anew and so schedules the next pour
     * after its goo type's viscosity's ticks, until the cell stands level.
     * slime-is-a-layered-cell-that-levels-out
     * spread-decreases-the-cell-it-leaves
     * goo-cell-holds-a-fill-and-a-state-per-side
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        BlockState held = runRunnyLayers(level, pos, shedLooseFilms(level, pos, state));
        if (!held.is(this)) {
            return;
        }
        if (!canSurvive(held, level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        pourOnce(level, pos, held);
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
            Optional<Placement> step = runFrom(level, pos, left, side);
            if (step.isPresent()) {
                left = stepRunnyLayer(level, pos, left, side, step.get());
            }
        }
        if (layersHeldIn(left) == 0) {
            left = Blocks.AIR.defaultBlockState();
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
        if (landing.pos().equals(pos)) {
            return landing.state().setValue(coatProperty(side), SideCoat.BARE);
        }
        level.setBlock(landing.pos(), landing.state(), UPDATE_ALL);
        return state.setValue(coatProperty(side), SideCoat.BARE);
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
        return onTheSide.isPresent() ? onTheSide.map(state -> new Placement(pos, state))
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
        RunStep step = runStep(liesOn(level, here), layersOf(state) >= MAX_LAYERS, belowFor(level, below, side));
        return switch (step) {
            case JOIN_HERE -> thickened(state, this).map(into -> new Placement(here, into));
            case JOIN_BELOW -> thickened(under, this).map(into -> new Placement(below, into));
            case RUN_DOWN -> coated(under, this, side, SideCoat.RUNNY).map(into -> new Placement(below, into));
            case FALL -> landingBelow(level, below).flatMap(cell -> thickened(level.getBlockState(cell), this)
                    .map(into -> new Placement(cell, into)));
            case WAIT -> Optional.empty();
        };
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
        if (under.is(this) && under.getValue(LAYERS) > 0) {
            return under.getValue(LAYERS) < MAX_LAYERS ? Below.FILL_ROOM : Below.FILL_FULL;
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
     * Drops the layer of each film whose wall has lost the face holding it
     * as goo, its side left bare, where the cell stands on what else it
     * holds; a cell holding nothing else breaks whole, its films among its drops.
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
            shed = shed.setValue(coatProperty(side), SideCoat.BARE);
        }
        if (loose.isEmpty() || !canSurvive(shed, level, pos)) {
            return state;
        }
        level.setBlock(pos, shed, UPDATE_ALL);
        LoadedAbilityCost.of(level, binding.layingAbility()).map(cost -> reclaimed(loose.size(), cost))
                .filter(mb -> mb > 0)
                .ifPresent(mb -> popResource(level, pos, GooStacks.createForOutput(binding.gooType(), mb)));
        return shed;
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
        if (standing.is(this) && standing.getValue(LAYERS) > 0) {
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
        return state.isAir() || state.is(this) && state.getValue(LAYERS) == 0;
    }

    private boolean unfilled(BlockState state) {
        return state.is(this) && state.getValue(LAYERS) > 0 && state.getValue(LAYERS) < MAX_LAYERS;
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
    private int cohesionAt(LevelReader level) {
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
        Optional<BlockPos> landing = opens(beside) && !liesOn(level, toward) ? landingBelow(level, toward)
                : Optional.of(toward);
        landing.ifPresent(cell -> pourInto(level, pos, state, cell));
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
            level.setBlock(pos, state.setValue(LAYERS, poured.giver()), UPDATE_ALL);
            if (poured.giver() == 0 && filmedSides(state).isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
            }
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
    private Optional<BlockPos> landingBelow(LevelReader level, BlockPos ledge) {
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
     * step names.
     * slick-coats-the-ground-with-kinetic-speed
     * slick-is-a-layered-cell-that-flows-thinner-and-faster-than-slime
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        if (surface().ramps()) {
            SlickStep.in(layingProgram(level)).ifPresent(slick -> SlickBuild.slide(entity, slick));
        }
        super.stepOn(level, pos, state, entity);
    }

    /**
     * The steps the laying ability runs, from the server's registry or the
     * client's synced abilities, since the client moves its own player.
     *
     * @param level the level the cell stands in
     * @return the steps, empty where the ability is not loaded
     */
    private List<Step> layingProgram(Level level) {
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
