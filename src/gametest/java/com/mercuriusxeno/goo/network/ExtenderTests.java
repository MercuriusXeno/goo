package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
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
 * Gametests for pulse's Extender held on the glove: while it stands, every
 * other held effect pays its upkeep on alternate ticks alone, so Kindle held
 * beside it drains half its goo.
 * extender-multiplies-the-next-self-duration
 */
@RegistersGameTests
public final class ExtenderTests {
    private static final String EXTENDER_HALVES_THE_OTHER_HELD_UPKEEP = "extender_halves_the_other_held_upkeep";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, EXTENDER_HALVES_THE_OTHER_HELD_UPKEEP,
                ExtenderTests::extenderHalvesTheOtherHeldUpkeep);
    }

    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final Identifier PULSE_EXTENDER = Identifier.parse("goo:pulse_extender");
    /** An even count, so the covered ticks are exactly half of them. */
    private static final int UPKEEP_TICKS = 20;
    private static final int HELD_PULSE = 3;
    private static final String SHOULD_HOLD_BOTH = "Kindle and Extender should both be held, holds %s";
    private static final String SHOULD_DRAIN_HALF =
            "Kindle beside Extender should drain about %d mB over %d ticks, half its upkeep, drained %d";

    private ExtenderTests() {
    }

    /**
     * A survival player holds Kindle and then Extender on the glove: over the
     * ticks after, Kindle drains within one upkeep of half what it drains alone.
     *
     * @param helper the gametest helper
     */
    public static void extenderHalvesTheOtherHeldUpkeep(GameTestHelper helper) {
        int kindleUpkeep = AbilityRegistry.of(helper.getLevel()).getAbility(BLAZE_KINDLE).upkeep()
                .in(GooValueTable.EMPTY);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.PULSE, HELD_PULSE * GooStacks.THOUSAND));
        HeartOverlayTests.invoke(player, GooTypes.PULSE, PULSE_EXTENDER);
        boolean holdsBoth = player.getData(GooAttachments.HELD_EFFECTS).holds(BLAZE_KINDLE)
                && player.getData(GooAttachments.HELD_EFFECTS).holds(PULSE_EXTENDER);
        int blazeBefore = blaze(player);
        SelfDeliveryTests.tickFor(helper, player, UPKEEP_TICKS);
        helper.runAfterDelay(UPKEEP_TICKS + 1, () -> {
            int drained = blazeBefore - blaze(player);
            int half = UPKEEP_TICKS * kindleUpkeep / 2;
            String held = player.getData(GooAttachments.HELD_EFFECTS).held().toString();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(holdsBoth, String.format(SHOULD_HOLD_BOTH, held));
            helper.assertTrue(Math.abs(drained - half) <= kindleUpkeep,
                    String.format(SHOULD_DRAIN_HALF, half, UPKEEP_TICKS, drained));
            helper.succeed();
        });
    }

    private static int blaze(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.BLAZE, 0);
    }
}