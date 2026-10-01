package io.github.snipperxx.docintel.org;

import io.github.snipperxx.docintel.common.ForbiddenException;
import io.github.snipperxx.docintel.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Hierarchical authorization: a role on a unit applies to the unit and its whole subtree,
 * and the effective role is the highest one found on the path from the root.
 */
@Service
@Transactional(readOnly = true)
public class AccessService {

    private final OrgUnitRepository units;
    private final MembershipRepository memberships;

    public AccessService(OrgUnitRepository units, MembershipRepository memberships) {
        this.units = units;
        this.memberships = memberships;
    }

    public Optional<Role> effectiveRole(long userId, OrgUnit unit) {
        return memberships.findRoles(userId, unit.lineageIds()).stream()
                .max(Comparator.naturalOrder());
    }

    /**
     * Loads the unit and checks the caller's role on it. Callers without any role get a 404
     * rather than a 403, so the existence of other teams' units is not revealed.
     */
    public OrgUnit requireUnit(long userId, long unitId, Role required) {
        OrgUnit unit = units.findById(unitId)
                .orElseThrow(() -> new NotFoundException("Unit " + unitId + " not found"));
        Role role = effectiveRole(userId, unit)
                .orElseThrow(() -> new NotFoundException("Unit " + unitId + " not found"));
        if (!role.includes(required)) {
            throw new ForbiddenException("Requires " + required + " on unit " + unitId + ", you have " + role);
        }
        return unit;
    }

    public List<Long> visibleProjectIds(long userId) {
        return memberships.findVisibleProjectIds(userId);
    }
}
