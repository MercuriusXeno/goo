package com.mercuriusxeno.goo.lab;

/**
 * The machine blocks {@code GooBlocks} registers, each owning one lab plot.
 * The thread that lands a new machine adds its constant here and its bay in
 * {@link LabLayout} (decision lab-iterates-the-registries).
 */
public enum LabMachine {
    /**
     * Drips its slotted canister's goo onto the first surface below.
     */
    TAP("tap", "Tap"),
    /**
     * Stores goo in bulk, receiving at its cap and transmitting from its base.
     */
    VAT("vat", "Vat"),
    /**
     * Melts items into goo and transmits it through its gasket.
     */
    CRUCIBLE("crucible", "Crucible"),
    /**
     * Routes incoming goo into the canisters in its radial slots.
     */
    HUB("hub", "Hub"),
    /**
     * Reconstitutes items from the goo in the canisters on its top face.
     */
    PLEXER("plexer", "Plexer"),
    /**
     * Converts goo from its corner canisters into its front hollow's canister.
     */
    REACTOR("reactor", "Reactor"),
    /**
     * The choral gasket standing as a block.
     */
    CHORAL_GASKET("choral_gasket", "Choral Gasket");

    /**
     * The block's registry path in the goo namespace.
     */
    private final String blockPath;
    /**
     * The name the plot's sign shows.
     */
    private final String displayName;

    /**
     * Binds a machine to its block path and sign name.
     *
     * @param blockPath   the registry path in the goo namespace
     * @param displayName the name the plot's sign shows
     */
    LabMachine(String blockPath, String displayName) {
        this.blockPath = blockPath;
        this.displayName = displayName;
    }

    /**
     * Answers the block's registry path in the goo namespace.
     *
     * @return the registry path
     */
    public String blockPath() {
        return blockPath;
    }

    /**
     * Answers the name the plot's sign shows.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }
}
