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
 * Gametests for Osseous's Chitin: the glove lays bone-plated hearts over the
 * player's present hearts that take hits before real health, fire among
 * them, and the player wears the overlay while they stand.
 * decision scales-crystal-hearts-diamond-blue-overlay
 * decision chitin-is-scales-moved-from-crystal
 */
@RegistersGameTests
public final class ChitinTests {
    private static final String CHITIN_ABSORBS_BEFORE_HEALTH = "chitin_absorbs_before_health";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, CHITIN_ABSORBS_BEFORE_HEALTH, ChitinTests::chitinAbsorbsBeforeHealth);
    }


    private static final Identifier OSSEOUS_CHITIN = Identifier.fromNamespaceAndPath(Goo.MODID, "osseous_chitin");
    private static final String SHOULD_LAY = "Chitin should lay bone-plated hearts, overlay %s";

    private ChitinTests() {
    }

    /**
     * A player invoking Chitin wears bone-plated hearts and the overlay.
     *
     * @param helper the gametest helper
     */
    public static void chitinAbsorbsBeforeHealth(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.OSSEOUS, OSSEOUS_CHITIN);
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        helper.assertTrue(overlay.kind() == HeartKind.CHITIN && overlay.stands(), String.format(SHOULD_LAY, overlay));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

}
