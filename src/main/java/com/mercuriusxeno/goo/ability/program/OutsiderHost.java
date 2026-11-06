package com.mercuriusxeno.goo.ability.program;

/**
 * A host over a channeling player who can be carried into their Outsider room.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public interface OutsiderHost extends ChannelHost {

    /**
     * Whether the player stands in the pocket dimension already.
     *
     * @return true inside the pocket
     */
    boolean standsInPocket();

    /**
     * Carries the player into their room, keeping where they stood for its door.
     */
    void enterPocket();
}
