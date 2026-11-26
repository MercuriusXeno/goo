package com.mercuriusxeno.goo.client;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every pipeline GooRenderTypes builds binds what its shaders declare: each
 * sampler and texel buffer its bind group layouts name is declared in the
 * shader pair's sources, its imports read in, and so is each uniform block
 * whose name no vanilla RenderPipelines snippet declares; no name stands in
 * two of one pipeline's layouts, which Minecraft 26.2 throws at shader
 * compile. A vanilla snippet's block, Globals among them, goes unread by
 * vanilla's own shaders as by goo's, and the runtime skips an unread block
 * silently, so a misnamed goo block draws wrong with nothing in the log; the
 * shaders compile on a GPU alone, so the mismatch is caught here headless.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
class PipelineBindingsTest {

    private static final Pattern IMPORT = Pattern.compile("#include\\s*<([\\w.-]+):([\\w/.-]+)>");
    private static final String SHADERS = "/assets/%s/shaders/%s";

    @TestFactory
    Stream<DynamicTest> everyDeclaredBindingStandsInTheShaderSources() {
        Set<String> vanillaBlocks = vanillaSnippetUniformBlocks();
        return pipelines().stream().map(pipeline -> DynamicTest.dynamicTest(pipeline.getLocation().toString(), () -> {
            String sources = sourcesOf(pipeline);
            List<String> missing = new ArrayList<>();
            for (String sampler : PipelineReads.samplers(pipeline.getBindGroupLayouts())) {
                if (!declares(sources, "uniform\\s+\\w*sampler\\w*\\s+" + sampler + "\\s*;")) {
                    missing.add("sampler " + sampler);
                }
            }
            for (BindGroupLayout.UniformDescription uniform
                    : PipelineReads.uniforms(pipeline.getBindGroupLayouts())) {
                if (uniform.type() == UniformType.UNIFORM_BUFFER && vanillaBlocks.contains(uniform.name())) {
                    continue;
                }
                String declaration = uniform.type() == UniformType.UNIFORM_BUFFER
                        ? "uniform\\s+" + uniform.name() + "\\s*\\{"
                        : "uniform\\s+\\w*samplerBuffer\\s+" + uniform.name() + "\\s*;";
                if (!declares(sources, declaration)) {
                    missing.add("uniform " + uniform.name());
                }
            }
            assertEquals(List.of(), missing, pipeline.getLocation() + " declares bindings its shaders never name");
        }));
    }

    @TestFactory
    Stream<DynamicTest> noBindingStandsInTwoLayoutsOfOnePipeline() {
        return pipelines().stream().map(pipeline -> DynamicTest.dynamicTest(pipeline.getLocation().toString(), () -> {
            List<String> names = new ArrayList<>(PipelineReads.samplers(pipeline.getBindGroupLayouts()));
            PipelineReads.uniforms(pipeline.getBindGroupLayouts()).forEach(u -> names.add(u.name()));
            Set<String> seen = new HashSet<>();
            List<String> twice = names.stream().filter(name -> !seen.add(name)).toList();
            assertTrue(twice.isEmpty(), pipeline.getLocation() + " declares twice: " + twice);
        }));
    }

    /**
     * Every goo pipeline draws into a color target: 26.2 gave a pipeline naming none the default target, and 26.3
     * gives it none, so a pipeline still leaning on that default would draw nothing.
     * decision minecraft-26-3-update-is-a-port-to-parity
     */
    @TestFactory
    Stream<DynamicTest> everyPipelineDrawsIntoAColorTarget() {
        return pipelines().stream().map(pipeline -> DynamicTest.dynamicTest(pipeline.getLocation().toString(),
                () -> assertNotNull(PipelineReads.colorTarget(pipeline),
                        pipeline.getLocation() + " names no color target")));
    }

    @Test
    void theGoosOwnBlocksAreChecked() {
        Set<String> vanillaBlocks = vanillaSnippetUniformBlocks();
        assertTrue(vanillaBlocks.containsAll(List.of("Globals", "Projection", "Lighting")), vanillaBlocks.toString());
        assertFalse(vanillaBlocks.contains(GooRenderTypes.FIRE_FIELD_BLOCK), vanillaBlocks.toString());
    }

    // The uniform block names the game's own RenderPipelines snippets declare.
    static Set<String> vanillaSnippetUniformBlocks() {
        Set<String> names = new HashSet<>();
        for (Field field : RenderPipelines.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && read(field) instanceof RenderPipeline.Snippet snippet) {
                snippet.bindGroupLayouts().ifPresent(layouts -> BindGroupLayout.flattenUniforms(layouts).stream()
                        .filter(uniform -> uniform.type() == UniformType.UNIFORM_BUFFER)
                        .forEach(uniform -> names.add(uniform.name())));
            }
        }
        return names;
    }

    private static boolean declares(String sources, String declaration) {
        return Pattern.compile(declaration).matcher(sources).find();
    }

    // Each public pipeline field, each phase of a public OIT set field, and each pipeline in a public list field,
    // of GooRenderTypes.
    static List<RenderPipeline> pipelines() {
        List<RenderPipeline> pipelines = new ArrayList<>();
        for (Field field : GooRenderTypes.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object value = read(field);
            if (value instanceof RenderPipeline pipeline) {
                pipelines.add(pipeline);
            } else if (value instanceof OitPipelineSet oit) {
                pipelines.addAll(List.of(oit.depthBoundsPipeline(), oit.transmittancePipeline(), oit.accumulatePipeline()));
            } else if (value instanceof List<?> list) {
                list.stream().filter(RenderPipeline.class::isInstance).map(RenderPipeline.class::cast)
                        .forEach(pipelines::add);
            }
        }
        return pipelines;
    }

    private static Object read(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(field.getName(), e);
        }
    }

    private static String sourcesOf(RenderPipeline pipeline) {
        Set<String> read = new HashSet<>();
        return source(PipelineReads.vertexShader(pipeline), ".vsh", read) + source(PipelineReads.fragmentShader(pipeline), ".fsh", read);
    }

    private static String source(Identifier shader, String extension, Set<String> read) {
        return withImports(text(SHADERS.formatted(shader.getNamespace(), shader.getPath() + extension)), read);
    }

    private static String withImports(String text, Set<String> read) {
        StringBuilder out = new StringBuilder(text);
        Matcher imports = IMPORT.matcher(text);
        while (imports.find()) {
            String path = SHADERS.formatted(imports.group(1), "include/" + imports.group(2));
            if (read.add(path)) {
                out.append('\n').append(withImports(text(path), read));
            }
        }
        return out.toString();
    }

    private static String text(String path) {
        try (InputStream in = PipelineBindingsTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(path + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
    }
}
