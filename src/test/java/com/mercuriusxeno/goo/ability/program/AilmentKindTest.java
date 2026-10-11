package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ailment table carries every ailment the type lists name, each with its
 * own color and pattern (decision ailment-overlay-shader-per-ailment).
 */
class AilmentKindTest {

    private static final int RGB_MASK = 0xFFFFFF;

    @Test
    void tableCarriesEveryAilmentTheListsName() {
        Set<AilmentKind> named = EnumSet.of(AilmentKind.STASIS, AilmentKind.HASTE, AilmentKind.SCALES,
                AilmentKind.ZONE, AilmentKind.HEX, AilmentKind.PETRIFY, AilmentKind.FROZEN);

        assertTrue(EnumSet.allOf(AilmentKind.class).containsAll(named));
        for (AilmentKind kind : AilmentKind.values()) {
            assertNotEquals(0, kind.rgb(), kind + " has no color");
            assertEquals(kind.rgb(), kind.rgb() & RGB_MASK, kind + " color is not 0xRRGGBB");
        }
    }

    @Test
    void stasisAndHasteShareTheGoldenGlint() {
        assertEquals(AilmentPattern.GLINT, AilmentKind.STASIS.pattern());
        assertEquals(AilmentPattern.GLINT, AilmentKind.HASTE.pattern());
        assertTrue(isGolden(AilmentKind.STASIS.rgb()) && isGolden(AilmentKind.HASTE.rgb()));
    }

    @Test
    void scalesIsDiamondBlueWithAPatternOfItsOwn() {
        assertEquals(AilmentPattern.FACETS, AilmentKind.SCALES.pattern());
        int rgb = AilmentKind.SCALES.rgb();
        assertTrue((rgb & 0xFF) > (rgb >> 16), "scales is not blue over red");
    }

    @Test
    void eachRemainingAilmentWearsItsOwnLook() {
        assertEquals(AilmentPattern.SHIMMER, AilmentKind.ZONE.pattern());
        assertEquals(AilmentKind.ZONE.rgb(), AilmentKind.TELEPORTITIS.rgb());
        assertEquals(AilmentPattern.SHIMMER, AilmentKind.TELEPORTITIS.pattern());
        assertEquals(AilmentPattern.GLINT, AilmentKind.HEX.pattern());
        assertNotEquals(AilmentKind.STASIS.rgb(), AilmentKind.HEX.rgb());
        assertEquals(AilmentPattern.STONE, AilmentKind.PETRIFY.pattern());
        assertEquals(AilmentPattern.FROST, AilmentKind.FROZEN.pattern());
    }

    private static boolean isGolden(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        return red > green && green > blue;
    }
}
