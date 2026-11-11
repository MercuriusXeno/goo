package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Draws each door a player channeling Outsider has conjured as the pocket's
 * own door draws: a two-tall sheet of the dimension door's swirling void,
 * full bright, seen from either side, facing its caster, grown from its
 * middle by its size. A door whose caster has left this level went with
 * them and draws no more.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ConjuredDoorRenderer {

    /** The pocket's own door's sheet, on the block atlas. */
    private static final Identifier DOOR_SPRITE = Identifier.fromNamespaceAndPath(Goo.MODID, "block/dimension_door");
    /** The door's half width and half height at full size, as the room's two-tall doorway stands. */
    private static final double HALF_WIDE = 0.5;
    private static final double HALF_TALL = 1.0;
    /** Translucent white, the sheet showing a little of what stands behind it. */
    private static final int SHEET_COLOR = 0xE6FFFFFF;

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
        double time = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Map<Integer, ClientConjuredDoors.Drawn> doors = ClientConjuredDoors.drawnAt(time);
        if (doors.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(GooSubmitter.blockSprite(DOOR_SPRITE));
        doors.forEach((caster, door) -> {
            if (level.getEntity(caster) != null && door.size() > 0) {
                event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.renderType(),
                        (pose, consumer) -> emitSheet(new RenderContext(pose, consumer,
                                GooSubmitter.fullbrightLight(), SHEET_COLOR), door, camera, uv));
            }
        });
    }

    /**
     * Emits a door's sheet: its two cells, each wearing the whole door
     * sprite as the doorway's two blocks do, both ways round.
     *
     * @param ctx    the render context
     * @param door   the door drawn
     * @param camera the camera's world position
     * @param uv     the door sprite's UV rect
     */
    private static void emitSheet(RenderContext ctx, ClientConjuredDoors.Drawn door, Vec3 camera,
                                  GooRenderUtil.UvRect uv) {
        double radians = Math.toRadians(door.yaw());
        Vec3 facing = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
        Vec3 across = new Vec3(Math.cos(radians), 0, Math.sin(radians)).scale(HALF_WIDE * door.size());
        Vec3 middle = door.at().add(0, HALF_TALL, 0).subtract(camera);
        Vec3 halfUp = new Vec3(0, HALF_TALL * door.size(), 0);
        emitCell(ctx, middle.subtract(halfUp), middle, across, facing, uv);
        emitCell(ctx, middle, middle.add(halfUp), across, facing, uv);
    }

    private static void emitCell(RenderContext ctx, Vec3 foot, Vec3 head, Vec3 across, Vec3 facing,
                                 GooRenderUtil.UvRect uv) {
        Vec3[] corners = {foot.subtract(across), foot.add(across), head.add(across), head.subtract(across)};
        float[] us = {uv.u0(), uv.u1(), uv.u1(), uv.u0()};
        float[] vs = {uv.v1(), uv.v1(), uv.v0(), uv.v0()};
        for (int i = 0; i < corners.length; i++) {
            vertex(ctx, corners[i], us[i], vs[i], facing.reverse());
        }
        for (int i = corners.length - 1; i >= 0; i--) {
            vertex(ctx, corners[i], us[i], vs[i], facing);
        }
    }

    private static void vertex(RenderContext ctx, Vec3 at, float u, float v, Vec3 normal) {
        ctx.vertex((float) at.x, (float) at.y, (float) at.z, u, v, (float) normal.x, (float) normal.y,
                (float) normal.z);
    }
}
