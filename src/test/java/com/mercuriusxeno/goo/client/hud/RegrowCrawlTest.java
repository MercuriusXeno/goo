package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The regrow crawl picks the half the overlay regrows next and follows its interval to the regrow time, stone crawling into a missing heart the same way (decisions ash-heart-smolders-while-reigniting, wood-crawls-across-regrowing-heart, heart-effects-crawl-while-held). */
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
    void barkCrawlsOverTheHalfBarkskinRegrowsNextAndCoversItAtTheRegrowTime() {
        HeartOverlay barked = new HeartOverlay(HeartKind.BARKSKIN, Collections.nCopies(10, 2), EXPIRES, NOW, NOW)
                .drain(3f, NOW).overlay();
        long regrowAt = barked.regrowAt();
        RegrowCrawl.Crawl start = RegrowCrawl.crawl(barked, FULL_HEALTH, NOW).orElseThrow();
        assertEquals(8, start.slot());
        assertEquals(1, start.fromHalf());
        assertEquals(0f, start.progress(), DELTA);
        assertEquals(1f, RegrowCrawl.crawl(barked, FULL_HEALTH, regrowAt).orElseThrow().progress(), DELTA);
        assertEquals(2, barked.tick(FULL_HEALTH, false, regrowAt).shieldAt(start.slot()));
    }

    @Test
    void noBarkStandingMeansNoCrawl() {
        HeartOverlay gone = new HeartOverlay(HeartKind.BARKSKIN, List.of(2, 2), EXPIRES, NOW, NOW).drain(4f, NOW).overlay();
        assertTrue(RegrowCrawl.crawl(gone, 4f, NOW).isEmpty());
    }

    @Test
    void stoneCrawlsIntoTheNextMissingHeart() {
        long interval = 50L;
        HeartOverlay stone = new HeartOverlay(HeartKind.STONESKIN, List.of(0, 0, 0, 2, 0, 0, 0, 0, 0, 0), EXPIRES,
                NOW + interval, NOW, 0.5f, 0f);
        RegrowCrawl.Crawl crawl = RegrowCrawl.crawl(stone, 6f, NOW + interval / 2f).orElseThrow();
        assertEquals(4, crawl.slot());
        assertEquals(0, crawl.fromHalf());
        assertTrue(crawl.progress() > 0f && crawl.progress() < 1f);
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
