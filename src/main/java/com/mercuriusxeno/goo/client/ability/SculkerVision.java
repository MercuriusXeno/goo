package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/**
 * Dive's warden senses: while the dive stands the view goes dark through
 * {@code goo:sculker_sense}, open space drawn as sculk contours swept by echo
 * rings, the scene's own colors faint near the diver and solid space a dark
 * sea past them.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 * decision sculker-shows-blocks-translucent-close-up
 *
 * <p>The chain dims the world by its depth, and the game renderer clears the
 * level's depth to draw the hand before its own post effect slot runs, so the
 * chain runs itself just before that clear, holding no slot.
 */
public final class SculkerVision {

    /** The chain at {@code assets/goo/post_effect/sculker_sense.json}. */
    static final Identifier POST_EFFECT_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "sculker_sense");
    private static final String SCULKER_CONFIG_BLOCK = "SculkerConfig";
    /** Std140 size of {@code SculkerConfig}: two vec4s. */
    private static final int SCULKER_CONFIG_SIZE = 32;
    /** How far from the diver, in blocks, the world has dimmed almost to nothing. */
    private static final float DIM_REACH = 40f;
    /** The share of the gap to full or no presence the vision closes each tick, about a second and a half to settle. */
    static final float EASE_PER_TICK = 0.05f;
    /** Below this presence the vision is gone and its chain stops running. */
    static final float GONE = 0.01f;
    /** How far from the diver, in blocks, the scene's own colors still show: the glass field's radius. */
    private static final float NEAR_REACH = (float) SculkerGlass.FIELD_RADIUS;
    private static final float BLOCKS_PER_CHUNK = 16f;
    /** The camera's far plane past its render distance, as {@code Camera} sets it up. */
    private static final float FAR_PER_RENDER_BLOCK = 4f;
    private static final float TICKS_PER_SECOND = 20f;

    private static final OwnedPostEffect SLOT = new OwnedPostEffect(POST_EFFECT_ID, SCULKER_CONFIG_BLOCK);
    private static final Vector4f SCULKER_PARAMS = new Vector4f();
    private static final Vector4f SCULKER_LOOK = new Vector4f();
    private static float presence;

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
     * Eases the senses in while the local player's Sculker stands and out
     * once it ends, and runs the chain on the level while they stand, its
     * depth still whole; the game renderer calls this once a frame, just
     * before it clears the level's depth to draw the hand.
     *
     * @param allocator the game renderer's resource pool
     */
    public static void senseBeforeTheHand(GraphicsResourceAllocator allocator) {
        Minecraft mc = Minecraft.getInstance();
        boolean stands = brewStands(mc);
        presence = easedPresence(presence, stands, mc.getDeltaTracker().getRealtimeDeltaTicks());
        SLOT.hide(mc.gameRenderer);
        if (mc.level == null || !stands && presence < GONE) {
            presence = stands ? presence : 0f;
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float seconds = ((mc.level.getGameTime() % Integer.MAX_VALUE) + partialTick) / TICKS_PER_SECOND;
        float far = Math.max(mc.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK * FAR_PER_RENDER_BLOCK,
                mc.options.cloudRange().get() * BLOCKS_PER_CHUNK);
        SCULKER_PARAMS.set(seconds, NEAR_REACH, far, Camera.PROJECTION_Z_NEAR);
        SCULKER_LOOK.set(presence, 0f, DIM_REACH, 0f);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, SCULKER_CONFIG_SIZE);
            builder.putVec4(SCULKER_PARAMS);
            builder.putVec4(SCULKER_LOOK);
            SLOT.runNow(mc, builder.get(), allocator);
        }
    }
}
