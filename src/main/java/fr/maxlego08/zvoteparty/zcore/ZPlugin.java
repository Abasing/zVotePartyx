package fr.maxlego08.zvoteparty.zcore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.maxlego08.zvoteparty.adapter.PlayerAdapter;
import fr.maxlego08.zvoteparty.adapter.RewardAdapter;
import fr.maxlego08.zvoteparty.adapter.VoteAdapter;
import fr.maxlego08.zvoteparty.api.PlayerVote;
import fr.maxlego08.zvoteparty.api.Reward;
import fr.maxlego08.zvoteparty.api.Vote;
import fr.maxlego08.zvoteparty.api.enums.InventoryName;
import fr.maxlego08.zvoteparty.api.storage.Script;
import fr.maxlego08.zvoteparty.command.CommandManager;
import fr.maxlego08.zvoteparty.command.VCommand;
import fr.maxlego08.zvoteparty.exceptions.ListenerNullException;
import fr.maxlego08.zvoteparty.inventory.VInventory;
import fr.maxlego08.zvoteparty.inventory.ZInventoryManager;
import fr.maxlego08.zvoteparty.listener.ListenerAdapter;
import fr.maxlego08.zvoteparty.zcore.enums.EnumInventory;
import fr.maxlego08.zvoteparty.zcore.enums.Folder;
import fr.maxlego08.zvoteparty.zcore.logger.Logger;
import fr.maxlego08.zvoteparty.zcore.logger.Logger.LogType;
import fr.maxlego08.zvoteparty.zcore.utils.gson.LocationAdapter;
import fr.maxlego08.zvoteparty.zcore.utils.gson.PotionEffectAdapter;
import fr.maxlego08.zvoteparty.zcore.utils.nms.NMSUtils;
import fr.maxlego08.zvoteparty.zcore.utils.plugins.Plugins;
import fr.maxlego08.zvoteparty.zcore.utils.storage.Persist;
import fr.maxlego08.zvoteparty.zcore.utils.storage.Saveable;

public abstract class ZPlugin extends JavaPlugin {

    private final Logger log = new Logger(this.getDescription().getFullName());
    private final List<Saveable> savers = new ArrayList<>();
    private final List<ListenerAdapter> listenerAdapters = new ArrayList<>();

    private Gson gson;
    private Persist persist;
    private long enableTime;

    protected CommandManager commandManager;
    protected ZInventoryManager zInventoryManager;

    private List<String> files = new ArrayList<>();

    @Override
    public void onEnable() {
        preEnable();
        // Plugin setup code here
        postEnable();
    }

    @Override
    public void onDisable() {
        preDisable();
        // Plugin shutdown code here
        postDisable();
    }

    protected void preEnable() {
        this.enableTime = System.currentTimeMillis();
        this.log.log("=== ENABLE START ===");
        this.log.log("Plugin Version V<&>c" + getDescription().getVersion(), LogType.INFO);

        this.getDataFolder().mkdirs();

        for (Folder folder : Folder.values()) {
            File currentFolder = new File(this.getDataFolder(), folder.toFolder());
            if (!currentFolder.exists()) {
                currentFolder.mkdir();
            }
        }

        this.gson = getGsonBuilder().create();
        this.persist = new Persist(this);

        boolean isNew = NMSUtils.isNewVersion();
        for (String file : files) {
            if (isNew) {
                if (!new File(getDataFolder() + "/inventories/" + file + ".yml").exists()) {
                    saveResource("inventories/1_13/" + file + ".yml", "inventories/" + file + ".yml", false);
                }
            } else {
                if (!new File(getDataFolder() + "/inventories/" + file + ".yml").exists()) {
                    saveResource("inventories/" + file + ".yml", false);
                }
            }
        }

        for (Script script : Script.values()) {
            if (!new File(getDataFolder() + "/scripts/" + script.name().toLowerCase() + ".sql").exists()) {
                this.saveResource("scripts/" + script.name().toLowerCase() + ".sql", false);
            }
        }
    }

    protected void postEnable() {
        if (this.zInventoryManager != null) {
            this.zInventoryManager.sendLog();
        }

        if (this.commandManager != null) {
            this.commandManager.validCommands();
        }

        this.log.log("=== ENABLE DONE <&>7(<&>6" + Math.abs(enableTime - System.currentTimeMillis()) + "ms<&>7) <&>e===");
    }

    protected void preDisable() {
        this.enableTime = System.currentTimeMillis();
        this.log.log("=== DISABLE START ===");
    }

    protected void postDisable() {
        this.log.log("=== DISABLE DONE <&>7(<&>6" + Math.abs(enableTime - System.currentTimeMillis()) + "ms<&>7) <&>e===");
    }

    public GsonBuilder getGsonBuilder() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .serializeNulls()
                .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.VOLATILE)
                .registerTypeAdapter(PotionEffect.class, new PotionEffectAdapter(this))
                .registerTypeAdapter(PlayerVote.class, new PlayerAdapter(this))
                .registerTypeAdapter(Vote.class, new VoteAdapter(this))
                .registerTypeAdapter(Reward.class, new RewardAdapter(this))
                .registerTypeAdapter(Location.class, new LocationAdapter(this));
    }

    public void addListener(Listener listener) {
        if (listener instanceof Saveable) {
            this.addSave((Saveable) listener);
        }
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    public void addListener(ListenerAdapter adapter) {
        if (adapter == null) {
            throw new ListenerNullException("Warning, your listener is null");
        }
        if (adapter instanceof Saveable) {
            this.addSave((Saveable) adapter);
        }
        this.listenerAdapters.add(adapter);
    }

    public void addSave(Saveable saver) {
        this.savers.add(saver);
    }

    public Logger getLog() {
        return this.log;
    }

    public Gson getGson() {
        return gson;
    }

    public Persist getPersist() {
        return persist;
    }

    public List<Saveable> getSavers() {
        return savers;
    }

    public <T> T getProvider(Class<T> classz) {
        RegisteredServiceProvider<T> provider = getServer().getServicesManager().getRegistration(classz);
        if (provider == null) {
            log.log("Unable to retrieve the provider " + classz.toString(), LogType.WARNING);
            return null;
        }
        return provider.getProvider();
    }

    public List<ListenerAdapter> getListenerAdapters() {
        return listenerAdapters;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ZInventoryManager getZInventoryManager() {
        return zInventoryManager;
    }

    protected boolean isEnable(Plugins pl) {
        Plugin plugin = getPlugin(pl);
        return plugin != null && plugin.isEnabled();
    }

    protected Plugin getPlugin(Plugins plugin) {
        return Bukkit.getPluginManager().getPlugin(plugin.getName());
    }

    protected void registerCommand(String command, VCommand vCommand, String... aliases) {
        this.commandManager.registerCommand(command, vCommand, aliases);
    }

    protected void registerInventory(EnumInventory inventory, VInventory vInventory) {
        this.zInventoryManager.registerInventory(inventory, vInventory);
    }

    public List<String> getFiles() {
        return files;
    }

    protected void saveResource(String resourcePath, String toPath, boolean replace) {
        if (resourcePath != null && !resourcePath.isEmpty()) {
            resourcePath = resourcePath.replace('\\', '/');
            InputStream in = this.getResource(resourcePath);
            if (in == null) {
                throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + this.getFile());
            } else {
                File outFile = new File(getDataFolder(), toPath);
                int lastIndex = toPath.lastIndexOf('/');
                File outDir = new File(getDataFolder(), toPath.substring(0, lastIndex >= 0 ? lastIndex : 0));
                if (!outDir.exists()) {
                    outDir.mkdirs();
                }

                try (OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, e);
                }
            }
        } else {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }
    }

    protected void registerFile(InventoryName file) {
        this.files.add(file.getName());
    }
}
