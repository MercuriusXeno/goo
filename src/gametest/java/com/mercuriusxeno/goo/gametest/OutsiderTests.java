package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.PlatformSpiral;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
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
        GooTestFunctions.registerTest(registrar, OUTSIDER_POCKET_SHOWS_NO_SKY, OutsiderTests::pocketShowsNoSky);
        GooTestFunctions.registerTest(registrar, OUTSIDER_SHOWS_ITS_CHARGE_AND_GROWTH,
                OutsiderTests::showsItsChargeAndGrowth);
    }

    private static final String OUTSIDER_SHOWS_ITS_CHARGE_AND_GROWTH = "outsider_shows_its_charge_and_growth";
    /** Ticks held inside before the first grown cell's 13 mB is fed at 2 mB a tick, and a tick more. */
    private static final int HELD_TO_FIRST_CELL = 8;
    private static final String SHOULD_SHOW_CHARGE = "A hold short of its charge should send void motes, sent %d particle packets";
    private static final String SHOULD_CHIME = "A grown cell should chime, sounds sent %s";

    /**
     * A caster charging Outsider sees void motes gather before the charge
     * lands, and once inside, the first cell the hold grows chimes.
     *
     * @param helper the gametest helper
     */
    public static void showsItsChargeAndGrowth(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        PacketRecorder packets = PacketRecorder.attachTo(caster);
        holdFor(helper, caster, CHARGE_TICKS + HELD_TO_FIRST_CELL);
        helper.runAfterDelay(CHARGE_TICKS - 1, () -> {
            int motes = packets.sentOf(ClientboundLevelParticlesPacket.class).size();
            helper.assertTrue(motes > 0, String.format(SHOULD_SHOW_CHARGE, motes));
        });
        helper.runAfterDelay(CHARGE_TICKS + HELD_TO_FIRST_CELL + 1, () -> {
            List<String> sounds = packets.sentOf(ClientboundSoundPacket.class).stream()
                    .map(sound -> sound.getSound().value().location().toString()).toList();
            helper.assertTrue(sounds.contains(SoundEvents.AMETHYST_BLOCK_CHIME.location().toString()),
                    String.format(SHOULD_CHIME, sounds));
            helper.succeed();
        });
    }

    private static final String OUTSIDER_POCKET_SHOWS_NO_SKY = "outsider_pocket_shows_no_sky";
    private static final ResourceKey<Biome> POCKET_VOID = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Goo.MODID, "pocket_void"));
    private static final String SHOULD_DRAW_NO_SKY = "The pocket should draw no skybox, so no sun, draws %s";
    private static final String SHOULD_BE_VOID_BIOME = "The pocket's rooms should stand in goo:pocket_void, stand in %s";

    /**
     * The pocket dimension draws no skybox, so neither sun nor moon nor
     * stars, and its rooms stand in a biome of black sky and fog: nothing
     * above, around or below.
     *
     * @param helper the gametest helper
     */
    public static void pocketShowsNoSky(GameTestHelper helper) {
        ServerLevel pocket = helper.getLevel().getServer().getLevel(OutsiderRooms.POCKET);
        helper.assertTrue(pocket != null, "The server should hold the pocket dimension");
        helper.assertTrue(pocket.dimensionType().skybox() == DimensionType.Skybox.NONE,
                String.format(SHOULD_DRAW_NO_SKY, pocket.dimensionType().skybox()));
        Holder<Biome> biome = pocket.getBiome(OutsiderRooms.voidstoneOf(0));
        helper.assertTrue(biome.is(POCKET_VOID), String.format(SHOULD_BE_VOID_BIOME, biome.getRegisteredName()));
        helper.succeed();
    }

    private static final String OUTSIDER_PLATFORM_SPIRALS_WHILE_HELD = "outsider_platform_spirals_while_held_inside";
    /** Ticks the caster keeps holding once inside. */
    private static final int HELD_INSIDE = 40;
    /**
     * Cells forty fed ticks grow: at 2 mB fed a tick, 80 mB, and each cell costing 10 plus its
     * Manhattan distance from the center, the spiral's next cells at (2,-1), (2,0), (2,1), (2,2),
     * (1,2) and (0,2) cost 13, 12, 13, 14, 13 and 12, 77 in all, and (-1,2)'s 13 more would pass 80.
     */
    private static final int CELLS_GROWN = 6;
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
    private static final String SHOULD_STAND_DOOR = "The room should hold a dimension doorway at %s, holds %s";
    private static final String SHOULD_STAND_OPEN = "The doorway should stand alone, two tall, with open air at %s, %s stands there";
    private static final String SHOULD_RETURN = "The door should return the caster to %s facing %s in %s, they stand at %s facing %s in %s";
    private static final String SHOULD_RESPAWN = "The %s at %s should stand again, %s stands there";
    private static final String SHOULD_CATCH = "The faller should stand south of the door at %s, stands at %s";
    private static final String SHOULD_BE_UNHARMED = "The faller should keep %.1f health with no fall built up, has %.1f and %.1f";

    private OutsiderTests() {
    }

    /**
     * A caster holding Outsider stays put through a hold short of its charge;
     * the charge's tick carries them into their room in goo:pocket, onto the
     * voidstone south of the two-tall doorway standing alone on the center,
     * facing it; walking into the doorway sets them back at the exact
     * place and facing they cast from.
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
            for (BlockPos doorway : List.of(door, door.above())) {
                helper.assertTrue(pocket.getBlockState(doorway).is(GooBlocks.DIMENSION_DOOR.get()),
                        String.format(SHOULD_STAND_DOOR, doorway, pocket.getBlockState(doorway)));
            }
            for (BlockPos around : aroundDoorwayOf(room)) {
                helper.assertTrue(pocket.getBlockState(around).isAir(),
                        String.format(SHOULD_STAND_OPEN, around, pocket.getBlockState(around)));
            }
            TeleportTransition home = GooBlocks.DIMENSION_DOOR.get().getPortalDestination(pocket, caster, door);
            helper.assertTrue(home != null, String.format(SHOULD_STAND_DOOR, door, "no way home"));
            caster.teleport(home);
            helper.assertTrue(caster.level() == level && caster.position().equals(castFrom)
                            && caster.getYRot() == CAST_YAW,
                    String.format(SHOULD_RETURN, castFrom, CAST_YAW, level.dimension().identifier(),
                            caster.position(), caster.getYRot(), caster.level().dimension().identifier()));
            helper.succeed();
        });
    }

    /**
     * Once the caster stands in their room, the center voidstone, a ring
     * cell of the platform and both doorway cells removed stand again by
     * the next restore check.
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
            for (BlockPos removed : List.of(doorOf(room[0]), doorOf(room[0]).above(), center, center.east())) {
                pocket.setBlockAndUpdate(removed, Blocks.AIR.defaultBlockState());
            }
        });
        helper.runAfterDelay(CHARGE_TICKS + 2 + OutsiderRooms.RESTORE_INTERVAL, () -> {
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos center = OutsiderRooms.voidstoneOf(room[0]);
            BlockPos door = doorOf(room[0]);
            for (BlockPos voidstone : List.of(center, center.east())) {
                helper.assertTrue(pocket.getBlockState(voidstone).is(GooBlocks.VOIDSTONE.get()),
                        String.format(SHOULD_RESPAWN, "voidstone", voidstone, pocket.getBlockState(voidstone)));
            }
            for (BlockPos doorway : List.of(door, door.above())) {
                helper.assertTrue(pocket.getBlockState(doorway).is(GooBlocks.DIMENSION_DOOR.get()),
                        String.format(SHOULD_RESPAWN, "doorway", doorway, pocket.getBlockState(doorway)));
            }
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

    /** The doorway's lower block: standing on the center voidstone. */
    private static BlockPos doorOf(int room) {
        return OutsiderRooms.voidstoneOf(room).above();
    }

    /** The open air the doorway stands in: either side of both its cells, and over it. */
    private static List<BlockPos> aroundDoorwayOf(int room) {
        BlockPos door = doorOf(room);
        return List.of(door.east(), door.east().above(), door.west(), door.west().above(), door.above(2));
    }

    /** Where a player arrives: on the voidstone just south of the door. */
    private static Vec3 arrivalOf(int room) {
        return Vec3.atBottomCenterOf(OutsiderRooms.voidstoneOf(room).south().above());
    }
}
