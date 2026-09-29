package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The four chrysm tiers hold their fixed volumes, each about 32 times the one below
 * (decision chrysm-tiers-in-32x-steps), and melt back into exactly that
 * volume of their type and the tenth in crystal spent on them (decision
 * chrysm-melts-back-to-its-goo).
 */
class ChrysmTierTest {

    /** The ratio of one tier to the one below: 32, rounded to round numbers. */
    private static final double TIER_RATIO = 32;
    private static final double RATIO_TOLERANCE = 1.5;

    @Test
    void eachTierHoldsItsFixedVolume() {
        assertEquals(32_000L, ChrysmTier.CHRYSM.volume());
        assertEquals(1_000_000L, ChrysmTier.BUDDING_CHRYSM.volume());
        assertEquals(32_000_000L, ChrysmTier.FLOWERING_CHRYSM.volume());
        assertEquals(1_000_000_000L, ChrysmTier.MATERIA.volume());
    }

    @Test
    void fourTiersStepAbout32xEach() {
        ChrysmTier[] tiers = ChrysmTier.values();
        assertEquals(4, tiers.length);
        for (int i = 1; i < tiers.length; i++) {
            assertEquals(TIER_RATIO, (double) tiers[i].volume() / tiers[i - 1].volume(), RATIO_TOLERANCE,
                    tiers[i].name());
        }
    }

    @Test
    void eachTierIsWorthItsVolumeOfItsTypeAndATenthInCrystal() {
        for (ChrysmTier tier : ChrysmTier.values()) {
            GooContents melted = tier.contentsOf(GooTypes.ENDER);
            assertEquals(Map.of(GooTypes.ENDER, (int) tier.volume(), GooTypes.CRYSTAL, (int) (tier.volume() / 10)),
                    melted.getAll(), tier.name());
        }
    }

    @Test
    void crystalChrysmIsWorthItsVolumeAndItsCrystalInOneEntry() {
        assertEquals(Map.of(GooTypes.CRYSTAL, 35_200), ChrysmTier.CHRYSM.contentsOf(GooTypes.CRYSTAL).getAll());
    }

    @Test
    void eachTierNamesItsLangKey() {
        assertEquals("item.goo.chrysm", ChrysmTier.CHRYSM.translationKey());
        assertEquals("item.goo.budding_chrysm", ChrysmTier.BUDDING_CHRYSM.translationKey());
        assertEquals("item.goo.flowering_chrysm", ChrysmTier.FLOWERING_CHRYSM.translationKey());
        assertEquals("item.goo.materia", ChrysmTier.MATERIA.translationKey());
    }
}
