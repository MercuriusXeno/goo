package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client keeps every drink the server shows, all its blocks together,
 * until the longest route's travel has passed its last block's drain, whether
 * or not the server is still talking (decision unmake-waves-dissolve-by-crucible-cost).
 */
class ClientDrinksTest {

    private static final int PLAYER = 7;
    private static final long START = 100;
    private static final long END = 134;
    private static final long LATER_END = 184;

    private static DrinkPayload drink(DrinkPayload.Streaming... streaming) {
        return new DrinkPayload(PLAYER, List.of(streaming));
    }

    private static DrinkPayload.Streaming streaming(BlockPos pos, long end) {
        return new DrinkPayload.Streaming(pos, START, end);
    }

    @Test
    void aDrinkIsDrawnWholeUntilItsLastBlocksTravelHasPassed() {
        ClientDrinks drinks = new ClientDrinks();
        drinks.show(drink(streaming(BlockPos.ZERO, END), streaming(BlockPos.ZERO.above(), LATER_END)));

        List<ClientDrinks.Drink> early = drinks.live(END + DrinkStream.LONGEST_TRAVEL_TICKS);
        assertEquals(1, early.size());
        assertEquals(2, early.getFirst().streaming().size(), "the drained block's trunk stays for the other's stream");
        assertEquals(1, drinks.live(LATER_END + DrinkStream.LONGEST_TRAVEL_TICKS - 1).size());
        assertTrue(drinks.live(LATER_END + DrinkStream.LONGEST_TRAVEL_TICKS).isEmpty());
    }

    @Test
    void laterWordsAddBlocksWithoutRepeatingOnes() {
        ClientDrinks drinks = new ClientDrinks();
        drinks.show(drink(streaming(BlockPos.ZERO, END)));
        drinks.show(drink(streaming(BlockPos.ZERO, END), streaming(BlockPos.ZERO.above(), END)));

        ClientDrinks.Drink drink = drinks.live(START).getFirst();

        assertEquals(PLAYER, drink.playerId());
        assertEquals(2, drink.streaming().size());
    }

}
