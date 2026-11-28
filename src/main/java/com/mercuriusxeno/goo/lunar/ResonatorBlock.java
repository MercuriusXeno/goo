package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * The resonator: alien tech in a simple entityless block that takes a vibration and echoes it, a sound relay. A
 * resting resonator that hears a vibration within a sculk sensor's hearing range carries it for as many ticks as the
 * sound took to reach it, then sets the same vibration off again from where it stands and cools down. While it
 * carries or cools it hears nothing, and it cools longer than any echo takes to cross its range, so two resonators in
 * each other's range relay a sound onward once and never bounce it back and forth; a chain relays it hop by hop.
 * decision resonator-echoes-a-vibration-as-a-sound-relay
 */
public class ResonatorBlock extends Block {

    /** Resting, carrying an echo, or cooling down after one, as a sculk sensor's phase reads. */
    public static final EnumProperty<SculkSensorPhase> PHASE = BlockStateProperties.SCULK_SENSOR_PHASE;
    /** How far a resonator hears, a sculk sensor's own hearing range. */
    public static final int HEARING_RANGE = 8;
    /** How long a resonator rests deaf after an echo, longer than any echo takes to cross its range. */
    static final int COOLDOWN_TICKS = 40;
    private static final int PLACE_FLAGS = Block.UPDATE_ALL;

    /**
     * The needle spire's shape, the operator's pick: a stepped plate, a needle post, a wide and a narrow ring, and
     * the post's tip, each ring its own size.
     */
    private static final VoxelShape SPIRE = Shapes.or(Block.box(2, 0, 2, 14, 1, 14), Block.box(4, 1, 4, 12, 3, 12),
            Block.box(7, 3, 7, 9, 16, 9), Block.box(5, 6, 5, 11, 7, 11), Block.box(6, 10, 6, 10, 11, 10));

    /** The vibration each carrying resonator will echo, by where it stands; held on the server, never saved. */
    private static final Map<GlobalPos, Holder<GameEvent>> CARRIED = new HashMap<>();

    /**
     * Creates the resonator, resting.
     *
     * @param properties the block properties
     */
    public ResonatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, SculkSensorPhase.INACTIVE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SPIRE;
    }

    /**
     * Whether a resonator hears now: only while it rests.
     *
     * @param state the resonator's state
     * @return whether it rests
     */
    public static boolean hears(BlockState state) {
        return state.getValue(PHASE) == SculkSensorPhase.INACTIVE;
    }

    /**
     * Takes up a vibration a resting resonator heard: it carries it, to echo once the sound's travel time has passed.
     *
     * @param level    the level it stands in
     * @param pos      the resonator
     * @param state    its resting state
     * @param event    the vibration it heard
     * @param distance how far the sound travelled to it, in blocks
     */
    public void carry(ServerLevel level, BlockPos pos, BlockState state, Holder<GameEvent> event, double distance) {
        CARRIED.put(GlobalPos.of(level.dimension(), pos.immutable()), event);
        level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.ACTIVE), PLACE_FLAGS);
        level.scheduleTick(pos, this, Math.max(1, (int) Math.floor(distance)));
    }

    /** A carrying resonator echoes and cools down; a cooling one comes to rest. */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        SculkSensorPhase phase = state.getValue(PHASE);
        if (phase == SculkSensorPhase.ACTIVE) {
            BlockState cooling = state.setValue(PHASE, SculkSensorPhase.COOLDOWN);
            level.setBlock(pos, cooling, PLACE_FLAGS);
            Holder<GameEvent> echo = CARRIED.remove(GlobalPos.of(level.dimension(), pos));
            if (echo != null) {
                level.gameEvent(echo, Vec3.atCenterOf(pos), GameEvent.Context.of(cooling));
            }
            level.scheduleTick(pos, this, COOLDOWN_TICKS);
        } else if (phase == SculkSensorPhase.COOLDOWN) {
            level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.INACTIVE), PLACE_FLAGS);
        }
    }

    /** A resonator taken from the world while it carries a vibration drops it unechoed. */
    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean movedByPiston) {
        CARRIED.remove(GlobalPos.of(level.dimension(), pos));
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
