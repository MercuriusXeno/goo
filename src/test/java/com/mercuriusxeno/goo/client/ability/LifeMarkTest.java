package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ability.LifeMark.HeartFill;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what a touched mob's life draws over its head: a row of hearts up to
 * five hearts of max life, one heart and "life / max" in hearts above that.
 */
class LifeMarkTest {

    @Nested
    class SmallMaxDrawsARow {

        @Test
        void missingLifeDrawsEmptyHearts() {
            assertEquals(new LifeMark.Row(List.of(HeartFill.FULL, HeartFill.FULL, HeartFill.FULL, HeartFill.HALF,
                    HeartFill.EMPTY)), LifeMark.of(7f, 10f));
        }

        @Test
        void oddMaxRoundsUpToAWholeHeart() {
            assertEquals(new LifeMark.Row(List.of(HeartFill.FULL, HeartFill.HALF)), LifeMark.of(3f, 3f));
        }

        @Test
        void slimLifeStillShowsHalfAHeart() {
            assertEquals(new LifeMark.Row(List.of(HeartFill.HALF, HeartFill.EMPTY)), LifeMark.of(0.4f, 4f));
        }
    }

    @Nested
    class LargeMaxClustersToACount {

        @Test
        void countReadsLifeOverMaxInHearts() {
            assertEquals(new LifeMark.Clustered("12 / 20"), LifeMark.of(24f, 40f));
        }

        @Test
        void halfHeartOfLifeReadsAsAHalf() {
            assertEquals(new LifeMark.Clustered("6.5 / 10"), LifeMark.of(13f, 20f));
        }

        @Test
        void maxJustPastFiveHeartsClusters() {
            assertEquals(new LifeMark.Clustered("5.5 / 5.5"), LifeMark.of(11f, 11f));
        }
    }
}
