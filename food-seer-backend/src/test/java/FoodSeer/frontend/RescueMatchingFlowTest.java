package FoodSeer.frontend;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import FoodSeer.dto.SurplusOfferDto;
import FoodSeer.dto.SurplusOfferRequest;
import FoodSeer.entity.Food;
import FoodSeer.entity.Order;
import FoodSeer.entity.User;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.NotificationRepository;
import FoodSeer.repositories.OrderRepository;
import FoodSeer.repositories.SurplusOfferRepository;
import FoodSeer.repositories.SurplusReservationRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.rescue.RescueAlert;
import FoodSeer.rescue.RescueAlertRepository;
import FoodSeer.security.JwtTokenProvider;
import FoodSeer.service.SurplusService;

/**
 * Rescue matching through a real browser, API and database. Start the React
 * frontend first (port 3000, or pass -Dfrontend.url=http://localhost:3001).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
class RescueMatchingFlowTest {
    private static final String FRONTEND = System.getProperty("frontend.url", "http://localhost:3000");

    @Autowired private FoodRepository foods;
    @Autowired private UserRepository users;
    @Autowired private OrderRepository orders;
    @Autowired private SurplusService surplus;
    @Autowired private SurplusReservationRepository reservations;
    @Autowired private RescueAlertRepository alerts;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private SurplusOfferRepository offers;
    @Autowired private NotificationRepository notifications;
    private ChromeDriver driver;
    private WebDriverWait wait;
    private final List<User> createdUsers = new ArrayList<>();
    private Food stew;
    private Order past;
    private Long offerId;

    /**
     * Browser tests share one Spring context and one database. Other browser
     * tests delete all users or foods in their setup, which fails if an order
     * from this test still references them, so remove everything created here.
     */
    @AfterEach
    void cleanUp() {
        if (driver != null) {
            driver.quit();
        }
        if (offerId != null) {
            reservations.findAll().stream().filter(r -> offerId.equals(r.getOffer().getId()))
                    .forEach(reservations::delete);
            for (RescueAlert alert : alerts.findByOfferId(offerId)) {
                if (alert.getNotificationId() != null) {
                    notifications.deleteById(alert.getNotificationId());
                }
                alerts.delete(alert);
            }
            offers.deleteById(offerId);
        }
        if (past != null) {
            orders.delete(past);
        }
        if (stew != null) {
            foods.delete(stew);
        }
        users.deleteAll(createdUsers);
    }

    @Test
    void staffAlertsMatchedCustomerWhoReservesInOneTap() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1280,1000");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        createdUsers.add(users.save(User.builder().username("rescue-browser-staff")
                .email("rescue-browser-staff@example.test").password("unused").role("ROLE_STAFF").build()));
        User fan = users.save(User.builder().username("rescue-browser-fan").email("rescue-browser-fan@example.test")
                .password("unused").role("ROLE_CUSTOMER").build());
        createdUsers.add(fan);
        createdUsers.add(users.save(User.builder().username("rescue-browser-allergic")
                .email("rescue-browser-allergic@example.test").password("unused").role("ROLE_CUSTOMER")
                .dietaryRestrictions("dairy").build()));
        stew = foods.save(new Food("Browser rescue stew", 20, 14, List.of("MILK")));
        past = new Order(null, "past stew");
        past.setUser(fan);
        past.addFood(stew);
        past.setCreatedAt(LocalDateTime.now().minusDays(1));
        past = orders.save(past);
        Instant now = Instant.now();
        SurplusOfferDto offer = surplus.publish(new SurplusOfferRequest(stew.getId(), "Beef, milk, carrots",
                new BigDecimal("7.00"), 3, "Browser test counter", now.minusSeconds(60), now.plusSeconds(7200),
                now.plusSeconds(3600)));
        offerId = offer.id();

        signIn("rescue-browser-staff", "ROLE_STAFF", "/rescue/manage");
        wait.until(browser -> browser.findElements(By.xpath(
                "//button[normalize-space(.)='Preview matches for BROWSER RESCUE STEW']")).size() == 1);
        driver.findElement(By.xpath("//button[normalize-space(.)='Preview matches for BROWSER RESCUE STEW']")).click();
        WebElement fanRow = wait.until(browser -> browser.findElement(By.xpath("//tr[td[text()='rescue-browser-fan']]")));
        assertTrue(fanRow.getText().contains("you ordered this once · 50% off"));
        WebElement allergicRow = driver.findElement(By.xpath("//tr[td[text()='rescue-browser-allergic']]"));
        assertTrue(allergicRow.getText().contains("Conflicts with saved dietary restrictions"));

        driver.findElement(By.xpath("//button[normalize-space(.)='Send rescue alerts for BROWSER RESCUE STEW']")).click();
        wait.until(browser -> browser.getPageSource().contains("rescue-browser-fan")
                && browser.getPageSource().contains("Alerted "));
        assertTrue(alerts.existsByOfferIdAndRecipientUsername(offer.id(), "rescue-browser-fan"));
        assertFalse(alerts.existsByOfferIdAndRecipientUsername(offer.id(), "rescue-browser-allergic"));

        signIn("rescue-browser-fan", "ROLE_CUSTOMER", "/rescue");
        wait.until(browser -> browser.getPageSource().contains("♻️ Rescue Deals (1)"));
        assertFalse(driver.getPageSource().contains("unread food safety"));
        WebElement reserve = wait.until(browser -> browser.findElement(
                By.xpath("//button[normalize-space(.)='Reserve 1 portion of BROWSER RESCUE STEW']")));
        reserve.click();
        wait.until(browser -> browser.getPageSource().contains("Reserved! Reservation #"));
        wait.until(browser -> browser.getPageSource().contains("You reserved this"));

        signIn("rescue-browser-staff", "ROLE_STAFF", "/rescue/manage");
        By stewCard = By.xpath("//article[h3[normalize-space(.)='BROWSER RESCUE STEW']]");
        wait.until(browser -> browser.findElements(stewCard).size() == 1
                && browser.findElement(stewCard).getText().contains("1 of them reserved"));
        assertTrue(driver.findElement(stewCard).getText().contains("$7.00 recovered"));

        var saved = reservations.findByCustomerUsernameOrderByCreatedAtDesc("rescue-browser-fan");
        assertEquals(1, saved.size());
        assertEquals(1, saved.getFirst().getQuantity());
        assertEquals(offer.id(), saved.getFirst().getOffer().getId());
    }

    private void signIn(final String username, final String role, final String path) {
        String token = tokens.generateToken(new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority(role))));
        driver.get(FRONTEND + "/");
        driver.executeScript("localStorage.setItem('token', arguments[0]);", token);
        driver.get(FRONTEND + path);
    }
}
