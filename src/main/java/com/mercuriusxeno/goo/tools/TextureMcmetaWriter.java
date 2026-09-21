package com.mercuriusxeno.goo.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes .mcmeta animation sidecar files for fluid strips and blob sprites.
 * Fluid strips use simple looping; blob sprites use ping-pong interpolation.
 */
final class TextureMcmetaWriter {

    /** Number of animation frames per texture strip. */
    private static final int FRAMES = 32;
    /** Offset from end of frame list to start the reverse pass (skip last frame already included). */
    private static final int PING_PONG_REVERSE_OFFSET = 2;
    /** Frame separator for mcmeta frame lists. */
    private static final String FRAME_SEPARATOR = ",";

    private TextureMcmetaWriter() {}

    /**
     * Writes a looping mcmeta for a fluid sprite strip.
     *
     * @param frametime the animation frame time in ticks
     * @param outputDir the directory to write into
     * @param filename  the mcmeta filename to write
     * @throws IOException if the file cannot be written
     */
    static void writeFluidMcmeta(int frametime, Path outputDir, String filename) throws IOException {
        String mcmeta = """
                {"animation": {"frametime": %d, "interpolate": true}}
                """.formatted(frametime);
        Files.writeString(outputDir.resolve(filename), mcmeta);
    }

    /**
     * Writes a ping-pong mcmeta for a blob sprite strip.
     *
     * @param frametime the animation frame time in ticks
     * @param outputDir the directory to write into
     * @param filename  the mcmeta filename to write
     * @throws IOException if the file cannot be written
     */
    static void writeBlobMcmeta(int frametime, Path outputDir, String filename) throws IOException {
        String mcmeta = """
                {"animation": {"frametime": %d, "interpolate": true, "frames": [%s]}}
                """.formatted(frametime, buildPingPongFrames());
        Files.writeString(outputDir.resolve(filename), mcmeta);
    }

    /**
     * Builds a ping-pong frame list: 0,1,...,FRAMES-1,FRAMES-2,...,1
     *
     * @return the comma-separated frame index list
     */
    private static StringBuilder buildPingPongFrames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FRAMES; i++) {
            if (i > 0) { sb.append(FRAME_SEPARATOR); }
            sb.append(i);
        }
        for (int i = FRAMES - PING_PONG_REVERSE_OFFSET; i >= 1; i--) {
            sb.append(FRAME_SEPARATOR).append(i);
        }
        return sb;
    }
}
