package com.mercuriusxeno.goo.client;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.jspecify.annotations.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

/**
 * The parts of a pipeline the tests read, as 26.2's pipeline answered them: Minecraft 26.3 keeps a pipeline's
 * shaders in one map by stage, its color targets in a list, its stencil test in its depth state, and its samplers
 * among its bind group uniforms.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
public final class PipelineReads {

    private PipelineReads() {
    }

    /**
     * @param pipeline the pipeline
     * @return the vertex shader it names
     */
    public static Identifier vertexShader(RenderPipeline pipeline) {
        return pipeline.getShaders().get(ShaderType.VERTEX);
    }

    /**
     * @param pipeline the pipeline
     * @return the fragment shader it names
     */
    public static Identifier fragmentShader(RenderPipeline pipeline) {
        return pipeline.getShaders().get(ShaderType.FRAGMENT);
    }

    /**
     * @param pipeline the pipeline
     * @return its first color target's state, or null where it writes none
     */
    public static @Nullable ColorTargetState colorTarget(RenderPipeline pipeline) {
        List<@Nullable ColorTargetState> targets = pipeline.getColorTargetStates();
        return targets.isEmpty() ? null : targets.getFirst();
    }

    /**
     * @param pipeline the pipeline
     * @return the stencil test its depth state holds, if any
     */
    public static Optional<StencilTest> stencilTest(RenderPipeline pipeline) {
        DepthStencilState depth = pipeline.getDepthStencilState();
        return depth == null ? Optional.empty() : Optional.ofNullable(depth.stencilTest());
    }


    /**
     * @param layouts the bind group layouts
     * @return the buffer and texel buffer uniforms they declare, in order, as 26.2 kept them apart from its samplers
     */
    public static List<BindGroupLayout.UniformDescription> uniforms(List<BindGroupLayout> layouts) {
        return BindGroupLayout.flattenUniforms(layouts).stream()
                .filter(uniform -> uniform.type() != UniformType.COMBINED_IMAGE_SAMPLER).toList();
    }

    /**
     * @param layouts the bind group layouts
     * @return the names of the samplers they declare, in order
     */
    public static List<String> samplers(List<BindGroupLayout> layouts) {
        return BindGroupLayout.flattenUniforms(layouts).stream()
                .filter(uniform -> uniform.type() == UniformType.COMBINED_IMAGE_SAMPLER)
                .map(BindGroupLayout.UniformDescription::name).toList();
    }

    /**
     * Reads the set a render type's setup attached through RenderSetup.setOitPipelines, which the type keeps
     * private and answers only through a prepare that needs the GPU.
     * decision goo-translucency-draws-through-order-independent-transparency
     *
     * @param renderType the render type
     * @return the OIT pipeline set its setup carries, or null where it carries none
     */
    public static @Nullable OitPipelineSet oitPipelines(RenderType renderType) {
        try {
            Field setupField = RenderType.class.getDeclaredField("state");
            setupField.setAccessible(true);
            Object setup = setupField.get(renderType);
            Field oitField = RenderSetup.class.getDeclaredField("oitPipelineSet");
            oitField.setAccessible(true);
            return (OitPipelineSet) oitField.get(setup);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RenderType no longer keeps its OIT set where 26.3 kept it", e);
        }
    }
}
