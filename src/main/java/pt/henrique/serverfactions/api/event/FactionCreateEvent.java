package pt.henrique.serverfactions.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a new faction is created.
 * MultiBank's FactionAccountListener can listen to this to create a group bank account.
 */
public class FactionCreateEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final String factionId;
    private final UUID leaderUuid;
    private final String name;
    private final String tag;

    public FactionCreateEvent(String factionId, UUID leaderUuid, String name, String tag) {
        this.factionId = factionId;
        this.leaderUuid = leaderUuid;
        this.name = name;
        this.tag = tag;
    }

    public String getFactionId() {
        return factionId;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

