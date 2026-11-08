package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
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
 * Sculker's warden senses: while the brew stands the world dims through
 * {@code goo:sculker_sense} with its distance from the diver, black by 8
 * blocks, so past the near world only what a real echo touched shows.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 * decision sculker-shows-blocks-translucent-close-up
 *
 * <p>The chain dims the world by its depth, and the game renderer clears the
 * level's depth to draw the hand before its own post effect slot runs, so the
 * chain runs itself, holding no slot, once the world's blocks are drawn and
 * before the glass field and the echoes draw over them: what stands behind
 * the glass is dimmed by its own distance, and an echo shows at its own
 * strength.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerVision {

    /** The chain at {@code assets/goo/post_effect/sculker_sense.json}. */
    static final Identifier POST_EFFECT_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "sculker_sense");
    private static final String SCULKER_CONFIG_BLOCK = "SculkerConfig";
    /** Std140 size of {@code SculkerConfig}: two vec4s. */
    private static final int SCULKER_CONFIG_SIZE = 32;
    /** The share of the gap to full or no presence the vision closes each tick, about a second and a half to settle. */
    static final float EASE_PER_TICK = 0.05f;
    /** Below this presence the vision is gone and its chain stops running. */
    static final float GONE = 0.01f;
    private static final float BLOCKS_PER_CHUNK = 16f;
    /** The camera's far plane past its render distance, as {@code Camera} sets it up. */
    private static final float FAR_PER_RENDER_BLOCK = 4f;
    /** The frames an unused render target is kept for, as the game renderer keeps its own. */
    private static final int FRAMES_TO_KEEP_TARGETS = 3;

    private static final OwnedPostEffect SLOT = new OwnedPostEffect(POST_EFFECT_ID, SCULKER_CONFIG_BLOCK);
    private static final Vector4f SCULKER_PARAMS = new Vector4f();
    private static final Vector4f SCULKER_LOOK = new Vector4f();
    private static float presence;
    /** The render targets the chain runs on, kept across frames as the game renderer keeps its own. */
    private static final CrossFrameResourcePool TARGETS = new CrossFrameResourcePool(FRAMES_TO_KEEP_TARGETS);

    private SculkerVision() {
    }

    private static boolean brewStands(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && SculkerSwim.dives(player);
    }

    /**
     * How fully the vision stands now, easing in as the brew starts and out as it ends.
     *
     * @return the presence, 0 to 1
     */
    public static float presence() {
        return presence;
    }

    /**
     * The presence after a frame: it closes a share of the gap to full
     * presence while the brew stands, and to none otherwise.
     *
     * @param presence   the presence before the frame, 0 to 1
     * @param stands     whether the brew stands
     * @param deltaTicks the frame's length in ticks
     * @return the presence after the frame
     */
    static float easedPresence(float presence, boolean stands, float deltaTicks) {
        float target = stands ? 1f : 0f;
        return presence + (target - presence) * Math.min(1f, deltaTicks * EASE_PER_TICK);
    }

    /**
     * Once the world's blocks are drawn, runs the senses on them, then draws
     * the glass field and the echoes over the dimmed world.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        sense();
        SculkerGlassRenderer.draw(event);
        SculkerEchoRenderer.draw(event);
    }

    /**
     * Eases the senses in while the local player's Sculker stands and out
     * once it ends, and runs the chain on the world while they stand.
     */
    private static void sense() {
        Minecraft mc = Minecraft.getInstance();
        boolean stands = brewStands(mc);
        presence = easedPresence(presence, stands, mc.getDeltaTracker().getRealtimeDeltaTicks());
        SLOT.hide(mc.gameRenderer);
        if (mc.level == null || !stands && presence < GONE) {
            presence = stands ? presence : 0f;
            return;
        }
        float far = Math.max(mc.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK * FAR_PER_RENDER_BLOCK,
                mc.options.cloudRange().get() * BLOCKS_PER_CHUNK);
        SCULKER_PARAMS.set(0f, 0f, far, Camera.PROJECTION_Z_NEAR);
        SCULKER_LOOK.set(presence, 0f, 0f, 0f);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, SCULKER_CONFIG_SIZE);
            builder.putVec4(SCULKER_PARAMS);
            builder.putVec4(SCULKER_LOOK);
            SLOT.runNow(mc, builder.get(), TARGETS);
            TARGETS.endFrame();
        }
    }
}
