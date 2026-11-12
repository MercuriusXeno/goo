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
     * Shows the hold charging toward the way in.
     *
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     */
    void showCharge(int held, int charge);

    /**
     * Stands the door the hold has conjured so far before the player, for
     * them and every player tracking them to see.
     * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
     *
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     */
    void conjureDoor(int held, int charge);

    /**
     * Carries the player into their room, keeping where they stood for its
     * door, when they can pay the way in.
     *
     * @param cost the void the way in costs (mB)
     */
    void enterPocket(int cost);

    /**
     * Feeds void the player carries into their room, when they carry it:
     * the cost of the ring its extent grows through spread over the ring time.
     *
     * @param ringTicks   the ticks every ring takes
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance from the center adds (mB)
     */
    void feedPlatform(int ringTicks, int base, int perDistance);
}
