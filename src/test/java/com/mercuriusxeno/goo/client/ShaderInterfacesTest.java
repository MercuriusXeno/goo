package com.mercuriusxeno.goo.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every goo shader compiles as Minecraft 26.3 asks: each file pulls its snippets in through {@code #include} and
 * gives each {@code in} and {@code out} an explicit location, and each vertex and fragment shader a pipeline or a post
 * effect pairs meet at every location the fragment shader reads.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
class ShaderInterfacesTest {

    private static final String GOO_SHADERS = "/assets/goo/shaders";
    private static final String POST_EFFECTS = "/assets/goo/post_effect";
    private static final String VERTEX = ".vsh";
    private static final String FRAGMENT = ".fsh";

    @TestFactory
    Stream<DynamicTest> everyGooShaderFileCompilesAs263Asks() throws IOException, URISyntaxException {
        Path root = resourceDirectory(GOO_SHADERS);
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).toList().stream().map(file -> DynamicTest.dynamicTest(
                    root.relativize(file).toString(), () -> assertEquals(List.of(),
                            ShaderInterfaces.sourceBreaks(Files.readString(file, StandardCharsets.UTF_8)),
                            file + " breaks 26.3's shader compile")));
        }
    }

    @TestFactory
    Stream<DynamicTest> everyPairedVertexShaderFeedsEachFragmentInput() throws IOException, URISyntaxException {
        List<Identifier[]> pairs = new ArrayList<>();
        PipelineBindingsTest.pipelines().forEach(pipeline -> pairs.add(new Identifier[] {
                PipelineReads.vertexShader(pipeline), PipelineReads.fragmentShader(pipeline)}));
        pairs.addAll(postEffectPairs());
        assertFalse(pairs.isEmpty(), "no pipeline or post effect pairs a shader");
        return pairs.stream().map(pair -> DynamicTest.dynamicTest(pair[0] + " feeding " + pair[1],
                () -> assertEquals(List.of(), ShaderInterfaces.pairBreaks(ShaderInterfaces.source(pair[0], VERTEX),
                        ShaderInterfaces.source(pair[1], FRAGMENT)), pair[0] + " fails to feed " + pair[1])));
    }

    @TestFactory
    Stream<DynamicTest> everyVertexShaderInputReadsAnElementOfItsPipelinesFormat() {
        return PipelineBindingsTest.pipelines().stream().map(pipeline -> DynamicTest.dynamicTest(
                pipeline.getLocation().toString(), () -> assertEquals(List.of(), ShaderInterfaces.vertexInputBreaks(
                        ShaderInterfaces.source(PipelineReads.vertexShader(pipeline), VERTEX),
                        pipeline.getVertexFormatBindings()), pipeline.getLocation() + " feeds its vertex shader wrong")));
    }

    // Each pass of each goo post effect, its vertex shader beside its fragment shader.
    static List<Identifier[]> postEffectPairs() throws IOException, URISyntaxException {
        List<Identifier[]> pairs = new ArrayList<>();
        try (Stream<Path> files = Files.list(resourceDirectory(POST_EFFECTS))) {
            for (Path file : files.toList()) {
                JsonObject effect = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (JsonElement pass : effect.getAsJsonArray("passes")) {
                    JsonObject named = pass.getAsJsonObject();
                    pairs.add(new Identifier[] {Identifier.parse(named.get("vertex_shader").getAsString()),
                            Identifier.parse(named.get("fragment_shader").getAsString())});
                }
            }
        }
        return pairs;
    }

    private static Path resourceDirectory(String path) throws URISyntaxException {
        return Path.of(ShaderInterfacesTest.class.getResource(path).toURI());
    }

    /** The reads themselves, on shaders built to break them, so a check that finds nothing is no check passing. */
    @Nested
    class TheReadsCatchEachBreak {

        private static final String LAID_VERTEX = """
                layout(location = 0) in vec3 Position;
                layout(location = 0) out vec4 vertexColor;
                layout(location = 1) flat out int corner;
                """;

        @Test
        void aMojImportBreaksTheSource() {
            assertEquals(1, ShaderInterfaces.sourceBreaks("#moj_import <minecraft:fog.glsl>\n").size());
        }

        @Test
        void aRelativeIncludeBreaksTheSource() {
            assertEquals(1, ShaderInterfaces.sourceBreaks("#include \"mingle_noise.glsl\"\n").size());
        }

        @Test
        void anInWithoutALocationBreaksTheSource() {
            assertEquals(List.of("in vertexColor names no location"),
                    ShaderInterfaces.sourceBreaks("in vec4 vertexColor;\n"));
        }

        @Test
        void anOutWithoutALocationBreaksTheSource() {
            assertEquals(1, ShaderInterfaces.sourceBreaks("flat out int corner;\n").size());
        }

        @Test
        void aLaidSourceIncludingItsSnippetsBreaksNothing() {
            assertEquals(List.of(), ShaderInterfaces.sourceBreaks("#include <minecraft:fog.glsl>\n" + LAID_VERTEX));
        }

        @Test
        void aFragmentInputNoVertexOutputHoldsBreaksThePair() {
            assertEquals(1, ShaderInterfaces.pairBreaks(LAID_VERTEX,
                    "layout(location = 2) in vec2 texCoord0;\n").size());
        }

        @Test
        void aFragmentInputOfAnotherTypeBreaksThePair() {
            assertEquals(1, ShaderInterfaces.pairBreaks(LAID_VERTEX,
                    "layout(location = 0) in vec2 vertexColor;\n").size());
        }

        @Test
        void aFragmentInputOfAnotherInterpolationBreaksThePair() {
            assertEquals(1, ShaderInterfaces.pairBreaks(LAID_VERTEX, "layout(location = 1) in int corner;\n").size());
        }

        @Test
        void aVertexInputNoFormatElementNamesBreaksTheInputs() {
            assertEquals(1, ShaderInterfaces.vertexInputBreaks("layout(location = 0) in vec3 Position;\n"
                    + "layout(location = 1) in vec3 Normal;\n", List.of(DefaultVertexFormat.POSITION)).size());
        }

        @Test
        void aVertexInputOfAnotherBaseTypeBreaksTheInputs() {
            assertEquals(1, ShaderInterfaces.vertexInputBreaks("layout(location = 0) in ivec3 Position;\n",
                    List.of(DefaultVertexFormat.POSITION)).size());
        }

        @Test
        void vanillasOwnBlockInputsReadTheBlockFormat() {
            assertEquals(List.of(), ShaderInterfaces.vertexInputBreaks(ShaderInterfaces.source(
                    Identifier.withDefaultNamespace("core/block"), VERTEX), List.of(DefaultVertexFormat.BLOCK)));
        }

        @Test
        void vanillasOwnBlockPairMeetsAtEveryLocation() {
            Identifier block = Identifier.withDefaultNamespace("core/block");
            assertEquals(List.of(), ShaderInterfaces.pairBreaks(ShaderInterfaces.source(block, VERTEX),
                    ShaderInterfaces.source(block, FRAGMENT)));
            assertFalse(ShaderInterfaces.variables(ShaderInterfaces.source(block, FRAGMENT)).isEmpty());
        }
    }
}
