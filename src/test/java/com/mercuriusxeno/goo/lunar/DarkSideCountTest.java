package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * goo:dark_side_count, decoded from a feature's light_side and dark_side counts, places its dark-side count of
 * attempts at a position past x 3,750,000 and its light-side count at x 0, so void and xeno blocks concentrate on
 * the dark side.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 */
class DarkSideCountTest {

    private static final int LIGHT_SIDE_COUNT = 1;
    private static final int DARK_SIDE_COUNT = 4;
    private static final String COUNTS = "{\"light_side\": " + LIGHT_SIDE_COUNT + ", \"dark_side\": "
            + DARK_SIDE_COUNT + "}";

    @Nested
    class TheCount {

        private final DarkSideCount count = DarkSideCount.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(COUNTS)).getOrThrow();
        private final RandomSource random = mock(RandomSource.class);

        @Test
        void aPositionPastTheLightSidePlacesTheDarkSidesCount() {
            assertEquals(DARK_SIDE_COUNT, count.count(random, new BlockPos(3_750_001, 0, 0)));
        }

        @Test
        void aPositionAtXZeroPlacesTheLightSidesCount() {
            assertEquals(LIGHT_SIDE_COUNT, count.count(random, BlockPos.ZERO));
        }
    }
}
