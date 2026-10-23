package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.EventDetailResponse;
import com.eventhub.eventhub_backend.dto.PageResponse;
import com.eventhub.eventhub_backend.dto.PublicEventResponse;
import com.eventhub.eventhub_backend.dto.TicketTypeResponse;
import com.eventhub.eventhub_backend.entity.Event;
import com.eventhub.eventhub_backend.enums.EventStatus;
import com.eventhub.eventhub_backend.repository.EventRepository;
import com.eventhub.eventhub_backend.repository.TicketTypeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicEventService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;

    @Transactional(readOnly = true)
    public PageResponse<PublicEventResponse> search(String q, String city, String category,
                                                    LocalDate from, LocalDate to,
                                                    int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must not be before 'from'");
        }
        if (q != null && q.length() > MAX_SEARCH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text is too long");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.asc("startTime"), Sort.Order.asc("id")));

        Page<Event> result = eventRepository.findAll(buildSpec(q, city, category, from, to), pageable);
        return PageResponse.from(result.map(PublicEventResponse::from));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getDetail(Long id) {
        Event event = eventRepository.findById(id)
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        List<TicketTypeResponse> ticketTypes = ticketTypeRepository.findByEventId(id)
                .stream().map(TicketTypeResponse::from).toList();

        return EventDetailResponse.from(event, ticketTypes);
    }

    private Specification<Event> buildSpec(String q, String city, String category,
                                           LocalDate from, LocalDate to) {
        LocalDateTime start = (from != null) ? from.atStartOfDay() : LocalDateTime.now();
        LocalDateTime endExclusive = (to != null) ? to.plusDays(1).atStartOfDay() : null;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));
            predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("startTime"), start));
            if (endExclusive != null) {
                predicates.add(cb.lessThan(root.<LocalDateTime>get("startTime"), endExclusive));
            }
            if (StringUtils.hasText(city)) {
                predicates.add(cb.equal(cb.lower(root.<String>get("city")), city.trim().toLowerCase()));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(cb.lower(root.<String>get("category")), category.trim().toLowerCase()));
            }
            if (StringUtils.hasText(q)) {
                String pattern = likePattern(q);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), pattern, '!'),
                        cb.like(cb.lower(root.<String>get("description")), pattern, '!'),
                        cb.like(cb.lower(root.<String>get("venue")), pattern, '!')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Escape LIKE wildcards so a search for "%" or "_" is treated as plain text
    private static String likePattern(String text) {
        String escaped = text.trim().toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}