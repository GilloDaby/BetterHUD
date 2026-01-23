package com.gillodaby.betterhud;

import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.command.system.CommandManager;

public class BetterHudPlugin extends JavaPlugin {

    private BetterHudService service;

    public BetterHudPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
    }

    @Override
    public void start() {
        EventBus bus = HytaleServer.get().getEventBus();
        service = new BetterHudService();

        bus.registerGlobal(PlayerReadyEvent.class, service::handlePlayerReady);
        bus.registerGlobal(PlayerDisconnectEvent.class, service::handlePlayerDisconnect);

        service.start();
        
        // Register command
        CommandManager commandManager = CommandManager.get();
        commandManager.register(new BetterHudCommand(service));
        
        System.out.println("[BetterHUD] Started and waiting for players.");
    }
}
