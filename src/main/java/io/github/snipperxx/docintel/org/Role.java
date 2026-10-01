package io.github.snipperxx.docintel.org;

/**
 * Ordered from least to most privileged; a higher role includes every lower one.
 */
public enum Role {
    VIEWER,
    EDITOR,
    ADMIN;

    public boolean includes(Role required) {
        return compareTo(required) >= 0;
    }
}
