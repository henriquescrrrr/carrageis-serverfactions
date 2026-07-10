package pt.henrique.serverfactions.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player leaves a faction (voluntarily, kicked, or faction disbanded).
 */
public class FactionMemberLeaveEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final String factionId;
    private final UUID memberUuid;
    private final LeaveReason reason;

    public FactionMemberLeaveEvent(String factionId, UUID memberUuid, LeaveReason reason) {
        this.factionId = factionId;
        this.memberUuid = memberUuid;
        this.reason = reason;
    }

    public String getFactionId() {
        return factionId;
    }

    public UUID getMemberUuid() {
        return memberUuid;
    }

    public LeaveReason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

