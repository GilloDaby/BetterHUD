package com.gillodaby.betterhud;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;

import java.util.concurrent.CompletableFuture;

/**
 * /betterhud show
 * /betterhud on
 * /betterhud hide
 * /betterhud off
 */
final class BetterHudCommand extends AbstractCommand {

    private final BetterHudService service;

    BetterHudCommand(BetterHudService service) {
        super("betterhud", "Show or hide the BetterHUD overlay");
        this.service = service;

        // show
        AbstractCommand show = new AbstractCommand("show", "Show the BetterHUD overlay") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleShow(ctx);
            }
        };
        addSubCommand(show);

        // on
        AbstractCommand on = new AbstractCommand("on", "Show the BetterHUD overlay") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleShow(ctx);
            }
        };
        addSubCommand(on);

        // hide
        AbstractCommand hide = new AbstractCommand("hide", "Hide the BetterHUD overlay") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleHide(ctx);
            }
        };
        addSubCommand(hide);

        // off
        AbstractCommand off = new AbstractCommand("off", "Hide the BetterHUD overlay") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleHide(ctx);
            }
        };
        addSubCommand(off);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // Default help message
        String help = String.join("\n",
            "/betterhud show",
            "/betterhud on",
            "/betterhud hide",
            "/betterhud off"
        );
        ctx.sendMessage(Message.raw(help));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleShow(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("[BetterHUD] Only players can use this command."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        if (player == null) {
            ctx.sendMessage(Message.raw("[BetterHUD] Player not found for this command sender."));
            return CompletableFuture.completedFuture(null);
        }
        service.showHud(player);
        ctx.sendMessage(Message.raw("[BetterHUD] HUD is now visible."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleHide(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("[BetterHUD] Only players can use this command."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        if (player == null) {
            ctx.sendMessage(Message.raw("[BetterHUD] Player not found for this command sender."));
            return CompletableFuture.completedFuture(null);
        }
        service.hideHud(player);
        ctx.sendMessage(Message.raw("[BetterHUD] HUD is now hidden."));
        return CompletableFuture.completedFuture(null);
    }
}
