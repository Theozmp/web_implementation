package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.entity.EventView;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.EventViewRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records event views for the recommender. record() is @Async so it runs on
 * Spring's shared executor and the HTTP response returns without waiting for the
 * INSERT. A failed INSERT (e.g. the event was deleted between the check and the
 * write) is swallowed; a missing view record doesn't affect correctness.
 */
@Service
@RequiredArgsConstructor
public class EventViewService {

    private final EventViewRepository viewRepository;
    private final EventRepository     eventRepository;
    private final UserRepository      userRepository;

    @Async
    @Transactional
    public void record(Long eventId, String username, String ipAddress) {
        eventRepository.findById(eventId).ifPresent(event -> {
            EventView view = new EventView();
            view.setEvent(event);
            view.setIpAddress(ipAddress);
            if (username != null) {
                userRepository.findByUsername(username).ifPresent(view::setViewer);
            }
            viewRepository.save(view);
        });
    }
}
