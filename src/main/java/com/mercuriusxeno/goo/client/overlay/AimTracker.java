package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ability.PortalView;
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
import org.jspecify.annotations.Nullable;
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
     * The outline a pass gives the aimed mob: its color on the copy the aim
     * reaches, the one before the player where the aim runs straight and the
     * one in the first view through the gate the aim passed where it runs
     * through, none on any other copy, so the outlines never change in
     * number as one copy hides another, as the operator found walking up to
     * a portal.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param color    the aimed mob's outline color, zero where the entity is not aimed at
     * @param view     whether a view through a gate draws the copy
     * @param viewGate the gate that first view looks into, null for the main frame or a view a portal deeper
     * @param passage  the gate the aim passed, null where it runs straight
     * @return the outline color, zero for none
     */
    static int outlineIn(int color, boolean view, @Nullable Gate viewGate, TargetResult.@Nullable Passage passage) {
        return GooTargetHighlighter.drawsMarks(view, viewGate, passage) ? color : 0;
    }

    /**
     * Render state modifier callback: sets outlineColor on the copy of the
     * entity the glove aims at that the aim reaches, so vanilla renders the
     * spectral glow outline in the goo color. Registered via
     * RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the entity being rendered
     * @param state  its render state
     */
    public static void modifyEntityRenderState(Entity entity, EntityRenderState state) {
        int color = outlineIn(STATE.outlineFor(entity), PortalView.extractingView(), PortalView.extractingGate(),
                STATE.target().passage());
        if (color != 0) {
            state.outlineColor = color;
        }
    }
}
