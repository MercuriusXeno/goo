package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.ability.zone.VoidPockets;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.network.StreamHolds;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooSounds;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lunar on the server, a hold. Held looking at the moon in the overworld, or at the blue planet in
 * lunar, it fixes the caster's gaze, sends them its rising tone, and holds them still where they stand while the moon
 * or the planet swells to fill their view; held until the fixation runs out, the moon carries them to lunar at the
 * landing its phase, its night's tick and their account UUID set, keeping where they left from, and the planet
 * carries them back to exactly that place and facing, each taking the astral then and leaving the arrival mark their
 * client's white clears on. Let go early, it
 * releases them where they stand and takes nothing. Held looking at neither, it refuses with a tell. Lunar places
 * nothing.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision holding-right-click-pulls-the-moon-and-early-release-cancels
 * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
 * decision traversal-to-the-moon-always-lands-on-the-light-side
 */
public final class AstralLunar {

    /** How far a held caster may drift from where they cast before the server sets them back, in blocks. */
    private static final double DRIFT = 1e-3;
    private static final float TONE_VOLUME = 1F;
    private static final float TONE_PITCH = 1F;
    /**
     * Server ticks a pull outlasts the last held tick it heard: as long as the press's stream hold carries over a
     * gap, so a press the stream reads as the same hold finds its pull standing, and one it reads as a new press finds
     * the pull let go.
     * decision diagnose-and-correct-the-lunar-hold-that-does-nothing
     */
    private static final int HOLD_OUTLASTS = StreamHolds.LATEST_CONTINUING_GAP;

    /** Each holding caster's last held tick and the cost their carry takes. */
    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

    private AstralLunar() {
    }

    /**
     * A caster's hold on the cast.
     *
     * @param heldAt the server tick the cast was last held
     * @param cost   the astral the carry takes (mB)
     */
    private record Hold(int heldAt, int cost) {
    }

    /**
     * Begins the fixation the first held tick of a cast asks for, when the caster looks at the moon or the planet and
     * stands in no fixation already. Nothing is paid yet: the moon or the planet takes the cost at the carry.
     *
     * @param caster   the caster
     * @param duration the ticks the fixation runs
     * @param cost     the astral the carry takes (mB)
     * @return true when the fixation began
     */
    public static boolean begin(ServerPlayer caster, int duration, int cost) {
        if (caster.getData(GooAttachments.LUNAR_FIXATION).stands()) {
            return false;
        }
        float moonAngle = moonAngleAt(caster);
        Optional<LunarSight.Sky> sky = LunarSight.skyAt(caster.level().dimension(), caster.getX(),
                caster.getLookAngle(), moonAngle, planetPhaseAt(caster));
        if (sky.isEmpty()) {
            return false;
        }
        caster.setData(GooAttachments.LUNAR_FIXATION, LunarFixation.held(sky.get(),
                LunarSight.moonDirection(moonAngle), caster.position(), caster.level().getGameTime(), duration));
        HOLDS.put(caster.getUUID(), new Hold(caster.level().getServer().getTickCount(), cost));
        sendTone(caster);
        return true;
    }

    /**
     * Keeps a caster's pull standing for a further held tick of the cast.
     * decision holding-right-click-pulls-the-moon-and-early-release-cancels
     *
     * @param caster the caster
     */
    public static void keepHeld(ServerPlayer caster) {
        int now = caster.level().getServer().getTickCount();
        HOLDS.computeIfPresent(caster.getUUID(), (id, hold) -> new Hold(now, hold.cost()));
    }

    /**
     * Sends Lunar's rising tone to the caster alone, where they stand, to rise over the fixation and peak as the
     * white floods their view.
     *
     * @param caster the caster
     */
    private static void sendTone(ServerPlayer caster) {
        caster.connection.send(new ClientboundSoundPacket(GooSounds.LUNAR_TONE, SoundSource.PLAYERS, caster.getX(),
                caster.getY(), caster.getZ(), TONE_VOLUME, TONE_PITCH, caster.getRandom().nextLong()));
    }

    /**
     * Whether a cast would find the moon or the planet along the caster's look.
     *
     * @param caster the caster
     * @return true when the look holds either
     */
    public static boolean findsTheSky(ServerPlayer caster) {
        return LunarSight.skyAt(caster.level().dimension(), caster.getX(), caster.getLookAngle(),
                moonAngleAt(caster), planetPhaseAt(caster)).isPresent();
    }

    /**
     * Shows the caster the refusal their look and their dimension read: "The big yellow one is the sun!" held on the
     * sun, else "That's no moon!" in the overworld and "That's not your world" on the moon.
     * decision terran-refusal-says-thats-not-your-world
     * decision pointing-at-the-sun-says-the-big-yellow-one-is-the-sun
     *
     * @param caster the caster
     */
    public static void refuse(ServerPlayer caster) {
        caster.sendSystemMessage(Component.translatable(LunarSight.refusalKey(caster.level().dimension(),
                caster.getX(), caster.getLookAngle(), sunAngleAt(caster))), true);
    }

    /**
     * Runs one server tick of every standing fixation: a caster still holding the cast stands held, one holding it
     * as the fixation runs out is carried, and one who let go, or cannot pay as the moon or the planet takes them, is
     * released where they stand, paying nothing.
     * decision holding-right-click-pulls-the-moon-and-early-release-cancels
     *
     * @param server the server
     */
    static void tickAll(MinecraftServer server) {
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer caster : players) {
            // Read only where a fixation was laid: reading creates the attachment, which every dimension change
            // would then sync.
            if (!caster.hasData(GooAttachments.LUNAR_FIXATION)) {
                continue;
            }
            LunarFixation fixation = caster.getData(GooAttachments.LUNAR_FIXATION);
            if (fixation.stands()) {
                tick(caster, fixation, server.getTickCount());
            }
        }
    }

    private static void tick(ServerPlayer caster, LunarFixation fixation, int now) {
        Hold hold = HOLDS.get(caster.getUUID());
        if (hold == null || now - hold.heldAt() > HOLD_OUTLASTS) {
            release(caster, fixation);
        } else if (!fixation.ranOut(caster.level().getGameTime())) {
            hold(caster, fixation.anchor());
        } else if (GooSourceScanner.hasEnough(caster, GooTypes.ASTRAL, hold.cost())) {
            HOLDS.remove(caster.getUUID());
            // Removed, not set to none, so the dimension change ahead carries no synced fixation or mark.
            caster.removeData(GooAttachments.LUNAR_FIXATION);
            caster.removeData(GooAttachments.LUNAR_ARRIVAL);
            if (carry(caster, fixation.sky())) {
                GooSourceScanner.deplete(caster, GooTypes.ASTRAL, hold.cost());
            }
        } else {
            release(caster, fixation);
        }
    }

    /**
     * Lets a caster go where they stand: the fixation stops standing, kept as let go so their client runs the trip
     * back, and nothing is paid.
     *
     * @param caster   the caster
     * @param fixation the fixation they held
     */
    private static void release(ServerPlayer caster, LunarFixation fixation) {
        HOLDS.remove(caster.getUUID());
        caster.setData(GooAttachments.LUNAR_FIXATION, fixation.releaseAt(caster.level().getGameTime()));
    }

    private static void hold(ServerPlayer caster, Vec3 anchor) {
        caster.setDeltaMovement(Vec3.ZERO);
        if (caster.position().distanceTo(anchor) > DRIFT) {
            caster.teleportTo(anchor.x, anchor.y, anchor.z);
        }
    }

    /**
     * Carries the caster by the moon or the planet, marking them arrived once they stand at the far end.
     *
     * @param caster the caster
     * @param sky    what carries them
     * @return true when the carry changed their level
     */
    private static boolean carry(ServerPlayer caster, LunarSight.Sky sky) {
        Level from = caster.level();
        if (sky == LunarSight.Sky.MOON) {
            toLunar(caster);
        } else {
            toHome(caster);
        }
        if (caster.level() == from) {
            return false;
        }
        // The caster's client holds its white over the crossing until this mark reaches it.
        caster.setData(GooAttachments.LUNAR_ARRIVAL, new LunarArrival(caster.level().getGameTime()));
        return true;
    }

    /**
     * Carries the caster to lunar at the landing the moon's phase and its night's tick set, over the column their
     * account UUID sets, keeping where they left from.
     * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
     * decision z-is-a-function-of-the-account-uuid
     * decision traversal-to-the-moon-always-lands-on-the-light-side
     *
     * @param caster the caster
     */
    private static void toLunar(ServerPlayer caster) {
        ServerLevel lunar = caster.level().getServer().getLevel(Lunar.LEVEL);
        if (lunar == null) {
            return;
        }
        caster.setData(GooAttachments.WAY_HOME, new WayHome(Optional.of(new VoidPockets.ReturnPoint(
                caster.level().dimension(), caster.position(), caster.getYRot(), caster.getXRot()))));
        LunarLanding.Spot landing = LunarLanding.at(moonPhaseAt(caster).index(),
                LunarLanding.nightTickAt(caster.level().getOverworldClockTime()), caster.getUUID());
        caster.teleport(new TeleportTransition(lunar, surfaceOver(lunar, new Vec3(landing.x(), 0, landing.z())),
                Vec3.ZERO, caster.getYRot(), caster.getXRot(), TeleportTransition.DO_NOTHING));
    }

    /**
     * Carries the caster back to exactly where and which way they left from; with no way home kept, to the
     * overworld's surface over the column they stand on.
     *
     * @param caster the caster
     */
    private static void toHome(ServerPlayer caster) {
        MinecraftServer server = caster.level().getServer();
        Optional<VoidPockets.ReturnPoint> point = caster.getData(GooAttachments.WAY_HOME).point();
        caster.setData(GooAttachments.WAY_HOME, WayHome.NONE);
        ServerLevel home = point.map(kept -> server.getLevel(kept.dimension())).orElse(server.overworld());
        if (home == null) {
            home = server.overworld();
        }
        Vec3 arrival = point.map(VoidPockets.ReturnPoint::position).orElse(surfaceOver(home, caster.position()));
        float yRot = point.map(VoidPockets.ReturnPoint::yRot).orElse(caster.getYRot());
        float xRot = point.map(VoidPockets.ReturnPoint::xRot).orElse(caster.getXRot());
        caster.teleport(new TeleportTransition(home, arrival, Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING));
    }

    private static Vec3 surfaceOver(ServerLevel level, Vec3 spot) {
        int x = (int) Math.floor(spot.x);
        int z = (int) Math.floor(spot.z);
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return new Vec3(spot.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), spot.z);
    }

    /**
     * The blue planet's phase over the caster: their dimension's moon phase turned half a cycle.
     * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
     *
     * @param caster the caster
     * @return the planet's phase
     */
    private static MoonPhase planetPhaseAt(ServerPlayer caster) {
        return LunarSight.planetPhase(moonPhaseAt(caster));
    }

    private static MoonPhase moonPhaseAt(ServerPlayer caster) {
        return caster.level().environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, caster.position());
    }

    private static float sunAngleAt(ServerPlayer caster) {
        return caster.level().environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, caster.position());
    }

    private static float moonAngleAt(ServerPlayer caster) {
        return caster.level().environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, caster.position());
    }
}
