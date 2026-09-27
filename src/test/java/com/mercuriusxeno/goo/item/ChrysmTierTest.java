package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chrysm tiers hold their fixed volumes, each 1,000 of the one below
 * (decision chrysm-tiers-fixed-and-stackable), and melt back into exactly that
 * volume of their type (decision chrysm-melts-back-to-its-goo).
 */
class ChrysmTierTest {

    private static final long TIER_RATIO = 1_000L;

    @Test
    void eachTierHoldsItsFixedVolume() {
        assertEquals(1_000L, ChrysmTier.CHRYSM.volume());
        assertEquals(1_000_000L, ChrysmTier.KILOCHRYSM.volume());
        assertEquals(1_000_000_000L, ChrysmTier.MEGACHRYSM.volume());
    }

    @Test
    void eachTierIsOneThousandOfTheOneBelow() {
        ChrysmTier[] tiers = ChrysmTier.values();
        assertEquals(3, tiers.length);
        for (int i = 1; i < tiers.length; i++) {
            assertEquals(tiers[i - 1].volume() * TIER_RATIO, tiers[i].volume(), tiers[i].name());
        }
    }

    @Test
    void eachTierMeltsIntoItsVolumeOfItsTypeAlone() {
        for (ChrysmTier tier : ChrysmTier.values()) {
            GooContents melted = tier.contentsOf(GooTypes.ENDER);
            assertEquals(Map.of(GooTypes.ENDER, (int) tier.volume()), melted.getAll(), tier.name());
        }
    }

    @Test
    void eachTierNamesItsLangKey() {
        assertEquals("item.goo.chrysm", ChrysmTier.CHRYSM.translationKey());
        assertEquals("item.goo.kilochrysm", ChrysmTier.KILOCHRYSM.translationKey());
        assertEquals("item.goo.megachrysm", ChrysmTier.MEGACHRYSM.translationKey());
    }
}
