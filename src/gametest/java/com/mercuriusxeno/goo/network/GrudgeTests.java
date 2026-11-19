package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
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

/**
 * Gametests for nether's Grudge: a wounded caster releases it through the
 * charge payload, the glove's own path, and the mobs it reaches lose the
 * health the caster is missing.
 * grudge-is-a-charged-crimson-nova-dealing-your-missing-health
 */
@RegistersGameTests
public final class GrudgeTests {
    private static final String GRUDGE_RINGS_OUT_MISSING_HEALTH = "grudge_rings_out_the_casters_missing_health";

    private static final Identifier NETHER_GRUDGE = Identifier.parse("goo:nether_grudge");
    private static final BlockPos CASTER_POS = new BlockPos(1, 1, 1);
    /** About one and a half blocks from the caster, inside even a short charge's ring. */
    private static final BlockPos NEAR_POS = new BlockPos(2, 1, 2);
    /** About four blocks from the caster, past a short charge's ring and inside a full one's. */
    private static final BlockPos FAR_POS = new BlockPos(4, 1, 4);
    /** The caster's health once wounded, well short of a player's twenty. */
    private static final float WOUNDED_HEALTH = 6f;
    /** Ticks of a short hold, a tenth of a full charge. */
    private static final int SHORT_HOLD_TICKS = 3;
    private static final int SETTLE_TICKS = 1;
    /** Grudges the caster pays for, with one over. */
    private static final int GRUDGES_PAID = 3;
    private static final float EPSILON = 0.01f;
    private static final String ABILITY_REQUIRED = "Ability registry must hold nether_grudge";
    private static final String SHOULD_LOSE_MISSING =
            "The %s zombie should lose the caster's missing health %s, stands %s of %s";
    private static final String SHOULD_KEEP_ITS_OWN = "The far zombie should keep its health past a short ring, stands %s";
    private static final String GRUDGE_SPARES_OWN_MINIONS = "grudge_spares_the_casters_own_minions";
    /** About one and a half blocks from the caster on the near zombie's other side, inside a short charge's ring. */
    private static final BlockPos MINION_POS = new BlockPos(0, 1, 2);
    private static final String SHOULD_SPARE_THE_MINION = "The caster's charmed zombie should keep its health, stands %s";

    private GrudgeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GRUDGE_RINGS_OUT_MISSING_HEALTH,
                GrudgeTests::grudgeRingsOutTheCastersMissingHealth);
        GooTestFunctions.registerTest(registrar, GRUDGE_SPARES_OWN_MINIONS, GrudgeTests::grudgeSparesTheCastersOwnMinions);
    }

    /**
     * A caster wounded to six of twenty releases a short Grudge over two
     * zombies beside them, one charmed by the caster: the charmed zombie keeps
     * its health and the other loses the fourteen the caster is missing.
     * heal-and-decay-never-harm-their-caster
     * drain-skips-your-rallied-and-charmed-mobs
     *
     * @param helper the gametest helper
     */
    public static void grudgeSparesTheCastersOwnMinions(GameTestHelper helper) {
        Mob stranger = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, NEAR_POS);
        Mob minion = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MINION_POS);
        ServerPlayer caster = caster(helper);
        caster.setHealth(WOUNDED_HEALTH);
        minion.setData(GooAttachments.CHARMED, new Charmed(caster.getUUID()));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float missing = caster.getMaxHealth() - caster.getHealth();
            release(caster, SHORT_HOLD_TICKS);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(minion.getHealth() == minion.getMaxHealth(),
                    String.format(SHOULD_SPARE_THE_MINION, minion.getHealth()));
            assertLostMissing(helper, "unallied", stranger, stranger.getHealth(), missing);
            helper.succeed();
        });
    }

    /**
     * A caster wounded to six of twenty releases a short Grudge: the near
     * zombie loses the fourteen the caster is missing and the far zombie
     * keeps its own; a full Grudge then reaches the far zombie too.
     *
     * @param helper the gametest helper
     */
    public static void grudgeRingsOutTheCastersMissingHealth(GameTestHelper helper) {
        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, NEAR_POS);
        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FAR_POS);
        ServerPlayer caster = caster(helper);
        caster.setHealth(WOUNDED_HEALTH);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float missing = caster.getMaxHealth() - caster.getHealth();
            release(caster, SHORT_HOLD_TICKS);
            float nearAfterShort = near.getHealth();
            float farAfterShort = far.getHealth();
            release(caster, grudge(helper).delivery().charge().maxTicks());
            float farAfterFull = far.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            assertLostMissing(helper, "near", near, nearAfterShort, missing);
            helper.assertTrue(farAfterShort == far.getMaxHealth(), String.format(SHOULD_KEEP_ITS_OWN, farAfterShort));
            assertLostMissing(helper, "far", far, farAfterFull, missing);
            helper.succeed();
        });
    }

    private static void assertLostMissing(GameTestHelper helper, String which, Mob mob, float health, float missing) {
        helper.assertTrue(Math.abs(mob.getMaxHealth() - missing - health) < EPSILON,
                String.format(SHOULD_LOSE_MISSING, which, missing, health, mob.getMaxHealth()));
    }

    private static AbilityDefinition grudge(GameTestHelper helper) {
        AbilityDefinition grudge = AbilityRegistry.of(helper.getLevel()).getAbility(NETHER_GRUDGE);
        helper.assertTrue(grudge != null, ABILITY_REQUIRED);
        return grudge;
    }

    private static void release(ServerPlayer caster, int heldTicks) {
        GooThrowHandler.releaseCharge(caster,
                new GooChargePayload(GooTypes.id(GooTypes.NETHER), NETHER_GRUDGE.toString(), heldTicks));
    }

    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.NETHER);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        AbilityDefinition grudge = grudge(helper);
        int cost = grudge.cost().in(GooValueTable.EMPTY);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.NETHER, GRUDGES_PAID * cost));
        KnownRecipes.teachRequires(player, grudge);
        return player;
    }
}
