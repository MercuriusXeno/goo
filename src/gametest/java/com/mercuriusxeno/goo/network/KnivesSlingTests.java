package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
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

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, KNIVES_RAIN_LAYS_INTO_AN_AREA, KnivesSlingTests::knivesRainLaysIntoAnArea);
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
    /** decision knives-cone-five-to-forty-five-and-three-to-twenty-seven-knives */
    private static final int FULL_CHARGE_KNIVES = 27;
    /** A fixed scatter, so the rain falls the same each run. */
    private static final long SCATTER_SEED = 11L;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_knives";
    private static final String SHOULD_SLING = "A full charge should sling %s knives, slung %s";
    private static final String STRUCK_AT_ONCE = "No husk should take a knife the tick the charge is let go";
    private static final String SHOULD_RAIN = "The rain should strike every husk across the area, struck %s of %s";

    private KnivesSlingTests() {
    }

    /**
     * A mock player holds crystal_knives to a full charge and lets go facing
     * three husks spread across the area ahead: none is struck the tick it
     * lets go, and the rain strikes all three.
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

        FleckLaunches launches = GooServerState.of(helper.getLevel().getServer()).fleckLaunches();
        int queuedBefore = launches.pendingCount();

        KnifeRainSling.sling(caster, GooTypes.CRYSTAL, knives, FULL_CHARGE_TICKS, RandomSource.create(SCATTER_SEED));

        int slung = launches.pendingCount() - queuedBefore;
        helper.assertTrue(slung == FULL_CHARGE_KNIVES, String.format(SHOULD_SLING, FULL_CHARGE_KNIVES, slung));
        helper.assertTrue(husks.stream().allMatch(husk -> husk.getHealth() == husk.getMaxHealth()), STRUCK_AT_ONCE);
        helper.succeedWhen(() -> {
            long struck = husks.stream().filter(husk -> husk.getHealth() < husk.getMaxHealth()).count();
            helper.assertTrue(struck == husks.size(), String.format(SHOULD_RAIN, struck, husks.size()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }
}
