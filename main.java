import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("CraftAxo Trap Eklentisi Basariyla Aktif Edildi!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CraftAxo Trap Eklentisi Devre Disi Birakildi.");
    }
}
