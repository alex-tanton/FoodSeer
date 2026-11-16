package FoodSeer.controller;

import java.util.Date;
import java.util.List;

import FoodSeer.dto.*;
import FoodSeer.exception.ErrorDetails;
import FoodSeer.service.SurplusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/surplus")
@PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ADMIN')")
public class SurplusController {
    private final SurplusService service;

    public SurplusController(SurplusService service) {
        this.service = service;
    }

    @GetMapping("/offers")
    public List<SurplusOfferDto> browse() {
        return service.browse(false);
    }

    @GetMapping("/offers/manage")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public List<SurplusOfferDto> manageOffers() {
        return service.browse(true);
    }

    @PostMapping("/offers")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<SurplusOfferDto> publish(@RequestBody SurplusOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.publish(request));
    }

    @PostMapping("/offers/{id}/reservations")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SurplusReservationDto> reserve(@PathVariable("id") Long id,
            @RequestBody SurplusReservationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.reserve(id, authentication.getName(), request));
    }

    @GetMapping("/reservations")
    public List<SurplusReservationDto> reservations(Authentication authentication) {
        boolean staff = authentication.getAuthorities().stream()
                .anyMatch(authority -> List.of("ROLE_STAFF", "ROLE_ADMIN").contains(authority.getAuthority()));
        return service.listReservations(authentication.getName(), staff);
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public SurplusReservationDto cancel(@PathVariable("id") Long id, Authentication authentication) {
        return service.cancel(id, authentication.getName());
    }

    @PostMapping("/reservations/{id}/collect")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public SurplusReservationDto collect(@PathVariable("id") Long id) {
        return service.collect(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDetails> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorDetails(new Date(), exception.getMessage(), "Invalid surplus request"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDetails> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDetails(new Date(), exception.getMessage(), "Surplus reservation conflict"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDetails> malformedRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ErrorDetails(new Date(),
                "Provide valid JSON with whole-number portions and ISO timestamps.", "Invalid surplus request"));
    }
}
