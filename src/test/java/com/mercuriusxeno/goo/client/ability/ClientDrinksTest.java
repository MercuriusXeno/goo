package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client keeps every block the server shows streaming until its stream
 * has wholly entered the glove, whether or not the server is still talking
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class ClientDrinksTest {

    private static final int PLAYER = 7;
    private static final long START = 100;
    private static final long END = 134;

    private static DrinkPayload drink(BlockPos... blocks) {
        List<DrinkPayload.Streaming> streaming = Arrays.stream(blocks)
                .map(pos -> new DrinkPayload.Streaming(pos, START, END)).toList();
        return new DrinkPayload(PLAYER, streaming);
    }

    @Test
    void aShownBlockIsDrawnUntilItsStreamHasEnteredTheGlove() {
        ClientDrinks drinks = new ClientDrinks();
        drinks.show(drink(BlockPos.ZERO));

        assertEquals(1, drinks.live(END + DrinkStream.TRAVEL_TICKS - 1).size());
        assertTrue(drinks.live(END + DrinkStream.TRAVEL_TICKS).isEmpty());
    }

    @Test
    void laterWordsAddBlocksWithoutRepeatingOnes() {
        ClientDrinks drinks = new ClientDrinks();
        drinks.show(drink(BlockPos.ZERO));
        drinks.show(drink(BlockPos.ZERO, BlockPos.ZERO.above()));

        ClientDrinks.Drink drink = drinks.live(START).getFirst();

        assertEquals(PLAYER, drink.playerId());
        assertEquals(2, drink.streaming().size());
    }

}
