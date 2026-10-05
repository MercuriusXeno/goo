package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/**
 * Teaches a mock player the items an ability requires, so a gametest of the
 * ability itself runs as a player who has it
 * (decision ability-hidden-until-recipes-known).
 */
public final class KnownRecipes {

    private KnownRecipes() {
    }

    /**
     * Teaches the player every item the ability requires.
     *
     * @param player  the mock player
     * @param ability the ability the player is to have
     */
    public static void teachRequires(ServerPlayer player, AbilityDefinition ability) {
        ability.requires().forEach(item -> PlayerKnowledge.learn(player, BuiltInRegistries.ITEM.getValue(item)));
    }
}
