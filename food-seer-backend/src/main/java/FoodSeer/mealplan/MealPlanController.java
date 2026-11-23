package FoodSeer.mealplan;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import FoodSeer.exception.ResourceNotFoundException;

/**
 * A customer's weekly meal plan. Errors are answered here, because the global
 * handler turns unknown exceptions into 500s.
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/api/meal-plans")
@PreAuthorize("hasRole('CUSTOMER')")
public class MealPlanController {

    private final MealPlanService service;

    public MealPlanController(final MealPlanService service) {
        this.service = service;
    }

    /** Builds a new plan, replacing the current one. */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) final PlanDtos.CreateRequest request,
            final Authentication auth) {
        return handle(() -> service.create(auth.getName(), request));
    }

    /** The current plan, with what was actually eaten so far. */
    @GetMapping("/current")
    public ResponseEntity<?> current(final Authentication auth) {
        return handle(() -> service.current(auth.getName()));
    }

    /** Swaps one meal and re-balances the rest of that day. */
    @PostMapping("/current/swap")
    public ResponseEntity<?> swap(@RequestBody final PlanDtos.SlotRequest slot, final Authentication auth) {
        return handle(() -> service.swap(auth.getName(), slot));
    }

    /** Orders one of today's meals. */
    @PostMapping("/current/order")
    public ResponseEntity<?> order(@RequestBody final PlanDtos.SlotRequest slot, final Authentication auth) {
        return handle(() -> service.order(auth.getName(), slot));
    }

    /** Clears the current plan. */
    @DeleteMapping("/current")
    public ResponseEntity<?> delete(final Authentication auth) {
        service.delete(auth.getName());
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<?> handle(final java.util.function.Supplier<PlanDtos.Plan> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (final ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (final IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (final IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }
}
