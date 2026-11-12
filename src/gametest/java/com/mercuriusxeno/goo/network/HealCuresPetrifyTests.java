package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.petrify.PetrifyEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for jelly Heal on petrification: a held stream drains a penned
 * zombie's petrify gauge faster than the fog's own drain, and empties a
 * nearly full one, taking its slow with it
 * (decision heal-stream-drains-the-petrify-gauge).
 */
@RegistersGameTests
public final class HealCuresPetrifyTests {
    private static final String HEAL_DRAINS_IN_CONE = "heal_drains_the_petrify_gauge_in_its_cone";
    private static final String HEAL_EMPTIES_NEARLY_FULL = "heal_empties_a_nearly_full_petrify_gauge";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HEAL_DRAINS_IN_CONE, HealCuresPetrifyTests::healDrainsTheGaugeInItsCone);
        GooTestFunctions.registerTest(registrar, HEAL_EMPTIES_NEARLY_FULL, HealCuresPetrifyTests::healEmptiesANearlyFullGauge);
    }

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on a block the test lays. */
    private static final BlockPos TARGET_POS = STAND_POS.east(3);
    /** Beside the player, some seventy degrees off the cone's axis, so the stream never reaches it. */
    private static final BlockPos OUTSIDE_POS = STAND_POS.east().south(3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the zombie's middle three blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 12f;
    private static final int HELD_GOO = 6;
    /** A gauge halfway to a statue. */
    private static final float HALF_GAUGE = Petrification.FULL / 2;
    /** A gauge one short of a statue. */
    private static final float NEARLY_FULL_GAUGE = Petrification.FULL - 1;
    /** Held ticks inside the fog's own drain delay, so only the stream lowers a gauge. */
    private static final int SHORT_HOLD_TICKS = 10;
    /**
     * Held ticks well short of the 200 the fog's own drain takes over a full
     * gauge; the fog's drain alone leaves a nearly full gauge near seventy here.
     */
    private static final int EMPTYING_HOLD_TICKS = 80;
    private static final Identifier JELLY_HEAL = Identifier.parse("goo:jelly_heal");
    private static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "petrify_slow");
    private static final String ABILITY_REQUIRED = "Ability registry must hold jelly_heal";
    private static final String SHOULD_DRAIN_FASTER =
            "The streamed zombie's gauge should read below the unstreamed one's %s, reads %s";
    private static final String SHOULD_EMPTY = "Heal should empty a nearly full gauge within %d ticks, it reads %s";
    private static final String SHOULD_LOSE_SLOW = "A zombie whose gauge Heal emptied should wear no petrify slow";

    private HealCuresPetrifyTests() {
    }

    /**
     * Two zombies stand at the same half gauge, one in Heal's cone and one
     * beside the player: after a short hold the streamed one reads lower.
     *
     * @param helper the gametest helper
     */
    public static void healDrainsTheGaugeInItsCone(GameTestHelper helper) {
        Mob streamed = pennedZombie(helper, TARGET_POS);
        Mob unstreamed = standingZombie(helper, OUTSIDE_POS);
        petrifyPartway(helper, streamed, HALF_GAUGE);
        petrifyPartway(helper, unstreamed, HALF_GAUGE);
        ServerPlayer player = healer(helper);
        hold(helper, player, 1, SHORT_HOLD_TICKS);
        helper.runAfterDelay(SHORT_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float streamedGauge = streamed.getData(GooAttachments.PETRIFICATION).gauge();
            float unstreamedGauge = unstreamed.getData(GooAttachments.PETRIFICATION).gauge();
            helper.assertTrue(streamedGauge < unstreamedGauge,
                    String.format(SHOULD_DRAIN_FASTER, unstreamedGauge, streamedGauge));
            helper.succeed();
        });
    }

    /**
     * A zombie one short of a statue, slowed by its gauge, held in Heal's
     * stream: well inside the fog's own 200-tick drain its gauge reads empty
     * and its petrify slow is gone.
     *
     * @param helper the gametest helper
     */
    public static void healEmptiesANearlyFullGauge(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper, TARGET_POS);
        petrifyPartway(helper, zombie, NEARLY_FULL_GAUGE);
        ServerPlayer player = healer(helper);
        hold(helper, player, 1, EMPTYING_HOLD_TICKS);
        helper.runAfterDelay(EMPTYING_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            Petrification now = zombie.getData(GooAttachments.PETRIFICATION);
            helper.assertTrue(now.equals(Petrification.NONE), String.format(SHOULD_EMPTY, EMPTYING_HOLD_TICKS, now));
            helper.assertTrue(zombie.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(SLOW_ID) == null,
                    SHOULD_LOSE_SLOW);
            helper.succeed();
        });
    }

    /**
     * Sets a zombie's gauge as though the fog filled it this tick, and slows it to match.
     *
     * @param helper the gametest helper
     * @param zombie the zombie
     * @param gauge  the gauge it stands at
     */
    private static void petrifyPartway(GameTestHelper helper, Mob zombie, float gauge) {
        Petrification petrification = new Petrification(gauge, helper.getLevel().getGameTime());
        zombie.setData(GooAttachments.PETRIFICATION, petrification);
        PetrifyEvents.slowBy(zombie, petrification);
    }

    /**
     * Rings a cell with glass two high and stands a zombie in it; the stream
     * strikes the entities in its cone whatever stands between.
     *
     * @param helper the gametest helper
     * @param pos    the cell
     * @return the zombie
     */
    private static Mob pennedZombie(GameTestHelper helper, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(pos.relative(side), Blocks.GLASS);
            helper.setBlock(pos.relative(side).above(), Blocks.GLASS);
        }
        return standingZombie(helper, pos);
    }

    /**
     * Floors a cell with stone and stands a helmeted zombie there with no
     * will of its own; the bay's edge leaves no room to pen it.
     *
     * @param helper the gametest helper
     * @param pos    the cell
     * @return the zombie
     */
    private static Mob standingZombie(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * Streams Heal every tick of a hold.
     *
     * @param helper the gametest helper
     * @param player the streaming player
     * @param from   the first tick of the hold
     * @param ticks  how many ticks it lasts
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int from, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY), JELLY_HEAL.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(from + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer healer(GameTestHelper helper) {
        AbilityDefinition heal = AbilityRegistry.of(helper.getLevel()).getAbility(JELLY_HEAL);
        helper.assertTrue(heal != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_ZOMBIE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.JELLY, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, heal);
        return player;
    }
}
