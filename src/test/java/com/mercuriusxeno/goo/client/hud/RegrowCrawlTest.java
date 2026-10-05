package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The regrow crawl picks the half the overlay regrows next and follows its interval to the regrow time (decisions ash-heart-smolders-while-reigniting, wood-crawls-across-regrowing-heart). */
class RegrowCrawlTest {

    private static final long NOW = 1_000L;
    private static final long EXPIRES = NOW + 1_200L;
    private static final float FULL_HEALTH = 20f;
    private static final float DELTA = 1e-5f;

    private static HeartOverlay kindle(List<Integer> shields, long regrowAt) {
        return new HeartOverlay(HeartKind.KINDLE, shields, EXPIRES, regrowAt, NOW);
    }

    @Test
    void theCrawlStandsOnTheSlotTheOverlayRegrowsNext() {
        HeartOverlay overlay = kindle(List.of(2, 2, 1, 0, 0), NOW + 10);
        RegrowCrawl.Crawl crawl = RegrowCrawl.crawl(overlay, 10f, NOW).orElseThrow();
        assertEquals(2, crawl.slot());
        assertEquals(1, crawl.fromHalf());
        HeartOverlay regrown = overlay.tick(10f, false, NOW + 10);
        assertEquals(2, regrown.shieldAt(crawl.slot()));
    }

    @Test
    void progressRunsFromZeroAtTheIntervalsStartToOneAtTheRegrowTime() {
        assertEquals(0f, RegrowCrawl.progress(NOW, NOW + 40, 40), DELTA);
        assertEquals(0.5f, RegrowCrawl.progress(NOW + 20, NOW + 40, 40), DELTA);
        assertEquals(1f, RegrowCrawl.progress(NOW + 40, NOW + 40, 40), DELTA);
        assertEquals(1f, RegrowCrawl.progress(NOW + 90, NOW + 40, 40), DELTA);
    }

    @Test
    void theCrawlReadsTheOverlaysOwnInterval() {
        HeartOverlay overlay = kindle(List.of(2, 0), 0L);
        long interval = overlay.regrowInterval();
        HeartOverlay timed = kindle(List.of(2, 0), NOW + interval);
        assertEquals(0f, RegrowCrawl.crawl(timed, 4f, NOW).orElseThrow().progress(), DELTA);
    }

    @Test
    void aFullBarAndNoOverlayCrawlNowhere() {
        assertTrue(RegrowCrawl.crawl(kindle(List.of(2, 2), NOW), 4f, NOW).isEmpty());
        assertTrue(RegrowCrawl.crawl(HeartOverlay.NONE, FULL_HEALTH, NOW).isEmpty());
    }

    @Test
    void aSmoothFrontReachesEvenlyAndARaggedOneStaysWithinAPixel() {
        assertEquals(0, RegrowCrawl.rowReach(4, 0f, 7, true));
        assertEquals(RegrowCrawl.HALF_WIDTH, RegrowCrawl.rowReach(4, 1f, 7, true));
        int even = Math.round(0.5f * RegrowCrawl.HALF_WIDTH);
        assertTrue(IntStream.range(0, 9).allMatch(row -> RegrowCrawl.rowReach(row, 0.5f, 11, false) == even));
        assertTrue(IntStream.range(0, 9).allMatch(row -> Math.abs(RegrowCrawl.rowReach(row, 0.5f, 11, true) - even) <= 1));
        assertTrue(IntStream.range(0, 9).anyMatch(row -> RegrowCrawl.rowReach(row, 0.5f, 11, true) != even)
                || IntStream.range(0, 9).anyMatch(row -> RegrowCrawl.rowReach(row, 0.5f, 14, true) != even));
    }
}
