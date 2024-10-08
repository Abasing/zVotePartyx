package fr.maxlego08.zvoteparty.listener.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import fr.maxlego08.zvoteparty.ZVotePartyPlugin;
import fr.maxlego08.zvoteparty.api.VotePartyManager;
import fr.maxlego08.zvoteparty.listener.ListenerAdapter;

public class VoteListener extends ListenerAdapter {

    private final ZVotePartyPlugin plugin;

    /**
     * Constructor to initialize the VoteListener with the plugin instance.
     * 
     * @param plugin The instance of ZVotePartyPlugin
     */
    public VoteListener(ZVotePartyPlugin plugin) {
        super();
        this.plugin = plugin;
    }

    /**
     * Handles player join events by giving votes to the player.
     * 
     * @param event The PlayerJoinEvent
     * @param player The player who joined the game
     */
    @Override
    protected void onConnect(PlayerJoinEvent event, Player player) {
        VotePartyManager manager = this.plugin.getManager();
        manager.giveVotes(player);
    }
}
