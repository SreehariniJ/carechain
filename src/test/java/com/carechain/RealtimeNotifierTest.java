package com.carechain;

import com.carechain.realtime.RealtimeNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RealtimeNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void publishBedsRefresh_shouldBroadcastToPublicTopic() {
        RealtimeNotifier notifier = new RealtimeNotifier(messagingTemplate);

        notifier.publishBedsRefresh("patient-admitted");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/realtime/beds"),
                argThat(payloadMatches("kind", "beds-refresh")));
    }

    @Test
    void publishAppointmentChange_shouldNotifyAdminDoctorAndPatient() {
        RealtimeNotifier notifier = new RealtimeNotifier(messagingTemplate);

        notifier.publishAppointmentChange(
                "patient@test.com",
                "doctor@test.com",
                "appointment-completed",
                "Appointment completed.",
                "success");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/realtime/admin"),
                argThat(payloadMatches("kind", "admin-refresh")));
        verify(messagingTemplate).convertAndSendToUser(
                eq("doctor@test.com"),
                eq("/queue/realtime"),
                argThat(payloadMatches("kind", "schedule-refresh")));
        verify(messagingTemplate).convertAndSendToUser(
                eq("patient@test.com"),
                eq("/queue/realtime"),
                argThat(payloadMatches("kind", "appointments-refresh")));
    }

    private ArgumentMatcher<Object> payloadMatches(String key, String expectedValue) {
        return argument -> argument instanceof Map<?, ?> payload
                && expectedValue.equals(payload.get(key));
    }
}
