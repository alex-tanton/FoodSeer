package FoodSeer.rescue;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Surplus rescue matching.
 *
 * <ul>
 * <li>GET /api/rescue/offers/{id}/matches: staff preview of who would be alerted and why.</li>
 * <li>POST /api/rescue/offers/{id}/notify: alert the top-ranked customers.</li>
 * <li>GET /api/rescue/deals: the signed-in customer's open rescue deals.</li>
 * <li>GET /api/rescue/stats and /api/rescue/offers/{id}/stats: rescue results for staff.</li>
 * </ul>
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/api/rescue")
public class RescueController {

    private final RescueService service;
    private final RescueStatsService stats;

    public RescueController(final RescueService service, final RescueStatsService stats) {
        this.service = service;
        this.stats = stats;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public List<RescueDtos.Stats> allStats() {
        return stats.all();
    }

    @GetMapping("/offers/{id}/stats")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public RescueDtos.Stats offerStats(@PathVariable("id") final Long id) {
        return stats.forOffer(id);
    }

    @GetMapping("/offers/{id}/matches")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public RescueDtos.Preview preview(@PathVariable("id") final Long id) {
        return service.preview(id);
    }

    @PostMapping("/offers/{id}/notify")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public RescueDtos.NotifyResult notifyMatches(@PathVariable("id") final Long id) {
        return service.notify(id);
    }

    @GetMapping("/deals")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<RescueDtos.Deal> deals(final Authentication authentication) {
        return service.deals(authentication.getName());
    }
}
