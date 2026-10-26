package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.Optional;

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
     * Draws the type's icon and leaves its hover area.
     *
     * @param graphics the frame's graphics
     * @param targets  the frame's click areas
     * @param type     the goo type's id
     * @param x        the icon's left edge
     * @param y        the icon's top edge
     */
    static void draw(GuiGraphicsExtractor graphics, ClickTargets targets, String type, int x, int y) {
        blit(graphics, type, x, y);
        addHover(targets, type, x, y);
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
