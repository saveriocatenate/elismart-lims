package it.elismart_lims.service.validation;

import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects outlier {@link MeasurementPair}s using two complementary criteria:
 *
 * <ol>
 *   <li><b>%CV threshold</b> — any pair whose {@code cvPct} exceeds the protocol's
 *       {@code maxCvAllowed} is flagged immediately. This is the primary criterion for
 *       n=2 duplicate measurements and is always applied.</li>
 *   <li><b>Grubbs test (bonus)</b> — between-pair outlier detection on groups that share
 *       the same {@link MeasurementPair#getPairType() pairType} and
 *       {@link MeasurementPair#getConcentrationNominal() concentrationNominal}. Requires
 *       at least {@value #GRUBBS_MIN_GROUP_SIZE} pairs per group.
 *       <p><em>Limitation:</em> the Grubbs test is not statistically valid for n=2 replicates
 *       (ISO 5725-2 requires n≥3); the %CV criterion handles that common case. The Grubbs
 *       test here operates <em>across</em> pairs at the same concentration level (between-pair)
 *       and is only applied when there are ≥3 such pairs.</p>
 *   </li>
 * </ol>
 */
@Service
public class OutlierDetectionService {

    private static final Logger log = LoggerFactory.getLogger(OutlierDetectionService.class);

    /**
     * Minimum number of pairs in a concentration group before the Grubbs test
     * is applied. ISO 5725-2 requires at least 3 observations.
     */
    static final int GRUBBS_MIN_GROUP_SIZE = 3;

    /**
     * Grubbs test critical values at significance level α=0.05 (two-sided), indexed by group
     * size n (3–10). Sourced from Grubbs (1969) and ISO 5725-2 Annex A.
     * For n&gt;10, the n=10 value is used as a conservative approximation.
     *
     * <p><b>Known limitation for n=3:</b> the maximum possible G statistic for three
     * observations is {@code (n-1)/√n = 2/√3 ≈ 1.1547}, which is effectively equal to
     * (and never exceeds) the α=0.05 critical value of 1.155. In practice the Grubbs test
     * cannot flag an outlier when the group contains exactly 3 pairs; it becomes effective
     * from n=4 onward.</p>
     */
    private static final Map<Integer, Double> GRUBBS_CRITICAL_VALUES = Map.of(
            3,  1.155,
            4,  1.481,
            5,  1.715,
            6,  1.887,
            7,  2.020,
            8,  2.126,
            9,  2.215,
            10, 2.290
    );

    /**
     * Identifies outlier {@link MeasurementPair}s from the supplied list.
     *
     * <p>Two criteria are applied in sequence:</p>
     * <ol>
     *   <li><b>%CV threshold</b>: pairs whose {@code cvPct} exceeds
     *       {@link Protocol#getMaxCvAllowed()} are flagged first.</li>
     *   <li><b>Grubbs test</b>: remaining (non-flagged) pairs are grouped by
     *       ({@code pairType}, {@code concentrationNominal}). Any group with
     *       ≥ {@value #GRUBBS_MIN_GROUP_SIZE} members is tested; the pair with the
     *       highest absolute deviation is flagged if its Grubbs statistic exceeds the
     *       critical value at α=0.05.</li>
     * </ol>
     *
     * @param pairs    the measurement pairs to evaluate; must not be {@code null}
     * @param protocol the protocol supplying the {@code maxCvAllowed} threshold
     * @return list of IDs of pairs that should be flagged as outliers; never {@code null}
     */
    public List<Long> detectOutliers(List<MeasurementPair> pairs, Protocol protocol) {
        Set<Long> outlierIds = new LinkedHashSet<>();

        // ── Criterion 1: %CV threshold ────────────────────────────────────────
        for (MeasurementPair pair : pairs) {
            if (pair.getCvPct() != null && pair.getCvPct() > protocol.getMaxCvAllowed()) {
                log.debug("Pair id={} flagged by %CV threshold: cvPct={} > maxCvAllowed={}",
                        pair.getId(), pair.getCvPct(), protocol.getMaxCvAllowed());
                outlierIds.add(pair.getId());
            }
        }

        // ── Criterion 2: Grubbs test (between-pair) ───────────────────────────
        Map<String, List<MeasurementPair>> groups = pairs.stream()
                .filter(p -> !outlierIds.contains(p.getId()))
                .collect(Collectors.groupingBy(p ->
                        p.getPairType().name() + ":"
                        + (p.getConcentrationNominal() != null
                                ? p.getConcentrationNominal().toString()
                                : "null")));

        for (Map.Entry<String, List<MeasurementPair>> entry : groups.entrySet()) {
            List<MeasurementPair> group = entry.getValue();
            if (group.size() < GRUBBS_MIN_GROUP_SIZE) {
                log.trace("Grubbs test skipped for group '{}': size={} < minimum={}",
                        entry.getKey(), group.size(), GRUBBS_MIN_GROUP_SIZE);
                continue;
            }
            Long flaggedId = applyGrubbsTest(group, entry.getKey());
            if (flaggedId != null) {
                outlierIds.add(flaggedId);
            }
        }

        return new ArrayList<>(outlierIds);
    }

    /**
     * Applies one iteration of the Grubbs test to detect a single outlier in the group.
     *
     * <p>The test statistic is {@code G = |xi − mean| / SD} where {@code xi} is the
     * candidate pair's {@code signalMean}. The pair with the maximum absolute deviation
     * is the candidate. If G exceeds the critical value for the group size at α=0.05,
     * the candidate's ID is returned.</p>
     *
     * <p>For group sizes larger than 10, the critical value for n=10 is used as a
     * conservative upper bound.</p>
     *
     * @param group    the non-flagged pairs in this concentration group (size ≥ 3 guaranteed)
     * @param groupKey human-readable key used in log messages
     * @return the ID of the outlier pair, or {@code null} if no outlier is detected
     */
    private Long applyGrubbsTest(List<MeasurementPair> group, String groupKey) {
        int n = group.size();
        double mean = group.stream()
                .mapToDouble(MeasurementPair::getSignalMean)
                .average()
                .orElse(0.0);

        double sd = computeSampleSd(group, mean);
        if (sd == 0.0) {
            log.trace("Grubbs test skipped for group '{}': SD=0 (all signals identical)", groupKey);
            return null;
        }

        MeasurementPair candidate = group.stream()
                .max(Comparator.comparingDouble(p -> Math.abs(p.getSignalMean() - mean)))
                .orElse(null);

        if (candidate == null) {
            return null;
        }

        double g = Math.abs(candidate.getSignalMean() - mean) / sd;
        double critical = GRUBBS_CRITICAL_VALUES.getOrDefault(n, GRUBBS_CRITICAL_VALUES.get(10));

        log.debug("Grubbs test group='{}': n={}, G={}, critical={}, candidate id={}",
                groupKey, n, g, critical, candidate.getId());

        if (g > critical) {
            log.debug("Pair id={} flagged by Grubbs test in group '{}'", candidate.getId(), groupKey);
            return candidate.getId();
        }
        return null;
    }

    /**
     * Computes the sample standard deviation of {@code signalMean} values in the group.
     *
     * @param group the pairs
     * @param mean  pre-computed arithmetic mean of {@code signalMean}
     * @return sample standard deviation; {@code 0.0} when n ≤ 1
     */
    private double computeSampleSd(List<MeasurementPair> group, double mean) {
        int n = group.size();
        if (n <= 1) {
            return 0.0;
        }
        double sumSq = group.stream()
                .mapToDouble(p -> Math.pow(p.getSignalMean() - mean, 2))
                .sum();
        return Math.sqrt(sumSq / (n - 1));
    }
}
