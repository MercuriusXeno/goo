package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Ticks the client's one {@link AimState} and answers its readers: the
 * frame and the throw read the tick's target, the entity renderer its
 * outline, the chain marker renderer whether it is aimed at (decision
 * render-context-is-the-one-emitter).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AimTracker {

    private static final AimState STATE = new AimState();

    private AimTracker() {
    }

    /**
     * Client tick: resolves the aim once for the tick, or holds the frozen
     * target through the post-throw freeze.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            STATE.clear();
            return;
        }
        ResourceKey<GooTypeDefinition> selectedType = GloveAim.selectedGooType(player);
        int entityOutline = selectedType != null ? ARGB.opaque(ClientGooTypes.highlight(selectedType)) : 0;
        TargetResult frozen = ThrowFreezeState.getFrozenTarget();
        if (frozen != null) {
            STATE.update(seed -> new AimState.Resolution(frozen, seed), entityOutline);
            return;
        }
        STATE.update(seed -> AimTargets.resolve(player, seed, GloveAim.targetingHint(player)), entityOutline);
    }

    /**
     * The target this tick resolved, the one the frame shows and the throw sends.
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

    /**
     * Returns true if the chain-marker block at the given position is the
     * current cone-assisted aim target, so its renderer keeps the targeted
     * look across the freeze window and through the eager cone scan.
     *
     * @param pos the chain marker block position
     * @return true if the aim assist is currently locked onto this marker
     */
    public static boolean isChainMarkerTargeted(BlockPos pos) {
        return STATE.isAimedAtMarker(pos);
    }
}
