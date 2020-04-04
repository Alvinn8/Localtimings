package me.alvin.localtimings;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LocalTimingsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] strings) {
        if (!sender.hasPermission("localtimings.command")) {
            sender.sendMessage("§cYou do not have permission to execute this command.");
            return true;
        }
        LocalTimingsExport.requestingReport.add(sender);
        LocalTimingsExport.reportTimings();
        return true;
    }
}
