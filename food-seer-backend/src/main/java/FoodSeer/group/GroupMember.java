package FoodSeer.group;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A participant in a group dining session.
 * Stores a snapshot of dietary restrictions and preferences active for this session.
 */
@Entity
@Table(name = "group_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "username"}))
@Getter
@Setter
@NoArgsConstructor
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GroupSession session;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(length = 500)
    private String dietaryRestrictions;

    @Column(length = 50)
    private String costPreference;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    public GroupMember(final GroupSession session, final String username, final String displayName,
            final String dietaryRestrictions, final String costPreference) {
        this.session = session;
        this.username = username;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : username;
        this.dietaryRestrictions = dietaryRestrictions;
        this.costPreference = costPreference;
        this.joinedAt = Instant.now();
    }
}
