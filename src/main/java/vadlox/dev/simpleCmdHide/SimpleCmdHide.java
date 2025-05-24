package vadlox.dev.simpleCmdHide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleCmdHide extends JavaPlugin implements Listener {
    private String mode;
    private String adminPermission;
    private String invalidCommandMessage;
    private String reloadMessage;
    private Map<String, GroupConfig> groupConfigMap;
    private LuckPerms luckPerms;

    public SimpleCmdHide() {
    }

    public void onEnable() {
        this.saveDefaultConfig();
        this.setupLuckPerms();
        this.loadConfiguration();
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    private void setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = this.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = (LuckPerms)provider.getProvider();
        } else {
            this.getLogger().severe("LuckPerms not found! This plugin requires LuckPerms to work.");
            this.getServer().getPluginManager().disablePlugin(this);
        }

    }

    private void loadConfiguration() {
        this.reloadConfig();
        FileConfiguration config = this.getConfig();
        this.mode = config.getString("mode", "blacklist").toLowerCase();
        this.adminPermission = config.getString("adminperm", "simplecmdhide.admin");
        this.invalidCommandMessage = this.translateColorCodes(config.getString("invalid-command-message", "&cYou do not have permission to execute this command!"));
        this.reloadMessage = this.translateColorCodes(config.getString("reload-message", "&aSimpleCmdHide configuration reloaded!"));
        this.groupConfigMap = new HashMap();
        Iterator var2;
        if (config.isConfigurationSection("groups")) {
            var2 = config.getConfigurationSection("groups").getKeys(false).iterator();

            while(var2.hasNext()) {
                String group = (String)var2.next();
                String parentGroup = config.getString("groups." + group + ".parent", (String)null);
                List<String> commands = config.getStringList("groups." + group + ".commands");
                List<String> lowerCaseCommands = new ArrayList();
                Iterator var7 = commands.iterator();

                while(var7.hasNext()) {
                    String command = (String)var7.next();
                    lowerCaseCommands.add(command.toLowerCase());
                }

                this.groupConfigMap.put(group, new GroupConfig(parentGroup, lowerCaseCommands));
            }
        }

        this.getLogger().info("Loaded groups and commands:");
        var2 = this.groupConfigMap.entrySet().iterator();

        while(var2.hasNext()) {
            Map.Entry<String, GroupConfig> entry = (Map.Entry)var2.next();
            this.getLogger().info("Group: " + (String)entry.getKey() + ", Commands: " + ((GroupConfig)entry.getValue()).commands + ", Parent: " + ((GroupConfig)entry.getValue()).parentGroup);
        }

    }

    public void onDisable() {
    }

    @EventHandler
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        if (!event.getPlayer().hasPermission(this.adminPermission)) {
            Set<String> commandsToProcess = this.getCommandsForPlayerGroup(event.getPlayer());
            if (this.mode.equalsIgnoreCase("blacklist")) {
                event.getCommands().removeAll(commandsToProcess);
            } else if (this.mode.equalsIgnoreCase("whitelist")) {
                event.getCommands().retainAll(commandsToProcess);
            }

            this.getLogger().info("Player: " + event.getPlayer().getName() + ", Available Commands: " + event.getCommands());
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().hasPermission(this.adminPermission)) {
            String message = event.getMessage();
            if (message.startsWith("/")) {
                String[] args = message.substring(1).split(" ");
                String commandLabel = args[0].toLowerCase();
                Set<String> commandsToProcess = this.getCommandsForPlayerGroup(event.getPlayer());
                boolean commandAllowed;
                if (this.mode.equalsIgnoreCase("blacklist")) {
                    commandAllowed = !commandsToProcess.contains(commandLabel);
                } else if (this.mode.equalsIgnoreCase("whitelist")) {
                    commandAllowed = commandsToProcess.contains(commandLabel);
                } else {
                    commandAllowed = true;
                }

                if (!commandAllowed) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(this.invalidCommandMessage);
                    this.getLogger().info("Blocked command '" + commandLabel + "' from player '" + event.getPlayer().getName() + "' due to group restrictions.");
                }

            }
        }
    }

    private Set<String> getCommandsForPlayerGroup(Player player) {
        Set<String> commandsToProcess = new HashSet();
        User user = this.luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String primaryGroup = user.getPrimaryGroup().toLowerCase();
            commandsToProcess.addAll(this.getCommandsForGroupAndParents(primaryGroup));
        }

        return commandsToProcess;
    }

    private Set<String> getCommandsForGroupAndParents(String group) {
        Set<String> commands = new HashSet();
        GroupConfig groupConfig = (GroupConfig)this.groupConfigMap.get(group);
        if (groupConfig != null) {
            commands.addAll(groupConfig.commands);
            if (groupConfig.parentGroup != null) {
                commands.addAll(this.getCommandsForGroupAndParents(groupConfig.parentGroup));
            }
        }

        return commands;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("simplecmdhide")) {
            if (!sender.hasPermission("simplecmdhide.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
                return true;
            } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                this.loadConfiguration();
                sender.sendMessage(this.reloadMessage);
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /simplecmdhide reload");
                return true;
            }
        } else {
            return false;
        }
    }

    private String translateColorCodes(String message) {
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while(matcher.find()) {
            String hexColor = matcher.group(1);
            StringBuilder hexReplacement = new StringBuilder("§x");
            char[] var7 = hexColor.toCharArray();
            int var8 = var7.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                char c = var7[var9];
                hexReplacement.append('§').append(c);
            }

            matcher.appendReplacement(buffer, hexReplacement.toString());
        }

        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private static class GroupConfig {
        String parentGroup;
        List<String> commands;

        public GroupConfig(String parentGroup, List<String> commands) {
            this.parentGroup = parentGroup;
            this.commands = commands;
        }
    }
}
