package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests that goo potions register over exactly the bundled type keys, so a
 * datapack type gets no potion and every bundled type keeps its own under its
 * former name. What each potion carries needs the registries, so
 * BrewEffectTests proves it in a gametest.
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
     * Each potion registers under its type id and the goo suffix, the paths
     * brewing yielded before the keys changed.
     */
    @Test
    void potionPathsKeepTheirNames() {
        assertEquals("blaze_goo", GooPotions.potionName(GooTypes.BLAZE));
        assertEquals("unstable_goo", GooPotions.potionName(GooTypes.UNSTABLE));
    }
}
