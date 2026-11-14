package com.mercuriusxeno.goo.client.zone;

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
import java.util.List;
import java.util.Map;

/**
 * Draws each door a player channeling Zone has conjured as a pixel
 * unzipping into a slash and widening into a doorway, from its first tick,
 * its outline filled with the End portal's starfield, seen from either
 * side, facing its caster. A door whose caster has left this level went
 * with them and draws no more.
 * decision crack-and-doorway-wear-the-end-portal-shader
 * decision door-opens-from-a-thin-crack-in-the-air
 * decision door-unzips-from-a-starfield-pixel-then-widens-into-you
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
            if (level.getEntity(caster) != null) {
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
     * Submits one door's outline under the End portal render type, whose
     * vertices carry their position alone, as the End gate submits its square.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the door drawn
     * @param camera    the camera's world position
     */
    static void submitDoor(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                           Vec3 camera) {
        Vec3[] corners = cornersOf(door, camera);
        collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(),
                (pose, consumer) -> emitBothWaysRound(pose, consumer, corners));
    }

    /**
     * The door's outline corners, stood upright at its foot across the way
     * its caster faced, relative to the camera.
     *
     * @param door   the door drawn
     * @param camera the camera's world position
     * @return the corners in order around the outline
     */
    private static Vec3[] cornersOf(ClientConjuredDoors.Drawn door, Vec3 camera) {
        double radians = Math.toRadians(door.yaw());
        Vec3 across = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        Vec3 foot = door.at().subtract(camera);
        List<DoorCrack.Corner> outline = DoorCrack.outline(door.unzipped(), door.widened());
        Vec3[] corners = new Vec3[outline.size()];
        for (int index = 0; index < corners.length; index++) {
            DoorCrack.Corner corner = outline.get(index);
            corners[index] = foot.add(across.scale(corner.across())).add(0, corner.up(), 0);
        }
        return corners;
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
