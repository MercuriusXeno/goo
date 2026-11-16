#version 330

// Lunar's blue overworld star (decision astral-sky-is-the-void-with-one-bright-blue-overworld-star): each quad of
// its core and halos draws in the color LunarSkyRenderer gave it, added over the sky.

in vec4 starColor;

out vec4 fragColor;

void main() {
    fragColor = vec4(starColor.rgb, 1.0);
}
