package com.mercuriusxeno.goo.ability;

/**
 * A datapack load's hold on the abilities it read: a mixin gives the server's
 * reloadable resources one, so a reload swaps the abilities with the rest of
 * the load and a server stop drops them (decision type-package-and-per-server-holders).
 */
public interface AbilityRegistrySource {

    /**
     * Answers the abilities this load holds.
     *
     * @return the registry, empty before the loader applies
     */
    AbilityRegistry abilityRegistry();

    /**
     * Holds the abilities the loader read.
     *
     * @param registry the loaded abilities
     */
    void holdAbilityRegistry(AbilityRegistry registry);
}
