package me.alvin.localtimings;

import com.google.common.base.Charsets;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class LocalTimings extends JavaPlugin {
    private static LocalTimings instance;

    public static LocalTimings getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        getCommand("LocalTimings").setExecutor(new LocalTimingsCommand());
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public void saveTimingData(String json) throws IOException {
        if (!this.getDataFolder().exists()) this.getDataFolder().mkdir();

        File file = new File(this.getDataFolder(), "timing.txt");

        Writer writer = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);

        try {
            writer.write(json);
        } finally {
            writer.close();
        }
    }
}
