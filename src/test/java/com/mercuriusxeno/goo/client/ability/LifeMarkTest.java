package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers what a mob's life draws over its head: one heart filled by its life
 * over its max, and its life counted in hearts as a mixed number; on a
 * downed mob, the skull in the heart's slot and its negative health counted
 * in hearts below zero.
 * health-bar-is-one-heart-filling-left-to-right
 * downed-mob-shows-a-skull-and-its-negative-health
 */
class LifeMarkTest {

    private static final float EPSILON = 1e-6f;

    @Nested
    class FillShare {

        @Test
        void fullLifeFillsTheWholeHeart() {
            assertEquals(1f, LifeMark.of(20f, 20f).fill(), EPSILON);
        }

        @Test
        void halfLifeFillsHalfTheHeart() {
            assertEquals(0.5f, LifeMark.of(10f, 20f).fill(), EPSILON);
        }

        @Test
        void slimLifeFillsItsShare() {
            assertEquals(0.02f, LifeMark.of(0.4f, 20f).fill(), EPSILON);
        }

        @Test
        void smallMaxDrawsTheSameOneHeartMark() {
            LifeMark mark = LifeMark.of(7f, 10f);
            assertEquals(0.7f, mark.fill(), EPSILON);
            assertEquals("3¹⁄₂", mark.count());
        }
    }

    @Nested
    class CountInHearts {

        @Test
        void donkeyReadsTwelveAndAHalf() {
            assertEquals("12¹⁄₂", LifeMark.of(25f, 25f).count());
        }

        @Test
        void wholeHeartsReadWhole() {
            assertEquals("10", LifeMark.of(20f, 20f).count());
        }

        @Test
        void oddHealthReadsAHalf() {
            assertEquals("6¹⁄₂", LifeMark.of(13f, 20f).count());
        }

        @Test
        void slimLifeRoundsUpToHalfAHeart() {
            assertEquals("¹⁄₂", LifeMark.of(0.4f, 20f).count());
        }
    }

    @Nested
    class DownedMob {

        /** A heart sprite's side, in pixels. */
        private static final int HEART_SIDE = 9;

        @Test
        void downedMarkNamesTheSkull() {
            assertEquals(LifeMark.SKULL, LifeMark.downed(-3f).sprite());
        }

        @Test
        void standingMarkNamesTheHeart() {
            assertEquals(LifeMark.HEART, LifeMark.of(13f, 20f).sprite());
        }

        @Test
        void threeHalfHeartsBelowZeroReadMinusOneAndAHalf() {
            assertEquals("-1¹⁄₂", LifeMark.downed(-3f).count());
        }

        @Test
        void twoHalfHeartsBelowZeroReadMinusOne() {
            assertEquals("-1", LifeMark.downed(-2f).count());
        }

        @Test
        void skullShipsAtTheHeartsSide() throws IOException {
            Identifier skull = LifeMark.downed(-3f).sprite();
            assertEquals(Goo.MODID, skull.getNamespace());
            String path = "/assets/" + skull.getNamespace() + "/" + skull.getPath();
            try (InputStream in = LifeMarkTest.class.getResourceAsStream(path)) {
                assertNotNull(in, path);
                BufferedImage image = ImageIO.read(in);
                assertEquals(HEART_SIDE, image.getWidth());
                assertEquals(HEART_SIDE, image.getHeight());
            }
        }
    }
}
