package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.EventPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventPhotoRepository extends JpaRepository<EventPhoto, Long> {

    List<EventPhoto> findByEventId(Long eventId);

    long countByEventId(Long eventId);
}
