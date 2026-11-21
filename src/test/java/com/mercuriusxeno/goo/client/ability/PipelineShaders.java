package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.PipelineReads;
import com.mercuriusxeno.goo.client.ShaderInterfaces;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Asserts the vertex and fragment shaders a pipeline names are files on the classpath, each compiling as Minecraft
 * 26.3 asks and meeting the other at every location the fragment shader reads.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
final class PipelineShaders {

    private PipelineShaders() {
    }

    /**
     * @param pipeline the pipeline whose shader pair must exist
     */
    static void assertExist(RenderPipeline pipeline) {
        Identifier vertex = PipelineReads.vertexShader(pipeline);
        Identifier fragment = PipelineReads.fragmentShader(pipeline);
        assertShaderExists(vertex, ".vsh");
        assertShaderExists(fragment, ".fsh");
        String vertexSource = ShaderInterfaces.source(vertex, ".vsh");
        String fragmentSource = ShaderInterfaces.source(fragment, ".fsh");
        List<String> breaks = new ArrayList<>(ShaderInterfaces.sourceBreaks(vertexSource));
        breaks.addAll(ShaderInterfaces.sourceBreaks(fragmentSource));
        breaks.addAll(ShaderInterfaces.pairBreaks(vertexSource, fragmentSource));
        assertEquals(List.of(), breaks, pipeline.getLocation() + " breaks 26.3's shader compile");
    }

    private static void assertShaderExists(Identifier shader, String extension) {
        String path = "/assets/" + shader.getNamespace() + "/shaders/" + shader.getPath() + extension;
        assertNotNull(PipelineShaders.class.getResource(path), path + " is not on the classpath");
    }
}
