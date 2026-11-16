package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.LoadedAbilityCost;
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
import com.mercuriusxeno.goo.network.RedHotPayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
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

    /** Whether the cell drips down the north side of the block under it. */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    /** Whether the cell drips down the east side of the block under it. */
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    /** Whether the cell drips down the south side of the block under it. */
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    /** Whether the cell drips down the west side of the block under it. */
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    /** The most layers one cell holds: a whole block of goo. */
    public static final int MAX_LAYERS = 15;
    /** The layers the cell holds, each a fifteenth of a block of goo. */
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, MAX_LAYERS);

    private static final Map<Direction, BooleanProperty> DRIPS = Map.of(Direction.NORTH, NORTH,
            Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST);
    /** Each layer count's box, indexed by the count. */
    private static final VoxelShape[] LAYER_BOXES = new VoxelShape[MAX_LAYERS + 1];

    static {
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
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(LAYERS, 1));
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
                .flatMap(cost -> reclaimedBy(binding, state.getValue(LAYERS), cost))
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
     * What a cell becomes when one more layer of a goo lands in it: air takes
     * a cell of one layer, a cell of that goo one layer more up to a whole
     * block of fifteen, anything else nothing.
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
        int layers = standing.is(cell) ? standing.getValue(LAYERS) : 0;
        return layers > 0 && layers < MAX_LAYERS ? Optional.of(standing.setValue(LAYERS, layers + 1))
                : Optional.empty();
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
     * A cell's state fitted to the cells beside it: it drips on each side
     * where no cell of its goo continues the film.
     *
     * @param state the cell's state
     * @param level the level
     * @param pos   the cell
     * @return the state with each side's drip set
     */
    BlockState fittedTo(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState fitted = state;
        for (Map.Entry<Direction, BooleanProperty> side : DRIPS.entrySet()) {
            BlockState beside = level.getBlockState(pos.relative(side.getKey()));
            fitted = fitted.setValue(side.getValue(), !continuesInto(beside));
        }
        return fitted;
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
     * sturdy top, or, for a goo pooling on lava, is a lava source.
     * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
     *
     * @param level the level
     * @param pos   the cell
     * @return true where this world goo can lie
     */
    public boolean liesOn(LevelReader level, BlockPos pos) {
        BlockPos under = pos.below();
        BlockState footing = level.getBlockState(under);
        return footing.isFaceSturdy(level, under, Direction.UP)
                || binding.liesOnLava() && footing.is(Blocks.LAVA) && footing.getFluidState().isSource();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, LAYERS);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return LAYER_BOXES[state.getValue(LAYERS)];
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return LAYER_BOXES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        return liesOn(level, pos);
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

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return fittedTo(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    /**
     * Fits a cell laid straight into its block space, as a drip's is, to the
     * film beside it, and starts it flowing.
     * slime-is-a-layered-cell-that-levels-out
     */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!canSurvive(state, level, pos)) {
            // a drip landing beside a wall finds no top to lie on
            level.removeBlock(pos, false);
            return;
        }
        BlockState fitted = fittedTo(state, level, pos);
        if (fitted != state) {
            level.setBlock(pos, fitted, UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, pourTicksAt(level));
    }

    /**
     * Pours one layer into the side the flow rule picks, which places the
     * cell anew and so schedules the next pour after its goo type's
     * viscosity's ticks, until the cell stands level; a level cell whose
     * laying ability sheds wisps sheds one toward the dark.
     * slime-is-a-layered-cell-that-levels-out
     * spread-decreases-the-cell-it-leaves
     * wisps-goo-sheds-a-wisp-toward-nearby-dark
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        if (!meltBelow(level, pos, state) && !pourOnce(level, pos, state)) {
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
        int spent = molten.map(step -> MoltenMelt.layersSpent(state.getValue(LAYERS),
                level.getBlockState(pos.below()), step.meltable())).orElse(0);
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
            meltInto(level, pos, state, spent);
        }
        return true;
    }

    /**
     * Turns the block under a cell into a lava source, the cell resting on it
     * lower by the layers the melt spends and gone where that empties it.
     * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block
     *
     * @param level the level
     * @param pos   the cell
     * @param state the cell's state
     * @param spent the layers the melt spends
     */
    private static void meltInto(ServerLevel level, BlockPos pos, BlockState state, int spent) {
        level.setBlock(pos.below(), Blocks.LAVA.defaultBlockState(), UPDATE_ALL);
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
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
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
        for (int tried = 0; tried < SIDES.size(); tried++) {
            Optional<Direction> toward = SlimeFlow.pour(state.getValue(LAYERS), sides);
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
        if (!standing.isAir()) {
            return Side.blocked(side);
        }
        BlockState under = level.getBlockState(cell.below());
        if (under.isAir() || unfilled(under)) {
            return Side.ledge(side);
        }
        return liesOn(level, cell) ? Side.holds(side, 0) : Side.blocked(side);
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
        Optional<BlockPos> landing = beside.isAir() && !liesOn(level, toward) ? landingBelow(level, toward)
                : Optional.of(toward);
        landing.ifPresent(cell -> pourInto(level, pos, state, cell));
    }

    /**
     * Pours one layer from a cell into the cell its layer lands in, where
     * that cell is open or holds this goo short of a whole block.
     * spread-decreases-the-cell-it-leaves
     *
     * @param level   the level
     * @param pos     the pouring cell
     * @param state   the pouring cell's state
     * @param landing the cell the layer lands in
     */
    private void pourInto(ServerLevel level, BlockPos pos, BlockState state, BlockPos landing) {
        BlockState taking = level.getBlockState(landing);
        boolean holdsLayers = taking.isAir() || taking.is(this);
        SlimeFlow.Poured poured = SlimeFlow.poured(state.getValue(LAYERS), layersOf(taking));
        if (holdsLayers && poured.taker() <= MAX_LAYERS
                && !wouldRiseAroundFeet(level, landing, layersOf(taking), poured.taker())) {
            BlockState into = taking.isAir() ? defaultBlockState() : taking;
            level.setBlock(landing, into.setValue(LAYERS, poured.taker()), UPDATE_ALL);
            level.setBlock(pos, state.setValue(LAYERS, poured.giver()), UPDATE_ALL);
        }
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
        while (below.getY() >= level.getMinY() && level.getBlockState(below).isAir()) {
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
        smite(level, state, entity);
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
        smite(level, state, entity);
        catchIn(level, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * Burns an undead touching a smiting cell and deals it holy damage by
     * the cell's layers, as the laying ability's wisps step names.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     *
     * @param level  the level the cell stands in
     * @param state  the cell's state
     * @param entity the entity touching the cell
     */
    private void smite(Level level, BlockState state, Entity entity) {
        if (surface().smites() && level instanceof ServerLevel server) {
            WispsStep.in(layingProgram(level))
                    .ifPresent(wisps -> WispsSmite.touch(server, entity, state.getValue(LAYERS), wisps));
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
     * Drops the cell where its top is gone, refits its drips, and wakes the
     * flow, since a changed neighbour may open a pour.
     * slime-is-a-layered-cell-that-levels-out
     */
    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        ticks.scheduleTick(pos, this, pourTicksAt(level));
        BooleanProperty drip = DRIPS.get(direction);
        return drip == null ? state : state.setValue(drip, !continuesInto(neighborState));
    }
}
