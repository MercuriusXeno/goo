package com.mercuriusxeno.goo.data;

/**
 * An expression named an item that holds no value, so the entry holding it cannot be valued: reading the item as
 * zero would value "36 lapis_lazuli + $mundane_block" as $mundane_block alone. The loader skips the entry.
 */
final class UnresolvedItemReference extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String item;

    /**
     * @param item the item named, as the expression spelled it
     */
    UnresolvedItemReference(String item) {
        super(item, null, false, false);
        this.item = item;
    }

    /**
     * @return the item named, as the expression spelled it
     */
    String item() {
        return item;
    }
}
