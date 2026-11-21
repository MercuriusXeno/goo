package com.mercuriusxeno.goo.client;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a shader's interface as Minecraft 26.3 compiles it: GLSL compiled to SPIR-V, which pulls a snippet in through
 * {@code #include} alone and matches a fragment shader's inputs to its vertex shader's outputs by location, so each
 * {@code in} and {@code out} carries an explicit location and each fragment input finds a vertex output of its type
 * and interpolation there. The game finds a mismatch only when a GPU compiles the pair, so this reads it headless.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
public final class ShaderInterfaces {

    private static final Pattern DECLARATION = Pattern.compile("^\\s*(?:layout\\s*\\(([^)]*)\\)\\s*)?"
            + "((?:(?:flat|smooth|noperspective|centroid)\\s+)*)(in|out)\\s+(\\w+)\\s+(\\w+)\\s*(?:\\[[^\\]]*])?\\s*;",
            Pattern.MULTILINE);
    private static final Pattern RELATIVE_INCLUDE = Pattern.compile("#include\\s*\"");
    private static final Pattern LOCATION = Pattern.compile("location\\s*=\\s*(\\d+)");
    private static final String SHADERS = "/assets/%s/shaders/%s";

    private ShaderInterfaces() {
    }

    /**
     * One {@code in} or {@code out} a shader declares.
     *
     * @param direction in or out
     * @param type      its GLSL type
     * @param name      its name
     * @param flat      whether it interpolates flat
     * @param location  its explicit location, or null where it names none
     */
    public record Variable(String direction, String type, String name, boolean flat, Integer location) {
    }

    /**
     * @param source a shader's source
     * @return each {@code in} and {@code out} it declares, in source order
     */
    public static List<Variable> variables(String source) {
        List<Variable> variables = new ArrayList<>();
        Matcher declaration = DECLARATION.matcher(source);
        while (declaration.find()) {
            Matcher location = declaration.group(1) == null ? null : LOCATION.matcher(declaration.group(1));
            Integer at = location != null && location.find() ? Integer.valueOf(location.group(1)) : null;
            variables.add(new Variable(declaration.group(3), declaration.group(4), declaration.group(5),
                    declaration.group(2).contains("flat"), at));
        }
        return variables;
    }

    /**
     * The breaks a shader source carries on its own: a {@code #moj_import}, an include by a relative path, and each
     * {@code in} or {@code out} naming no location.
     *
     * @param source a shader's source
     * @return one line for each break, empty where it compiles as 26.3 asks
     */
    public static List<String> sourceBreaks(String source) {
        List<String> breaks = new ArrayList<>();
        if (source.contains("#moj_import")) {
            breaks.add("pulls a snippet in through #moj_import, which 26.3 reads as no directive");
        }
        if (RELATIVE_INCLUDE.matcher(source).find()) {
            breaks.add("includes a snippet by a relative path, where 26.3 finds an include by its id under include alone");
        }
        for (Variable variable : variables(source)) {
            if (variable.location() == null) {
                breaks.add(variable.direction() + " " + variable.name() + " names no location");
            }
        }
        return breaks;
    }

    /**
     * The breaks between a vertex shader and the fragment shader it feeds: each fragment input whose location no
     * vertex output holds, or holds at another type or interpolation.
     *
     * @param vertexSource   the vertex shader's source
     * @param fragmentSource the fragment shader's source
     * @return one line for each break, empty where every fragment input meets its vertex output
     */
    public static List<String> pairBreaks(String vertexSource, String fragmentSource) {
        Map<Integer, Variable> outputs = new HashMap<>();
        for (Variable output : variables(vertexSource)) {
            if ("out".equals(output.direction()) && output.location() != null) {
                outputs.put(output.location(), output);
            }
        }
        List<String> breaks = new ArrayList<>();
        for (Variable input : variables(fragmentSource)) {
            if (!"in".equals(input.direction()) || input.location() == null) {
                continue;
            }
            Variable output = outputs.get(input.location());
            if (output == null) {
                breaks.add("in " + input.name() + " at location " + input.location() + " meets no vertex output");
            } else if (!output.type().equals(input.type()) || output.flat() != input.flat()) {
                breaks.add("in " + input.name() + " at location " + input.location() + " meets out "
                        + output.name() + " of another type or interpolation");
            }
        }
        return breaks;
    }

    /**
     * The breaks between a vertex shader and the vertex formats its pipeline feeds it: each input no format element
     * names, or names at another base type or with fewer components, which 26.3 throws when it builds the pipeline.
     *
     * @param vertexSource the vertex shader's source
     * @param bindings     the vertex formats the pipeline binds, a slot left empty as null
     * @return one line for each break, empty where every input reads an element
     */
    public static List<String> vertexInputBreaks(String vertexSource, List<VertexFormat> bindings) {
        Map<String, GpuFormat> elements = new HashMap<>();
        for (VertexFormat binding : bindings) {
            if (binding != null) {
                binding.getElements().forEach(element -> elements.putIfAbsent(element.name(), element.format()));
            }
        }
        List<String> breaks = new ArrayList<>();
        for (Variable input : variables(vertexSource)) {
            if (!"in".equals(input.direction())) {
                continue;
            }
            GpuFormat format = elements.get(input.name());
            if (format == null) {
                breaks.add("in " + input.name() + " reads no element of the pipeline's vertex format");
            } else if (!baseTypeOf(input.type()).equals(baseTypeOf(format.componentType()))
                    || componentsOf(input.type()) > format.componentCount()) {
                breaks.add("in " + input.name() + " " + input.type() + " reads element " + format);
            }
        }
        return breaks;
    }

    private static String baseTypeOf(String glslType) {
        if (glslType.startsWith("i")) {
            return "int";
        }
        return glslType.startsWith("u") ? "uint" : "float";
    }

    private static String baseTypeOf(GpuFormat.ComponentType componentType) {
        String name = componentType.name();
        if (name.startsWith("SINT")) {
            return "int";
        }
        return name.startsWith("UINT") ? "uint" : "float";
    }

    private static int componentsOf(String glslType) {
        char last = glslType.charAt(glslType.length() - 1);
        return glslType.contains("vec") && Character.isDigit(last) ? last - '0' : 1;
    }

    /**
     * @param shader    a shader's id, its path under shaders without the extension
     * @param extension .vsh or .fsh
     * @return its source, read from the classpath
     */
    public static String source(Identifier shader, String extension) {
        return text(SHADERS.formatted(shader.getNamespace(), shader.getPath() + extension));
    }

    private static String text(String path) {
        try (InputStream in = ShaderInterfaces.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(path + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
    }
}
