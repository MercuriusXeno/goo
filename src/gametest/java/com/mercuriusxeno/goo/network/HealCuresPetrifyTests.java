package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.petrify.PetrifyEvents;
import com.mercuriusxeno.goo.ability.petrify.Statues;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
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
    private static final String HEAL_WAKES_STATUE = "heal_wakes_a_statue_into_its_mob";
    private static final String HEAL_DRAINS_WOKEN_STONE = "heal_drains_the_stone_off_a_woken_mob";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HEAL_DRAINS_IN_CONE, HealCuresPetrifyTests::healDrainsTheGaugeInItsCone);
        GooTestFunctions.registerTest(registrar, HEAL_EMPTIES_NEARLY_FULL, HealCuresPetrifyTests::healEmptiesANearlyFullGauge);
        GooTestFunctions.registerTest(registrar, HEAL_WAKES_STATUE, HealCuresPetrifyTests::healWakesAStatueIntoItsMob);
        GooTestFunctions.registerTest(registrar, HEAL_DRAINS_WOKEN_STONE, HealCuresPetrifyTests::healDrainsTheStoneOffAWokenMob);
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
    /** A yaw no zombie faces by chance, so the woken zombie facing it faces the statue's way. */
    private static final float STATUE_YAW = 37f;
    private static final float YAW_TOLERANCE = 0.01f;
    /** The tick the test turns its zombie to a statue, and the tick Heal's stream first reaches it. */
    private static final int ENCASE_TICK = 1;
    private static final int WAKE_TICK = 2;
    /** Held ticks after the wake, inside the 200 the fog's own drain takes over a full gauge. */
    private static final int DRAIN_AFTER_WAKE_TICKS = 100;
    private static final String SHOULD_WAKE_ONE = "Heal should wake the statue into one zombie in its cell, found %d";
    private static final String SHOULD_FACE = "The woken zombie should face the statue's yaw %s, faces %s";
    private static final String SHOULD_BE_BABY = "The woken zombie should be a baby as its statue was";
    private static final String SHOULD_BE_ALONE =
            "The woken zombie should be the only entity the statue leaves, the bay holds %s";
    private static final String SHOULD_WAKE_NEARLY_FULL =
            "The woken zombie's gauge should stand above zero and below full on waking, reads %s";
    private static final String SHOULD_WAKE_SLOWED = "The woken zombie should wear the petrify slow on waking";
    private static final String SHOULD_DRAIN_WOKEN =
            "Heal held on the woken zombie should empty its gauge within %d ticks, it reads %s";

    private HealCuresPetrifyTests() {
    }

    /**
     * A baby zombie turned to face an odd yaw becomes a statue, and Heal's
     * stream reaches it once: on that tick one baby zombie stands in the
     * statue's cell facing that yaw, the cell holds air, and nothing else the
     * statue would drop lies in the bay.
     *
     * @param helper the gametest helper
     */
    public static void healWakesAStatueIntoItsMob(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper, TARGET_POS);
        zombie.setBaby(true);
        faceStatueYaw(zombie);
        ServerPlayer player = healer(helper);
        helper.runAfterDelay(ENCASE_TICK, () -> Statues.encase(helper.getLevel(), zombie, null));
        helper.runAfterDelay(WAKE_TICK, () -> {
            GooStreamHandler.streamTick(player, healTick(player));
            helper.getLevel().getServer().getPlayerList().remove(player);
            Mob woken = wokenZombie(helper);
            helper.assertTrue(Math.abs(woken.yBodyRot - STATUE_YAW) < YAW_TOLERANCE,
                    String.format(SHOULD_FACE, STATUE_YAW, woken.yBodyRot));
            helper.assertTrue(woken.isBaby(), SHOULD_BE_BABY);
            helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
            List<Entity> left = helper.getLevel().getEntities((Entity) null, helper.getBounds(), entity -> true);
            helper.assertTrue(left.equals(List.of(woken)), String.format(SHOULD_BE_ALONE, left));
            helper.succeed();
        });
    }

    /**
     * A zombie turned to a statue and woken by Heal's stream stands nearly
     * stone and slowed on the waking tick; Heal held on it empties its gauge
     * inside the fog's own 200-tick drain.
     *
     * @param helper the gametest helper
     */
    public static void healDrainsTheStoneOffAWokenMob(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper, TARGET_POS);
        ServerPlayer player = healer(helper);
        helper.runAfterDelay(ENCASE_TICK, () -> Statues.encase(helper.getLevel(), zombie, null));
        Mob[] woken = new Mob[1];
        helper.runAfterDelay(WAKE_TICK, () -> {
            GooStreamHandler.streamTick(player, healTick(player));
            woken[0] = wokenZombie(helper);
            Petrification waking = woken[0].getData(GooAttachments.PETRIFICATION);
            helper.assertTrue(waking.started() && !waking.full(), String.format(SHOULD_WAKE_NEARLY_FULL, waking));
            helper.assertTrue(woken[0].getAttribute(Attributes.MOVEMENT_SPEED).getModifier(SLOW_ID) != null,
                    SHOULD_WAKE_SLOWED);
        });
        hold(helper, player, WAKE_TICK + 1, DRAIN_AFTER_WAKE_TICKS - 1);
        helper.runAfterDelay(WAKE_TICK + DRAIN_AFTER_WAKE_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            Petrification now = woken[0].getData(GooAttachments.PETRIFICATION);
            helper.assertTrue(now.equals(Petrification.NONE),
                    String.format(SHOULD_DRAIN_WOKEN, DRAIN_AFTER_WAKE_TICKS, now));
            helper.succeed();
        });
    }

    /**
     * The one zombie standing in the target cell, failing the test where the
     * cell holds none or several.
     *
     * @param helper the gametest helper
     * @return the zombie
     */
    private static Mob wokenZombie(GameTestHelper helper) {
        List<Zombie> inCell = helper.getLevel().getEntitiesOfClass(Zombie.class,
                new AABB(helper.absolutePos(TARGET_POS)), Zombie::isAlive);
        helper.assertTrue(inCell.size() == 1, String.format(SHOULD_WAKE_ONE, inCell.size()));
        return inCell.getFirst();
    }

    private static void faceStatueYaw(Mob mob) {
        mob.setYRot(STATUE_YAW);
        mob.setYBodyRot(STATUE_YAW);
        mob.setYHeadRot(STATUE_YAW);
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
        GooStreamPayload tick = healTick(player);
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(from + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    /**
     * One tick of Heal's stream from where the player stands; a test calls
     * it inside the same scheduled task as the assertions on its tick, since
     * tasks scheduled on one tick run in no fixed order.
     *
     * @param player the streaming player
     * @return the stream tick's payload
     */
    private static GooStreamPayload healTick(ServerPlayer player) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY), JELLY_HEAL.toString(),
                player.getEyePosition(), player.getEyePosition());
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
