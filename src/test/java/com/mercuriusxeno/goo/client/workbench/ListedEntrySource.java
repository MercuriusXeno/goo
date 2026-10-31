package com.mercuriusxeno.goo.client.workbench;

import java.util.List;
import java.util.stream.IntStream;

/**
 * A fake entry source answering the lists a test hands it.
 *
 * @param items    the items it lists
 * @param itemTags the item tags it lists
 */
record ListedEntrySource(List<ItemName> items, List<ItemTag> itemTags) implements ManagerEntrySource {

    /**
     * @param id      the tag's namespaced id, without #
     * @param members how many placeholder members the tag holds, for a test reading only its count
     * @return the tag
     */
    static ItemTag sizedTag(String id, int members) {
        return new ItemTag(id, IntStream.range(0, members).mapToObj(index -> id + "_member_" + index).toList());
    }
}
