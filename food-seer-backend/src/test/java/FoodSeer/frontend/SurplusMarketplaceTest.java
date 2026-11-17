package FoodSeer.frontend;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import FoodSeer.entity.Food;
import FoodSeer.entity.User;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.SurplusReservationRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Start the React frontend on port 3000 before running this browser test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
class SurplusMarketplaceTest {
    @Autowired private FoodRepository foods;
    @Autowired private UserRepository users;
    @Autowired private SurplusReservationRepository reservations;
    @Autowired private JwtTokenProvider tokens;
    private ChromeDriver driver;
    private WebDriverWait wait;

    @AfterEach
    void closeBrowser() {
        if (driver != null) driver.quit();
    }

    @Test
    void staffPublishCustomerReservesAndStaffCollectsThroughRealApi() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1280,1000");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        users.save(User.builder().username("surplus-browser-staff").email("surplus-browser-staff@example.test")
                .password("unused").role("ROLE_STAFF").build());
        users.save(User.builder().username("surplus-browser-customer").email("surplus-browser-customer@example.test")
                .password("unused").role("ROLE_CUSTOMER").build());
        Food food = foods.save(new Food("Browser surplus soup", 20, 10, List.of("MILK")));
        Instant now = Instant.now();

        signIn("surplus-browser-staff", "ROLE_STAFF");
        wait.until(browser -> browser.findElements(By.id("surplus-food")).size() == 1);
        new Select(driver.findElement(By.id("surplus-food"))).selectByValue(food.getId().toString());
        driver.findElement(By.id("surplus-ingredients")).sendKeys("Milk, vegetables");
        driver.findElement(By.id("surplus-price")).sendKeys("3.25");
        driver.findElement(By.id("surplus-portions")).sendKeys("5");
        driver.findElement(By.id("surplus-location")).sendKeys("Browser test front desk");
        date("surplus-start", now.minusSeconds(120));
        date("surplus-end", now.plusSeconds(7200));
        date("surplus-expiry", now.plusSeconds(3600));
        driver.findElement(By.cssSelector(".surplus-form button[type=submit]")).click();
        wait.until(browser -> browser.getPageSource().contains("Surplus offer published."));

        signIn("surplus-browser-customer", "ROLE_CUSTOMER");
        WebElement card = wait.until(browser -> browser.findElement(By.xpath(
                "//article[h3[text()='BROWSER SURPLUS SOUP']]")));
        assertTrue(card.getText().contains("Milk, vegetables"));
        assertTrue(card.getText().contains("MILK"));
        WebElement quantity = card.findElement(By.cssSelector("input[name=quantity]"));
        quantity.clear();
        quantity.sendKeys("2");
        date(card.findElement(By.cssSelector("input[name=pickupAt]")).getAttribute("id"), now.plusSeconds(1800));
        card.findElement(By.tagName("button")).click();
        wait.until(browser -> browser.getPageSource().contains("Pay $6.50 at pickup."));
        wait.until(browser -> browser.getPageSource().contains("2 portions · $6.50 total · RESERVED"));

        signIn("surplus-browser-staff", "ROLE_STAFF");
        WebElement collect = wait.until(browser -> browser.findElement(By.xpath(
                "//button[starts-with(normalize-space(.), 'Mark #') and contains(., 'collected')]")));
        collect.click();
        wait.until(browser -> browser.getPageSource().contains("2 portions · $6.50 total · COLLECTED"));
        var saved = reservations.findByCustomerUsernameOrderByCreatedAtDesc("surplus-browser-customer");
        assertEquals(1, saved.size());
        assertEquals("COLLECTED", saved.getFirst().getStatus().name());
        assertEquals(2, saved.getFirst().getQuantity());
        assertEquals(20, foods.findById(food.getId()).orElseThrow().getAmount());
    }

    private void signIn(String username, String role) {
        String token = tokens.generateToken(new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority(role))));
        driver.get("http://localhost:3000/");
        driver.executeScript("localStorage.setItem('token', arguments[0]);", token);
        driver.get("http://localhost:3000/surplus");
    }

    private void date(String id, Instant instant) {
        String value = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        driver.executeScript("arguments[0].value = arguments[1];",
                driver.findElement(By.id(id)), value);
    }
}
