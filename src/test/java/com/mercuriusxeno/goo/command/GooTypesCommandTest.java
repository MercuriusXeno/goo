package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the /goo types listing seam: ids sort by full id across namespaces
 * and the message carries the count and every id.
 */
class GooTypesCommandTest {

    private static final ResourceKey<GooTypeDefinition> ADDON_TYPE = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));

    /**
     * Ids from any namespace sort ascending by their full string.
     */
    @Test
    void sortedIdsOrdersAcrossNamespaces() {
        List<String> ids = GooTypesCommand.sortedIds(Stream.of(GooTypes.ROCK, ADDON_TYPE, GooTypes.AEON));
        assertEquals(List.of("goo:aeon", "goo:rock", "gootest:seventeenth"), ids);
    }

    /**
     * The listing names the count and joins the ids.
     */
    @Test
    void formatListingCarriesCountAndIds() {
        String line = GooTypesCommand.formatListing(List.of("goo:aeon", "gootest:seventeenth"));
        assertEquals("Goo types (2): goo:aeon, gootest:seventeenth", line);
    }
}
