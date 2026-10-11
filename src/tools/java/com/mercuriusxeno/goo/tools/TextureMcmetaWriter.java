package com.mercuriusxeno.goo.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes .mcmeta animation sidecar files for fluid strips and goo sprites.
 * Both ping-pong, so the cellular automaton strip never jumps from its last
 * frame back to its first; the fluid list spaces its separators the way the
 * shipped fluid sidecars do.
 */
final class TextureMcmetaWriter {

    /** Number of animation frames per texture strip. */
    private static final int FRAMES = 32;
    /** Offset from end of frame list to start the reverse pass (skip last frame already included). */
    private static final int PING_PONG_REVERSE_OFFSET = 2;
    /** Frame separator for goo sprite frame lists. */
    private static final String GOO_FRAME_SEPARATOR = ",";
    /** Frame separator for fluid strip frame lists. */
    private static final String FLUID_FRAME_SEPARATOR = ", ";

    private TextureMcmetaWriter() {}

    /**
     * Writes a ping-pong mcmeta for a fluid sprite strip.
     *
     * @param frametime the animation frame time in ticks
     * @param outputDir the directory to write into
     * @param filename  the mcmeta filename to write
     * @throws IOException if the file cannot be written
     */
    static void writeFluidMcmeta(int frametime, Path outputDir, String filename) throws IOException {
        Files.writeString(outputDir.resolve(filename), fluidMcmeta(frametime));
    }

    /**
     * The sidecar text for a fluid sprite strip.
     *
     * @param frametime the animation frame time in ticks
     * @return the mcmeta JSON, newline-terminated
     */
    static String fluidMcmeta(int frametime) {
        return """
                {"animation": {"frametime": %d, "interpolate": true, "frames": [%s]}}
                """.formatted(frametime, buildPingPongFrames(FLUID_FRAME_SEPARATOR));
    }

    /**
     * Writes a ping-pong mcmeta for a goo sprite strip.
     *
     * @param frametime the animation frame time in ticks
     * @param outputDir the directory to write into
     * @param filename  the mcmeta filename to write
     * @throws IOException if the file cannot be written
     */
    static void writeGooMcmeta(int frametime, Path outputDir, String filename) throws IOException {
        String mcmeta = """
                {"animation": {"frametime": %d, "interpolate": true, "frames": [%s]}}
                """.formatted(frametime, buildPingPongFrames(GOO_FRAME_SEPARATOR));
        Files.writeString(outputDir.resolve(filename), mcmeta);
    }

    /**
     * Builds a ping-pong frame list: 0,1,...,FRAMES-1,FRAMES-2,...,1
     *
     * @param separator the text between frame indices
     * @return the frame index list
     */
    private static StringBuilder buildPingPongFrames(String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FRAMES; i++) {
            if (i > 0) { sb.append(separator); }
            sb.append(i);
        }
        for (int i = FRAMES - PING_PONG_REVERSE_OFFSET; i >= 1; i--) {
            sb.append(separator).append(i);
        }
        return sb;
    }
}
