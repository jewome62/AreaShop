package me.wiefferink.areashop.managers;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.Utils;
import me.wiefferink.areashop.events.notify.AddedRegionEvent;
import me.wiefferink.areashop.events.notify.DeletedRegionEvent;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.GeneralRegion.RegionEvent;
import me.wiefferink.areashop.regions.GeneralRegion.RegionType;
import me.wiefferink.areashop.regions.RegionGroup;
import me.wiefferink.areashop.regions.RentRegion;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileManager {
	private static FileManager instance = null;
	
	private AreaShop plugin = null;
	private HashMap<String, GeneralRegion> regions = null;
	private String regionsPath = null;
	private HashMap<String, RegionGroup> groups = null;
	private String configPath = null;
	private YamlConfiguration config = null;
	private String groupsPath = null;
	private YamlConfiguration groupsConfig = null;
	private String defaultPath = null;
	private YamlConfiguration defaultConfig = null;
	private boolean saveGroupsRequired = false;
	private Set<String> worldRegionsRequireSaving;
	
	private HashMap<String,Integer> versions = null;
	private String versionPath = null;
	private String schemFolder = null;

	// Enum for region types
	public enum AddResult {		
		BLACKLISTED("blacklisted"),
		NOPERMISSION("nopermission"),
		ALREADYADDED("alreadyadded"),
		SUCCESS("success");
		
		private final String value;

		AddResult(String value) {
			this.value = value;
		}
		public String getValue() {
			return value;
		}
	} 
	
	/**
	 * Constructor, initialize variabeles
	 * @param plugin AreaShop
	 */
	public FileManager(AreaShop plugin) {
		this.plugin = plugin;
		regions = new HashMap<>();
		regionsPath = plugin.getDataFolder() + File.separator + AreaShop.regionsFolder;
		configPath = plugin.getDataFolder() + File.separator + "config.yml";
		groups = new HashMap<>();
		groupsPath = plugin.getDataFolder() + File.separator + AreaShop.groupsFile;
		defaultPath = plugin.getDataFolder() + File.separator + AreaShop.defaultFile;
		versionPath = plugin.getDataFolder().getPath() + File.separator + AreaShop.versionFile;
		schemFolder = plugin.getDataFolder() + File.separator + AreaShop.schematicFolder;
		worldRegionsRequireSaving = new HashSet<>();
		File schemFile = new File(schemFolder);
		if(!schemFile.exists() & !schemFile.mkdirs()) {
			AreaShop.warn("Could not create schematic files directory: "+schemFile.getAbsolutePath());
		}
		loadVersions();
	}
	
	public static FileManager getInstance() {
		if(instance == null) {
			instance = new FileManager(AreaShop.getInstance());
		}
		return instance;
	}
	
	
	//////////////////////////////////////////////////////////
	// GETTERS
	//////////////////////////////////////////////////////////
	
	public AreaShop getPlugin() {
		return plugin;
	}
	
	public String getSchematicFolder() {
		return schemFolder;
	}
	
	public RegionGroup getGroup(String name) {
		return groups.get(name.toLowerCase());
	}
	
	public Collection<RegionGroup> getGroups() {
		return groups.values();
	}
	
	public YamlConfiguration getDefaultSettings() {
		return defaultConfig;
	}
	
	public YamlConfiguration getConfig() {
		return config;
	}
	
	public GeneralRegion getRegion(String name) {
		return regions.get(name.toLowerCase());
	}
	
	public RentRegion getRent(String name) {
		GeneralRegion region = regions.get(name.toLowerCase());
		if(region != null && region.isRentRegion()) {
			return (RentRegion)region;
		}
		return null;
	}
	
	public BuyRegion getBuy(String name) {
		GeneralRegion region = regions.get(name.toLowerCase());
		if(region != null && region.isBuyRegion()) {
			return (BuyRegion)region;
		}
		return null;
	}
	
	public List<RentRegion> getRents() {
		List<RentRegion> result = new ArrayList<>();
		for(GeneralRegion region : regions.values()) {
			if(region.isRentRegion()) {
				result.add((RentRegion)region);
			}
		}
		return result;
	}
	
	public List<BuyRegion> getBuys() {
		List<BuyRegion> result = new ArrayList<>();
		for(GeneralRegion region : regions.values()) {
			if(region.isBuyRegion()) {
				result.add((BuyRegion)region);
			}
		}
		return result;
	}
	
	public List<GeneralRegion> getRegions() {
		return new ArrayList<>(regions.values());
	}
	
	/**
	 * Get a list of names of a certain group of things
	 * @return A String list with all the names
	 */
	public List<String> getBuyNames() {
		ArrayList<String> result = new ArrayList<>();
		for(BuyRegion region : getBuys()) {
			result.add(region.getName());
		}
		return result;
	}
	public List<String> getRentNames() {
		ArrayList<String> result = new ArrayList<>();
		for(RentRegion region : getRents()) {
			result.add(region.getName());
		}
		return result;
	}
	public List<String> getRegionNames() {
		ArrayList<String> result = new ArrayList<>();
		for(GeneralRegion region : getRegions()) {
			result.add(region.getName());
		}
		return result;
	}
	public List<String> getGroupNames() {
		ArrayList<String> result = new ArrayList<>();
		for(RegionGroup group : getGroups()) {
			result.add(group.getName());
		}
		return result;
	}
	
	/**
	 * Add a rent to the list without saving it to disk (useful for loading at startup)
	 * @param rent The rental region to add
	 */
	public void addRentNoSave(RentRegion rent) {
		if(rent == null) {
			AreaShop.debug("Tried adding a null rent!");
			return;
		}
		regions.put(rent.getName().toLowerCase(), rent);
		Bukkit.getPluginManager().callEvent(new AddedRegionEvent(rent));
	}
	/**
	 * Add a rent to the list and mark it as to-be-saved
	 * @param rent Then rental region to add
	 */
	public void addRent(RentRegion rent) {
		addRentNoSave(rent);
		rent.saveRequired();
	}
	
	/**
	 * Add a buy to the list without saving it to disk (useful for laoding at startup)
	 * @param buy The buy region to add
	 */
	public void addBuyNoSave(BuyRegion buy) {
		if(buy == null) {
			AreaShop.debug("Tried adding a null buy!");
			return;
		}
		regions.put(buy.getName().toLowerCase(), buy);
		Bukkit.getPluginManager().callEvent(new AddedRegionEvent(buy));
	}
	/**
	 * Add a buy to the list and mark it as to-be-saved
	 * @param buy The buy region to add
	 */
	public void addBuy(BuyRegion buy) {
		addBuyNoSave(buy);
		buy.saveRequired();
	}
	
	/**
	 * Add a RegionGroup
	 * @param group The RegionGroup to add
	 */
	public void addGroup(RegionGroup group) {
		groups.put(group.getName().toLowerCase(), group);
		String lowGroup = group.getName().toLowerCase();
		groupsConfig.set(lowGroup + ".name", group.getName());
		groupsConfig.set(lowGroup + ".priority", 0);
		saveGroupsIsRequired();
	}
	
	/**
	 * Check if a player can add a certain region as rent or buy region
	 * @param sender The player/console that wants to add a region
	 * @param region The WorldGuard region to add
	 * @param type The type the region should have in AreaShop
	 * @return The result if a player would want to add this region
	 */
	public AddResult checkRegionAdd(CommandSender sender, ProtectedRegion region, RegionType type) {
		Player player = null;
		if(sender instanceof Player) {
			player = (Player)sender;
		}
		// Determine if the player is an owner or member of the region
		boolean isMember = player != null && plugin.getWorldGuardHandler().containsMember(region, player.getUniqueId());
		boolean isOwner = player != null && plugin.getWorldGuardHandler().containsOwner(region, player.getUniqueId());		
		AreaShop.debug("checkRegionAdd: isOwner=" + isOwner + ", isMember=" + isMember);
		String typeString;
		if(type == RegionType.RENT) {
			typeString = "rent";
		} else {
			typeString = "buy";
		}
		AreaShop.debug("  permissions: .create=" + sender.hasPermission("areashop.create" + typeString) + ", .create.owner=" + sender.hasPermission("areashop.create" + typeString + ".owner") + ", .create.member=" + sender.hasPermission("areashop.create" + typeString + ".member"));
		if(!(sender.hasPermission("areashop.create" + typeString)
				|| (sender.hasPermission("areashop.create" + typeString + ".owner") && isOwner)
				|| (sender.hasPermission("areashop.create" + typeString + ".member") && isMember))) {
			return AddResult.NOPERMISSION;
		}
		GeneralRegion asRegion = plugin.getFileManager().getRegion(region.getId());
		if(asRegion != null) {
			return AddResult.ALREADYADDED;
		} else if(plugin.getFileManager().isBlacklisted(region.getId())) {
			return AddResult.BLACKLISTED;
		} else {
			return AddResult.SUCCESS;
		}
	}
	
	/**
	 * Remove a rent from the list
	 * @param rent The region to remove
	 * @param giveMoneyBack use true to give money back to the player if someone is currently renting this region, otherwise false
	 * @return true if the rent has been removed, false otherwise
	 */
	public boolean removeRent(RentRegion rent, boolean giveMoneyBack) {
		boolean result = false;
		if(rent != null) {
			rent.setDeleted();
			if(rent.isRented()) {
				rent.unRent(giveMoneyBack, null);
			}
			// Handle schematics and run commands
			rent.handleSchematicEvent(RegionEvent.DELETED);
			rent.runEventCommands(RegionEvent.DELETED, true);

			// Delete the signs and the variable
			if(rent.getWorld() != null) {
				for(Location sign : rent.getSignsFeature().getSignLocations()) {
					sign.getBlock().setType(Material.AIR);
					AreaShop.debug("Removed sign at: " + sign.toString());
				}
			}
			RegionGroup[] groups = getGroups().toArray(new RegionGroup[getGroups().size()]);
			for(RegionGroup group : groups) {
				group.removeMember(rent);
			}
			rent.resetRegionFlags();
			regions.remove(rent.getLowerCaseName());
			File file = new File(plugin.getDataFolder() + File.separator + AreaShop.regionsFolder + File.separator + rent.getLowerCaseName() + ".yml");
			boolean deleted;
			if(file.exists()) {
				try {
					deleted = file.delete();
				} catch(Exception e) {
					deleted = false;
				}
				if(!deleted) {
					AreaShop.warn("File could not be deleted: "+file.toString());
				}
			}
			result = true;

			// Broadcast event
			Bukkit.getPluginManager().callEvent(new DeletedRegionEvent(rent));

			// Run commands
			rent.runEventCommands(RegionEvent.DELETED, false);
		}		
		return result;
	}
	
	/**
	 * Remove a buy from the list
	 * @param buy The BuyRegion to remove
	 * @param giveMoneyBack true if money should be given back to the player, otherwise false
	 * @return true if the buy has been removed, false otherwise
	 */
	public boolean removeBuy(BuyRegion buy, boolean giveMoneyBack) {
		boolean result = false;
		if(buy != null) {
			buy.setDeleted();
			if(buy.isSold()) {
				buy.sell(giveMoneyBack, null);
			}
			// Handle schematics and run commands
			buy.handleSchematicEvent(RegionEvent.DELETED);
			buy.runEventCommands(RegionEvent.DELETED, true);
			
			// Delete the sign and the variable
			if(buy.getWorld() != null) {
				for(Location sign : buy.getSignsFeature().getSignLocations()) {
					sign.getBlock().setType(Material.AIR);
				}
			}
			regions.remove(buy.getLowerCaseName());
			buy.resetRegionFlags();
			
			// Removing from groups
			for(RegionGroup group : getGroups()) {
				group.removeMember(buy);
			}
			
			// Deleting the file
			File file = new File(plugin.getDataFolder() + File.separator + AreaShop.regionsFolder + File.separator + buy.getLowerCaseName() + ".yml");
			boolean deleted;
			if(file.exists()) {
				try {
					deleted = file.delete();
				} catch(Exception e) {
					deleted = false;
				}
				if(!deleted) {
					AreaShop.warn("File could not be deleted: "+file.toString());
				}
			}
			
			result = true;

			// Broadcast event
			Bukkit.getPluginManager().callEvent(new DeletedRegionEvent(buy));

			// Run commands
			buy.runEventCommands(RegionEvent.DELETED, false);
		}		
		return result;
	}
	
	/**
	 * Remove a group
	 * @param group Group to remove
	 */
	public void removeGroup(RegionGroup group) {
		groups.remove(group.getLowerCaseName());
		groupsConfig.set(group.getLowerCaseName(), null);
		saveGroupsIsRequired();
	}
	
	/**
	 * Update all signs that need periodic updating
	 */
	public void performPeriodicSignUpdate() {
		final List<RentRegion> regions = new ArrayList<>(getRents());
		new BukkitRunnable() {
			private int current = 0;
			
			@Override
			public void run() {
				for(int i=0; i<plugin.getConfig().getInt("signs.regionsPerTick"); i++) {
					if(current < regions.size()) {
						if(regions.get(current).needsPeriodicUpdate()) {
							regions.get(current).update();
						}
						current++;
					}
				}
				if(current >= regions.size()) {
					this.cancel();
				}
			}
		}.runTaskTimer(plugin, 1, 1);		
	}
	
	/**
	 * Send out rent expire warnings
	 */
	public void sendRentExpireWarnings() {
		final List<RentRegion> regions = new ArrayList<>(getRents());
		new BukkitRunnable() {
			private int current = 0;
			
			@Override
			public void run() {
				for(int i=0; i<plugin.getConfig().getInt("expireWarning.regionsPerTick"); i++) {
					if(current < regions.size()) {
						regions.get(current).sendExpirationWarnings();
						current++;
					}
				}
				if(current >= regions.size()) {
					this.cancel();
				}
			}
		}.runTaskTimer(plugin, 1, 1);
	}
	
	/**
	 * Update regions in a task to minimize lag
	 * @param regions Regions to update
	 * @param confirmationReceiver The CommandSender that should be notified at completion
	 */
	public void updateRegions(final List<GeneralRegion> regions, final CommandSender confirmationReceiver) {
		final int regionsPerTick = plugin.getConfig().getInt("update.regionsPerTick");
		if(confirmationReceiver != null) {
			plugin.message(confirmationReceiver, "reload-updateStart", regions.size(), regionsPerTick*20);
		}
		new BukkitRunnable() {
			private int current = 0;
			@Override
			public void run() {
				for(int i=0; i<regionsPerTick; i++) {
					if(current < regions.size()) {
						regions.get(current).update();
						current++;
					} 
				}
				if(current >= regions.size()) {
					if(confirmationReceiver != null) {
						plugin.message(confirmationReceiver, "reload-updateComplete");
					}
					this.cancel();
				}
			}
		}.runTaskTimer(plugin, 1, 1);
	}
	public void updateRegions(List<GeneralRegion> regions) {
		updateRegions(regions, null);
	}
	/**
	 * Update all regions, happens in a task to minimize lag
	 */
	public void updateAllRegions() {
		updateRegions(getRegions(), null);
	}
	public void updateAllRegions(CommandSender confirmationReceiver) {
		updateRegions(getRegions(), confirmationReceiver);
	}

	
	/**
	 * Save the group file to disk
	 */
	public void saveGroupsIsRequired() {
		saveGroupsRequired = true;
	}
	public boolean isSaveGroupsRequired() {
		return saveGroupsRequired;
	}
	
	public void saveGroupsNow() {
		AreaShop.debug("saveGroupsNow() done");
		saveGroupsRequired = false;
		try {
			groupsConfig.save(groupsPath);
		} catch (IOException e) {
			AreaShop.warn("Groups file could not be saved: "+groupsPath);
		}
	}
	
	
	/**
	 * Save all region related files spread over time (low load)
	 */
	public void saveRequiredFiles() {
		if(isSaveGroupsRequired()) {
			saveGroupsNow();
		}
		this.saveWorldGuardRegions();

		final List<GeneralRegion> regions = new ArrayList<>(getRegions());
		new BukkitRunnable() {
			private int current = 0;
			
			@Override
			public void run() {
				for(int i=0; i<plugin.getConfig().getInt("saving.regionsPerTick"); i++) {
					if(current < regions.size()) {
						if(regions.get(current).isSaveRequired()) {
							regions.get(current).saveNow();
						}
						current++;
					}
				}
				if(current >= regions.size()) {
					this.cancel();
				}
			}
		}.runTaskTimer(plugin, 1, 1);
	}
	
	/**
	 * Save all region related files directly (only for cases like onDisable())
	 */
	public void saveRequiredFilesAtOnce() {
		if(isSaveGroupsRequired()) {
			saveGroupsNow();
		}
		for(GeneralRegion region : getRegions()) {
			if(region.isSaveRequired()) {
				region.saveNow();
			}						
		}
		this.saveWorldGuardRegions();
	}
	
	/**
	 * Indicates that a/multiple WorldGuard regions need to be saved
	 * @param worldName The world where the regions that should be saved is in
	 */
	public void saveIsRequiredForRegionWorld(String worldName) {
		worldRegionsRequireSaving.add(worldName);
	}
	
	/**
	 * Save all worldGuard regions that need saving
	 */
	public void saveWorldGuardRegions() {
		for(String world : worldRegionsRequireSaving) {
			World bukkitWorld = Bukkit.getWorld(world);
			if(bukkitWorld != null) {
				RegionManager manager = plugin.getWorldGuard().getRegionManager(bukkitWorld);
				if(manager != null) {
					try {
						if(plugin.getWorldGuard().getDescription().getVersion().startsWith("5.")) {
							manager.save();
						} else {							
							manager.saveChanges();
						}
					} catch(Exception e) {
						AreaShop.warn("WorldGuard regions in world "+world+" could not be saved");
					}
				}
			}
		}
	}
	
	/**
	 * Get the folder the region files are located in
	 * @return The folder where the region.yml files are in
	 */
	public String getRegionFolder() {
		return regionsPath;
	}
	
	/**
	 * Check if a region is on the adding blacklist
	 * @param region The region name to check
	 * @return true if the region may not be added, otherwise false
	 */
	public boolean isBlacklisted(String region) {
		for(String line : plugin.getConfig().getStringList("blacklist")) {
			Pattern pattern = Pattern.compile(line, Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(region);
			if(matcher.matches()) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Unrent regions that have no time left, regions to check per tick is in the config
	 */
	public void checkRents() {
		final List<RentRegion> regions = new ArrayList<>(getRents());
		new BukkitRunnable() {
			private int current = 0;
			
			@Override
			public void run() {
				for(int i=0; i<plugin.getConfig().getInt("expiration.regionsPerTick"); i++) {
					if(current < regions.size()) {
						regions.get(current).checkExpiration();
						current++;
					}
				}
				if(current >= regions.size()) {
					this.cancel();
				}
			}
		}.runTaskTimer(plugin, 1, 1);
	}
	
	/**
	 * Check all regions and unrent/sell them if the player is inactive for too long
	 */
	public void checkForInactiveRegions() {
		final List<GeneralRegion> regions = new ArrayList<>(getRegions());
		new BukkitRunnable() {
			private int current = 0;
			
			@Override
			public void run() {
				for(int i=0; i<plugin.getConfig().getInt("inactive.regionsPerTick"); i++) {
					if(current < regions.size()) {
						regions.get(current).checkInactive();
						current++;
					}
				}
				if(current >= regions.size()) {
					this.cancel();
				}
			}
		}.runTaskTimer(plugin, 1, 1);
	}

	
	/**
	 * Load the file with the versions, used to check if the other files need conversion
	 */
	@SuppressWarnings("unchecked")
	public void loadVersions() {
		File file = new File(versionPath);
		if(file.exists()) {
			// Load versions from the file
			try {
				ObjectInputStream input = new ObjectInputStream(new FileInputStream(versionPath));
				versions = (HashMap<String,Integer>)input.readObject();
				input.close();
			} catch (IOException | ClassNotFoundException | ClassCastException e) {
				AreaShop.warn("Something went wrong reading file: "+versionPath);
				versions = null;
			}
		}
		if(versions == null || versions.size() == 0) {
			versions = new HashMap<>();
			versions.put(AreaShop.versionFiles, 0);
			this.saveVersions();
		}
	}
	
	/**
	 * Save the versions file to disk
	 */
	public void saveVersions() {
		if(!(new File(versionPath).exists())) {
			AreaShop.info("versions file created, this should happen only after installing or upgrading the plugin");
		}
		try {
			ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(versionPath));
			output.writeObject(versions);
			output.close();
		} catch (IOException e) {
			AreaShop.warn("File could not be saved: "+versionPath);
		}
	}

	/**
	 * Load all files from disk
	 * @return true if the files are loaded correctly, otherwise false
	 */
	public boolean loadFiles() {
		// Load config.yml + add defaults from .jar
		boolean result = loadConfigFile();
		// Load default.yml + add defaults from .jar
		result &= loadDefaultFile();
		// Convert old formats to the latest (object saving to .yml saving)
		preUpdateFiles();
		// Load region files (regions folder)
		loadRegionFiles();
		// Convert old formats to the latest (changes in .yml saving format)
		postUpdateFiles();
		// Load groups.yml
		result &= loadGroupsFile();

		return result;
	}
	
	/**
	 * Load the default.yml file
	 * @return true if it has been loaded successfully, otherwise false
	 */
	public boolean loadDefaultFile() {
		boolean result = true;
		File defaultFile = new File(defaultPath);
		// Safe the file from the jar to disk if it does not exist
		if(!defaultFile.exists()) {
			try(
					InputStream input = plugin.getResource(AreaShop.defaultFile);
					OutputStream output = new FileOutputStream(defaultFile)
			) {
				int read;
				byte[] bytes = new byte[1024];		 
				while ((read = input.read(bytes)) != -1) {
					output.write(bytes, 0, read);
				} 
				input.close();
				output.close();
				AreaShop.info("File with default region settings has been saved, should only happen on first startup");
			} catch(IOException e) {
				AreaShop.warn("Something went wrong saving the default region settings: "+defaultFile.getAbsolutePath());
			}
		}
		// Load default.yml from the plugin folder, and as backup the default one
		try(
				InputStreamReader custom = new InputStreamReader(new FileInputStream(defaultFile), Charsets.UTF_8);
				InputStreamReader normal = new InputStreamReader(plugin.getResource(AreaShop.defaultFile), Charsets.UTF_8)
		) {
			defaultConfig = YamlConfiguration.loadConfiguration(custom);
			if(defaultConfig.getKeys(false).size() == 0) {
				AreaShop.warn("File 'default.yml' is empty, check for errors in the log.");
				result = false;
			} else {
				defaultConfig.addDefaults(YamlConfiguration.loadConfiguration(normal));
			}
		} catch(IOException e) {
			result = false;
		}
		return result;
	}
	
	/**
	 * Load the default.yml file
	 * @return true if it has been loaded successfully, otherwise false
	 */
	public boolean loadConfigFile() {
		boolean result = true;
		File configFile = new File(configPath);
		// Safe the file from the jar to disk if it does not exist
		if(!configFile.exists()) {
			try(
					InputStream input = plugin.getResource(AreaShop.configFile);
					OutputStream output = new FileOutputStream(configFile)
			) {
				int read;
				byte[] bytes = new byte[1024];		 
				while ((read = input.read(bytes)) != -1) {
					output.write(bytes, 0, read);
				}
				AreaShop.info("Default config file has been saved, should only happen on first startup");
			} catch(IOException e) {
				AreaShop.warn("Something went wrong saving the config file: "+configFile.getAbsolutePath());
			}
		}
		// Load config.yml from the plugin folder
		try(
				InputStreamReader custom = new InputStreamReader(new FileInputStream(configFile), Charsets.UTF_8);
				InputStreamReader normal = new InputStreamReader(plugin.getResource(AreaShop.configFile), Charsets.UTF_8);
				InputStreamReader hidden = new InputStreamReader(plugin.getResource(AreaShop.configFileHidden), Charsets.UTF_8)
		) {
			config = YamlConfiguration.loadConfiguration(custom);
			if(config.getKeys(false).size() == 0) {
				AreaShop.warn("File 'config.yml' is empty, check for errors in the log.");
				result = false;
			} else {
				config.addDefaults(YamlConfiguration.loadConfiguration(normal));
				config.addDefaults(YamlConfiguration.loadConfiguration(hidden));
				// Set the debug and chatprefix variables
				plugin.setDebug(this.getConfig().getBoolean("debug"));
				if(getConfig().isList("chatPrefix")) {
					plugin.setChatprefix(getConfig().getStringList("chatPrefix"));
				} else {
					ArrayList<String> list = new ArrayList<>();
					list.add(getConfig().getString("chatPrefix"));
					plugin.setChatprefix(list);
				}
			}
		} catch(IOException e) {
			AreaShop.warn("Something went wrong while reading the config.yml file: "+configFile.getAbsolutePath());
			result = false;
		}
		Utils.initialize(config);
		return result;
	}
	
	/**
	 * Load the groups.yml file from disk
	 * @return true if succeeded, otherwise false
	 */
	public boolean loadGroupsFile() {
		boolean result = true;
		File groupFile = new File(groupsPath);
		if(groupFile.exists() && groupFile.isFile()) {
			try(
					InputStreamReader reader = new InputStreamReader(new FileInputStream(groupFile), Charsets.UTF_8)
			) {
				groupsConfig = YamlConfiguration.loadConfiguration(reader);
			} catch(IOException e) {
				AreaShop.warn("Could not load groups.yml file: "+groupFile.getAbsolutePath());
			}
		}
		if(groupsConfig == null) {
			groupsConfig = new YamlConfiguration();
		}
		for(String groupName : groupsConfig.getKeys(false)) {
			RegionGroup group = new RegionGroup(plugin, groupName);
			groups.put(groupName, group);
		}
		return result;
	}
	
	/**
	 * Load all region files
	 */
	public void loadRegionFiles() {
		regions.clear();
		File file = new File(regionsPath);
		if(!file.exists()) {
			if(!file.mkdirs()) {
				AreaShop.warn("Could not create region files directory: "+file.getAbsolutePath());
				return;
			}
			plugin.setReady(true);
		} else if(file.isDirectory()) {
			File[] regionFiles = file.listFiles();
			if(regionFiles == null) {
				return;
			}
			for(File regionFile : regionFiles) {
				if(regionFile.exists() && regionFile.isFile()) {
					// Load the region file from disk in UTF8 mode
					YamlConfiguration config;
					try(
							InputStreamReader reader = new InputStreamReader(new FileInputStream(regionFile), Charsets.UTF_8)
					) {
						config = YamlConfiguration.loadConfiguration(reader);
						if(config.getKeys(false).size() == 0) {
							AreaShop.warn("Region file '"+regionFile.getName()+"' is empty, check for errors in the log.");
						}
					} catch(IOException e) {
						AreaShop.warn("Something went wrong reading region file: "+regionFile.getAbsolutePath());
						continue;
					}
					// Construct the correct type of region
					if(RegionType.RENT.getValue().equals(config.getString("general.type"))) {
						RentRegion rent = new RentRegion(plugin, config);
						addRentNoSave(rent);
					} else if(RegionType.BUY.getValue().equals(config.getString("general.type"))) {
						BuyRegion buy = new BuyRegion(plugin, config);
						addBuyNoSave(buy);
					}
				}						
			}
			plugin.setReady(true);			
			new BukkitRunnable() {				
				@Override
				public void run() {
					List<GeneralRegion> noWorld = new ArrayList<>();
					List<GeneralRegion> noRegion = new ArrayList<>();
					List<GeneralRegion> incorrectDuration = new ArrayList<>();
					for(GeneralRegion region : AreaShop.getInstance().getFileManager().getRegions()) {
						// Add broken regions to a list
						if(region != null) {
							if(region.getWorld() == null) {
								noWorld.add(region);
							}
							if(region.getRegion() == null) {
								noRegion.add(region);
							}
							if(region.isRentRegion() && !Utils.checkTimeFormat(((RentRegion)region).getDurationString())) {
								incorrectDuration.add(region);
							}
						}
					}					
					// All files are loaded, print possible problems to the console
					if(!noRegion.isEmpty()) {
						List<String> noRegionNames = new ArrayList<>();
						for(GeneralRegion region : noRegion) {
							noRegionNames.add(region.getName());
						}
						AreaShop.warn("AreaShop regions that are missing their WorldGuard region: "+Utils.createCommaSeparatedList(noRegionNames));
						AreaShop.warn("Remove these regions from AreaShop with '/as del' or recreate their regions in WorldGuard.");
					}
					boolean noWorldRegions = !noWorld.isEmpty();
					while(!noWorld.isEmpty()) {
						List<GeneralRegion> toDisplay = new ArrayList<>();
						String missingWorld = noWorld.get(0).getWorldName();
						toDisplay.add(noWorld.get(0));
						for(int i=1; i<noWorld.size(); i++) {
							if(noWorld.get(i).getWorldName().equalsIgnoreCase(missingWorld)) {
								toDisplay.add(noWorld.get(i));
							}
						}
						List<String> noWorldNames = new ArrayList<>();
						for(GeneralRegion region : noRegion) {
							noWorldNames.add(region.getName());
						}
						AreaShop.warn("World "+missingWorld+" is not loaded, the following AreaShop regions are not functional now: "+Utils.createCommaSeparatedList(noWorldNames));
						noWorld.removeAll(toDisplay);
					}
					if(noWorldRegions) {
						AreaShop.warn("Remove these regions from AreaShop with '/as del' or load the world(s) on the server again.");
					}
					if(!incorrectDuration.isEmpty()) {
						List<String> incorrectDurationNames = new ArrayList<>();
						for(GeneralRegion region : incorrectDuration) {
							incorrectDurationNames.add(region.getName());
						}
						AreaShop.warn("The following regions have an incorrect time format as duration: "+Utils.createCommaSeparatedList(incorrectDurationNames));
					}
				}
			}.runTask(plugin);
		}
	}
	
	
	/**
	 * Checks for old file formats and converts them to the latest format.
	 * After conversion the region files need to be loaded
	 */
	@SuppressWarnings("unchecked")
	public void preUpdateFiles() {
		Integer fileStatus = versions.get(AreaShop.versionFiles);
		
		// If the the files are already the current version
		if(fileStatus != null && fileStatus == AreaShop.versionFilesCurrent) {
			return;
		}
		AreaShop.info("Updating AreaShop data to the latest format:");
		
		// Update to YAML based format
		if(fileStatus == null || fileStatus < 2) {
			String rentPath = plugin.getDataFolder() + File.separator + "rents";
			String buyPath = plugin.getDataFolder() + File.separator + "buys";
			File rentFile = new File(rentPath);
			File buyFile = new File(buyPath);
			String oldFolderPath = plugin.getDataFolder() + File.separator + "#old" + File.separator;
			File oldFolderFile = new File(oldFolderPath);
			// Convert old rent files
			boolean buyFileFound = false, rentFileFound = false;
			if(rentFile.exists()) {
				rentFileFound = true;
				if(!oldFolderFile.exists() & !oldFolderFile.mkdirs()) {
					AreaShop.warn("Could not create directory: "+oldFolderFile.getAbsolutePath());
				}
				
				if(versions.get("rents") == null) {
					versions.put("rents", -1);
				}
				
				HashMap<String, HashMap<String, String>> rents = null;
				try {
					ObjectInputStream input = new ObjectInputStream(new FileInputStream(rentPath));
			    	rents = (HashMap<String,HashMap<String,String>>)input.readObject();
					input.close();
				} catch (IOException | ClassNotFoundException | ClassCastException e) {
					AreaShop.warn("  Error: Something went wrong reading file: "+rentPath);
				}
				// Delete the file if it is totally wrong
				if(rents == null) {
					try {
						if(!rentFile.delete()) {
							AreaShop.warn("Could not delete file: "+rentFile.getAbsolutePath());
						}
					} catch(Exception e) {
						AreaShop.warn("Could not delete file: "+rentFile.getAbsolutePath());
					}
				} else {
					// Move old file
					try {
						Files.move(new File(rentPath), new File(oldFolderPath + "rents"));
					} catch (Exception e) {
						AreaShop.warn("  Could not create a backup of '"+rentPath+"', check the file permissions (conversion to next version continues)");
					}
					// Check if conversion is needed
					if(versions.get("rents") < 1) {
						// Upgrade the rent to the latest version
						if(versions.get("rents") < 0) {
							for(String rentName : rents.keySet()) {
								HashMap<String,String> rent = rents.get(rentName);
								// Save the rentName in the hashmap and use a small caps rentName as key
								if(rent.get("name") == null) {
									rent.put("name", rentName);
									rents.remove(rentName);
									rents.put(rentName.toLowerCase(), rent);
								}
								// Save the default setting for region restoring
								if(rent.get("restore") == null) {
									rent.put("restore", "general");
								}
								// Save the default setting for the region restore profile
								if(rent.get("profile") == null) {
									rent.put("profile", "default");
								}
								// Change to version 0
								versions.put("rents", 0);
							}
							AreaShop.info("  Updated version of '"+buyPath+"' from -1 to 0 (switch to using lowercase region names, adding default schematic enabling and profile)");
						}
						if(versions.get("rents") < 1) {
							for(String rentName : rents.keySet()) {
								HashMap<String,String> rent = rents.get(rentName);
								if(rent.get("player") != null) {
									@SuppressWarnings("deprecation")  // Fake deprecation by Bukkit to inform developers, method will stay
									OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(rent.get("player"));
									rent.put("playeruuid", offlinePlayer.getUniqueId().toString());		
									rent.remove("player");
								}
								// Change version to 1
								versions.put("rents", 1);
							}
							AreaShop.info("  Updated version of '"+rentPath+"' from 0 to 1 (switch to UUID's for player identification)");
						}				
					}		
					// Save rents to new format
					File regionsFile = new File(regionsPath);
					if(!regionsFile.exists() & !regionsFile.mkdirs()) {
						AreaShop.warn("Could not create directory: "+regionsFile.getAbsolutePath());
						return;
					}
					for(HashMap<String, String> rent : rents.values()) {
						YamlConfiguration config = new YamlConfiguration();
						config.set("general.name", rent.get("name").toLowerCase());
						config.set("general.type", "rent");
						config.set("general.world", rent.get("world"));
						config.set("general.signs.0.location.world", rent.get("world"));
						config.set("general.signs.0.location.x", Double.parseDouble(rent.get("x")));
						config.set("general.signs.0.location.y", Double.parseDouble(rent.get("y")));
						config.set("general.signs.0.location.z", Double.parseDouble(rent.get("z")));
						config.set("rent.price", Double.parseDouble(rent.get("price")));
						config.set("rent.duration", rent.get("duration"));
						if(rent.get("restore") != null && !rent.get("restore").equals("general")) {
							config.set("general.enableRestore", rent.get("restore"));
						}
						if(rent.get("profile") != null && !rent.get("profile").equals("default")) {
							config.set("general.schematicProfile", rent.get("profile"));
						}
						if(rent.get("tpx") != null) {
							config.set("general.teleportLocation.world", rent.get("world"));
							config.set("general.teleportLocation.x", Double.parseDouble(rent.get("tpx")));
							config.set("general.teleportLocation.y", Double.parseDouble(rent.get("tpy")));
							config.set("general.teleportLocation.z", Double.parseDouble(rent.get("tpz")));
							config.set("general.teleportLocation.yaw", rent.get("tpyaw"));
							config.set("general.teleportLocation.pitch", rent.get("tppitch"));
						}
						if(rent.get("playeruuid") != null) {
							config.set("rent.renter", rent.get("playeruuid"));
							config.set("rent.renterName", Utils.toName(rent.get("playeruuid")));
							config.set("rent.rentedUntil", Long.parseLong(rent.get("rented")));
						}
						try {
							config.save(new File(regionsPath + File.separator + rent.get("name").toLowerCase() + ".yml"));
						} catch (IOException e) {
							AreaShop.warn("  Error: Could not save region file while converting: "+regionsPath+File.separator+rent.get("name").toLowerCase()+".yml");
						}
					}
					AreaShop.info("  Updated rent regions to new .yml format (check the /regions folder)");
				}
				
				// Change version number
				versions.remove("rents");
				versions.put(AreaShop.versionFiles, AreaShop.versionFilesCurrent);			
				saveVersions();			
			}
			if(buyFile.exists()) {
				buyFileFound = true;
				if(!oldFolderFile.exists() & !oldFolderFile.mkdirs()) {
					AreaShop.warn("Could not create directory: "+oldFolderFile.getAbsolutePath());
					return;
				}
				
				if(versions.get("buys") == null) {
					versions.put("buys", -1);
				}
				
				HashMap<String, HashMap<String, String>> buys = null;
				try {
					ObjectInputStream input = new ObjectInputStream(new FileInputStream(buyPath));
			    	buys = (HashMap<String,HashMap<String,String>>)input.readObject();
					input.close();
				} catch (IOException | ClassNotFoundException | ClassCastException e) {
					AreaShop.warn("  Something went wrong reading file: "+buyPath);
				}
				// Delete the file if it is totally wrong
				if(buys == null) {
					try {
						if(!buyFile.delete()) {
							AreaShop.warn("Could not delete file: "+buyFile.getAbsolutePath());
						}
					} catch(Exception e) {
						AreaShop.warn("Could not delete file: "+buyFile.getAbsolutePath());
					}
				} else {
					// Backup current file
					try {
						Files.move(new File(buyPath), new File(oldFolderPath + "buys"));
					} catch (Exception e) {
						AreaShop.warn("  Could not create a backup of '"+buyPath+"', check the file permissions (conversion to next version continues)");
					}
					// Check if conversion is needed
					if(versions.get("buys") < 1) {
						// Upgrade the buy to the latest version
						if(versions.get("buys") < 0) {
							for(String buyName : buys.keySet()) {
								HashMap<String,String> buy = buys.get(buyName);
								// Save the buyName in the hashmap and use a small caps buyName as key
								if(buy.get("name") == null) {
									buy.put("name", buyName);
									buys.remove(buyName);
									buys.put(buyName.toLowerCase(), buy);
								}
								// Save the default setting for region restoring
								if(buy.get("restore") == null) {
									buy.put("restore", "general");
								}
								// Save the default setting for the region restore profile
								if(buy.get("profile") == null) {
									buy.put("profile", "default");
								}
								// Change to version 0
								versions.put("buys", 0);
							}
							AreaShop.info("  Updated version of '"+buyPath+"' from -1 to 0 (switch to using lowercase region names, adding default schematic enabling and profile)");
						}
						if(versions.get("buys") < 1) {
							for(String buyName : buys.keySet()) {
								HashMap<String,String> buy = buys.get(buyName);
								if(buy.get("player") != null) {
									@SuppressWarnings("deprecation")  // Fake deprecation by Bukkit to inform developers, method will stay
									OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(buy.get("player"));
									buy.put("playeruuid", offlinePlayer.getUniqueId().toString());		
									buy.remove("player");
								}
								// Change version to 1
								versions.put("buys", 1);
							}
							AreaShop.info("  Updated version of '"+buyPath+"' from 0 to 1 (switch to UUID's for player identification)");
						}				
					}		
				
					// Save buys to new format
					File regionsFile = new File(regionsPath);
					if(!regionsFile.exists() & !regionsFile.mkdirs()) {
						AreaShop.warn("Could not create directory: "+regionsFile.getAbsolutePath());
					}
					for(HashMap<String, String> buy : buys.values()) {
						YamlConfiguration config = new YamlConfiguration();
						config.set("general.name", buy.get("name").toLowerCase());
						config.set("general.type", "buy");
						config.set("general.world", buy.get("world"));
						config.set("general.signs.0.location.world", buy.get("world"));
						config.set("general.signs.0.location.x", Double.parseDouble(buy.get("x")));
						config.set("general.signs.0.location.y", Double.parseDouble(buy.get("y")));
						config.set("general.signs.0.location.z", Double.parseDouble(buy.get("z")));
						config.set("buy.price", Double.parseDouble(buy.get("price")));
						if(buy.get("restore") != null && !buy.get("restore").equals("general")) {
							config.set("general.enableRestore", buy.get("restore"));
						}
						if(buy.get("profile") != null && !buy.get("profile").equals("default")) {
							config.set("general.schematicProfile", buy.get("profile"));
						}
						if(buy.get("tpx") != null) {
							config.set("general.teleportLocation.world", buy.get("world"));
							config.set("general.teleportLocation.x", Double.parseDouble(buy.get("tpx")));
							config.set("general.teleportLocation.y", Double.parseDouble(buy.get("tpy")));
							config.set("general.teleportLocation.z", Double.parseDouble(buy.get("tpz")));
							config.set("general.teleportLocation.yaw", buy.get("tpyaw"));
							config.set("general.teleportLocation.pitch", buy.get("tppitch"));
						}
						if(buy.get("playeruuid") != null) {
							config.set("buy.buyer", buy.get("playeruuid"));
							config.set("buy.buyerName", Utils.toName(buy.get("playeruuid")));
						}
						try {
							config.save(new File(regionsPath + File.separator + buy.get("name").toLowerCase() + ".yml"));
						} catch (IOException e) {
							AreaShop.warn("  Error: Could not save region file while converting: "+regionsPath+File.separator+buy.get("name").toLowerCase()+".yml");
						}
					}
					AreaShop.info("  Updated buy regions to new .yml format (check the /regions folder)");
				}
	
				// Change version number
				versions.remove("buys");		
			}

			// Separate try-catch blocks to try them all individually (don't stop after 1 has failed)
			try {
				Files.move(new File(rentPath + ".old"), new File(oldFolderPath + "rents.old"));
			} catch(Exception e) {
				// Ignore
			}
			try {
				Files.move(new File(buyPath + ".old"), new File(oldFolderPath + "buys.old"));
			} catch(Exception e) {
				// Ignore
			}
			if(buyFileFound || rentFileFound) {
				try {
					Files.move(new File(plugin.getDataFolder() + File.separator + "config.yml"), new File(oldFolderPath + "config.yml"));
				} catch(Exception e) {
					// Ignore
				}
			}
			// Update versions file to 2
			versions.put(AreaShop.versionFiles, 2);			
			saveVersions();
			AreaShop.info("  Updated to YAML based storage (v1 to v2)");
		}
	}
	
	/**
	 * Checks for old file formats and converts them to the latest format.
	 * This is to be triggered after the load of the region files
	 */
	public void postUpdateFiles() {
		Integer fileStatus = versions.get(AreaShop.versionFiles);
		
		// If the the files are already the current version
		if(fileStatus != null && fileStatus == AreaShop.versionFilesCurrent) {
			return;
		}
		
		// Add 'general.lastActive' to rented/bought regions (initialize at current time)
		if(fileStatus == null || fileStatus < 3) {
			for(GeneralRegion region : getRegions()) {
				region.updateLastActiveTime();
			}
			// Update versions file to 3
			versions.put(AreaShop.versionFiles, 3);			
			saveVersions();
			AreaShop.info("  Added last active time to regions (v2 to v3)");
		}
	}
	
	/**
	 * Get the settings of a group
	 * @param groupName Name of the group to get the settings from
	 * @return The settings of the group
	 */
	public ConfigurationSection getGroupSettings(String groupName) {
		return groupsConfig.getConfigurationSection(groupName.toLowerCase());
	}
	
	/**
	 * Set a setting for a group
	 * @param group The group to set it for
	 * @param path The path to set
	 * @param setting The value to set
	 */
	public void setGroupSetting(RegionGroup group, String path, Object setting) {
		groupsConfig.set(group.getName().toLowerCase() + "." + path, setting);
	}
}















