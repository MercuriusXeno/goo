package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.util.List;

/**
 * Resolves the client's one {@link AimState} each frame and answers its
 * readers: the frame and the throw read the frame's target, the entity
 * renderer its outline, the ability block renderer whether it is aimed at
 * (decisions render-context-is-the-one-emitter, aim-target-follows-client-aim).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AimTracker {

    private static final AimState STATE = new AimState();

    private AimTracker() {
    }

    /**
     * Frame start: resolves the aim along the camera's ray. The frame's
     * camera update runs before this event, so the camera holds the eye
     * height it eases toward a crouch, the one the frame draws.
     * decision aim-ray-starts-at-the-camera
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onRenderFrameStart(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            STATE.clear();
            return;
        }
        ResourceKey<GooTypeDefinition> selectedType = GloveAim.selectedGooType(player);
        int entityOutline = selectedType != null ? ARGB.opaque(ClientGooTypes.highlight(selectedType)) : 0;
        Camera camera = mc.gameRenderer.getMainCamera();
        AimTargets.AimRay ray = AimTargets.AimRay.along(camera.position(), new Vec3(camera.forwardVector()),
                GloveAim.aimRange(player, AimState.MAX_RANGE));
        List<Step> behaviors = GloveAim.selectedBehaviors(player);
        STATE.update(seed -> AimTargets.resolve(player, ray, seed, GloveAim.targetingHint(player), behaviors),
                entityOutline);
    }

    /**
     * The target the last frame resolved, the one the frame shows and the throw sends.
     *
     * @return the target, NONE when nothing is aimed at
     */
    public static TargetResult currentTarget() {
        return STATE.target();
    }

    /**
     * Render state modifier callback: sets outlineColor on the entity the
     * glove aims at so vanilla renders the spectral glow outline in the goo
     * color. Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the entity being rendered
     * @param state  its render state
     */
    public static void modifyEntityRenderState(Entity entity, EntityRenderState state) {
        int color = STATE.outlineFor(entity);
        if (color != 0) {
            state.outlineColor = color;
        }
    }
}
