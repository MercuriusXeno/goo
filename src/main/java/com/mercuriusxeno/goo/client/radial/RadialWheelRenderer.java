package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Draws the glove's radial wheel from a {@link RadialWheel}: one ring of
 * petals from the hub to the rim, type petals with their icon alone and the
 * open type's ability petals with icon, name and cost, the open type's name
 * and holdings in the hub.
 * decision abilities-replace-the-hovered-type
 */
final class RadialWheelRenderer {

    private static final int NORMAL_ALPHA = 0xAA;
    private static final int HOVER_ALPHA = 0xDD;
    private static final int DISABLED_ALPHA = 0x55;
    private static final float DISABLED_DIM = 0.4f;
    /** Shade a resting wedge's fluid fill blits under, below the hovered wedge's full white. */
    private static final int REST_SHADE = 0xCC;
    private static final int DISABLED_SHADE = (int) (0xFF * DISABLED_DIM);
    private static final int HUB_COLOR = 0x44FFFFFF;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int HOVER_TEXT_COLOR = 0xFFFFFF00;
    private static final int DISABLED_TEXT_COLOR = 0xFF888888;
    /** The near-black border every word draws inside. */
    static final int OUTLINE_COLOR = 0xFF101010;
    /** How many pixels the border reaches past each side of a word. */
    private static final int OUTLINE_WIDTH = 1;
    private static final int TYPE_ICON_SIZE = 11;
    /** ability-icons-read-16x16 */
    static final int ABILITY_ICON_SIZE = 16;
    private static final int ABILITY_ICON_OFFSET = ABILITY_ICON_SIZE / 2;
    private static final int LABEL_GAP = 1;
    private static final int HALF = 2;

    private static final String TYPE_ICON_PREFIX = "textures/goo/type/";
    private static final String ABILITY_ICON_PREFIX = "textures/goo/ability/";
    private static final String ICON_SUFFIX = ".png";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final String BADGE_ICON_PREFIX = "textures/goo/badge/";
    private static final String WORD_SEPARATOR = "\\s+";


    private RadialWheelRenderer() {
    }

    /**
     * What one frame of the wheel draws from.
     *
     * @param wheel     the wheel's state
     * @param types     the types, one per petal at rest
     * @param abilities the abilities each type opens to, by type index
     * @param available the amount the player holds per type, snapshot on open
     * @param centerX   the wheel's center x
     * @param centerY   the wheel's center y
     * @param radius    the wheel's outer radius
     * @param look        the fluid, edge and colors a petal draws with
     * @param partialTick the fraction of a tick since the last one, for the petals' ease
     */
    record Frame(RadialWheel wheel, List<ResourceKey<GooTypeDefinition>> types,
                 List<List<ClientAbility>> abilities, Map<ResourceKey<GooTypeDefinition>, Integer> available,
                 int centerX, int centerY, int radius, PetalLook look, float partialTick) {
    }

    /**
     * Draws the hub, every petal of the ring and the center label.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param frame    what the frame draws from
     */
    static void render(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        blitMask(graphics, frame, frame.look().hubMask(), HUB_COLOR);
        List<Words> words = new ArrayList<>();
        for (RadialWheel.PetalArc petal : frame.wheel().displayedLayout(frame.partialTick())) {
            if (petal.isAbility()) {
                words.add(renderAbility(graphics, frame, petal));
            } else {
                renderType(graphics, frame, petal);
            }
        }
        // abilities-replace-the-hovered-type: words draw last, on a layer nothing draws over
        for (Words ability : words) {
            drawWords(graphics, font, ability);
        }
        renderCenterLabel(graphics, font, frame);
    }

    /**
     * Draws a type's petal with its icon alone, shrunken or at rest.
     * decision abilities-replace-the-hovered-type
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws from
     * @param petal    the type's petal
     */
    private static void renderType(GuiGraphicsExtractor graphics, Frame frame, RadialWheel.PetalArc petal) {
        ResourceKey<GooTypeDefinition> key = frame.types().get(petal.type());
        boolean selected = petal.type() == frame.wheel().selectedType();
        PetalPainter.paint(graphics, frame, frame.look().fluidFace(key), petal,
                computeOverlayTint(selected, frame.available().getOrDefault(key, 0) <= 0));
        blitIcon(graphics, new Icon(typeIcon(key), TYPE_ICON_SIZE), tipCenter(frame, petal),
                COLOR_WHITE);
    }

    /**
     * Draws an ability petal of the open type with its icon at its tip, and
     * answers the words it shows, which draw after every petal.
     * decision abilities-replace-the-hovered-type
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws from
     * @param petal    the ability's petal
     * @return the ability's words and where they go
     */
    private static Words renderAbility(GuiGraphicsExtractor graphics, Frame frame, RadialWheel.PetalArc petal) {
        ResourceKey<GooTypeDefinition> key = frame.types().get(petal.type());
        ClientAbility ability = frame.abilities().get(petal.type()).get(petal.ability());
        boolean hovered = petal.ability() == frame.wheel().hoveredAbility();
        FanSlot slotLabels = fanSlot(ability, frame.available().getOrDefault(key, 0));
        PetalPainter.paint(graphics, frame, frame.look().fluidFace(key), petal,
                computeOverlayTint(hovered, slotLabels.dimmed()));
        int[] slot = tipCenter(frame, petal);
        blitAbilityIcon(graphics, ability, slot,
                slotLabels.dimmed() ? computeOverlayTint(false, true) : COLOR_WHITE);
        int textColor = slotLabels.dimmed() ? DISABLED_TEXT_COLOR : hovered ? HOVER_TEXT_COLOR : COLOR_WHITE;
        return new Words(splitNameLines(buildLabel(ability).getString()), Component.literal(slotLabels.costLabel()),
                slot, textColor);
    }

    /**
     * An ability's words: its name's lines, its cost, the icon center they
     * gather around and their color.
     *
     * @param name       the name's lines
     * @param cost       the first-throw cost
     * @param iconCenter the icon's center on screen
     * @param color      the words' color
     */
    private record Words(List<Component> name, Component cost, int[] iconCenter, int color) {
    }

    /**
     * The screen point at the center of a petal's round tip, where its icon
     * and words have room to breathe.
     * decision abilities-replace-the-hovered-type
     *
     * @param frame what the frame draws from
     * @param petal the petal
     * @return the point's x and y
     */
    static int[] tipCenter(Frame frame, RadialWheel.PetalArc petal) {
        PetalMask.Point tip = new PetalMask.Petal(petal.start(), petal.arc(), petal.inner(), petal.length())
                .tipCenter();
        return new int[]{frame.centerX() + (int) Math.round(tip.x() * frame.radius()),
                frame.centerY() + (int) Math.round(tip.y() * frame.radius())};
    }

    /**
     * Draws the ability's icon, then its badge untinted directly to the icon's
     * right, where the words never clip it and it never covers the icon.
     * badge-marks-the-target-kind
     *
     * @param graphics the GUI graphics extractor
     * @param ability  the synced ability
     * @param slot     the icon's center
     * @param color    the wedge's tint for the icon
     */
    private static void blitAbilityIcon(GuiGraphicsExtractor graphics, ClientAbility ability, int[] slot, int color) {
        blitIcon(graphics, new Icon(resolveAbilityIcon(ability), ABILITY_ICON_SIZE), slot, color);
        blitIcon(graphics, new Icon(badgeIcon(ability.badge()), ABILITY_ICON_SIZE), badgeBeside(slot), COLOR_WHITE);
    }

    /**
     * The point a badge centers on: one icon's width right of the ability
     * icon's center, on its row, so the badge sits beside the icon edge to edge.
     * badge-marks-the-target-kind
     *
     * @param iconCenter the ability icon's center
     * @return the badge's center
     */
    private static int[] badgeBeside(int[] iconCenter) {
        return new int[]{iconCenter[0] + ABILITY_ICON_SIZE, iconCenter[1]};
    }

    /**
     * The sprite that marks a badge kind.
     * badge-marks-the-target-kind
     *
     * @param badge the ability's declared target kind
     * @return the badge texture under textures/goo/badge/
     */
    static Identifier badgeIcon(AbilityBadge badge) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, BADGE_ICON_PREFIX + badge.getSerializedName() + ICON_SUFFIX);
    }


    /**
     * Breaks a resolved ability name on its spaces into one line per word,
     * so a two-term name fits the petal's width.
     * petal-label-wraps-two-terms
     *
     * @param name the ability's resolved name
     * @return one line per word, a one-word name answering one line
     */
    static List<Component> splitNameLines(String name) {
        return Arrays.stream(name.trim().split(WORD_SEPARATOR))
                .map(word -> (Component) Component.literal(word))
                .toList();
    }

    /**
     * Draws a line of words centered on a point with a dark border all the
     * way around, so light words stand out against the brightest goos: the
     * line eight times one pixel off in each direction in the border's color,
     * then once on top in its own, the way glowing signs outline theirs.
     * decision abilities-replace-the-hovered-type
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param line     the words
     * @param centerX  the x the line centers on
     * @param y        the line's top
     * @param color    the words' color
     */
    static void outlinedText(GuiGraphicsExtractor graphics, Font font, Component line, int centerX, int y, int color) {
        int left = centerX - font.width(line) / HALF;
        for (int dx = -OUTLINE_WIDTH; dx <= OUTLINE_WIDTH; dx++) {
            for (int dy = -OUTLINE_WIDTH; dy <= OUTLINE_WIDTH; dy++) {
                if (dx != 0 || dy != 0) {
                    graphics.text(font, line, left + dx, y + dy, OUTLINE_COLOR, false);
                }
            }
        }
        graphics.text(font, line, left, y, color, false);
    }

    /**
     * Draws an ability's name centered directly above its icon and its cost
     * centered directly below it, on screen.
     * decision abilities-replace-the-hovered-type
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param words    the ability's words and their icon
     */
    private static void drawWords(GuiGraphicsExtractor graphics, Font font, Words words) {
        int x = words.iconCenter()[0];
        int nameY = words.iconCenter()[1] - ABILITY_ICON_OFFSET - LABEL_GAP - words.name().size() * font.lineHeight;
        for (Component line : words.name()) {
            outlinedText(graphics, font, line, x, nameY, words.color());
            nameY += font.lineHeight;
        }
        outlinedText(graphics, font, words.cost(), x, words.iconCenter()[1] + ABILITY_ICON_OFFSET + LABEL_GAP,
                words.color());
    }

    /**
     * What an ability petal of the open type reads: its first-throw cost, dimmed
     * when it exceeds the type's holdings (decision radial-shows-first-throw-cost-and-holdings).
     *
     * @param costLabel the first-throw cost, formatted
     * @param dimmed    true when the first throw costs more than the holdings
     */
    record FanSlot(String costLabel, boolean dimmed) {
    }

    /**
     * Reads an ability wedge's first-throw cost against the type's holdings.
     *
     * @param ability  the synced ability
     * @param holdings the amount the player holds of its type
     * @return the wedge's labels
     */
    static FanSlot fanSlot(ClientAbility ability, int holdings) {
        int firstThrow = ability.throwCost(0);
        return new FanSlot(GooFormat.formatAmount(firstThrow), firstThrow > holdings);
    }

    /**
     * The center's holdings line for the selected type.
     *
     * @param holdings the amount the player holds of the type
     * @return the holdings, formatted
     */
    static String holdingsLabel(int holdings) {
        // hud-amounts-read-through-goo-format: the machine panels' goo convention
        return GooFormat.formatAmount(holdings);
    }

    private static void renderCenterLabel(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        RadialWheel wheel = frame.wheel();
        if (!wheel.isOpen()) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = frame.types().get(wheel.selectedType());
        int holdings = frame.available().getOrDefault(type, 0);
        int textColor = holdings <= 0 ? DISABLED_TEXT_COLOR : COLOR_WHITE;
        outlinedText(graphics, font, Component.translatable(GooTypeNames.translationKey(type)),
                frame.centerX(), frame.centerY() - font.lineHeight, textColor);
        outlinedText(graphics, font, Component.literal(holdingsLabel(holdings)),
                frame.centerX(), frame.centerY() + LABEL_GAP, textColor);
    }

    private static void blitMask(GuiGraphicsExtractor graphics, Frame frame, Identifier mask, int color) {
        int size = frame.radius() * HALF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, mask, frame.centerX() - frame.radius(),
                frame.centerY() - frame.radius(), 0.0f, 0.0f, size, size,
                RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE,
                color);
    }

    /**
     * An icon texture and its square size, drawn at the size its source is.
     *
     * @param texture the icon's texture
     * @param size    the source's width and height in pixels, and the drawn size
     */
    private record Icon(Identifier texture, int size) {
    }

    private static void blitIcon(GuiGraphicsExtractor graphics, Icon icon, int[] center, int color) {
        int offset = icon.size() / HALF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), center[0] - offset, center[1] - offset,
                0.0f, 0.0f, icon.size(), icon.size(), icon.size(), icon.size(), color);
    }


    private static Identifier typeIcon(ResourceKey<GooTypeDefinition> type) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, TYPE_ICON_PREFIX + GooTypes.id(type) + ICON_SUFFIX);
    }

    /**
     * Computes the tint a wedge's textured mask blits under, so the fluid
     * fill still reads hovered and disabled (decision wedges-render-fluid-texture):
     * a hovered wedge is brighter and more opaque than a resting one, a
     * disabled one darker and dimmer.
     *
     * @param hovered  true for the hovered or selected wedge
     * @param disabled true when the player holds none of it, or cannot afford it
     * @return the packed ARGB tint
     */
    static int computeOverlayTint(boolean hovered, boolean disabled) {
        if (disabled) {
            return ARGB.color(DISABLED_ALPHA, DISABLED_SHADE, DISABLED_SHADE, DISABLED_SHADE);
        }
        return hovered ? ARGB.color(HOVER_ALPHA, COLOR_WHITE) : ARGB.color(NORMAL_ALPHA, REST_SHADE, REST_SHADE, REST_SHADE);
    }

    /**
     * The petal's name label: the bare name, the badge marking the target kind.
     * badge-marks-the-target-kind
     *
     * @param ability the synced ability
     * @return the ability's translatable name
     */
    static Component buildLabel(ClientAbility ability) {
        return Component.translatable(ability.displayName());
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
}
