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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
    private final String PREFIX = "§c[§eA§ax§bo§dT§er§aa§bp§c] §f";
    private final HashMap<String, String> invites = new HashMap<>();
    private final Set<String> sellConfirmations = new HashSet<>();
    private final HashMap<UUID, List<String>> lTrapSelections = new HashMap<>();
    private final HashMap<UUID, Double> pendingSellPrices = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupEconomy();

        if (getCommand("trap") != null) {
            getCommand("trap").setExecutor(this);
            getCommand("trap").setTabCompleter(this);
        }
        if (getCommand("trapadmin") != null) {
            getCommand("trapadmin").setExecutor(this);
            getCommand("trapadmin").setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("AxoTrap Eklentisi Tam Surum Guncellendi ve Aktif!");
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

    private int getNextAvailableTrapId() {
        FileConfiguration config = getConfig();
        if (!config.contains("traps")) return 1;

        Set<Integer> activeIds = new HashSet<>();
        for (String key : config.getConfigurationSection("traps").getKeys(false)) {
            if (config.contains("traps." + key + ".id")) {
                activeIds.add(config.getInt("traps." + key + ".id"));
            }
        }

        for (int id = 1; id <= 80; id++) {
            if (!activeIds.contains(id)) {
                return id;
            }
        }
        return -1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("trap")) {
            if (args.length == 1) {
                return Arrays.asList(
                        "claim", "list", "menu", "invite", "kabul", "reddet", "leave",
                        "trust", "satısakoy", "fly", "setspawn", "spawn", "deposit",
                        "withdraw", "kick", "pvp", "izin", "yetki"
                );
            }
        } else if (command.getName().equalsIgnoreCase("trapadmin")) {
            if (sender.hasPermission("trapplugin.trapadmin.create") || sender.isOp()) {
                if (args.length == 1) {
                    return Arrays.asList("create", "sil");
                } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                    return Arrays.asList("L", "kısım", "kisim");
                } else if (args.length == 3 && args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("L")) {
                    return Arrays.asList("iptal", "kısım");
                }
            }
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

        if (command.getName().equalsIgnoreCase("trapadmin")) {
            if (!player.hasPermission("trapplugin.trapadmin.create") && !player.isOp()) {
                player.sendMessage(PREFIX + "§cBunu yapmak için yetkiniz yok!");
                return true;
            }

            if (args.length == 0) {
                player.sendMessage(PREFIX + "§cKullanım: §b/trapadmin create [L] veya /trapadmin sil <id>");
                return true;
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("create")) {
                handleCreateCommand(player, args);
            } else if (sub.equals("sil")) {
                if (args.length < 2) player.sendMessage(PREFIX + "§cKullanım: §b/trapadmin sil <Trap ID>");
                else handleSil(player, args[1]);
            } else {
                player.sendMessage(PREFIX + "§cBöyle bir komut bulunamadı!");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("trap")) {
            if (args.length == 0) {
                sendHelp(player);
                return true;
            }

            String sub = args[0].toLowerCase();
            switch (sub) {
                case "claim":
                    handleClaim(player);
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
                        double price = 60000.0;
                        if (args.length > 1) {
                            try {
                                price = Double.parseDouble(args[1]);
                            } catch (NumberFormatException ignored) {}
                        }
                        handleSellRequest(player, price);
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
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§c--- §eA§ax§bo§dT§er§aa§bp §c---");
        player.sendMessage("§b/trap claim §7- Bulundugun chunku alir.");
        player.sendMessage("§b/trap list §7- Trap menusunu acar.");
        player.sendMessage("§b/trap invite <oyuncu> §7- Trap'e davet eder.");
        player.sendMessage("§b/trap kabul §7- Gelen daveti kabul eder.");
        player.sendMessage("§b/trap reddet §7- Gelen daveti reddeder.");
        player.sendMessage("§b/trap leave §7- Trapten ayrilir / satisa koyar.");
        player.sendMessage("§b/trap trust <oyuncu> §7- Oyuncuya yetki verir.");
        player.sendMessage("§b/trap satısakoy [fiyat] §7- Trapi belirledigin fiyata satisa koyar.");
        player.sendMessage("§b/trap fly §7- Trap icinde ucmani saglar.");
        player.sendMessage("§b/trap setspawn §7- Trapin spawn noktasini ayarlar.");
        player.sendMessage("§b/trap spawn §7- Nerede olursan ol trap spawnina isinlanir.");
        player.sendMessage("§b/trap deposit <miktar> §7- Kasaya para yatirir.");
        player.sendMessage("§b/trap withdraw <miktar> §7- Kasadan para ceker.");
        player.sendMessage("§b/trap kick <oyuncu> §7- Oyuncuyu trapden atar.");
        player.sendMessage("§b/trap pvp <ac/kapat> §7- PvP durumunu degistirir.");
        player.sendMessage("§b/trap izin <oyuncu> <yetki> <ac/kapa> §7- Ozel izin verir.");
        player.sendMessage("§b/trap yetki <oyuncu> §7- Oyuncunun izinlerini gosterir.");
        if (player.hasPermission("trapplugin.trapadmin.create") || player.isOp()) {
            player.sendMessage("§c/trapadmin create §7- Bulundugun chunku trap yapar.");
            player.sendMessage("§c/trapadmin create L §7- L-Trap olusturur.");
            player.sendMessage("§c/trapadmin sil <id> §7- Trap kaydını siler.");
        }
    }

    private void handleCreateCommand(Player player, String[] args) {
        Chunk currentChunk = player.getLocation().getChunk();
        String currentKey = getChunkKey(currentChunk);
        FileConfiguration config = getConfig();

        if (config.contains("traps." + currentKey)) {
            player.sendMessage(PREFIX + "§cBu trap zaten oluşturulmuş!");
            return;
        }

        int trapId = getNextAvailableTrapId();
        if (trapId == -1) {
            player.sendMessage(PREFIX + "§cMaksimum 80 trap sınırına ulaşıldı!");
            return;
        }

        if (args.length == 1) {
            String trapName = "Trap #" + trapId;
            config.set("traps." + currentKey + ".id", trapId);
            config.set("traps." + currentKey + ".name", trapName);
            config.set("traps." + currentKey + ".owner", "NONE");
            config.set("traps." + currentKey + ".health", 5000);
            config.set("traps." + currentKey + ".max-health", 5000);
            config.set("traps." + currentKey + ".bank", 0.0);
            config.set("traps." + currentKey + ".pvp", true);
            config.set("traps." + currentKey + ".for-sale", true);
            saveConfig();

            player.sendMessage(PREFIX + "§a" + trapName + " başarıyla oluşturuldu!");
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("L")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("iptal")) {
                lTrapSelections.remove(player.getUniqueId());
                player.sendMessage(PREFIX + "§cL-Trap seçim süreci iptal edildi!");
                return;
            }

            if (args.length == 2) {
                List<String> selections = new ArrayList<>();
                selections.add(currentKey);
                lTrapSelections.put(player.getUniqueId(), selections);
                player.sendMessage(PREFIX + "§aL-Trap Oluşturma Başlatıldı! Diğer chunk'a gidip §b/trapadmin create L kısım 2 kabul §fyazın.");
                return;
            }

            if (args.length >= 5 && (args[2].equalsIgnoreCase("kısım") || args[2].equalsIgnoreCase("kisim")) && args[4].equalsIgnoreCase("kabul")) {
                List<String> selections = lTrapSelections.get(player.getUniqueId());
                if (selections == null) {
                    player.sendMessage(PREFIX + "§cÖnce §b/trapadmin create L §cyazmalısınız!");
                    return;
                }

                String stage = args[3];
                if (stage.equals("2")) {
                    if (selections.contains(currentKey)) { player.sendMessage(PREFIX + "§cBu chunk'ı zaten seçtiniz!"); return; }
                    selections.add(currentKey);
                    player.sendMessage(PREFIX + "§aKısım 2 seçildi! Son chunk'a gidip §b/trapadmin create L kısım 3 kabul §fyazın.");
                } else if (stage.equals("3")) {
                    if (selections.contains(currentKey)) { player.sendMessage(PREFIX + "§cBu chunk'ı zaten seçtiniz!"); return; }
                    selections.add(currentKey);

                    String mainKey = selections.get(0);
                    String trapName = "Trap #" + trapId;

                    config.set("traps." + mainKey + ".id", trapId);
                    config.set("traps." + mainKey + ".name", trapName);
                    config.set("traps." + mainKey + ".owner", "NONE");
                    config.set("traps." + mainKey + ".health", 5000);
                    config.set("traps." + mainKey + ".max-health", 5000);
                    config.set("traps." + mainKey + ".bank", 0.0);
                    config.set("traps." + mainKey + ".pvp", true);
                    config.set("traps." + mainKey + ".for-sale", true);
                    config.set("traps." + mainKey + ".chunks", selections);

                    for (int i = 1; i < selections.size(); i++) {
                        config.set("traps." + selections.get(i) + ".parent", mainKey);
                    }

                    saveConfig();
                    lTrapSelections.remove(player.getUniqueId());
                    player.sendMessage(PREFIX + "§aL-Şeklindeki " + trapName + " başarıyla oluşturuldu!");
                }
            }
        }
    }

    private void handleSil(Player player, String trapIdentifier) {
        FileConfiguration config = getConfig();
        if (!config.contains("traps")) {
            player.sendMessage(PREFIX + "§cKayıtlı trap bulunamadı!");
            return;
        }

        String targetKey = null;
        for (String key : config.getConfigurationSection("traps").getKeys(false)) {
            String name = config.getString("traps." + key + ".name", "");
            int id = config.getInt("traps." + key + ".id", -1);
            if (key.equalsIgnoreCase(trapIdentifier) || name.equalsIgnoreCase(trapIdentifier) || name.equalsIgnoreCase("Trap #" + trapIdentifier) || String.valueOf(id).equals(trapIdentifier)) {
                targetKey = key;
                break;
            }
        }

        if (targetKey != null) {
            int trapId = config.getInt("traps." + targetKey + ".id");
            if (config.contains("traps." + targetKey + ".chunks")) {
                List<String> subChunks = config.getStringList("traps." + targetKey + ".chunks");
                for (String subKey : subChunks) { config.set("traps." + subKey, null); }
            }
            config.set("traps." + targetKey, null);
            saveConfig();
            player.sendMessage(PREFIX + "§aTrap #" + trapId + " tamamen silindi!");
        } else {
            player.sendMessage(PREFIX + "§cBelirtilen trap bulunamadı!");
        }
    }

    private String getMainKey(String key) {
        FileConfiguration config = getConfig();
        if (config.contains("traps." + key + ".parent")) {
            return config.getString("traps." + key + ".parent");
        }
        return key;
    }

    private void handleClaim(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key)) {
            player.sendMessage(PREFIX + "§cBurada satın alınabilir bir trap bulunmuyor!");
            return;
        }

        if (hasOwnedTrap(player)) {
            player.sendMessage(PREFIX + "§cEn fazla 1 adet trap sahibi olabilirsiniz!");
            return;
        }

        double cost = config.getDouble("traps." + key + ".custom-price", config.getDouble("trap-price", 60000.0));
        String oldOwnerUUID = config.getString("traps." + key + ".owner");

        if (!oldOwnerUUID.equals("NONE") && !config.getBoolean("traps." + key + ".for-sale", false)) {
            player.sendMessage(PREFIX + "§cBu trap başkasına ait ve satışta değil!");
            return;
        }

        if (econ != null && !player.isOp()) {
            if (econ.getBalance(player) < cost) {
                player.sendMessage(PREFIX + "§cTrap almak için §e" + cost + "$ §cgerekli!");
                return;
            }
            econ.withdrawPlayer(player, cost);

            if (!oldOwnerUUID.equals("NONE")) {
                Player oldOwner = Bukkit.getPlayer(UUID.fromString(oldOwnerUUID));
                if (oldOwner != null && oldOwner.isOnline()) {
                    econ.depositPlayer(oldOwner, cost);
                    oldOwner.sendMessage(PREFIX + "§aTrap'iniz " + player.getName() + " tarafından " + cost + "$ karşılığında satın alındı!");
                } else {
                    econ.depositPlayer(Bukkit.getOfflinePlayer(UUID.fromString(oldOwnerUUID)), cost);
                }
            }
        }

        config.set("traps." + key + ".owner", player.getUniqueId().toString());
        config.set("traps." + key + ".owner-name", player.getName());
        config.set("traps." + key + ".for-sale", false);
        config.set("traps." + key + ".custom-price", null);
        saveConfig();

        player.sendMessage(PREFIX + "§aBulunduğun trap §e" + cost + "$ §akarşılığında başarıyla senin oldu!");
    }

    private void openTrapMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Trap Listesi");
        FileConfiguration config = getConfig();

        if (config.contains("traps")) {
            Map<Integer, String> sortedTraps = new TreeMap<>();
            for (String key : config.getConfigurationSection("traps").getKeys(false)) {
                if (config.contains("traps." + key + ".parent")) continue;
                int id = config.getInt("traps." + key + ".id", 999);
                sortedTraps.put(id, key);
            }

            int slot = 0;
            for (Map.Entry<Integer, String> entry : sortedTraps.entrySet()) {
                if (slot >= 54) break;
                String key = entry.getValue();
                String name = config.getString("traps." + key + ".name", "Trap #" + entry.getKey());
                String owner = config.getString("traps." + key + ".owner", "NONE");
                boolean isForSale = config.getBoolean("traps." + key + ".for-sale", false);
                double price = config.getDouble("traps." + key + ".custom-price", 60000.0);

                ItemStack item = new ItemStack(Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (owner.equals("NONE") || isForSale) {
                        meta.setDisplayName("§a" + name + " - [SATIŞTA]");
                        meta.setLore(Arrays.asList("§7Durum: §aSatışta", "§7Fiyat: §e" + price + "$", "§e/trap claim yazarak alabilirsiniz."));
                    } else {
                        String ownerName = config.getString("traps." + key + ".owner-name", "Bilinmiyor");
                        meta.setDisplayName("§c" + name + " - [DOLU]");
                        meta.setLore(Arrays.asList("§7Durum: §cDolu", "§7Sahibi: §f" + ownerName));
                    }
                    item.setItemMeta(meta);
                }
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
            if (name.contains("SATIŞTA")) {
                player.closeInventory();
                player.sendMessage(PREFIX + "§eSatın almak için ilgili trap bölgesine gidip §b/trap claim §eyazabilirsiniz.");
            }
        }
    }

    private void handleSetSpawn(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece sahibi olduğun trap için spawn ayarlayabilirsin!");
            return;
        }

        Location loc = player.getLocation();
        config.set("traps." + key + ".spawn.world", loc.getWorld().getName());
        config.set("traps." + key + ".spawn.x", loc.getX());
        config.set("traps." + key + ".spawn.y", loc.getY());
        config.set("traps." + key + ".spawn.z", loc.getZ());
        config.set("traps." + key + ".spawn.yaw", loc.getYaw());
        config.set("traps." + key + ".spawn.pitch", loc.getPitch());
        saveConfig();

        player.sendMessage(PREFIX + "§aTrap spawn noktası başarıyla ayarlandı!");
    }

    private void handleSpawn(Player player) {
        FileConfiguration config = getConfig();
        if (!config.contains("traps")) {
            player.sendMessage(PREFIX + "§cKayıtlı trap bulunamadı!");
            return;
        }

        String targetTrapKey = null;
        String uuid = player.getUniqueId().toString();

        for (String key : config.getConfigurationSection("traps").getKeys(false)) {
            if (uuid.equals(config.getString("traps." + key + ".owner"))) {
                targetTrapKey = key;
                break;
            }
            List<String> members = config.getStringList("traps." + key + ".members");
            if (members.contains(uuid)) {
                targetTrapKey = key;
                break;
            }
        }

        if (targetTrapKey == null) {
            player.sendMessage(PREFIX + "§cSahibi olduğunuz veya üyesi olduğunuz bir trap bulunamadı!");
            return;
        }

        if (!config.contains("traps." + targetTrapKey + ".spawn")) {
            player.sendMessage(PREFIX + "§cTrap için henüz spawn noktası ayarlanmamış!");
            return;
        }

        String worldName = config.getString("traps." + targetTrapKey + ".spawn.world", "world");
        double x = config.getDouble("traps." + targetTrapKey + ".spawn.x");
        double y = config.getDouble("traps." + targetTrapKey + ".spawn.y");
        double z = config.getDouble("traps." + targetTrapKey + ".spawn.z");
        float yaw = (float) config.getDouble("traps." + targetTrapKey + ".spawn.yaw");
        float pitch = (float) config.getDouble("traps." + targetTrapKey + ".spawn.pitch");

        Location loc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
        player.teleport(loc);
        player.sendMessage(PREFIX + "§aTrap spawn noktasına ışınlandınız.");
    }

    private void handleSellRequest(Player player, double price) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece kendi trapini satışa koyabilirsin!");
            return;
        }

        sellConfirmations.add(player.getUniqueId().toString());
        pendingSellPrices.put(player.getUniqueId(), price);

        TextComponent msg = new TextComponent(PREFIX + "§fTrap'inizi §e" + price + "$ §ffiyatla satmak üzeresiniz. ");
        TextComponent button = new TextComponent("§c§l[ONAYLAMAK İÇİN TIKLAYIN]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trap satısakoy onay"));
        msg.addExtra(button);

        player.spigot().sendMessage(msg);
    }

    private void handleSellConfirm(Player player) {
        String uuid = player.getUniqueId().toString();

        if (!sellConfirmations.contains(uuid)) {
            player.sendMessage(PREFIX + "§cAktif satış talebiniz yok.");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(uuid)) {
            player.sendMessage(PREFIX + "§cBu trap size ait değil!");
            sellConfirmations.remove(uuid);
            pendingSellPrices.remove(player.getUniqueId());
            return;
        }

        double price = pendingSellPrices.getOrDefault(player.getUniqueId(), 60000.0);
        config.set("traps." + key + ".for-sale", true);
        config.set("traps." + key + ".custom-price", price);
        config.set("traps." + key + ".owner", "NONE");
        config.set("traps." + key + ".owner-name", "NONE");
        config.set("traps." + key + ".members", new ArrayList<>());
        config.set("traps." + key + ".permissions", null);
        saveConfig();

        sellConfirmations.remove(uuid);
        pendingSellPrices.remove(player.getUniqueId());

        player.sendMessage(PREFIX + "§aTrap başarıyla " + price + "$ fiyatla satışa çıkarıldı ve sahipliğinizden ayrıldı!");
    }

    private void handleInvite(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
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
            player.sendMessage(PREFIX + "§cReddedilecek davet yok!");
            return;
        }
        invites.remove(uuid);
        player.sendMessage(PREFIX + "§cTrap daveti reddedildi.");
    }

    private void handleLeave(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key)) {
            player.sendMessage(PREFIX + "§cBulunduğunuz alanda bir trap yok!");
            return;
        }

        String uuid = player.getUniqueId().toString();
        if (uuid.equals(config.getString("traps." + key + ".owner"))) {
            player.sendMessage(PREFIX + "§cTrap sahibi direkt ayrılamaz! Satışa koymak için §b/trap satısakoy <fiyat> §ckullanın.");
            return;
        }

        List<String> members = config.getStringList("traps." + key + ".members");
        if (members.contains(uuid)) {
            members.remove(uuid);
            config.set("traps." + key + ".members", members);
            config.set("traps." + key + ".permissions." + uuid, null);
            saveConfig();
            player.sendMessage(PREFIX + "§aTrap'ten başarıyla ayrıldınız.");
        } else {
            player.sendMessage(PREFIX + "§cBu trap'in üyesi değilsiniz!");
        }
    }

    private void handleTrust(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
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

        player.sendMessage(PREFIX + "§a" + target.getName() + " oyuncusuna yetki (Trust) verildi!");
    }

    private void handleKick(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece trap sahibi üye atabilir!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        String targetUUID = target != null ? target.getUniqueId().toString() : "";

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
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece trap sahibi izinleri değiştirebilir!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage(PREFIX + "§cOyuncu bulunamadı!"); return; }

        String targetUUID = target.getUniqueId().toString();
        List<String> members = config.getStringList("traps." + key + ".members");
        if (!members.contains(targetUUID)) { player.sendMessage(PREFIX + "§cBu oyuncu üye değil!"); return; }

        boolean enable = state.equalsIgnoreCase("ac") || state.equalsIgnoreCase("aç");
        config.set("traps." + key + ".permissions." + targetUUID + "." + perm.toLowerCase(), enable);
        saveConfig();

        player.sendMessage(PREFIX + "§e" + target.getName() + " §fiçin §b" + perm + " §fyetkisi: " + (enable ? "§aAÇIK" : "§cKAPALI"));
    }

    private void handleYetkiList(Player player, String targetName) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage(PREFIX + "§cOyuncu bulunamadı!"); return; }

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

        String key = getMainKey(getChunkKey(chunk));
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
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!config.contains("traps." + key) || !config.getString("traps." + key + ".owner").equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "§cSadece trap sahibi PvP modunu değiştirebilir!");
            return;
        }

        boolean pvpState = status.equalsIgnoreCase("ac") || status.equalsIgnoreCase("aç");
        config.set("traps." + key + ".pvp", pvpState);
        saveConfig();
        player.sendMessage(PREFIX + "PvP durumu: " + (pvpState ? "§aAÇIK" : "§cKAPALI"));
    }

    private void handleDeposit(Player player, String amountStr) {
        Chunk chunk = player.getLocation().getChunk();
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();

            if (econ != null) {
                if (econ.getBalance(player) < amount) { player.sendMessage(PREFIX + "§cYeterli paranız yok!"); return; }
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
        String key = getMainKey(getChunkKey(chunk));
        FileConfiguration config = getConfig();

        if (!hasSpecificPerm(player, chunk, "withdraw")) {
            player.sendMessage(PREFIX + "§cBu trap kasasından para çekme yetkiniz yok!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();

            double currentBank = config.getDouble("traps." + key + ".bank", 0.0);
            if (currentBank < amount) { player.sendMessage(PREFIX + "§cKasada yeterli para yok!"); return; }

            config.set("traps." + key + ".bank", currentBank - amount);
            saveConfig();
            if (econ != null) econ.depositPlayer(player, amount);

            player.sendMessage(PREFIX + "Kasadan §e" + amount + "$ §fçekildi.");
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + "§cGeçerli bir miktar girin!");
        }
    }

    // --- GÜVENLİK VE EVENT KONTROLLERİ ---

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Chunk toChunk = event.getTo().getChunk();
        Player player = event.getPlayer();
        String key = getMainKey(getChunkKey(toChunk));
        FileConfiguration config = getConfig();

        if (config.contains("traps." + key)) {
            String owner = config.getString("traps." + key + ".owner", "NONE");
            String trapName = config.getString("traps." + key + ".name", "Trap");
            int hp = config.getInt("traps." + key + ".health", 5000);
            int maxHp = config.getInt("traps." + key + ".max-health", 5000);
            String ownerText = owner.equals("NONE") ? "§eSahibi Yok" : "§b" + config.getString("traps." + key + ".owner-name", "Sahibi Var");

            String actionBarMsg = "§c[§eA§ax§bo§dT§er§aa§bp§c] §e🛡 " + trapName + " §8| §fSahibi: " + ownerText + " §8| §aCan: §e" + hp + "/" + maxHp;
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
        FileConfiguration config = getConfig();
        String key = getMainKey(getChunkKey(chunk));

        if (config.contains("traps." + key)) {
            if (!hasSpecificPerm(event.getPlayer(), chunk, "blokkir")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(PREFIX + "§cÜyesi olmadığınız trapte blok kırma yetkiniz yok!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        FileConfiguration config = getConfig();
        String key = getMainKey(getChunkKey(chunk));

        if (config.contains("traps." + key)) {
            if (!hasSpecificPerm(event.getPlayer(), chunk, "blokkoy")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(PREFIX + "§cÜyesi olmadığınız trapte blok koyma yetkiniz yok!");
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Chunk chunk = clicked.getChunk();
        FileConfiguration config = getConfig();
        String key = getMainKey(getChunkKey(chunk));

        if (config.contains("traps." + key)) {
            Material type = clicked.getType();
            boolean isChest = type.name().contains("CHEST") || type.name().contains("SHULKER");
            boolean isGate = type.name().contains("FENCE") || type.name().contains("DOOR") || type.name().contains("TRAPDOOR") || type.name().contains("BUTTON") || type.name().contains("LEVER");

            if (isChest || isGate) {
                if (!hasSpecificPerm(event.getPlayer(), chunk, "cit")) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(PREFIX + "§cÜyesi olmadığınız trapte etkileşime geçemezsiniz!");
                }
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        FileConfiguration config = getConfig();
        for (Block block : new ArrayList<>(event.blockList())) {
            Chunk chunk = block.getChunk();
            String key = getMainKey(getChunkKey(chunk));
            if (config.contains("traps." + key)) {
                // Trap canını azaltma mantığı
                int currentHp = config.getInt("traps." + key + ".health", 5000);
                currentHp -= 100; // Patlama başına hasar
                if (currentHp < 0) currentHp = 0;
                config.set("traps." + key + ".health", currentHp);
                saveConfig();
            }
        }
    }
}
