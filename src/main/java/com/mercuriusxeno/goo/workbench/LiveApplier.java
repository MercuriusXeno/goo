package com.mercuriusxeno.goo.workbench;

/**
 * Applies base_values.json text to the values every player on a running server holds, as a Save, a Promote or a
 * Discard leaves it standing; the in-game manager binds one and the gradle run, holding no server, binds
 * {@link #NONE} (decision save-applies-the-draft-live-promote-renames-it).
 */
@FunctionalInterface
public interface LiveApplier {
    /** The applier of a run with no server: the file effects alone. */
    LiveApplier NONE = text -> { };

    /**
     * @param baseValuesText the base_values.json text now standing for the world
     */
    void apply(String baseValuesText);
}
