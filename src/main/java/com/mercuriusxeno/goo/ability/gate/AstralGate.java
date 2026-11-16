package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.ability.zone.VoidPockets;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

/**
 * Astral's gate on the server. Cast looking at the moon in the overworld, or at the blue overworld star in lunar,
 * it fixes the caster's gaze and holds them still where they stand while their view closes in; when the fixation
 * runs out the moon carries them to lunar over the column they stood on, keeping where they left from, and the star
 * carries them back to exactly that place and facing. Cast looking at neither, it refuses with a tell. The gate
 * places nothing.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 */
public final class AstralGate {

    /** The tell a cast looking at neither the moon nor the star shows. */
    static final String TELL_REFUSED = "goo.ability.astral.gate.refused";
    /** How far a held caster may drift from where they cast before the server sets them back, in blocks. */
    private static final double DRIFT = 1e-3;

    private AstralGate() {
    }

    /**
     * Begins the fixation a cast asks for, when the caster looks at the moon or the star and stands in no
     * fixation already.
     *
     * @param caster   the caster
     * @param duration the ticks the fixation runs
     * @return true when the fixation began
     */
    public static boolean begin(ServerPlayer caster, int duration) {
        if (caster.getData(GooAttachments.GATE_FIXATION).stands()) {
            return false;
        }
        float moonAngle = moonAngleAt(caster);
        Optional<GateSight.Sky> sky = GateSight.skyAt(caster.level().dimension(), caster.getLookAngle(), moonAngle);
        if (sky.isEmpty()) {
            return false;
        }
        caster.setData(GooAttachments.GATE_FIXATION, new GateFixation(sky.get(),
                GateSight.directionOf(sky.get(), moonAngle), caster.position(), caster.level().getGameTime(), duration));
        return true;
    }

    /**
     * Whether a cast would find the moon or the star along the caster's look.
     *
     * @param caster the caster
     * @return true when the look holds either
     */
    public static boolean findsTheSky(ServerPlayer caster) {
        return GateSight.skyAt(caster.level().dimension(), caster.getLookAngle(), moonAngleAt(caster))
                .isPresent();
    }

    /**
     * Shows the caster the gate's refusal.
     *
     * @param caster the caster
     */
    public static void refuse(ServerPlayer caster) {
        caster.sendSystemMessage(Component.translatable(TELL_REFUSED), true);
    }

    /**
     * Runs one server tick of every standing fixation: each caster stands held, and a caster whose fixation ran
     * out is carried.
     *
     * @param server the server
     */
    static void tickAll(MinecraftServer server) {
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer caster : players) {
            // Read only where a fixation was laid: reading creates the attachment, which every dimension change
            // would then sync.
            if (!caster.hasData(GooAttachments.GATE_FIXATION)) {
                continue;
            }
            GateFixation fixation = caster.getData(GooAttachments.GATE_FIXATION);
            if (!fixation.stands()) {
                continue;
            }
            if (fixation.ranOut(caster.level().getGameTime())) {
                // Removed, not set to none, so the dimension change ahead carries no synced fixation.
                caster.removeData(GooAttachments.GATE_FIXATION);
                carry(caster, fixation.sky());
            } else {
                hold(caster, fixation.anchor());
            }
        }
    }

    private static void hold(ServerPlayer caster, Vec3 anchor) {
        caster.setDeltaMovement(Vec3.ZERO);
        if (caster.position().distanceTo(anchor) > DRIFT) {
            caster.teleportTo(anchor.x, anchor.y, anchor.z);
        }
    }

    private static void carry(ServerPlayer caster, GateSight.Sky sky) {
        if (sky == GateSight.Sky.MOON) {
            toLunar(caster);
        } else {
            toHome(caster);
        }
    }

    /**
     * Carries the caster to lunar over the column they stood on, keeping where they left from.
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
        caster.teleport(new TeleportTransition(lunar, surfaceOver(lunar, caster.position()), Vec3.ZERO,
                caster.getYRot(), caster.getXRot(), TeleportTransition.DO_NOTHING));
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

    private static float moonAngleAt(ServerPlayer caster) {
        return caster.level().environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, caster.position());
    }
}
