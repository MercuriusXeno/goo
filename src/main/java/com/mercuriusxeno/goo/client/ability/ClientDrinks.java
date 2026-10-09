package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blocks streaming into Unmake drinks this client draws, each kept from
 * the server's first word of it until its stream has wholly entered the
 * glove; and the block each one was, remembered from the first frame the
 * client saw it melting, so the stream's tail is drawn after the block is gone.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class ClientDrinks {

    /** The store the drink handler and the drink renderer share. */
    public static final ClientDrinks CLIENT = new ClientDrinks();

    /**
     * One player's drink: the blocks streaming into their glove.
     *
     * @param playerId  the player's entity id
     * @param streaming the blocks still streaming or whose stream is still in the air
     */
    public record Drink(int playerId, List<DrinkPayload.Streaming> streaming) {
    }

    private final Map<Integer, Map<BlockPos, DrinkPayload.Streaming>> drinks = new HashMap<>();
    private final Remembered<BlockPos, BlockState> blocks = new Remembered<>();

    /**
     * Keeps every block the server shows streaming.
     *
     * @param payload the drink this tick
     */
    public void show(DrinkPayload payload) {
        Map<BlockPos, DrinkPayload.Streaming> shown = drinks.computeIfAbsent(payload.playerId(),
                ignored -> new HashMap<>());
        for (DrinkPayload.Streaming streaming : payload.streaming()) {
            shown.put(streaming.pos(), streaming);
        }
    }

    /**
     * The block a streaming block was, remembering the one seen this frame.
     *
     * @param pos  the block
     * @param seen the block it stands in for as the client sees it this frame, null once it is gone
     * @return the block it was, or null while the client has never seen it
     */
    public @Nullable BlockState blockOf(BlockPos pos, @Nullable BlockState seen) {
        return blocks.of(pos.immutable(), seen);
    }

    /**
     * The drinks to draw, forgetting each block whose stream has wholly entered the glove.
     *
     * @param now the game time, with the partial tick
     * @return the drinks with a block left to draw
     */
    public List<Drink> live(double now) {
        List<Drink> live = new ArrayList<>();
        Set<BlockPos> drawn = new HashSet<>();
        drinks.values().forEach(shown -> shown.values().removeIf(streaming -> DrinkStream.gone(streaming, now)));
        drinks.values().removeIf(Map::isEmpty);
        drinks.forEach((playerId, shown) -> {
            live.add(new Drink(playerId, List.copyOf(shown.values())));
            drawn.addAll(shown.keySet());
        });
        blocks.keepOnly(drawn);
        return live;
    }
}
