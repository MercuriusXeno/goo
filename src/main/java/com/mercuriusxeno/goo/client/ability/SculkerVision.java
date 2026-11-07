package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/**
 * Dive's warden senses: while the dive stands the view goes dark through
 * {@code goo:sculker_sense}, open space drawn as sculk contours swept by echo
 * rings, the scene's own colors faint near the diver and solid space a dark
 * sea past them.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerVision {

    /** The chain at {@code assets/goo/post_effect/sculker_sense.json}. */
    static final Identifier POST_EFFECT_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "sculker_sense");
    private static final String SCULKER_CONFIG_BLOCK = "SculkerConfig";
    /** Std140 size of {@code SculkerConfig}: one vec4. */
    private static final int SCULKER_CONFIG_SIZE = 16;
    /** How far from the diver, in blocks, the scene's own colors still show. */
    private static final float NEAR_REACH = 6f;
    private static final float BLOCKS_PER_CHUNK = 16f;
    /** The camera's far plane past its render distance, as {@code Camera} sets it up. */
    private static final float FAR_PER_RENDER_BLOCK = 4f;
    private static final float TICKS_PER_SECOND = 20f;

    private static final OwnedPostEffect SLOT = new OwnedPostEffect(POST_EFFECT_ID, SCULKER_CONFIG_BLOCK);
    private static final Vector4f SCULKER_PARAMS = new Vector4f();

    private SculkerVision() {
    }

    /**
     * Draws the senses while the local player's Sculker stands and releases
     * the slot once it ends.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !SculkerSwim.dives(player)) {
            SLOT.hide(mc.gameRenderer);
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float seconds = ((mc.level.getGameTime() % Integer.MAX_VALUE) + partialTick) / TICKS_PER_SECOND;
        float far = Math.max(mc.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK * FAR_PER_RENDER_BLOCK,
                mc.options.cloudRange().get() * BLOCKS_PER_CHUNK);
        SCULKER_PARAMS.set(seconds, NEAR_REACH, far, Camera.PROJECTION_Z_NEAR);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, SCULKER_CONFIG_SIZE);
            builder.putVec4(SCULKER_PARAMS);
            SLOT.show(mc, builder.get());
        }
    }
}
