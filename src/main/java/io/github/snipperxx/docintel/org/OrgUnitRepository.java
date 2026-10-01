package io.github.snipperxx.docintel.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    List<OrgUnit> findByParentIdOrderByName(Long parentId);
}
