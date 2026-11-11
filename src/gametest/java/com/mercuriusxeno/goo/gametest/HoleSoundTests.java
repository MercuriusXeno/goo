package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.hole.HoleOpening;
import com.mercuriusxeno.goo.ability.hole.PortableHole;
import com.mercuriusxeno.goo.ability.hole.PortableHoles;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for the sounds Void's hole plays, read from the sound packets its
 * owner, standing beside it, is sent: the opening wub pitched up at the
 * mouth's middle, in place of the ender chest.
 * decision hole-opens-with-the-outsider-wub-pitched-up
 */
@RegistersGameTests
public final class HoleSoundTests {

    private static final String OPENING_WUBS_PITCHED_UP = "hole_opening_wubs_pitched_up";
    private static final Identifier ENDER_CHEST_OPEN = Identifier.withDefaultNamespace("block.ender_chest.open");
    private static final float OPENING_PITCH = 1.375F;

    private HoleSoundTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, OPENING_WUBS_PITCHED_UP, HoleSoundTests::openingWubsPitchedUp);
    }

    /**
     * Opening the hole plays the opening sound once at the mouth's middle at
     * pitch 1.375, and no ender chest opening.
     *
     * @param helper the gametest helper
     */
    public static void openingWubsPitchedUp(GameTestHelper helper) {
        helper.setBlock(HoleTests.FLOOR, Blocks.STONE);
        ServerPlayer owner = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        PacketRecorder heard = PacketRecorder.attachTo(owner);
        HoleOpening.open(helper.getLevel(), owner, helper.absolutePos(HoleTests.FLOOR), Direction.UP,
                HoleTests.LIFETIME, HoleTests.REFRESH);
        Optional<PortableHole> hole = PortableHoles.of(owner.getUUID());
        List<ClientboundSoundPacket> opening = HeardSounds.of(heard, GooSoundIds.HOLE_OPEN);
        List<ClientboundSoundPacket> chest = HeardSounds.of(heard, ENDER_CHEST_OPEN);
        helper.getLevel().getServer().getPlayerList().remove(owner);
        helper.assertTrue(hole.isPresent(), "The hole should stand once opened");
        Vec3 middle = hole.get().mouth().getCenter();
        helper.assertTrue(opening.size() == 1 && HeardSounds.playedAt(opening.getFirst(), middle)
                        && opening.getFirst().getPitch() == OPENING_PITCH,
                "Opening the hole should play its opening sound once at the mouth's middle " + middle + " at pitch "
                        + OPENING_PITCH + ", plays " + describe(opening));
        helper.assertTrue(chest.isEmpty(), "Opening the hole should play no ender chest, plays " + describe(chest));
        helper.succeed();
    }

    /** Where and at what pitch each sound played, for a failure message. */
    static String describe(List<ClientboundSoundPacket> sounds) {
        return HeardSounds.describe(sounds) + " at pitches "
                + sounds.stream().map(ClientboundSoundPacket::getPitch).toList();
    }
}
