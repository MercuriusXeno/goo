package com.mercuriusxeno.goo.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every goo pipeline's shaders compile to SPIR-V as Minecraft 26.3's client compiles them: through shaderc for
 * Vulkan, each snippet found by its id under include, each pipeline's own defines set. The game compiles them only on
 * a client with a GPU, where a break shows as a pipeline that draws nothing, so the compile runs here headless.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
class ShaderCompileTest {

    /** shaderc's Vulkan 1.1 target, the environment GlslCompiler compiles for. */
    private static final int VULKAN_1_1 = (1 << 22) | (1 << 12);
    private static final int TARGET_VULKAN = 0;
    private static final int VERTEX_KIND = 0;
    private static final int FRAGMENT_KIND = 1;
    private static final Map<String, ShaderSource.CachedIncludeSource> INCLUDES = new HashMap<>();
    private static final ShaderSource.CachedIncludeSource MISSING = ShaderSource.CachedIncludeSource.createError(
            "not found");

    @AfterAll
    static void releaseIncludes() {
        INCLUDES.values().forEach(ShaderSource.CachedIncludeSource::close);
        INCLUDES.clear();
    }

    @TestFactory
    Stream<DynamicTest> everyGooPipelinesShadersCompileToSpirV() {
        List<RenderPipeline> pipelines = PipelineBindingsTest.pipelines();
        return pipelines.stream().map(pipeline -> DynamicTest.dynamicTest(pipeline.getLocation().toString(), () -> {
            List<String> errors = new ArrayList<>();
            compile(PipelineReads.vertexShader(pipeline), ".vsh", VERTEX_KIND, pipeline.getShaderDefines(), errors);
            compile(PipelineReads.fragmentShader(pipeline), ".fsh", FRAGMENT_KIND, pipeline.getShaderDefines(),
                    errors);
            if (!errors.isEmpty()) {
                fail(pipeline.getLocation() + " fails 26.3's compile:\n" + String.join("\n", errors));
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> everyGooPostEffectPassCompilesToSpirV() throws IOException, URISyntaxException {
        return ShaderInterfacesTest.postEffectPairs().stream().map(pair -> DynamicTest.dynamicTest(
                pair[0] + " feeding " + pair[1], () -> {
                    List<String> errors = new ArrayList<>();
                    compile(pair[0], ".vsh", VERTEX_KIND, ShaderDefines.EMPTY, errors);
                    compile(pair[1], ".fsh", FRAGMENT_KIND, ShaderDefines.EMPTY, errors);
                    if (!errors.isEmpty()) {
                        fail(pair[1] + " fails 26.3's compile:\n" + String.join("\n", errors));
                    }
                }));
    }

    private static void compile(Identifier shader, String extension, int kind, ShaderDefines defines,
                                List<String> errors) {
        String name = shader + extension;
        String source = ShaderInterfaces.source(shader, extension);
        long options = Shaderc.shaderc_compile_options_initialize();
        Shaderc.shaderc_compile_options_set_target_env(options, TARGET_VULKAN, VULKAN_1_1);
        Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
        Shaderc.shaderc_compile_options_set_preserve_bindings(options, false);
        defines.values().forEach((key, value) -> Shaderc.shaderc_compile_options_add_macro_definition(options, key,
                value));
        defines.flags().forEach(flag -> Shaderc.shaderc_compile_options_add_macro_definition(options, flag, ""));
        ByteBuffer text = MemoryUtil.memUTF8(source, false);
        ByteBuffer file = MemoryUtil.memUTF8(name);
        ByteBuffer entry = MemoryUtil.memUTF8("main");
        long compiler = Shaderc.shaderc_compiler_initialize();
        try (ShadercIncludeResolve resolve = ShadercIncludeResolve.create((data, requested, type, requesting, depth)
                -> include(MemoryUtil.memASCII(requested)).includeResultPtr());
             ShadercIncludeResultRelease release = ShadercIncludeResultRelease.create((data, result) -> { })) {
            Shaderc.shaderc_compile_options_set_include_callbacks(options, resolve, release, 0L);
            long result = Shaderc.shaderc_compile_into_spv(compiler, text, kind, file, entry, options);
            if (Shaderc.shaderc_result_get_compilation_status(result) != 0) {
                errors.add(name + ": " + Shaderc.shaderc_result_get_error_message(result));
            }
            Shaderc.shaderc_result_release(result);
        } finally {
            Shaderc.shaderc_compiler_release(compiler);
            Shaderc.shaderc_compile_options_release(options);
            MemoryUtil.memFree(file);
            MemoryUtil.memFree(entry);
        }
    }

    // A snippet by its id, read from the include folder of its namespace, as ShaderManager keys its includes.
    private static synchronized ShaderSource.CachedIncludeSource include(String requested) {
        Identifier id = Identifier.tryParse(requested);
        if (id == null) {
            return MISSING;
        }
        return INCLUDES.computeIfAbsent(id.toString(), key -> {
            String path = "/assets/" + id.getNamespace() + "/shaders/include/" + id.getPath();
            try (InputStream in = ShaderCompileTest.class.getResourceAsStream(path)) {
                return in == null ? MISSING : ShaderSource.CachedIncludeSource.create(id,
                        new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(path, e);
            }
        });
    }
}
