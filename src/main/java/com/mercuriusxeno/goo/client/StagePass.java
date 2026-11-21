package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * A goo draw's render pass onto the main target. Minecraft 26.3 holds a render pass open through most level render
 * stages and refuses a second while it stands, so a draw on such a stage draws into the stage's own pass; on a stage
 * holding none, the draw opens its own pass onto the main target, as 26.2's immediate draws did, and closes it after.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
public final class StagePass implements AutoCloseable {

    private static final String OWN_TARGET_IN_STAGE = "A goo draw onto its own target ran inside a level stage's open pass: ";

    private static @Nullable RenderPass stagePass;

    private final RenderPass pass;
    private final boolean opened;

    private StagePass(RenderPass pass, boolean opened) {
        this.pass = pass;
        this.opened = opened;
    }

    /**
     * Runs a stage's goo draws with the stage's own pass standing for every draw onto the main target.
     *
     * @param pass  the pass the stage holds open, or null where it holds none
     * @param draws the stage's draws
     */
    public static void during(@Nullable RenderPass pass, Runnable draws) {
        RenderPass outer = stagePass;
        stagePass = pass;
        try {
            draws.run();
        } finally {
            stagePass = outer;
        }
    }

    /**
     * @return the pass the current stage holds open, or null where it holds none
     */
    public static @Nullable RenderPass current() {
        return stagePass;
    }

    /**
     * The pass a draw onto the main target's color and depth draws through.
     *
     * @param label the pass's label in GPU debuggers, used where the draw opens its own
     * @return the pass
     */
    public static StagePass onMain(Supplier<String> label) {
        if (stagePass != null) {
            return new StagePass(stagePass, false);
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        return new StagePass(RenderSystem.getDevice().createCommandEncoder().createRenderPass(label,
                main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty()), true);
    }

    /**
     * The pass a draw onto the main target's color alone draws through: the stage's own where it holds one open.
     *
     * @param label the pass's label in GPU debuggers, used where the draw opens its own
     * @return the pass
     */
    public static StagePass onMainColor(Supplier<String> label) {
        if (stagePass != null) {
            return new StagePass(stagePass, false);
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        return new StagePass(RenderSystem.getDevice().createCommandEncoder().createRenderPass(label,
                main.getColorTextureView(), Optional.empty()), true);
    }

    /**
     * The pass a draw onto a target of its own draws through, which it opens; a stage's open pass refuses it.
     *
     * @param label  the pass's label in GPU debuggers
     * @param target the target drawn onto, its depth with it where it holds one
     * @return the pass
     */
    public static StagePass onTarget(Supplier<String> label, RenderTarget target) {
        if (stagePass != null) {
            throw new IllegalStateException(OWN_TARGET_IN_STAGE + label.get());
        }
        return new StagePass(RenderSystem.getDevice().createCommandEncoder().createRenderPass(label,
                target.getColorTextureView(), Optional.empty(),
                target.hasDepth() ? target.getDepthTextureView() : null, OptionalDouble.empty()), true);
    }

    /**
     * @return the render pass the draw draws through
     */
    public RenderPass pass() {
        return pass;
    }

    @Override
    public void close() {
        if (opened) {
            pass.close();
        }
    }
}
