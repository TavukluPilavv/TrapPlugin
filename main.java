package com.craftaxo.trapplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class Main extends JavaPlugin implements CommandExecutor, Listener {

    private static Economy econ = null;

    @Override
    public void onEnable() {
        // Vault Ekonomi Bağlantısı
        if (!setupEconomy()) {
            getLogger().severe("Vault veya bir Ekonomi eklentisi (EssentialsX vb.) bulunamadi! Eklenti kapatiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Konfigürasyon kayıt ayarları
        saveDefaultConfig();

        // Komut ve Etkinlik Dinleyicilerini Kaydetme
        if (getCommand("trap") != null) {
            getCommand("trap").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("CraftAxo Trap Eklentisi Aktif Edildi!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    // --- KOMUT İŞLEYİCİSİ ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Bu komutu sadece oyuncular kullanabilir!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
            handleClaim(player);
            return true;
        }

        player.sendMessage("§cKullanim: §f/trap claim");
        return true;
    }

    // --- /TRAP CLAIM MANTIĞI ---
    private void handleClaim(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String chunkKey = chunk.getWorld().getName() + ";" + chunk.getX() + ";" + chunk.getZ();

        FileConfiguration config = getConfig();

        // Chunk zaten alınmış mı kontrol et
        if (config.contains("traps." + chunkKey)) {
            player.sendMessage("§cBu chunk zaten bir trap olarak sahiplenilmis!");
            return;
        }

        // Bakiye kontrolü (60.000$)
        double cost = 60000.0;
        if (econ.getBalance(player) < cost) {
            player.sendMessage("§cTrap claim edebilmek icin §e60.000$ §cgerekli!");
            return;
        }

        // Parayı kes ve Chunk'ı kaydet
        econ.withdrawPlayer(player, cost);
        config.set("traps." + chunkKey + ".owner", player.getUniqueId().toString());
        saveConfig();

        player.sendMessage("§a[Trap] §fBulundugun chunk §e60.000$ §fkarşiliginda senin trap'in oldu!");
    }

    // --- TRAP KORUMA ETKİNLİKLERİ ---
    
    // Chunk sahibini veya OP durumunu kontrol eden yardımcı metod
    private boolean canInteract(Player player, Chunk chunk) {
        if (player.isOp()) return true; // OP olanlar sınırsız erişir

        String chunkKey = chunk.getWorld().getName() + ";" + chunk.getX() + ";" + chunk.getZ();
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + chunkKey)) {
            return true; // Trap değilse herkes erişebilir
        }

        String ownerUUID = config.getString("traps." + chunkKey + ".owner");
        return player.getUniqueId().toString().equals(ownerUUID);
    }

    // 1. Blok Kırma Engeli
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canInteract(event.getPlayer(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cBu trap senin degil! Blok kıramazsin.");
        }
    }

    // 2. Blok Koyma Engeli
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canInteract(event.getPlayer(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cBu trap senin degil! Blok koyamazsin.");
        }
    }

    // 3. Sandık / Çit / Kapı Etkileşim Engeli
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material type = clicked.getType();
        boolean isChest = type.name().contains("CHEST") || type.name().contains("SHULKER");
        boolean isGate = type.name().contains("FENCE_GATE") || type.name().contains("DOOR") || type.name().contains("TRAPDOOR");

        if (isChest || isGate) {
            if (!canInteract(event.getPlayer(), clicked.getChunk())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cBu trap sana ait degil! Burada kapı veya sandık acamazsin.");
            }
        }
    }
}

