package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.KnownItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The overlay's items sort by display name under the client language's collator, and a query keeps
 * the names containing it, case ignored
 * (decision menu-lists-known-items-alphabetically-with-search-and-scroll).
 */
class KnownItemsListingTest {

    private static final Locale SWEDISH = Locale.forLanguageTag("sv-SE");

    /** A known item whose id sorts opposite to its name, so an order by id shows. */
    private static KnownItem named(String name, String idPath) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getHoverName()).thenReturn(Component.literal(name));
        return new KnownItem(Identifier.withDefaultNamespace(idPath), stack);
    }

    private static List<String> names(List<KnownItem> items) {
        return items.stream().map(KnownItemsListing::displayName).toList();
    }

    @Nested
    class Order {
        @Test
        void englishCollatorSortsByNameRegardlessOfCase() {
            List<KnownItem> items = List.of(named("cherry", "a"), named("Banana", "b"), named("apple", "c"));
            assertEquals(List.of("apple", "Banana", "cherry"), names(KnownItemsListing.order(items, Locale.ENGLISH)));
        }

        @Test
        void swedishCollatorSortsOAfterZ() {
            List<KnownItem> items = List.of(named("Öl", "a"), named("Zink", "b"));
            assertEquals(List.of("Zink", "Öl"), names(KnownItemsListing.order(items, SWEDISH)));
        }

        @Test
        void languageCodeNamesItsLocale() {
            assertEquals(SWEDISH, KnownItemsListing.localeOf("sv_se"));
        }
    }

    @Nested
    class Filter {
        private final List<KnownItem> items = List.of(named("Dirt", "a"), named("Sandstone", "b"), named("Stone", "c"));

        @Test
        void queryKeepsTheNamesContainingItIgnoringCase() {
            assertEquals(List.of("Sandstone", "Stone"),
                    names(KnownItemsListing.filter(items, "STON", Locale.ENGLISH)));
        }

        @Test
        void emptyQueryKeepsEveryItem() {
            assertEquals(List.of("Dirt", "Sandstone", "Stone"), names(KnownItemsListing.filter(items, "", Locale.ENGLISH)));
        }
    }
}
