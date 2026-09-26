package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypeNames;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import java.util.List;
import java.util.Map;

/**
 * Draws the glove's radial wheel from a {@link RadialWheel}: type wedges
 * through both rings, or receded to the inner ring with the selected
 * type's abilities fanned in the outer ring, each with its icon and label
 * (decision type-recedes-and-abilities-fan-out).
 */
final class RadialWheelRenderer {

    private static final int NORMAL_ALPHA = 0xAA;
    private static final int HOVER_ALPHA = 0xDD;
    private static final int DISABLED_ALPHA = 0x55;
    private static final float DISABLED_DIM = 0.4f;
    private static final int HUB_COLOR = 0x44FFFFFF;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int HOVER_TEXT_COLOR = 0xFFFFFF00;
    private static final int DISABLED_TEXT_COLOR = 0xFF888888;
    /** Gap between the hub circle and the inner ring, as a fraction of the wheel's radius. */
    private static final double HUB_GAP = 0.02;
    private static final int ICON_SIZE = 11;
    private static final int ICON_OFFSET = 5;
    private static final int LABEL_GAP = 1;
    private static final int HALF = 2;
    private static final double MID = 0.5;

    private static final String TYPE_ICON_PREFIX = "textures/goo/type/";
    private static final String ABILITY_ICON_PREFIX = "textures/goo/ability/";
    private static final String ICON_SUFFIX = ".png";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final String MOB_SUFFIX = " (Mob)";

    private static final String ZERO_LABEL = "0";
    private static final int KILO_THRESHOLD = 1_000;
    private static final int MEGA_THRESHOLD = 1_000_000;
    private static final double KILO_DIVISOR = 1_000.0;
    private static final double MEGA_DIVISOR = 1_000_000.0;
    private static final String MB_SUFFIX = " mB";
    private static final String KILO_FORMAT = "%.1fk";
    private static final String MEGA_FORMAT = "%.1fM";

    private RadialWheelRenderer() {
    }

    /**
     * What one frame of the wheel draws from.
     *
     * @param wheel     the wheel's state
     * @param types     the types, one per wedge
     * @param abilities the abilities each type fans out, by type index
     * @param available the mB the player holds per type, snapshot on open
     * @param centerX   the wheel's center x
     * @param centerY   the wheel's center y
     * @param radius    the wheel's outer radius
     */
    record Frame(RadialWheel wheel, List<ResourceKey<GooTypeDefinition>> types,
                 List<List<ClientAbility>> abilities, Map<ResourceKey<GooTypeDefinition>, Integer> available,
                 int centerX, int centerY, int radius) {
    }

    /**
     * Draws the hub, the type wedges, the fan and the center label.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param frame    what the frame draws from
     */
    static void render(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        blitMask(graphics, frame, RadialTextures.getHubTexture(RadialWheel.HUB_FRACTION - HUB_GAP), HUB_COLOR);
        for (int type = 0; type < frame.types().size(); type++) {
            renderType(graphics, frame, type);
        }
        if (frame.wheel().isFanned()) {
            renderFan(graphics, font, frame);
        }
        renderCenterLabel(graphics, font, frame);
    }

    private static void renderType(GuiGraphicsExtractor graphics, Frame frame, int type) {
        RadialWheel wheel = frame.wheel();
        ResourceKey<GooTypeDefinition> key = frame.types().get(type);
        double outer = wheel.isFanned() ? RadialWheel.RING_FRACTION : 1.0;
        boolean selected = type == wheel.selectedType();
        int base = selected ? ClientGooTypes.bright(key) : ClientGooTypes.wheel(key);
        int color = computeWedgeColor(base, selected, frame.available().getOrDefault(key, 0) <= 0);
        blitMask(graphics, frame, RadialTextures.getArcTexture(type * wheel.typeArc(), wheel.typeArc(),
                RadialWheel.HUB_FRACTION, outer), color);
        double iconRadius = (RadialWheel.HUB_FRACTION + outer) * MID * frame.radius();
        blitIcon(graphics, typeIcon(key), frame, wheel.typeCenter(type), iconRadius, COLOR_WHITE);
    }

    private static void renderFan(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        RadialWheel wheel = frame.wheel();
        int type = wheel.selectedType();
        List<ClientAbility> fan = frame.abilities().get(type);
        int base = ClientGooTypes.wheel(frame.types().get(type));
        double arc = wheel.fanArc(type);
        double slotRadius = (RadialWheel.RING_FRACTION + 1.0) * MID * frame.radius();
        for (int ability = 0; ability < fan.size(); ability++) {
            boolean hovered = ability == wheel.hoveredAbility();
            int color = ARGB.color(hovered ? HOVER_ALPHA : NORMAL_ALPHA, base);
            double start = wheel.fanStart(type) + ability * arc;
            blitMask(graphics, frame, RadialTextures.getArcTexture(start, arc, RadialWheel.RING_FRACTION, 1.0), color);
            double middle = start + arc * MID;
            blitIcon(graphics, resolveAbilityIcon(fan.get(ability)), frame, middle, slotRadius, color);
            int[] slot = pointAt(frame, middle, slotRadius);
            graphics.centeredText(font, buildLabel(fan.get(ability)), slot[0], slot[1] + ICON_OFFSET + LABEL_GAP,
                    hovered ? HOVER_TEXT_COLOR : COLOR_WHITE);
        }
    }

    private static void renderCenterLabel(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        RadialWheel wheel = frame.wheel();
        if (!wheel.isFanned()) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = frame.types().get(wheel.selectedType());
        boolean empty = frame.available().getOrDefault(type, 0) <= 0;
        graphics.centeredText(font, Component.translatable(GooTypeNames.translationKey(type)),
                frame.centerX(), frame.centerY() - font.lineHeight / HALF, empty ? DISABLED_TEXT_COLOR : COLOR_WHITE);
    }

    private static void blitMask(GuiGraphicsExtractor graphics, Frame frame, Identifier mask, int color) {
        int size = frame.radius() * HALF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, mask, frame.centerX() - frame.radius(),
                frame.centerY() - frame.radius(), 0.0f, 0.0f, size, size,
                RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE,
                color);
    }

    private static void blitIcon(GuiGraphicsExtractor graphics, Identifier icon, Frame frame,
                                 double angle, double radius, int color) {
        int[] at = pointAt(frame, angle, radius);
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, at[0] - ICON_OFFSET, at[1] - ICON_OFFSET,
                0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, color);
    }

    private static int[] pointAt(Frame frame, double angle, double radius) {
        return new int[]{frame.centerX() + (int) (Math.sin(angle) * radius),
                frame.centerY() - (int) (Math.cos(angle) * radius)};
    }

    private static Identifier typeIcon(ResourceKey<GooTypeDefinition> type) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, TYPE_ICON_PREFIX + GooTypes.id(type) + ICON_SUFFIX);
    }

    /**
     * Computes a wedge's ARGB color, brightened when hovered or selected
     * and dimmed when the player holds none of its goo.
     *
     * @param baseColor the base RGB color from the goo type
     * @param hovered   true for the hovered or selected wedge
     * @param disabled  true when the player holds none of it
     * @return the packed ARGB color
     */
    static int computeWedgeColor(int baseColor, boolean hovered, boolean disabled) {
        int r = ARGB.red(baseColor);
        int g = ARGB.green(baseColor);
        int b = ARGB.blue(baseColor);
        if (disabled) {
            return ARGB.color(DISABLED_ALPHA, (int) (r * DISABLED_DIM), (int) (g * DISABLED_DIM),
                    (int) (b * DISABLED_DIM));
        }
        return ARGB.color(hovered ? HOVER_ALPHA : NORMAL_ALPHA, r, g, b);
    }

    private static Component buildLabel(ClientAbility ability) {
        Component base = Component.translatable(ability.displayName());
        return ability.hasTag(AbilityTags.ENTITY) ? base.copy().append(MOB_SUFFIX) : base;
    }

    /**
     * Resolves the icon texture for an ability. Uses the explicit override
     * if provided, otherwise falls back to convention path.
     *
     * @param ability the client ability descriptor
     * @return the icon texture identifier
     */
    static Identifier resolveAbilityIcon(ClientAbility ability) {
        if (!ability.icon().isEmpty()) {
            // diagnose-then-fix-radial-icon-id: a namespaced icon keeps its namespace rather than taking a second goo:
            return ability.icon().indexOf(NAMESPACE_SEPARATOR) >= 0
                    ? Identifier.parse(ability.icon())
                    : Identifier.fromNamespaceAndPath(Goo.MODID, ability.icon());
        }
        return Identifier.fromNamespaceAndPath(Goo.MODID, ABILITY_ICON_PREFIX + ability.id().getPath() + ICON_SUFFIX);
    }

    /**
     * Formats a mB quantity for display. Shows "0" for zero,
     * abbreviated "1.2k" for thousands, "1.2M" for millions.
     *
     * @param mB the quantity in microblobs
     * @return the human-readable formatted string
     */
    static String formatQuantity(int mB) {
        if (mB <= 0) {
            return ZERO_LABEL;
        }
        if (mB < KILO_THRESHOLD) {
            return mB + MB_SUFFIX;
        }
        if (mB < MEGA_THRESHOLD) {
            return String.format(KILO_FORMAT, mB / KILO_DIVISOR);
        }
        return String.format(MEGA_FORMAT, mB / MEGA_DIVISOR);
    }
}
