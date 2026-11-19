package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.network.ClientAbilities;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.item.SprayerSetting;
import com.mercuriusxeno.goo.network.SprayerSelectPayload;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * The sprayer's wheel, open while the glove menu key is held with the sprayer in hand: one ring of three arcs
 * shown together, the canister's sprayable goo types drawn in their own goo, the flow steps as one, two and three
 * drops, and the nozzles in gold, each with its icon. Letting go of the key over a petal sets that one setting and
 * keeps the other two; letting go over the hub or a gap closes with the sprayer unchanged. The petal under the
 * cursor names itself in the hub.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision radial-selects-on-g-release
 * operator ruling 2026-11-18: layout A, one ring of three arcs; nozzles and flow carry icons, flow as drops
 */
public final class SprayerRadialScreen extends Screen {

    private static final int HALF = 2;
    /** Where the ring starts and ends, as fractions of the wheel's radius. */
    private static final double RING_INNER = 0.3;
    private static final double RING_OUTER = 0.85;
    private static final int ICON_SIZE = 24;
    private static final int WHITE = 0xFFFFFFFF;
    /** The tint the petal under the cursor draws under. */
    private static final int HOVERED = 0xFFFFF29A;
    /** The tint every petal not under the cursor and not the current setting draws under. */
    private static final int RESTING = 0xB0C8C8C8;
    private static final Identifier GOLD = Identifier.withDefaultNamespace("textures/block/gold_block.png");
    private static final Identifier PLAIN = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final int GOLD_EDGE = 0xFF8A6512;
    private static final int PLAIN_EDGE = 0xFF8C8C8C;
    private static final String ICON_FOLDER = "textures/gui/sprayer/";
    private static final String FLOW_ICON = "flow_";
    private static final String NOZZLE_ICON = "nozzle_";
    private static final String PNG = ".png";
    /** The type id a pick naming no type sends, leaving the canister's most held sprayable type to spray. */
    private static final String NO_TYPE_PICKED = "";

    private final SprayerWheel model;
    private final List<SprayerWheel.RingPetal> ring;
    private SprayerSetting kept;
    private int hovered = SprayerWheel.NONE;

    private SprayerRadialScreen(SprayerWheel model, SprayerSetting kept) {
        super(Component.empty());
        this.model = model;
        this.ring = model.ring();
        this.kept = kept;
    }

    /**
     * Opens the wheel over the held sprayer: its canister's sprayable goo types, none for an empty sprayer, beside
     * the flow steps and the nozzles, which an empty sprayer sets too.
     * operator report 2026-11-18: an empty sprayer opened no wheel
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack sprayer = SprayerItem.heldSprayer(player);
        ClientAbilities synced = ClientAbilities.current();
        SprayerWheel model = SprayerWheel.of(CanisterItem.getFluidContent(SprayerItem.fittedCanister(sprayer)),
                type -> synced.forType(type).stream().anyMatch(ability -> ability.badge() == AbilityBadge.RESIDUAL));
        mc.setScreen(new SprayerRadialScreen(model, SprayerItem.settingOf(sprayer)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int radius() {
        return (int) RadialWheel.outerRadius(width, height);
    }

    @Override
    public void mouseMoved(double x, double y) {
        double dx = x - width / (double) HALF;
        double dy = y - height / (double) HALF;
        double distance = Math.hypot(dx, dy) / radius();
        hovered = distance < RING_INNER ? SprayerWheel.NONE : model.petalAt(RadialWheel.angleOf(dx, dy));
    }

    /**
     * Paints every petal, its icon or its goo's name on it, and the hovered petal's name in the hub.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDelta) {
        super.extractBackground(graphics, mouseX, mouseY, frameDelta);
        Canvas canvas = new Canvas(width / HALF, height / HALF, radius());
        for (int petal = 0; petal < ring.size(); petal++) {
            paintPetal(graphics, canvas, petal);
        }
        if (hovered != SprayerWheel.NONE) {
            RadialWheelRenderer.outlinedText(graphics, font, nameOf(ring.get(hovered)), canvas.centerX(),
                    canvas.centerY() - font.lineHeight / HALF, WHITE);
        }
    }

    private void paintPetal(GuiGraphicsExtractor graphics, Canvas canvas, int petal) {
        SprayerWheel.RingPetal at = ring.get(petal);
        double start = model.startOf(petal);
        RadialWheel.PetalArc arc = new RadialWheel.PetalArc(petal, RadialWheel.NONE, start, model.petalArc(),
                RING_OUTER, null, 0, 1, RING_INNER);
        int tint = petal == hovered ? HOVERED : isCurrent(at) ? WHITE : RESTING;
        PetalPainter.paint(graphics, canvas, faceOf(at), arc, tint);
        int[] middle = middleOf(canvas, start + model.petalArc() / HALF);
        if (at.group() == SprayerWheel.Group.GOO) {
            RadialWheelRenderer.outlinedText(graphics, font, nameOf(at), middle[0], middle[1] - font.lineHeight / HALF,
                    WHITE);
        } else {
            PetalPainter.paintSprite(graphics, canvas, arc.shape(), PetalLook.LIVE.sprite(iconOf(at)), middle,
                    ICON_SIZE, WHITE);
        }
    }

    private boolean isCurrent(SprayerWheel.RingPetal at) {
        return switch (at.group()) {
            case GOO -> kept.pickedType().map(model.types().get(at.index())::equals).orElse(at.index() == 0);
            case FLOW -> kept.flow() == model.flows().get(at.index());
            case NOZZLE -> kept.nozzle() == model.nozzles().get(at.index());
        };
    }

    private PetalLook.FluidFace faceOf(SprayerWheel.RingPetal at) {
        return switch (at.group()) {
            case GOO -> PetalLook.LIVE.fluidFace(model.types().get(at.index()));
            case FLOW -> wholeTexture(PLAIN, PLAIN_EDGE);
            case NOZZLE -> wholeTexture(GOLD, GOLD_EDGE);
        };
    }

    private static PetalLook.FluidFace wholeTexture(Identifier texture, int edge) {
        return new PetalLook.FluidFace(PetalLook.LIVE.sprite(texture), new PetalLook.SpriteBox(0f, 1f, 0f, 1f), WHITE,
                edge);
    }

    private Identifier iconOf(SprayerWheel.RingPetal at) {
        String name = at.group() == SprayerWheel.Group.FLOW
                ? FLOW_ICON + model.flows().get(at.index()).getSerializedName()
                : NOZZLE_ICON + model.nozzles().get(at.index()).getSerializedName();
        return Identifier.fromNamespaceAndPath(Goo.MODID, ICON_FOLDER + name + PNG);
    }

    private Component nameOf(SprayerWheel.RingPetal at) {
        return switch (at.group()) {
            case GOO -> GooTypeNames.name(model.types().get(at.index()));
            case FLOW -> Component.translatable(model.flows().get(at.index()).langKey());
            case NOZZLE -> Component.translatable(model.nozzles().get(at.index()).langKey());
        };
    }

    private static int[] middleOf(Canvas canvas, double angle) {
        double distance = (RING_INNER + RING_OUTER) / HALF * canvas.radius();
        return new int[]{canvas.centerX() + (int) Math.round(Math.sin(angle) * distance),
                canvas.centerY() - (int) Math.round(Math.cos(angle) * distance)};
    }

    /**
     * Reads the glove menu key's release, since an open screen takes the keyboard from the mapping.
     *
     * @param event the key release event
     * @return true if the event was handled
     */
    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!GloveRadialKey.MAPPING.matches(event)) {
            return super.keyReleased(event);
        }
        if (hovered != SprayerWheel.NONE) {
            keep(model.picked(kept, ring.get(hovered)));
        }
        onClose();
        return true;
    }

    /**
     * Keeps a pick on the held sprayer here and on the server.
     *
     * @param picked the new setting
     */
    private void keep(SprayerSetting picked) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack sprayer = mc.player == null ? ItemStack.EMPTY : SprayerItem.heldSprayer(mc.player);
        if (sprayer.isEmpty()) {
            return;
        }
        kept = picked;
        SprayerItem.keepSetting(sprayer, picked);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new SprayerSelectPayload(
                    picked.pickedType().map(GooTypes::id).orElse(NO_TYPE_PICKED), picked.flow(), picked.nozzle())));
        }
    }

    /**
     * Where the sprayer's wheel draws.
     *
     * @param centerX the wheel's center, x
     * @param centerY the wheel's center, y
     * @param radius  the wheel's outer radius
     */
    private record Canvas(int centerX, int centerY, int radius) implements PetalCanvas {
    }
}
