package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.MeteorStep;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.ability.world.MeteorFall;
import com.mercuriusxeno.goo.data.GooValueTable;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Meteo: under a clear sky its meteor leaves its spawn height
 * at once, falls for about two seconds and blasts a crater at the target;
 * under a roof the throw goes ahead, paid for, and calls a meteor.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 * decision meteo-crashes-through-whatever-is-above
 */
@RegistersGameTests
public final class MeteoTests {
    private static final String METEO_STRIKES = "meteo_strikes_under_clear_sky";
    private static final String METEO_THROWN_UNDER_A_ROOF = "meteo_is_thrown_under_a_roof";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, METEO_STRIKES, MeteoTests::meteoStrikesUnderClearSky);
        GooTestFunctions.registerTest(registrar, METEO_THROWN_UNDER_A_ROOF, MeteoTests::meteoIsThrownUnderARoof);
    }


    private static final Identifier ASTRAL_METEO = Identifier.fromNamespaceAndPath(Goo.MODID, "astral_meteo");
    /** The ground block the meteor strikes on its top face. */
    private static final BlockPos GROUND = new BlockPos(4, 1, 4);
    /** How far over the ground the roof stands. */
    private static final int ROOF_HEIGHT = 4;
    /** How far above the ground a falling meteor is looked for. */
    private static final double FALL_SEARCH = MeteorFall.FALL_HEIGHT + 16.0;
    private static final int NO_TARGET_ENTITY = -1;
    private static final String ABILITY_REQUIRED = "goo:astral_meteo must be loaded";
    private static final String SKY_REQUIRED = "The test bay's ground should stand under an open sky";
    private static final String SHOULD_FALL = "A meteor should fall toward the ground under a clear sky";
    private static final String SHOULD_CRATER = "The meteor should blast the ground at %s into a crater";
    /** Ticks by which the meteor must have left its spawn height: it never hangs. */
    private static final int FIRST_TICKS = 2;
    /** Ticks past the named fall the landing may take, for the tick the meteor is added on and its last. */
    private static final int LANDING_SLACK = 2;
    private static final String STEP_REQUIRED = "goo:astral_meteo should name a meteor step";
    private static final String LEAVES_AT_ONCE = "The meteor should have left its spawn height within %d ticks,"
            + " %s, stands at %s";
    private static final String SHOULD_LAND = "The meteor should land within %d ticks of appearing";
    private static final String SUPPLY_REQUIRED = "The thrower should hold Meteo's cost, holds %d of %d";
    private static final String SHOULD_PAY = "A roofed throw should cost Meteo's %d, cost %d";
    private static final String SHOULD_FALL_UNDER_A_ROOF = "A roofed throw should call a meteor over the cell";

    private MeteoTests() {
    }

    /**
     * Meteo landing on open ground calls a meteor that falls for its step's
     * fall ticks and blasts the ground it strikes into a crater;
     * MeteorStepTest holds the forty ticks.
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
        helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING, ground.getX(), ground.getZ())
                == ground.above().getY(), SKY_REQUIRED);

        MeteorStep step = meteo.behaviors().stream().filter(MeteorStep.class::isInstance)
                .map(MeteorStep.class::cast).findFirst().orElse(null);
        helper.assertTrue(step != null, STEP_REQUIRED);
        int falls = step.fallTicks();

        AbilityImpact.land(level, ground, GooTypes.ASTRAL, Direction.UP, meteo);

        java.util.List<Meteor> called = meteorsOver(level, ground);
        helper.assertTrue(!called.isEmpty(), SHOULD_FALL);
        Meteor meteor = called.getFirst();
        double spawnY = meteor.getY();
        helper.runAfterDelay(FIRST_TICKS, () -> helper.assertTrue(!meteor.isRemoved() && meteor.getY() < spawnY,
                String.format(LEAVES_AT_ONCE, FIRST_TICKS, spawnY, meteor.getY())));
        helper.runAfterDelay(falls + LANDING_SLACK, () -> {
            helper.assertTrue(meteor.isRemoved(), String.format(SHOULD_LAND, falls + LANDING_SLACK));
            helper.assertTrue(level.getBlockState(ground).isAir(), String.format(SHOULD_CRATER, ground));
            helper.succeed();
        });
    }

    /**
     * Meteo thrown at ground under a stone roof goes ahead: the thrower pays
     * its cost and a meteor falls over the cell.
     * decision meteo-crashes-through-whatever-is-above
     *
     * @param helper the gametest helper
     */
    public static void meteoIsThrownUnderARoof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition meteo = AbilityRegistry.of(level).getAbility(ASTRAL_METEO);
        helper.assertTrue(meteo != null, ABILITY_REQUIRED);
        BlockPos ground = helper.absolutePos(GROUND);
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        openCageRoofOver(level, ground);
        level.setBlockAndUpdate(ground.above(ROOF_HEIGHT), Blocks.STONE.defaultBlockState());
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.ASTRAL);
        KnownRecipes.teachRequires(player, meteo);
        int held = astralHeld(player);
        int cost = meteo.cost().in(GooValueTable.EMPTY);
        helper.assertTrue(held >= cost, String.format(SUPPLY_REQUIRED, held, cost));

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.ASTRAL), NO_TARGET_ENTITY,
                ground, Direction.UP.ordinal(), false, ASTRAL_METEO.toString(), player.getEyePosition()));

        int spent = held - astralHeld(player);
        helper.assertTrue(spent == cost, String.format(SHOULD_PAY, cost, spent));
        helper.succeedWhen(() -> {
            helper.assertTrue(!meteorsOver(level, ground).isEmpty(), SHOULD_FALL_UNDER_A_ROOF);
            level.getServer().getPlayerList().remove(player);
        });
    }

    /**
     * Clears the barriers of the test's cage standing over a cell, so the
     * cell sees the sky the flat world gives it.
     *
     * @param level the level
     * @param cell  the cell
     */
    public static void openCageRoofOver(ServerLevel level, BlockPos cell) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cell.getX(), cell.getZ());
        for (BlockPos above = cell.above(); above.getY() < top; above = above.above()) {
            if (level.getBlockState(above).is(Blocks.BARRIER)) {
                level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * The meteors falling over a cell, up to their spawn height.
     *
     * @param level  the level
     * @param ground the cell
     * @return the meteors over it
     */
    public static java.util.List<Meteor> meteorsOver(ServerLevel level, BlockPos ground) {
        return level.getEntitiesOfClass(Meteor.class, new AABB(ground).expandTowards(0, FALL_SEARCH, 0));
    }

    private static int astralHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ASTRAL, 0);
    }
}
