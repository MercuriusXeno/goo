package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's airlessness: a player in goo:lunar loses one air a tick as underwater, takes drowning damage
 * when the air runs out, and keeps it under water breathing, while a player outside lunar refills as vanilla does.
 * decision astral-air-drains-as-underwater
 */
@RegistersGameTests
public final class LunarAirTests {
    private static final String LUNAR_AIR_DRAINS_AS_UNDERWATER = "lunar_air_drains_as_underwater";

    private static final ResourceKey<Level> LUNAR = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));
    /** Vanilla takes one air a tick from a breath held underwater, and refills four a tick in open air. */
    private static final int TICKS = 20;
    private static final int AIR_TAKEN_A_TICK = 1;
    private static final int AIR_REFILLED_A_TICK = 4;
    private static final int PART_FULL_AIR = 100;
    /** One tick before vanilla's drowning threshold of -20 air. */
    private static final int LAST_BREATH = -19;
    private static final int WATER_BREATHING_TICKS = 600;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String DRAINS = "A player in lunar should lose %d air over %d ticks, %d to %d";
    private static final String DROWNS = "A player out of air in lunar should take drowning damage, health %s to %s";
    private static final String HELD = "Water breathing should hold a player's air in lunar, %d to %d";
    private static final String REFILLS = "A player outside lunar should refill %d air a tick, %d to %d";

    private LunarAirTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_AIR_DRAINS_AS_UNDERWATER, LunarAirTests::lunarAirDrainsAsUnderwater);
    }

    /**
     * In goo:lunar a survival player's air drains one a tick, drowning damage lands when it runs out, water
     * breathing holds it, and a player in the overworld refills four a tick.
     *
     * @param helper the gametest helper
     */
    public static void lunarAirDrainsAsUnderwater(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = overworld.getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        lunar.getChunk(0, 0);
        Vec3 openGround = new Vec3(0.5, lunar.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) + 1, 0.5);
        travel(player, new TeleportTransition(lunar, openGround, Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));

        int full = player.getMaxAirSupply();
        player.setAirSupply(full);
        tick(player, TICKS);
        helper.assertTrue(player.getAirSupply() == full - TICKS * AIR_TAKEN_A_TICK,
                String.format(DRAINS, TICKS * AIR_TAKEN_A_TICK, TICKS, full, player.getAirSupply()));

        player.setAirSupply(LAST_BREATH);
        float before = player.getHealth();
        tick(player, 1);
        helper.assertTrue(player.getHealth() < before, String.format(DROWNS, before, player.getHealth()));

        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, WATER_BREATHING_TICKS));
        player.setAirSupply(PART_FULL_AIR);
        tick(player, TICKS);
        helper.assertTrue(player.getAirSupply() >= PART_FULL_AIR,
                String.format(HELD, PART_FULL_AIR, player.getAirSupply()));
        overworld.getServer().getPlayerList().remove(player);

        ServerPlayer outside = SurvivalPlayers.placeIn(helper);
        outside.setAirSupply(PART_FULL_AIR);
        tick(outside, 1);
        helper.assertTrue(outside.getAirSupply() == PART_FULL_AIR + AIR_REFILLED_A_TICK,
                String.format(REFILLS, AIR_REFILLED_A_TICK, PART_FULL_AIR, outside.getAirSupply()));
        overworld.getServer().getPlayerList().remove(outside);
        helper.succeed();
    }

    /**
     * Carries the player to another level as a client would arrive: a real client acknowledges the dimension change
     * and reports itself loaded, and until both the server holds the player invulnerable.
     *
     * @param player     the player
     * @param transition where the player goes
     */
    private static void travel(ServerPlayer player, TeleportTransition transition) {
        player.teleport(transition);
        player.hasChangedDimension();
        player.connection.markClientLoaded();
    }

    private static void tick(ServerPlayer player, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            player.baseTick();
        }
    }
}
