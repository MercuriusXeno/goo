package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.zone.GateLook;
import com.mercuriusxeno.goo.client.tartarus.TartarusGateRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Draws each door a player channeling has conjured, from its first tick,
 * through the drawing its look names: Zone's and End's door with the
 * starfield, Tartarus' gate with its own texel doorway. A door whose caster has left
 * this level went with them and draws no more.
 * decision crack-and-doorway-wear-the-end-portal-shader
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
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
        double time = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        drawnThisFrame(mc, level).forEach((caster, door) -> {
            if (level.getEntity(caster) != null) {
                submitDoor(event.getSubmitNodeCollector(), event.getPoseStack(), door, camera, time);
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
     * Submits one door through the drawing its look names.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the door drawn
     * @param camera    the camera's world position
     * @param time      the client game tick and the frame's partial tick
     */
    static void submitDoor(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                           Vec3 camera, double time) {
        drawingFor(door.look()).submit(collector, poseStack, door, camera, time);
    }

    /**
     * The drawing a door's look names.
     * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
     *
     * @param look what the door is drawn with
     * @return the starfield door's drawing, or Tartarus' own texel doorway
     */
    public static GateDrawing drawingFor(GateLook look) {
        return switch (look) {
            case STARFIELD -> StarfieldDoorDrawing.INSTANCE;
            case NETHER_PORTAL -> TartarusGateRenderer.INSTANCE;
        };
    }
}
