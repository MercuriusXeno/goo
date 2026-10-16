package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block's color is the plain mean of its particle sprite's shown pixels,
 * nothing multiplied in, and its goo's color is its types' tints blended by
 * their volume shares (decision unmake-skin-is-a-solid-sampled-color).
 */
class DrinkColorsTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int CHANNEL = 0xFF;

    private static void assertChannelsWithinOne(int expected, int actual) {
        assertEquals(expected >>> 24, actual >>> 24, "alpha");
        for (int shift = 0; shift <= 16; shift += 8) {
            int want = (expected >>> shift) & CHANNEL;
            int got = (actual >>> shift) & CHANNEL;
            assertEquals(want, got, 1, "channel at shift " + shift + " of " + Integer.toHexString(actual));
        }
    }

    @Nested
    class BlockColor {

        @Test
        void redAndBlueAverageToPurple() {
            assertChannelsWithinOne(0xFF7F007F, DrinkColors.blockColorOf(new int[] {RED, BLUE}));
        }

        @Test
        void aPixelWithNoAlphaIsSkipped() {
            assertChannelsWithinOne(RED, DrinkColors.blockColorOf(new int[] {RED, 0x0000FF00}));
        }

        @Test
        void aPartlyShownPixelCountsAtItsOwnColorAndTheMeanIsOpaque() {
            assertChannelsWithinOne(0xFF7F007F, DrinkColors.blockColorOf(new int[] {0x80FF0000, BLUE}));
        }

        @Test
        void aSpriteWithNothingShownIsWhite() {
            assertEquals(DrinkColors.OPAQUE_WHITE, DrinkColors.blockColorOf(new int[] {0, 0x00123456}));
        }

        @Test
        void theSeamTakesThePixelsAloneAndNoTint() throws NoSuchMethodException {
            Method seam = DrinkColors.class.getMethod("blockColorOf", int[].class);

            assertArrayEquals(new Class<?>[] {int[].class}, seam.getParameterTypes());
        }
    }

    @Nested
    class GooColor {

        @Test
        void twoTypesBlendByTheirShares() {
            int blended = DrinkColors.gooColorOf(new int[] {0xFF000000 | 200 << 16 | 40 << 8 | 100,
                0xFF000000 | 40 << 16 | 200 << 8 | 20}, new float[] {0.75f, 0.25f});

            assertChannelsWithinOne(0xFF000000 | 160 << 16 | 80 << 8 | 80, blended);
        }

        @Test
        void oneTypeIsItsOwnTint() {
            assertChannelsWithinOne(BLUE, DrinkColors.gooColorOf(new int[] {BLUE}, new float[] {1f}));
        }

        @Test
        void noShareIsWhite() {
            assertEquals(DrinkColors.OPAQUE_WHITE, DrinkColors.gooColorOf(new int[0], new float[0]));
        }
    }

    /** decision diagnose-then-fix-unmake-metallic-color */
    @Nested
    class Skin {

        @Test
        void theSkinIsTheBlocksColorAtTheBlockTheGoosAtTheHandAndCrossfadesBetweenByTheRoute() {
            assertEquals(RED, DrinkColors.skinAt(RED, BLUE, 0));
            assertChannelsWithinOne(0xFF800080, DrinkColors.skinAt(RED, BLUE, 0.5));
            assertEquals(BLUE, DrinkColors.skinAt(RED, BLUE, 1));
            assertEquals(BLUE, DrinkColors.skinAt(RED, BLUE, 2), "never past the goo's color");
        }
    }
}
