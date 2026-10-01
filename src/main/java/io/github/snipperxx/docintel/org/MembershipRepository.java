package io.github.snipperxx.docintel.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("select m.role from Membership m where m.user.id = :userId and m.unit.id in :unitIds")
    List<Role> findRoles(long userId, Collection<Long> unitIds);

    Optional<Membership> findByUserIdAndUnitId(long userId, long unitId);

    @Query("select m from Membership m join fetch m.user where m.unit.id = :unitId order by m.user.email")
    List<Membership> findByUnitWithUser(long unitId);

    @Query("select m from Membership m join fetch m.unit where m.user.id = :userId order by m.unit.path")
    List<Membership> findByUserWithUnit(long userId);

    /**
     * Every project at or below a unit where the user holds any role. A role on a
     * department therefore reaches all of its projects through the path prefix.
     */
    @Query("""
            select distinct u.id from OrgUnit u, Membership m
            where m.user.id = :userId
              and u.type = io.github.snipperxx.docintel.org.OrgUnitType.PROJECT
              and u.path like concat(m.unit.path, '%')
            """)
    List<Long> findVisibleProjectIds(long userId);
}
