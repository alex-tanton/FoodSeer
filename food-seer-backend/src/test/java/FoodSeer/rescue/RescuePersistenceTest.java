package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import FoodSeer.dto.CalorieBudgetDto;
import FoodSeer.entity.Allergen;
import FoodSeer.entity.Notification;
import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.User;
import FoodSeer.repositories.AllergenRepository;
import FoodSeer.repositories.NotificationRepository;
import FoodSeer.repositories.SurplusOfferRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.CalorieBudgetService;

/** Real tables and transactions on H2: duplicates, daily caps, and the notification rows. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({RescueService.class, RescueCandidateFactory.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RescuePersistenceTest {
    @Autowired private RescueService service;
    @Autowired private RescueAlertRepository alerts;
    @Autowired private NotificationRepository notifications;
    @Autowired private SurplusOfferRepository offers;
    @Autowired private UserRepository users;
    @Autowired private AllergenRepository allergens;
    @MockBean private CalorieBudgetService budgets;

    @BeforeEach
    void setup() {
        alerts.deleteAll();
        notifications.deleteAll();
        offers.deleteAll();
        if (allergens.count() == 0) {
            for (String name : RescueFixtures.KNOWN) {
                allergens.save(new Allergen(name));
            }
        }
        user("rescue-ann", "ROLE_CUSTOMER", null);
        user("rescue-eggy", "ROLE_CUSTOMER", "EGGS");
        user("rescue-staff", "ROLE_STAFF", null);
        when(budgets.getBudgetFor(any()))
                .thenReturn(new CalorieBudgetDto(null, false, null, false, 0, List.of(), null));
    }

    private void user(final String name, final String role, final String restrictions) {
        if (!users.existsByUsername(name)) {
            users.save(User.builder().username(name).email(name + "@test.example").password("unused").role(role)
                    .dietaryRestrictions(restrictions).build());
        }
    }

    private SurplusOffer offer(final String name, final List<String> allergenTags) {
        Instant now = Instant.now();
        SurplusOffer offer = new SurplusOffer();
        offer.setFoodId(1L);
        offer.setFoodName(name);
        offer.setIngredients("stuff");
        offer.setAllergens(new ArrayList<>(allergenTags));
        offer.setCalories(400);
        offer.setOriginalPrice(new BigDecimal("10.00"));
        offer.setDiscountedPrice(new BigDecimal("5.00"));
        offer.setRemainingPortions(5);
        offer.setPickupLocation("Desk");
        offer.setPickupStart(now);
        offer.setPickupEnd(now.plusSeconds(7200));
        offer.setExpiresAt(now.plusSeconds(3600));
        offer.setCreatedAt(now);
        return offers.save(offer);
    }

    private List<String> alertedCustomers() {
        return alerts.findAll().stream().map(RescueAlert::getRecipientUsername)
                .filter(name -> name.startsWith("rescue-")).sorted().toList();
    }

    @Test
    void sendingTwiceNeverAlertsAnyoneTwice() {
        SurplusOffer offer = offer("PASTA", List.of("WHEAT"));

        RescueDtos.NotifyResult first = service.notify(offer.getId());
        RescueDtos.NotifyResult second = service.notify(offer.getId());

        assertTrue(first.alerted().containsAll(List.of("rescue-ann", "rescue-eggy")));
        assertTrue(second.alerted().isEmpty());
        assertEquals(List.of("rescue-ann", "rescue-eggy"), alertedCustomers());
    }

    @Test
    void allergicCustomersAndStaffAreNeverAlerted() {
        SurplusOffer offer = offer("OMELETTE", List.of("EGGS"));
        service.notify(offer.getId());
        assertEquals(List.of("rescue-ann"), alertedCustomers());
    }

    @Test
    void rescueNotificationsAreUnreadNoticesWithoutAnIncident() {
        SurplusOffer offer = offer("PASTA", List.of());
        long unreadBefore = notifications.countByRecipientUsernameAndAcknowledgedFalse("rescue-ann");

        service.notify(offer.getId());

        List<Notification> annNotices = notifications.findByRecipientUsernameOrderByCreatedAtDesc("rescue-ann");
        assertEquals(1, annNotices.size());
        assertEquals(Notification.KIND_RESCUE, annNotices.get(0).getKind());
        assertNull(annNotices.get(0).getIncidentId());
        assertTrue(annNotices.get(0).getMessage().startsWith("Rescue deal: PASTA"));
        assertEquals(unreadBefore + 1, notifications.countByRecipientUsernameAndAcknowledgedFalse("rescue-ann"));
        RescueAlert alert = alerts.findByRecipientUsernameOrderByCreatedAtDesc("rescue-ann").get(0);
        assertEquals(annNotices.get(0).getId(), alert.getNotificationId());
    }

    @Test
    void dailyCapHoldsAcrossOffers() {
        for (String name : List.of("SOUP", "SALAD", "CAKE")) {
            service.notify(offer(name, List.of()).getId());
        }
        assertEquals(RescuePolicy.DEFAULT.dailyCap(),
                alerts.findByRecipientUsernameOrderByCreatedAtDesc("rescue-ann").size());
        assertEquals(RescuePolicy.DEFAULT.dailyCap(), service.deals("rescue-ann").size());
    }

    @Test
    void databaseRejectsDuplicateAlertsForOneOffer() {
        SurplusOffer offer = offer("PASTA", List.of());
        RankedCandidate match = new RankedCandidate("rescue-ann", new ScoreParts(0, 0, 0.5, 0, 0.05), 0, 50, 60,
                "50% off · 1 h left");
        alerts.save(RescueAlert.of(offer.getId(), match, Instant.now()));
        assertThrows(DataIntegrityViolationException.class,
                () -> alerts.save(RescueAlert.of(offer.getId(), match, Instant.now())));
    }

    @Test
    void dealsListTheCustomersOpenAlerts() {
        SurplusOffer offer = offer("PASTA", List.of());
        service.notify(offer.getId());
        List<RescueDtos.Deal> deals = service.deals("rescue-ann");
        assertEquals(1, deals.size());
        assertEquals("PASTA", deals.get(0).offer().foodName());
        assertFalse(deals.get(0).reserved());
    }
}
