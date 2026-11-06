package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.DiveSwim;
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
 * {@code goo:dive_sense}, open space drawn as sculk contours swept by echo
 * rings, the scene's own colors faint near the diver and solid space a dark
 * sea past them.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DiveVision {

    /** The chain at {@code assets/goo/post_effect/dive_sense.json}. */
    static final Identifier POST_EFFECT_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "dive_sense");
    private static final String DIVE_CONFIG_BLOCK = "DiveConfig";
    /** Std140 size of {@code DiveConfig}: one vec4. */
    private static final int DIVE_CONFIG_SIZE = 16;
    /** How far from the diver, in blocks, the scene's own colors still show. */
    private static final float NEAR_REACH = 6f;
    private static final float BLOCKS_PER_CHUNK = 16f;
    /** The camera's far plane past its render distance, as {@code Camera} sets it up. */
    private static final float FAR_PER_RENDER_BLOCK = 4f;
    private static final float TICKS_PER_SECOND = 20f;

    private static final OwnedPostEffect SLOT = new OwnedPostEffect(POST_EFFECT_ID, DIVE_CONFIG_BLOCK);
    private static final Vector4f DIVE_PARAMS = new Vector4f();

    private DiveVision() {
    }

    /**
     * Draws the senses while the local player's dive stands and releases
     * the slot once it ends.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !DiveSwim.dives(player)) {
            SLOT.hide(mc.gameRenderer);
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float seconds = ((mc.level.getGameTime() % Integer.MAX_VALUE) + partialTick) / TICKS_PER_SECOND;
        float far = Math.max(mc.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK * FAR_PER_RENDER_BLOCK,
                mc.options.cloudRange().get() * BLOCKS_PER_CHUNK);
        DIVE_PARAMS.set(seconds, NEAR_REACH, far, Camera.PROJECTION_Z_NEAR);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, DIVE_CONFIG_SIZE);
            builder.putVec4(DIVE_PARAMS);
            SLOT.show(mc, builder.get());
        }
    }
}
