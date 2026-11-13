package br.com.carbonifera.ete.user;

public enum UserRole {
    OPERATOR,
    SUPERVISOR,
    ADMIN,
    VIEWER;

    public boolean isStationBound() {
        return this == OPERATOR || this == VIEWER;
    }
}
