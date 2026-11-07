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
     * Carries the player into their room, keeping where they stood for its
     * door, when they can pay the way in.
     *
     * @param cost the void the way in costs (mB)
     */
    void enterPocket(int cost);

    /**
     * Feeds void the player carries into their room's platform, when they carry it.
     *
     * @param feed        the void fed (mB)
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance from the center adds (mB)
     */
    void feedPlatform(int feed, int base, int perDistance);
}
