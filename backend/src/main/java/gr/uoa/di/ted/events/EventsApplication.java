package gr.uoa.di.ted.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync lets @Async methods (e.g. EventViewService.record) run off the HTTP thread.
// @EnableScheduling activates @Scheduled (e.g. the nightly BMF model retrain).
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EventsApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventsApplication.class, args);
	}

}
