package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.PlatformSpiral;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Void's Outsider: held for its charge it carries the caster
 * into their room in goo:pocket, standing on the voidstone; the room's door
 * takes them back to the exact place they cast from; the door and voidstone
 * stand again when removed; and a player falling below the floor is set back
 * on it unharmed
 * (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform).
 */
@RegistersGameTests
public final class OutsiderTests {
    private static final String OUTSIDER_CARRIES_IN_AND_THE_DOOR_RETURNS = "outsider_carries_in_and_the_door_returns";
    private static final String OUTSIDER_DOOR_AND_VOIDSTONE_RESPAWN = "outsider_door_and_voidstone_respawn";
    private static final String OUTSIDER_VOID_CATCHES_THE_FALLER = "outsider_void_catches_the_faller";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, OUTSIDER_CARRIES_IN_AND_THE_DOOR_RETURNS,
                OutsiderTests::outsiderCarriesInAndTheDoorReturns);
        GooTestFunctions.registerTest(registrar, OUTSIDER_DOOR_AND_VOIDSTONE_RESPAWN,
                OutsiderTests::doorAndVoidstoneRespawn);
        GooTestFunctions.registerTest(registrar, OUTSIDER_VOID_CATCHES_THE_FALLER, OutsiderTests::voidCatchesTheFaller);
        GooTestFunctions.registerTest(registrar, OUTSIDER_PLATFORM_SPIRALS_WHILE_HELD,
                OutsiderTests::platformSpiralsWhileHeldInside);
    }

    private static final String OUTSIDER_PLATFORM_SPIRALS_WHILE_HELD = "outsider_platform_spirals_while_held_inside";
    /** Ticks the caster keeps holding once inside. */
    private static final int HELD_INSIDE = 20;
    /**
     * Cells twenty fed ticks grow: at 50 mB fed a tick and each cell costing 50 mB more than the last,
     * cells cost 1, 2, 3, 4 and 5 ticks of feed, 15 in all, and the sixth's 6 more would pass 20.
     */
    private static final int CELLS_GROWN = 5;
    private static final int VOID_FOR_GROWTH = 20 * GooStacks.THOUSAND;
    private static final String SHOULD_GROW = "Cell %d at %s should be grown voidstone, %s stands there";
    private static final String SHOULD_WAIT = "Cell %d at %s should not be grown yet, %s stands there";
    private static final String SHOULD_KEEP_COUNT = "The room should keep %d platform cells, keeps %d";

    /**
     * A caster held on inside their room grows the platform one cell at a
     * time along the spiral, each cell taking longer than the last, and the
     * room keeps the count it reached.
     *
     * @param helper the gametest helper
     */
    public static void platformSpiralsWhileHeldInside(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, VOID_FOR_GROWTH));
        holdFor(helper, caster, CHARGE_TICKS + HELD_INSIDE);
        helper.runAfterDelay(CHARGE_TICKS + HELD_INSIDE + 1, () -> {
            ServerLevel pocket = helper.getLevel().getServer().getLevel(OutsiderRooms.POCKET);
            Integer room = pocket == null ? null : OutsiderRooms.roomOf(pocket, caster.getUUID());
            helper.assertTrue(room != null && caster.level() == pocket,
                    String.format(SHOULD_ENTER, "", caster.position(), caster.getYRot(),
                            caster.level().dimension().identifier()));
            int grownTo = PlatformSpiral.START_CELLS + CELLS_GROWN;
            for (int index = PlatformSpiral.START_CELLS; index <= grownTo; index++) {
                PlatformSpiral.Cell cell = PlatformSpiral.cellAt(index);
                BlockPos at = OutsiderRooms.voidstoneOf(room).offset(cell.x(), 0, cell.z());
                if (index < grownTo) {
                    helper.assertTrue(pocket.getBlockState(at).is(GooBlocks.VOIDSTONE.get()),
                            String.format(SHOULD_GROW, index, at, pocket.getBlockState(at)));
                } else {
                    helper.assertTrue(pocket.getBlockState(at).isAir(),
                            String.format(SHOULD_WAIT, index, at, pocket.getBlockState(at)));
                }
            }
            int kept = OutsiderRooms.platformCells(pocket, caster.getUUID());
            helper.assertTrue(kept == grownTo, String.format(SHOULD_KEEP_COUNT, grownTo, kept));
            helper.succeed();
        });
    }

    private static final Identifier OUTSIDER = Identifier.parse("goo:void_outsider");
    /** The hold Outsider asks before it carries the caster in: one charge. */
    private static final int CHARGE_TICKS = 20;
    private static final float CAST_YAW = 37f;
    private static final double FALL_BELOW_FLOOR = 20;
    private static final double FALL_ASIDE = 6;
    private static final float FALLEN = 30f;
    /** The yaw facing north, toward the door from the arrival cell south of it. */
    private static final float FACING_DOOR = 180f;
    private static final String SHOULD_WAIT_FOR_THE_CHARGE = "A hold short of its charge should leave the caster in %s";
    private static final String SHOULD_ENTER = "The caster should stand south of the door at %s facing it in goo:pocket, stands at %s facing %s in %s";
    private static final String SHOULD_STAND_DOOR = "The room should hold a dimension door at %s, holds %s";
    private static final String SHOULD_RETURN = "The door should return the caster to %s facing %s in %s, they stand at %s facing %s in %s";
    private static final String SHOULD_RESPAWN = "The %s at %s should stand again, %s stands there";
    private static final String SHOULD_CATCH = "The faller should stand south of the door at %s, stands at %s";
    private static final String SHOULD_BE_UNHARMED = "The faller should keep %.1f health with no fall built up, has %.1f and %.1f";

    private OutsiderTests() {
    }

    /**
     * A caster holding Outsider stays put through a hold short of its charge;
     * the charge's tick carries them into their room in goo:pocket, onto the
     * voidstone south of the door standing on the center, facing it; using
     * the door sets them back at the exact place and facing they cast from.
     *
     * @param helper the gametest helper
     */
    public static void outsiderCarriesInAndTheDoorReturns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.setYRot(CAST_YAW);
        Vec3 castFrom = caster.position();
        holdFor(helper, caster, CHARGE_TICKS - 1);
        helper.runAfterDelay(CHARGE_TICKS - 1, () -> helper.assertTrue(caster.level() == level,
                String.format(SHOULD_WAIT_FOR_THE_CHARGE, caster.level().dimension().identifier())));
        holdAt(helper, caster, CHARGE_TICKS);
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            int room = assertEntered(helper, caster);
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos door = doorOf(room);
            helper.assertTrue(pocket.getBlockState(door).is(GooBlocks.DIMENSION_DOOR.get()),
                    String.format(SHOULD_STAND_DOOR, door, pocket.getBlockState(door)));
            pocket.getBlockState(door).useWithoutItem(pocket, caster,
                    new BlockHitResult(Vec3.atCenterOf(door), Direction.UP, door, false));
            helper.assertTrue(caster.level() == level && caster.position().equals(castFrom)
                            && caster.getYRot() == CAST_YAW,
                    String.format(SHOULD_RETURN, castFrom, CAST_YAW, level.dimension().identifier(),
                            caster.position(), caster.getYRot(), caster.level().dimension().identifier()));
            helper.succeed();
        });
    }

    /**
     * Once the caster stands in their room, the center voidstone, a ring
     * cell of the platform and the door removed stand again by the next
     * restore check.
     *
     * @param helper the gametest helper
     */
    public static void doorAndVoidstoneRespawn(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, caster, CHARGE_TICKS);
        int[] room = new int[1];
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            room[0] = assertEntered(helper, caster);
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos center = OutsiderRooms.voidstoneOf(room[0]);
            pocket.setBlockAndUpdate(doorOf(room[0]), Blocks.AIR.defaultBlockState());
            pocket.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
            pocket.setBlockAndUpdate(center.east(), Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(CHARGE_TICKS + 2 + OutsiderRooms.RESTORE_INTERVAL, () -> {
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos center = OutsiderRooms.voidstoneOf(room[0]);
            BlockPos door = doorOf(room[0]);
            for (BlockPos voidstone : List.of(center, center.east())) {
                helper.assertTrue(pocket.getBlockState(voidstone).is(GooBlocks.VOIDSTONE.get()),
                        String.format(SHOULD_RESPAWN, "voidstone", voidstone, pocket.getBlockState(voidstone)));
            }
            helper.assertTrue(pocket.getBlockState(door).is(GooBlocks.DIMENSION_DOOR.get()),
                    String.format(SHOULD_RESPAWN, "door", door, pocket.getBlockState(door)));
            helper.succeed();
        });
    }

    /**
     * A caster in their room who falls off the platform, well below the
     * floor with a long fall built up, stands back south of the door after
     * their next tick with their health whole and no fall left to land.
     *
     * @param helper the gametest helper
     */
    public static void voidCatchesTheFaller(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, caster, CHARGE_TICKS);
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            int room = assertEntered(helper, caster);
            Vec3 arrival = arrivalOf(room);
            float health = caster.getHealth();
            caster.setPos(arrival.x + FALL_ASIDE, arrival.y - FALL_BELOW_FLOOR, arrival.z);
            caster.fallDistance = FALLEN;
            caster.doTick();
            helper.assertTrue(caster.position().equals(arrival),
                    String.format(SHOULD_CATCH, arrival, caster.position()));
            helper.assertTrue(caster.getHealth() == health && caster.fallDistance == 0,
                    String.format(SHOULD_BE_UNHARMED, health, caster.getHealth(), caster.fallDistance));
            helper.succeed();
        });
    }

    private static void holdFor(GameTestHelper helper, ServerPlayer caster, int ticks) {
        for (int held = 1; held <= ticks; held++) {
            holdAt(helper, caster, held);
        }
    }

    private static void holdAt(GameTestHelper helper, ServerPlayer caster, int tick) {
        helper.runAfterDelay(tick, () -> GooStreamHandler.streamTick(caster,
                new GooStreamPayload(GooTypes.id(GooTypes.VOID), OUTSIDER.toString(), caster.getEyePosition(),
                        caster.getEyePosition().add(caster.getLookAngle()), caster.blockPosition().below(),
                        GooStreamPayload.NO_FACE)));
    }

    private static int assertEntered(GameTestHelper helper, ServerPlayer caster) {
        ServerLevel pocket = helper.getLevel().getServer().getLevel(OutsiderRooms.POCKET);
        Integer room = pocket == null ? null : OutsiderRooms.roomOf(pocket, caster.getUUID());
        Vec3 arrival = room == null ? Vec3.ZERO : arrivalOf(room);
        helper.assertTrue(room != null && caster.level() == pocket && caster.position().equals(arrival)
                        && caster.getYRot() == FACING_DOOR,
                String.format(SHOULD_ENTER, arrival, caster.position(), caster.getYRot(),
                        caster.level().dimension().identifier()));
        return room;
    }

    /** The door's block: standing on the center voidstone. */
    private static BlockPos doorOf(int room) {
        return OutsiderRooms.voidstoneOf(room).above();
    }

    /** Where a player arrives: on the voidstone just south of the door. */
    private static Vec3 arrivalOf(int room) {
        return Vec3.atBottomCenterOf(OutsiderRooms.voidstoneOf(room).south().above());
    }
}
