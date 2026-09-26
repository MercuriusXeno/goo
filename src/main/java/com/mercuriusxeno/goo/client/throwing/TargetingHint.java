package com.mercuriusxeno.goo.client.throwing;

/**
 * Targeting mode derived from the selected ability's tags.
 */
public enum TargetingHint {
    /**
     * No ability selected - suppress all targeting and throws.
     */
    NONE,
    /**
     * Entity-tagged ability - aim-assist entities only, no block fallback.
     */
    ENTITY,
    /**
     * Block-tagged ability - block targeting only, no entity aim-assist.
     */
    BLOCK
}
