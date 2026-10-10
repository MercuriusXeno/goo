package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.crystal.ScalesOverlay;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gametests for Crystal's Scales: the glove lays crystal hearts over the
 * player's present hearts that take hits before real health, fire among
 * them, and the player wears the faceted diamond-blue overlay while they stand.
 * decision scales-crystal-hearts-diamond-blue-overlay
 */
public final class ScalesTests {

    private static final Identifier CRYSTAL_SCALES = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_scales");
    private static final float ONE_POINT = 1f;
    private static final String SHOULD_LAY = "Scales should lay crystal hearts, overlay %s";
    private static final String SHOULD_WEAR = "The player should wear the Scales overlay while crystal stands";
    private static final String SHOULD_ABSORB = "Crystal should take the %s hit: health %.1f of %.1f, halves %d of %d";

    private ScalesTests() {
    }

    /**
     * A player invoking Scales wears crystal hearts and the overlay; a
     * generic hit and a fire hit each break a crystal half and leave health whole.
     *
     * @param helper the gametest helper
     */
    public static void scalesAbsorbsBeforeHealth(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.CRYSTAL, CRYSTAL_SCALES);
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        helper.assertTrue(overlay.kind() == HeartKind.SCALES && overlay.stands(), String.format(SHOULD_LAY, overlay));
        helper.assertTrue(ScalesOverlay.wearsScales(player), SHOULD_WEAR);

        strikeAbsorbed(helper, player, "generic", () -> HeartOverlayTests.hurt(helper, player,
                player.damageSources().generic(), ONE_POINT));
        strikeAbsorbed(helper, player, "fire", () -> HeartOverlayTests.hurt(helper, player,
                player.damageSources().inFire(), ONE_POINT));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static void strikeAbsorbed(GameTestHelper helper, ServerPlayer player, String what, Runnable strike) {
        float health = player.getHealth();
        int halves = HeartOverlayTests.halves(player);
        strike.run();
        float after = player.getHealth();
        int halvesAfter = HeartOverlayTests.halves(player);
        helper.assertTrue(after == health && halvesAfter == halves - 1,
                String.format(SHOULD_ABSORB, what, after, health, halvesAfter, halves));
    }
}
