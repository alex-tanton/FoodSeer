package FoodSeer.group;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a collaborative group dining room.
 * Tracks session code, active status, participating members, and final order.
 */
@Entity
@Table(name = "group_sessions")
@Getter
@Setter
@NoArgsConstructor
public class GroupSession {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String sessionCode;

    @Column(nullable = false, length = 100)
    private String sessionName;

    @Column(nullable = false, length = 50)
    private String hostUsername;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant submittedAt;

    private Long orderId;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupCartItem> cartItems = new ArrayList<>();

    public GroupSession(final String sessionCode, final String sessionName, final String hostUsername) {
        this.sessionCode = sessionCode.toUpperCase();
        this.sessionName = sessionName;
        this.hostUsername = hostUsername;
        this.status = STATUS_ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
