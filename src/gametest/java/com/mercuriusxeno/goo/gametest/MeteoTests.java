package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.ability.world.MeteorSky;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Gametests for Meteo: under a clear sky its meteor falls and blasts a
 * crater at the target; under a roof the throw fizzles before it is paid
 * for, taking no goo and calling no meteor (decision meteo-needs-a-clear-sky).
 */
public final class MeteoTests {

    private static final Identifier ASTRAL_METEO = Identifier.fromNamespaceAndPath(Goo.MODID, "astral_meteo");
    /** The ground block the meteor strikes on its top face. */
    private static final BlockPos GROUND = new BlockPos(4, 1, 4);
    /** How far over the ground the roof stands. */
    private static final int ROOF_HEIGHT = 4;
    /** How far above the ground a falling meteor is looked for. */
    private static final double FALL_SEARCH = 64.0;
    private static final int NO_TARGET_ENTITY = -1;
    private static final String ABILITY_REQUIRED = "goo:astral_meteo must be loaded";
    private static final String SKY_REQUIRED = "The test bay's ground should stand under an open sky";
    private static final String SHOULD_FALL = "A meteor should fall toward the ground under a clear sky";
    private static final String SHOULD_CRATER = "The meteor should blast the ground at %s into a crater";
    private static final String SUPPLY_REQUIRED = "The thrower should hold Meteo's cost, holds %d of %d";
    private static final String SHOULD_NOT_PAY = "A roofed throw should cost nothing, cost %d";
    private static final String SHOULD_NOT_FALL = "A roofed throw should call no meteor";

    private MeteoTests() {
    }

    /**
     * Meteo landing on open ground calls a meteor that falls and blasts the
     * ground it strikes into a crater.
     *
     * @param helper the gametest helper
     */
    public static void meteoStrikesUnderClearSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition meteo = AbilityRegistry.of(level).getAbility(ASTRAL_METEO);
        helper.assertTrue(meteo != null, ABILITY_REQUIRED);
        BlockPos ground = helper.absolutePos(GROUND);
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        openCageRoofOver(level, ground);
        helper.assertTrue(MeteorSky.clearAbove(level, ground.above()), SKY_REQUIRED);

        AbilityImpact.land(level, ground, GooTypes.ASTRAL, Direction.UP, meteo);

        helper.assertTrue(!meteorsOver(level, ground).isEmpty(), SHOULD_FALL);
        helper.succeedWhen(() -> helper.assertTrue(level.getBlockState(ground).isAir(),
                String.format(SHOULD_CRATER, ground)));
    }

    /**
     * Meteo thrown at ground under a stone roof fizzles: the thrower keeps
     * every drop of goo and no meteor falls.
     *
     * @param helper the gametest helper
     */
    public static void meteoFizzlesUnderARoof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition meteo = AbilityRegistry.of(level).getAbility(ASTRAL_METEO);
        helper.assertTrue(meteo != null, ABILITY_REQUIRED);
        BlockPos ground = helper.absolutePos(GROUND);
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(ground.above(ROOF_HEIGHT), Blocks.STONE.defaultBlockState());
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.ASTRAL);
        KnownRecipes.teachRequires(player, meteo);
        int held = astralHeld(player);
        helper.assertTrue(held >= meteo.cost(), String.format(SUPPLY_REQUIRED, held, meteo.cost()));

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.ASTRAL), NO_TARGET_ENTITY,
                ground, Direction.UP.ordinal(), false, ASTRAL_METEO.toString(), player.getEyePosition()));

        int spent = held - astralHeld(player);
        helper.assertTrue(spent == 0, String.format(SHOULD_NOT_PAY, spent));
        helper.assertTrue(meteorsOver(level, ground).isEmpty(), SHOULD_NOT_FALL);
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Clears the barriers of the test's cage standing over a cell, so the
     * cell sees the sky the flat world gives it.
     *
     * @param level the level
     * @param cell  the cell
     */
    private static void openCageRoofOver(ServerLevel level, BlockPos cell) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cell.getX(), cell.getZ());
        for (BlockPos above = cell.above(); above.getY() < top; above = above.above()) {
            if (level.getBlockState(above).is(Blocks.BARRIER)) {
                level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static java.util.List<Meteor> meteorsOver(ServerLevel level, BlockPos ground) {
        return level.getEntitiesOfClass(Meteor.class, new AABB(ground).expandTowards(0, FALL_SEARCH, 0));
    }

    private static int astralHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ASTRAL, 0);
    }
}
