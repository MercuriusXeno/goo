package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.hole.HoleOpening;
import com.mercuriusxeno.goo.ability.hole.PortableHole;
import com.mercuriusxeno.goo.ability.hole.PortableHoles;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametests for the sounds Void's hole plays, read from the sound packets a
 * player standing beside it is sent: the opening wub pitched up at the mouth's
 * middle, and the reversed wub pitched down when a punch closes it or its time
 * runs out, each in place of the ender chest; a second cast replacing the hole
 * and the owner leaving its dimension close it silently.
 * decision hole-opens-with-the-outsider-wub-pitched-up
 * decision hole-closes-with-the-outsider-wub-reversed-and-pitched-down
 */
@RegistersGameTests
public final class HoleSoundTests {

    private static final String OPENING_WUBS_PITCHED_UP = "hole_opening_wubs_pitched_up";
    private static final String PUNCH_CLOSES_WITH_THE_REVERSED_WUB = "hole_punch_closes_with_the_reversed_wub";
    private static final String TIME_RUN_OUT_CLOSES_WITH_THE_REVERSED_WUB =
            "hole_time_run_out_closes_with_the_reversed_wub";
    private static final String SECOND_CAST_CLOSES_SILENTLY = "hole_second_cast_closes_silently";
    private static final String LEAVING_ITS_DIMENSION_CLOSES_SILENTLY = "hole_leaving_its_dimension_closes_silently";
    private static final Identifier ENDER_CHEST_OPEN = Identifier.withDefaultNamespace("block.ender_chest.open");
    private static final Identifier ENDER_CHEST_CLOSE = Identifier.withDefaultNamespace("block.ender_chest.close");
    private static final float OPENING_PITCH = 1.375F;
    private static final float CLOSING_PITCH = 0.625F;
    /** A floor block, relative, a second cast opens on while the first hole stands. */
    private static final BlockPos SECOND_FLOOR = new BlockPos(3, 0, 3);
    /** The ticks a timed hole stands, short so the test waits it out quickly. */
    private static final int SHORT_LIFETIME = 20;
    /** Ticks past a closing a test waits, so the closing has played. */
    private static final int SETTLE = 5;
    /**
     * How far from a mouth's middle a sound counts as this test's: past the
     * punched corner's face, short of a neighbouring bay's hole.
     */
    private static final double NEARBY = 3.0;

    private HoleSoundTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, OPENING_WUBS_PITCHED_UP, HoleSoundTests::openingWubsPitchedUp);
        GooTestFunctions.registerTest(registrar, PUNCH_CLOSES_WITH_THE_REVERSED_WUB,
                HoleSoundTests::punchClosesWithTheReversedWub);
        GooTestFunctions.registerTest(registrar, TIME_RUN_OUT_CLOSES_WITH_THE_REVERSED_WUB,
                HoleSoundTests::timeRunOutClosesWithTheReversedWub);
        GooTestFunctions.registerTest(registrar, SECOND_CAST_CLOSES_SILENTLY, HoleSoundTests::secondCastClosesSilently);
        GooTestFunctions.registerTest(registrar, LEAVING_ITS_DIMENSION_CLOSES_SILENTLY,
                HoleSoundTests::leavingItsDimensionClosesSilently);
    }

    /**
     * Opening the hole plays the opening sound once at the mouth's middle at
     * pitch 1.375, and no ender chest opening.
     *
     * @param helper the gametest helper
     */
    public static void openingWubsPitchedUp(GameTestHelper helper) {
        ServerPlayer owner = ownerOnTheFloor(helper);
        PacketRecorder heard = PacketRecorder.attachTo(owner);
        Vec3 middle = openOn(helper, owner, HoleTests.FLOOR, HoleTests.LIFETIME);
        List<ClientboundSoundPacket> opening = heardNear(heard, middle,GooSoundIds.HOLE_OPEN);
        List<ClientboundSoundPacket> chest = heardNear(heard, middle,ENDER_CHEST_OPEN);
        helper.getLevel().getServer().getPlayerList().remove(owner);
        assertPlayedOnceAt(helper, opening, middle, OPENING_PITCH, "Opening the hole");
        helper.assertTrue(chest.isEmpty(), "Opening the hole should play no ender chest, plays " + describe(chest));
        helper.succeed();
    }

    /**
     * A punch on a corner of a 3x3 hole's footprint closes it with the closing
     * sound once at pitch 0.625, and no ender chest closing.
     *
     * @param helper the gametest helper
     */
    public static void punchClosesWithTheReversedWub(GameTestHelper helper) {
        ServerPlayer owner = HoleTests.caster(helper, 1);
        HoleTests.layFloor(helper, cell -> true);
        PacketRecorder heard = PacketRecorder.attachTo(owner);
        Vec3 middle = openOn(helper, owner, HoleTests.FLOOR, HoleTests.LIFETIME);
        CommonHooks.onLeftClickBlock(owner, helper.absolutePos(HoleTests.CORNER), Direction.UP,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        helper.runAfterDelay(1, () -> {
            boolean stands = PortableHoles.of(owner.getUUID()).isPresent();
            List<ClientboundSoundPacket> closing = heardNear(heard, middle,GooSoundIds.HOLE_CLOSE);
            List<ClientboundSoundPacket> chest = heardNear(heard, middle,ENDER_CHEST_CLOSE);
            helper.getLevel().getServer().getPlayerList().remove(owner);
            helper.assertFalse(stands, "A punch on the hole's corner should close it");
            helper.assertTrue(closing.size() == 1 && closing.getFirst().getPitch() == CLOSING_PITCH,
                    "Punching the hole closed should play its closing sound once at pitch " + CLOSING_PITCH
                            + ", plays " + describe(closing));
            helper.assertTrue(chest.isEmpty(), "Punching the hole closed should play no ender chest, plays "
                    + describe(chest));
            helper.succeed();
        });
    }

    /**
     * A hole whose time runs out plays the closing sound once at the mouth's
     * middle at pitch 0.625.
     *
     * @param helper the gametest helper
     */
    public static void timeRunOutClosesWithTheReversedWub(GameTestHelper helper) {
        ServerPlayer owner = ownerOnTheFloor(helper);
        PacketRecorder heard = PacketRecorder.attachTo(owner);
        Vec3 middle = openOn(helper, owner, HoleTests.FLOOR, SHORT_LIFETIME);
        helper.runAfterDelay(SHORT_LIFETIME + SETTLE, () -> {
            boolean stands = PortableHoles.of(owner.getUUID()).isPresent();
            List<ClientboundSoundPacket> closing = heardNear(heard, middle,GooSoundIds.HOLE_CLOSE);
            helper.getLevel().getServer().getPlayerList().remove(owner);
            helper.assertFalse(stands, "The hole should close once its time runs out");
            assertPlayedOnceAt(helper, closing, middle, CLOSING_PITCH, "A hole whose time runs out");
            helper.succeed();
        });
    }

    /**
     * A second cast while the first hole stands replaces it playing the
     * opening sound alone: two openings heard, and no closing.
     *
     * @param helper the gametest helper
     */
    public static void secondCastClosesSilently(GameTestHelper helper) {
        ServerPlayer owner = ownerOnTheFloor(helper);
        helper.setBlock(SECOND_FLOOR, Blocks.STONE);
        PacketRecorder heard = PacketRecorder.attachTo(owner);
        Vec3 middle = openOn(helper, owner, HoleTests.FLOOR, HoleTests.LIFETIME);
        openOn(helper, owner, SECOND_FLOOR, HoleTests.LIFETIME);
        helper.runAfterDelay(SETTLE, () -> {
            List<ClientboundSoundPacket> opening = heardNear(heard, middle,GooSoundIds.HOLE_OPEN);
            List<ClientboundSoundPacket> closing = heardNear(heard, middle,GooSoundIds.HOLE_CLOSE);
            helper.getLevel().getServer().getPlayerList().remove(owner);
            helper.assertTrue(opening.size() == 2, "Two casts should play the opening sound twice, play "
                    + describe(opening));
            helper.assertTrue(closing.isEmpty(), "A second cast replacing the hole should play no closing sound, "
                    + "plays " + describe(closing));
            helper.succeed();
        });
    }

    /**
     * The owner gone to the Nether closes the hole with no closing sound,
     * heard by an onlooker standing beside it, who stays in its level.
     *
     * @param helper the gametest helper
     */
    public static void leavingItsDimensionClosesSilently(GameTestHelper helper) {
        ServerPlayer owner = ownerOnTheFloor(helper);
        ServerPlayer onlooker = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        PacketRecorder heard = PacketRecorder.attachTo(onlooker);
        Vec3 middle = openOn(helper, owner, HoleTests.FLOOR, HoleTests.LIFETIME);
        helper.runAfterDelay(1, () -> {
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            owner.teleportTo(nether, 0.5, 64, 0.5, Set.of(), 0F, 0F, false);
        });
        helper.runAfterDelay(1 + SETTLE, () -> {
            Optional<PortableHole> hole = PortableHoles.of(owner.getUUID());
            List<ClientboundSoundPacket> closing = heardNear(heard, middle,GooSoundIds.HOLE_CLOSE);
            helper.getLevel().getServer().getPlayerList().remove(owner);
            helper.getLevel().getServer().getPlayerList().remove(onlooker);
            helper.assertTrue(hole.isEmpty(), "Leaving the hole's dimension should close it, stands " + hole);
            helper.assertTrue(closing.isEmpty(), "A hole its owner left should close with no closing sound, plays "
                    + describe(closing));
            helper.succeed();
        });
    }

    /** A Void player in the bay, with stone laid at the floor block a hole opens on. */
    private static ServerPlayer ownerOnTheFloor(GameTestHelper helper) {
        helper.setBlock(HoleTests.FLOOR, Blocks.STONE);
        return SelfDeliveryTests.invoker(helper, GooTypes.VOID);
    }

    /**
     * Opens the owner's hole on a floor block's top face, uncast, since a cast
     * leaves synced attachments a mock connection refuses on a level change.
     *
     * @return the middle of the hole's mouth
     */
    private static Vec3 openOn(GameTestHelper helper, ServerPlayer owner, BlockPos floor, int lifetime) {
        HoleOpening.open(helper.getLevel(), owner, helper.absolutePos(floor), Direction.UP, lifetime,
                HoleTests.REFRESH);
        Optional<PortableHole> hole = PortableHoles.of(owner.getUUID());
        helper.assertTrue(hole.isPresent(), "The hole should stand once opened");
        return hole.get().mouth().getCenter();
    }

    private static void assertPlayedOnceAt(GameTestHelper helper, List<ClientboundSoundPacket> sounds, Vec3 middle,
                                           float pitch, String what) {
        helper.assertTrue(sounds.size() == 1 && HeardSounds.playedAt(sounds.getFirst(), middle)
                        && sounds.getFirst().getPitch() == pitch,
                what + " should play its sound once at the mouth's middle " + middle + " at pitch " + pitch
                        + ", plays " + describe(sounds));
    }

    /**
     * The sounds of one event a player heard within a few blocks of a mouth's
     * middle, so a hole opened in a neighbouring bay goes uncounted.
     */
    private static List<ClientboundSoundPacket> heardNear(PacketRecorder heard, Vec3 middle, Identifier sound) {
        return HeardSounds.of(heard, sound).stream()
                .filter(packet -> middle.closerThan(new Vec3(packet.getX(), packet.getY(), packet.getZ()), NEARBY))
                .toList();
    }

    /** Where and at what pitch each sound played, for a failure message. */
    private static String describe(List<ClientboundSoundPacket> sounds) {
        return HeardSounds.describe(sounds) + " at pitches "
                + sounds.stream().map(ClientboundSoundPacket::getPitch).toList();
    }
}
