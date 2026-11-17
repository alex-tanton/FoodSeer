package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import FoodSeer.dto.NotificationDto;
import FoodSeer.entity.Notification;
import FoodSeer.repositories.IncidentRepository;
import FoodSeer.repositories.NotificationRepository;
import FoodSeer.service.impl.NotificationServiceImpl;

/** The small change to the inherited notification channel that lets it carry rescue alerts. */
class NotificationKindTest {

    @Test
    void rowsSavedBeforeKindsExistReadAsSafety() {
        assertEquals(Notification.KIND_SAFETY, new Notification().getKind());
    }

    @Test
    void oldDtoConstructorStillBuildsASafetyNotice() {
        NotificationDto dto = new NotificationDto(1L, 2L, 3L, "msg", LocalDateTime.now(), false, null, null);
        assertEquals("SAFETY", dto.kind());
    }

    @Test
    void serviceReportsRescueKindWithoutLookingUpAnIncident() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        IncidentRepository incidents = mock(IncidentRepository.class);
        Notification rescue = new Notification();
        rescue.setId(5L);
        rescue.setKind(Notification.KIND_RESCUE);
        rescue.setRecipientUsername("ann");
        rescue.setMessage("Rescue deal");
        rescue.setCreatedAt(LocalDateTime.now());
        when(notifications.findByRecipientUsernameOrderByCreatedAtDesc("ann")).thenReturn(List.of(rescue));

        List<NotificationDto> result = new NotificationServiceImpl(notifications, incidents).getNotifications("ann");

        assertEquals("RESCUE", result.get(0).kind());
        assertNull(result.get(0).incidentId());
        assertNull(result.get(0).remedy());
        verifyNoInteractions(incidents);
    }
}
