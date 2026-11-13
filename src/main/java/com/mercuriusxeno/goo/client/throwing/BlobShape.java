package com.mercuriusxeno.goo.client.throwing;

/**
 * A goo blob's box against a flight blob's cube: the shares of the cube's
 * edge its width (x), depth (z) and height (y) take. The core and the shell
 * both draw in it, so they press and stand together.
 * palm-blob-rests-pressed-and-stands-into-the-cube
 *
 * @param width  the share along x
 * @param depth  the share along z
 * @param height the share along y
 */
public record BlobShape(float width, float depth, float height) {

    /** A flight blob's own cube. */
    public static final BlobShape CUBE = new BlobShape(1f, 1f, 1f);

    /**
     * This shape grown or shrunk evenly.
     *
     * @param size the share every edge takes
     * @return the scaled shape
     */
    public BlobShape scaled(float size) {
        return new BlobShape(width * size, depth * size, height * size);
    }
}
