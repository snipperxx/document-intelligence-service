package io.github.snipperxx.docintel.org;

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
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "org_unit")
public class OrgUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgUnitType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrgUnit parent;

    /**
     * Materialized path of ids from the root, e.g. {@code /1/4/9/}. Assigned once the id is known.
     */
    @Column(length = 512)
    private String path;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrgUnit() {
    }

    private OrgUnit(String name, OrgUnitType type, OrgUnit parent) {
        this.name = name;
        this.type = type;
        this.parent = parent;
        this.createdAt = Instant.now();
    }

    public static OrgUnit root(String name) {
        return new OrgUnit(name, OrgUnitType.ORGANIZATION, null);
    }

    public static OrgUnit child(OrgUnit parent, String name, OrgUnitType type) {
        if (!type.canBeChildOf(parent.getType())) {
            throw new IllegalArgumentException(type + " cannot be created under " + parent.getType());
        }
        return new OrgUnit(name, type, parent);
    }

    /**
     * Must be called after the first flush, when the generated id is available.
     */
    void assignPath() {
        if (id == null) {
            throw new IllegalStateException("Unit must be persisted before its path is assigned");
        }
        String parentPath = parent == null ? "/" : parent.getPath();
        this.path = parentPath + id + "/";
    }

    /**
     * Ids from the root down to and including this unit.
     */
    public List<Long> lineageIds() {
        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public OrgUnitType getType() {
        return type;
    }

    public OrgUnit getParent() {
        return parent;
    }

    public String getPath() {
        return path;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
