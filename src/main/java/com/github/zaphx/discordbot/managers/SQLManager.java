package com.github.zaphx.discordbot.managers;

import com.github.zaphx.discordbot.Dizcord;
import com.github.zaphx.discordbot.utilities.ArgumentException;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import gnu.trove.map.hash.THashMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SQLManager {

    private DiscordClientManager clientManager = DiscordClientManager.getInstance();

    private FileConfiguration config = Dizcord.getInstance().getConfig();
    private static SQLManager instance;
    public final String prefix = config.getString("sql.prefix");

    private final int PORT = config.getInt("sql.port");
    private final String USERNAME = config.getString("sql.username");
    private final String PASSWORD = config.getString("sql.password");
    private final String HOST = config.getString("sql.host");
    private final String DATABASE = config.getString("sql.database");

    // Not public constructor
    private SQLManager() {
    }

    /**
     * Gets the instance of the SQLManager
     *
     * @return A new instance if one does not exist, else the instance
     */
    public static SQLManager getInstance() {
        return instance == null ? instance = new SQLManager() : instance;
    }

    /**
     * Gets an SQL connection to the SQL server of the spigot server
     *
     * @return The connection to the SQL server the server uses
     */
    @NotNull
    private Connection getConnection() {
        String driver = "com.mysql.jdbc.Driver";
        String url = String.format("jdbc:mysql://%s:%d/%s", HOST, PORT, DATABASE);

        try {
            // Check if driver exists
            Class.forName(driver);
            return DriverManager.getConnection(url + "?useSSL=false", USERNAME, PASSWORD);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.print("An error occurred while establishing connection to the SQL server. See stacktrace below for more information.");
            e.printStackTrace();
        }
        // Should never happen
        return null;
    }

    /**
     * Checks if a table exits
     *
     * @param tableName The table to look for
     * @return True if the table exists, else false
     */
    private boolean tableExist(String tableName) {
        Connection connection = getConnection();
        boolean tExists = false;
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            while (rs.next()) {
                String tName = rs.getString("TABLE_NAME");
                if (tName != null && tName.equals(config.getString("sql.prefix") + tableName)) {
                    tExists = true;
                    break;
                }
            }
            connection.close();
        } catch (SQLException e) {
            System.err.print("An error occurred while checking if a table exists in your database. See stacktrace below for more information.");
            e.printStackTrace();
        }
        // Close connection to prevent too many open connections
        return tExists;
    }

    /**
     * Executes an SQL statement
     *
     * @param sql
     * @param parameters
     */
    public void executeStatementAndPost(@Language("sql") String sql, Object... parameters) {

        Future<Void> future = CompletableFuture.supplyAsync(() -> {
            try {
                Connection connection = getConnection();
                PreparedStatement ps = connection.prepareCall(String.format(sql, parameters));
                ps.execute();
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
        // always replace this -> ¼
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Count the entries in a table
     *
     * @param table The table to look in
     * @return The amount of entries in the table
     */
    long countTickets(String table) {
        Future<Long> future = CompletableFuture.supplyAsync(() -> {
            try {
                Connection connection = getConnection();
                PreparedStatement ps = connection.prepareCall("SELECT COUNT(ticket) AS size FROM " + prefix + table);
                List<Long> list = new ArrayList<>();
                ResultSet set = ps.executeQuery();
                while (set.next()) {
                    list.add(set.getLong("size"));
                }
                connection.close();
                return list.get(0);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0L;
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return 0L;

    }

    /**
     * Create the warnings table if it does not exist
     */
    public void createWarningsIfNotExists() {
        Future<Boolean> future = CompletableFuture.supplyAsync(() -> {
            if (!tableExist("warnings")) {
                try {
                    Connection connection = getConnection();
                    connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + prefix + "warnings (\n" +
                            "ticket INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY, \n" +
                            "id BIGINT UNSIGNED NOT NULL,\n" +
                            "reason VARCHAR(255) NOT NULL, \n" +
                            "warnee BIGINT UNSIGNED NOT NULL" +
                            ")");
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return true;
        });
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

    }

    /**
     * Create the warnings table if it does not exist
     */
    public void createMessagesIfNotExists() {
        Future<Boolean> future = CompletableFuture.supplyAsync(() -> {
            if (!tableExist("messages")) {
                try {
                    Connection connection = getConnection();
                    connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + prefix + "messages (\n" +
                            "id BIGINT UNSIGNED PRIMARY KEY NOT NULL,\n" +
                            "content text, \n" +
                            "author BIGINT UNSIGNED NOT NULL, \n" +
                            "author_name VARCHAR(255) NOT NULL, \n" +
                            "channel BIGINT UNSIGNED NOT NULL" +
                            ")");
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return true;
        });
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

    }

    /**
     * Creates a mute table if it does not exist
     */
    public void createMutesIfNotExists() {
        Future<Boolean> future = CompletableFuture.supplyAsync(() -> {
            Connection connection = getConnection();
            if (!tableExist("mutes")) {
                try {
                    String prefix = config.getString("sql.prefix");
                    connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + prefix + "mutes (\n" +
                            "ticket INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY, \n" +
                            "id BIGINT UNSIGNED NOT NULL, \n" +
                            "time DATETIME NOT NULL DEFAULT NOW(), \n" +
                            "muter BIGINT UNSIGNED NOT NULL, \n" +
                            "expires BIGINT UNSIGNED NOT NULL, \n" +
                            "type BIGINT UNSIGNED NOT NULL" +
                            ")");
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return true;
        });
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a reminder table if it does not exist
     */
    public void createAccountLinkIfNotExists() {
        Future<Boolean> future = CompletableFuture.supplyAsync(() -> {
            Connection connection = getConnection();
            if (!tableExist("reminders")) {
                try {
                    connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + prefix + "links (\n" +
                            "id VARCHAR(255) NOT NULL PRIMARY KEY, \n" +
                            "hash BIGINT UNSIGNED NOT NULL, \n" +
                            "time DATETIME NOT NULL DEFAULT NOW(), \n" +
                            "discord BIGINT NOT NULL" +
                            ")");
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return true;
        });
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes a user from the mute table when unmuted.
     */
    public void unmute() {
        Connection connection = getConnection();
        Future<Void> future = CompletableFuture.supplyAsync(() -> {
            try {
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM " + prefix + "mutes WHERE expires < UNIX_TIMESTAMP()");
                ResultSet rs = preparedStatement.executeQuery();
                while (rs.next()) {
                    clientManager.getClient().getMemberById(Snowflake.of(clientManager.GUILD_Id), Snowflake.of(rs.getLong("id"))).doOnNext(member -> {
                        try {
                            member.removeRole(Snowflake.of(rs.getLong("type")));
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }); // removeRole(clientManager.getClient().getRoleById(rs.getLong("type")));
                    executeStatementAndPost("DELETE FROM %smutes WHERE id = %s", prefix, rs.getLong("id"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method will get a deleted message and the information associated with that message
     *
     * @param id The Id of the message
     * @return A THashMap containing the information about the message
     */
    THashMap<String, String> getDeletedMessage(String id) {
        Connection connection = getConnection();
        Mono<THashMap<String, String>> map = Mono.just(connection).map(c -> {
            THashMap<String, String> message = new THashMap<>();
            try {
                ResultSet set = c.prepareStatement("SELECT * FROM " + prefix + "messages WHERE id = '" + id + "'").executeQuery();
                while (set.next()) {
                    message.put("id", id);
                    message.put("content", set.getString("content"));
                    message.put("author", set.getString("author"));
                    message.put("authorName", set.getString("author_name"));
                    message.put("channel", set.getString("channel"));
                }
                c.prepareStatement("DELETE FROM " + prefix + "messages WHERE id = '" + id + "'").execute();
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();

            }
            return message;
        });
        return map.block();
    }

    /**
     * Checks if a user has linked their discord account to their minecraft account
     *
     * @param discordId     The string representation of the Discord Id
     * @param minecraftUUId The UUId of the player we are looking for
     * @return The truth value of the existence of a link in the database
     */
    public boolean isUserLinked(String discordId, UUID minecraftUUId) {
        Connection connection = getConnection();
        Future<Boolean> future = CompletableFuture.supplyAsync(() -> {
            boolean isLinked;

            try {
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + prefix + "links WHERE id = '" + minecraftUUId.toString() + "' OR discord = '" + discordId + "'");
                ResultSet resultSet = statement.executeQuery();

                isLinked = resultSet.next();
                return isLinked;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks if a user has linked their discord account to their minecraft account
     *
     * @param discordId The string representation of the Discord Id
     * @return The truth value of the existence of a link in the database
     */
    public boolean isUserLinked(String discordId) {
        Connection connection = getConnection();
        Future<Boolean> future = CompletableFuture.supplyAsync(() -> {
            boolean isLinked;

            try {
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + prefix + "links WHERE discord = '" + discordId + "'");
                ResultSet resultSet = statement.executeQuery();

                isLinked = resultSet.next();
                return isLinked;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getPlayerFromLink(Member user) {
        Connection connection = getConnection();
        Future<String> future = CompletableFuture.supplyAsync(() -> {

            try {
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + prefix + "links WHERE discord = '" + user.getId().asString() + "'");
                ResultSet set = statement.executeQuery();

                while (set.next()) {
                    return set.getString("id");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;

        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Adds a message to the SQL database
     *
     * @param message The message to add
     */
    void addMessage(Message message) {
        User u = message.getAuthor().orElseThrow(ArgumentException::new);
        TextChannel c = message.getChannel().filter(ch -> ch instanceof TextChannel).cast(TextChannel.class).block();
        executeStatementAndPost("INSERT INTO " + prefix + "messages (id, content, author, author_name, channel) values ('%s','%s','%s','%s','%s')",
                message.getId().asString(), message.getContent().get().replaceAll("'", "¼"), u.getId().asString(), u.getUsername(), c.getId().asString());
    }
}