package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Crystal's Scales: the glove lays crystal hearts over the
 * player's present hearts that take hits before real health, fire among
 * them, and the player wears the faceted diamond-blue overlay while they stand.
 * decision scales-crystal-hearts-diamond-blue-overlay
 */
@RegistersGameTests
public final class ScalesTests {
    private static final String SCALES_ABSORBS_BEFORE_HEALTH = "scales_absorbs_before_health";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SCALES_ABSORBS_BEFORE_HEALTH, ScalesTests::scalesAbsorbsBeforeHealth);
    }


    private static final Identifier CRYSTAL_SCALES = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_scales");
    private static final String SHOULD_LAY = "Scales should lay crystal hearts, overlay %s";

    private ScalesTests() {
    }

    /**
     * A player invoking Scales wears crystal hearts and the overlay.
     *
     * @param helper the gametest helper
     */
    public static void scalesAbsorbsBeforeHealth(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.CRYSTAL, CRYSTAL_SCALES);
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        helper.assertTrue(overlay.kind() == HeartKind.SCALES && overlay.stands(), String.format(SHOULD_LAY, overlay));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

}
