package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The post-fuse work of a chain marker block entity. The marker owns the
 * shared state (goo type, stack count, fuse countdown, placed face) and hands
 * the rest to its ability's program, {@code ProgramBehavior}, the one
 * implementation (decision delete-dead-fold-mirrors).
 *
 * <p>Lifecycle on the server:
 * <ol>
 *   <li>The marker ticks its fuse down in {@code tickFuse}.</li>
 *   <li>When the fuse hits zero, the marker loads its ability's program and
 *       calls {@link #onFuseExpired}, the program's first tick.</li>
 *   <li>While {@link #isActive()} is true, the marker calls
 *       {@link #serverTick} once per server tick.</li>
 *   <li>The first tick {@link #isActive()} returns false, the marker removes
 *       itself from the world.</li>
 * </ol>
 *
 * <p>Persistence is owned by the behavior: the marker calls
 * {@link #saveAdditional} and {@link #loadAdditional} during its own
 * save/load, and the behavior writes only its own tag keys. On load, the
 * marker reloads the program before delegating {@link #loadAdditional} to it.
 */
public interface ChainBehavior {

    /**
     * Called the tick the fuse hits zero: the program's first tick.
     *
     * @param level the server level
     * @param pos   the chain marker position
     * @param be    the owning block entity (for reading shared fields)
     */
    void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be);

    /**
     * Called once per server tick while {@link #isActive()} is true.
     *
     * @param level the server level
     * @param pos   the chain marker position
     * @param be    the owning block entity
     */
    void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be);

    /**
     * Returns true while the behavior still has work to do. The BE
     * removes itself the tick this first returns false.
     *
     * @return true if the behavior should keep ticking
     */
    boolean isActive();

    /**
     * Returns true if the marker accepts more blobs after its fuse expires.
     *
     * @return true if post-fuse stacking is allowed
     */
    default boolean allowsTopOff() {
        return false;
    }

    /**
     * Persists this behavior's state onto the BE's shared value stream.
     * Implementations should only write their own tag keys; the BE writes
     * the shared-field tags separately.
     *
     * @param output the value output to write to
     */
    void saveAdditional(ValueOutput output);

    /**
     * Restores this behavior's state from the BE's shared value stream.
     * Called by the BE after it reloads the program during
     * {@code loadAdditional}.
     *
     * @param input the value input to read from
     */
    void loadAdditional(ValueInput input);
}
