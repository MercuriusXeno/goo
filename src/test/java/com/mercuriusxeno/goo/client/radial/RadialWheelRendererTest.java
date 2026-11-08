package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.data.GooFraction;
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
import com.mercuriusxeno.goo.client.hud.InWorldHud;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import com.mercuriusxeno.goo.client.fade.Afterglows;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        void everyShippedAbilityResolvesWithoutThrowing() {
            assertAll(shippedAbilities().stream().map(ability ->
                    (Executable) () -> assertDoesNotThrow(() -> RadialWheelRenderer.resolveAbilityIcon(ability),
                            ability.id().toString())));
        }

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

        /** quantum-anchors-link-two-points: the ability once called Anchor reads Portal; its key stays. */
        @Test
        void quantumAnchorReadsPortal() throws IOException {
            assertEquals("Portal", englishLang().get("goo.ability.quantum.anchor").getAsString());
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
     * A badge draws at half the icon's side, up and right of the icon, covering its top-right corner by a texel or
     * two along each side, so it reads attached rather than beside.
     * decision badges-halve-and-sit-up-right-and-closer
     */
    @Nested
    class BadgeBox {

        private static final int[] ICON_CENTER = {200, 120};
        private static final int HALF_BADGE_SIDE = 8;

        private final RadialWheelRenderer.ItemRect icon = new RadialWheelRenderer.ItemRect(
                ICON_CENTER[0] - RadialWheelRenderer.ABILITY_ICON_SIZE / 2,
                ICON_CENTER[1] - RadialWheelRenderer.ABILITY_ICON_SIZE / 2, RadialWheelRenderer.ABILITY_ICON_SIZE);
        private final RadialWheelRenderer.ItemRect badge = RadialWheelRenderer.badgeBox(ICON_CENTER);

        @Test
        void badgeIsEightPixelsSquare() {
            assertEquals(HALF_BADGE_SIDE, badge.size());
        }

        @Test
        void badgeCentersAboveAndRightOfTheIcon() {
            double badgeX = badge.left() + badge.size() / 2.0;
            double badgeY = badge.top() + badge.size() / 2.0;
            assertAll(
                    () -> assertTrue(badgeX > ICON_CENTER[0], "badge center x " + badgeX),
                    () -> assertTrue(badgeY < ICON_CENTER[1], "badge center y " + badgeY));
        }

        @Test
        void badgeCoversTheIconsTopRightCornerByOneOrTwoTexels() {
            int across = Math.min(icon.left() + icon.size(), badge.left() + badge.size())
                    - Math.max(icon.left(), badge.left());
            int down = Math.min(icon.top() + icon.size(), badge.top() + badge.size()) - Math.max(icon.top(), badge.top());
            assertAll(
                    () -> assertTrue(across >= 1 && across <= 2, "overlap across " + across),
                    () -> assertTrue(down >= 1 && down <= 2, "overlap down " + down),
                    () -> assertEquals(icon.left() + icon.size(), badge.left() + across, "on the icon's right edge"),
                    () -> assertEquals(icon.top(), badge.top() + badge.size() - down, "on the icon's top edge"));
        }
    }

    /**
     * Where an ability wears two badges, the prism badge draws first, so behind, centered above its partner, the two
     * overlapping by a texel or two, and the cursor names each from its center.
     * decision prism-badge-sits-behind-and-above-barely-overlapping
     * decision hovering-a-badge-names-its-kind
     */
    @Nested
    class PrismBadgeLayout {

        private static final int[] ICON_CENTER = {200, 120};
        private static final int MOST_OVERLAP = 2;

        private final List<RadialWheelRenderer.BadgeIcon> layout = RadialWheelRenderer.badgeLayout(ICON_CENTER,
                AbilityBadge.WORLD, true);

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
        void prismBadgeCentersAboveItsPartnerOverlappingItByATexelOrTwo() {
            RadialWheelRenderer.ItemRect prism = prism().rect();
            RadialWheelRenderer.ItemRect partner = partner().rect();
            int across = Math.min(prism.left() + prism.size(), partner.left() + partner.size())
                    - Math.max(prism.left(), partner.left());
            int down = Math.min(prism.top() + prism.size(), partner.top() + partner.size())
                    - Math.max(prism.top(), partner.top());
            assertAll(
                    () -> assertTrue(centerOf(prism)[1] < centerOf(partner)[1],
                            "prism center y " + centerOf(prism)[1] + ", partner's " + centerOf(partner)[1]),
                    () -> assertTrue(across > 0 && down >= 1 && down <= MOST_OVERLAP,
                            "overlap " + across + " across, " + down + " down"));
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
     * An ability's name draws a little under full size and its cost smaller still, so across the widest fan the mod
     * ships, opened at any angle on the wheel a 1080p screen draws at GUI scale 3 and at 4, no two neighboring
     * petals' words meet: every shipped fan in the order the wheel fans it, each petal reading its English name and
     * the widest shipped cost label, each line measured by the vanilla font's glyph advances.
     * decision ability-name-shrinks-some-and-other-text-more
     */
    @Nested
    class PetalWords {

        private static final int TYPES = 16;
        private static final int CENTER = 500;
        /** The vanilla font's advance for most glyphs: five pixels of ink and one of spacing. */
        private static final int GLYPH_ADVANCE = 6;
        /** The vanilla font's narrowest advance, a one-pixel glyph such as i. */
        private static final int NARROWEST_ADVANCE = 2;
        private static final int LINE_HEIGHT = 9;
        /** The widest cost label a shipped petal reads: blink's formula. */
        private static final String WIDEST_COST = "200 + 100/block";
        private static final String ABILITY_KEY_PREFIX = "goo.ability.";
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
        void neighboringPetalsWordsNeverMeetInAnyShippedFan(int guiWidth) throws IOException {
            int radius = (int) RadialWheel.outerRadius(guiWidth, guiWidth * 9 / 16);
            JsonObject lang = ShortNames.englishLang();
            Component cost = Component.literal(WIDEST_COST);
            List<Executable> checks = new ArrayList<>();
            for (Map.Entry<String, List<ClientAbility>> fan : shippedFans().entrySet()) {
                List<List<Component>> names = fan.getValue().stream().map(ability -> RadialWheelRenderer
                        .splitNameLines(lang.get(ability.displayName()).getAsString())).toList();
                for (int open = 0; open < TYPES; open++) {
                    RadialWheel wheel = openedWheel(TYPES, names.size(), open, radius);
                    RadialWheelRenderer.Frame frame = new RadialWheelRenderer.Frame(wheel, List.of(), List.of(),
                            Map.of(), CENTER, CENTER, radius, mock(PetalLook.class), 0.0f);
                    int opened = open;
                    List<List<RadialWheelRenderer.PlacedLine>> words = wheel.displayedLayout(0.0f).stream()
                            .filter(petal -> petal.isAbility() && petal.type() == opened)
                            .sorted(Comparator.comparingInt(RadialWheel.PetalArc::ability))
                            .map(petal -> RadialWheelRenderer.placeWords(names.get(petal.ability()), cost,
                                    RadialWheelRenderer.tipCenter(frame, petal),
                                    PetalWords::vanillaWidth, LINE_HEIGHT))
                            .toList();
                    for (int ability = 0; ability + 1 < words.size(); ability++) {
                        List<RadialWheelRenderer.PlacedLine> here = words.get(ability);
                        List<RadialWheelRenderer.PlacedLine> next = words.get(ability + 1);
                        String where = fan.getKey() + " opened at slot " + open + ": "
                                + fan.getValue().get(ability).displayName() + " and "
                                + fan.getValue().get(ability + 1).displayName();
                        checks.add(() -> assertFalse(anyMeet(here, next), where));
                    }
                }
            }
            assertFalse(checks.isEmpty(), "no neighboring petals checked");
            assertAll(checks);
        }

        private static boolean anyMeet(List<RadialWheelRenderer.PlacedLine> one,
                                       List<RadialWheelRenderer.PlacedLine> other) {
            return one.stream().anyMatch(line -> other.stream().anyMatch(across -> meet(line.box(), across.box())));
        }

        private static boolean meet(double[] one, double[] other) {
            return one[0] < other[2] && other[0] < one[2] && one[1] < other[3] && other[1] < one[3];
        }

        /** Each shipped type's abilities in fan order, by the type named in their display key. */
        private static Map<String, List<ClientAbility>> shippedFans() {
            return shippedAbilities().stream()
                    .sorted(AbilityBadge.fanOrder(ClientAbility::badge, ClientAbility::order))
                    .collect(Collectors.groupingBy(ability -> ability.displayName()
                            .substring(ABILITY_KEY_PREFIX.length()).split("\\.")[0]));
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
        void wedgeReadsItsFirstThrowCost() {
            ClientAbility cheap = costing(AFFORDABLE);
            ClientAbility dear = costing(UNAFFORDABLE);

            assertEquals("1K | x1", RadialWheelRenderer.fanSlot(cheap, HOLDINGS).costLabel());
            assertEquals("2K | x0", RadialWheelRenderer.fanSlot(dear, HOLDINGS).costLabel());
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
        private static final int LINES_PER_ABILITY = 2;
        /** An ability index no petal holds, so a frame rendered with it locks nothing. */
        private static final int NO_ABILITY = -1;
        /** The open type's ability a locked frame locks. */
        private static final int LOCKED_ABILITY = 1;
        /** Stands for every ability of the open type, each given an on_prism reaction. */
        private static final int EVERY_ABILITY = -2;
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
        /** The open type's ability whose JSON names cost mechanics; the rest charge a plain cost. */
        private static final int ZERO_COST_ABILITY = 2;
        /** The open type's ability charging a plain cost that a test hovers. */
        private static final int PLAIN_COST_ABILITY = 0;
        /** Every test ability's description key, which the fake lang reads back as written. */
        private static final String DESCRIPTION = "ability.gootest.word.description";
        /** The zero-cost ability's cost-mechanics key, which the fake lang reads back as written. */
        private static final String COST_MECHANICS = "ability.gootest.mechanics";
        /** How far, in pixels, an item's center may stray from the petal's center line or its stride. */
        private static final double COLUMN_TOLERANCE = 1.5;

        private static final PetalLook.SpriteBox SPRITE = new PetalLook.SpriteBox(0.25f, 0.5f, 0.125f, 0.25f);
        private static final int EDGE_COLOR = 0xFF123456;
        private static final String BAKED_PETAL_PATH = "dynamic/radial_arc_";
        /** How near a grid line a corner sits to read either edge of the sprite, as a fraction of a tile. */
        private static final double TILE_TOLERANCE = 1e-3;
        /** How far a sampled UV may stray from the expected, in atlas units: float rounding of screen positions. */
        private static final double SAMPLE_TOLERANCE = 1e-3;
        /** How far, in pixels, an icon's center may sit from its tip's center: integer pixel rounding. */
        private static final double TIP_TOLERANCE = 2.0;
        /** Every line's width in pixels, as the stubbed font measures it. */
        private static final int WORD_WIDTH = 40;
        /** The font's line height, which a mocked Font keeps from its declaration. */
        private static final int LINE_HEIGHT = 9;
        /** A type icon's side, as the renderer draws it. */
        private static final int TYPE_ICON_SIZE = 12;
        /** How many poses deep the frame's matrix stack nests: the panel's push, then a scaled word's. */
        private static final int POSE_DEPTH = 4;
        /** Where a text call's ARGB color sits among its arguments. */
        private static final int TEXT_COLOR_ARGUMENT = 4;
        /** Where a blit's ARGB color sits among its arguments. */
        private static final int ICON_COLOR_ARGUMENT = 10;

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
            public CostMechanicsLookup costMechanicsLookup(ResourceKey<GooTypeDefinition> type) {
                return new CostMechanicsLookup(key -> key, item -> GooFraction.ZERO);
            }
        };

        /**
         * One line of words the frame drew, read through the pose it drew under: the screen x it centered on, its
         * screen top, what, its color and the scale it drew at.
         */
        private record DrawnText(double x, double y, Component text, int color, float scale) {
        }

        /** Every line each rendered frame drew, in draw order, by the graphics it drew to. */
        private final Map<GuiGraphicsExtractor, List<DrawnText>> drawnTexts = new java.util.IdentityHashMap<>();

        /**
         * Records each line a mocked graphics draws, its place carried through the pose it draws under, so a
         * scaled line reads back where it lands on screen.
         */
        private void recordTexts(GuiGraphicsExtractor graphics, Matrix3x2fStack pose) {
            List<DrawnText> drawn = new ArrayList<>();
            drawnTexts.put(graphics, drawn);
            doAnswer(call -> {
                org.joml.Vector2f center = pose.transformPosition((int) call.getArgument(2) + WORD_WIDTH / 2f,
                        (int) call.getArgument(3), new org.joml.Vector2f());
                drawn.add(new DrawnText(center.x, center.y, call.getArgument(1), call.getArgument(TEXT_COLOR_ARGUMENT),
                        pose.m00()));
                return null;
            }).when(graphics).text(any(Font.class), any(Component.class), anyInt(), anyInt(), anyInt(), anyBoolean());
        }

        private final List<ResourceKey<GooTypeDefinition>> types = IntStream.range(0, TYPES)
                .mapToObj(type -> ResourceKey.create(GooTypes.REGISTRY,
                        Identifier.fromNamespaceAndPath(Goo.MODID, "type_" + type)))
                .toList();

        private RadialWheel openWheel() {
            return openedWheel(TYPES, ABILITIES, OPEN_TYPE, RADIUS);
        }

        private GuiGraphicsExtractor renderFrame(RadialWheel wheel) {
            return renderFrame(wheel, NO_ABILITY);
        }

        /**
         * Renders the frame with one ability of the open type locked.
         *
         * @param wheel         the wheel's state
         * @param lockedAbility the open type's ability index to lock, or {@link #NO_ABILITY}
         * @return the mocked graphics the frame drew to
         */
        private GuiGraphicsExtractor renderFrame(RadialWheel wheel, int lockedAbility) {
            return renderFrame(wheel, lockedAbility, NO_ABILITY);
        }

        /**
         * Renders the frame with one ability of the open type locked and one
         * carrying an on_prism reaction.
         *
         * @param wheel         the wheel's state
         * @param lockedAbility the open type's ability index to lock, or {@link #NO_ABILITY}
         * @param prismAbility  the open type's ability index reacting to prisms, or {@link #NO_ABILITY}
         * @return the mocked graphics the frame drew to
         */
        private GuiGraphicsExtractor renderFrame(RadialWheel wheel, int lockedAbility, int prismAbility) {
            return renderFrame(wheel, lockedAbility, prismAbility, RADIUS);
        }

        /**
         * Renders the frame at a wheel radius, with one ability of the open
         * type locked and one, or every one, carrying an on_prism reaction.
         *
         * @param wheel         the wheel's state
         * @param lockedAbility the open type's ability index to lock, or {@link #NO_ABILITY}
         * @param prismAbility  the open type's ability index reacting to prisms, {@link #EVERY_ABILITY}
         *                      for all of them, or {@link #NO_ABILITY}
         * @param radius        the wheel's outer radius in pixels
         * @return the mocked graphics the frame drew to
         */
        private GuiGraphicsExtractor renderFrame(RadialWheel wheel, int lockedAbility, int prismAbility, int radius) {
            return renderFrame(wheel, lockedAbility, prismAbility, radius, null);
        }

        /**
         * Renders the frame, then names whatever the cursor rests on.
         *
         * @param wheel         the wheel's state
         * @param lockedAbility the open type's ability index to lock, or {@link #NO_ABILITY}
         * @param prismAbility  the open type's ability index reacting to prisms, {@link #EVERY_ABILITY}
         *                      for all of them, or {@link #NO_ABILITY}
         * @param radius        the wheel's outer radius in pixels
         * @param cursor        the cursor's screen x and y, or null to name nothing
         * @return the mocked graphics the frame drew to
         */
        private GuiGraphicsExtractor renderFrame(RadialWheel wheel, int lockedAbility, int prismAbility, int radius,
                                                 int @org.jspecify.annotations.Nullable [] cursor) {
            List<List<OfferedAbility>> abilities = IntStream.range(0, TYPES)
                    .mapToObj(type -> IntStream.range(0, ABILITIES).mapToObj(ability -> new OfferedAbility(
                            new ClientAbility(
                            Identifier.fromNamespaceAndPath("gootest", "ability_" + type + "_" + ability),
                            "ability.gootest.word", "", 0, List.of(), List.of(), 0, Delivery.ARC, AbilityBadge.WORLD,
                            List.of(), AbilityArea.NONE, IndicatorShowing.HELD, List.of(),
                            AbilityDefinition.NO_UPKEEP, DistancePrice.NONE,
                            type == OPEN_TYPE && (ability == prismAbility || prismAbility == EVERY_ABILITY)
                                    ? List.of(mock(Step.class)) : List.of(),
                            ArmPose.defaultFor(DeliveryKind.ARC), DESCRIPTION,
                            ability == ZERO_COST_ABILITY ? Optional.of(COST_MECHANICS) : Optional.empty()),
                            type == OPEN_TYPE && ability == lockedAbility ? REQUIRED : List.of()))
                            .toList())
                    .toList();
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Matrix3x2fStack pose = new Matrix3x2fStack(POSE_DEPTH);
            when(graphics.pose()).thenReturn(pose);
            recordTexts(graphics, pose);
            Font font = mock(Font.class);
            when(font.width(any(FormattedText.class))).thenReturn(WORD_WIDTH);
            RadialWheelRenderer.Hoverables hoverables = RadialWheelRenderer.render(graphics, font,
                    frameOf(wheel, abilities, radius));
            if (cursor != null) {
                RadialWheelRenderer.nameWhatTheCursorRestsOn(graphics, font, hoverables, cursor[0], cursor[1]);
            }
            return graphics;
        }

        /** The tooltip lines the frame set for the cursor. */
        private List<Component> tooltipsSet(GuiGraphicsExtractor graphics) {
            return submittedArguments(graphics, "setTooltipForNextFrame", Component.class);
        }

        /**
         * A cursor resting on a petal's own badge names its kind, on the
         * prism badge peeking out under it names Prism, and on the petal's
         * face away from both names no kind.
         * decision hovering-a-badge-names-its-kind
         */
        @Test
        void aBadgeUnderTheCursorNamesItsKind() {
            RadialWheel wheel = openWheel();
            RadialWheel.PetalArc petal = wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .findFirst().orElseThrow();
            int[] icon = RadialWheelRenderer.contentCenter(frameOf(wheel, List.of()), petal);
            int[] own = RadialWheelRenderer.badgeOnCorner(icon);
            int[] prism = RadialWheelRenderer.prismBadgeAbove(own);
            int badgeHalf = RadialWheelRenderer.BADGE_SIZE / 2;
            int[] onOwn = {own[0] + badgeHalf / 2, own[1] + badgeHalf / 2};
            int[] onPrismBelowOwn = aPointShowing(new RadialWheelRenderer.ItemRect(prism[0] - badgeHalf,
                    prism[1] - badgeHalf, RadialWheelRenderer.BADGE_SIZE), List.of(RadialWheelRenderer.badgeBox(icon)));
            Component world = Component.translatable("goo.badge." + AbilityBadge.WORLD.getSerializedName());
            Component prismName = Component.translatable("goo.badge." + AbilityBadge.PRISM.getSerializedName());

            assertAll(
                    () -> assertEquals(List.of(world),
                            tooltipsSet(renderFrame(wheel, NO_ABILITY, petal.ability(), RADIUS, onOwn)), "own badge"),
                    () -> assertEquals(List.of(prismName), tooltipsSet(renderFrame(wheel, NO_ABILITY,
                            petal.ability(), RADIUS, onPrismBelowOwn)), "prism badge"),
                    () -> assertEquals(List.of(), tooltipsSet(renderFrame(wheel, NO_ABILITY,
                            petal.ability(), RADIUS, icon)), "petal face"),
                    () -> assertEquals(List.of(), tooltipsSet(renderFrame(wheel, NO_ABILITY,
                            NO_ABILITY, RADIUS, onPrismBelowOwn)), "no prism badge drawn"));
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

        private RadialWheelRenderer.Frame frameOf(RadialWheel wheel, List<List<OfferedAbility>> abilities) {
            return frameOf(wheel, abilities, RADIUS);
        }

        private RadialWheelRenderer.Frame frameOf(RadialWheel wheel, List<List<OfferedAbility>> abilities,
                                                  int radius) {
            return new RadialWheelRenderer.Frame(wheel, types, abilities,
                    Map.of(types.get(OPEN_TYPE), HOLDINGS), CENTER_X, CENTER_Y, radius, fakeLook, 0.0f);
        }

        /** Whether a screen point lies within a petal's angular span and between its inner and outer radius. */
        private static boolean insidePetal(RadialWheel.PetalArc petal, double x, double y) {
            double distance = Math.hypot(x - CENTER_X, y - CENTER_Y) / RADIUS;
            double offset = RadialWheel.angleOf(x - CENTER_X, y - CENTER_Y) - petal.start();
            double wrapped = (offset % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            return wrapped < petal.arc() && distance > petal.shape().inner() && distance < petal.shape().outer();
        }

        /** Whether all four corners of an item's square lie inside a petal. */
        private static boolean squareInsidePetal(RadialWheel.PetalArc petal, RadialWheelRenderer.ItemRect rect) {
            int size = RadialWheelRenderer.ITEM_ICON_SIZE;
            return insidePetal(petal, rect.left(), rect.top()) && insidePetal(petal, rect.left() + size, rect.top())
                    && insidePetal(petal, rect.left(), rect.top() + size)
                    && insidePetal(petal, rect.left() + size, rect.top() + size);
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
         * part under the petal's face, and the cursor still names the item
         * anywhere on its whole square.
         * decision icons-slide-in-from-behind-the-tip
         */
        @Test
        void itemStraddlingTheBorderCutsAtItAndStillNamesItselfAcrossItsSquare() {
            RadialWheel wheel = openWheel();
            RadialWheelRenderer.Frame frame = frameOf(wheel, List.of());
            RadialWheel.PetalArc petal = wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .findFirst().orElseThrow();
            PetalMask.Point edge = PetalMask.Point.polar(petal.center(), petal.shape().outer());
            int half = RadialWheelRenderer.ITEM_ICON_SIZE / 2;
            RadialWheelRenderer.ItemRect rect = new RadialWheelRenderer.ItemRect(
                    (int) Math.round(CENTER_X + edge.x() * RADIUS) - half,
                    (int) Math.round(CENTER_Y + edge.y() * RADIUS) - half);
            Identifier glass = Identifier.withDefaultNamespace("glass");
            RadialWheelRenderer.ItemIcon icon = new RadialWheelRenderer.ItemIcon(glass, mock(ItemStack.class), rect,
                    false, petal.shape());

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
            double far = RadialWheelRenderer.ITEM_ICON_SIZE - 0.5;
            assertAll(List.of(new double[]{0, 0}, new double[]{far, 0}, new double[]{0, far}, new double[]{far, far})
                    .stream().map(offset -> (Executable) () -> assertEquals(glass, RadialWheelRenderer.itemUnder(
                            List.of(icon), rect.left() + offset[0], rect.top() + offset[1]).item(),
                            "corner " + offset[0] + "," + offset[1])));
        }

        private static double[] centerOf(RadialWheelRenderer.ItemRect rect) {
            return new double[]{rect.left() + RadialWheelRenderer.ITEM_ICON_SIZE / 2.0,
                    rect.top() + RadialWheelRenderer.ITEM_ICON_SIZE / 2.0};
        }

        private List<DrawnText> renderTexts(RadialWheel wheel) {
            return wordsDrawn(renderFrame(wheel), color -> color != RadialWheelRenderer.OUTLINE_COLOR);
        }

        /** The lines a frame drew in the colors a test asks for, each read back to its center on screen. */
        private List<DrawnText> wordsDrawn(GuiGraphicsExtractor graphics, java.util.function.IntPredicate color) {
            return drawnTexts.get(graphics).stream().filter(text -> color.test(text.color())).toList();
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void everyLineDrawsInsideADarkBorderAllTheWayAround() {
            GuiGraphicsExtractor graphics = renderFrame(restingOnOpenType(RADIUS));
            List<DrawnText> lines = wordsDrawn(graphics, color -> color != RadialWheelRenderer.OUTLINE_COLOR);
            List<DrawnText> border = wordsDrawn(graphics, color -> color == RadialWheelRenderer.OUTLINE_COLOR);
            double slack = 1e-3;

            assertFalse(lines.isEmpty());
            assertAll(lines.stream().map(line -> (Executable) () -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        double x = line.x() + dx * line.scale();
                        double y = line.y() + dy * line.scale();
                        boolean expected = dx != 0 || dy != 0;
                        assertEquals(expected, border.stream().anyMatch(edge -> Math.abs(edge.x() - x) < slack
                                && Math.abs(edge.y() - y) < slack && edge.text().equals(line.text())),
                                line + " border at " + dx + "," + dy);
                    }
                }
            }));
        }

        private <T> List<T> submittedArguments(GuiGraphicsExtractor graphics, String method, Class<T> type) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals(method))
                    .flatMap(call -> Arrays.stream(call.getArguments()))
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        private static boolean isInHub(DrawnText text) {
            return Math.hypot(text.x() - CENTER_X, text.y() - CENTER_Y) < RadialWheel.HUB_FRACTION * RADIUS;
        }

        /** Whether a word lies out on the outer ring, past the hub and the band the types drop into. */
        private static boolean isOnTheRing(DrawnText text) {
            return Math.hypot(text.x() - CENTER_X, text.y() - CENTER_Y) >= RadialWheel.TYPE_INNER_FRACTION * RADIUS;
        }

        /** The outer ring's petal under a word: an ability petal, the type petals standing in the band inside it. */
        private static RadialWheel.PetalArc petalUnder(RadialWheel wheel, DrawnText text) {
            return RadialWheel.petalAt(wheel.layout().stream().filter(RadialWheel.PetalArc::isAbility).toList(),
                    RadialWheel.angleOf(text.x() - CENTER_X, text.y() - CENTER_Y));
        }

        @Test
        void hubDrawsTheOpenTypesNameAndHoldings() {
            List<Component> center = renderTexts(openWheel()).stream()
                    .filter(OpenTypeFrame::isInHub)
                    .map(DrawnText::text)
                    .toList();

            assertEquals(List.of(Component.translatable(GooTypeNames.translationKey(types.get(OPEN_TYPE))),
                    Component.literal(RadialWheelRenderer.holdingsLabel(HOLDINGS))), center);
        }

        @Test
        void eachAbilityPetalDrawsTwoLinesAndNoTypePetalDrawsText() {
            RadialWheel wheel = restingOnOpenType(RADIUS);
            Map<RadialWheel.PetalArc, Long> linesByPetal = renderTexts(wheel).stream()
                    .filter(OpenTypeFrame::isOnTheRing)
                    .collect(Collectors.groupingBy(text -> petalUnder(wheel, text), Collectors.counting()));

            assertTrue(linesByPetal.keySet().stream().allMatch(RadialWheel.PetalArc::isAbility),
                    "text drawn on a type petal: " + linesByPetal);
            assertAll(wheel.layout().stream().filter(RadialWheel.PetalArc::isAbility).map(petal -> (Executable) () ->
                    assertEquals(LINES_PER_ABILITY, linesByPetal.getOrDefault(petal, 0L), petal.toString())));
        }

        /** decision petals-render-the-live-fluid */
        @Test
        void petalFillSamplesTheAtlasSpriteAndBlitsNoBakedPetal() {
            GuiGraphicsExtractor graphics = renderFrame(openWheel());
            List<PetalRenderState> fills = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class).stream().filter(this::isFill).toList();
            List<PetalRenderState.ScreenVertex> corners = fills.stream()
                    .flatMap(fill -> fill.vertices().stream()).toList();

            assertEquals(openWheel().displayedLayout(0.0f).size(), fills.size());
            assertTrue(corners.stream().allMatch(corner -> corner.u() >= SPRITE.u0() && corner.u() <= SPRITE.u1()
                    && corner.v() >= SPRITE.v0() && corner.v() <= SPRITE.v1()), "a corner samples off the sprite");
            assertEquals(List.of(SPRITE.u0(), SPRITE.u1(), SPRITE.v0(), SPRITE.v1()), List.of(
                    corners.stream().map(PetalRenderState.ScreenVertex::u).min(Float::compare).orElseThrow(),
                    corners.stream().map(PetalRenderState.ScreenVertex::u).max(Float::compare).orElseThrow(),
                    corners.stream().map(PetalRenderState.ScreenVertex::v).min(Float::compare).orElseThrow(),
                    corners.stream().map(PetalRenderState.ScreenVertex::v).max(Float::compare).orElseThrow()));
            assertTrue(submittedArguments(graphics, "blit", Identifier.class).stream()
                    .noneMatch(texture -> texture.getPath().startsWith(BAKED_PETAL_PATH)));
            assertAll(corners.stream().map(corner -> (Executable) () -> {
                assertTiled(corner.u(), corner.x(), CENTER_X, SPRITE.u0(), SPRITE.u1());
                assertTiled(corner.v(), corner.y(), CENTER_Y, SPRITE.v0(), SPRITE.v1());
            }));
        }

        /**
         * A corner samples the sprite at the fraction its screen position falls
         * within its tile of the fixed grid; a corner on a grid line may read
         * either edge of the sprite.
         */
        private static void assertTiled(float sampled, float screen, int center, float low, float high) {
            double normalized = (screen - center) / (double) RADIUS;
            double across = (normalized - PetalMesh.TILE_ORIGIN) / PetalMesh.TILE;
            double fraction = across - Math.floor(across);
            double expected = low + fraction * (high - low);
            boolean onGridLine = fraction < TILE_TOLERANCE || fraction > 1 - TILE_TOLERANCE;
            assertTrue(Math.abs(sampled - expected) < SAMPLE_TOLERANCE
                    || onGridLine && (Math.abs(sampled - low) < SAMPLE_TOLERANCE
                    || Math.abs(sampled - high) < SAMPLE_TOLERANCE),
                    "sampled " + sampled + " expected " + expected + " at " + screen);
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void abilityIconCentersOnItsTipWithTheNameAboveAndTheCostBelow() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel);
            List<DrawnText> texts = renderTexts(wheel);
            int half = RadialWheelRenderer.ABILITY_ICON_SIZE / 2;

            List<PetalRenderState> submitted = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class);
            TextureSetup badge = sprites.get(RadialWheelRenderer.badgeIcon(AbilityBadge.WORLD));

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        PetalMask.Point tip = petal.shape().tipCenter();
                        PetalRenderState iconState = spriteOf(submitted, iconOf(petal));
                        float[] iconBox = boxOf(iconState);
                        int iconX = Math.round(iconBox[0]) + half;
                        int iconY = Math.round(iconBox[1]) + half;
                        assertTrue(Math.hypot(iconX - (CENTER_X + tip.x() * RADIUS),
                                iconY - (CENTER_Y + tip.y() * RADIUS)) <= TIP_TOLERANCE,
                                petal + " icon at " + iconX + "," + iconY);
                        assertEquals(0xFFFFFFFF, iconState.color(), petal + " tint");
                        PetalRenderState badgeState = submitted.get(submitted.indexOf(iconState) + 1);
                        assertTrue(badgeState.textureSetup() == badge, petal + " badge follows");
                        float[] badgeBox = boxOf(badgeState);
                        RadialWheelRenderer.ItemRect expected = RadialWheelRenderer.badgeBox(new int[]{iconX, iconY});
                        assertEquals(expected.left(), Math.round(badgeBox[0]), petal + " badge's left edge");
                        assertEquals(expected.top(), Math.round(badgeBox[1]), petal + " badge's top edge");
                        List<DrawnText> words = texts.stream().filter(text -> text.x() == iconX
                                && Math.abs(text.y() - iconY) <= half + 2 * LINE_HEIGHT).toList();
                        assertEquals(LINES_PER_ABILITY, words.size(), petal + " lines");
                        assertTrue(words.getFirst().y() + LINE_HEIGHT * words.getFirst().scale() <= iconY - half,
                                petal + " name above");
                        assertTrue(words.getLast().y() >= iconY + half, petal + " cost below");
                    }));
        }

        /**
         * Every ability carrying an on_prism reaction paints its icon, then
         * the prism badge, then its own badge over it, each where
         * {@link RadialWheelRenderer#badgeLayout} places it and whole on its
         * petal at the wheel radius a GUI of scale 3 and of scale 4 draws on a
         * 1080p screen.
         * decision on-prism-reaction-wears-the-prism-badge
         * decision prism-badge-sits-behind-and-above-barely-overlapping
         */
        @ParameterizedTest
        @ValueSource(ints = {SCALE_THREE_WIDTH, SCALE_FOUR_WIDTH})
        void prismBadgeDrawsWholeBehindItsPartnerOnEveryPetal(int guiWidth) {
            int radius = (int) RadialWheel.outerRadius(guiWidth, guiWidth * GUI_HEIGHT_PER_WIDTH / GUI_WIDTH_UNITS);
            RadialWheel wheel = openWheel();
            List<PetalRenderState> submitted = submittedArguments(
                    renderFrame(wheel, NO_ABILITY, EVERY_ABILITY, radius), "submitGuiElementRenderState",
                    PetalRenderState.class);
            int size = RadialWheelRenderer.BADGE_SIZE;
            int half = RadialWheelRenderer.ABILITY_ICON_SIZE / 2;
            TextureSetup prismBadge = sprites.get(RadialWheelRenderer.badgeIcon(AbilityBadge.PRISM));
            TextureSetup worldBadge = sprites.get(RadialWheelRenderer.badgeIcon(AbilityBadge.WORLD));

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        PetalRenderState iconState = spriteOf(submitted, iconOf(petal));
                        int iconAt = submitted.indexOf(iconState);
                        PetalRenderState prismState = submitted.get(iconAt + 1);
                        PetalRenderState ownState = submitted.get(iconAt + 2);
                        assertTrue(prismState.textureSetup() == prismBadge, petal + " prism badge after the icon");
                        assertTrue(ownState.textureSetup() == worldBadge, petal + " own badge over the prism badge");
                        float[] iconBox = boxOf(iconState);
                        RadialWheelRenderer.ItemRect expected = RadialWheelRenderer.badgeLayout(new int[]{
                                Math.round(iconBox[0]) + half, Math.round(iconBox[1]) + half}, AbilityBadge.WORLD,
                                true).getFirst().rect();
                        float[] prismBox = boxOf(prismState);
                        assertEquals(expected.left(), Math.round(prismBox[0]), petal + " prism badge's left");
                        assertEquals(expected.top(), Math.round(prismBox[1]), petal + " prism badge's top");
                        assertEquals(size * size, areaOf(prismState), AREA_TOLERANCE, petal + " prism badge shown whole");
                    }));
        }

        /** The wheel opened on the open type with the cursor on one of its abilities. */
        private static RadialWheel hoveringAbility(int ability, int radius) {
            RadialWheel wheel = openedWheel(TYPES, ABILITIES, OPEN_TYPE, radius);
            RadialWheel.PetalArc petal = wheel.layout().stream()
                    .filter(arc -> arc.isAbility() && arc.ability() == ability).findFirst().orElseThrow();
            double reach = (petal.shape().inner() + petal.shape().outer()) / 2 * radius;
            wheel.moveCursor(Math.sin(petal.center()) * reach, -Math.cos(petal.center()) * reach, radius);
            for (int tick = 0; tick < RingEase.DURATION_TICKS; tick++) {
                wheel.tick();
            }
            assertEquals(ability, wheel.hoveredAbility(), "the cursor hovers the ability");
            return wheel;
        }

        /**
         * The wheel opened on the open type with the cursor resting on that type's
         * petal in the inner band, so no ability is hovered and no panel draws.
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

        /** The left edge of each text call drawing a line of words, by the words. */
        private static Map<String, Integer> panelTextLefts(GuiGraphicsExtractor graphics) {
            Map<String, Integer> lefts = new HashMap<>();
            mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("text")
                            && (int) call.getArgument(TEXT_COLOR_ARGUMENT) != RadialWheelRenderer.OUTLINE_COLOR)
                    .forEach(call -> lefts.put(((Component) call.getArgument(1)).getString(), call.getArgument(2)));
            return lefts;
        }

        /** The left edge of every panel background quad the frame blitted. */
        private static List<Integer> panelBackgroundLefts(GuiGraphicsExtractor graphics) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("blit")
                            && InWorldHud.BG_TEXTURE.equals(call.getArgument(1)))
                    .map(call -> (Integer) call.getArgument(2))
                    .toList();
        }

        private static int radiusAt(int guiWidth) {
            return (int) RadialWheel.outerRadius(guiWidth, guiWidth * GUI_HEIGHT_PER_WIDTH / GUI_WIDTH_UNITS);
        }

        /**
         * A hovered zero-cost ability draws its description and its cost-mechanics
         * text in a panel whose box lies wholly right of the wheel's outer radius.
         * decision explanation-panel-draws-right-of-the-wheel
         */
        @ParameterizedTest
        @ValueSource(ints = {SCALE_THREE_WIDTH, SCALE_FOUR_WIDTH})
        void hoveredZeroCostAbilityExplainsItselfRightOfTheWheel(int guiWidth) {
            int radius = radiusAt(guiWidth);
            GuiGraphicsExtractor graphics = renderFrame(hoveringAbility(ZERO_COST_ABILITY, radius), NO_ABILITY,
                    NO_ABILITY, radius);
            Map<String, Integer> lefts = panelTextLefts(graphics);
            List<Integer> box = panelBackgroundLefts(graphics);
            int wheelRight = CENTER_X + radius;

            assertTrue(lefts.containsKey(DESCRIPTION), "description drawn: " + lefts.keySet());
            assertTrue(lefts.containsKey(COST_MECHANICS), "cost mechanics drawn: " + lefts.keySet());
            assertFalse(box.isEmpty(), "panel background drawn");
            assertTrue(box.stream().allMatch(left -> left > wheelRight), "box left of " + wheelRight + ": " + box);
            assertTrue(lefts.get(DESCRIPTION) > wheelRight, "description at " + lefts.get(DESCRIPTION));
            assertTrue(lefts.get(COST_MECHANICS) > wheelRight, "cost mechanics at " + lefts.get(COST_MECHANICS));
        }

        /**
         * A hovered ability charging a plain cost draws its description and no
         * cost-mechanics text.
         * decision explanation-panel-draws-right-of-the-wheel
         */
        @ParameterizedTest
        @ValueSource(ints = {SCALE_THREE_WIDTH, SCALE_FOUR_WIDTH})
        void hoveredPlainCostAbilityDrawsItsDescriptionAlone(int guiWidth) {
            int radius = radiusAt(guiWidth);
            Map<String, Integer> lefts = panelTextLefts(renderFrame(hoveringAbility(PLAIN_COST_ABILITY, radius),
                    NO_ABILITY, NO_ABILITY, radius));

            assertTrue(lefts.containsKey(DESCRIPTION), "description drawn: " + lefts.keySet());
            assertTrue(lefts.get(DESCRIPTION) > CENTER_X + radius, "description at " + lefts.get(DESCRIPTION));
            assertFalse(lefts.containsKey(COST_MECHANICS), "cost mechanics drawn for a plain cost");
        }

        /**
         * With no ability hovered, no panel draws.
         * decision explanation-panel-draws-right-of-the-wheel
         */
        @ParameterizedTest
        @ValueSource(ints = {SCALE_THREE_WIDTH, SCALE_FOUR_WIDTH})
        void noHoveredAbilityDrawsNoPanel(int guiWidth) {
            int radius = radiusAt(guiWidth);
            RadialWheel wheel = restingOnOpenType(radius);
            assertEquals(RadialWheel.NONE, wheel.hoveredAbility(), "no ability hovered");

            GuiGraphicsExtractor graphics = renderFrame(wheel, NO_ABILITY, NO_ABILITY, radius);

            assertFalse(panelTextLefts(graphics).containsKey(DESCRIPTION), "description drawn with nothing hovered");
            assertTrue(panelBackgroundLefts(graphics).isEmpty(), "panel background drawn with nothing hovered");
        }

        /**
         * An ability carrying no on_prism reaction paints its own badge alone.
         * decision on-prism-reaction-wears-the-prism-badge
         */
        @Test
        void anAbilityWithoutAPrismReactionPaintsNoPrismBadge() {
            renderFrame(openWheel(), NO_ABILITY, NO_ABILITY);

            assertFalse(sprites.containsKey(RadialWheelRenderer.badgeIcon(AbilityBadge.PRISM)),
                    "the frame bound the prism badge's sprite");
        }

        /** The screen area a submission's quads cover, each quad's corners in winding order. */
        private static double areaOf(PetalRenderState state) {
            List<PetalRenderState.ScreenVertex> corners = state.vertices();
            double area = 0;
            for (int quad = 0; quad + 3 < corners.size(); quad += 4) {
                double twice = 0;
                for (int corner = 0; corner < 4; corner++) {
                    PetalRenderState.ScreenVertex from = corners.get(quad + corner);
                    PetalRenderState.ScreenVertex to = corners.get(quad + (corner + 1) % 4);
                    twice += from.x() * to.y() - to.x() * from.y();
                }
                area += Math.abs(twice) / 2;
            }
            return area;
        }

        /**
         * A locked petal's fill draws under the disabled tint an unaffordable
         * petal takes; its unlocked siblings stay bright.
         * decision locked-petal-stays-on-the-wheel
         */
        @Test
        void lockedPetalFillDrawsUnderTheDisabledTint() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel, LOCKED_ABILITY);
            int disabledTint = RadialWheelRenderer.computeOverlayTint(false, true);
            List<RadialWheel.PetalArc> petals = wheel.displayedLayout(0.0f);
            List<PetalRenderState> fills = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class).stream().filter(this::isFill).toList();

            assertAll(petals.stream().filter(RadialWheel.PetalArc::isAbility).map(petal -> (Executable) () ->
                    assertEquals(petal.ability() == LOCKED_ABILITY, fills.get(petals.indexOf(petal)).color()
                            == disabledTint, petal + " fill tint")));
        }

        /**
         * A locked petal draws no words and no ability icon, only one item per
         * required item inside the petal, with one red slash over each
         * learned item and none over the rest.
         * decision locked-petal-lists-the-unlearned-items
         */
        @Test
        void lockedPetalDrawsOnlyItsRequiredItemsWithTheLearnedSlashed() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel, LOCKED_ABILITY);
            RadialWheel.PetalArc locked = wheel.layout().stream()
                    .filter(petal -> petal.isAbility() && petal.ability() == LOCKED_ABILITY).findFirst().orElseThrow();
            List<RadialWheelRenderer.ItemRect> items = submittedArguments(graphics,
                    "submitPictureInPictureRenderState", CutItemRenderState.class).stream()
                    .map(state -> new RadialWheelRenderer.ItemRect(state.x0(), state.y0()))
                    .toList();
            List<PetalRenderState> slashes = submittedArguments(graphics,
                    "submitGuiElementRenderState", PetalRenderState.class).stream()
                    .filter(state -> state.color() == RadialWheelRenderer.SLASH_COLOR)
                    .toList();
            List<PetalRenderState.ScreenVertex> slashCorners = slashes.stream()
                    .flatMap(state -> state.vertices().stream())
                    .toList();
            String lockedIcon = "ability_" + locked.type() + "_" + locked.ability() + ".png";

            int[] lockedTip = RadialWheelRenderer.tipCenter(frameOf(wheel, List.of()), locked);
            assertTrue(wordsDrawn(graphics, color -> color != RadialWheelRenderer.OUTLINE_COLOR).stream()
                    .filter(OpenTypeFrame::isOnTheRing)
                    .noneMatch(text -> petalUnder(wheel, text).equals(locked) || text.x() == lockedTip[0]),
                    "a word drew on the locked petal");
            assertTrue(mockingDetails(graphics).getInvocations().stream()
                    .noneMatch(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().endsWith(lockedIcon)), "the locked ability's icon drew");
            assertEquals(REQUIRED.size(), items.size());
            assertAll(IntStream.range(0, items.size()).mapToObj(index -> (Executable) () -> {
                RadialWheelRenderer.ItemRect rect = items.get(index);
                // clustered squares' bounds may touch, so a slash belongs to the square holding all its corners
                boolean slashed = slashes.stream().anyMatch(slash -> !slash.vertices().isEmpty()
                        && slash.vertices().stream().allMatch(cell -> onSquare(rect, cell)));
                assertEquals(REQUIRED.get(index).learned(), slashed, rect + " slash");
            }));
            assertTrue(slashCorners.stream().allMatch(cell -> items.stream()
                    .anyMatch(rect -> onSquare(rect, cell))), "a slash drew off its item");
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
            RadialWheel.PetalArc petal = wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .findFirst().orElseThrow();
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


        private boolean isFill(PetalRenderState state) {
            return state.textureSetup() == atlas;
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
         * An unlocked ability petal's icon reaches the GUI as quads cut to its
         * petal, carrying the icon's own texture, after the petal's edge; a
         * type petal's icon still blits whole.
         * decision icons-slide-in-from-behind-the-tip
         */
        @Test
        void abilityIconSubmitsCutToItsPetalAfterTheEdgeAndTypeIconsBlitWhole() {
            RadialWheel wheel = openWheel();
            GuiGraphicsExtractor graphics = renderFrame(wheel);
            List<PetalRenderState> submitted = submittedArguments(graphics, "submitGuiElementRenderState",
                    PetalRenderState.class);

            assertAll(wheel.displayedLayout(0.0f).stream().filter(RadialWheel.PetalArc::isAbility)
                    .map(petal -> (Executable) () -> {
                        PetalRenderState icon = spriteOf(submitted, iconOf(petal));
                        int index = submitted.indexOf(icon);
                        PetalRenderState edge = submitted.get(index - 1);
                        assertFalse(edge.isTextured(), petal + " icon follows its edge");
                        assertAll(icon.vertices().stream().map(corner -> (Executable) () -> assertTrue(
                                PetalMeshTest.insideWithSlack(petal.shape(), (corner.x() - CENTER_X) / RADIUS,
                                        (corner.y() - CENTER_Y) / RADIUS), petal + " corner " + corner)));
                        assertTrue(icon.vertices().stream().allMatch(corner -> corner.u() >= 0 && corner.u() <= 1
                                && corner.v() >= 0 && corner.v() <= 1), petal + " samples off its icon");
                    }));
            assertTrue(mockingDetails(graphics).getInvocations().stream()
                    .noneMatch(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().contains("/ability/")), "an ability icon blitted whole");
            assertEquals(typePetalsOf(wheel).size(),
                    mockingDetails(graphics).getInvocations().stream()
                            .filter(call -> call.getMethod().getName().equals("blit")
                                    && call.getArgument(1).toString().contains("/type/")).count());
        }

        /** Every type petal the frame draws. */
        private static List<RadialWheel.PetalArc> typePetalsOf(RadialWheel wheel) {
            return wheel.displayedLayout(0.0f).stream().filter(petal -> !petal.isAbility()).toList();
        }

        /** The hub mask's blit: where it draws and how big. */
        private static org.mockito.invocation.Invocation hubBlit(GuiGraphicsExtractor graphics) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().endsWith("hub"))
                    .findFirst().orElseThrow();
        }

        /**
         * At rest the frame draws every type petal and no word, the neutral
         * zone's mask at half the wheel's reach; with a type open, at the hub's.
         * decision halves-arc-petals-at-rest
         */
        @Test
        void theNeutralZoneDrawsHalfTheWheelAtRestAndTheHubWithATypeOpen() {
            RadialWheel rest = new RadialWheel(TYPES, type -> ABILITIES);
            GuiGraphicsExtractor restFrame = renderFrame(rest);
            GuiGraphicsExtractor openFrame = renderFrame(openWheel());

            assertTrue(wordsDrawn(restFrame, color -> true).isEmpty(), "a word drew at rest");
            assertEquals(TYPES, rest.displayedLayout(0.0f).size());
            assertEquals(2 * RADIUS, (int) hubBlit(restFrame).getArgument(6));
            assertEquals((int) Math.round(RADIUS * RadialWheel.HUB_FRACTION / RadialWheel.HUB_REST_FRACTION) * 2,
                    (int) hubBlit(openFrame).getArgument(6));
        }

        /**
         * With a type open the frame draws every type petal in the band and
         * the open type's abilities: one fill per petal.
         * decision abilities-replace-the-hovered-type
         */
        @Test
        void anOpenFrameDrawsEveryTypeAndTheOpenTypesAbilities() {
            RadialWheel wheel = openWheel();
            List<RadialWheel.PetalArc> petals = wheel.displayedLayout(0.0f);
            List<PetalRenderState> fills = submittedArguments(renderFrame(wheel), "submitGuiElementRenderState",
                    PetalRenderState.class).stream().filter(this::isFill).toList();

            assertEquals(TYPES + ABILITIES, petals.size());
            assertEquals(petals.size(), fills.size());
            assertTrue(fills.stream().noneMatch(fill -> fill.vertices().isEmpty()), "a petal drew nothing");
        }
        private static org.mockito.invocation.Invocation iconBlit(GuiGraphicsExtractor graphics, String icon) {
            return mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().endsWith(icon))
                    .findFirst().orElseThrow();
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void typeIconSitsDeadCenterAlongItsPetalAtRestAndAtItsTipInTheInnerRing() {
            RadialWheel rest = new RadialWheel(TYPES, type -> ABILITIES);
            RadialWheel open = openWheel();
            GuiGraphicsExtractor restGraphics = renderFrame(rest);
            GuiGraphicsExtractor openGraphics = renderFrame(open);
            int half = TYPE_ICON_SIZE / 2;

            assertAll(typePetalsOf(rest).stream().map(petal -> (Executable) () -> {
                double along = (petal.inner() + petal.length()) / 2;
                assertIconAt(restGraphics, petal, PetalMask.Point.polar(petal.center(), along), half);
            }));
            assertAll(typePetalsOf(open).stream().map(petal -> (Executable) ()
                    -> assertIconAt(openGraphics, petal, petal.shape().tipCenter(), half)));
        }

        /**
         * With a type open, each type icon's drawn square centers on its tip's
         * center to within half a pixel either way, so an odd-sized icon
         * leans neither right nor down.
         */
        @Test
        void openTypeIconCentersOnItsTipToHalfAPixel() {
            GuiGraphicsExtractor openGraphics = renderFrame(openWheel());
            assertAll(typePetalsOf(openWheel()).stream().map(petal -> (Executable) () -> {
                PetalMask.Point tip = petal.shape().tipCenter();
                var blit = iconBlit(openGraphics, "type_" + petal.type() + ".png");
                double dx = (int) blit.getArgument(2) + TYPE_ICON_SIZE / 2.0 - (CENTER_X + tip.x() * RADIUS);
                double dy = (int) blit.getArgument(3) + TYPE_ICON_SIZE / 2.0 - (CENTER_Y + tip.y() * RADIUS);
                assertTrue(Math.abs(dx) <= 0.5 && Math.abs(dy) <= 0.5,
                        petal.type() + " icon center is off its tip by " + dx + ", " + dy);
            }));
        }

        /** Asserts a type petal's icon blitted centered on a point of the wheel. */
        private static void assertIconAt(GuiGraphicsExtractor graphics, RadialWheel.PetalArc petal,
                                         PetalMask.Point expected, int half) {
            String icon = "type_" + petal.type() + ".png";
            var iconBlit = mockingDetails(graphics).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("blit")
                            && call.getArgument(1).toString().endsWith(icon))
                    .findFirst().orElseThrow();
            assertTrue(Math.hypot((int) iconBlit.getArgument(2) + half - (CENTER_X + expected.x() * RADIUS),
                    (int) iconBlit.getArgument(3) + half - (CENTER_Y + expected.y() * RADIUS)) <= TIP_TOLERANCE,
                    petal + " icon off its place");
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void abilityWordsDrawAfterEveryPetalAndIcon() {
            List<String> calls = mockingDetails(renderFrame(restingOnOpenType(RADIUS))).getInvocations().stream()
                    .map(call -> call.getMethod().getName()).toList();
            int lastShape = Math.max(calls.lastIndexOf("submitGuiElementRenderState"), calls.lastIndexOf("blit"));

            assertTrue(calls.indexOf("text") > lastShape, "a word drew before shape " + lastShape);
        }

        /** decisions petals-render-the-live-fluid, wedges-take-a-solid-edge */
        @Test
        void petalEdgeStripDrawsUntexturedInTheEdgeColorFadedByPresence() {
            List<PetalRenderState> edges = submittedArguments(renderFrame(openWheel()),
                    "submitGuiElementRenderState", PetalRenderState.class).stream()
                    .filter(state -> !state.isTextured()).toList();
            List<RadialWheel.PetalArc> petals = openWheel().displayedLayout(0.0f);

            assertEquals(petals.size(), edges.size());
            assertAll(IntStream.range(0, edges.size()).mapToObj(index -> (Executable) () -> {
                assertEquals(RadialWheelRenderer.faded(EDGE_COLOR, petals.get(index).presence()),
                        edges.get(index).color());
                assertFalse(edges.get(index).vertices().isEmpty());
            }));
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
            int half = RadialWheelRenderer.ABILITY_ICON_SIZE / 2;
            List<RadialWheelRenderer.ItemRect> squares = new ArrayList<>(List.of(new RadialWheelRenderer.ItemRect(
                    center[0] - half, center[1] - half, RadialWheelRenderer.ABILITY_ICON_SIZE)));
            RadialWheelRenderer.badgeLayout(center, AbilityBadge.WORLD, true).forEach(badge -> squares.add(badge.rect()));

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
        void aTypeWhoseAbilityEndedHalfASecondAgoPulsesAtHalfTheSwing() {
            Afterglows<Identifier, Identifier> glows = new Afterglows<>();
            PetalPulse.pulses(glows, Set.of(BLAZE_KINDLE), 99.95f);
            PetalPulse.pulses(glows, Set.of(), 100f);
            Map<Identifier, Float> settling = PetalPulse.pulses(glows, Set.of(), 100f + Afterglow.FADE_TICKS / 2f);
            int halfSwing = Math.round((RadialWheelRenderer.HOVER_ALPHA - RadialWheelRenderer.NORMAL_ALPHA) / 2f);
            assertEquals(RadialWheelRenderer.NORMAL_ALPHA + halfSwing,
                    ARGB.alpha(RadialWheelRenderer.typePetalTint(frame(settling), BLAZE)));
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
