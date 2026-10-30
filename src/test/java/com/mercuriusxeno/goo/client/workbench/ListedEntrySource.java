package com.mercuriusxeno.goo.client.workbench;

import java.util.List;

/**
 * A fake entry source answering the lists a test hands it.
 *
 * @param items    the items it lists
 * @param itemTags the item tags it lists
 */
record ListedEntrySource(List<ItemName> items, List<TagSize> itemTags) implements ManagerEntrySource {
}
