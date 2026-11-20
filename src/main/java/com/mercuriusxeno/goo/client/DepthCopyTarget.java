package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import org.jspecify.annotations.Nullable;

/**
 * The one builder of the targets a goo visual copies the world's depth
 * into: a copy's depth texture must hold its source's format, and a target
 * with a stencil holds a different depth format from one without, so the
 * target answered matches the source in size and in stencil. The builder
 * holds one target for each format, because a portal's pass draws into a
 * target without a stencil between the frame's own passes with one.
 * decision diagnose-and-correct-the-crash-in-every-depth-copying-visual
 * decision depth-copies-stop-rebuilding-every-frame-in-portal-views
 */
public final class DepthCopyTarget {

    private static final int FORMATS = 2;

    private final String label;
    private final HeldShapes shapes = new HeldShapes();
    private final @Nullable TextureTarget[] targets = new TextureTarget[FORMATS];
    private @Nullable TextureTarget last;

    /**
     * A builder whose targets carry a label.
     *
     * @param label the name its targets carry in a frame capture
     */
    public DepthCopyTarget(String label) {
        this.label = label;
    }

    /**
     * The target to copy the source's depth into: the one held for the
     * source's format, built or remade only where it differs from the source.
     *
     * @param source the target whose depth is copied
     * @return a target the source's depth copies into
     */
    public TextureTarget matching(RenderTarget source) {
        Shape wanted = Shape.of(source);
        int format = formatOf(wanted);
        Shape build = shapes.buildFor(wanted);
        TextureTarget held = targets[format];
        if (build != null) {
            if (held != null) {
                held.destroyBuffers();
            }
            held = new TextureTarget(label, build.width(), build.height(), true, build.stencil(),
                    GpuFormat.RGBA8_UNORM);
            targets[format] = held;
        }
        last = held;
        return held;
    }

    /**
     * The target last answered, or null before the first.
     *
     * @return the target last answered
     */
    public @Nullable TextureTarget current() {
        return last;
    }

    private static int formatOf(Shape shape) {
        return shape.stencil() ? 1 : 0;
    }

    /**
     * What a depth texture's format follows: a target's size and whether it carries a stencil.
     *
     * @param width   its width in pixels
     * @param height  its height in pixels
     * @param stencil whether its depth texture carries a stencil
     */
    record Shape(int width, int height, boolean stencil) {

        static Shape of(RenderTarget target) {
            return new Shape(target.width, target.height, target.useStencil);
        }
    }

    /**
     * The shapes of the targets held, one for each format, and the decision
     * of what to build for a source, read without a GPU.
     */
    static final class HeldShapes {

        private final @Nullable Shape[] byFormat = new Shape[FORMATS];

        /**
         * The shape to build so a source's depth copies into the target held
         * for its format, held from then on, or null where that target
         * already matches the source.
         *
         * @param source the shape of the target copied from
         * @return the shape to build, or null where none is built
         */
        @Nullable Shape buildFor(Shape source) {
            int format = formatOf(source);
            Shape build = shapeToBuild(byFormat[format], source);
            if (build != null) {
                byFormat[format] = build;
            }
            return build;
        }
    }

    /**
     * The shape of the target to build so a source's depth copies into it.
     *
     * @param held   the shape of the target held for the source's format, null before the first
     * @param source the shape of the target copied from
     * @return the shape to build, or null where the held target already matches
     */
    static @Nullable Shape shapeToBuild(@Nullable Shape held, Shape source) {
        return source.equals(held) ? null : source;
    }
}
