package com.carechain.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishBedsRefresh(String source) {
        publishAfterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/realtime/beds",
                basePayload("beds-refresh", source, null, "info")));
    }

    public void publishAdminRefresh(String source) {
        publishAfterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/realtime/admin",
                basePayload("admin-refresh", source, null, "info")));
    }

    public void publishDoctorScheduleRefresh(String doctorEmail, String source, String message) {
        publishUserEvent(doctorEmail, "schedule-refresh", source, message, "info");
    }

    public void publishPatientAppointmentsRefresh(String patientEmail, String source, String message, String level) {
        publishUserEvent(patientEmail, "appointments-refresh", source, message, level);
    }

    public void publishPatientQueueRefresh(String patientEmail, String source, String message, String level) {
        publishUserEvent(patientEmail, "queue-refresh", source, message, level);
    }

    public void publishPatientTriageRefresh(String patientEmail, String source, String message, String level) {
        publishUserEvent(patientEmail, "triage-refresh", source, message, level);
    }

    public void publishAppointmentChange(String patientEmail,
                                         String doctorEmail,
                                         String source,
                                         String patientMessage,
                                         String patientLevel) {
        publishAfterCommit(() -> {
            messagingTemplate.convertAndSend(
                    "/topic/realtime/admin",
                    basePayload("admin-refresh", source, null, "info"));
            messagingTemplate.convertAndSendToUser(
                    doctorEmail,
                    "/queue/realtime",
                    basePayload("schedule-refresh", source, "Schedule updated.", "info"));
            messagingTemplate.convertAndSendToUser(
                    patientEmail,
                    "/queue/realtime",
                    basePayload("appointments-refresh", source, patientMessage, patientLevel));
        });
    }

    public void publishQueueChange(String patientEmail, String source, String patientMessage, String patientLevel) {
        publishAfterCommit(() -> {
            messagingTemplate.convertAndSend(
                    "/topic/realtime/admin",
                    basePayload("admin-refresh", source, null, "info"));
            messagingTemplate.convertAndSendToUser(
                    patientEmail,
                    "/queue/realtime",
                    basePayload("queue-refresh", source, patientMessage, patientLevel));
        });
    }

    private void publishUserEvent(String email, String kind, String source, String message, String level) {
        publishAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                email,
                "/queue/realtime",
                basePayload(kind, source, message, level)));
    }

    private Map<String, Object> basePayload(String kind, String source, String message, String level) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        payload.put("source", source);
        payload.put("level", level);
        payload.put("date", LocalDate.now().toString());
        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        return payload;
    }

    private void publishAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
