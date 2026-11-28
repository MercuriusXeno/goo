package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.MortarPaving;
import com.mercuriusxeno.goo.network.MortarCarryPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The Mortar each Level hold carries, as the client draws it: the last
 * carry the server sent for each holder, each pile holding layers drawn as
 * Mortar of its count in its cell over the disc, where no block stands. A
 * carry of empty piles clears the holder's drawing, and one the server stops
 * sending fades from the store, so a holder gone from view leaves nothing drawn.
 * carried-mortar-stops-being-a-block-on-the-level-plane
 */
public final class CarriedMortar {

    /** The client's store. */
    public static final CarriedMortar CLIENT = new CarriedMortar();

    /** The game ticks a carry stays drawn after the last the server sent, past a held tick's jitter. */
    private static final long DRAWN_FOR_TICKS = 5;

    private final Map<Integer, Sent> carries = new HashMap<>();

    /**
     * One holder's carry as the server last sent it.
     *
     * @param payload the carry
     * @param sentOn  the game tick it arrived on
     */
    private record Sent(MortarCarryPayload payload, long sentOn) {
    }

    /**
     * Takes a carry the server sent, in place of the holder's last.
     *
     * @param payload  the carry
     * @param gameTime the game tick it arrived on
     */
    public void accept(MortarCarryPayload payload, long gameTime) {
        if (payload.piles().stream().allMatch(layers -> layers == 0)) {
            carries.remove(payload.holderId());
        } else {
            carries.put(payload.holderId(), new Sent(payload, gameTime));
        }
    }

    /**
     * What the carries draw now: each pile holding layers, its Mortar state at its cell.
     *
     * @param mortarOfLayers the Mortar state of a layer count
     * @param gameTime       the game tick now
     * @return each cell drawn with the state drawn there
     */
    public Map<BlockPos, BlockState> drawn(IntFunction<BlockState> mortarOfLayers, long gameTime) {
        carries.values().removeIf(sent -> gameTime - sent.sentOn() > DRAWN_FOR_TICKS);
        Map<BlockPos, BlockState> drawn = new LinkedHashMap<>();
        for (Sent sent : carries.values()) {
            List<BlockPos> cells = MortarPaving.pileCells(sent.payload().plane(), sent.payload().middle());
            List<Integer> piles = sent.payload().piles();
            for (int pile = 0; pile < Math.min(cells.size(), piles.size()); pile++) {
                if (piles.get(pile) > 0) {
                    drawn.put(cells.get(pile), mortarOfLayers.apply(piles.get(pile)));
                }
            }
        }
        return drawn;
    }
}
