package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for Stoneskin held on a hurt player: stone primes over
 * the first missing heart.
 */
@RegistersGameTests
public final class StoneskinRoutingTests {
    private static final String STONESKIN_PRIMES_STONE = "stoneskin_primes_stone_over_the_first_missing_heart";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, STONESKIN_PRIMES_STONE, StoneskinRoutingTests::stoneskinPrimesStoneOverTheFirstMissingHeart);
    }


    private static final Identifier ROCK_STONESKIN = Identifier.parse("goo:rock_stoneskin");
    private static final float SIX_HEALTH = 6f;
    /** Under natural regeneration's threshold, so only stone could restore health. */
    private static final int HUNGRY_FOOD = 10;
    private static final String SHOULD_STONE_FIRST_MISSING = "Stoneskin should prime stone at slot %d, read %s";

    private StoneskinRoutingTests() {
    }

    /**
     * A player at six health eats Stoneskin: stone primes over the first
     * missing heart.
     * heart-effects-crawl-while-held
     *
     * @param helper the gametest helper
     */
    public static void stoneskinPrimesStoneOverTheFirstMissingHeart(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.ROCK);
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(HUNGRY_FOOD);
        player.getFoodData().setSaturation(0);
        player.setHealth(SIX_HEALTH);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(ROCK_STONESKIN));
        SelfDeliveryTests.invoke(player, GooTypes.ROCK, ROCK_STONESKIN);
        SelfDeliveryTests.eatThrough(player);
        HeartOverlay stoned = player.getData(GooAttachments.HEART_OVERLAY);
        int firstMissing = HeartOverlay.filledSlots(SIX_HEALTH);

        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(stoned.shieldAt(firstMissing) == HeartOverlay.FULL_SHIELD
                        && stoned.shieldHalves() == HeartOverlay.FULL_SHIELD,
                String.format(SHOULD_STONE_FIRST_MISSING, firstMissing, stoned.shields()));
        helper.succeed();
    }
}
