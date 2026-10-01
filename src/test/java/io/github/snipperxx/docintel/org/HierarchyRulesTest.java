package io.github.snipperxx.docintel.org;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HierarchyRulesTest {

    @Test
    void unitTypesNestInFixedOrder() {
        assertThat(OrgUnitType.DEPARTMENT.canBeChildOf(OrgUnitType.ORGANIZATION)).isTrue();
        assertThat(OrgUnitType.PROJECT.canBeChildOf(OrgUnitType.DEPARTMENT)).isTrue();

        assertThat(OrgUnitType.PROJECT.canBeChildOf(OrgUnitType.ORGANIZATION)).isFalse();
        assertThat(OrgUnitType.DEPARTMENT.canBeChildOf(OrgUnitType.PROJECT)).isFalse();
        assertThat(OrgUnitType.ORGANIZATION.canBeChildOf(OrgUnitType.ORGANIZATION)).isFalse();
    }

    @Test
    void higherRolesIncludeLowerOnes() {
        assertThat(Role.ADMIN.includes(Role.EDITOR)).isTrue();
        assertThat(Role.EDITOR.includes(Role.VIEWER)).isTrue();
        assertThat(Role.EDITOR.includes(Role.EDITOR)).isTrue();

        assertThat(Role.VIEWER.includes(Role.EDITOR)).isFalse();
        assertThat(Role.EDITOR.includes(Role.ADMIN)).isFalse();
    }

    @Test
    void pathListsLineageFromRoot() {
        OrgUnit org = persisted(OrgUnit.root("Acme"), 1L);
        OrgUnit department = persisted(OrgUnit.child(org, "Legal", OrgUnitType.DEPARTMENT), 4L);
        OrgUnit project = persisted(OrgUnit.child(department, "Contracts", OrgUnitType.PROJECT), 9L);

        assertThat(project.getPath()).isEqualTo("/1/4/9/");
        assertThat(project.lineageIds()).containsExactly(1L, 4L, 9L);
    }

    @Test
    void rejectsInvalidNesting() {
        OrgUnit org = persisted(OrgUnit.root("Acme"), 1L);

        assertThatThrownBy(() -> OrgUnit.child(org, "Contracts", OrgUnitType.PROJECT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Simulates what JPA does on insert: the id is generated, then the path can be assigned.
     */
    private static OrgUnit persisted(OrgUnit unit, long id) {
        ReflectionTestUtils.setField(unit, "id", id);
        unit.assignPath();
        return unit;
    }
}
