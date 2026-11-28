package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.ability.world.BeaconRails;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * The light beacon's block entity: its column is a reflector's light rail at full power, so it lays rail at the max
 * light through every cell of air the column runs through, to the first block that stops it, and takes back the rail
 * the column leaves as blocks change across it or the beacon goes. It refreshes as it first ticks and every second
 * after, as a reflector does. The client's renderer draws the column.
 * decision bulb-one-model-max-light-beacon-combo
 * decision beacon-column-into-a-reflector-counts-as-max-light
 * operator ruling 2026-11-28: the beacon's beam works as a reflector's beam does, at full power
 */
public class LightBeaconBlockEntity extends BlockEntity {

    /** Ticks between column refreshes, a reflector's. */
    static final int REFRESH_TICKS = 20;
    private static final String TAG_RAILED = "Railed";

    private List<BlockPos> railed = List.of();
    private boolean refreshed;

    /**
     * Creates the light beacon's block entity.
     *
     * @param pos   its position
     * @param state its state
     */
    public LightBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.LIGHT_BEACON.get(), pos, state);
    }

    /**
     * Lays the column's rail on the beacon's first tick and every second after.
     *
     * @param level  the level
     * @param pos    the beacon's position
     * @param state  its state
     * @param beacon its block entity
     */
    static void serverTick(Level level, BlockPos pos, BlockState state, LightBeaconBlockEntity beacon) {
        if (level instanceof ServerLevel server && (!beacon.refreshed || level.getGameTime() % REFRESH_TICKS == 0)) {
            beacon.refreshed = true;
            beacon.layColumn(server, pos, state);
        }
    }

    private void layColumn(ServerLevel level, BlockPos pos, BlockState state) {
        List<BlockPos> column = BeaconRails.lay(level, pos, state.getValue(FaceMountedBlock.FACING), railed);
        if (!railed.equals(column)) {
            railed = List.copyOf(column);
            setChanged();
        }
    }

    /**
     * Takes back the column's rail as the beacon goes.
     */
    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
        if (level instanceof ServerLevel server) {
            BeaconRails.takeBack(server, railed);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        railed = input.read(TAG_RAILED, BlockPos.CODEC.listOf()).orElse(List.of());
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_RAILED, BlockPos.CODEC.listOf(), railed);
    }
}
