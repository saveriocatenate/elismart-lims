package it.elismart_lims.service.validation;

import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutlierDetectionService}.
 *
 * <p>Tests cover the two outlier-detection criteria:
 * <ol>
 *   <li>%CV threshold — pairs exceeding {@code maxCvAllowed} are flagged.</li>
 *   <li>Grubbs test — a single statistical outlier in a group of ≥3 pairs at the
 *       same concentration is flagged; groups smaller than {@link OutlierDetectionService#GRUBBS_MIN_GROUP_SIZE}
 *       are skipped.</li>
 * </ol>
 * Tests also verify that outlier-flagged pairs are excluded from ValidationEngine
 * evaluation (exercised indirectly via the existing {@link ValidationEngineTest}).</p>
 */
class OutlierDetectionServiceTest {

    private OutlierDetectionService service;

    /**
     * Protocol with {@code maxCvAllowed = 10.0%} and a LINEAR curve type.
     * Used across all tests unless a stricter or different protocol is needed.
     */
    private Protocol protocol;

    @BeforeEach
    void setUp() {
        service = new OutlierDetectionService();
        protocol = Protocol.builder()
                .id(1L)
                .name("Test Protocol")
                .numCalibrationPairs(3)
                .numControlPairs(1)
                .maxCvAllowed(10.0)
                .maxErrorAllowed(20.0)
                .curveType(CurveType.LINEAR)
                .build();
    }

    // -------------------------------------------------------------------------
    // Criterion 1: %CV threshold
    // -------------------------------------------------------------------------

    /**
     * A pair whose cvPct (≈ 20%) exceeds maxCvAllowed (10%) must be flagged.
     */
    @Test
    @DisplayName("pair with cvPct above maxCvAllowed is flagged as outlier")
    void detectOutliers_highCvPair_isFlagged() {
        // signal1=10, signal2=18 → SD=|10-18|/√2≈5.66, mean=14, %CV≈40%
        double s1 = 10.0, s2 = 18.0;
        double mean = ValidationConstants.calculateSignalMean(s1, s2);
        double cv   = ValidationConstants.calculateCvPercent(s1, s2);

        MeasurementPair highCvPair = MeasurementPair.builder()
                .id(1L)
                .pairType(PairType.CONTROL)
                .concentrationNominal(5.0)
                .signal1(s1).signal2(s2)
                .signalMean(mean).cvPct(cv)
                .isOutlier(false)
                .build();

        List<Long> outliers = service.detectOutliers(List.of(highCvPair), protocol);

        assertThat(outliers).containsExactly(1L);
    }

    /**
     * A pair whose cvPct (0%) is below maxCvAllowed (10%) must NOT be flagged.
     */
    @Test
    @DisplayName("pair with cvPct below maxCvAllowed is not flagged")
    void detectOutliers_lowCvPair_isNotFlagged() {
        double signal = 15.0;
        double cv = ValidationConstants.calculateCvPercent(signal, signal); // 0.0

        MeasurementPair cleanPair = MeasurementPair.builder()
                .id(2L)
                .pairType(PairType.CONTROL)
                .concentrationNominal(5.0)
                .signal1(signal).signal2(signal)
                .signalMean(signal).cvPct(cv)
                .isOutlier(false)
                .build();

        List<Long> outliers = service.detectOutliers(List.of(cleanPair), protocol);

        assertThat(outliers).isEmpty();
    }

    /**
     * Mixed list: one high-CV pair and one clean pair — only the high-CV one is flagged.
     */
    @Test
    @DisplayName("only the pair with cvPct above threshold is flagged in a mixed list")
    void detectOutliers_mixedCvPairs_onlyHighCvFlagged() {
        double s1 = 10.0, s2 = 20.0;
        MeasurementPair bad = MeasurementPair.builder()
                .id(10L)
                .pairType(PairType.CONTROL)
                .concentrationNominal(5.0)
                .signal1(s1).signal2(s2)
                .signalMean(ValidationConstants.calculateSignalMean(s1, s2))
                .cvPct(ValidationConstants.calculateCvPercent(s1, s2))
                .isOutlier(false)
                .build();

        double ok = 12.0;
        MeasurementPair good = MeasurementPair.builder()
                .id(11L)
                .pairType(PairType.CONTROL)
                .concentrationNominal(10.0)
                .signal1(ok).signal2(ok)
                .signalMean(ok)
                .cvPct(ValidationConstants.calculateCvPercent(ok, ok))
                .isOutlier(false)
                .build();

        List<Long> outliers = service.detectOutliers(List.of(bad, good), protocol);

        assertThat(outliers).containsExactly(10L);
        assertThat(outliers).doesNotContain(11L);
    }

    /**
     * A pair with null cvPct is treated as having no precision info and must NOT be flagged
     * by the %CV criterion.
     */
    @Test
    @DisplayName("pair with null cvPct is not flagged by %CV criterion")
    void detectOutliers_nullCvPct_notFlagged() {
        MeasurementPair pair = MeasurementPair.builder()
                .id(3L)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(1.0)
                .signal1(5.0).signal2(5.0)
                .signalMean(5.0).cvPct(null)
                .isOutlier(false)
                .build();

        List<Long> outliers = service.detectOutliers(List.of(pair), protocol);

        assertThat(outliers).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Criterion 2: Grubbs test
    // -------------------------------------------------------------------------

    /**
     * Four CALIBRATION pairs at the same concentration — one with a clearly outlying
     * signalMean. The Grubbs test should flag the outlier.
     *
     * <p>Note: for n=3 the maximum possible G statistic is (n-1)/√n ≈ 1.1547, which is
     * below the α=0.05 critical value of 1.155, making the test ineffective at that group
     * size. At n=4 the test becomes meaningful.</p>
     *
     * <p>Dataset: signals 10, 10, 10, 100 at nominal=5.
     * mean=32.5, SD=45, G(100)=67.5/45=1.50 &gt; G_crit(n=4)=1.481.</p>
     */
    @Test
    @DisplayName("Grubbs test flags the outlying pair in a group of 4")
    void detectOutliers_grubbsTest_flagsOutlier() {
        MeasurementPair p1 = calibPair(20L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(21L, 5.0, 10.0);
        MeasurementPair p3 = calibPair(23L, 5.0, 10.0);
        MeasurementPair p4 = calibPair(22L, 5.0, 100.0); // outlier

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3, p4), protocol);

        assertThat(outliers).contains(22L);
    }

    /**
     * Three CALIBRATION pairs at the same concentration with similar signalMean values.
     * The Grubbs statistic stays below the critical value — no outlier should be flagged.
     *
     * <p>Dataset: signals 10, 11, 12 — tightly clustered; G well below 1.155.</p>
     */
    @Test
    @DisplayName("Grubbs test does not flag normal variation in a group of 3")
    void detectOutliers_grubbsTest_noOutlierInTightGroup() {
        MeasurementPair p1 = calibPair(30L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(31L, 5.0, 11.0);
        MeasurementPair p3 = calibPair(32L, 5.0, 12.0);

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3), protocol);

        assertThat(outliers).isEmpty();
    }

    /**
     * Only 2 pairs share the same concentration — Grubbs test must be skipped
     * (group size &lt; {@link OutlierDetectionService#GRUBBS_MIN_GROUP_SIZE}).
     * Both pairs have low %CV, so the result must be empty.
     */
    @Test
    @DisplayName("Grubbs test is skipped for groups smaller than GRUBBS_MIN_GROUP_SIZE")
    void detectOutliers_grubbsSkippedForSmallGroup() {
        MeasurementPair p1 = calibPair(40L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(41L, 5.0, 100.0); // extreme but group size = 2

        List<Long> outliers = service.detectOutliers(List.of(p1, p2), protocol);

        // %CV of both is 0 (identical signals) → no %CV flag; Grubbs skipped → no outlier
        assertThat(outliers).isEmpty();
    }

    /**
     * A pair already flagged by the %CV criterion is excluded from Grubbs grouping,
     * so it does not affect which pair the Grubbs test would select.
     */
    @Test
    @DisplayName("pair pre-flagged by %CV is excluded from Grubbs grouping")
    void detectOutliers_preFlaggedPairExcludedFromGrubbs() {
        // p1 has high CV → flagged by criterion 1
        double s1 = 5.0, s2 = 20.0;
        MeasurementPair p1 = MeasurementPair.builder()
                .id(50L)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(5.0)
                .signal1(s1).signal2(s2)
                .signalMean(ValidationConstants.calculateSignalMean(s1, s2))
                .cvPct(ValidationConstants.calculateCvPercent(s1, s2))
                .isOutlier(false)
                .build();

        // p2 and p3 are clean — same concentration as p1 but won't form a Grubbs group ≥3
        MeasurementPair p2 = calibPair(51L, 5.0, 10.0);
        MeasurementPair p3 = calibPair(52L, 5.0, 11.0);

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3), protocol);

        // p1 flagged by %CV; p2 and p3 remaining form a group of size 2 → Grubbs skipped
        assertThat(outliers).containsExactly(50L);
        assertThat(outliers).doesNotContain(51L, 52L);
    }

    /**
     * Empty input list must return an empty result without throwing.
     */
    @Test
    @DisplayName("empty pair list returns empty outlier list")
    void detectOutliers_emptyList_returnsEmpty() {
        List<Long> outliers = service.detectOutliers(List.of(), protocol);

        assertThat(outliers).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Grubbs SD near-zero guard (floating-point degenerate case)
    // -------------------------------------------------------------------------

    /**
     * Four CALIBRATION pairs whose {@code signalMean} values differ by at most {@code 1e-16}
     * (sub-epsilon floating-point noise). The computed sample SD will be on the order of
     * {@code 1e-16}, which is well below the {@code 1e-12} threshold. No pair must be flagged.
     *
     * <p>Without the near-zero guard the G statistic would be {@code Infinity}, which is
     * always &gt; any critical value and would incorrectly flag a pair as an outlier.</p>
     */
    @Test
    @DisplayName("Grubbs test skipped when all signalMeans differ by 1e-16 (near-zero SD)")
    void detectOutliers_grubbsNearZeroSd_noOutlierFlagged() {
        double base = 10.0;
        MeasurementPair p1 = calibPair(60L, 5.0, base);
        MeasurementPair p2 = calibPair(61L, 5.0, base + 1e-16);
        MeasurementPair p3 = calibPair(62L, 5.0, base + 2e-16);
        MeasurementPair p4 = calibPair(63L, 5.0, base + 3e-16);

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3, p4), protocol);

        assertThat(outliers).isEmpty();
    }

    /**
     * Four CALIBRATION pairs where one {@code signalMean} is clearly separated from the
     * other three. The SD is well above {@code 1e-12} so the guard does not fire, and the
     * Grubbs test correctly identifies the outlying pair.
     *
     * <p>Dataset: signals 10, 10, 10, 100 at nominal=5.
     * mean=32.5, SD≈45, G(100)≈1.50 &gt; G_crit(n=4)=1.481.</p>
     */
    @Test
    @DisplayName("Grubbs test correctly flags outlier when SD is well above near-zero threshold")
    void detectOutliers_grubbsClearOutlier_isFlagged() {
        MeasurementPair p1 = calibPair(70L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(71L, 5.0, 10.0);
        MeasurementPair p3 = calibPair(72L, 5.0, 10.0);
        MeasurementPair p4 = calibPair(73L, 5.0, 100.0); // outlier

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3, p4), protocol);

        assertThat(outliers).contains(73L);
        assertThat(outliers).doesNotContain(70L, 71L, 72L);
    }

    // -------------------------------------------------------------------------
    // Iterative Grubbs test
    // -------------------------------------------------------------------------

    /**
     * Five CALIBRATION pairs at the same concentration, one extreme outlier.
     * The iterative Grubbs should flag it in the first pass and stop — the
     * remaining four identical pairs produce SD≈0 on the second pass.
     *
     * <p>Dataset: [10, 10, 10, 10, 10000] at nominal=5.
     * Pass 1 — mean≈2008, SD≈4467.7, G(10000)≈1.789 &gt; G_crit(5)=1.715 → flag 10000.
     * Pass 2 — remaining [10, 10, 10, 10]: SD=0 → near-zero guard, no outlier → loop ends.</p>
     */
    @Test
    @DisplayName("iterative Grubbs flags single outlier in first pass and terminates for 5-pair group")
    void grubbsIterative_fivePairsOneOutlier_flaggedInFirstPass() {
        MeasurementPair p1 = calibPair(100L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(101L, 5.0, 10.0);
        MeasurementPair p3 = calibPair(102L, 5.0, 10.0);
        MeasurementPair p4 = calibPair(103L, 5.0, 10.0);
        MeasurementPair p5 = calibPair(104L, 5.0, 10000.0); // outlier

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3, p4, p5), protocol);

        assertThat(outliers).containsExactly(104L);
    }

    /**
     * Five CALIBRATION pairs at the same concentration, two outliers.
     * The iterative Grubbs must flag the first outlier, then re-run on the
     * reduced group and flag the second one.
     *
     * <p>Dataset: [10, 10, 10, 100, 10000] at nominal=5.
     * Pass 1 — mean≈2026, SD≈4456, G(10000)≈1.789 &gt; G_crit(5)=1.715 → flag 10000.
     * Pass 2 — remaining [10, 10, 10, 100]: mean=32.5, SD=45, G(100)=1.50 &gt; G_crit(4)=1.481 → flag 100.
     * Pass 3 — remaining [10, 10, 10] (n=3): G ≤ 1.1547 &lt; G_crit(3)=1.155 → no outlier → loop ends.</p>
     */
    @Test
    @DisplayName("iterative Grubbs flags two outliers across two consecutive passes in a group of 5")
    void grubbsIterative_fivePairsTwoOutliers_bothFlaggedIteratively() {
        MeasurementPair p1 = calibPair(110L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(111L, 5.0, 10.0);
        MeasurementPair p3 = calibPair(112L, 5.0, 10.0);
        MeasurementPair p4 = calibPair(113L, 5.0, 100.0);   // second outlier
        MeasurementPair p5 = calibPair(114L, 5.0, 10000.0); // first outlier

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3, p4, p5), protocol);

        assertThat(outliers).contains(113L, 114L);
        assertThat(outliers).doesNotContain(110L, 111L, 112L);
    }

    /**
     * Four CALIBRATION pairs at the same concentration, one extreme outlier.
     * After flagging it the residual group has exactly 3 pairs. Grubbs at n=3
     * is a statistical no-op (max possible G≈1.1547 ≤ G_crit(3)=1.155), so
     * no further pair is flagged.
     *
     * <p>Dataset: [10, 10, 10, 100] at nominal=5.
     * Pass 1 — mean=32.5, SD=45, G(100)=1.50 &gt; G_crit(4)=1.481 → flag 100.
     * Pass 2 — remaining [10, 10, 10] (n=3): SD=0 → near-zero guard → loop ends.</p>
     */
    @Test
    @DisplayName("iterative Grubbs flags single outlier in group of 4 and does not flag any of the remaining 3")
    void grubbsIterative_fourPairsOneOutlier_onlyOutlierFlagged() {
        MeasurementPair p1 = calibPair(120L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(121L, 5.0, 10.0);
        MeasurementPair p3 = calibPair(122L, 5.0, 10.0);
        MeasurementPair p4 = calibPair(123L, 5.0, 100.0); // outlier

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3, p4), protocol);

        assertThat(outliers).contains(123L);
        assertThat(outliers).doesNotContain(120L, 121L, 122L);
    }

    /**
     * Three CALIBRATION pairs at the same concentration — one with a value
     * orders of magnitude larger than the others. Due to the known statistical
     * limitation of the Grubbs test at n=3 (maximum possible G≈1.1547 ≤ 1.155
     * critical), no outlier should ever be flagged regardless of the spread.
     */
    @Test
    @DisplayName("iterative Grubbs produces no outlier for a group of exactly 3 (n=3 statistical limitation)")
    void grubbsIterative_groupOfThree_noOutlierDueToStatisticalLimitation() {
        MeasurementPair p1 = calibPair(130L, 5.0, 10.0);
        MeasurementPair p2 = calibPair(131L, 5.0, 10.0);
        MeasurementPair p3 = calibPair(132L, 5.0, 1000.0); // extreme but n=3

        List<Long> outliers = service.detectOutliers(List.of(p1, p2, p3), protocol);

        assertThat(outliers).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Creates a CALIBRATION pair with identical signals (cvPct=0) and the given signalMean.
     *
     * @param id      pair ID
     * @param nominal nominal concentration
     * @param signal  identical value for signal1, signal2, and signalMean
     * @return a clean CALIBRATION pair
     */
    private MeasurementPair calibPair(long id, double nominal, double signal) {
        return MeasurementPair.builder()
                .id(id)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(nominal)
                .signal1(signal).signal2(signal)
                .signalMean(signal)
                .cvPct(0.0)
                .isOutlier(false)
                .build();
    }
}
