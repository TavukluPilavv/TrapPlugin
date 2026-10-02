package com.craftaxo.trapplugin;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main extends JavaPlugin implements CommandExecutor, Listener {

    private static Economy econ = null;
    private final HashMap<String, String> invites = new HashMap<>();
    private final Set<String> sellConfirmations = new HashSet<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault veya Ekonomi eklentisi bulunamadi!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        if (getCommand("trap") != null) {
            getCommand("trap").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("CraftAxo Trap Eklentisi Tam Sürüm Aktif!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ";" + chunk.getX() + ";" + chunk.getZ();
    }

    // --- KOMUT YÖNETİMİ ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Bu komutu sadece oyuncular kullanabilir!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "claim":
                handleClaim(player);
                break;
            case "invite":
                if (args.length < 2) player.sendMessage("§cKullanim: §f/trap invite <oyuncu>");
                else handleInvite(player, args[1]);
                break;
            case "kabul":
                handleAccept(player);
                break;
            case "satısakoy":
            case "satisakoy":
                if (args.length > 1 && args[1].equalsIgnoreCase("onay")) {
                    handleSellConfirm(player);
                } else {
                    handleSellRequest(player);
                }
                break;
            case "fly":
                handleFly(player);
                break;
            case "deposit":
                if (args.length < 2) player.sendMessage("§cKullanim: §f/trap deposit <miktar>");
                else handleDeposit(player, args[1]);
                break;
            case "withdraw":
                if (args.length < 2) player.sendMessage("§cKullanim: §f/trap withdraw <miktar>");
                else handleWithdraw(player, args[1]);
                break;
            case "kick":
                if (args.length < 2) player.sendMessage("§cKullanim: §f/trap kick <oyuncu>");
                else handleKick(player, args[1]);
                break;
            case "pvp":
                if (args.length < 2) player.sendMessage("§cKullanim: §f/trap pvp <ac/kapat>");
                else handlePvP(player, args[1]);
                break;
            case "izin":
                if (args.length < 4) {
                    player.sendMessage("§cKullanim: §f/trap izin <oyuncu> <yetki> <ac/kapa>");
                    player.sendMessage("§7Yetkiler: §fblokkoy, blokkir, cit, fly, withdraw");
                } else {
                    handleIzin(player, args[1], args[2], args[3]);
                }
                break;
            case "yetki":
                if (args.length < 2) player.sendMessage("§cKullanim: §f/trap yetki <oyuncu>");
                else handleYetkiList(player, args[1]);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§e--- TRAP SISTEMI KOMUTLARI ---");
        player.sendMessage("§f/trap claim §7- Bulundugun chunku 60k'ya alir.");
        player.sendMessage("§f/trap invite <oyuncu> §7- Trap'e davet eder.");
        player.sendMessage("§f/trap kabul §7- Gelen daveti kabul eder.");
        player.sendMessage("§f/trap satısakoy §7- Trapi 60k'ya satisa koyar.");
        player.sendMessage("§f/trap fly §7- Trap icinde ucmani saglar.");
        player.sendMessage("§f/trap deposit/withdraw <miktar> §7- Kasayi yonetir.");
        player.sendMessage("§f/trap kick <oyuncu> §7- Oyuncuyu trapden atar.");
        player.sendMessage("§f/trap pvp <ac/kapat> §7- PvP durumunu degistirir.");
        player.sendMessage("§f/trap izin <oyuncu> <yetki> <ac/kapa> §7- Özel izin verir.");
        player.sendMessage("§f/trap yetki <oyuncu> §7- Oyuncunun izinlerini gosterir.");
    }

    // --- 1. CLAIM & SATIŞ ---
    private void handleClaim(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (config.contains("traps." + key)) {
            player.sendMessage("§cBu chunk zaten bir trap!");
            return;
        }

        double cost = 60000.0;
        if (econ.getBalance(player) < cost) {
            player.sendMessage("§cTrap claim etmek için §e60.000$ §cgerekli!");
            return;
        }

        econ.withdrawPlayer(player, cost);
        config.set("traps." + key + ".owner", player.getUniqueId().toString());
        config.set("traps." + key + ".bank", 0.0);
        config.set("traps." + key + ".pvp", true);
        saveConfig();

        player.sendMessage("§a[Trap] §fBulundugun chunk §e60.000$ §fkarşiliginda senin oldu!");
    }

    private void handleSellRequest(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage("§cSadece kendi trapini satisa koyabilirsin!");
            return;
        }

        sellConfirmations.add(player.getUniqueId().toString());

        TextComponent msg = new TextComponent("§a[Trap] §fTrap'inizi §e60.000$ §ffiyatla satmak üzeresiniz. ");
        TextComponent button = new TextComponent("§c§l[ONAYLAMAK İÇİN TIKLAYIN]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trap satısakoy onay"));
        msg.addExtra(button);

        player.spigot().sendMessage(msg);
        player.sendMessage("§7(Veya komutla: §f/trap satısakoy onay§7)");
    }

    private void handleSellConfirm(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!sellConfirmations.contains(uuid)) {
            player.sendMessage("§cAktif bir satis talebiniz bulunmuyor. Önce §f/trap satısakoy §cyazin.");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(uuid)) {
            player.sendMessage("§cSatis yapilamadi! Bu trap size ait degil.");
            sellConfirmations.remove(uuid);
            return;
        }

        config.set("traps." + key, null);
        saveConfig();
        econ.depositPlayer(player, 60000.0);
        sellConfirmations.remove(uuid);

        player.sendMessage("§a[Trap] §fTrap'iniz satildi ve hesabiniza §e60.000$ §faktarildi.");
    }

    // --- 2. DAVET & KABUL & KICK ---
    private void handleInvite(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage("§cSadece sahibi oldugun trap'e oyuncu davet edebilirsin!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cOyuncu bulunamadi!");
            return;
        }

        invites.put(target.getUniqueId().toString(), key);
        player.sendMessage("§a[Trap] §e" + target.getName() + " §foyuncusuna davet gonderildi.");

        TextComponent msg = new TextComponent("§a[Trap] §e" + player.getName() + " §fsizi trapine davet etti! ");
        TextComponent button = new TextComponent("§e§l[KABUL ET]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trap kabul"));
        msg.addExtra(button);

        target.spigot().sendMessage(msg);
        target.sendMessage("§7(Veya komutla: §f/trap kabul§7)");
    }

    private void handleAccept(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!invites.containsKey(uuid)) {
            player.sendMessage("§cBekleyen bir trap davetiniz yok!");
            return;
        }

        String key = invites.remove(uuid);
        FileConfiguration config = getConfig();

        List<String> members = config.getStringList("traps." + key + ".members");
        if (!members.contains(uuid)) {
            members.add(uuid);
            config.set("traps." + key + ".members", members);
            saveConfig();
        }

        player.sendMessage("§a[Trap] §fDavet kabul edildi! Artik bu trap'e uyesiniz.");
    }

    private void handleKick(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage("§cSadece trap sahibi üye atabilir!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        String targetUUID = target != null ? target.getUniqueId().toString() : Bukkit.getOfflinePlayer(targetName).getUniqueId().toString();

        List<String> members = config.getStringList("traps." + key + ".members");
        if (members.contains(targetUUID)) {
            members.remove(targetUUID);
            config.set("traps." + key + ".members", members);
            config.set("traps." + key + ".permissions." + targetUUID, null); // İzinlerini de sil
            saveConfig();
            player.sendMessage("§a[Trap] §e" + targetName + " §ftrapden atildi.");
            if (target != null && target.isOnline()) target.sendMessage("§c[Trap] " + player.getName() + " sizi trapden atti.");
        } else {
            player.sendMessage("§cBu oyuncu trap uyesi degil!");
        }
    }

    // --- 3. İZİN VE YETKİ SİSTEMİ ---
    private void handleIzin(Player player, String targetName, String perm, String state) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage("§cSadece trap sahibi izinleri degistirebilir!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cOyuncu bulunamadi!");
            return;
        }

        String targetUUID = target.getUniqueId().toString();
        List<String> members = config.getStringList("traps." + key + ".members");

        if (!members.contains(targetUUID)) {
            player.sendMessage("§cBu oyuncu trap uyesi degil! Önce davet edin.");
            return;
        }

        String permName = perm.toLowerCase();
        if (!permName.equals("blokkoy") && !permName.equals("blokkir") && !permName.equals("cit") && !permName.equals("fly") && !permName.equals("withdraw")) {
            player.sendMessage("§cGecerli yetkiler: §fblokkoy, blokkir, cit, fly, withdraw");
            return;
        }

        boolean enable = state.equalsIgnoreCase("ac") || state.equalsIgnoreCase("aç");
        config.set("traps." + key + ".permissions." + targetUUID + "." + permName, enable);
        saveConfig();

        player.sendMessage("§a[Trap] §e" + target.getName() + " §ficin §e" + permName + " §fyetkisi: " + (enable ? "§aAÇIK" : "§cKAPALI"));
        target.sendMessage("§a[Trap] §fBu trapdeki §e" + permName + " §fyetkiniz: " + (enable ? "§aAÇIK" : "§cKAPALI"));
    }

    private void handleYetkiList(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key)) {
            player.sendMessage("§cBurasi bir trap alani degil!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cOyuncu bulunamadi!");
            return;
        }

        String targetUUID = target.getUniqueId().toString();
        player.sendMessage("§e--- " + target.getName() + " YETKILERI ---");
        player.sendMessage("§fBlok Koyma: " + getPermStatus(config, key, targetUUID, "blokkoy"));
        player.sendMessage("§fBlok Kirma: " + getPermStatus(config, key, targetUUID, "blokkir"));
        player.sendMessage("§fÇit/Kapi Acma: " + getPermStatus(config, key, targetUUID, "cit"));
        player.sendMessage("§fUçma (Fly): " + getPermStatus(config, key, targetUUID, "fly"));
        player.sendMessage("§fPara Cekme (Withdraw): " + getPermStatus(config, key, targetUUID, "withdraw"));
    }

    private String getPermStatus(FileConfiguration config, String key, String uuid, String perm) {
        boolean hasPerm = config.getBoolean("traps." + key + ".permissions." + uuid + "." + perm, false);
        return hasPerm ? "§aAÇIK" : "§cKAPALI";
    }

    private boolean hasSpecificPerm(Player player, Chunk chunk, String perm) {
        if (player.isOp()) return true;

        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key)) return true;

        String ownerUUID = config.getString("traps." + key + ".owner");
        if (player.getUniqueId().toString().equals(ownerUUID)) return true;

        String uuid = player.getUniqueId().toString();
        List<String> members = config.getStringList("traps." + key + ".members");
        if (!members.contains(uuid)) return false;

        return config.getBoolean("traps." + key + ".permissions." + uuid + "." + perm, false);
    }

    // --- 4. FLY & PVP & KASA ---
    private void handleFly(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        if (hasSpecificPerm(player, chunk, "fly")) {
            boolean canFly = !player.getAllowFlight();
            player.setAllowFlight(canFly);
            player.sendMessage("§a[Trap] §fUçma modu: " + (canFly ? "§aAÇIK" : "§cKAPALI"));
        } else {
            player.sendMessage("§cBu trapde fly kullanma yetkiniz yok!");
        }
    }

    private void handlePvP(Player player, String status) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage("§cSadece trap sahibi PvP modunu degistirebilir!");
            return;
        }

        boolean pvpState = status.equalsIgnoreCase("ac") || status.equalsIgnoreCase("aç");
        config.set("traps." + key + ".pvp", pvpState);
        saveConfig();

        player.sendMessage("§a[Trap] §fBu trap içinde PvP: " + (pvpState ? "§aAÇIK" : "§cKAPALI"));
    }

    private void handleDeposit(Player player, String amountStr) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key)) {
            player.sendMessage("§cBurasi bir trap alani degil!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();

            if (econ.getBalance(player) < amount) {
                player.sendMessage("§cYeterli paraniz yok!");
                return;
            }

            econ.withdrawPlayer(player, amount);
            double currentBank = config.getDouble("traps." + key + ".bank", 0.0);
            config.set("traps." + key + ".bank", currentBank + amount);
            saveConfig();

            player.sendMessage("§a[Trap] §fKasaya §e" + amount + "$ §fyatırıldı. Yeni Bakiye: §e" + (currentBank + amount) + "$");
        } catch (NumberFormatException e) {
            player.sendMessage("§cGecerli bir miktar girin!");
        }
    }

    private void handleWithdraw(Player player, String amountStr) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key)) {
            player.sendMessage("§cBurasi bir trap alani degil!");
            return;
        }

        if (!hasSpecificPerm(player, chunk, "withdraw")) {
            player.sendMessage("§cBu trap kasasindan para çekme yetkiniz yok!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();

            double currentBank = config.getDouble("traps." + key + ".bank", 0.0);
            if (currentBank < amount) {
                player.sendMessage("§cKasada yeterli para yok! Bakiye: §e" + currentBank + "$");
                return;
            }

            config.set("traps." + key + ".bank", currentBank - amount);
            saveConfig();
            econ.depositPlayer(player, amount);

            player.sendMessage("§a[Trap] §fKasadan §e" + amount + "$ §fçekildi. Kalan: §e" + (currentBank - amount) + "$");
        } catch (NumberFormatException e) {
            player.sendMessage("§cGecerli bir miktar girin!");
        }
    }

    // --- ETKİNLİK DİNLEYİCİLERİ ---
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Chunk chunk = event.getEntity().getLocation().getChunk();
            String key = getChunkKey(chunk);
            FileConfiguration config = getConfig();

            if (config.contains("traps." + key)) {
                boolean pvpOpen = config.getBoolean("traps." + key + ".pvp", true);
                if (!pvpOpen) {
                    event.setCancelled(true);
                    attacker.sendMessage("§cbu trapda pvp kapali");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isFlying() && !player.isOp()) {
            Chunk fromChunk = event.getFrom().getChunk();
            Chunk toChunk = event.getTo().getChunk();

            if (!fromChunk.equals(toChunk)) {
                if (!hasSpecificPerm(player, toChunk, "fly")) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                    player.sendMessage("§c[Trap] Yetkiniz olmayan bir alana gectiginiz icin ucma kapatildi!");
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!hasSpecificPerm(event.getPlayer(), event.getBlock().getChunk(), "blokkir")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cBu trapde blok kırma yetkiniz yok!");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!hasSpecificPerm(event.getPlayer(), event.getBlock().getChunk(), "blokkoy")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cBu trapde blok koyma yetkiniz yok!");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material type = clicked.getType();
        boolean isChest = type.name().contains("CHEST") || type.name().contains("SHULKER");
        boolean isGate = type.name().contains("FENCE") || type.name().contains("DOOR") || type.name().contains("TRAPDOOR");

        if (isChest || isGate) {
            if (!hasSpecificPerm(event.getPlayer(), clicked.getChunk(), "cit")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cBu trapde kapı, çit veya sandık açma yetkiniz yok!");
            }
        }
    }
}
