package fr.maxlego08.zvoteparty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import fr.maxlego08.zvoteparty.api.PlayerManager;
import fr.maxlego08.zvoteparty.api.PlayerVote;
import fr.maxlego08.zvoteparty.api.Reward;
import fr.maxlego08.zvoteparty.api.Vote;
import fr.maxlego08.zvoteparty.api.VotePartyManager;
import fr.maxlego08.zvoteparty.api.enums.Message;
import fr.maxlego08.zvoteparty.api.enums.RewardType;
import fr.maxlego08.zvoteparty.api.storage.IStorage;
import fr.maxlego08.zvoteparty.api.storage.Storage;
import fr.maxlego08.zvoteparty.loader.RewardLoader;
import fr.maxlego08.zvoteparty.save.Config;
import fr.maxlego08.zvoteparty.zcore.logger.Logger;
import fr.maxlego08.zvoteparty.zcore.logger.Logger.LogType;
import fr.maxlego08.zvoteparty.zcore.utils.loader.Loader;
import fr.maxlego08.zvoteparty.zcore.utils.storage.Persist;
import fr.maxlego08.zvoteparty.zcore.utils.yaml.YamlUtils;

public class ZVotePartyManager extends YamlUtils implements VotePartyManager {

    private final ZVotePartyPlugin plugin;
    private final List<Reward> rewards = new ArrayList<>();
    private final List<Reward> partyRewards = new ArrayList<>();
    private List<String> globalCommands = new ArrayList<>();
    private List<String> commands = new ArrayList<>();
    private long needVote = 50;

    public ZVotePartyManager(ZVotePartyPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public void reload(CommandSender sender) {
        try {
            this.plugin.reloadConfig();
            this.loadConfiguration();
            this.plugin.getSavers().forEach(e -> e.load(this.plugin.getPersist()));
            message(sender, Message.RELOAD_SUCCESS);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to reload ZVoteParty: " + e.getMessage());
            e.printStackTrace();
            message(sender, Message.RELOAD_SUCCESS);
        }
    }

    @Override
    public void loadConfiguration() {
        File configFile = new File(this.plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            Logger.warning("Config file not found. Using default settings.");
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        Loader<Reward> loader = new RewardLoader();

        // Load vote rewards
        this.rewards.clear();
        try {
            ConfigurationSection section = configuration.getConfigurationSection("rewards");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String path = "rewards." + key + ".";
                    Reward reward = loader.load(configuration, path);
                    if (reward != null) this.rewards.add(reward);
                }
            }
        } catch (Exception e) {
            Logger.warning("Error loading vote rewards: " + e.getMessage());
        }

        // Load party rewards
        this.partyRewards.clear();
        try {
            ConfigurationSection section = configuration.getConfigurationSection("party.rewards");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String path = "party.rewards." + key + ".";
                    Reward reward = loader.load(configuration, path);
                    if (reward != null) this.partyRewards.add(reward);
                }
            }
        } catch (Exception e) {
            Logger.warning("Error loading party rewards: " + e.getMessage());
        }

        // Load commands and votes needed
        this.needVote = configuration.getLong("party.votes_needed", 50);
        this.globalCommands = configuration.getStringList("party.global_commands");
        this.commands = configuration.getStringList("party.commands");

        Logger.info("Loaded " + this.rewards.size() + " vote rewards and " +
                    this.partyRewards.size() + " party rewards", LogType.SUCCESS);
    }

    @Override
    public void openVote(Player player) {
        if (Config.enableVoteMessage) message(player, Message.VOTE_INFORMATIONS);
        if (Config.enableVoteInventory && this.plugin.getLoader() != null) {
            this.plugin.getLoader().open(player);
            return;
        }
        if (!Config.enableVoteMessage) message(player, "§cError in configuration, contact admin.");
    }

    @Override
    public void vote(String username, String serviceName, boolean updateVoteParty) {
        OfflinePlayer offlinePlayer = Bukkit.getPlayerExact(username);
        if (offlinePlayer == null) offlinePlayer = Bukkit.getOfflinePlayer(username);

        this.handleVoteParty();

        if (offlinePlayer != null) {
            this.vote(offlinePlayer, serviceName);
        } else {
            IStorage iStorage = this.plugin.getIStorage();
            iStorage.performCustomVoteAction(username, serviceName, null);
        }
    }

    @Override
    public void handleVoteParty() {
        IStorage iStorage = this.plugin.getIStorage();
        iStorage.addVoteCount(1);
        if (iStorage.getVoteCount() >= this.needVote) this.start();
    }

    @Override
    public void vote(CommandSender sender, String username, boolean updateVoteParty) {
        this.vote(username, "Serveur Minecraft Vote", updateVoteParty);
        message(sender, Message.VOTE_SEND, "%player%", username);
    }

    @Override
    public void vote(OfflinePlayer offlinePlayer, String serviceName) {
        Reward reward = getRandomReward(RewardType.VOTE);
        if (reward == null) {
            Bukkit.getLogger().info("No vote reward available for " + offlinePlayer.getName());
            return;
        }

        IStorage iStorage = this.plugin.getIStorage();
        if (reward.needToBeOnline() && Config.storage.equals(Storage.REDIS) && !offlinePlayer.isOnline()) {
            iStorage.performCustomVoteAction(offlinePlayer.getName(), serviceName, offlinePlayer.getUniqueId());
            return;
        }

        this.plugin.get(offlinePlayer, playerVote -> {
            try {
                Vote vote = playerVote.vote(this.plugin, serviceName, reward, false);
                iStorage.insertVote(playerVote, vote, reward);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to process vote for " + offlinePlayer.getName());
                e.printStackTrace();
            }
        }, false);
    }

    @Override
    public boolean secretVote(String username, String serviceName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer == null || !offlinePlayer.isOnline()) return false;

        Reward reward = getRandomReward(RewardType.VOTE);
        if (reward == null || !reward.needToBeOnline()) return false;

        this.plugin.get(offlinePlayer, playerVote -> {
            IStorage iStorage = this.plugin.getIStorage();
            Vote vote = playerVote.vote(this.plugin, serviceName, reward, false);
            iStorage.insertVote(playerVote, vote, reward);
        }, false);

        return true;
    }

    @Override
    public Reward getRandomReward(RewardType type) {
        List<Reward> list = type == RewardType.VOTE ? this.rewards : this.partyRewards;
        if (list.isEmpty()) return null;

        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            Reward reward = list.get(ThreadLocalRandom.current().nextInt(list.size()));
            double chance = ThreadLocalRandom.current().nextDouble(0, 100);
            if (reward.getPercent() >= chance) return reward;
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    @Override
    public void giveVotes(Player player) {
        this.plugin.get(player, playerVote -> {
            List<Vote> votes = playerVote.getNeedRewardVotes();
            if (!votes.isEmpty()) {
                schedule(Config.joinGiveVoteMilliSecond, () -> {
                    message(player, Message.VOTE_LATER, "%amount%", votes.size());
                    votes.forEach(e -> {
                        try { e.giveReward(this.plugin, player); }
                        catch (Exception ex) { Bukkit.getLogger().warning("Failed to give reward to " + player.getName()); }
                    });
                });
                IStorage iStorage = this.plugin.getIStorage();
                iStorage.updateRewards(player.getUniqueId());
            }
        }, true);
    }

    @Override
    public void start() {
        IStorage iStorage = this.plugin.getIStorage();
        iStorage.startVoteParty();
        this.secretStart();
    }

    @Override
    public void secretStart() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

        ZVotePartyPlugin.getScheduler().runNextTick(task -> {
            for (Player player : online) {
                for (String cmd : this.globalCommands) {
                    if (cmd == null || cmd.isEmpty()) continue;
                    try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName())); }
                    catch (Exception e) { Bukkit.getLogger().warning("Failed global command: " + cmd); }
                }

                Reward reward = getRandomReward(RewardType.PARTY);
                if (reward != null) {
                    try { reward.give(this.plugin, player); }
                    catch (Exception e) { Bukkit.getLogger().warning("Failed to give party reward to " + player.getName()); }
                }
            }

            for (String cmd : this.commands) {
                if (cmd == null || cmd.isEmpty()) continue;
                try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd); }
                catch (Exception e) { Bukkit.getLogger().warning("Failed party command: " + cmd); }
            }
        });

        broadcast(Message.VOTE_PARTY_START);
    }

    @Override
    public void removeVote(CommandSender sender, OfflinePlayer player) {
        PlayerManager manager = this.plugin.getPlayerManager();
        manager.getPlayer(player, optional -> {
            if (!optional.isPresent() || optional.get().getVoteCount() == 0) {
                message(sender, Message.VOTE_REMOVE_ERROR, "%player%", player.getName());
                return;
            }
            optional.get().removeVote();
            message(sender, Message.VOTE_REMOVE_SUCCESS, "%player%", player.getName());
        }, true);
    }

    @Override
    public void voteOffline(UUID uniqueId, String serviceName) {
        Reward reward = getRandomReward(RewardType.VOTE);
        if (reward == null) return;

        IStorage iStorage = this.plugin.getIStorage();
        this.plugin.get(uniqueId, playerVote -> {
            try {
                Vote vote = playerVote.vote(this.plugin, serviceName, reward, true);
                iStorage.insertVote(playerVote, vote, reward);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed offline vote for " + uniqueId);
            }
        }, false);
    }

    @Override
    public List<String> getGlobalCommands() { return this.globalCommands; }

    @Override
    public List<Reward> getPartyReward() { return this.partyRewards; }

    @Override
    public long getNeedVotes() { return this.needVote; }

    @Override
    public long getPlayerVoteCount(Player player) {
        Optional<PlayerVote> optional = this.plugin.getPlayerManager().getSyncPlayer(player);
        return optional.map(PlayerVote::getVoteCount).orElse(0L);
    }

    @Override
    public void sendNeedVote(CommandSender sender) { message(sender, Message.VOTE_NEEDED); }

    @Override
    public void forceStart(CommandSender sender) {
        message(sender, Message.VOTE_STARTPARTY);
        this.start();
    }

    @Override
    public void save(Persist persist) {}
    @Override
    public void load(Persist persist) {}
}
