package pt.henrique.serverfactions.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when faction leadership is transferred to a new player.
 */
public class FactionLeaderChangeEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final String factionId;
    private final UUID oldLeader;
    private final UUID newLeader;

    public FactionLeaderChangeEvent(String factionId, UUID oldLeader, UUID newLeader) {
        this.factionId = factionId;
        this.oldLeader = oldLeader;
        this.newLeader = newLeader;
    }

    public String getFactionId() {
        return factionId;
    }

    public UUID getOldLeader() {
        return oldLeader;
    }

    public UUID getNewLeader() {
        return newLeader;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

