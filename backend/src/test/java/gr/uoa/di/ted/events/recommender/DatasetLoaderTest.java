package gr.uoa.di.ted.events.recommender;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DatasetLoader}. Pure Java, no Spring context or database.
 * They run against a tiny fixture (src/test/resources/dataset/test_event_interest.csv),
 * never the real dataset, so they stay fast and deterministic.
 *
 * The fixture has a header, 5 valid rows, 2 malformed rows (empty event id and
 * non-numeric event id) and a comment line. Valid interested=1 rows map to 5.0 and
 * everything else to 1.0; malformed rows and the header/comment are skipped.
 */
class DatasetLoaderTest {

    private static final String FIXTURE = "classpath:dataset/test_event_interest.csv";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    private DatasetLoader loaderFor(String path, boolean enabled) {
        BiasedMfConfig config = new BiasedMfConfig();
        config.getDataset().setPath(path);
        config.getDataset().setEnabled(enabled);
        return new DatasetLoader(config, resourceLoader);
    }

    // happy path

    @Test
    void load_parsesOnlyValidRows_skippingHeaderCommentAndMalformed() {
        List<TrainingSample> samples = loaderFor(FIXTURE, true).load();

        // 5 valid rows; header, comment and the 2 malformed rows are dropped.
        assertThat(samples).hasSize(5);
    }

    @Test
    void load_mapsInterestedToFiveAndEverythingElseToOne() {
        List<TrainingSample> samples = loaderFor(FIXTURE, true).load();

        // interested=1 rows: (101,5001), (102,5001), (104,5002) map to 5.0
        assertThat(samples).contains(
                new TrainingSample(101, 5001, 5.0),
                new TrainingSample(102, 5001, 5.0),
                new TrainingSample(104, 5002, 5.0));

        // interested=0 (incl. the not_interested row) maps to 1.0
        assertThat(samples).contains(
                new TrainingSample(101, 5002, 1.0),
                new TrainingSample(103, 5003, 1.0));

        long fives = samples.stream().filter(s -> s.rating() == 5.0).count();
        long ones  = samples.stream().filter(s -> s.rating() == 1.0).count();
        assertThat(fives).isEqualTo(3);
        assertThat(ones).isEqualTo(2);
    }

    @Test
    void load_coversExpectedDistinctUsersAndItems() {
        List<TrainingSample> samples = loaderFor(FIXTURE, true).load();

        Set<Long> users = samples.stream().map(TrainingSample::userId).collect(Collectors.toSet());
        Set<Long> items = samples.stream().map(TrainingSample::eventId).collect(Collectors.toSet());

        assertThat(users).containsExactlyInAnyOrder(101L, 102L, 103L, 104L);
        assertThat(items).containsExactlyInAnyOrder(5001L, 5002L, 5003L);
    }

    @Test
    void load_neverProducesEmptyOrZeroEventIds() {
        // guards against empty-event rows leaking through as eventId=0.
        List<TrainingSample> samples = loaderFor(FIXTURE, true).load();
        assertThat(samples).allSatisfy(s -> assertThat(s.eventId()).isPositive());
    }

    // disabled / missing

    @Test
    void load_returnsEmpty_whenDisabled() {
        assertThat(loaderFor(FIXTURE, false).load()).isEmpty();
    }

    @Test
    void load_returnsEmpty_whenResourceMissing() {
        assertThat(loaderFor("classpath:dataset/does_not_exist.csv", true).load()).isEmpty();
    }

    @Test
    void load_returnsEmpty_whenPathBlank() {
        assertThat(loaderFor("", true).load()).isEmpty();
    }
}
