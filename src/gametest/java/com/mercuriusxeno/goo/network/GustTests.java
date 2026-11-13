package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for typhoon Gust streamed through the real stream delivery: its
 * rushing wind flings a mob in its cone out past its reach while held
 * (decision repel-cone-flings-with-frost-wind-lines).
 */
@RegistersGameTests
public final class GustTests {
    private static final String GUST_FLINGS_A_ZOMBIE = "gust_flings_a_zombie";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GUST_FLINGS_A_ZOMBIE, GustTests::gustFlingsAZombie);
    }


    private static final Identifier TYPHOON_GUST = Identifier.parse("goo:typhoon_gust");
    /** In the sixteen-block light bay, with room east of the zombie to fling it. */
    private static final BlockPos LONG_RUN_STREAMER_POS = new BlockPos(1, 1, 8);
    private static final BlockPos LONG_RUN_ZOMBIE_POS = new BlockPos(4, 1, 8);
    private static final float FACING_EAST = -90f;
    /** Level, so a body three blocks off stands inside the stream's cone. */
    private static final float LEVEL = 0f;
    private static final int HOLD_TICKS = 10;
    private static final String ABILITY_REQUIRED = "Ability registry must hold typhoon_gust";
    private static final String SHOULD_FLING_OUT =
            "Ten held ticks of Gust should fling the zombie away from %.2f off, stands %.2f from the streamer";

    private GustTests() {
    }

    /**
     * A player streams Gust east at a zombie three blocks off: within ten
     * held ticks the zombie stands farther from the streamer than it stood.
     *
     * @param helper the gametest helper
     */
    public static void gustFlingsAZombie(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, LONG_RUN_ZOMBIE_POS);
        ServerPlayer streamer = streamer(helper, LONG_RUN_STREAMER_POS);
        Vec3 eye = streamer.getEyePosition();
        double stood = zombie.position().distanceTo(eye);
        hold(helper, streamer);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            double away = zombie.position().distanceTo(eye);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(away > stood, String.format(SHOULD_FLING_OUT, stood, away));
            helper.succeed();
        });
    }

    private static ServerPlayer streamer(GameTestHelper helper, BlockPos standing) {
        AbilityDefinition gust = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_GUST);
        helper.assertTrue(gust != null, ABILITY_REQUIRED);
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON, TYPHOON_GUST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(standing));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(LEVEL);
        KnownRecipes.teachRequires(streamer, gust);
        return streamer;
    }

    private static GooStreamPayload tick(ServerPlayer streamer) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.TYPHOON), TYPHOON_GUST.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
    }

    private static void hold(GameTestHelper helper, ServerPlayer streamer) {
        GooStreamPayload tick = tick(streamer);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
    }
}
