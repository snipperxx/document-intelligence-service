package io.github.snipperxx.docintel.org;

import io.github.snipperxx.docintel.auth.AppUser;
import io.github.snipperxx.docintel.auth.AppUserRepository;
import io.github.snipperxx.docintel.common.BadRequestException;
import io.github.snipperxx.docintel.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class OrgService {

    private final OrgUnitRepository units;
    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final AccessService access;

    public OrgService(OrgUnitRepository units, MembershipRepository memberships, AppUserRepository users,
                      AccessService access) {
        this.units = units;
        this.memberships = memberships;
        this.users = users;
        this.access = access;
    }

    /**
     * Any user may found an organization and becomes its admin.
     */
    @Transactional
    public UnitView createOrganization(long userId, String name) {
        OrgUnit unit = persist(OrgUnit.root(name.strip()));
        memberships.save(new Membership(users.getReferenceById(userId), unit, Role.ADMIN));
        return UnitView.of(unit, Role.ADMIN);
    }

    @Transactional
    public UnitView createChild(long userId, long parentId, String name, OrgUnitType type) {
        OrgUnit parent = access.requireUnit(userId, parentId, Role.ADMIN);
        if (!type.canBeChildOf(parent.getType())) {
            throw new BadRequestException(type + " cannot be created under " + parent.getType());
        }
        OrgUnit unit = persist(OrgUnit.child(parent, name.strip(), type));
        return UnitView.of(unit, Role.ADMIN);
    }

    @Transactional(readOnly = true)
    public UnitView get(long userId, long unitId) {
        OrgUnit unit = access.requireUnit(userId, unitId, Role.VIEWER);
        return UnitView.of(unit, access.effectiveRole(userId, unit).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<UnitView> children(long userId, long unitId) {
        OrgUnit unit = access.requireUnit(userId, unitId, Role.VIEWER);
        Role inherited = access.effectiveRole(userId, unit).orElseThrow();
        return units.findByParentIdOrderByName(unitId).stream()
                .map(child -> UnitView.of(child, access.effectiveRole(userId, child).orElse(inherited)))
                .toList();
    }

    /**
     * Units where the user was granted a role directly (not the inherited subtree).
     */
    @Transactional(readOnly = true)
    public List<UnitView> myUnits(long userId) {
        return memberships.findByUserWithUnit(userId).stream()
                .map(m -> UnitView.of(m.getUnit(), m.getRole()))
                .toList();
    }

    @Transactional
    public MemberView grant(long userId, long unitId, String email, Role role) {
        OrgUnit unit = access.requireUnit(userId, unitId, Role.ADMIN);
        AppUser member = users.findByEmailIgnoreCase(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("No user with email " + email));
        Membership membership = memberships.findByUserIdAndUnitId(member.getId(), unitId)
                .map(existing -> {
                    existing.changeRole(role);
                    return existing;
                })
                .orElseGet(() -> memberships.save(new Membership(member, unit, role)));
        return MemberView.of(membership);
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(long userId, long unitId) {
        access.requireUnit(userId, unitId, Role.VIEWER);
        return memberships.findByUnitWithUser(unitId).stream().map(MemberView::of).toList();
    }

    private OrgUnit persist(OrgUnit unit) {
        OrgUnit saved = units.saveAndFlush(unit);
        saved.assignPath();
        return saved;
    }

    public record UnitView(Long id, String name, OrgUnitType type, Long parentId, String path, Role myRole) {
        static UnitView of(OrgUnit unit, Role role) {
            Long parentId = unit.getParent() == null ? null : unit.getParent().getId();
            return new UnitView(unit.getId(), unit.getName(), unit.getType(), parentId, unit.getPath(), role);
        }
    }

    public record MemberView(Long userId, String email, String displayName, Role role) {
        static MemberView of(Membership m) {
            return new MemberView(m.getUser().getId(), m.getUser().getEmail(), m.getUser().getDisplayName(),
                    m.getRole());
        }
    }
}
