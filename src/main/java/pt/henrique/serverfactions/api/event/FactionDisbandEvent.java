package pt.henrique.serverfactions.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a faction is dissolved/disbanded.
 * MultiBank can listen to this to freeze/delete the faction bank account.
 */
public class FactionDisbandEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final String factionId;
    private final UUID disbandedByUuid;

    public FactionDisbandEvent(String factionId, UUID disbandedByUuid) {
        this.factionId = factionId;
        this.disbandedByUuid = disbandedByUuid;
    }

    public String getFactionId() {
        return factionId;
    }

    public UUID getDisbandedByUuid() {
        return disbandedByUuid;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

