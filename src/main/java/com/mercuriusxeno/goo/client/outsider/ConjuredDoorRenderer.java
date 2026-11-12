package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws each door a player channeling Outsider has conjured as a jagged rip
 * in the air opening into a doorway, its outline filled with the End
 * portal's starfield, seen from either side, facing its caster. A door whose
 * caster has left this level went with them and draws no more.
 * decision crack-and-doorway-wear-the-end-portal-shader
 * decision door-opens-from-a-thin-crack-in-the-air
 * decision conjured-door-opens-as-a-jagged-rip
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ConjuredDoorRenderer {


    private static @Nullable ClientLevel drawnIn;

    private ConjuredDoorRenderer() {
    }

    /**
     * Submits every conjured door the client holds.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != drawnIn) {
            ClientConjuredDoors.forget();
            drawnIn = mc.level;
        }
        if (mc.level == null) {
            return;
        }
        ClientLevel level = mc.level;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        drawnThisFrame(mc, level).forEach((caster, door) -> {
            if (level.getEntity(caster) != null && door.size() > 0) {
                submitDoor(event.getSubmitNodeCollector(), event.getPoseStack(), door, camera);
            }
        });
    }

    /**
     * Every door the client holds as this frame draws it, the local
     * player's own stood from their look at the frame's partial tick.
     *
     * @param mc    the client
     * @param level the level drawn
     * @return the doors drawn, by caster
     */
    private static Map<Integer, ClientConjuredDoors.Drawn> drawnThisFrame(Minecraft mc, ClientLevel level) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double time = level.getGameTime() + partialTick;
        LocalPlayer player = mc.player;
        if (player == null) {
            return ClientConjuredDoors.drawnAt(time);
        }
        return ClientConjuredDoors.drawnAt(time, player.getId(),
                new ClientConjuredDoors.Look(player.getPosition(partialTick), player.getViewYRot(partialTick)),
                EntryCover.LOCAL.covering());
    }

    /**
     * Submits one door's rip under the End portal render type, whose
     * vertices carry their position alone, as the End gate submits its square.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the door drawn
     * @param camera    the camera's world position
     */
    static void submitDoor(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                           Vec3 camera) {
        List<Vec3[]> quads = quadsOf(door, camera);
        collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(),
                (pose, consumer) -> quads.forEach(quad -> emitBothWaysRound(pose, consumer, quad)));
    }

    /**
     * The door's rip as one quad per pair of neighboring rows, stood upright
     * at its foot across the way its caster faced, relative to the camera.
     * The render type draws quads alone, so the pointed head and foot are
     * quads with two corners together.
     *
     * @param door   the door drawn
     * @param camera the camera's world position
     * @return each quad's four corners in order around it
     */
    private static List<Vec3[]> quadsOf(ClientConjuredDoors.Drawn door, Vec3 camera) {
        double radians = Math.toRadians(door.yaw());
        Vec3 across = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        Vec3 foot = door.at().subtract(camera);
        List<RipRow> rows = DoorCrack.outline(door.size());
        List<Vec3[]> quads = new ArrayList<>(rows.size() - 1);
        for (int index = 1; index < rows.size(); index++) {
            RipRow below = rows.get(index - 1);
            RipRow above = rows.get(index);
            quads.add(new Vec3[] {
                placed(foot, across, below.leftAcross(), below.up()),
                placed(foot, across, below.rightAcross(), below.up()),
                placed(foot, across, above.rightAcross(), above.up()),
                placed(foot, across, above.leftAcross(), above.up()),
            });
        }
        return quads;
    }

    private static Vec3 placed(Vec3 foot, Vec3 across, double sideways, double up) {
        return foot.add(across.scale(sideways)).add(0, up, 0);
    }

    private static void emitBothWaysRound(PoseStack.Pose pose, VertexConsumer consumer, Vec3[] corners) {
        for (Vec3 corner : corners) {
            vertex(pose, consumer, corner);
        }
        for (int index = corners.length - 1; index >= 0; index--) {
            vertex(pose, consumer, corners[index]);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z);
    }
}
