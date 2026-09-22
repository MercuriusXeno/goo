package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that goo potions register over exactly the bundled type keys, each
 * with a themed factory, so a datapack type gets no potion and every bundled
 * type keeps its own under its former name.
 */
class GooPotionsTest {

    private static final ResourceKey<GooTypeDefinition> DATAPACK_TYPE = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));

    /**
     * The potion types are the bundled keys and nothing else, in bundled order.
     */
    @Test
    void potionTypesAreExactlyTheBundledKeys() {
        assertEquals(List.copyOf(GooTypes.BUNDLED), List.copyOf(GooPotions.POTION_TYPES));
        assertFalse(GooPotions.POTION_TYPES.contains(DATAPACK_TYPE));
    }

    /**
     * Every potion type has a factory, so registration cannot land on a
     * missing one, and the datapack type has none.
     */
    @Test
    void everyPotionTypeHasAFactory() {
        for (ResourceKey<GooTypeDefinition> key : GooPotions.POTION_TYPES) {
            assertTrue(GooPotions.hasPotionFactory(key), "No potion factory for " + key.identifier());
        }
        assertFalse(GooPotions.hasPotionFactory(DATAPACK_TYPE));
    }

    /**
     * Each potion registers under its type id and the goo suffix, the paths
     * brewing yielded before the keys changed.
     */
    @Test
    void potionPathsKeepTheirNames() {
        assertEquals("blaze_goo", GooPotions.potionName(GooTypes.BLAZE));
        assertEquals("unstable_goo", GooPotions.potionName(GooTypes.UNSTABLE));
    }
}
