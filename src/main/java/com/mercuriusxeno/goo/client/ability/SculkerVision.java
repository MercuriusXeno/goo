package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.sculk.SculkerSwim;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import java.util.function.Consumer;

/**
 * Sculker's warden senses: while the held effect stands the world dims through
 * {@code goo:sculker_sense} with its distance from the submerged player, black by 6
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
public final class SculkerVision {

    /** The chain at {@code assets/goo/post_effect/sculker_sense.json}. */
    static final Identifier POST_EFFECT_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "sculker_sense");
    private static final String SCULKER_CONFIG_BLOCK = "SculkerConfig";
    /** Std140 size of {@code SculkerConfig}: two vec4s. */
    private static final int SCULKER_CONFIG_SIZE = 32;
    /** The share of the gap to full or no presence the vision closes each tick, about a second and a half to settle. */
    static final float EASE_PER_TICK = 0.05f;
    /** Below this presence the vision is gone and its chain stops running. */
    public static final float GONE = 0.01f;
    private static final float BLOCKS_PER_CHUNK = 16f;
    /** The camera's far plane past its render distance, as {@code Camera} sets it up. */
    private static final float FAR_PER_RENDER_BLOCK = 4f;
    /** The frames an unused render target is kept for, as the game renderer keeps its own. */
    private static final int FRAMES_TO_KEEP_TARGETS = 3;
    /**
     * The profiler sections each of Sculker's world draws reports its frame time under.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     */
    private static final String VISION_DIM_SECTION = "goo_sculker_vision_dim";
    static final String GLASS_DRAW_SECTION = "goo_sculker_glass_draw";
    static final String ECHO_DRAW_SECTION = "goo_sculker_echo_draw";

    private static final OwnedPostEffect SLOT = new OwnedPostEffect(POST_EFFECT_ID, SCULKER_CONFIG_BLOCK);
    private static final Vector4f SCULKER_PARAMS = new Vector4f();
    private static final Vector4f SCULKER_LOOK = new Vector4f();
    private static float presence;
    /** The render targets the chain runs on, kept across frames as the game renderer keeps its own. */
    private static final CrossFrameResourcePool TARGETS = new CrossFrameResourcePool(FRAMES_TO_KEEP_TARGETS);

    private SculkerVision() {
    }

    private static boolean heldEffectStands(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && SculkerSwim.holdsSculker(player);
    }

    /**
     * How fully the vision stands now, easing in as the held effect starts and out as it ends.
     *
     * @return the presence, 0 to 1
     */
    public static float presence() {
        return presence;
    }

    /**
     * The cloud color under the vision: its alpha fades as the vision eases
     * in, so the clouds go dark with the rest of the far world.
     *
     * @param color    the clouds' own color, ARGB
     * @param presence how fully the vision stands, 0 to 1
     * @return the color the clouds draw in
     */
    public static int cloudColor(int color, float presence) {
        return ARGB.color(Math.round(ARGB.alpha(color) * (1f - presence)), color);
    }

    /**
     * Whether the clouds draw not at all, the vision standing full.
     *
     * @param presence how fully the vision stands, 0 to 1
     * @return true once the vision has all but fully eased in
     */
    public static boolean hidesClouds(float presence) {
        return presence > 1f - GONE;
    }

    /**
     * The presence after a frame: it closes a share of the gap to full
     * presence while the held effect stands, and to none otherwise.
     *
     * @param presence   the presence before the frame, 0 to 1
     * @param stands     whether the held effect stands
     * @param deltaTicks the frame's length in ticks
     * @return the presence after the frame
     */
    static float easedPresence(float presence, boolean stands, float deltaTicks) {
        float target = stands ? 1f : 0f;
        return presence + (target - presence) * Math.min(1f, deltaTicks * EASE_PER_TICK);
    }

    /**
     * Eases the senses in while the local player's Sculker stands and out
     * once it ends, the clouds fading with them, and while they stand hands
     * the shared path the chain that dims the world by its depth: a post
     * effect over the finished frame, sorted by nothing, registered ahead
     * of {@link SculkerGlassRenderer#collect} and
     * {@link SculkerEchoRenderer#collect}, so the glass field and the echoes
     * draw over the dimmed world. The path draws it once the level is drawn,
     * before the game renderer clears the level's depth for the hand.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the dim's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        boolean stands = heldEffectStands(mc);
        presence = easedPresence(presence, stands, mc.getDeltaTracker().getRealtimeDeltaTicks());
        SLOT.hide(mc.gameRenderer);
        if (mc.level == null || !stands && presence < GONE) {
            presence = stands ? presence : 0f;
            return;
        }
        draws.accept(new SortedDraw(0, () -> profiled(VISION_DIM_SECTION, () -> dim(mc))));
    }

    /**
     * Runs a draw of the sense view under its profiler section.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     *
     * @param section the section the draw reports its frame time under
     * @param draw    the draw
     */
    static void profiled(String section, Runnable draw) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push(section);
        draw.run();
        profiler.pop();
    }

    private static void dim(Minecraft mc) {
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
