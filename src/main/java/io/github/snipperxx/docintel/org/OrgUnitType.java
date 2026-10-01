package io.github.snipperxx.docintel.org;

/**
 * Levels of the organization tree. Each level may only sit directly below one other level.
 */
public enum OrgUnitType {
    ORGANIZATION(null),
    DEPARTMENT(ORGANIZATION),
    PROJECT(DEPARTMENT);

    private final OrgUnitType allowedParent;

    OrgUnitType(OrgUnitType allowedParent) {
        this.allowedParent = allowedParent;
    }

    public boolean canBeChildOf(OrgUnitType parentType) {
        return allowedParent != null && allowedParent == parentType;
    }

    public boolean isRoot() {
        return allowedParent == null;
    }
}
