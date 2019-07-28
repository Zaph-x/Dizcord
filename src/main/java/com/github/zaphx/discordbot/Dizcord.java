package com.github.zaphx.discordbot;

import com.github.zaphx.discordbot.discord.command.*;
import com.github.zaphx.discordbot.api.commandhandler.CommandHandler;
import com.github.zaphx.discordbot.discord.listeners.*;
import com.github.zaphx.discordbot.managers.AntiSwearManager;
import com.github.zaphx.discordbot.managers.DiscordClientManager;
import com.github.zaphx.discordbot.managers.SQLManager;
import com.github.zaphx.discordbot.minecraft.commands.MainCommand;
import com.github.zaphx.discordbot.minecraft.commands.ToDiscord;
import com.github.zaphx.discordbot.utilities.DebugLogger;
import com.github.zaphx.discordbot.utilities.MultiLogger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.modules.Configuration;
import sx.blah.discord.util.DiscordException;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Dizcord extends JavaPlugin {

    // PLUGIN DESCRIPTION
    /**
     * Minecraft colour prefix
     */
    private String prefix = "§";
    /**
     * The logger used to log in Dizcord
     */
    private MultiLogger log;
    /**
     * The Dizcord instance
     */
    private static Dizcord dizcord;
    /**
     * The object for the configuration file
     */
    private FileConfiguration config;
    /**
     * The swear configuration
     */
    private FileConfiguration swearFile = new YamlConfiguration();
    /**
     * The Discord client object
     */
    private IDiscordClient client;

    /**
     * Override from {@link JavaPlugin}
     */
    @Override
    public void onEnable() {
        dizcord = this;
        this.log = new MultiLogger(this.getLogger(), new DebugLogger());
        log.info("Creating configuration files");
        createConfig();
        this.config = dizcord.getConfig();
        log.info("Configuration files created");
        log.info("Creating DiscordClientManager");
        DiscordClientManager clientManager = DiscordClientManager.getInstance();
        log.info("Configuring bot");
        Configuration.LOAD_EXTERNAL_MODULES = false;
        Configuration.AUTOMATICALLY_ENABLE_MODULES = false;

        if (!validateConfig()) {
            log.warning("The bot could not be started. Please fill in the config properly and try again.");
        } else {

            try {
                log.info("Building client");
                client = clientManager.getClient();
            } catch (DiscordException e) {
                log.severe("No client built");
                e.printStackTrace();
            }


        }
        log.info("Logging client in");
        clientManager.login(client);
        Discord4J.disableAudio();
        log.info("Created the bot successfully");

        createFile("swears.yml", this.swearFile);

        SQLManager sql = SQLManager.getInstance();

        log.info("Registering listeners");
        client.getDispatcher().registerListener(new OnReadyEvent());
        client.getDispatcher().registerListener(new MemberJoinEvent());
        client.getDispatcher().registerListener(new ChatDeleteEvent());
        client.getDispatcher().registerListener(new OnUserBanEvent());
        client.getDispatcher().registerListener(new ChatListener());
        client.getDispatcher().registerListener(new OnChannelCreateEvent());
        client.getDispatcher().registerListener(new OnChannelDeleteEvent());
        client.getDispatcher().registerListener(new OnChatEditEvent());
        client.getDispatcher().registerListener(new OnRoleCreateEvent());
        client.getDispatcher().registerListener(new OnRoleDeleteEvent());
        client.getDispatcher().registerListener(new OnRoleEditEvent());
        client.getDispatcher().registerListener(new AnyEvent());
        log.info("Listeners registered");

        log.info("Creating SQL tables if they don't exist");
        sql.createMutesIfNotExists();
        sql.createAccountLinkIfNotExists();
        sql.createWarningsIfNotExists();
        sql.createMessagesIfNotExists();
        log.info("SQL tables created");

        log.info("registering commands");
        CommandHandler commandHandler = CommandHandler.getInstance();
        commandHandler.registerCommand("help", new Help());
        commandHandler.registerCommand("mute", new Mute());
        commandHandler.registerCommand("warn", new Warn());
        commandHandler.registerCommand("adallow", new AdAllow());
        commandHandler.registerCommand("mapmessages", new MapMessages());
        commandHandler.registerCommand("linkaccounts", new AccountLink());
        commandHandler.registerCommand("unlinkaccount", new AccountUnlink());
        commandHandler.registerCommand("whois", new WhoIs());
        commandHandler.registerCommand("events", new Event());
        log.info("Commands registered");

        log.info("Registering in-game commands");
        getCommand("dizcord").setExecutor(new MainCommand());
        getCommand("todiscord").setExecutor(new ToDiscord());
        log.info("In-game commands registered");

        log.info("Loading external bot plugins!");
    }

    /**
     * Override from {@link JavaPlugin}
     */
    @Override
    public void onDisable() {
        log.info("Dizcord is shutting down");
        CommandHandler commandHandler = CommandHandler.getInstance();
        commandHandler.getCommandMap().clear();
        getLogger().log(Level.INFO, "Dizcord has successfully been disabled!");
    }

    /**
     * This method is used to create the config for the Dizcord bot.
     */
    private void createConfig() {
        if (!getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            getDataFolder().mkdirs();
        }
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            getLogger().log(Level.INFO, "No configuration found for Dizcord v" + getDescription().getVersion());
            saveDefaultConfig();
        } else {
            getLogger().log(Level.INFO, "Configuration found for Dizcord v" + getDescription().getVersion() + "!");
        }
    }

    private void createFile(String fileName, FileConfiguration yamlFile) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            getLogger().log(Level.INFO, "No swear file found for Dizcord v" + getDescription().getVersion());
            this.saveResource(fileName, false);
        }
        try {
            yamlFile.load(file);
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the instance of the Dizcord bot
     *
     * @return The Dizcord instance
     */
    public static Dizcord getInstance() {
        return dizcord;
    }

    /**
     * This method will validate the plugin config.
     *
     * @return True if config is valid
     */
    private boolean validateConfig() {
        if (config.getString("discord.token").equalsIgnoreCase("")) {
            log.warning("You must supply the plugin with a valid discord bot token");
            return false;
        }
        if (config.getLong("discord.guild-id") == 0) {
            log.warning("You must supply the plugin with a valid guild-id");
            return false;
        }
        if (config.getLong("discord.log-channel") == 0) {
            log.warning("You must supply the plugin with a valid log-channel");
            return false;
        }
        if (config.getLong("discord.rules-channel") == 0) {
            log.warning("You must supply the plugin with a valid rules-channel");
            return false;
        }
        if (config.getLong("discord.announce-channel") == 0) {
            log.warning("You must supply the plugin with a valid announce-channel");
            return false;
        }
        if (config.getLong("discord.mute-role") == 0) {
            log.warning("You must supply the plugin with a valid muted-role");
            return false;
        }
        if (config.getLong("discord.voice-mute-role") == 0) {
            log.warning("You must supply the plugin with a valid voice-muted-id");
            return false;
        }
        if (config.getLong("discord.reports-channel") == 0 && config.getBoolean("trello.enabled")) {
            log.warning("You must supply the plugin with a valid reports-channel");
            return false;
        }
        if (config.getLong("discord.suggestions-channel") == 0 && config.getBoolean("trello.enabled")) {
            log.warning("You must supply the plugin with a valid suggestions-channel");
            return false;
        }
        if (config.getBoolean("trello.enabled")) {
            if (config.getString("trello.API-key").equalsIgnoreCase("")) {
                log.warning("You must supply the plugin with a valid trello API key");
                return false;
            }
            if (config.getString("trello.API-token").equalsIgnoreCase("")) {
                log.warning("You must supply the plugin with a valid trello API token");
                return false;
            }
            if (config.getString("trello.issues").equalsIgnoreCase("")) {
                log.warning("You must supply the plugin with a valid trello issues board");

            }
            if (config.getString("trello.suggestions").equalsIgnoreCase("")) {
                log.warning("You must supply the plugin with a valid trello suggestions board");

            }
        }
        if (config.getString("sql.host").equalsIgnoreCase("")) {
            log.warning("You must supply the plugin with a valid sql host address");
            return false;
        }
        if (config.getInt("sql.port") == 0) {
            log.warning("You must supply the plugin with a valid port for the sql server");
            return false;
        }
        if (config.getString("sql.username").equalsIgnoreCase("")) {
            log.warning("You must supply the plugin with a valid sql username");
            return false;
        }
        if (config.getString("sql.database").equalsIgnoreCase("")) {
            log.warning("You must supply the plugin with a valid sql database");
            return false;
        }

        return true;

    }

    /**
     * Getter method to return the minecraft colour prefix
     *
     * @return minecraft colour prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns an instance of the Dizcord logger
     *
     * @return The Dizcord logger
     */
    public MultiLogger getLog() {
        return this.log;
    }

    public FileConfiguration getSwearFile() {
        return this.swearFile;
    }
}