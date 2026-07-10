package pt.henrique.serverfactions.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player joins a faction (via invite accept or public join).
 */
public class FactionMemberJoinEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final String factionId;
    private final UUID memberUuid;

    public FactionMemberJoinEvent(String factionId, UUID memberUuid) {
        this.factionId = factionId;
        this.memberUuid = memberUuid;
    }

    public String getFactionId() {
        return factionId;
    }

    public UUID getMemberUuid() {
        return memberUuid;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

