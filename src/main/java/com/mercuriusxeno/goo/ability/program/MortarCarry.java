package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.MortarCarryPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * The Mortar a Level hold carries over its disc: nine piles of layers, one
 * a column of the 3x3 over the disc, standing as no block while the hold
 * lasts. Each tick it rides, the players watching it are sent the piles to
 * draw; when the hold ends it is set down as Mortar where it last rode,
 * through the host it last rode with, as the hold's gradual break clears its
 * cracks.
 * carried-mortar-stops-being-a-block-on-the-level-plane
 */
public final class MortarCarry {

    /** The entity id a carry names when no entity holds it. */
    private static final int NO_HOLDER = -1;

    private List<Integer> piles = Collections.nCopies(MortarPaving.PILES, 0);
    private @Nullable ChannelHost lastHost;
    private ChannelAim.@Nullable FacePlane plane;
    private @Nullable BlockPos middle;
    /** Whether the watching clients were last sent piles to draw, which an emptied carry clears. */
    private boolean drawn;

    /**
     * @return the nine piles' layers, in pile order
     */
    public List<Integer> piles() {
        return piles;
    }

    /**
     * @return the layers the carry holds
     */
    public int layers() {
        return piles.stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Lets the carry ride over the disc where the cursor rests this tick, and
     * sends the watching players its piles to draw, or once its drawing to
     * clear where it has emptied.
     *
     * @param host      the channel host this tick runs through
     * @param facePlane the face the hold began on
     * @param disc      the block the cursor rests on, the disc's middle
     * @param riding    the nine piles' layers
     */
    void ride(ChannelHost host, ChannelAim.FacePlane facePlane, BlockPos disc, List<Integer> riding) {
        lastHost = host;
        plane = facePlane;
        middle = disc.immutable();
        piles = List.copyOf(riding);
        sendDrawing(host, facePlane, middle);
    }

    /**
     * Sets the carry down as Mortar where it last rode, then empties it and
     * clears its drawing; a carry that never rode sets nothing down.
     */
    public void release() {
        ChannelHost host = lastHost;
        ChannelAim.FacePlane facePlane = plane;
        BlockPos disc = middle;
        if (host == null || facePlane == null || disc == null) {
            return;
        }
        if (layers() > 0) {
            MortarPaving.setDown(host, facePlane, disc, piles);
        }
        piles = Collections.nCopies(MortarPaving.PILES, 0);
        sendDrawing(host, facePlane, disc);
        lastHost = null;
    }

    private void sendDrawing(ChannelHost host, ChannelAim.FacePlane facePlane, BlockPos disc) {
        boolean holds = layers() > 0;
        if (holds || drawn) {
            Entity holder = host.breaker();
            BlockVisuals.sendToWatchers(host.level(), disc, new MortarCarryPayload(
                    holder == null ? NO_HOLDER : holder.getId(), facePlane, disc, piles));
        }
        drawn = holds;
    }
}
