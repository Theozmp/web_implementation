package gr.uoa.di.ted.events;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Pin to dev only (not the new dev,test-data default) so this context-load smoke
// test doesn't trigger the demo-data seeder.
@SpringBootTest
@ActiveProfiles("dev")
class EventsApplicationTests {

	@Test
	void contextLoads() {
	}

}
