package pt.henrique.serverfactions.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import pt.henrique.serverfactions.ServerFactions;
import pt.henrique.serverfactions.config.LangManager;
import pt.henrique.serverfactions.gui.*;
import pt.henrique.serverfactions.manager.FactionManager;
import pt.henrique.serverfactions.model.Faction;
import pt.henrique.serverfactions.util.SignInputUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Registers all /fac commands using Paper's Brigadier API.
 * Aliases: /f, /faction
 */
@SuppressWarnings("UnstableApiUsage")
public final class FactionCommand {

    private FactionCommand() {}

    public static void register(ServerFactions plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    Commands.literal("fac")
                            .executes(FactionCommand::handleBase)
                            // /fac create
                            .then(Commands.literal("create")
                                    .executes(FactionCommand::handleCreate))
                            // /fac invite <player>
                            .then(Commands.literal("invite")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(FactionCommand::suggestInvitablePlayers)
                                            .executes(FactionCommand::handleInvite)))
                            // /fac chat
                            .then(Commands.literal("chat")
                                    .executes(FactionCommand::handleChat))
                            // /fac info [faction]
                            .then(Commands.literal("info")
                                    .executes(FactionCommand::handleInfoSelf)
                                    .then(Commands.argument("faction", StringArgumentType.greedyString())
                                            .suggests(FactionCommand::suggestFactionNames)
                                            .executes(FactionCommand::handleInfoOther)))
                            // /fac list
                            .then(Commands.literal("list")
                                    .executes(FactionCommand::handleList))
                            // /fac invites
                            .then(Commands.literal("invites")
                                    .executes(FactionCommand::handleInvites))
                            // /fac leave
                            .then(Commands.literal("leave")
                                    .executes(FactionCommand::handleLeave))
                            // /fac admin ...
                            .then(Commands.literal("admin")
                                    .requires(src -> src.getSender().hasPermission("serverfactions.admin"))
                                    .then(Commands.literal("disband")
                                            .then(Commands.argument("faction", StringArgumentType.greedyString())
                                                    .suggests(FactionCommand::suggestFactionNames)
                                                    .executes(FactionCommand::handleAdminDisband)))
                                    .then(Commands.literal("reload")
                                            .executes(FactionCommand::handleAdminReload)))
                            .build(),
                    "Faction management commands",
                    List.of("f", "faction")
            );
        });
    }

    // ======================== HANDLERS ========================

    private static int handleBase(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;
        MainMenuGui.open(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleCreate(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;

        ServerFactions plugin = ServerFactions.getInstance();
        FactionManager fm = plugin.getFactionManager();
        LangManager lang = plugin.getLangManager();

        if (fm.isInFaction(player.getUniqueId())) {
            player.sendMessage(lang.get("faction-already-in-faction", player));
            return 0;
        }

        // Start creation flow: Sign for name
        String namePrompt = lang.getRaw("sign-faction-name", player);
        SignInputUtil.requestInput(plugin, player, namePrompt, name -> {
            if (name.isEmpty()) return;
            int min = plugin.getConfigManager().getMinNameLength();
            int max = plugin.getConfigManager().getMaxNameLength();
            if (name.length() < min || name.length() > max) {
                player.sendMessage(lang.get("faction-name-too-long", player,
                        Map.of("min", String.valueOf(min), "max", String.valueOf(max))));
                return;
            }
            if (fm.isNameTaken(name)) {
                player.sendMessage(lang.get("faction-name-taken", player));
                return;
            }
            // Sign for tag
            String tagPrompt = lang.getRaw("sign-faction-tag", player)
                    .replace("{max}", String.valueOf(plugin.getConfigManager().getMaxTagLength()));
            SignInputUtil.requestInput(plugin, player, tagPrompt, tag -> {
                if (tag.isEmpty()) return;
                if (tag.length() > plugin.getConfigManager().getMaxTagLength()) {
                    player.sendMessage(lang.get("faction-tag-too-long", player,
                            Map.of("max", String.valueOf(plugin.getConfigManager().getMaxTagLength()))));
                    return;
                }
                if (fm.isTagTaken(tag)) {
                    player.sendMessage(lang.get("faction-tag-taken", player));
                    return;
                }
                VisibilitySelectGui.open(player, name, tag);
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int handleInvite(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;

        ServerFactions plugin = ServerFactions.getInstance();
        FactionManager fm = plugin.getFactionManager();
        LangManager lang = plugin.getLangManager();

        String factionId = fm.getFactionIdByPlayer(player.getUniqueId());
        if (factionId == null) {
            player.sendMessage(lang.get("faction-not-in-faction", player));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(lang.get("faction-target-in-faction", player,
                    Map.of("player", targetName)));
            return 0;
        }

        fm.invitePlayer(player.getUniqueId(), target.getUniqueId(), factionId);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleChat(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;

        ServerFactions plugin = ServerFactions.getInstance();
        FactionManager fm = plugin.getFactionManager();
        LangManager lang = plugin.getLangManager();

        if (!fm.isInFaction(player.getUniqueId())) {
            player.sendMessage(lang.get("faction-not-in-faction", player));
            return 0;
        }

        fm.toggleFactionChat(player.getUniqueId());
        boolean active = fm.isFactionChatActive(player.getUniqueId());
        player.sendMessage(lang.get(active ? "faction-chat-enabled" : "faction-chat-disabled", player));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleInfoSelf(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;

        FactionManager fm = ServerFactions.getInstance().getFactionManager();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(ServerFactions.getInstance().getLangManager()
                    .get("faction-not-in-faction", player));
            return 0;
        }
        FactionInfoGui.open(player, faction.getFactionId());
        return Command.SINGLE_SUCCESS;
    }

    private static int handleInfoOther(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;

        String factionName = StringArgumentType.getString(ctx, "faction");
        FactionManager fm = ServerFactions.getInstance().getFactionManager();

        // Search by name (case-insensitive)
        String factionId = fm.getFactionIdByName(factionName);
        if (factionId == null) {
            player.sendMessage(ServerFactions.getInstance().getLangManager()
                    .get("faction-name-taken", player)); // reuse: "not found" would be better
            return 0;
        }
        FactionInfoGui.open(player, factionId);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleList(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;
        PublicFactionsGui.open(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleInvites(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;
        InvitesGui.open(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleLeave(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();
        if (!checkPerm(player, "serverfactions.use")) return 0;

        ServerFactions plugin = ServerFactions.getInstance();
        FactionManager fm = plugin.getFactionManager();
        LangManager lang = plugin.getLangManager();
        Faction faction = fm.getFactionByPlayer(player.getUniqueId());

        if (faction == null) {
            player.sendMessage(lang.get("faction-not-in-faction", player));
            return 0;
        }
        ConfirmActionGui.open(player,
                lang.getRaw("gui-confirm-leave", player),
                "<yellow>Leave <white>" + faction.getName() + "</white>?",
                () -> fm.leaveFaction(player.getUniqueId()));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleAdminDisband(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();

        String factionName = StringArgumentType.getString(ctx, "faction");
        FactionManager fm = ServerFactions.getInstance().getFactionManager();
        LangManager lang = ServerFactions.getInstance().getLangManager();

        String factionId = fm.getFactionIdByName(factionName);
        if (factionId == null) {
            player.sendMessage(lang.get("faction-name-taken", player));
            return 0;
        }

        fm.dissolveFaction(factionId, player.getUniqueId());
        player.sendMessage(lang.get("admin-disband", player, Map.of("faction", factionName)));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleAdminReload(CommandContext<CommandSourceStack> ctx) {
        if (!requirePlayer(ctx)) return 0;
        Player player = (Player) ctx.getSource().getSender();

        ServerFactions plugin = ServerFactions.getInstance();
        plugin.getConfigManager().reload();
        plugin.getLangManager().reload();
        player.sendMessage(plugin.getLangManager().get("admin-reload", player));
        return Command.SINGLE_SUCCESS;
    }

    // ======================== SUGGESTIONS ========================

    private static CompletableFuture<Suggestions> suggestInvitablePlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        FactionManager fm = ServerFactions.getInstance().getFactionManager();
        String input = builder.getRemainingLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(ctx.getSource().getSender())) continue;
            if (!fm.isInFaction(online.getUniqueId()) && online.getName().toLowerCase().startsWith(input)) {
                builder.suggest(online.getName());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFactionNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        FactionManager fm = ServerFactions.getInstance().getFactionManager();
        String input = builder.getRemainingLowerCase();
        for (Faction f : fm.getAllFactions()) {
            if (f.getName().toLowerCase().startsWith(input)) {
                builder.suggest(f.getName());
            }
        }
        return builder.buildFuture();
    }

    // ======================== UTIL ========================

    private static boolean requirePlayer(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender() instanceof Player;
    }

    private static boolean checkPerm(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            player.sendMessage(net.kyori.adventure.text.Component.text("§cNo permission."));
            return false;
        }
        return true;
    }
}

