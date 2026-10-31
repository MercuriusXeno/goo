package com.mercuriusxeno.goo.client.workbench;

import java.util.List;

/**
 * The items and item tags the values manager lists, read on the client rather than posted by the server
 * (decision client-builds-items-and-tags-from-its-own-registries).
 */
public interface ManagerEntrySource {

    /**
     * One item the manager lists.
     *
     * @param id   the item's namespaced id
     * @param name the item's default-instance display name
     */
    record ItemName(String id, String name) {
    }

    /**
     * One item tag the manager lists.
     *
     * @param id      the tag's namespaced id, without #
     * @param members the namespaced id of each item the tag holds, which an Items row the tag values reads as its
     *                group (decision items-row-valued-by-a-tag-reads-the-tag-as-its-group)
     */
    record ItemTag(String id, List<String> members) {
    }

    /**
     * @return every item, in sorted key order
     */
    List<ItemName> items();

    /**
     * @return every item tag, sorted by id
     */
    List<ItemTag> itemTags();
}
