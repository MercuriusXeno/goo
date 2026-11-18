package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.HeardSounds;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for a held stream's sound with loop false, Goop's squelch, driven
 * through the real stream tick: its holder is sent each beat to play at the
 * listener and no positioned play, and a player near is sent each beat at the
 * holder's eye.
 * diagnose-and-correct-the-spray-squelch-playing-in-one-ear
 * goo-sound-at-the-listener-reaches-both-ears
 */
@RegistersGameTests
public final class StreamSoundTests {

    private static final String SQUELCH_REACHES_BOTH_EARS = "goop_squelch_reaches_the_holder_at_the_listener";
    private static final Identifier GOOP = Identifier.parse("goo:weird_goop");
    private static final Identifier SQUELCH = Identifier.parse("minecraft:block.honey_block.slide");
    /** Goop's squelch beats every ten ticks of the hold, so thirty held ticks beat three times. */
    private static final int HELD_TICKS = 30;
    private static final int BEATS = 3;
    /** Where the onlooker stands from the holder, in earshot and clear of the cone's apex. */
    private static final Vec3 ONLOOKER_OFFSET = new Vec3(0, 0, 3);

    private StreamSoundTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SQUELCH_REACHES_BOTH_EARS,
                StreamSoundTests::goopSquelchReachesTheHolderAtTheListener);
    }

    /**
     * Thirty held ticks of Goop send its holder three squelches to play at
     * the listener and no positioned squelch, and send an onlooker three
     * squelches at the holder's eye.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void goopSquelchReachesTheHolderAtTheListener(GameTestHelper helper) {
        ServerPlayer holder = SelfDeliveryTests.invoker(helper, GooTypes.WEIRD);
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, "Ability registry must hold goo:weird_goop");
        KnownRecipes.teachRequires(holder, goop);
        ServerPlayer onlooker = helper.makeMockServerPlayerInLevel();
        Vec3 stand = holder.position().add(ONLOOKER_OFFSET);
        onlooker.setPos(stand.x, stand.y, stand.z);
        PacketRecorder holderHeard = PacketRecorder.attachClaiming(holder, ListenerSoundPayload.class);
        PacketRecorder onlookerHeard = PacketRecorder.attachTo(onlooker);
        Vec3 eye = holder.getEyePosition();
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), GOOP.toString(), eye, eye);
        for (int held = 1; held <= HELD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(holder, tick));
        }
        helper.runAfterDelay(HELD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(holder);
            helper.getLevel().getServer().getPlayerList().remove(onlooker);
            List<ClientboundSoundPacket> positioned = HeardSounds.of(holderHeard, SQUELCH);
            long atListener = holderHeard.payloadsOf(ListenerSoundPayload.class).stream()
                    .filter(payload -> payload.cue().sound().equals(SQUELCH)).count();
            helper.assertTrue(positioned.isEmpty() && atListener == BEATS, "The holder of Goop's stream should be "
                    + "sent " + BEATS + " squelches at the listener and none positioned, eye " + eye + "; sent "
                    + atListener + " at the listener and positioned at " + HeardSounds.describe(positioned));
            List<ClientboundSoundPacket> near = HeardSounds.of(onlookerHeard, SQUELCH);
            helper.assertTrue(near.size() == BEATS && HeardSounds.at(onlookerHeard, SQUELCH, eye) == BEATS,
                    "An onlooker should be sent " + BEATS + " squelches at the holder's eye " + eye + ", sent "
                            + HeardSounds.describe(near));
            helper.succeed();
        });
    }
}
