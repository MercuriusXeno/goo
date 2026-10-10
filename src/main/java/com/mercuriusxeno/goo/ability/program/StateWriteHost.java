package com.mercuriusxeno.goo.ability.program;

import java.util.Map;

/**
 * A host whose own block can take new property values in place (capability
 * {@link HostCapability#STATE_WRITE}), the block and its block entity kept.
 */
public interface StateWriteHost extends StepHost {

    /**
     * Sets properties on the block standing at the anchor, keeping the block.
     *
     * @param state each state property to set, by its name, to the value's name
     */
    void writeOwnState(Map<String, String> state);
}
