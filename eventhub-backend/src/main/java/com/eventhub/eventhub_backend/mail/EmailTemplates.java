package com.eventhub.eventhub_backend.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class EmailTemplates {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, h:mm a", Locale.ENGLISH);

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public EmailContent bookingConfirmed(BookingEmailData d, boolean fromWaitingList) {
        String intro = fromWaitingList
                ? "Good news: tickets became available and your waiting list request is now a booking."
                : "Your booking is confirmed.";
        return build("Your tickets for " + d.eventTitle(), d.recipientName(),
                List.of(intro, "Show the QR code from the My tickets page at the entrance."),
                bookingDetails(d), d.ticketCodes(), "View my tickets", frontendUrl + "/my-tickets");
    }

    public EmailContent ticketsHeld(BookingEmailData d) {
        String minutes = d.minutesToPay() + (d.minutesToPay() == 1 ? " minute" : " minutes");
        return build("Tickets available for " + d.eventTitle() + ": pay within " + minutes, d.recipientName(),
                List.of("A place opened up for you on the waiting list. We are holding your tickets for "
                                + minutes + ".",
                        "If you do not pay in time, they go to the next person in line."),
                bookingDetails(d), List.of(), "Pay now", frontendUrl + "/checkout/" + d.bookingId());
    }

    public EmailContent bookingCancelled(BookingEmailData d, boolean refunded) {
        return build("Booking cancelled: " + d.eventTitle(), d.recipientName(),
                List.of("Your booking was cancelled and the tickets are back on sale." + refundLine(d, refunded)),
                bookingDetails(d), List.of(), "Browse events", frontendUrl + "/");
    }

    public EmailContent bookingExpired(BookingEmailData d) {
        return build("Your reservation for " + d.eventTitle() + " expired", d.recipientName(),
                List.of("We did not receive your payment in time, so we released your tickets.",
                        "If tickets are still available you can book again."),
                bookingDetails(d), List.of(), "Back to the event", frontendUrl + "/events/" + d.eventId());
    }

    public EmailContent eventCancelled(BookingEmailData d, boolean refunded) {
        return build("Event cancelled: " + d.eventTitle(), d.recipientName(),
                List.of("The organizer has cancelled this event, so your booking was cancelled."
                        + refundLine(d, refunded)),
                bookingDetails(d), List.of(), "Browse other events", frontendUrl + "/");
    }

    public EmailContent eventCancelledForWaitingList(WaitlistEmailData w) {
        return build("Event cancelled: " + w.eventTitle(), w.recipientName(),
                List.of("The organizer has cancelled this event, so your waiting list request was removed."),
                waitlistDetails(w), List.of(), "Browse other events", frontendUrl + "/");
    }

    public EmailContent eventReminder(BookingEmailData d) {
        return build("Reminder: " + d.eventTitle() + " is coming up", d.recipientName(),
                List.of("Your event starts soon. Here is everything you need."),
                bookingDetails(d), d.ticketCodes(), "View my tickets", frontendUrl + "/my-tickets");
    }

    public EmailContent waitlistJoined(WaitlistEmailData w) {
        return build("You are on the waiting list for " + w.eventTitle(), w.recipientName(),
                List.of("You are number " + w.position() + " in line for " + w.quantity() + " x "
                        + oneLine(w.ticketTypeName()) + ". We will email you as soon as tickets are free."),
                waitlistDetails(w), List.of(), "View my tickets", frontendUrl + "/my-tickets");
    }

    // ---------- building blocks ----------

    private Map<String, String> bookingDetails(BookingEmailData d) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Event", oneLine(d.eventTitle()));
        details.put("When", d.eventStart().format(WHEN));
        details.put("Where", where(d.venue(), d.city()));
        details.put("Tickets", d.quantity() + " x " + oneLine(d.ticketTypeName()));
        details.put("Total", money(d.total()));
        details.put("Booking number", "#" + d.bookingId());
        return details;
    }

    private Map<String, String> waitlistDetails(WaitlistEmailData w) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Event", oneLine(w.eventTitle()));
        details.put("When", w.eventStart().format(WHEN));
        details.put("Tickets", w.quantity() + " x " + oneLine(w.ticketTypeName()));
        return details;
    }

    private String refundLine(BookingEmailData d, boolean refunded) {
        return refunded ? " A refund of " + money(d.total()) + " will go back to your card." : "";
    }

    private String where(String venue, String city) {
        String place = Stream.of(venue, city)
                .filter(part -> part != null && !part.isBlank())
                .map(this::oneLine)
                .collect(Collectors.joining(", "));
        return place.isEmpty() ? "To be announced" : place;
    }

    private String money(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return "Free";
        }
        return "₹" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private EmailContent build(String subject, String name, List<String> paragraphs,
                               Map<String, String> details, List<String> codes,
                               String buttonLabel, String buttonUrl) {
        String greeting = "Hi " + oneLine(name) + ",";

        StringBuilder text = new StringBuilder(greeting).append("\n\n");
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#111827\">");
        html.append("<h2 style=\"color:#4f46e5;margin:0 0 16px\">EventHub</h2>");
        html.append("<p>").append(esc(greeting)).append("</p>");

        for (String paragraph : paragraphs) {
            text.append(paragraph).append("\n\n");
            html.append("<p>").append(esc(paragraph)).append("</p>");
        }

        if (!details.isEmpty()) {
            html.append("<table style=\"border-collapse:collapse;margin:16px 0;width:100%\">");
            for (Map.Entry<String, String> row : details.entrySet()) {
                text.append(row.getKey()).append(": ").append(row.getValue()).append("\n");
                html.append("<tr><td style=\"padding:6px 12px 6px 0;color:#6b7280\">")
                        .append(esc(row.getKey()))
                        .append("</td><td style=\"padding:6px 0;font-weight:600\">")
                        .append(esc(row.getValue()))
                        .append("</td></tr>");
            }
            html.append("</table>");
            text.append("\n");
        }

        if (!codes.isEmpty()) {
            text.append("Your ticket codes:\n");
            html.append("<p>Your ticket codes:</p><ul style=\"font-family:monospace;font-size:13px\">");
            for (String code : codes) {
                text.append("  ").append(code).append("\n");
                html.append("<li>").append(esc(code)).append("</li>");
            }
            html.append("</ul>");
            text.append("\n");
        }

        text.append(buttonLabel).append(": ").append(buttonUrl).append("\n\nEventHub");
        html.append("<p><a href=\"").append(esc(buttonUrl))
                .append("\" style=\"background:#4f46e5;color:#ffffff;padding:10px 18px;border-radius:8px;")
                .append("text-decoration:none;display:inline-block\">")
                .append(esc(buttonLabel)).append("</a></p>")
                .append("<p style=\"color:#6b7280;font-size:13px\">This is an automated message from EventHub.</p>")
                .append("</div>");

        return new EmailContent(oneLine(subject), text.toString(), html.toString());
    }

    // User text goes into HTML: escape it, so a title like <script>... shows as text
    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    // Line breaks in a subject could be used to inject extra mail headers
    private String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}