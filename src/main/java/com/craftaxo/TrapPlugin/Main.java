package com.craftaxo.trapplugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Main extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private static Economy econ = null;
    private final String PREFIX = "§b[AxoCrafT] §f";
    private final HashMap<String, String> invites = new HashMap<>();
    private final Set<String> sellConfirmations = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupEconomy();

        if (getCommand("trap") != null) {
            getCommand("trap").setExecutor(this);
            getCommand("trap").setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("AxoCrafT Trap Eklentisi Tam Surum Aktif!");
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

    private boolean hasOwnedTrap(Player player) {
        FileConfiguration config = getConfig();
        if (!config.contains("traps")) return false;
        String uuid = player.getUniqueId().toString();

        for (String key : config.getConfigurationSection("traps").getKeys(false)) {
            if (uuid.equals(config.getString("traps." + key + ".owner"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "claim", "list", "menu", "invite", "kabul", "reddet", "leave",
                    "trust", "satısakoy", "fly", "setspawn", "spawn", "deposit",
                    "withdraw", "kick", "pvp", "izin", "yetki"
            ));
            if (sender.isOp()) completions.add("create");
            return completions;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Bu komutu sadece oyuncular kullanabilir!");
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
            case "create":
                handleCreate(player);
                break;
            case "list":
            case "menu":
                openTrapMenu(player);
                break;
            case "invite":
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trap invite <oyuncu>");
                else handleInvite(player, args[1]);
                break;
            case "kabul":
                handleAccept(player);
                break;
            case "reddet":
                handleReject(player);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "trust":
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trap trust <oyuncu>");
                else handleTrust(player, args[1]);
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
            case "setspawn":
                handleSetSpawn(player);
                break;
            case "spawn":
                handleSpawn(player);
                break;
            case "deposit":
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trap deposit <miktar>");
                else handleDeposit(player, args[1]);
                break;
            case "withdraw":
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trap withdraw <miktar>");
                else handleWithdraw(player, args[1]);
                break;
            case "kick":
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trap kick <oyuncu>");
                else handleKick(player, args[1]);
                break;
            case "pvp":
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trap pvp <ac/kapat>");
                else handlePvP(player, args[1]);
                break;
            case "izin":
                if (args.length < 4) {
                    player.sendMessage(PREFIX + "§cKullanım: §b/trap izin <oyuncu> <yetki> <ac/kapa>");
                    player.sendMessage("§7Yetkiler: §bblokkoy, blokkir, cit, fly, withdraw");
                } else {
                    handleIzin(player, args[1], args[2], args[3]);
                }
                break;
            case "yetki":
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trap yetki <oyuncu>");
                else handleYetkiList(player, args[1]);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§b--- TRAP SİSTEMİ KOMUTLARI ---");
        player.sendMessage("§b/trap claim §7- Bulundugun chunku alir.");
        player.sendMessage("§b/trap list §7- Trap menusunu acar.");
        player.sendMessage("§b/trap invite <oyuncu> §7- Trap'e davet eder.");
        player.sendMessage("§b/trap kabul §7- Gelen daveti kabul eder.");
        player.sendMessage("§b/trap reddet §7- Gelen daveti reddeder.");
        player.sendMessage("§b/trap leave §7- Uyesi oldugun trapden ayrilirsin.");
        player.sendMessage("§b/trap trust <oyuncu> §7- Oyuncuya blok/cit yetkisi verir.");
        player.sendMessage("§b/trap satısakoy §7- Trapi satisa koyar.");
        player.sendMessage("§b/trap fly §7- Trap icinde ucmani saglar.");
        player.sendMessage("§b/trap setspawn §7- Trapin spawn noktasini ayarlar.");
        player.sendMessage("§b/trap spawn §7- Trap spawnina isinlar.");
        player.sendMessage("§b/trap deposit <miktar> §7- Kasaya para yatirir.");
        player.sendMessage("§b/trap withdraw <miktar> §7- Kasadan para ceker.");
        player.sendMessage("§b/trap kick <oyuncu> §7- Oyuncuyu trapden atar.");
        player.sendMessage("§b/trap pvp <ac/kapat> §7- PvP durumunu degistirir.");
        player.sendMessage("§b/trap izin <oyuncu> <yetki> <ac/kapa> §7- Ozel izin verir.");
        player.sendMessage("§b/trap yetki <oyuncu> §7- Oyuncunun izinlerini gosterir.");
        if (player.isOp()) {
            player.sendMessage("§c/trap create §7- OP Özel: Chunku ucretsiz satisa cikarir.");
        }
    }

    private void handleCreate(Player player) {
        if (!player.isOp()) {
            player.sendMessage(PREFIX + "§cBu komutu sadece OP yetkisi olanlar kullanabilir!");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (config.contains("traps." + key)) {
            player.sendMessage(PREFIX + "§cBu chunk zaten bir trap olarak kayitli!");
            return;
        }

        int count = config.getInt("trap-count", 0) + 1;
        config.set("trap-count", count);

        String trapName = "Trap #" + count;
        config.set("traps." + key + ".name", trapName);
        config.set("traps." + key + ".owner", "NONE");
        config.set("traps." + key + ".health", 30000);
        config.set("traps." + key + ".max-health", 30000);
        config.set("traps." + key + ".bank", 0.0);
        config.set("traps." + key + ".pvp", true);
        saveConfig();

        player.sendMessage(PREFIX + "§a" + trapName + " başarıyla oluşturuldu ve satışa çıkarıldı!");
    }

    private void handleClaim(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (hasOwnedTrap(player)) {
            player.sendMessage(PREFIX + "§cEn fazla 1 adet trap sahibi olabilirsiniz!");
            return;
        }

        double cost = config.getDouble("trap-price", 60000.0);

        if (config.contains("traps." + key)) {
            String owner = config.getString("traps." + key + ".owner");
            if (!owner.equals("NONE")) {
                player.sendMessage(PREFIX + "§cBu trap zaten başkasına ait!");
                return;
            }
        }

        if (econ != null) {
            if (econ.getBalance(player) < cost) {
                player.sendMessage(PREFIX + "§cTrap almak için §e" + cost + "$ §cgerekli!");
                return;
            }
            econ.withdrawPlayer(player, cost);
        }

        if (!config.contains("traps." + key)) {
            int count = config.getInt("trap-count", 0) + 1;
            config.set("trap-count", count);
            config.set("traps." + key + ".name", "Trap #" + count);
        }

        config.set("traps." + key + ".owner", player.getUniqueId().toString());
        config.set("traps." + key + ".owner-name", player.getName());
        config.set("traps." + key + ".health", 30000);
        config.set("traps." + key + ".max-health", 30000);
        config.set("traps." + key + ".bank", 0.0);
        config.set("traps." + key + ".pvp", true);
        saveConfig();

        player.sendMessage(PREFIX + "§aBulunduğun chunk §e" + cost + "$ §akarşılığında başarıyla senin oldu!");
    }

    private void openTrapMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Trap Listesi");
        FileConfiguration config = getConfig();

        if (config.contains("traps")) {
            int slot = 0;
            for (String key : config.getConfigurationSection("traps").getKeys(false)) {
                if (slot >= 54) break;

                String name = config.getString("traps." + key + ".name", "Trap");
                String owner = config.getString("traps." + key + ".owner", "NONE");

                ItemStack item;
                ItemMeta meta;

                if (owner.equals("NONE")) {
                    item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                    meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§a" + name + " - [SATIN ALINABİLİR]");
                        meta.setLore(Arrays.asList("§7Durum: §aBoş (Yeşil)", "§7Fiyat: §e60,000$", "§eTıklayarak satın alabilirsiniz."));
                    }
                } else {
                    item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    meta = item.getItemMeta();
                    if (meta != null) {
                        String ownerName = config.getString("traps." + key + ".owner-name", Bukkit.getOfflinePlayer(UUID.fromString(owner)).getName());
                        meta.setDisplayName("§c" + name + " - [SATILDI]");
                        meta.setLore(Arrays.asList("§7Durum: §cDolu (Kırmızı)", "§7Sahibi: §f" + ownerName));
                    }
                }

                if (meta != null) item.setItemMeta(meta);
                inv.setItem(slot++, item);
            }
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("§8Trap Listesi")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

            Player player = (Player) event.getWhoClicked();
            String name = event.getCurrentItem().getItemMeta().getDisplayName();

            if (name.contains("SATIN ALINABİLİR")) {
                player.closeInventory();
                player.sendMessage(PREFIX + "§eSatın almak için ilgili trap bölgesine gidip §b/trap claim §eyazabilirsiniz.");
            }
        }
    }

    private void handleSetSpawn(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece sahibi olduğun trap için spawn ayarlayabilirsin!");
            return;
        }

        Location loc = player.getLocation();
        config.set("traps." + key + ".spawn.x", loc.getX());
        config.set("traps." + key + ".spawn.y", loc.getY());
        config.set("traps." + key + ".spawn.z", loc.getZ());
        config.set("traps." + key + ".spawn.yaw", loc.getYaw());
        config.set("traps." + key + ".spawn.pitch", loc.getPitch());
        saveConfig();

        player.sendMessage(PREFIX + "§aTrap spawn noktası başarıyla ayarlandı!");
    }

    private void handleSpawn(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key + ".spawn")) {
            player.sendMessage(PREFIX + "§cBu trap için henüz spawn noktası ayarlanmamış!");
            return;
        }

        double x = config.getDouble("traps." + key + ".spawn.x");
        double y = config.getDouble("traps." + key + ".spawn.y");
        double z = config.getDouble("traps." + key + ".spawn.z");
        float yaw = (float) config.getDouble("traps." + key + ".spawn.yaw");
        float pitch = (float) config.getDouble("traps." + key + ".spawn.pitch");

        Location loc = new Location(player.getWorld(), x, y, z, yaw, pitch);
        player.teleport(loc);
        player.sendMessage(PREFIX + "§aTrap spawn noktasına ışınlandınız.");
    }

    private void handleSellRequest(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece kendi trapini satışa koyabilirsin!");
            return;
        }

        sellConfirmations.add(player.getUniqueId().toString());
        double price = config.getDouble("trap-price", 60000.0);

        TextComponent msg = new TextComponent(PREFIX + "§fTrap'inizi §e" + price + "$ §ffiyatla satmak üzeresiniz. ");
        TextComponent button = new TextComponent("§c§l[ONAYLAMAK İÇİN TIKLAYIN]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trap satısakoy onay"));
        msg.addExtra(button);

        player.spigot().sendMessage(msg);
    }

    private void handleSellConfirm(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!sellConfirmations.contains(uuid)) {
            player.sendMessage(PREFIX + "§cAktif satış talebiniz yok. Önce §b/trap satısakoy §cyazın.");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(uuid)) {
            player.sendMessage(PREFIX + "§cBu trap size ait değil!");
            sellConfirmations.remove(uuid);
            return;
        }

        double price = config.getDouble("trap-price", 60000.0);
        config.set("traps." + key + ".owner", "NONE");
        config.set("traps." + key + ".owner-name", null);
        saveConfig();

        if (econ != null) econ.depositPlayer(player, price);
        sellConfirmations.remove(uuid);

        player.sendMessage(PREFIX + "AxoCrafT trap başarıyla satıldı.");
    }

    private void handleInvite(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece sahibi olduğun trap'e oyuncu davet edebilirsin!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(PREFIX + "§cOyuncu bulunamadı!");
            return;
        }

        invites.put(target.getUniqueId().toString(), key);
        player.sendMessage(PREFIX + "§e" + target.getName() + " §foyuncusuna davet gönderildi.");

        TextComponent msg = new TextComponent(PREFIX + "§e" + player.getName() + " §fsizi trapine davet etti! ");
        TextComponent buttonKabul = new TextComponent("§a§l[KABUL ET] ");
        buttonKabul.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trap kabul"));

        TextComponent buttonRed = new TextComponent("§c§l[REDDET]");
        buttonRed.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trap reddet"));

        msg.addExtra(buttonKabul);
        msg.addExtra(buttonRed);

        target.spigot().sendMessage(msg);
    }

    private void handleAccept(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!invites.containsKey(uuid)) {
            player.sendMessage(PREFIX + "§cBekleyen trap davetiniz yok!");
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

        player.sendMessage(PREFIX + "§aDavet kabul edildi! Artık bu trap'e üyesiniz.");
    }

    private void handleReject(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!invites.containsKey(uuid)) {
            player.sendMessage(PREFIX + "§cReddedilecek bekleyen trap davetiniz yok!");
            return;
        }

        invites.remove(uuid);
        player.sendMessage(PREFIX + "§cTrap davetini reddettiniz.");
    }

    private void handleLeave(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key)) {
            player.sendMessage(PREFIX + "§cBulunduğunuz alanda bir trap yok!");
            return;
        }

        String uuid = player.getUniqueId().toString();

        if (uuid.equals(config.getString("traps." + key + ".owner"))) {
            player.sendMessage(PREFIX + "§cTrap sahibi klan/trap'den leave atamaz! Satış yapabilirsiniz.");
            return;
        }

        List<String> members = config.getStringList("traps." + key + ".members");
        if (members.contains(uuid)) {
            members.remove(uuid);
            config.set("traps." + key + ".members", members);
            config.set("traps." + key + ".permissions." + uuid, null);
            saveConfig();
            player.sendMessage(PREFIX + "§aTrap'ten ayrıldınız.");
        } else {
            player.sendMessage(PREFIX + "§cBu trap'in üyesi değilsiniz!");
        }
    }

    private void handleTrust(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece trap sahibi trust verebilir!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(PREFIX + "§cOyuncu bulunamadı!");
            return;
        }

        String targetUUID = target.getUniqueId().toString();
        List<String> members = config.getStringList("traps." + key + ".members");
        if (!members.contains(targetUUID)) {
            members.add(targetUUID);
            config.set("traps." + key + ".members", members);
        }

        config.set("traps." + key + ".permissions." + targetUUID + ".blokkoy", true);
        config.set("traps." + key + ".permissions." + targetUUID + ".blokkir", true);
        config.set("traps." + key + ".permissions." + targetUUID + ".cit", true);
        saveConfig();

        player.sendMessage(PREFIX + "§a" + target.getName() + " oyuncusuna blok kırma, koyma ve çit açma yetkisi (Trust) verildi!");
    }

    private void handleKick(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece trap sahibi üye atabilir!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        String targetUUID = target != null ? target.getUniqueId().toString() : Bukkit.getOfflinePlayer(targetName).getUniqueId().toString();

        if (targetUUID.equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cKendinizi trap'ten atamazsınız!");
            return;
        }

        List<String> members = config.getStringList("traps." + key + ".members");
        if (members.contains(targetUUID)) {
            members.remove(targetUUID);
            config.set("traps." + key + ".members", members);
            config.set("traps." + key + ".permissions." + targetUUID, null);
            saveConfig();
            player.sendMessage(PREFIX + "§e" + targetName + " §ftrapden atıldı.");
        } else {
            player.sendMessage(PREFIX + "§cBu oyuncu trap üyesi değil!");
        }
    }

    private void handleIzin(Player player, String targetName, String perm, String state) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece trap sahibi izinleri değiştirebilir!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(PREFIX + "§cOyuncu bulunamadı!");
            return;
        }

        String targetUUID = target.getUniqueId().toString();
        List<String> members = config.getStringList("traps." + key + ".members");

        if (!members.contains(targetUUID)) {
            player.sendMessage(PREFIX + "§cBu oyuncu trap üyesi değil!");
            return;
        }

        String permName = perm.toLowerCase();
        boolean enable = state.equalsIgnoreCase("ac") || state.equalsIgnoreCase("aç");
        config.set("traps." + key + ".permissions." + targetUUID + "." + permName, enable);
        saveConfig();

        player.sendMessage(PREFIX + "§e" + target.getName() + " §fiçin §b" + permName + " §fyetkisi: " + (enable ? "§aAÇIK" : "§cKAPALI"));
    }

    private void handleYetkiList(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(PREFIX + "§cOyuncu bulunamadı!");
            return;
        }

        String targetUUID = target.getUniqueId().toString();
        player.sendMessage("§b--- " + target.getName() + " YETKİLERİ ---");
        player.sendMessage("§fBlok Koyma: " + getPermStatus(config, key, targetUUID, "blokkoy"));
        player.sendMessage("§fBlok Kırma: " + getPermStatus(config, key, targetUUID, "blokkir"));
        player.sendMessage("§fÇit/Kapı Açma: " + getPermStatus(config, key, targetUUID, "cit"));
        player.sendMessage("§fUçma (Fly): " + getPermStatus(config, key, targetUUID, "fly"));
        player.sendMessage("§fPara Çekme: " + getPermStatus(config, key, targetUUID, "withdraw"));
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
        if (ownerUUID.equals("NONE")) return true;
        if (player.getUniqueId().toString().equals(ownerUUID)) return true;

        String uuid = player.getUniqueId().toString();
        List<String> members = config.getStringList("traps." + key + ".members");
        if (!members.contains(uuid)) return false;

        return config.getBoolean("traps." + key + ".permissions." + uuid + "." + perm, false);
    }

    private void handleFly(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        if (hasSpecificPerm(player, chunk, "fly")) {
            boolean canFly = !player.getAllowFlight();
            player.setAllowFlight(canFly);
            player.sendMessage(PREFIX + "Uçma modu: " + (canFly ? "§aAÇIK" : "§cKAPALI"));
        } else {
            player.sendMessage(PREFIX + "§cBu trapde fly kullanma yetkiniz yok!");
        }
    }

    private void handlePvP(Player player, String status) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece trap sahibi PvP modunu değiştirebilir!");
            return;
        }

        boolean pvpState = status.equalsIgnoreCase("ac") || status.equalsIgnoreCase("aç");
        config.set("traps." + key + ".pvp", pvpState);
        saveConfig();

        player.sendMessage(PREFIX + "Bu trap içinde PvP: " + (pvpState ? "§aAÇIK" : "§cKAPALI"));
    }

    private void handleDeposit(Player player, String amountStr) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();

            if (econ != null) {
                if (econ.getBalance(player) < amount) {
                    player.sendMessage(PREFIX + "§cYeterli paranız yok!");
                    return;
                }
                econ.withdrawPlayer(player, amount);
            }

            double currentBank = config.getDouble("traps." + key + ".bank", 0.0);
            config.set("traps." + key + ".bank", currentBank + amount);
            saveConfig();

            player.sendMessage(PREFIX + "Kasaya §e" + amount + "$ §fyatırıldı.");
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + "§cGeçerli bir miktar girin!");
        }
    }

    private void handleWithdraw(Player player, String amountStr) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (!hasSpecificPerm(player, chunk, "withdraw")) {
            player.sendMessage(PREFIX + "§cBu trap kasasından para çekme yetkiniz yok!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();

            double currentBank = config.getDouble("traps." + key + ".bank", 0.0);
            if (currentBank < amount) {
                player.sendMessage(PREFIX + "§cKasada yeterli para yok!");
                return;
            }

            config.set("traps." + key + ".bank", currentBank - amount);
            saveConfig();
            if (econ != null) econ.depositPlayer(player, amount);

            player.sendMessage(PREFIX + "Kasadan §e" + amount + "$ §fçekildi.");
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + "§cGeçerli bir miktar girin!");
        }
    }

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
                    attacker.sendMessage(PREFIX + "§cBu trapda pvp kapali");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Chunk toChunk = event.getTo().getChunk();
        Player player = event.getPlayer();
        String key = getChunkKey(toChunk);
        FileConfiguration config = getConfig();

        if (config.contains("traps." + key)) {
            String name = config.getString("traps." + key + ".name", "Trap");
            String owner = config.getString("traps." + key + ".owner", "NONE");

            String actionBarMsg;
            if (owner.equals("NONE")) {
                actionBarMsg = "§d[AxoCrafT] §e⚔ §a" + name + " §8| §cTrap Sahibi Yok";
            } else {
                String ownerName = config.getString("traps." + key + ".owner-name", Bukkit.getOfflinePlayer(UUID.fromString(owner)).getName());
                int hp = config.getInt("traps." + key + ".health", 30000);
                int maxHp = config.getInt("traps." + key + ".max-health", 30000);
                actionBarMsg = "§d[AxoCrafT] §e⚔ §a" + name + " §8| §aSahibi: §f" + ownerName + " §8| §aCan: §e" + hp + "/" + maxHp;
            }

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMsg));
        }

        if (!event.getFrom().getChunk().equals(toChunk)) {
            if (player.isFlying() && !player.isOp()) {
                if (!hasSpecificPerm(player, toChunk, "fly")) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                    player.sendMessage(PREFIX + "§cYetkiniz olmayan bir alana geçtiğiniz için uçma kapatıldı!");
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (config.contains("traps." + key)) {
            if (!hasSpecificPerm(event.getPlayer(), chunk, "blokkir")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(PREFIX + "§cBu trapde blok kırma yetkiniz yok!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (config.contains("traps." + key)) {
            if (!hasSpecificPerm(event.getPlayer(), chunk, "blokkoy")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(PREFIX + "§cBu trapde blok koyma yetkiniz yok!");
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Chunk chunk = clicked.getChunk();
        String key = getChunkKey(chunk);
        FileConfiguration config = getConfig();

        if (config.contains("traps." + key)) {
            Material type = clicked.getType();
            boolean isChest = type.name().contains("CHEST") || type.name().contains("SHULKER");
            boolean isGate = type.name().contains("FENCE") || type.name().contains("DOOR") || type.name().contains("TRAPDOOR");

            if (isChest || isGate) {
                if (!hasSpecificPerm(event.getPlayer(), chunk, "cit")) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(PREFIX + "§cBu trapde kapı, çit veya sandık açma yetkiniz yok!");
                }
            }
        }
    }
}
