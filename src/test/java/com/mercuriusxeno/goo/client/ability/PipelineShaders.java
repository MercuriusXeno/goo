package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.PipelineReads;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Asserts the vertex and fragment shaders a pipeline names are files on the classpath.
 */
final class PipelineShaders {

    private PipelineShaders() {
    }

    /**
     * @param pipeline the pipeline whose shader pair must exist
     */
    static void assertExist(RenderPipeline pipeline) {
        assertShaderExists(PipelineReads.vertexShader(pipeline), ".vsh");
        assertShaderExists(PipelineReads.fragmentShader(pipeline), ".fsh");
    }

    private static void assertShaderExists(Identifier shader, String extension) {
        String path = "/assets/" + shader.getNamespace() + "/shaders/" + shader.getPath() + extension;
        assertNotNull(PipelineShaders.class.getResource(path), path + " is not on the classpath");
    }
}
