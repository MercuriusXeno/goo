package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A goo type drawn as its icon from the mod's own type texture, bare beside its amount, as the radial wheel and the
 * in-world panel draw it, with a hover area whose tooltip names the type (decision
 * goo-types-draw-as-icons-named-on-hover).
 */
final class GooTypeIcons {
    /** The icon's side. */
    static final int SIZE = 16;
    private static final String TEXTURE_PREFIX = "textures/goo/type/";
    private static final String TEXTURE_SUFFIX = ".png";
    // A hover key a prefab name never spells, since a name holds no colon.
    private static final String HOVER_PREFIX = "goo-type:";
    private static final String PICK_PREFIX = "goo-type-pick:";
    private static final String PICK_NOTE = " (click to pick another)";
    private static final int OPAQUE = 0xFFFFFFFF;
    private static final int PNG_WIDTH_OFFSET = 16;
    private static final Map<String, Integer> SIZES = new ConcurrentHashMap<>();

    private GooTypeIcons() {
    }

    /**
     * @param type a goo type's id
     * @return its icon texture
     */
    static Identifier texture(String type) {
        return Identifier.fromNamespaceAndPath(GooTypes.NAMESPACE, TEXTURE_PREFIX + type + TEXTURE_SUFFIX);
    }

    /**
     * @param type a goo type's id
     * @return its icon's side as its texture draws it one texel per pixel, read once from the texture
     */
    static int sizeOf(String type) {
        return SIZES.computeIfAbsent(type, each -> sizeOf(each, GooTypeIcons::openTexture));
    }

    /**
     * @param type   a goo type's id
     * @param opener opens a texture's png, empty where the resource is missing
     * @return the png's width, the icon's declared side where the texture is missing or unreadable
     */
    static int sizeOf(String type, Function<Identifier, Optional<InputStream>> opener) {
        return opener.apply(texture(type)).map(GooTypeIcons::pngWidth).orElse(SIZE);
    }

    private static Optional<InputStream> openTexture(Identifier texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture).flatMap(resource -> {
            try {
                return Optional.of(resource.open());
            } catch (IOException missing) {
                return Optional.empty();
            }
        });
    }

    // A png's width is the big-endian int after its signature and the IHDR chunk's length and name.
    private static int pngWidth(InputStream png) {
        try (DataInputStream in = new DataInputStream(png)) {
            in.skipNBytes(PNG_WIDTH_OFFSET);
            return in.readInt();
        } catch (IOException unreadable) {
            return SIZE;
        }
    }

    /**
     * Draws the type's icon and leaves its hover area.
     *
     * @param graphics the frame's graphics
     * @param targets  the frame's click areas
     * @param type     the goo type's id
     * @param x        the icon's left edge
     * @param y        the icon's top edge
     */
    static void draw(GuiGraphicsExtractor graphics, ClickTargets targets, String type, int x, int y) {
        draw(graphics, targets, type, x, y, SIZE);
    }

    /**
     * Draws the type's icon at a side of its own, its hover area the same size.
     *
     * @param graphics the frame's graphics
     * @param targets  the frame's click areas
     * @param type     the goo type's id
     * @param x        the icon's left edge
     * @param y        the icon's top edge
     * @param size     the icon's side
     */
    static void draw(GuiGraphicsExtractor graphics, ClickTargets targets, String type, int x, int y, int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(type), x, y, 0.0f, 0.0f, size, size, size, size, OPAQUE);
        targets.addHover(x, y, size, size, HOVER_PREFIX + type);
    }

    /**
     * Draws the type's icon alone, for a caller that leaves its own hover area.
     *
     * @param graphics the frame's graphics
     * @param type     the goo type's id
     * @param x        the icon's left edge
     * @param y        the icon's top edge
     */
    static void blit(GuiGraphicsExtractor graphics, String type, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(type), x, y, 0.0f, 0.0f, SIZE, SIZE, SIZE, SIZE, OPAQUE);
    }

    /**
     * @param type a goo type's id
     * @return the hover key whose tooltip names it
     */
    static String hoverKey(String type) {
        return HOVER_PREFIX + type;
    }

    /**
     * @param targets the frame's click areas
     * @param type    the goo type's id
     * @param x       the icon's left edge
     * @param y       the icon's top edge
     */
    static void addHover(ClickTargets targets, String type, int x, int y) {
        targets.addHover(x, y, SIZE, SIZE, HOVER_PREFIX + type);
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the type's name where the area is a type icon's, empty otherwise
     */
    static Optional<Component> tooltipFor(String hoverKey) {
        if (hoverKey.startsWith(PICK_PREFIX)) {
            return Optional.of(nameOf(hoverKey.substring(PICK_PREFIX.length())).copy().append(PICK_NOTE));
        }
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(nameOf(hoverKey.substring(HOVER_PREFIX.length())));
    }

    /**
     * @param type a goo type's id
     * @return the hover key whose tooltip names it and says a click picks another
     */
    static String pickHoverKey(String type) {
        return PICK_PREFIX + type;
    }

    /**
     * @param type a goo type's id
     * @return the type's translated name
     */
    static Component nameOf(String type) {
        return Component.translatable(GooTypeNames.translationKey(GooTypes.bundled(type)));
    }

}
