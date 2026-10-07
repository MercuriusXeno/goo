package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Gametest that Blast reaches the glove only for a player who knows gunpowder.
 * decision blast-keeps-its-explosion-gated-on-gunpowder
 */
public final class BlastGateTests {

    private static final String REMOVAL = "removal";
    private static final String BLAST_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_explode").toString();
    private static final String BLAST_NOT_SYNCED = "The unstable glove entries should carry Blast";
    private static final String BLAST_SHOWN_EARLY = "Blast should stay off the glove until gunpowder is known";
    private static final String BLAST_HIDDEN_LATE = "Blast should reach the glove once gunpowder is known";

    private BlastGateTests() {
    }

    /**
     * Syncs the unstable glove entries, asserts Blast is hidden from a mock
     * player who does not know gunpowder, teaches it, and asserts Blast shows.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void blastHiddenUntilGunpowder(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ClientAbility blast = AbilitySyncPayload.gloveEntries(GooTypes.UNSTABLE,
                        AbilityRegistry.of(helper.getLevel()).getAbilitiesForType(GooTypes.UNSTABLE)).stream()
                .filter(entry -> entry.abilityId().equals(BLAST_ID))
                .map(ClientAbility::fromEntry)
                .findFirst()
                .orElse(null);
        helper.assertTrue(blast != null, BLAST_NOT_SYNCED);
        helper.assertFalse(blast.isKnownTo(PlayerKnowledge.of(player)), BLAST_SHOWN_EARLY);
        PlayerKnowledge.learn(player, Items.GUNPOWDER);
        helper.assertTrue(blast.isKnownTo(PlayerKnowledge.of(player)), BLAST_HIDDEN_LATE);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
