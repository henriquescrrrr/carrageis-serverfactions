package pt.henrique.serverfactions.api.event;

/**
 * Reason a member left a faction.
 */
public enum LeaveReason {
    /** Player voluntarily left */
    LEFT,
    /** Player was kicked by another member */
    KICKED,
    /** Faction was disbanded */
    DISBANDED
}

