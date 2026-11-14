package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.LuxEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for glow's Lux as a held effect through the glove: cast, it
 * stands and keeps night vision up without particles; invoked again, it
 * ends and takes the night vision with it; and its gaze picks the living
 * mob under the crosshair within 32 blocks in clear line (decisions
 * lux-night-vision-without-particles, self-effects-trickle-until-ended).
 */
@RegistersGameTests
public final class LuxTests {
    private static final String LUX_SEES_IN_THE_DARK_UNTIL_ENDED = "lux_sees_in_the_dark_until_ended";
    private static final String LUX_GAZE_PICKS = "lux_gaze_picks_the_mob_in_clear_line_within_reach";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUX_SEES_IN_THE_DARK_UNTIL_ENDED, LuxTests::luxSeesInTheDark);
        GooTestFunctions.registerTest(registrar, LUX_GAZE_PICKS, LuxTests::luxGazePicksTheMobInClearLineWithinReach);
    }


    private static final Identifier GLOW_LUX = Identifier.parse("goo:glow_lux");
    private static final String SHOULD_HOLD = "Once it lands the player should hold Lux";
    private static final String SHOULD_SEE_IN_THE_DARK = "Lux should grant its endless night vision with no particles or card, granted %s";
    private static final String SHOULD_PICK = "Lux's gaze should pick the zombie in clear line within reach, picked %s";
    /** The player stands this far over the bay's top, looking straight up into open sky. */
    private static final int OVER_THE_BAY = 2;
    private static final float STRAIGHT_UP = -90f;
    /** Blocks above the eyes the zombie floats within reach, the block between sits, and the zombie floats past reach. */
    private static final double WITHIN_REACH = 12;

    private LuxTests() {
    }

    /**
     * A survival player casts Lux from the glove and the player ticks once:
     * Lux is held and stands, and night vision stands without particles.
     *
     * @param helper the gametest helper
     */
    public static void luxSeesInTheDark(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.GLOW, GLOW_LUX);
        player.doTick();
        boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(GLOW_LUX);
        boolean stands = player.getData(GooAttachments.LUX).standsAt(player.level().getGameTime());
        MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(held && stands, SHOULD_HOLD);
        helper.assertTrue(LuxEvents.isLuxVision(vision), String.format(SHOULD_SEE_IN_THE_DARK, vision));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A player stands over the bay looking straight up into open sky: a
     * zombie floating 12 blocks above the eyes is the one Lux's gaze picks.
     *
     * @param helper the gametest helper
     */
    public static void luxGazePicksTheMobInClearLineWithinReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.GLOW);
        AABB bay = helper.getBounds();
        player.snapTo(bay.getCenter().x, bay.maxY + OVER_THE_BAY, bay.getCenter().z, 0f, STRAIGHT_UP);
        Vec3 eye = player.getEyePosition();
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setNoGravity(true);
        zombie.setNoAi(true);
        zombie.setPos(eye.x, eye.y + WITHIN_REACH, eye.z);
        level.addFreshEntity(zombie);
        LivingEntity seen = LuxEvents.gazedAt(player);
        zombie.discard();
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(seen == zombie, String.format(SHOULD_PICK, seen));
        helper.succeed();
    }
}
