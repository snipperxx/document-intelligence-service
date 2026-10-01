package io.github.snipperxx.docintel.org;

import io.github.snipperxx.docintel.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "membership")
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id")
    private OrgUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Membership() {
    }

    public Membership(AppUser user, OrgUnit unit, Role role) {
        this.user = user;
        this.unit = unit;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public OrgUnit getUnit() {
        return unit;
    }

    public Role getRole() {
        return role;
    }
}
