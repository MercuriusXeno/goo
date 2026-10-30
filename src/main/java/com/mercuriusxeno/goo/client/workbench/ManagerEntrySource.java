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
     * @param members how many items the tag holds
     */
    record TagSize(String id, int members) {
    }

    /**
     * @return every item, in sorted key order
     */
    List<ItemName> items();

    /**
     * @return every item tag, sorted by id
     */
    List<TagSize> itemTags();
}
