package com.lawlessmc.spawnfence;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SpawnFencePlugin extends JavaPlugin implements Listener {

    private String survivalHost;
    private int survivalPort;
    private int fall;
    private int rise;
    private int connectTimeoutMs;
    private double borderSize;
    private boolean kickIfSurvivalUp;
    private String kickMessage;

    private final AtomicBoolean survivalUp = new AtomicBoolean(true);
    private final AtomicInteger streak = new AtomicInteger(0);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        survivalHost = getConfig().getString("survival-host", "127.0.0.1");
        survivalPort = getConfig().getInt("survival-port", 30004);
        fall = Math.max(1, getConfig().getInt("fall", 3));
        rise = Math.max(1, getConfig().getInt("rise", 2));
        connectTimeoutMs = Math.max(200, getConfig().getInt("connect-timeout-ms", 1500));
        borderSize = getConfig().getDouble("worldborder-size", 2048);
        kickIfSurvivalUp = getConfig().getBoolean("kick-if-survival-up", true);
        kickMessage = getConfig().getString("kick-message", "Survival is back. Reconnect to lawlessmc.com");

        Bukkit.getPluginManager().registerEvents(this, this);
        int interval = Math.max(20, getConfig().getInt("check-interval-ticks", 40));
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::probeSurvival, 20L, interval);
        Bukkit.getScheduler().runTask(this, this::applyBorders);
        getLogger().info("SpawnFence: border=" + (int) borderSize + " survival=" + survivalHost + ":" + survivalPort);
    }

    private void applyBorders() {
        for (World world : Bukkit.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(borderSize);
            border.setDamageAmount(0.2);
            border.setDamageBuffer(2);
            border.setWarningDistance(16);
            world.setSpawnFlags(true, true);
        }
    }

    private void probeSurvival() {
        boolean up = tcpUp(survivalHost, survivalPort, connectTimeoutMs);
        if (up == survivalUp.get()) {
            streak.set(0);
            return;
        }
        int n = streak.incrementAndGet();
        int need = up ? rise : fall;
        if (n < need) {
            return;
        }
        streak.set(0);
        survivalUp.set(up);
        getLogger().info("survival " + survivalHost + ":" + survivalPort + " is now " + (up ? "UP" : "DOWN"));
        if (up && kickIfSurvivalUp) {
            Bukkit.getScheduler().runTask(this, this::kickAll);
        }
    }

    private void kickAll() {
        Component msg = Component.text(kickMessage);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.kick(msg);
        }
    }

    private static boolean tcpUp(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean outside(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return true;
        }
        double half = borderSize / 2.0;
        return Math.abs(loc.getX()) > half || Math.abs(loc.getZ()) > half;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        applyBorders();
        if (kickIfSurvivalUp && survivalUp.get()) {
            event.getPlayer().kick(Component.text(kickMessage));
            return;
        }
        if (outside(event.getPlayer().getLocation())) {
            World w = event.getPlayer().getWorld();
            event.getPlayer().teleport(new Location(w, 0.5, w.getHighestBlockYAt(0, 0) + 1, 0.5));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (outside(event.getTo())) {
            event.setCancelled(true);
        }
    }
}
