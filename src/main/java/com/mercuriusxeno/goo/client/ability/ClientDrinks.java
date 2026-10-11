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
import java.util.function.IntPredicate;

/**
 * The blocks streaming into Unmake drinks this client draws, each drink kept
 * from the server's first word of it until its every stream has wholly
 * entered the glove and its skin shows nothing more, with the fixed layout of its tree; and the block each
 * one was, remembered from the first frame the client saw it flowing, so the
 * stream's tail is drawn after the block is gone.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class ClientDrinks {

    /** The store the drink handler and the drink renderer share. */
    public static final ClientDrinks CLIENT = new ClientDrinks();

    /**
     * One player's drink: the blocks streaming into their glove, and the fixed layout of their tree.
     *
     * @param playerId  the player's entity id
     * @param streaming the blocks still streaming or whose stream is still in the air
     * @param layout    the layout of the drink's tree, laid as each block first appeared
     */
    public record Drink(int playerId, List<DrinkPayload.Streaming> streaming, DrinkLayout layout) {
    }

    private final Map<Integer, Map<BlockPos, DrinkPayload.Streaming>> drinks = new HashMap<>();
    private final Map<Integer, DrinkLayout> layouts = new HashMap<>();
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
     * The drinks to draw, forgetting each drink whose every stream has had
     * its travel and whose skin has nothing left to show; a drink's blocks
     * are kept together until then, since a stream still in the air runs
     * down the trunks of blocks drained before it, and the skin is what says
     * when the last of it is in the glove.
     *
     * @param now       the game time, with the partial tick
     * @param stillSeen whether a player's drink still has skin to show, by the player's entity id
     * @return the drinks with a block left to draw
     */
    public List<Drink> live(double now, IntPredicate stillSeen) {
        List<Drink> live = new ArrayList<>();
        Set<BlockPos> drawn = new HashSet<>();
        drinks.entrySet().removeIf(drink -> drink.getValue().values().stream()
                .allMatch(streaming -> DrinkStream.gone(streaming, now)) && !stillSeen.test(drink.getKey()));
        layouts.keySet().retainAll(drinks.keySet());
        drinks.forEach((playerId, shown) -> {
            live.add(new Drink(playerId, List.copyOf(shown.values()),
                    layouts.computeIfAbsent(playerId, ignored -> new DrinkLayout())));
            drawn.addAll(shown.keySet());
        });
        blocks.keepOnly(drawn);
        return live;
    }
}
