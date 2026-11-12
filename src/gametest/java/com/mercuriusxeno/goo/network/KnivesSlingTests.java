package com.mercuriusxeno.goo.network;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Crystal's Knives: a full charge let go rains glass knives
 * over an area ahead, striking mobs spread across it, and no knife strikes
 * before it has left the hand and flown.
 * decision shards-sling-then-morph-to-flechettes
 */
@RegistersGameTests
public final class KnivesSlingTests {
    private static final String KNIVES_RAIN_LAYS_INTO_AN_AREA = "knives_rain_lays_into_an_area";
    private static final String KNIVES_FRACTIONAL_COST_BANKS_ITS_HALF = "knives_fractional_cost_banks_its_half";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, KNIVES_RAIN_LAYS_INTO_AN_AREA, KnivesSlingTests::knivesRainLaysIntoAnArea);
        GooTestFunctions.registerTest(registrar, KNIVES_FRACTIONAL_COST_BANKS_ITS_HALF,
                KnivesSlingTests::fractionalCostBanksItsHalf);
    }


    private static final Identifier CRYSTAL_KNIVES = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_knives");
    /** The light bay is sixteen blocks across and holds no floor of its own. */
    private static final int BAY_SIDE = 16;
    private static final BlockPos CASTER_POS = new BlockPos(8, 1, 1);
    /** Husks across the cone seven blocks ahead, where the lobbed knives come down through them. */
    private static final List<BlockPos> SPREAD = List.of(new BlockPos(6, 1, 8), new BlockPos(8, 1, 8),
            new BlockPos(10, 1, 8));
    /** Facing south, down the bay toward the husks. */
    private static final float FACING_SOUTH = 0f;
    private static final int FULL_CHARGE_TICKS = 30;
    /** A fixed scatter, so the rain falls the same each run. */
    private static final long SCATTER_SEED = 11L;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_knives";
    private static final String SHOULD_RAIN = "The rain should strike every husk across the area, struck %s of %s";
    private static final String FIELD_COST = "cost";
    /** A cost of 12 1/2 mB, written as the decimal an ability file may write. */
    private static final double TWELVE_AND_A_HALF = 12.5;
    private static final String SHOULD_CHARGE = "Release %s of a 12 1/2 mB cost should take %s mB and bank %s, took %s and"
            + " banked %s";

    private KnivesSlingTests() {
    }

    /**
     * A mock player holds crystal_knives to a full charge and lets go facing
     * three husks spread across the area ahead: the rain strikes all three.
     *
     * @param helper the gametest helper
     */
    public static void knivesRainLaysIntoAnArea(GameTestHelper helper) {
        for (int x = 0; x < BAY_SIDE; x++) {
            for (int z = 0; z < BAY_SIDE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        List<Mob> husks = SPREAD.stream().<Mob>map(pos -> helper.spawnWithNoFreeWill(EntityType.HUSK, pos)).toList();
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, FACING_SOUTH, 0f);
        AbilityDefinition knives = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_KNIVES);
        helper.assertTrue(knives != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(caster, knives);

        KnifeRainSling.sling(caster, GooTypes.CRYSTAL, knives, FULL_CHARGE_TICKS, RandomSource.create(SCATTER_SEED));

        helper.succeedWhen(() -> {
            long struck = husks.stream().filter(husk -> husk.getHealth() < husk.getMaxHealth()).count();
            helper.assertTrue(struck == husks.size(), String.format(SHOULD_RAIN, struck, husks.size()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * Knives priced at 12 1/2 mB, released twice: the first release takes 13 mB and banks 1/2 in the caster's
     * remainder, the second takes 12 and the remainder pays the rest, 25 mB across the two.
     * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
     *
     * @param helper the gametest helper
     */
    public static void fractionalCostBanksItsHalf(GameTestHelper helper) {
        AbilityDefinition knives = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_KNIVES);
        helper.assertTrue(knives != null, ABILITY_REQUIRED);
        AbilityDefinition halved = costing(knives, TWELVE_AND_A_HALF);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(caster, halved);

        int[] took = {13, 12};
        GooFraction[] banked = {GooFraction.of(1, 2), GooFraction.ZERO};
        for (int release = 0; release < took.length; release++) {
            int before = crystalHeld(caster);
            KnifeRainSling.sling(caster, GooTypes.CRYSTAL, halved, FULL_CHARGE_TICKS, RandomSource.create(SCATTER_SEED));
            int spent = before - crystalHeld(caster);
            GooFraction kept = GooCapabilities.remainderLedgerOf(caster).remainders().of(GooTypes.CRYSTAL);
            helper.assertTrue(spent == took[release] && kept.equals(banked[release]),
                    String.format(SHOULD_CHARGE, release + 1, took[release], banked[release], spent, kept));
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    // The ability as its file would read with another cost.
    private static AbilityDefinition costing(AbilityDefinition ability, double cost) {
        Codec<AbilityDefinition> codec = AbilityDefinition.codecFor(ability.id());
        JsonObject file = codec.encodeStart(JsonOps.INSTANCE, ability).getOrThrow().getAsJsonObject();
        file.addProperty(FIELD_COST, cost);
        return codec.parse(JsonOps.INSTANCE, file).getOrThrow();
    }

    private static int crystalHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.CRYSTAL, 0);
    }
}
