package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The effective value cache carries fractional values whole through its JSON form,
 * tested at the JSON seam so no file is touched
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class GooValueCacheTest {

    @Test
    void aFractionalValueRoundTripsThroughTheCacheJson() {
        Identifier carpet = Identifier.withDefaultNamespace("white_carpet");
        GooValue written = fraction(GooTypes.LEAF, 64, 3, GooTypes.JELLY, 2, 1);
        Map<Identifier, GooValue> readBack = new HashMap<>();

        GooValueCache.deserializeEffectiveValues(GooValueCache.serializeValues(Map.of(carpet, written)), readBack);

        assertEquals(written, readBack.get(carpet));
    }
}
