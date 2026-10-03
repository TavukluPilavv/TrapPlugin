package com.tavuklupilavv.axoguard;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AxoGuard extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    public static Economy econ = null;

    public static class TrapData {
        public String owner;
        public String chunkKey;
        public double bank = 0;
        public int hp = 30000;
        public int maxHp = 30000;
        public boolean pvpEnabled = false;
        public boolean isForSale = false;
        public Set<String> trusted = new HashSet<>();
        public Map<String, Set<String>> permissions = new HashMap<>();

        public TrapData(String owner, String chunkKey) {
            this.owner = owner;
            this.chunkKey = chunkKey;
        }
    }

    public static Map<String, TrapData> trapsByChunk = new HashMap<>();
    public static Map<String, String> pendingInvites = new HashMap<>();
    public static Map<String, Boolean> saleConfirmations = new HashMap<>();

    @Override
    public void onEnable() {
        setupEconomy();
        Objects.requireNonNull(getCommand("trap")).setExecutor(this);
        Objects.requireNonNull(getCommand("trap")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AxoGuard Trap eklentisi yüklendi!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage(ChatColor.AQUA + "=== " + ChatColor.YELLOW + "AxoCraft Trap Komutları" + ChatColor.AQUA + " ===");
            p.sendMessage(ChatColor.YELLOW + "/trap claim " + ChatColor.BLUE + "- 60k karsiligi chunk al.");
            p.sendMessage(ChatColor.YELLOW + "/trap invite <oyuncu> " + ChatColor.BLUE + "- Trap'e davet et.");
            p.sendMessage(ChatColor.YELLOW + "/trap kabul " + ChatColor.BLUE + "- Trap davetini kabul et.");
            p.sendMessage(ChatColor.YELLOW + "/trap deposit <miktar> " + ChatColor.BLUE + "- Bankaya para yatir.");
            p.sendMessage(ChatColor.YELLOW + "/trap withdraw <miktar> " + ChatColor.BLUE + "- Bankadan para cek.");
            p.sendMessage(ChatColor.YELLOW + "/trap satısakoy " + ChatColor.BLUE + "- Trapi satisa çıkar.");
            p.sendMessage(ChatColor.YELLOW + "/trap satısakoy onay " + ChatColor.BLUE + "- Satisi onayla.");
            p.sendMessage(ChatColor.YELLOW + "/trap pvp " + ChatColor.BLUE + "- PvP durumunu degistir.");
            p.sendMessage(ChatColor.YELLOW + "/trap fly " + ChatColor.BLUE + "- Trap icinde uc.");
            p.sendMessage(ChatColor.YELLOW + "/trap izin <oyuncu> <yetki> " + ChatColor.BLUE + "- Yetki ver.");
            p.sendMessage(ChatColor.YELLOW + "/trap kick <oyuncu> " + ChatColor.BLUE + "- Trap'ten birini at.");
            p.sendMessage(ChatColor.YELLOW + "/trap list " + ChatColor.BLUE + "- Trap listesi menusunu ac.");
            if (p.isOp()) {
                p.sendMessage(ChatColor.GOLD + "/trap create / /trap l create " + ChatColor.AQUA + "- OP Trap olustur.");
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        Chunk chunk = p.getLocation().getChunk();
        String chunkKey = chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();

        switch (sub) {
            case "claim":
                if (trapsByChunk.containsKey(chunkKey)) {
                    p.sendMessage(ChatColor.BLUE + "Bu chunk zaten sahiplenilmis!");
                    return true;
                }
                if (!p.isOp() && (econ == null || !econ.has(p, 60000))) {
                    p.sendMessage(ChatColor.YELLOW + "Trap claim etmek icin 60.000$ gereklidir!");
                    return true;
                }
                if (!p.isOp()) econ.withdrawPlayer(p, 60000);
                
                TrapData newTrap = new TrapData(p.getName(), chunkKey);
                trapsByChunk.put(chunkKey, newTrap);
                p.sendMessage(ChatColor.AQUA + "Tebrikler! " + ChatColor.YELLOW + "Trap " + p.getName() + ChatColor.AQUA + " basariyla alindi.");
                break;

            case "create":
            case "l":
                if (!p.isOp()) {
                    p.sendMessage(ChatColor.YELLOW + "Bu komutu sadece OPlar kullanabilir!");
                    return true;
                }
                createNormalTrap(p.getLocation());
                p.sendMessage(ChatColor.AQUA + "[" + ChatColor.YELLOW + "AxoCraft" + ChatColor.AQUA + "] " + ChatColor.YELLOW + "Trap Oluşturuldu!");
                break;

            case "invite":
                if (args.length < 2) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    pendingInvites.put(target.getName(), p.getName());
                    p.sendMessage(ChatColor.AQUA + target.getName() + ChatColor.YELLOW + " oyuncusuna davet gonderildi.");
                    target.sendMessage(ChatColor.YELLOW + p.getName() + ChatColor.AQUA + " sizi trapine davet etti! Onaylamak icin: " + ChatColor.YELLOW + "/trap kabul");
                }
                break;

            case "kabul":
                if (pendingInvites.containsKey(p.getName())) {
                    String inviter = pendingInvites.remove(p.getName());
                    p.sendMessage(ChatColor.AQUA + inviter + ChatColor.YELLOW + " tarafindan gonderilen trap daveti kabul edildi!");
                }
                break;

            case "deposit":
                if (args.length > 1 && econ != null) {
                    double amount = Double.parseDouble(args[1]);
                    if (econ.has(p, amount)) {
                        econ.withdrawPlayer(p, amount);
                        TrapData t = trapsByChunk.get(chunkKey);
                        if (t != null) t.bank += amount;
                        p.sendMessage(ChatColor.BLUE + "Bankaya " + ChatColor.YELLOW + amount + "$" + ChatColor.BLUE + " yatirildi.");
                    }
                }
                break;

            case "withdraw":
                if (args.length > 1 && econ != null) {
                    double amount = Double.parseDouble(args[1]);
                    TrapData t = trapsByChunk.get(chunkKey);
                    if (t != null && t.bank >= amount) {
                        t.bank -= amount;
                        econ.depositPlayer(p, amount);
                        p.sendMessage(ChatColor.AQUA + "Bankadan " + ChatColor.YELLOW + amount + "$" + ChatColor.AQUA + " cekildi.");
                    }
                }
                break;

            case "satısakoy":
            case "satisakoy":
                if (args.length > 1 && args[1].equalsIgnoreCase("onay")) {
                    if (saleConfirmations.getOrDefault(p.getName(), false)) {
                        TrapData t = trapsByChunk.get(chunkKey);
                        if (t != null && t.owner.equalsIgnoreCase(p.getName())) {
                            t.isForSale = true;
                            if (econ != null) econ.depositPlayer(p, 60000);
                            p.sendMessage(ChatColor.AQUA + "Trap 60k karsiliginda satisa konuldu!");
                            saleConfirmations.remove(p.getName());
                        }
                    }
                } else {
                    saleConfirmations.put(p.getName(), true);
                    p.sendMessage(ChatColor.YELLOW + "Satisi onaylamak icin: " + ChatColor.AQUA + "/trap satısakoy onay");
                }
                break;

            case "pvp":
                TrapData tPvp = trapsByChunk.get(chunkKey);
                if (tPvp != null && tPvp.owner.equalsIgnoreCase(p.getName())) {
                    tPvp.pvpEnabled = !tPvp.pvpEnabled;
                    p.sendMessage(ChatColor.BLUE + "PvP Durumu: " + (tPvp.pvpEnabled ? ChatColor.YELLOW + "ACIK" : ChatColor.AQUA + "KAPALI"));
                }
                break;

            case "fly":
                p.setAllowFlight(!p.getAllowFlight());
                p.sendMessage(ChatColor.AQUA + "Trap Ucus Modu: " + (p.getAllowFlight() ? ChatColor.YELLOW + "ACIK" : ChatColor.BLUE + "KAPALI"));
                break;

            case "list":
                openTrapListMenu(p);
                break;
        }
        return true;
    }

    private void createNormalTrap(Location loc) {
        Block base = loc.getBlock();
        int width = 3, height = 4, length = 3;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < 2; z++) {
                    Block b = base.getRelative(x, y, z);
                    if (b.getType() == Material.AIR) b.setType(Material.STONE);
                }
            }
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < 2; x++) {
                    Block b = base.getRelative(x, y, z);
                    if (b.getType() == Material.AIR) b.setType(Material.STONE);
                }
            }
        }
    }

    private void openTrapListMenu(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.BLUE + "AxoCraft Trap Listesi");
        int slot = 0;
        for (TrapData t : trapsByChunk.values()) {
            if (slot >= 27) break;
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Trap " + t.owner);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.AQUA + "Sahibi: " + ChatColor.YELLOW + t.owner);
                lore.add(ChatColor.AQUA + "Durum: " + (t.isForSale ? ChatColor.RED + "DOLU / SATIŞTA" : ChatColor.GREEN + "BOŞ / AKTİF"));
                lore.add(ChatColor.AQUA + "Can: " + ChatColor.YELLOW + t.hp + "/" + t.maxHp);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
        p.openInventory(gui);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getChunk().equals(e.getTo().getChunk())) return;
        Chunk toChunk = e.getTo().getChunk();
        String key = toChunk.getWorld().getName() + "," + toChunk.getX() + "," + toChunk.getZ();
        
        if (trapsByChunk.containsKey(key)) {
            TrapData t = trapsByChunk.get(key);
            Player p = e.getPlayer();
            p.sendTitle(
                ChatColor.LIGHT_PURPLE + "[Pembekarpuz]", 
                ChatColor.YELLOW + "⚔ Trap " + ChatColor.GREEN + t.owner + 
                ChatColor.YELLOW + " | Can: " + ChatColor.RED + t.hp + "/" + t.maxHp, 
                10, 40, 10
            );
        }
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
            Chunk c = e.getEntity().getLocation().getChunk();
            String key = c.getWorld().getName() + "," + c.getX() + "," + c.getZ();
            if (trapsByChunk.containsKey(key)) {
                TrapData t = trapsByChunk.get(key);
                if (!t.pvpEnabled) {
                    e.setCancelled(true);
                    e.getDamager().sendMessage(ChatColor.YELLOW + "Bu trapda pvp kapali!");
                }
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (checkProtection(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (checkProtection(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && checkProtection(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    private boolean checkProtection(Player p, Location loc) {
        if (p.isOp()) return false;
        Chunk c = loc.getChunk();
        String key = c.getWorld().getName() + "," + c.getX() + "," + c.getZ();
        if (trapsByChunk.containsKey(key)) {
            TrapData t = trapsByChunk.get(key);
            return !t.owner.equalsIgnoreCase(p.getName()) && !t.trusted.contains(p.getName());
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("claim", "invite", "kabul", "deposit", "withdraw", "satısakoy", "pvp", "fly", "izin", "kick", "list"));
            if (sender.isOp()) {
                list.add("create");
                list.add("l");
            }
            return list;
        }
        return Collections.emptyList();
    }
}
