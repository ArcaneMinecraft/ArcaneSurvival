package com.arcaneminecraft.survival;

import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import com.arcaneminecraft.ArcaneCommons;

// TODO: Redo the Runnable() implementation using a Class.

final class ArcAFK implements CommandExecutor, Listener {
	private final ArcaneSurvival plugin;
	private final BukkitScheduler scheduler;
	private final HashSet<Player> resetTimerQueue = new HashSet<>();
	private final HashMap<Player, BukkitTask> afkTask = new HashMap<>(); // nothing: active, hasObject: active, null: afk
	private static final long AFK_COUNTDOWN = 6000L; // 6000 tick (5 minute) countdown to being afk
	private static final String FORMAT_AFK = ChatColor.GRAY + "* ";
	private static final String TAG_AFK = ChatColor.DARK_PURPLE + "[AFK] " + ChatColor.RESET;
	
	ArcAFK(ArcaneSurvival plugin) {
		this.plugin = plugin;
		scheduler = plugin.getServer().getScheduler();
		
		// resetTimer Queue Runner (because creating multiple new object didn't seem ideal at all)
		scheduler.runTaskTimerAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				for (Player p : resetTimerQueue) {
					resetTimer(p);
				}
				resetTimerQueue.clear();
			}
		}, 0L, 1200L); // run every 1200 ticks (1 minute)
		
		// if players are already online
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			resetTimerQueue.add(p);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(ArcaneCommons.noConsoleMsg());
			return true;
		}
		Player p = (Player)sender;
		if (!isAFK(p)) setAFK(p);
		resetTimerQueue.remove(p);
		
		return true;
	}
	
	private void resetTimer(Player p) { // this is run for active players every minute
		BukkitTask t = afkTask.put(p, scheduler.runTaskLaterAsynchronously(plugin, new Runnable(){
			@Override
			public void run() {
				setAFK(p);
			}
		}, AFK_COUNTDOWN));
		// If task existed before, cancel that task.
		if (t != null) t.cancel();
	}
	
	private boolean isAFK(Player p) {
		return afkTask.containsKey(p) && afkTask.get(p) == null;
	}
	
	private void setAFK(Player p) {
		if (isAFK(p)) return;
		
		// If a timer was already running, cancel it.
		BukkitTask t = afkTask.put(p, null);
		if (t != null) t.cancel();
		
		// Player is now afk.
		p.setSleepingIgnored(true);
		p.setPlayerListName(TAG_AFK + p.getPlayerListName());
		p.sendMessage(FORMAT_AFK + "You are now AFK.");
	}
	
	private void unsetAFK(Player p) {
		resetTimerQueue.add(p);
		
		if (!isAFK(p)) return;
		// only truly afk players below this comment
		
		BukkitTask t = afkTask.remove(p);
		if (t != null) t.cancel();
		
		// Check leaderboard string
		String temp = p.getPlayerListName();
		if (temp.isEmpty() || temp == null || temp.length() < 9)
		{
			plugin.getLogger().info("ArcaneSurvival: AFK: empty player name? " + String.valueOf(temp));
			p.setPlayerListName("I Am Error");
		}
		p.setPlayerListName(temp.substring(8)); // magic number much? TAG_AFK is odd.
		
		p.setSleepingIgnored(false);
		p.sendRawMessage(FORMAT_AFK + "You are no longer AFK.");
	}
	
	@EventHandler (priority=EventPriority.MONITOR)
	public void detectJoin (PlayerJoinEvent e) { 
		resetTimer(e.getPlayer());
	}
	
	@EventHandler (priority=EventPriority.MONITOR)
	public void detectQuit (PlayerQuitEvent e) {
		resetTimerQueue.remove(e.getPlayer());
		BukkitTask t = afkTask.remove(e.getPlayer());
		if (t != null) t.cancel();
	}
	
	// TODO Running command does not reset AFK countdown
	@EventHandler (priority=EventPriority.MONITOR)
	public void detectCommand (PlayerCommandPreprocessEvent e) { unsetAFK(e.getPlayer()); }
	
	@EventHandler (priority=EventPriority.MONITOR)
	public void detectChat (AsyncPlayerChatEvent e) { unsetAFK(e.getPlayer()); }
	
	@EventHandler (priority=EventPriority.MONITOR)
	public void detectMotion (PlayerMoveEvent e) { unsetAFK(e.getPlayer()); }
}
