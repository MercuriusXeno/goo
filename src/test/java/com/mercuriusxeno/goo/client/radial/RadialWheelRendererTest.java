package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.ArmPose;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.CostMechanicsLookup;
import com.mercuriusxeno.goo.client.network.OfferedAbility;
import net.minecraft.world.item.ItemStack;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Matrix3x2fStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers RadialWheelRenderer.resolveAbilityIcon over every shipped ability, as the
 * ability sync hands each one to the client (decision diagnose-then-fix-radial-icon-id),
 * and the fan's cost and holdings labels.
 */
class RadialWheelRendererTest {

    private static final String ASSETS_ROOT = "assets/";
    private static final String TEXTURE_PATH = "textures/goo/ability/unstable_lurker.png";

    private static ClientAbility clientAbilityOf(AbilityDefinition definition) {
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.behaviors(),
                definition.cost().in(GooValueTable.EMPTY), definition.delivery(), definition.badge(), definition.requires());
    }

    /**
     * The shipped abilities the radial lists: every one the ability sync sends the glove.
     *
     * @return the radial's shipped abilities
     */
    private static List<ClientAbility> shippedAbilities() {
        return AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .map(RadialWheelRendererTest::clientAbilityOf)
                .toList();
    }

    /** A wheel with the cursor moved onto one type's petal tip, the fan landed. */
    static RadialWheel openedWheel(int types, int abilities, int openType, int radius) {
        RadialWheel wheel = new RadialWheel(types, type -> abilities);
        double angle = wheel.layout().stream().filter(petal -> petal.type() == openType).findFirst().orElseThrow()
                .center();
        double reach = (RadialWheel.TYPE_INNER_FRACTION + 1.0) / 2 * radius;
        wheel.moveCursor(Math.sin(angle) * reach, -Math.cos(angle) * reach, radius);
        for (int tick = 0; tick < RingEase.DURATION_TICKS; tick++) {
            wheel.tick();
        }
        return wheel;
    }

    private static ClientAbility abilityWithIcon(String icon) {
        return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_lurker"),
                "ability.goo.unstable_lurker", icon, 0, List.of(), List.of(), 0, Delivery.ARC, AbilityBadge.WORLD, List.of());
    }

    /** The badge, not the label, marks the target kind (decision badge-marks-the-target-kind). */
    @Nested
    class Badges {

        @Test
        void entityAbilityLabelReadsTheBareName() {
            ClientAbility entity = new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "hex_charm"),
                    "goo.ability.hex.charm", "", 0, List.of(AbilityTags.ENTITY), List.of(), 0, Delivery.ARC, AbilityBadge.MOB, List.of());

            assertEquals(Component.translatable("goo.ability.hex.charm"), RadialWheelRenderer.buildLabel(entity));
        }

        /** decision badges-halve-and-sit-up-right-and-closer */
        @Test
        void everyBadgeKindDrawsFromAn8x8SpriteUnderGooBadge() {
            assertAll(Arrays.stream(AbilityBadge.values()).map(badge -> (Executable) () -> {
                Identifier sprite = RadialWheelRenderer.badgeIcon(badge);
                assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID,
                        "textures/goo/badge/" + badge.getSerializedName() + ".png"), sprite);
                assertEquals(List.of(RadialWheelRenderer.BADGE_SIZE, RadialWheelRenderer.BADGE_SIZE),
                        pngDimensions(ASSETS_ROOT + sprite.getNamespace() + "/" + sprite.getPath()), badge.name());
            }));
        }

        /**
         * The badge folder ships a sprite only where a badge reads it, so a retired badge leaves no sprite behind.
         * decision clock-badge-marks-durative-abilities
         */
        @Test
        void theBadgeFolderHoldsExactlyTheSpritesTheBadgesRead() throws IOException, URISyntaxException {
            URL folder = RadialWheelRendererTest.class.getClassLoader()
                    .getResource(ASSETS_ROOT + Goo.MODID + "/textures/goo/badge");
            assertNotNull(folder);
            Set<String> shipped;
            try (Stream<Path> sprites = Files.list(Path.of(folder.toURI()))) {
                shipped = sprites.map(sprite -> sprite.getFileName().toString()).collect(Collectors.toSet());
            }
            assertEquals(Arrays.stream(AbilityBadge.values()).map(badge -> badge.getSerializedName() + ".png")
                    .collect(Collectors.toSet()), shipped);
        }

        /** decision hovering-a-badge-names-its-kind */
        @Test
        void theLangFileNamesEveryBadgeKind() throws IOException {
            JsonObject lang;
            try (InputStream in = RadialWheelRendererTest.class.getResourceAsStream("/assets/goo/lang/en_us.json")) {
                lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            assertAll(Arrays.stream(AbilityBadge.values()).map(badge -> (Executable) () -> {
                String key = "goo.badge." + badge.getSerializedName();
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
            }));
        }
    }

    @Nested
    class ShippedAbilities {

        @Test
        void everyShippedAbilityIconExistsUnderGooAssets() {
            assertAll(shippedAbilities().stream().map(ability -> (Executable) () -> {
                Identifier icon = RadialWheelRenderer.resolveAbilityIcon(ability);
                assertEquals(Goo.MODID, icon.getNamespace(), ability.id().toString());
                String resource = ASSETS_ROOT + icon.getNamespace() + "/" + icon.getPath();
                assertNotNull(RadialWheelRendererTest.class.getClassLoader().getResource(resource),
                        ability.id() + " resolves " + icon + " but no " + resource + " is on the classpath");
                // ability-icons-read-16x16
                assertEquals(List.of(RadialWheelRenderer.ABILITY_ICON_SIZE, RadialWheelRenderer.ABILITY_ICON_SIZE),
                        pngDimensions(resource), resource);
            }));
        }

        /**
         * Every shipped ability draws an icon of its own: no two share bytes, and none is a type icon as it
         * stands or stretched to the ability icon's size.
         * decision every-icon-its-own-drawing
         * decision rock-abilities-get-icons
         */
        @Test
        void everyShippedAbilityDrawsAnIconOfItsOwn() throws IOException {
            Map<String, List<String>> abilitiesByIcon = new HashMap<>();
            List<String> typeIcons = new ArrayList<>();
            for (ClientAbility ability : shippedAbilities()) {
                Identifier icon = RadialWheelRenderer.resolveAbilityIcon(ability);
                String resource = ASSETS_ROOT + icon.getNamespace() + "/" + icon.getPath();
                byte[] bytes = pngBytes(resource);
                abilitiesByIcon.computeIfAbsent(HexFormat.of().formatHex(bytes), key -> new ArrayList<>())
                        .add(ability.id().getPath());
                BufferedImage drawn = ImageIO.read(new ByteArrayInputStream(bytes));
                for (ResourceKey<GooTypeDefinition> type : GooTypes.BUNDLED) {
                    String typeResource = ASSETS_ROOT + Goo.MODID + "/textures/goo/type/" + GooTypes.id(type) + ".png";
                    if (isTypeIcon(drawn, bytes, pngBytes(typeResource))) {
                        typeIcons.add(ability.id().getPath() + " is the " + GooTypes.id(type) + " type icon");
                    }
                }
            }
            List<List<String>> shared = abilitiesByIcon.values().stream().filter(group -> group.size() > 1)
                    .map(group -> group.stream().sorted().toList()).sorted(Comparator.comparing(List::getFirst))
                    .toList();
            assertAll(
                    () -> assertEquals(List.of(), shared, "ability icons sharing bytes"),
                    () -> assertEquals(List.of(), typeIcons, "ability icons that are type icons"));
        }
    }

    /** Pixels of a stretched type icon may differ from the source at its resampled seams; nine in ten agree. */
    private static final double STRETCHED_AGREEMENT = 0.9;

    /**
     * Whether an ability icon is a type icon: the same bytes, or the type icon stretched to the ability icon's
     * size, which nearest sampling back to the type icon's size undoes.
     */
    private static boolean isTypeIcon(BufferedImage drawn, byte[] drawnBytes, byte[] typeBytes) throws IOException {
        if (Arrays.equals(drawnBytes, typeBytes)) {
            return true;
        }
        BufferedImage type = ImageIO.read(new ByteArrayInputStream(typeBytes));
        int agree = 0;
        for (int y = 0; y < type.getHeight(); y++) {
            for (int x = 0; x < type.getWidth(); x++) {
                int sampled = drawn.getRGB((2 * x + 1) * drawn.getWidth() / (2 * type.getWidth()),
                        (2 * y + 1) * drawn.getHeight() / (2 * type.getHeight()));
                agree += sampled == type.getRGB(x, y) ? 1 : 0;
            }
        }
        return agree >= STRETCHED_AGREEMENT * type.getWidth() * type.getHeight();
    }

    private static byte[] pngBytes(String resource) throws IOException {
        try (InputStream stream = RadialWheelRendererTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " is not on the classpath");
            return stream.readAllBytes();
        }
    }

    private static final int IHDR_WIDTH_OFFSET = 16;
    private static final int IHDR_END = 24;

    /** Reads a classpath PNG's width and height from its IHDR chunk, which follows the 8-byte signature and the chunk's length and type. */
    static List<Integer> pngDimensions(String resource) throws IOException {
        try (InputStream stream = RadialWheelRendererTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " is not on the classpath");
            ByteBuffer header = ByteBuffer.wrap(stream.readNBytes(IHDR_END));
            return List.of(header.getInt(IHDR_WIDTH_OFFSET), header.getInt(IHDR_WIDTH_OFFSET + Integer.BYTES));
        }
    }

    /** Each petal reads a short name, a two-term one wrapping to a line per word (decisions ability-names-are-one-word, petal-label-wraps-two-terms). */
    @Nested
    class ShortNames {

        private static final String LANG_RESOURCE = "assets/goo/lang/en_us.json";
        /** A petal label holds one term, or two where it wraps to a line per word. */
        private static final int MOST_LABEL_TERMS = 2;

        private static JsonObject englishLang() throws IOException {
            try (InputStream stream = RadialWheelRendererTest.class.getClassLoader().getResourceAsStream(LANG_RESOURCE)) {
                assertNotNull(stream, LANG_RESOURCE + " is not on the classpath");
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }

        @Test
        void everyShippedAbilityReadsItsShortName() throws IOException {
            JsonObject lang = englishLang();
            List<ClientAbility> shipped = shippedAbilities();
            assertFalse(shipped.isEmpty(), "no shipped ability scanned");
            assertAll(shipped.stream().map(ability -> (Executable) () -> {
                JsonElement label = lang.get(ability.displayName());
                assertNotNull(label, ability.id() + " names " + ability.displayName() + ", which en_us.json lacks");
                String[] terms = label.getAsString().strip().split("\\s+");
                assertTrue(!terms[0].isEmpty() && terms.length <= MOST_LABEL_TERMS,
                        ability.id() + " reads \"" + label.getAsString() + "\"");
            }));
        }

        /** portals-link-by-looking-through-portal-vision: Quantum's portal ability reads Portal. */
        @Test
        void quantumPortalReadsPortal() throws IOException {
            assertEquals("Portal", englishLang().get("goo.ability.quantum.portal").getAsString());
        }

        @Test
        void twoWordNameSplitsIntoALinePerWord() {
            assertEquals(List.of("Frost", "Disc"), RadialWheelRenderer.splitNameLines("Frost Disc").stream()
                    .map(Component::getString).toList());
        }

        @Test
        void oneWordNameStaysOneLine() {
            assertEquals(List.of("Stasis"), RadialWheelRenderer.splitNameLines("Stasis").stream()
                    .map(Component::getString).toList());
        }
    }

    /**
     * Each badge, eight pixels square, stands right of the ability's icon without touching it, and the icon and
     * badges together center on the cluster's point, with and without a prism badge.
     * decision badges-halve-and-sit-up-right-and-closer
     * decision abilities-replace-the-hovered-type
     */
    @Nested
    class IconClusterLayout {

        private static final int[] CENTER = {200, 120};
        private static final int HALF_BADGE_SIDE = 8;
        /** How far a cluster's middle may sit off its point: half a pixel, an odd width's rounding. */
        private static final double CENTERING_SLACK = 0.5;
        /** A line far wider than any badge's reach, so only height keeps the words off the badges. */
        private static final int WIDE_LINE = 400;
        private static final int LINE_HEIGHT = 9;

        private List<RadialWheelRenderer.IconCluster> clusters() {
            return List.of(RadialWheelRenderer.clusterAround(CENTER, AbilityBadge.WORLD, false),
                    RadialWheelRenderer.clusterAround(CENTER, AbilityBadge.WORLD, true));
        }

        @Test
        void everyBadgeIsEightPixelsSquareRightOfTheIconWithoutTouchingIt() {
            assertAll(clusters().stream().flatMap(cluster -> cluster.badges().stream().map(badge -> (Executable) () -> {
                RadialWheelRenderer.ItemRect icon = cluster.icon();
                assertEquals(HALF_BADGE_SIDE, badge.rect().size(), badge.badge() + " side");
                assertEquals(RadialWheelRenderer.ABILITY_ICON_SIZE, icon.size(), "icon side");
                assertTrue(badge.rect().left() > icon.left() + icon.size(),
                        badge.badge() + " left " + badge.rect().left() + ", icon right " + (icon.left() + icon.size()));
            })));
        }

        /**
         * Every badge, with or without the prism badge, stands below the name's
         * lines placed round the same tip and above the cost line, so none meets
         * the words however wide they run.
         * decision badges-halve-and-sit-up-right-and-closer
         */
        @Test
        void everyBadgeStandsBelowTheNameAndAboveTheCost() {
            Component word = Component.literal("word");
            List<RadialWheelRenderer.PlacedLine> words = RadialWheelRenderer.placeWords(List.of(word, word), word,
                    CENTER, line -> WIDE_LINE, LINE_HEIGHT);
            double nameBottom = words.get(1).box()[3];
            double costTop = words.getLast().box()[1];
            assertAll(clusters().stream().flatMap(cluster -> cluster.badges().stream()).map(badge -> (Executable) () -> {
                RadialWheelRenderer.ItemRect square = badge.rect();
                assertTrue(nameBottom <= square.top(), badge.badge() + " top " + square.top() + ", name bottom "
                        + nameBottom);
                assertTrue(costTop >= square.top() + square.size(), badge.badge() + " bottom "
                        + (square.top() + square.size()) + ", cost top " + costTop);
            }));
        }

        @Test
        void iconAndBadgesCenterTogetherOnThePoint() {
            assertAll(clusters().stream().map(cluster -> (Executable) () -> {
                List<RadialWheelRenderer.ItemRect> squares = new ArrayList<>(List.of(cluster.icon()));
                cluster.badges().forEach(badge -> squares.add(badge.rect()));
                double left = squares.stream().mapToInt(RadialWheelRenderer.ItemRect::left).min().orElseThrow();
                double right = squares.stream().mapToInt(square -> square.left() + square.size()).max().orElseThrow();
                double top = squares.stream().mapToInt(RadialWheelRenderer.ItemRect::top).min().orElseThrow();
                double bottom = squares.stream().mapToInt(square -> square.top() + square.size()).max().orElseThrow();
                assertTrue(Math.abs((left + right) / 2 - CENTER[0]) <= CENTERING_SLACK,
                        "middle x " + (left + right) / 2 + " for " + squares.size() + " squares");
                assertTrue(Math.abs((top + bottom) / 2 - CENTER[1]) <= CENTERING_SLACK,
                        "middle y " + (top + bottom) / 2 + " for " + squares.size() + " squares");
            }));
        }
    }

    /**
     * Where an ability wears two badges, the prism badge draws first, so behind, fanned up and right of its partner,
     * which covers only the prism badge's bottom-left corner, at most two pixels each way, and the cursor names each
     * from its center.
     * decision prism-badge-sits-behind-and-above-barely-overlapping
     * decision hovering-a-badge-names-its-kind
     */
    @Nested
    class PrismBadgeLayout {

        private static final int[] ICON_CENTER = {200, 120};
        private static final int MOST_OVERLAP = 2;

        private final List<RadialWheelRenderer.BadgeIcon> layout = RadialWheelRenderer.clusterAround(ICON_CENTER,
                AbilityBadge.WORLD, true).badges();

        private RadialWheelRenderer.BadgeIcon prism() {
            return layout.stream().filter(badge -> badge.badge() == AbilityBadge.PRISM).findFirst().orElseThrow();
        }

        private RadialWheelRenderer.BadgeIcon partner() {
            return layout.stream().filter(badge -> badge.badge() == AbilityBadge.WORLD).findFirst().orElseThrow();
        }

        private static int[] centerOf(RadialWheelRenderer.ItemRect square) {
            return new int[]{square.left() + square.size() / 2, square.top() + square.size() / 2};
        }

        @Test
        void prismBadgeDrawsBeforeItsPartner() {
            assertEquals(List.of(AbilityBadge.PRISM, AbilityBadge.WORLD),
                    layout.stream().map(RadialWheelRenderer.BadgeIcon::badge).toList());
        }

        @Test
        void prismBadgeFansUpAndRightWithOnlyItsBottomLeftCornerCovered() {
            RadialWheelRenderer.ItemRect prism = prism().rect();
            RadialWheelRenderer.ItemRect partner = partner().rect();
            int across = Math.min(prism.left() + prism.size(), partner.left() + partner.size())
                    - Math.max(prism.left(), partner.left());
            int down = Math.min(prism.top() + prism.size(), partner.top() + partner.size())
                    - Math.max(prism.top(), partner.top());
            assertAll(
                    () -> assertTrue(centerOf(prism)[0] > centerOf(partner)[0],
                            "prism center x " + centerOf(prism)[0] + ", partner's " + centerOf(partner)[0]),
                    () -> assertTrue(centerOf(prism)[1] < centerOf(partner)[1],
                            "prism center y " + centerOf(prism)[1] + ", partner's " + centerOf(partner)[1]),
                    () -> assertTrue(across >= 1 && across <= MOST_OVERLAP && down >= 1 && down <= MOST_OVERLAP,
                            "overlap " + across + " across, " + down + " down"),
                    () -> assertEquals(List.of(prism.left(), prism.top() + prism.size()),
                            List.of(partner.left() + partner.size() - across, partner.top() + down),
                            "the covered part is the prism badge's bottom-left corner"));
        }

        /** A pixel of a square that none of the squares drawn over it covers. */
        private static int[] aPointShowing(RadialWheelRenderer.ItemRect square,
                                           List<RadialWheelRenderer.ItemRect> covers) {
            for (int y = square.top(); y < square.top() + square.size(); y++) {
                for (int x = square.left(); x < square.left() + square.size(); x++) {
                    int px = x;
                    int py = y;
                    if (covers.stream().noneMatch(cover -> cover.contains(px, py))) {
                        return new int[]{x, y};
                    }
                }
            }
            throw new AssertionError(square + " lies wholly under " + covers);
        }

        /**
         * A cursor resting on a petal's own badge names its kind in a tooltip
         * beside it, and on the prism badge peeking out under it names Prism.
         * decision hovering-a-badge-names-its-kind
         */
        @Test
        void aBadgeUnderTheCursorNamesItsKindInATooltip() {
            RadialWheelRenderer.Hoverables hoverables = new RadialWheelRenderer.Hoverables(List.of(), layout);
            RadialWheelRenderer.ItemRect own = partner().rect();
            int[] onOwn = {own.left() + own.size() / 2, own.top() + own.size() - 1};
            int[] onPrismBelowOwn = aPointShowing(prism().rect(), List.of(own));
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Font font = mock(Font.class);

            RadialWheelRenderer.nameWhatTheCursorRestsOn(graphics, font, hoverables, onOwn[0], onOwn[1]);
            RadialWheelRenderer.nameWhatTheCursorRestsOn(graphics, font, hoverables, onPrismBelowOwn[0],
                    onPrismBelowOwn[1]);

            verify(graphics).setTooltipForNextFrame(font,
                    Component.translatable("goo.badge." + AbilityBadge.WORLD.getSerializedName()), onOwn[0], onOwn[1]);
            verify(graphics).setTooltipForNextFrame(font,
                    Component.translatable("goo.badge." + AbilityBadge.PRISM.getSerializedName()),
                    onPrismBelowOwn[0], onPrismBelowOwn[1]);
        }

        @Test
        void eachBadgeNamesItsKindFromItsCenter() {
            int[] prismCenter = centerOf(prism().rect());
            int[] partnerCenter = centerOf(partner().rect());
            assertAll(
                    () -> assertEquals(Component.translatable("goo.badge." + AbilityBadge.PRISM.getSerializedName()),
                            RadialWheelRenderer.badgeNameUnder(layout, prismCenter[0], prismCenter[1]), "prism"),
                    () -> assertEquals(Component.translatable("goo.badge." + AbilityBadge.WORLD.getSerializedName()),
                            RadialWheelRenderer.badgeNameUnder(layout, partnerCenter[0], partnerCenter[1]),
                            "partner"));
        }
    }

    /**
     * An ability's name draws a little under full size and its cost smaller still, so in every shipped fan, on a wheel
     * showing any number of the shipped types and opened at any slot, at the radius a 1080p screen draws at GUI scale
     * 3 and at 4, no two of the fan's petals' words meet: each petal reads its English name and the widest cost label
     * its ability shows, each line measured by the vanilla font's glyph advances.
     * decision ability-name-shrinks-some-and-other-text-more
     */
    @Nested
    class PetalWords {

        private static final int CENTER = 500;
        /** The vanilla font's advance for most glyphs: five pixels of ink and one of spacing. */
        private static final int GLYPH_ADVANCE = 6;
        /** The vanilla font's narrowest advance, a one-pixel glyph such as i. */
        private static final int NARROWEST_ADVANCE = 2;
        private static final int LINE_HEIGHT = 9;
        private static final String ABILITY_KEY_PREFIX = "goo.ability.";
        /** How many samples of holdings each power of ten takes. */
        private static final int STEPS_PER_DECADE = 10;
        /** The most holdings a petal's label is read at, in mB: a billion. */
        private static final int MOST_HOLDINGS = 1_000_000_000;
        /** How many samples reach from one mB to {@link #MOST_HOLDINGS}. */
        private static final int HOLDING_STEPS = 9 * STEPS_PER_DECADE;
        /** A 1080p screen's GUI width at GUI scale 3, and at GUI scale 4. */
        private static final int SCALE_THREE_WIDTH = 640;
        private static final int SCALE_FOUR_WIDTH = 480;

        @Test
        void nameDrawsUnderFullSizeAndTheCostSmallerStill() {
            assertAll(
                    () -> assertTrue(RadialWheelRenderer.NAME_SCALE < 1f, "name scale " + RadialWheelRenderer.NAME_SCALE),
                    () -> assertTrue(RadialWheelRenderer.DETAIL_SCALE < RadialWheelRenderer.NAME_SCALE,
                            "cost scale " + RadialWheelRenderer.DETAIL_SCALE));
        }

        @ParameterizedTest
        @ValueSource(ints = {SCALE_THREE_WIDTH, SCALE_FOUR_WIDTH})
        void noTwoPetalsWordsMeetInAnyShippedFanAtAnyWheelTheShippedTypesCanShow(int guiWidth) throws IOException {
            int radius = (int) RadialWheel.outerRadius(guiWidth, guiWidth * 9 / 16);
            JsonObject lang = ShortNames.englishLang();
            Map<String, List<ClientAbility>> fans = shippedFans();
            List<String> meetings = new ArrayList<>();
            int pairs = 0;
            for (Map.Entry<String, List<ClientAbility>> fan : fans.entrySet()) {
                List<List<Component>> names = fan.getValue().stream().map(ability -> RadialWheelRenderer
                        .splitNameLines(lang.get(ability.displayName()).getAsString())).toList();
                List<Component> costs = fan.getValue().stream().map(PetalWords::widestLabel).toList();
                for (int types = 1; types <= fans.size(); types++) {
                    for (int open = 0; open < types; open++) {
                        List<List<RadialWheelRenderer.PlacedLine>> words = fanWords(types, open, radius, names, costs);
                        for (int one = 0; one < words.size(); one++) {
                            for (int other = one + 1; other < words.size(); other++) {
                                pairs++;
                                for (String meeting : meetings(words.get(one), words.get(other))) {
                                    meetings.add(fan.getKey() + " on a " + types + "-type wheel opened at slot " + open
                                            + ", petals " + one + " and " + other + ": " + meeting);
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(pairs > 0, "no pair of petals checked");
            assertEquals(List.of(), meetings);
        }

        /** Each petal's placed words in one open fan, in fan order. */
        private List<List<RadialWheelRenderer.PlacedLine>> fanWords(int types, int open, int radius,
                                                                    List<List<Component>> names, List<Component> costs) {
            RadialWheel wheel = openedWheel(types, names.size(), open, radius);
            RadialWheelRenderer.Frame frame = new RadialWheelRenderer.Frame(wheel, List.of(), List.of(), Map.of(),
                    CENTER, CENTER, radius, mock(PetalLook.class), 0.0f);
            return wheel.displayedLayout(0.0f).stream()
                    .filter(petal -> petal.isAbility() && petal.type() == open)
                    .sorted(Comparator.comparingInt(RadialWheel.PetalArc::ability))
                    .map(petal -> RadialWheelRenderer.placeWords(names.get(petal.ability()), costs.get(petal.ability()),
                            RadialWheelRenderer.tipCenter(frame, petal), PetalWords::vanillaWidth, LINE_HEIGHT))
                    .toList();
        }

        /** Each pair of lines, one from each petal, whose boxes meet, as the two lines' words. */
        private static List<String> meetings(List<RadialWheelRenderer.PlacedLine> one,
                                             List<RadialWheelRenderer.PlacedLine> other) {
            return one.stream().flatMap(line -> other.stream().filter(across -> meet(line.box(), across.box()))
                    .map(across -> line.text().getString() + " meets " + across.text().getString())).toList();
        }

        private static boolean meet(double[] one, double[] other) {
            return one[0] < other[2] && other[0] < one[2] && one[1] < other[3] && other[1] < one[3];
        }

        /** Each shipped type's abilities in fan order, by the type named in their display key. */
        private static Map<String, List<ClientAbility>> shippedFans() {
            return AbilityJson.files().stream()
                    .map(AbilityJson::decode)
                    .map(definition -> ClientAbility.fromEntry(com.mercuriusxeno.goo.network.AbilitySyncPayload
                            .gloveEntries(definition.gooType(), List.of(definition)).getFirst()))
                    .sorted(AbilityBadge.fanOrder(ClientAbility::badge, ClientAbility::order))
                    .collect(Collectors.groupingBy(ability -> ability.displayName()
                            .substring(ABILITY_KEY_PREFIX.length()).split("\\.")[0]));
        }

        /**
         * The widest cost line an ability's petal reads, by the vanilla font: its petal label over holdings from
         * none to {@link #MOST_HOLDINGS}, sampled ten to a decade with each power of ten less one, every reagent
         * plentiful and no upkeep standing, so every count width the amount and duration formats print is met.
         */
        private static Component widestLabel(ClientAbility ability) {
            List<Integer> holdings = new ArrayList<>(List.of(0));
            for (int step = 0; step <= HOLDING_STEPS; step++) {
                int sampled = (int) Math.round(Math.pow(10, step / (double) STEPS_PER_DECADE));
                holdings.add(sampled);
                holdings.add(sampled - 1);
            }
            return holdings.stream()
                    .map(held -> Component.literal(ability.petalLabel(held, Integer.MAX_VALUE,
                            com.mercuriusxeno.goo.client.network.StandingUpkeep.NONE)))
                    .max(Comparator.comparingInt(PetalWords::vanillaWidth)).orElseThrow();
        }

        /** A line's width in the vanilla font: each glyph's advance, its pixel of spacing included. */
        private static int vanillaWidth(Component line) {
            return line.getString().chars().map(PetalWords::advance).sum();
        }

        /** One glyph's advance in the vanilla font's ASCII sheet; any glyph it lacks reads as wide as most. */
        private static int advance(int glyph) {
            return switch (glyph) {
                case 'i', '!', '.', ',', ':', ';', '|', '\'' -> NARROWEST_ADVANCE;
                case 'l', '`' -> NARROWEST_ADVANCE + 1;
                case 'I', 't', '[', ']', ' ' -> NARROWEST_ADVANCE + 2;
                case 'f', 'k', '<', '>', '(', ')', '{', '}', '*', '"' -> GLYPH_ADVANCE - 1;
                case '@', '~' -> GLYPH_ADVANCE + 1;
                default -> GLYPH_ADVANCE;
            };
        }
    }

    /** The fan reads each ability's first-throw cost and the type's holdings (decision radial-shows-first-throw-cost-and-holdings). */
    @Nested
    class CostAndHoldings {

        private static final int HOLDINGS = 1500;
        private static final int AFFORDABLE = 1000;
        private static final int UNAFFORDABLE = 2000;
        private static final int SIXTEEN_GOO = 16_000;

        private static ClientAbility costing(int firstThrow) {
            return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "cost_" + firstThrow),
                    "ability.goo.cost", "", 0, List.of(), List.of(), firstThrow, Delivery.ARC, AbilityBadge.WORLD, List.of());
        }

        @Test
        void centerReadsTheTypesHoldings() {
            assertEquals("1.5K", RadialWheelRenderer.holdingsLabel(HOLDINGS));
            assertEquals("16K", RadialWheelRenderer.holdingsLabel(SIXTEEN_GOO));
        }

        @Test
        void wedgeCostingMoreThanTheHoldingsReadsDimmed() {
            assertFalse(RadialWheelRenderer.fanSlot(costing(AFFORDABLE), HOLDINGS).dimmed());
            assertTrue(RadialWheelRenderer.fanSlot(costing(UNAFFORDABLE), HOLDINGS).dimmed());
        }

        @Test
        void wedgeCostingExactlyTheHoldingsReadsBright() {
            assertFalse(RadialWheelRenderer.fanSlot(costing(HOLDINGS), HOLDINGS).dimmed());
        }

        /** A held effect reads its upkeep a second, dimmed short of a second's worth (decision self-effects-trickle-until-ended). */
        @Test
        void heldWedgeReadsItsUpkeepPerSecond() {
            ClientAbility held = new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "held"), "ability.goo.held",
                    "", 0, List.of(), List.of(), 0, Delivery.of(DeliveryKind.SELF), AbilityBadge.DURATIVE, List.of(),
                    AbilityArea.NONE, IndicatorShowing.HELD, List.of(), 1);
            assertEquals("20/s | 1.25m", RadialWheelRenderer.fanSlot(held, HOLDINGS).costLabel());
            assertFalse(RadialWheelRenderer.fanSlot(held, 20).dimmed());
            assertTrue(RadialWheelRenderer.fanSlot(held, 19).dimmed());
        }
    }

    /**
     * A frame with a type open draws shrunken type petals with no text, each
     * ability petal with its name and cost, and the open type's name and
     * holdings in the hub (decision abilities-replace-the-hovered-type).
     */
    @Nested
    class OpenTypeFrame {

        private static final int TYPES = 16;
        private static final int OPEN_TYPE = 8;
        private static final int ABILITIES = 4;
        private static final int HOLDINGS = 1500;
        private static final int CENTER_X = 1200;
        private static final int CENTER_Y = 1100;
        private static final int RADIUS = 1000;
        /** A 1080p screen's GUI width at GUI scale 3, and at GUI scale 4. */
        private static final int SCALE_THREE_WIDTH = 640;
        private static final int SCALE_FOUR_WIDTH = 480;
        /** A 16:9 screen's height per width, as units of each. */
        private static final int GUI_HEIGHT_PER_WIDTH = 9;
        private static final int GUI_WIDTH_UNITS = 16;
        /** How far, in square pixels, a whole badge's covered area may stray from its square: float rounding. */
        private static final double AREA_TOLERANCE = 0.5;
        /** The items the locked ability requires: glass learned, sand and clay not. */
        private static final List<OfferedAbility.RequiredItem> REQUIRED = List.of(
                new OfferedAbility.RequiredItem(Identifier.withDefaultNamespace("glass"), true),
                new OfferedAbility.RequiredItem(Identifier.withDefaultNamespace("sand"), false),
                new OfferedAbility.RequiredItem(Identifier.withDefaultNamespace("clay"), false));
        /** How far, in pixels, an item's center may stray from the petal's center line or its stride. */
        private static final double COLUMN_TOLERANCE = 1.5;
        /** How far, in pixels, a computed point may stray from where it belongs: float rounding. */
        private static final double POINT_TOLERANCE = 1e-6;

        private static final PetalLook.SpriteBox SPRITE = new PetalLook.SpriteBox(0.25f, 0.5f, 0.125f, 0.25f);
        private static final int EDGE_COLOR = 0xFF123456;
        /** Every line's width in pixels, as the stubbed font measures it. */
        private static final int WORD_WIDTH = 40;
        /** A type icon's side, as the renderer draws it. */
        private static final int TYPE_ICON_SIZE = 12;
        /** How many poses deep the frame's matrix stack nests: the panel's push, then a scaled word's. */
        private static final int POSE_DEPTH = 4;

        private final TextureSetup atlas = TextureSetup.singleTexture(mock(GpuTextureView.class),
                mock(GpuSampler.class));
        /** Each whole-file sprite the frame bound, by its texture, so a submission names the sprite it draws. */
        private final Map<Identifier, TextureSetup> sprites = new java.util.HashMap<>();

        /** An atlas, a sprite box and colors with no client behind them, so the frame renders off the game. */
        private final PetalLook fakeLook = new PetalLook() {
            @Override
            public TextureSetup sprite(Identifier texture) {
                return sprites.computeIfAbsent(texture, unused -> TextureSetup.singleTexture(
                        mock(GpuTextureView.class), mock(GpuSampler.class)));
            }

            @Override
            public FluidFace fluidFace(ResourceKey<GooTypeDefinition> type) {
                return new FluidFace(atlas, SPRITE, 0xFFFFFFFF, EDGE_COLOR);
            }

            @Override
            public Identifier hubMask() {
                return Identifier.fromNamespaceAndPath("gootest", "hub");
            }

            @Override
            public ItemStack itemStack(Identifier item) {
                return mock(ItemStack.class);
            }

            @Override
            public CostMechanicsLookup costMechanicsLookup() {
                return new CostMechanicsLookup(key -> key, () -> GooValueTable.EMPTY);
            }
        };

        private final List<ResourceKey<GooTypeDefinition>> types = IntStream.range(0, TYPES)
                .mapToObj(type -> ResourceKey.create(GooTypes.REGISTRY,
                        Identifier.fromNamespaceAndPath(Goo.MODID, "type_" + type)))
                .toList();

        private RadialWheel openWheel() {
            return openedWheel(TYPES, ABILITIES, OPEN_TYPE, RADIUS);
        }

        private static ClientAbility abilityOf(int type, int ability) {
            return new ClientAbility(Identifier.fromNamespaceAndPath("gootest", "ability_" + type + "_" + ability),
                    "ability.gootest.word", "", 0, List.of(), List.of(), 0, Delivery.ARC, AbilityBadge.WORLD,
                    List.of());
        }

        /**
         * Renders the whole frame, every ability unlocked.
         *
         * @param wheel the wheel's state
         * @return the mocked graphics the frame drew to
         */
        private GuiGraphicsExtractor renderFrame(RadialWheel wheel) {
            List<List<OfferedAbility>> abilities = IntStream.range(0, TYPES)
                    .mapToObj(type -> IntStream.range(0, ABILITIES)
                            .mapToObj(ability -> new OfferedAbility(abilityOf(type, ability), List.of())).toList())
                    .toList();
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            when(graphics.pose()).thenReturn(new Matrix3x2fStack(POSE_DEPTH));
            Font font = mock(Font.class);
            when(font.width(any(FormattedText.class))).thenReturn(WORD_WIDTH);
            RadialWheelRenderer.render(graphics, font, frameOf(wheel, abilities, RADIUS));
            return graphics;
        }

        private RadialWheelRenderer.Frame frameOf(RadialWheel wheel, List<List<OfferedAbility>> abilities) {
            return frameOf(wheel, abilities, RADIUS);
        }

        private RadialWheelRenderer.Frame frameOf(RadialWheel wheel, List<List<OfferedAbility>> abilities,
                                                  int radius) {
            return new RadialWheelRenderer.Frame(wheel, types, abilities,
                    Map.of(types.get(OPEN_TYPE), HOLDINGS), CENTER_X, CENTER_Y, radius, fakeLook, 0.0f);
        }

        private static RadialWheel.PetalArc firstAbilityPetal(RadialWheel wheel) {
            return wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .findFirst().orElseThrow();
        }

        /** Whether a screen corner lies on an item's square, its far edges included, within float rounding. */
        private static boolean onSquare(RadialWheelRenderer.ItemRect rect, PetalRenderState.ScreenVertex corner) {
            double slack = 1e-3;
            int size = RadialWheelRenderer.ITEM_ICON_SIZE;
            return corner.x() >= rect.left() - slack && corner.x() <= rect.left() + size + slack
                    && corner.y() >= rect.top() - slack && corner.y() <= rect.top() + size + slack;
        }

        /**
         * An item square straddling a locked petal's border draws only its
         * part under the petal's face.
         * decision icons-slide-in-from-behind-the-tip
         */
        @Test
        void itemStraddlingTheBorderCutsAtIt() {
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());
            RadialWheel.PetalArc petal = firstAbilityPetal(wheel);
            PetalMask.Point edge = PetalMask.Point.polar(petal.center(), petal.shape().outer());
            int half = RadialWheelRenderer.ITEM_ICON_SIZE / 2;
            RadialWheelRenderer.ItemRect rect = new RadialWheelRenderer.ItemRect(
                    (int) Math.round(CENTER_X + edge.x() * RADIUS) - half,
                    (int) Math.round(CENTER_Y + edge.y() * RADIUS) - half);
            RadialWheelRenderer.ItemIcon icon = new RadialWheelRenderer.ItemIcon(Identifier.withDefaultNamespace(
                    "glass"), mock(ItemStack.class), rect, false, petal.shape());

            List<PetalRenderState.ScreenVertex> cut = PetalPainter.itemCut(frame, icon);

            assertFalse(cut.isEmpty());
            assertAll(cut.stream().map(corner -> (Executable) () -> {
                assertTrue(PetalMeshTest.insideWithSlack(petal.shape(), (corner.x() - CENTER_X) / (double) RADIUS,
                        (corner.y() - CENTER_Y) / (double) RADIUS), corner + " off the petal");
                assertTrue(onSquare(rect, corner), corner + " off the square");
            }));
            double kept = 0;
            for (int quad = 0; quad < cut.size(); quad += 4) {
                double twice = 0;
                for (int i = 0; i < 4; i++) {
                    PetalRenderState.ScreenVertex from = cut.get(quad + i);
                    PetalRenderState.ScreenVertex to = cut.get(quad + (i + 1) % 4);
                    twice += from.x() * to.y() - to.x() * from.y();
                }
                kept += Math.abs(twice) / 2;
            }
            int whole = RadialWheelRenderer.ITEM_ICON_SIZE * RadialWheelRenderer.ITEM_ICON_SIZE;
            assertTrue(kept > 0 && kept < whole, "kept " + kept + " of " + whole);
        }

        private static double[] centerOf(RadialWheelRenderer.ItemRect rect) {
            return new double[]{rect.left() + RadialWheelRenderer.ITEM_ICON_SIZE / 2.0,
                    rect.top() + RadialWheelRenderer.ITEM_ICON_SIZE / 2.0};
        }

        private <T> List<T> submittedArguments(GuiGraphicsExtractor graphics, String method, Class<T> type) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals(method))
                    .flatMap(call -> Arrays.stream(call.getArguments()))
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        @Test
        void hubReadsTheOpenTypesNameThenItsHoldings() {
            assertEquals(List.of(Component.translatable(GooTypeNames.translationKey(types.get(OPEN_TYPE))),
                    Component.literal(RadialWheelRenderer.holdingsLabel(HOLDINGS))),
                    RadialWheelRenderer.centerLabelLines(frameOf(openWheel(), List.of())));
        }

        /**
         * Each ability's icon draws at the corner
         * {@link RadialWheelRenderer#clusterAround} places it round its tip,
         * straight after its petal's edge strip, its badge following.
         * decision abilities-replace-the-hovered-type
         * decision badges-halve-and-sit-up-right-and-closer
         */
        @Test
        void abilityClusterCentersOnItsTipStraightAfterItsPetalsEdge() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel);
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());
            int half = RadialWheelRenderer.ABILITY_ICON_SIZE / 2;

            List<PetalRenderState> submitted = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class);
            TextureSetup badge = sprites.get(RadialWheelRenderer.badgeIcon(AbilityBadge.WORLD));

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        int[] tip = RadialWheelRenderer.tipCenter(frame, petal);
                        RadialWheelRenderer.IconCluster cluster = RadialWheelRenderer.clusterAround(tip,
                                AbilityBadge.WORLD, false);
                        PetalRenderState iconState = spriteOf(submitted, iconOf(petal));
                        int iconAt = submitted.indexOf(iconState);
                        float[] iconBox = boxOf(iconState);
                        assertEquals(List.of(cluster.iconCenter()[0] - half, cluster.iconCenter()[1] - half),
                                List.of(Math.round(iconBox[0]), Math.round(iconBox[1])), petal + " icon's corner");
                        assertEquals(0xFFFFFFFF, iconState.color(), petal + " tint");
                        PetalRenderState before = submitted.get(iconAt - 1);
                        assertTrue(!before.isTextured()
                                        && before.color() == RadialWheelRenderer.faded(EDGE_COLOR, petal.presence()),
                                petal + " icon draws straight after its petal's edge strip");
                        PetalRenderState badgeState = submitted.get(iconAt + 1);
                        assertTrue(badgeState.textureSetup() == badge, petal + " badge follows");
                        float[] badgeBox = boxOf(badgeState);
                        RadialWheelRenderer.ItemRect expected = cluster.badges().getLast().rect();
                        assertEquals(expected.left(), Math.round(badgeBox[0]), petal + " badge's left edge");
                        assertEquals(expected.top(), Math.round(badgeBox[1]), petal + " badge's top edge");
                    }));
        }

        /** The screen area quads in the wheel's normalized units cover, at a wheel radius. */
        private static double screenAreaOf(List<PetalMesh.Quad> quads, int radius) {
            double area = 0;
            for (PetalMesh.Quad quad : quads) {
                List<PetalMesh.Vertex> corners = quad.corners();
                double twice = 0;
                for (int corner = 0; corner < corners.size(); corner++) {
                    PetalMesh.Vertex from = corners.get(corner);
                    PetalMesh.Vertex to = corners.get((corner + 1) % corners.size());
                    twice += from.x() * to.y() - to.x() * from.y();
                }
                area += Math.abs(twice) / 2;
            }
            return area * radius * radius;
        }

        private static int radiusAt(int guiWidth) {
            return (int) RadialWheel.outerRadius(guiWidth, guiWidth * GUI_HEIGHT_PER_WIDTH / GUI_WIDTH_UNITS);
        }

        /**
         * The prism badge's square, where {@link RadialWheelRenderer#clusterAround}
         * places it round each petal's tip, lies whole under its petal's face at
         * the wheel radius a GUI of scale 3 and of scale 4 draws on a 1080p screen.
         * decision on-prism-reaction-wears-the-prism-badge
         * decision prism-badge-sits-behind-and-above-barely-overlapping
         */
        @ParameterizedTest
        @ValueSource(ints = {SCALE_THREE_WIDTH, SCALE_FOUR_WIDTH})
        void prismBadgeLiesWholeUnderItsPetalOnEveryPetal(int guiWidth) {
            int radius = radiusAt(guiWidth);
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of(), radius);
            int size = RadialWheelRenderer.BADGE_SIZE;
            double half = size / 2.0;

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        RadialWheelRenderer.ItemRect prism = RadialWheelRenderer.clusterAround(
                                RadialWheelRenderer.tipCenter(frame, petal), AbilityBadge.WORLD, true)
                                .badges().getFirst().rect();
                        List<PetalMesh.Quad> shown = PetalMesh.clipSquare(petal.shape(),
                                (prism.left() + half - CENTER_X) / radius, (prism.top() + half - CENTER_Y) / radius,
                                half / radius);
                        assertEquals(size * size, screenAreaOf(shown, radius), AREA_TOLERANCE,
                                petal + " prism badge shown whole");
                    }));
        }

        /**
         * A locked petal's fill takes the disabled tint an unaffordable petal
         * takes; an unlocked affordable one rests.
         * decision locked-petal-stays-on-the-wheel
         */
        @Test
        void aLockedPetalTakesTheDisabledTintAndAnUnlockedAffordableOneRests() {
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());
            RadialWheel.PetalArc petal = firstAbilityPetal(wheel);
            ClientAbility ability = abilityOf(OPEN_TYPE, petal.ability());
            RadialWheelRenderer.FanSlot affordable = new RadialWheelRenderer.FanSlot("", false);

            assertEquals(List.of(RadialWheelRenderer.computeOverlayTint(false, true),
                            RadialWheelRenderer.computeOverlayTint(false, false)),
                    List.of(RadialWheelRenderer.abilityPetalTint(frame, petal, new OfferedAbility(ability, REQUIRED),
                                    affordable),
                            RadialWheelRenderer.abilityPetalTint(frame, petal, new OfferedAbility(ability, List.of()),
                                    affordable)));
        }

        /**
         * A locked petal lists one item icon per required item, in the order
         * the ability names them, each carrying whether it is learned.
         * decision locked-petal-lists-the-unlearned-items
         */
        @Test
        void aLockedPetalListsOneIconPerRequiredItemCarryingItsLearnedFlag() {
            RadialWheel wheel = openWheel();
            RadialWheel.PetalArc petal = firstAbilityPetal(wheel);

            List<RadialWheelRenderer.ItemIcon> icons = RadialWheelRenderer.requiredIcons(frameOf(wheel, List.of()),
                    petal, new OfferedAbility(abilityOf(OPEN_TYPE, petal.ability()), REQUIRED));

            assertEquals(REQUIRED.stream().map(item -> item.item() + " learned " + item.learned()).toList(),
                    icons.stream().map(icon -> icon.item() + " learned " + icon.learned()).toList());
        }

        /**
         * Each learned item takes one red slash lying on its square.
         * decision locked-petal-lists-the-unlearned-items
         */
        @Test
        void eachLearnedItemTakesOneSlashOnItsSquare() {
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());
            RadialWheel.PetalArc petal = firstAbilityPetal(wheel);
            List<RadialWheelRenderer.ItemIcon> icons = RadialWheelRenderer.requiredIcons(frame, petal,
                    new OfferedAbility(abilityOf(OPEN_TYPE, petal.ability()), REQUIRED));
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            when(graphics.pose()).thenReturn(new Matrix3x2fStack(POSE_DEPTH));

            RadialWheelRenderer.slashLearnedItems(graphics, frame, icons);

            List<PetalRenderState> slashes = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class);
            // clustered squares' bounds may touch, so a slash belongs to the square holding all its corners
            assertEquals(icons.stream().filter(RadialWheelRenderer.ItemIcon::learned)
                            .map(RadialWheelRenderer.ItemIcon::rect).toList(),
                    slashes.stream().map(slash -> icons.stream().map(RadialWheelRenderer.ItemIcon::rect)
                            .filter(rect -> slash.vertices().stream().allMatch(cell -> onSquare(rect, cell)))
                            .findFirst().orElseThrow()).toList());
            assertTrue(slashes.stream().allMatch(slash -> slash.color() == RadialWheelRenderer.SLASH_COLOR),
                    "a slash drew off the slash color");
        }

        /** The mean of a cluster's item centers. */
        private static double[] clusterCenter(List<double[]> centers) {
            return new double[]{centers.stream().mapToDouble(c -> c[0]).average().orElseThrow(),
                    centers.stream().mapToDouble(c -> c[1]).average().orElseThrow()};
        }

        /**
         * The items cluster around one point on the petal's center line toward
         * its end: one on the point, two side by side, three in a triangle
         * pointing up, four in a square, every neighbor one stride apart.
         * decision locked-petal-lists-the-unlearned-items
         */
        @Test
        void itemsClusterTowardThePetalsEndInARegularPolygon() {
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());
            RadialWheel.PetalArc petal = firstAbilityPetal(wheel);
            double sinAngle = Math.sin(petal.center());
            double cosAngle = Math.cos(petal.center());
            int stride = RadialWheelRenderer.ITEM_STRIDE;

            assertAll(IntStream.rangeClosed(1, 5).mapToObj(count -> (Executable) () -> {
                List<double[]> centers = RadialWheelRenderer.itemCluster(frame, petal, count).stream()
                        .map(OpenTypeFrame::centerOf).toList();
                double[] middle = clusterCenter(centers);
                double along = (middle[0] - CENTER_X) * sinAngle - (middle[1] - CENTER_Y) * cosAngle;
                double across = (middle[0] - CENTER_X) * cosAngle + (middle[1] - CENTER_Y) * sinAngle;
                assertEquals(count, centers.size());
                assertEquals(0.0, across, COLUMN_TOLERANCE, count + " off the center line");
                assertEquals(petal.shape().outer() * RADIUS - RadialWheelRenderer.clusterRadius(count)
                        - RadialWheelRenderer.ITEM_ICON_SIZE, along, COLUMN_TOLERANCE, count + " toward the end");
                for (int index = 0; count > 1 && index < count; index++) {
                    double[] here = centers.get(index);
                    double[] next = centers.get((index + 1) % count);
                    assertEquals(stride, Math.hypot(next[0] - here[0], next[1] - here[1]),
                            COLUMN_TOLERANCE, count + " neighbors a stride apart");
                }
            }));
            List<double[]> pair = RadialWheelRenderer.itemCluster(frame, petal, 2).stream()
                    .map(OpenTypeFrame::centerOf).toList();
            assertEquals(pair.get(0)[1], pair.get(1)[1], COLUMN_TOLERANCE, "two side by side");
            List<double[]> triangle = RadialWheelRenderer.itemCluster(frame, petal, 3).stream()
                    .map(OpenTypeFrame::centerOf).toList();
            assertTrue(triangle.get(0)[1] < triangle.get(1)[1] && triangle.get(1)[1] == triangle.get(2)[1],
                    "three in a triangle pointing up");
            List<double[]> square = RadialWheelRenderer.itemCluster(frame, petal, 4).stream()
                    .map(OpenTypeFrame::centerOf).toList();
            assertEquals(square.get(0)[1], square.get(3)[1], COLUMN_TOLERANCE, "a square, its top row level");
            assertEquals(square.get(1)[1], square.get(2)[1], COLUMN_TOLERANCE, "a square, its bottom row level");
        }

        private static Identifier iconOf(RadialWheel.PetalArc petal) {
            return Identifier.fromNamespaceAndPath(Goo.MODID,
                    "textures/goo/ability/ability_" + petal.type() + "_" + petal.ability() + ".png");
        }

        /** The one submission drawing a sprite's texture. */
        private PetalRenderState spriteOf(List<PetalRenderState> submitted, Identifier texture) {
            TextureSetup bound = sprites.get(texture);
            List<PetalRenderState> drawing = submitted.stream().filter(state -> state.textureSetup() == bound)
                    .toList();
            assertEquals(1, drawing.size(), texture + " submissions");
            return drawing.getFirst();
        }

        /** A submission's least x and y on screen, the top-left of an uncut sprite. */
        private static float[] boxOf(PetalRenderState state) {
            return new float[]{
                    state.vertices().stream().map(PetalRenderState.ScreenVertex::x).min(Float::compare).orElseThrow(),
                    state.vertices().stream().map(PetalRenderState.ScreenVertex::y).min(Float::compare).orElseThrow()};
        }

        /**
         * An unlocked ability petal's icon reaches the GUI as quads sampling
         * within the icon's own texture; each type petal's icon blits whole.
         * decision icons-slide-in-from-behind-the-tip
         */
        @Test
        void abilityIconSamplesWithinItsOwnTextureAndEachTypeIconBlitsWhole() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel);
            List<PetalRenderState> submitted = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class);

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> assertTrue(spriteOf(submitted, iconOf(petal)).vertices().stream()
                            .allMatch(corner -> corner.u() >= 0 && corner.u() <= 1 && corner.v() >= 0
                                    && corner.v() <= 1), petal + " samples off its icon")));
            assertEquals(typePetalsOf(wheel).size(),
                    mockingDetails(graphics).getInvocations().stream()
                            .filter(call -> call.getMethod().getName().equals("blit")
                                    && call.getArgument(1).toString().contains("/type/")).count());
        }

        /** Every type petal the frame draws. */
        private static List<RadialWheel.PetalArc> typePetalsOf(RadialWheel wheel) {
            return wheel.displayedLayout(0.0f).stream().filter(petal -> !petal.isAbility()).toList();
        }

        /**
         * The neutral zone's mask reaches the whole radius at rest and the
         * hub's share of it, the hub over the rest size, with a type open.
         * decision halves-arc-petals-at-rest
         */
        @Test
        void theHubReachesTheWholeRadiusAtRestAndItsShareWithATypeOpen() {
            assertEquals(List.of(RADIUS,
                            (int) Math.round(RADIUS * RadialWheel.HUB_FRACTION / RadialWheel.HUB_REST_FRACTION)),
                    List.of(RadialWheelRenderer.hubReach(frameOf(new RadialWheel(TYPES, type -> ABILITIES),
                                    List.of())),
                            RadialWheelRenderer.hubReach(frameOf(openWheel(), List.of()))));
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void typeIconCentersAlongItsPetalAtRestAndOnItsTipInTheInnerRing() {
            RadialWheel rest = new RadialWheel(TYPES, type -> ABILITIES);
            RadialWheel open = openWheel();

            assertAll(typePetalsOf(rest).stream().map(petal -> (Executable) () -> assertNear(
                    RadialWheelRenderer.lengthCenter(frameOf(rest, List.of()), petal),
                    PetalMask.Point.polar(petal.center(), (petal.inner() + petal.length()) / 2), petal + " at rest")));
            assertAll(typePetalsOf(open).stream().map(petal -> (Executable) () -> assertNear(
                    RadialWheelRenderer.lengthCenter(frameOf(open, List.of()), petal), petal.shape().tipCenter(),
                    petal + " open")));
        }

        /** Asserts a screen point sits within float rounding of where a point of the wheel lands. */
        private static void assertNear(double[] point, PetalMask.Point expected, String what) {
            assertAll(what,
                    () -> assertEquals(CENTER_X + expected.x() * RADIUS, point[0], POINT_TOLERANCE, "x"),
                    () -> assertEquals(CENTER_Y + expected.y() * RADIUS, point[1], POINT_TOLERANCE, "y"));
        }

        /**
         * An icon's square rounds once from its unrounded center, so each open
         * type icon's middle sits within half a pixel of its tip either way.
         */
        @Test
        void openTypeIconsCornerRoundsOnceSoItsMiddleSitsWithinHalfAPixel() {
            RadialWheel open = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(open, List.of());
            assertAll(typePetalsOf(open).stream().map(petal -> (Executable) () -> {
                double[] center = RadialWheelRenderer.lengthCenter(frame, petal);
                int[] corner = RadialWheelRenderer.iconCorner(center, TYPE_ICON_SIZE);
                double dx = corner[0] + TYPE_ICON_SIZE / 2.0 - center[0];
                double dy = corner[1] + TYPE_ICON_SIZE / 2.0 - center[1];
                assertTrue(Math.abs(dx) <= 0.5 && Math.abs(dy) <= 0.5,
                        petal.type() + " icon center is off its tip by " + dx + ", " + dy);
            }));
        }

        /**
         * The wheel opened on the open type with the cursor resting on that type's
         * petal in the inner band, so no ability is hovered.
         */
        private static RadialWheel restingOnOpenType(int radius) {
            RadialWheel wheel = openedWheel(TYPES, ABILITIES, OPEN_TYPE, radius);
            double angle = wheel.layout().stream().filter(arc -> !arc.isAbility() && arc.type() == OPEN_TYPE)
                    .findFirst().orElseThrow().center();
            double reach = (RadialWheel.HUB_FRACTION + RadialWheel.TYPE_BAND_FRACTION) / 2 * radius;
            wheel.moveCursor(Math.sin(angle) * reach, -Math.cos(angle) * reach, radius);
            assertEquals(OPEN_TYPE, wheel.selectedType(), "the open type stays open");
            return wheel;
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void abilityWordsDrawAfterEveryPetalAndIcon() {
            List<String> calls = mockingDetails(renderFrame(restingOnOpenType(RADIUS))).getInvocations().stream()
                    .map(call -> call.getMethod().getName()).toList();
            int lastShape = Math.max(calls.lastIndexOf("submitGuiElementRenderState"), calls.lastIndexOf("blit"));

            assertTrue(calls.indexOf("text") > lastShape, "a word drew before shape " + lastShape);
        }
    }

    /**
     * An ability's words placed round its resting tip, and each line drawn
     * inside its dark border.
     * decision abilities-replace-the-hovered-type
     */
    @Nested
    class WordPlacement {

        private static final int[] TIP = {200, 120};
        private static final int LINE_HEIGHT = 9;
        private static final int LINE_WIDTH = 40;
        private static final int TEXT_COLOR = 0xFFABCDEF;
        private final List<Component> name = List.of(Component.literal("Unstable"), Component.literal("Lurker"));
        private final Component cost = Component.literal("1K | x1");

        private List<RadialWheelRenderer.PlacedLine> placed() {
            return RadialWheelRenderer.placeWords(name, cost, TIP, line -> LINE_WIDTH, LINE_HEIGHT);
        }

        @Test
        void anAbilitysWordsAreItsNamesLinesThenItsCost() {
            assertEquals(List.of(name.getFirst(), name.getLast(), cost),
                    placed().stream().map(RadialWheelRenderer.PlacedLine::text).toList());
        }

        /** decision badges-halve-and-sit-up-right-and-closer */
        @Test
        void theNameStandsAboveTheIconAndTheCostBelowItBothCenteredOnTheTip() {
            List<RadialWheelRenderer.PlacedLine> lines = placed();
            RadialWheelRenderer.PlacedLine lastNameLine = lines.get(name.size() - 1);
            RadialWheelRenderer.ItemRect icon = RadialWheelRenderer.clusterAround(TIP, AbilityBadge.WORLD, false)
                    .icon();

            assertAll(
                    () -> assertEquals(List.of(TIP[0], TIP[0], TIP[0]),
                            lines.stream().map(RadialWheelRenderer.PlacedLine::centerX).toList(), "centered"),
                    () -> assertTrue(lastNameLine.top() + LINE_HEIGHT * lastNameLine.scale() <= icon.top(),
                            "name bottom " + (lastNameLine.top() + LINE_HEIGHT * lastNameLine.scale())
                                    + ", icon top " + icon.top()),
                    () -> assertTrue(lines.getLast().top() >= icon.top() + icon.size(),
                            "cost top " + lines.getLast().top() + ", icon bottom " + (icon.top() + icon.size())));
        }

        @Test
        void aLineDrawsABorderCopyAPixelOffEachWayInTheOutlineColorThenItselfInItsColor() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Font font = mock(Font.class);
            when(font.width(any(FormattedText.class))).thenReturn(LINE_WIDTH);
            Component line = name.getFirst();
            int left = TIP[0] - LINE_WIDTH / 2;

            RadialWheelRenderer.outlinedText(graphics, font, line, TIP[0], TIP[1], TEXT_COLOR);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        verify(graphics).text(font, line, left + dx, TIP[1] + dy, RadialWheelRenderer.OUTLINE_COLOR,
                                false);
                    }
                }
            }
            assertEquals(List.of(font, line, left, TIP[1], TEXT_COLOR, false),
                    Arrays.asList(mockingDetails(graphics).getInvocations().stream().reduce((first, second) -> second)
                            .orElseThrow().getArguments()));
        }
    }

    /**
     * An ability petal's content slides in along the petal's center line from
     * past its outer radius to the tip's center as the type opens (decision
     * icons-slide-in-from-behind-the-tip).
     */
    @Nested
    class ContentSlide {

        private static final int TYPES = 16;
        private static final int OPEN_TYPE = 8;
        private static final int ABILITIES = 3;
        private static final int CENTER_X = 400;
        private static final int CENTER_Y = 300;
        private static final int RADIUS = 200;
        private static final int REQUIRED_ITEMS = 3;
        /** How far, in pixels, a point may stray from the center line or an expected spot: integer pixel rounding. */
        private static final double ROUNDING = 1.0;

        private final RadialWheel wheel = openWheel();
        private final RadialWheelRenderer.Frame frame = new RadialWheelRenderer.Frame(wheel, List.of(), List.of(),
                Map.of(), CENTER_X, CENTER_Y, RADIUS, null, 0.0f);
        private final RadialWheel.PetalArc fanned = wheel.displayedLayout(0.0f).stream()
                .filter(RadialWheel.PetalArc::isAbility).findFirst().orElseThrow();

        private static RadialWheel openWheel() {
            return openedWheel(TYPES, ABILITIES, OPEN_TYPE, RADIUS);
        }

        private RadialWheel.PetalArc at(double openness) {
            return new RadialWheel.PetalArc(fanned.type(), fanned.ability(), fanned.start(), fanned.arc(),
                    fanned.length(), fanned.root(), openness, fanned.presence());
        }

        /** How far a screen point lies along the petal's center line from the wheel's center. */
        private double along(double x, double y) {
            return (x - CENTER_X) * Math.sin(fanned.center()) - (y - CENTER_Y) * Math.cos(fanned.center());
        }

        /** How far a screen point lies off the petal's center line. */
        private double across(double x, double y) {
            return (x - CENTER_X) * Math.cos(fanned.center()) + (y - CENTER_Y) * Math.sin(fanned.center());
        }

        private boolean insidePetal(double x, double y) {
            return fanned.shape().contains((x - CENTER_X) / RADIUS, (y - CENTER_Y) / RADIUS);
        }

        @Test
        void fullyOpenRestsOnTheTipCenter() {
            assertEquals(1.0, fanned.openness());
            assertEquals(List.of(RadialWheelRenderer.tipCenter(frame, fanned)[0],
                            RadialWheelRenderer.tipCenter(frame, fanned)[1]),
                    Arrays.stream(RadialWheelRenderer.contentCenter(frame, at(1.0))).boxed().toList());
        }

        @Test
        void closedLiesOnTheCenterLineWithTheIconAndBadgeOutsideThePetal() {
            int[] center = RadialWheelRenderer.contentCenter(frame, at(0.0));
            RadialWheelRenderer.IconCluster cluster = RadialWheelRenderer.clusterAround(center, AbilityBadge.WORLD, true);
            List<RadialWheelRenderer.ItemRect> squares = new ArrayList<>(List.of(cluster.icon()));
            cluster.badges().forEach(badge -> squares.add(badge.rect()));

            assertEquals(0.0, across(center[0], center[1]), ROUNDING);
            assertTrue(along(center[0], center[1]) > fanned.shape().outer() * RADIUS, "short of the outer radius");
            assertAll(squares.stream().flatMap(square -> IntStream.range(0, 4).mapToObj(corner -> (Executable) () -> {
                int x = square.left() + (corner % 2 == 0 ? 0 : square.size());
                int y = square.top() + (corner / 2 == 0 ? 0 : square.size());
                assertFalse(insidePetal(x, y), square + " corner " + corner);
            })));
        }

        @Test
        void halfOpenLiesBetweenOnTheCenterLine() {
            int[] closed = RadialWheelRenderer.contentCenter(frame, at(0.0));
            int[] half = RadialWheelRenderer.contentCenter(frame, at(0.5));
            int[] open = RadialWheelRenderer.contentCenter(frame, at(1.0));

            assertEquals(0.0, across(half[0], half[1]), ROUNDING);
            assertTrue(along(half[0], half[1]) < along(closed[0], closed[1]), "past the closed point");
            assertTrue(along(half[0], half[1]) > along(open[0], open[1]), "short of the resting point");
        }

        @Test
        void itemClusterRestsInPlaceFullyOpenAndRidesOutByTheIconsOffsetClosed() {
            List<RadialWheelRenderer.ItemRect> resting = RadialWheelRenderer.itemCluster(frame, at(1.0),
                    REQUIRED_ITEMS);
            List<RadialWheelRenderer.ItemRect> closed = RadialWheelRenderer.itemCluster(frame, at(0.0),
                    REQUIRED_ITEMS);
            int[] iconClosed = RadialWheelRenderer.contentCenter(frame, at(0.0));
            int[] iconOpen = RadialWheelRenderer.contentCenter(frame, at(1.0));
            double iconOffset = along(iconClosed[0], iconClosed[1]) - along(iconOpen[0], iconOpen[1]);

            assertAll(IntStream.range(0, REQUIRED_ITEMS).mapToObj(index -> (Executable) () -> {
                RadialWheelRenderer.ItemRect from = resting.get(index);
                RadialWheelRenderer.ItemRect to = closed.get(index);
                assertEquals(iconOffset, along(to.left(), to.top()) - along(from.left(), from.top()), 2 * ROUNDING,
                        "item " + index + " offset");
                assertEquals(0.0, across(to.left(), to.top()) - across(from.left(), from.top()), 2 * ROUNDING,
                        "item " + index + " off the center line");
            }));
        }
    }

    /** The cursor names the item icon it rests on (decision locked-petal-lists-the-unlearned-items). */
    @Nested
    class ItemHover {

        private static final Identifier GLASS = Identifier.withDefaultNamespace("glass");
        private static final Identifier SAND = Identifier.withDefaultNamespace("sand");
        private static final RadialWheelRenderer.ItemRect GLASS_RECT = new RadialWheelRenderer.ItemRect(200, 100);
        private static final RadialWheelRenderer.ItemRect SAND_RECT = new RadialWheelRenderer.ItemRect(209, 117);

        private static final PetalMask.Petal FACE = new PetalMask.Petal(0.0, Math.PI / 4, RadialWheel.HUB_FRACTION, 1.0);

        private final List<RadialWheelRenderer.ItemIcon> icons = List.of(
                new RadialWheelRenderer.ItemIcon(GLASS, mock(ItemStack.class), GLASS_RECT, true, FACE),
                new RadialWheelRenderer.ItemIcon(SAND, mock(ItemStack.class), SAND_RECT, false, FACE));

        @Test
        void cursorInsideAnIconAnswersThatItem() {
            assertEquals(SAND, RadialWheelRenderer.itemUnder(icons, SAND_RECT.left() + 1, SAND_RECT.top() + 1).item());
            assertEquals(GLASS, RadialWheelRenderer.itemUnder(icons, GLASS_RECT.left(), GLASS_RECT.top()).item());
            double far = RadialWheelRenderer.ITEM_ICON_SIZE - 0.5;
            assertEquals(GLASS, RadialWheelRenderer.itemUnder(icons, GLASS_RECT.left() + far, GLASS_RECT.top() + far)
                    .item(), "the far corner");
        }

        @Test
        void cursorOutsideEveryIconAnswersNone() {
            int size = RadialWheelRenderer.ITEM_ICON_SIZE;
            assertAll(
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, GLASS_RECT.left() - 1, GLASS_RECT.top())),
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, GLASS_RECT.left() + size,
                            GLASS_RECT.top()), "right of the first icon, above the second"),
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, SAND_RECT.left(), SAND_RECT.top() + size)),
                    () -> assertNull(RadialWheelRenderer.itemUnder(icons, SAND_RECT.left() + size, SAND_RECT.top())));
        }
    }

    @Nested
    class ExplicitIcon {

        @Test
        void namespacedIconKeepsItsOwnNamespace() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon(Goo.MODID + ":" + TEXTURE_PATH)));
        }

        @Test
        void bareIconTakesTheGooNamespace() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon(TEXTURE_PATH)));
        }

        @Test
        void emptyIconFallsBackToTheConventionPath() {
            assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, TEXTURE_PATH),
                    RadialWheelRenderer.resolveAbilityIcon(abilityWithIcon("")));
        }
    }

    /** A type petal holding a held ability pulses while the type is not expanded (decision wheel-pulses-the-active-effect). */
    @Nested
    class TypePetalPulse {

        private static final Identifier BLAZE_KINDLE = Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_kindle");
        private static final int BLAZE = 0;
        private static final float QUARTER_PERIOD = PetalPulse.PERIOD_TICKS / 4f;

        private RadialWheelRenderer.Frame frame(Map<Identifier, Float> active) {
            return frame(active, 1000);
        }

        private RadialWheelRenderer.Frame frame(Map<Identifier, Float> active, int blazeHeld) {
            List<ResourceKey<GooTypeDefinition>> types = List.of(GooTypes.BLAZE, GooTypes.LEAF);
            List<List<OfferedAbility>> abilities = List.of(
                    List.of(new OfferedAbility(new ClientAbility(BLAZE_KINDLE, "ability.goo.kindle", "", 0, List.of(),
                            List.of(), 0, Delivery.of(DeliveryKind.SELF), AbilityBadge.DURATIVE, List.of()), List.of())),
                    List.of());
            return new RadialWheelRenderer.Frame(new RadialWheel(types.size(), type -> 1), types, abilities,
                    Map.of(GooTypes.BLAZE, blazeHeld, GooTypes.LEAF, 1000), 0, 0, 100, null, 0f, active,
                    QUARTER_PERIOD);
        }

        @Test
        void anUnexpandedTypeHoldingAnActiveAbilityPulses() {
            int pulsing = RadialWheelRenderer.typePetalTint(frame(Map.of(BLAZE_KINDLE, 1f)), BLAZE);
            assertEquals(RadialWheelRenderer.HOVER_ALPHA, ARGB.alpha(pulsing));
            assertTrue(ARGB.red(pulsing) > ARGB.red(RadialWheelRenderer.computeOverlayTint(false, false)));
        }

        /** decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends */
        @Test
        void aTypeWhoseAbilityPulsesAtHalfStrengthTintsAtHalfTheSwing() {
            int halfSwing = Math.round((RadialWheelRenderer.HOVER_ALPHA - RadialWheelRenderer.NORMAL_ALPHA) / 2f);
            assertEquals(RadialWheelRenderer.NORMAL_ALPHA + halfSwing,
                    ARGB.alpha(RadialWheelRenderer.typePetalTint(frame(Map.of(BLAZE_KINDLE, 0.5f)), BLAZE)));
        }

        @Test
        void aTypeHoldingNoActiveEffectRests() {
            assertEquals(RadialWheelRenderer.computeOverlayTint(false, false),
                    RadialWheelRenderer.typePetalTint(frame(Map.of()), BLAZE));
        }

        /** decision wheel-shows-only-held-types-with-abilities: no shown type is unheld, so none dims. */
        @Test
        void aTypeHeldAtZeroRestsUndimmed() {
            assertEquals(RadialWheelRenderer.computeOverlayTint(false, false),
                    RadialWheelRenderer.typePetalTint(frame(Map.of(), 0), BLAZE));
        }
    }

    /** A petal's fill and icon fade by its presence. */
    @Nested
    class NeighborFade {

        private static final double FAR_PRESENCE = 0.4;
        private static final int FULL_ALPHA = 0xFF;

        private final RadialWheel.PetalArc farNeighbor = new RadialWheel.PetalArc(0, RadialWheel.NONE, 0.0, 0.1,
                1.0, null, 0.0, FAR_PRESENCE);

        private RadialWheelRenderer.Frame frame() {
            List<ResourceKey<GooTypeDefinition>> types = List.of(GooTypes.BLAZE);
            return new RadialWheelRenderer.Frame(new RadialWheel(types.size(), type -> 0), types, List.of(List.of()),
                    Map.of(GooTypes.BLAZE, 1000), 0, 0, 100, null, 0f);
        }

        @Test
        void aFortyPercentNeighborsFillDrawsAtFortyPercentOfTheRestingAlpha() {
            int tint = RadialWheelRenderer.typePetalTint(frame(), farNeighbor);

            assertEquals(Math.round(RadialWheelRenderer.NORMAL_ALPHA * FAR_PRESENCE), ARGB.alpha(tint));
        }

        @Test
        void aFortyPercentNeighborsIconDrawsAtFortyPercentOfFullAlpha() {
            assertEquals(Math.round(FULL_ALPHA * FAR_PRESENCE),
                    ARGB.alpha(RadialWheelRenderer.typeIconColor(farNeighbor)));
        }
    }

    /** The tint a textured wedge blits under still tells hovered and disabled from resting (decision wedges-render-fluid-texture). */
    @Nested
    class OverlayTint {

        private final int resting = RadialWheelRenderer.computeOverlayTint(false, false);

        @Test
        void hoveredIsBrighterAndMoreOpaqueThanResting() {
            int hovered = RadialWheelRenderer.computeOverlayTint(true, false);
            assertAll(
                    () -> assertTrue(ARGB.alpha(hovered) > ARGB.alpha(resting)),
                    () -> assertTrue(ARGB.red(hovered) > ARGB.red(resting)),
                    () -> assertTrue(ARGB.green(hovered) > ARGB.green(resting)),
                    () -> assertTrue(ARGB.blue(hovered) > ARGB.blue(resting)));
        }

        @Test
        void disabledIsDarkerAndDimmerThanResting() {
            int disabled = RadialWheelRenderer.computeOverlayTint(false, true);
            assertAll(
                    () -> assertTrue(ARGB.alpha(disabled) < ARGB.alpha(resting)),
                    () -> assertTrue(ARGB.red(disabled) < ARGB.red(resting)),
                    () -> assertTrue(ARGB.green(disabled) < ARGB.green(resting)),
                    () -> assertTrue(ARGB.blue(disabled) < ARGB.blue(resting)));
        }

        @Test
        void disabledOutweighsHovered() {
            assertEquals(RadialWheelRenderer.computeOverlayTint(false, true),
                    RadialWheelRenderer.computeOverlayTint(true, true));
        }
    }
}
