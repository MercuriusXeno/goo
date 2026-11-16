package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityRegistrySource;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hex's Leech through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Leech cell there, and a husk walking
 * a lane of Leech goo covers less ground than a like husk walking bare stone
 * over the same ticks; a pig standing in it keeps its health, a husk
 * striking that pig heals by the damage it dealt, and the strike rolls the
 * deplete chance on the cell under the pig.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
 */
@RegistersGameTests
public final class LeechTests {

    private static final String LEECH_LAYS_AND_SLOWS = "leech_drip_lays_a_cell_and_the_goo_slows_a_walker";
    private static final Identifier LEECH = Identifier.parse("goo:hex_leech");
    /** The goo lane's westmost stone, where its walker starts; the lane runs east across the bay. */
    private static final BlockPos GOO_LANE = new BlockPos(0, 1, 1);
    /** The bare lane's westmost stone, three blocks south of the goo lane, out of its walker's reach. */
    private static final BlockPos BARE_LANE = GOO_LANE.south(3);
    /** The lane's eastmost offset from its start, the bay's far wall short of one block. */
    private static final int LANE_EAST = 5;
    /** The ticks both husks walk before the ground each covered is read, short of the bare husk's whole lane. */
    private static final int WALK_TICKS = 20;
    /**
     * How far two husks walking alike lanes may part by chance: unslowed, the
     * husk stepping up onto one-layer goo and the husk on stone each covered
     * 1.957 blocks in WALK_TICKS, yet one run read the goo walker a hair
     * behind, so the goo walker must trail by more than this to count slowed.
     */
    private static final double WALK_JITTER = 0.1;
    private static final String ABILITY_REQUIRED = "The ability registry must hold hex_leech";
    private static final String LAYERS_WRONG = "The cell over the lane's stone should hold one layer of Leech,"
            + " stands %s";
    private static final String NOT_SLOWED = "The husk on Leech goo should cover less ground in %d ticks than the"
            + " husk on bare stone: %.3f on goo, %.3f on stone";
    private static final String GOO_SPARES = "leech_goo_spares_a_mob_standing_in_it";
    /** The stone a full Leech cell stands on, in the middle of the bay. */
    private static final BlockPos SPIRIT_FLOOR = new BlockPos(2, 1, 2);
    /** Ticks a pig stands in the goo, five times the 20 ticks the spirits once bit at. */
    private static final int STANDING_TICKS = 100;
    private static final String HURT_BY_THE_GOO = "A pig standing in Leech goo for %d ticks should keep its health:"
            + " %.2f at the start, %.2f after";
    private static final String STRIKE_DEPLETES = "a_drained_strike_depletes_the_cell_under_its_victim";
    /** A deplete chance every roll falls under. */
    private static final double SURE_DEPLETE = 1.0;
    /** The sourceless damage dealt the pig after the strike, under its health left. */
    private static final float SOURCELESS_DAMAGE = 1f;
    private static final String NOT_DEPLETED = "A drained strike at a deplete chance of 1 should leave the cell at"
            + " %d layers, stands %s";
    private static final String SOURCELESS_MISSED = "The sourceless hit should land on the pig: %.2f before, %.2f after";
    private static final String SOURCELESS_DEPLETED = "Sourceless damage should leave the cell at %d layers, stands %s";
    private static final String BEHAVIORS = "behaviors";
    private static final String TYPE = "type";
    private static final String LEECH_GOO = "leech_goo";
    private static final String DEPLETE_CHANCE = "deplete_chance";
    private static final String STRIKE_DRAINS_LIFE = "a_strike_on_a_mob_in_leech_goo_heals_its_attacker";
    /** The stone the attacking husk stands on, two blocks east of the goo, outside it. */
    private static final BlockPos ATTACKER_FLOOR = SPIRIT_FLOOR.east(2);
    /** The husk's health once wounded, half its twenty. */
    private static final float WOUNDED_HEALTH = 10f;
    /** The damage the husk's strike deals the pig, under the pig's ten health. */
    private static final float STRIKE_DAMAGE = 4f;
    /** Two healths that match differ by no more than float rounding. */
    private static final float SAME_HEALTH = 1e-4f;
    private static final String NOT_DRAINED = "The husk striking a pig in Leech goo should heal by the damage dealt"
            + " times the drained life fraction to %.2f, reads %.2f";

    private LeechTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LEECH_LAYS_AND_SLOWS,
                LeechTests::leechDripLaysACellAndTheGooSlowsAWalker);
        GooTestFunctions.registerTest(registrar, GOO_SPARES, LeechTests::leechGooSparesAMobStandingInIt);
        GooTestFunctions.registerTest(registrar, STRIKE_DEPLETES,
                LeechTests::aDrainedStrikeDepletesTheCellUnderItsVictim);
        GooTestFunctions.registerTest(registrar, STRIKE_DRAINS_LIFE,
                LeechTests::aStrikeOnAMobInLeechGooHealsItsAttacker);
    }

    /**
     * A wounded husk on bare stone strikes a pig standing on a full Leech
     * cell for a known damage; the husk's health reads its wounded health
     * plus that damage times the drained life fraction Leech carries.
     * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
     *
     * @param helper the gametest helper
     */
    public static void aStrikeOnAMobInLeechGooHealsItsAttacker(GameTestHelper helper) {
        layFullCell(helper);
        helper.setBlock(ATTACKER_FLOOR, Blocks.STONE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, SPIRIT_FLOOR.above(2));
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, ATTACKER_FLOOR.above());
        husk.setHealth(WOUNDED_HEALTH);
        AbilityDefinition leech = AbilityRegistry.of(helper.getLevel()).getAbility(LEECH);
        helper.assertTrue(leech != null, ABILITY_REQUIRED);
        float fraction = LeechGooStep.in(leech.behaviors()).orElseThrow().drainFraction();
        pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), STRIKE_DAMAGE);
        float healed = husk.getHealth();
        float expected = WOUNDED_HEALTH + STRIKE_DAMAGE * fraction;
        helper.assertTrue(Math.abs(healed - expected) < SAME_HEALTH, String.format(NOT_DRAINED, expected, healed));
        helper.succeed();
    }

    /**
     * A pig stands on a full Leech cell for five times the spirits' former
     * cadence and keeps the health it started with: the spirits deal no damage.
     * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
     *
     * @param helper the gametest helper
     */
    public static void leechGooSparesAMobStandingInIt(GameTestHelper helper) {
        layFullCell(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, SPIRIT_FLOOR.above(2));
        float start = pig.getHealth();
        helper.runAfterDelay(STANDING_TICKS, () -> {
            helper.assertTrue(pig.getHealth() == start,
                    String.format(HURT_BY_THE_GOO, STANDING_TICKS, start, pig.getHealth()));
            helper.succeed();
        });
    }

    /**
     * A husk strikes a pig standing on a full Leech cell while the server
     * holds Leech at a deplete chance of 1, and the cell reads a layer lower;
     * sourceless damage then lands on the pig and the cell stands as it was.
     * The held chance is restored within the tick, so no other test meets it.
     * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
     *
     * @param helper the gametest helper
     */
    public static void aDrainedStrikeDepletesTheCellUnderItsVictim(GameTestHelper helper) {
        layFullCell(helper);
        helper.setBlock(ATTACKER_FLOOR, Blocks.STONE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, SPIRIT_FLOOR.above(2));
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, ATTACKER_FLOOR.above());
        MinecraftServer server = helper.getLevel().getServer();
        AbilityRegistrySource held = (AbilityRegistrySource) server.getServerResources().managers();
        AbilityRegistry shipped = held.abilityRegistry();
        held.holdAbilityRegistry(withDepleteChance(shipped, SURE_DEPLETE));
        try {
            pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), STRIKE_DAMAGE);
        } finally {
            held.holdAbilityRegistry(shipped);
        }
        int lower = VolumeCellBlock.MAX_LAYERS - 1;
        BlockState struck = helper.getBlockState(SPIRIT_FLOOR.above());
        helper.assertTrue(VolumeCellBlock.layersOf(struck) == lower, String.format(NOT_DEPLETED, lower, struck));
        pig.invulnerableTime = 0;
        float before = pig.getHealth();
        pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), SOURCELESS_DAMAGE);
        helper.assertTrue(pig.getHealth() < before, String.format(SOURCELESS_MISSED, before, pig.getHealth()));
        BlockState after = helper.getBlockState(SPIRIT_FLOOR.above());
        helper.assertTrue(VolumeCellBlock.layersOf(after) == lower, String.format(SOURCELESS_DEPLETED, lower, after));
        helper.succeed();
    }

    /**
     * The shipped abilities with Leech's deplete chance set to a value, by
     * Leech's definition encoded, its leech goo step's chance rewritten and
     * the definition decoded again.
     *
     * @param shipped the shipped abilities
     * @param chance  the deplete chance Leech carries
     * @return the abilities with Leech's chance set
     */
    private static AbilityRegistry withDepleteChance(AbilityRegistry shipped, double chance) {
        Codec<AbilityDefinition> codec = AbilityDefinition.codecFor(LEECH);
        JsonObject json = codec.encodeStart(JsonOps.INSTANCE, shipped.getAbility(LEECH)).getOrThrow()
                .getAsJsonObject();
        for (JsonElement behavior : json.getAsJsonArray(BEHAVIORS)) {
            JsonObject step = behavior.getAsJsonObject();
            if (LEECH_GOO.equals(step.get(TYPE).getAsString())) {
                step.addProperty(DEPLETE_CHANCE, chance);
            }
        }
        Map<Identifier, AbilityDefinition> abilities = new HashMap<>();
        shipped.all().forEach(ability -> abilities.put(ability.id(), ability));
        abilities.put(LEECH, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
        return new AbilityRegistry(abilities);
    }

    private static void layFullCell(GameTestHelper helper) {
        helper.setBlock(SPIRIT_FLOOR, Blocks.STONE);
        helper.setBlock(SPIRIT_FLOOR.above(), GooBlocks.HEX_LEECH.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
    }

    /**
     * Leech drips land one layer on each stone of a lane east of its start,
     * the first of them read at one layer; a husk walks that lane while a
     * like husk walks a bare stone lane toward a like target, and after the
     * same ticks the husk in the goo has covered the less ground.
     *
     * @param helper the gametest helper
     */
    public static void leechDripLaysACellAndTheGooSlowsAWalker(GameTestHelper helper) {
        layLane(helper, GOO_LANE);
        layLane(helper, BARE_LANE);
        AbilityDefinition leech = AbilityRegistry.of(helper.getLevel()).getAbility(LEECH);
        helper.assertTrue(leech != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.HEX);
        KnownRecipes.teachRequires(caster, leech);
        for (int east = 1; east <= LANE_EAST; east++) {
            LandedDrips.landOn(helper, caster, leech, GOO_LANE.east(east), 1);
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState first = helper.getBlockState(GOO_LANE.east(1).above());
        boolean oneLayer = first.is(GooBlocks.HEX_LEECH.get()) && first.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, first));
        Mob onGoo = walkTheLane(helper, GOO_LANE);
        Mob onStone = walkTheLane(helper, BARE_LANE);
        double gooStart = onGoo.getX();
        double stoneStart = onStone.getX();
        helper.runAfterDelay(WALK_TICKS, () -> {
            double onGooCovered = onGoo.getX() - gooStart;
            double onStoneCovered = onStone.getX() - stoneStart;
            helper.assertTrue(onGooCovered + WALK_JITTER < onStoneCovered,
                    String.format(NOT_SLOWED, WALK_TICKS, onGooCovered, onStoneCovered));
            helper.succeed();
        });
    }

    /**
     * Lays a lane of stone on bedrock from its start east across the bay,
     * open air over it.
     *
     * @param helper the gametest helper
     * @param start  the lane's westmost stone
     */
    private static void layLane(GameTestHelper helper, BlockPos start) {
        for (int east = 0; east <= LANE_EAST; east++) {
            BlockPos stone = start.east(east);
            helper.setBlock(stone.below(), Blocks.BEDROCK);
            helper.setBlock(stone, Blocks.STONE);
            helper.setBlock(stone.above(), Blocks.AIR);
        }
    }

    /**
     * Spawns a husk on a lane's westmost stone, where daylight does not burn
     * it, and walks it toward the lane's eastmost block.
     *
     * @param helper the gametest helper
     * @param start  the lane's westmost stone
     * @return the walking husk
     */
    private static Mob walkTheLane(GameTestHelper helper, BlockPos start) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, start.above());
        helper.walkTo(husk, start.east(LANE_EAST).above(), 1f);
        return husk;
    }
}
