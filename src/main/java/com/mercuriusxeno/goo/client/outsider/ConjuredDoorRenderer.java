package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
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
 * portal's starfield and traced by a bright rim, seen from either side,
 * facing its caster. A door whose caster has left this level went with
 * them and draws no more.
 * decision crack-and-doorway-wear-the-end-portal-shader
 * decision door-opens-from-a-thin-crack-in-the-air
 * decision conjured-door-opens-as-a-jagged-rip
 * decision rip-wears-a-bright-rim-on-its-torn-edge
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ConjuredDoorRenderer {

    /**
     * The rim's render type: plain colored quads through the position-color
     * shader, which samples no lightmap so the rim draws full bright, added
     * onto the world, depth tested with depth write off, both faces drawn.
     */
    static final RenderType RIM_RENDER_TYPE = GooRenderTypes.GLOW_SHELL_TYPE;

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
     * vertices carry their position alone, as the End gate submits its square,
     * then its rim under the rim's render type, each vertex carrying its color.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the door drawn
     * @param camera    the camera's world position
     */
    static void submitDoor(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                           Vec3 camera) {
        DoorPlane plane = DoorPlane.of(door, camera);
        List<RipRow> outline = DoorCrack.outline(door.size());
        List<Vec3[]> ripQuads = ripQuadsOf(plane, outline);
        List<DoorRim.RimQuad> rimQuads = DoorRim.band(outline);
        collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(),
                (pose, consumer) -> ripQuads.forEach(quad -> emitBothWaysRound(pose, consumer, quad)));
        collector.submitCustomGeometry(poseStack, RIM_RENDER_TYPE,
                (pose, consumer) -> rimQuads.forEach(quad -> emitRimBothWaysRound(pose, consumer, plane, quad)));
    }

    /**
     * The door's own plane: its foot relative to the camera, and the way
     * across it, square to the way its caster faced.
     *
     * @param foot   the middle of the door's foot, relative to the camera
     * @param across the unit direction across the door's width
     */
    private record DoorPlane(Vec3 foot, Vec3 across) {

        static DoorPlane of(ClientConjuredDoors.Drawn door, Vec3 camera) {
            double radians = Math.toRadians(door.yaw());
            return new DoorPlane(door.at().subtract(camera), new Vec3(Math.cos(radians), 0, Math.sin(radians)));
        }

        Vec3 placed(double sideways, double up) {
            return foot.add(across.scale(sideways)).add(0, up, 0);
        }
    }

    /**
     * The door's rip as one quad per pair of neighboring rows. The render
     * type draws quads alone, so the pointed head and foot are quads with
     * two corners together.
     *
     * @param plane   the door's plane
     * @param outline the rip's rows from foot to head
     * @return each quad's four corners in order around it
     */
    private static List<Vec3[]> ripQuadsOf(DoorPlane plane, List<RipRow> outline) {
        List<Vec3[]> quads = new ArrayList<>(outline.size() - 1);
        for (int index = 1; index < outline.size(); index++) {
            RipRow below = outline.get(index - 1);
            RipRow above = outline.get(index);
            quads.add(new Vec3[] {
                plane.placed(below.leftAcross(), below.up()),
                plane.placed(below.rightAcross(), below.up()),
                plane.placed(above.rightAcross(), above.up()),
                plane.placed(above.leftAcross(), above.up()),
            });
        }
        return quads;
    }

    private static void emitBothWaysRound(PoseStack.Pose pose, VertexConsumer consumer, Vec3[] corners) {
        for (Vec3 corner : corners) {
            vertex(pose, consumer, corner);
        }
        for (int index = corners.length - 1; index >= 0; index--) {
            vertex(pose, consumer, corners[index]);
        }
    }

    private static void emitRimBothWaysRound(PoseStack.Pose pose, VertexConsumer consumer, DoorPlane plane,
                                             DoorRim.RimQuad quad) {
        List<DoorRim.RimCorner> corners = quad.corners();
        for (DoorRim.RimCorner corner : corners) {
            vertex(pose, consumer, plane.placed(corner.across(), corner.up())).setColor(corner.color());
        }
        for (DoorRim.RimCorner corner : corners.reversed()) {
            vertex(pose, consumer, plane.placed(corner.across(), corner.up())).setColor(corner.color());
        }
    }

    private static VertexConsumer vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at) {
        return consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z);
    }
}
