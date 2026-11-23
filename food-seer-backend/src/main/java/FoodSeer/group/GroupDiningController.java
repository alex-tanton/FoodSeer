package FoodSeer.group;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for collaborative group dining rooms, harmonized menus, and split billing.
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/api/group")
@PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ADMIN')")
public class GroupDiningController {

    private final GroupDiningService groupDiningService;

    public GroupDiningController(final GroupDiningService groupDiningService) {
        this.groupDiningService = groupDiningService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<GroupDtos.GroupSessionDto> createSession(
            @RequestBody final GroupDtos.CreateSessionRequest request, final Authentication authentication) {
        final String username = authentication != null ? authentication.getName() : "anonymous";
        final GroupDtos.GroupSessionDto session = groupDiningService.createSession(username, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/sessions/{code}/join")
    public ResponseEntity<GroupDtos.GroupSessionDto> joinSession(@PathVariable("code") final String code,
            @RequestBody(required = false) final GroupDtos.JoinSessionRequest request,
            final Authentication authentication) {
        final String username = authentication != null ? authentication.getName() : "guest";
        final GroupDtos.GroupSessionDto session = groupDiningService.joinSession(code, username, request);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/sessions/{code}")
    public ResponseEntity<GroupDtos.GroupSessionDto> getSession(@PathVariable("code") final String code) {
        return ResponseEntity.ok(groupDiningService.getSession(code));
    }

    @GetMapping("/sessions/{code}/menu")
    public ResponseEntity<GroupDtos.HarmonizedMenuResponse> getHarmonizedMenu(
            @PathVariable("code") final String code) {
        return ResponseEntity.ok(groupDiningService.getHarmonizedMenu(code));
    }

    @PostMapping("/sessions/{code}/items")
    public ResponseEntity<GroupDtos.GroupSessionDto> addToTray(@PathVariable("code") final String code,
            @RequestBody final GroupDtos.AddCartItemRequest request, final Authentication authentication) {
        final String username = authentication != null ? authentication.getName() : "guest";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupDiningService.addToTray(code, username, request));
    }

    @DeleteMapping("/sessions/{code}/items/{itemId}")
    public ResponseEntity<GroupDtos.GroupSessionDto> removeFromTray(@PathVariable("code") final String code,
            @PathVariable("itemId") final Long itemId, final Authentication authentication) {
        final String username = authentication != null ? authentication.getName() : "guest";
        return ResponseEntity.ok(groupDiningService.removeFromTray(code, username, itemId));
    }

    @GetMapping("/sessions/{code}/split-bill")
    public ResponseEntity<GroupDtos.SplitBillResponse> getSplitBill(@PathVariable("code") final String code) {
        return ResponseEntity.ok(groupDiningService.getSplitBill(code));
    }

    @PostMapping("/sessions/{code}/checkout")
    public ResponseEntity<GroupDtos.CheckoutResponse> checkout(@PathVariable("code") final String code,
            final Authentication authentication) {
        final String username = authentication != null ? authentication.getName() : "anonymous";
        return ResponseEntity.ok(groupDiningService.checkout(code, username));
    }
}
