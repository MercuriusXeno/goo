package com.mercuriusxeno.goo.data;

import java.util.List;

/**
 * A datapack load's hold on the reactor reactions it read: a mixin gives the
 * server's reloadable resources one, so a reload swaps the reactions with the
 * rest of the load and a server stop drops them
 * (decision type-package-and-per-server-holders).
 */
public interface GooReactionSource {

    /**
     * Answers the reactions this load holds, most inputs first.
     *
     * @return the reactions, empty before the loader applies
     */
    List<GooReaction> gooReactions();

    /**
     * Holds the reactions the loader read.
     *
     * @param reactions the loaded reactions, most inputs first
     */
    void holdGooReactions(List<GooReaction> reactions);
}
