package com.mercuriusxeno.goo.tools.architecture;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The threshold all but a twentieth of a set hold at or under: a set under
 * twenty answers its maximum, so it allows no outlier.
 */
class TwentiethRuleTest {

    private static final int OUTLIER = 50;

    static Stream<Arguments> setsAndThresholds() {
        return Stream.of(
                Arguments.of(List.of(), 0),
                Arguments.of(List.of(3, 9, 5), 9),
                Arguments.of(peersAndOutliers(18, 3, 1), OUTLIER),
                Arguments.of(peersAndOutliers(19, 1, 1), 1),
                Arguments.of(peersAndOutliers(38, 2, 2), 2),
                Arguments.of(peersAndOutliers(37, 2, 3), OUTLIER));
    }

    private static List<Integer> peersAndOutliers(int peers, int peerValue, int outliers) {
        List<Integer> values = new ArrayList<>(Collections.nCopies(outliers, OUTLIER));
        values.addAll(Collections.nCopies(peers, peerValue));
        return values;
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("setsAndThresholds")
    void threshold(List<Integer> values, int expected) {
        assertEquals(expected, TwentiethRule.threshold(values));
    }
}
