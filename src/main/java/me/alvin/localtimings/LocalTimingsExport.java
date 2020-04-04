/*
 * EDITED BY ALVIN, ORIGINAL LICENSE BELOW.
 * ----------------------------------------
*/

/*
 *
 * This file is licensed under the MIT License (MIT).
 *
 * Copyright (c) 2014 Daniel Ennis <http://aikar.co>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.alvin.localtimings;

import co.aikar.timings.TimingHistory;
import co.aikar.timings.Timings;
import co.aikar.timings.TimingsManager;
import co.aikar.timings.TimingsReportListener;
import co.aikar.util.JSONUtil;
import com.google.common.base.Function;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.EntityType;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import static co.aikar.util.JSONUtil.appendObjectData;
import static co.aikar.util.JSONUtil.createObject;
import static co.aikar.util.JSONUtil.pair;
import static co.aikar.util.JSONUtil.toArray;
import static co.aikar.util.JSONUtil.toArrayMapper;
import static co.aikar.util.JSONUtil.toObjectMapper;

@SuppressWarnings({"rawtypes", "SuppressionAnnotation"})
public class LocalTimingsExport extends Thread {

    private final TimingsReportListener listeners;
    private final Map out;
    private final TimingHistory[] history;
    private static long lastReport = 0;
    public final static List<CommandSender> requestingReport = Lists.newArrayList();

    private static Object get(String fieldName, Object object) {
        Field field = null;
        try {
            field = object.getClass().getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        field.setAccessible(true);
        try {
            return field.get(object);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Object getStatic(String fieldName, Class<?> clazz) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        field.setAccessible(true);
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private LocalTimingsExport(TimingsReportListener listeners, Map out, TimingHistory[] history) {
        super("Timings paste thread");
        this.listeners = listeners;
        this.out = out;
        this.history = history;
    }

    /**
     * Checks if any pending reports are being requested, and builds one if needed.
     */
    public static void reportTimings() {
        if (requestingReport.isEmpty()) {
            return;
        }
        TimingsReportListener listeners = new TimingsReportListener(requestingReport);
        listeners.addConsoleIfNeeded();

        requestingReport.clear();
        long now = System.currentTimeMillis();
        final long lastReportDiff = now - lastReport;
        if (lastReportDiff < 60000) {
            listeners.sendMessage(ChatColor.RED + "Please wait at least 1 minute in between Timings reports. (" + (int)((60000 - lastReportDiff) / 1000) + " seconds)");
            listeners.done();
            return;
        }
        Field field = null;
        try {
            field =TimingsManager.class.getDeclaredField("timingStart");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return;
        }
        field.setAccessible(true);
        long timingStart;
        try {
            timingStart = (long) field.get(null);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return;
        }
        final long lastStartDiff = now - timingStart;
        if (lastStartDiff < 180000) {
            listeners.sendMessage(ChatColor.RED + "Please wait at least 3 minutes before generating a Timings report. Unlike Timings v1, v2 benefits from longer timings and is not as useful with short timings. (" + (int)((180000 - lastStartDiff) / 1000) + " seconds)");
            listeners.done();
            return;
        }
        listeners.sendMessage(ChatColor.GREEN + "Preparing Timings Report...");
        lastReport = now;
        Map parent = createObject(
                // Get some basic system details about the server
                pair("version", Bukkit.getVersion()),
                pair("maxplayers", Bukkit.getMaxPlayers()),
                pair("start", timingStart / 1000),
                pair("end", System.currentTimeMillis() / 1000),
                pair("sampletime", (System.currentTimeMillis() - timingStart) / 1000)
        );
        if (!TimingsManager.privacy) {
            appendObjectData(parent,
                    pair("server", Bukkit.getUnsafe().getTimingsServerName()),
                    pair("motd", Bukkit.getServer().getMotd()),
                    pair("online-mode", Bukkit.getServer().getOnlineMode()),
                    pair("icon", Bukkit.getServer().getServerIcon().getData())
            );
        }

        final Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        parent.put("system", createObject(
                pair("timingcost", getCost()),
                pair("name", System.getProperty("os.name")),
                pair("version", System.getProperty("os.version")),
                pair("jvmversion", System.getProperty("java.version")),
                pair("arch", System.getProperty("os.arch")),
                pair("maxmem", runtime.maxMemory()),
                pair("cpu", runtime.availableProcessors()),
                pair("runtime", ManagementFactory.getRuntimeMXBean().getUptime()),
                pair("flags", StringUtils.join(runtimeBean.getInputArguments(), " ")),
                pair("gc", toObjectMapper(ManagementFactory.getGarbageCollectorMXBeans(), input -> pair(input.getName(), toArray(input.getCollectionCount(), input.getCollectionTime()))))
                )
        );

        Set<Material> tileEntityTypeSet = Sets.newHashSet();
        Set<EntityType> entityTypeSet = Sets.newHashSet();

        EvictingQueue<TimingHistory> HISTORY = (EvictingQueue<TimingHistory>) getStatic("HISTORY", TimingsManager.class);

        int size = HISTORY.size();
        TimingHistory[] history = new TimingHistory[size + 1];
        int i = 0;
        for (TimingHistory timingHistory : HISTORY) {
            tileEntityTypeSet.addAll((Collection<? extends Material>) get("tileEntityTypeSet", timingHistory));
            entityTypeSet.addAll((Collection<? extends EntityType>) get("entityTypeSet", timingHistory));
            history[i++] = timingHistory;
        }

        try {
            Constructor constructor = TimingHistory.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            history[i] = (TimingHistory) constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return;
        }
        ; // Current snapshot
        tileEntityTypeSet.addAll((Collection<? extends Material>) get("tileEntityTypeSet", history[i]));
        entityTypeSet.addAll((Collection<? extends EntityType>) get("entityTypeSet", history[i]));


        Map handlers = createObject();
        Map groupData;

        Class<?> timingIdentifier = null;
        try {
            timingIdentifier = Class.forName("co.aikar.timings.TimingIdentifier");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Field group_map = null;
        try {
            group_map = timingIdentifier.getDeclaredField("GROUP_MAP");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        group_map.setAccessible(true);
        Object GROUP_MAP = null;
        try {
            GROUP_MAP = group_map.get(null);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        synchronized (GROUP_MAP) {
            Method valuesMethod = null;
            try {
                valuesMethod = GROUP_MAP.getClass().getDeclaredMethod("values");
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            valuesMethod.setAccessible(true);
            Collection values = null;
            try {
                values = (Collection) valuesMethod.invoke(GROUP_MAP);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            for (Object group : values) {
                // TimingIdentifier.TimingGroup
                Field handlersField = null;

                try {
                    handlersField = group.getClass().getDeclaredField("handlers");
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
                handlersField.setAccessible(true);
                List handlers2 = null;
                try {
                    handlers2 = (List) handlersField.get(group);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                synchronized (handlers2) {
                    for (Object id : handlers2) {
                        // TimingHandler

                        Method isTimed = null;
                        try {
                            isTimed = Class.forName("co.aikar.timings.TimingHandler").getDeclaredMethod("isTimed");
                        } catch (NoSuchMethodException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        isTimed.setAccessible(true);

                        Method isSpecial = null;
                        try {
                            isSpecial = Class.forName("co.aikar.timings.TimingHandler").getDeclaredMethod("isSpecial");
                        } catch (NoSuchMethodException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        isSpecial.setAccessible(true);

                        try {
                            if (!((boolean) isTimed.invoke(id)) && !((boolean) isSpecial.invoke(id))) {
                                continue;
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }

                        Field identifierField = null;
                        try {
                            identifierField = Class.forName("co.aikar.timings.TimingHandler").getDeclaredField("identifier");
                        } catch (NoSuchFieldException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        identifierField.setAccessible(true);
                        Object identifier = null;
                        try {
                            identifier = identifierField.get(id);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                        Field nameField = null;
                        try {
                            nameField = identifier.getClass().getDeclaredField("name");
                        } catch (NoSuchFieldException e) {
                            e.printStackTrace();
                        }
                        nameField.setAccessible(true);
                        String name = null;
                        try {
                            name = (String) nameField.get(identifier);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                        if (name.startsWith("##")) {
                            name = name.substring(3);
                        }

                        Field idField = null;
                        try {
                            idField = Class.forName("co.aikar.timings.TimingHandler").getDeclaredField("id");
                        } catch (NoSuchFieldException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        idField.setAccessible(true);
                        int idid = 0;
                        try {
                            idid = idField.getInt(id);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                        Field groupIdFField = null;
                        try {
                            groupIdFField = group.getClass().getDeclaredField("id");
                        } catch (NoSuchFieldException e) {
                            e.printStackTrace();
                        }
                        groupIdFField.setAccessible(true);
                        int groupid = 0;
                        try {
                            groupid = groupIdFField.getInt(group);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                        handlers.put(idid, toArray(
                                groupid,
                                name
                        ));
                    }
                }
            }

            groupData = toObjectMapper(
                    values, group -> a(group));
        }


        parent.put("idmap", createObject(
                pair("groups", groupData),
                pair("handlers", handlers),
                pair("worlds", toObjectMapper(((Map<String, Integer>) getStatic("worldMap", TimingHistory.class)).entrySet(), input -> pair(input.getValue(), input.getKey()))),
                pair("tileentity",
                        toObjectMapper(tileEntityTypeSet, input -> pair(input.ordinal(), input.name()))),
                pair("entity",
                        toObjectMapper(entityTypeSet, input -> pair(input.ordinal(), input.name())))
        ));

        // Information about loaded plugins

        parent.put("plugins", toObjectMapper(Bukkit.getPluginManager().getPlugins(),
                plugin -> pair(plugin.getName(), createObject(
                        pair("version", plugin.getDescription().getVersion()),
                        pair("description", String.valueOf(plugin.getDescription().getDescription()).trim()),
                        pair("website", plugin.getDescription().getWebsite()),
                        pair("authors", StringUtils.join(plugin.getDescription().getAuthors(), ", "))
                ))));



        // Information on the users Config

        parent.put("config", createObject(
                pair("spigot", mapAsJSON(Bukkit.spigot().getSpigotConfig(), null)),
                pair("bukkit", mapAsJSON(Bukkit.spigot().getBukkitConfig(), null)),
                pair("paper", mapAsJSON(Bukkit.spigot().getPaperConfig(), null))
        ));

        new LocalTimingsExport(listeners, parent, history).start();
    }

    private static JSONUtil.JSONPair a(Object group) {
        Field groupidField = null;
        try {
            groupidField = group.getClass().getDeclaredField("id");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        groupidField.setAccessible(true);
        int groupid = 0;
        try {
            groupid = groupidField.getInt(group);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        Field groupNameField = null;
        try {
            groupNameField = group.getClass().getDeclaredField("name");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        groupNameField.setAccessible(true);
        String groupname = null;
        try {
            groupname = (String) groupNameField.get(group);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return pair(groupid, groupname);
    }

    private static Object newTimingHandler(String name) {
        Method method = null;
        try {
            method = Timings.class.getDeclaredMethod("ofSafe", String.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        method.setAccessible(true);
        try {
            return method.invoke(null, name);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void startTiming(Object sampler) {
        Method method = null;
        try {
            method = sampler.getClass().getDeclaredMethod("startTiming");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        method.setAccessible(true);
        try {
            method.invoke(sampler);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static void stopTiming(Object sampler) {
        Method method = null;
        try {
            method = sampler.getClass().getDeclaredMethod("stopTiming");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        method.setAccessible(true);
        try {
            method.invoke(sampler);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static void reset(Object sampler) {
        Method method = null;
        try {
            method = sampler.getClass().getDeclaredMethod("reset", boolean.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        method.setAccessible(true);
        try {
            method.invoke(sampler, true);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    static long getCost() {
        // Benchmark the users System.nanotime() for cost basis
        int passes = 100;

        Object SAMPLER1 = newTimingHandler("Timings Sampler 1");
        Object SAMPLER2 = newTimingHandler("Timings Sampler 2");
        Object SAMPLER3 = newTimingHandler("Timings Sampler 3");
        Object SAMPLER4 = newTimingHandler("Timings Sampler 4");
        Object SAMPLER5 = newTimingHandler("Timings Sampler 5");
        Object SAMPLER6 = newTimingHandler("Timings Sampler 6");

        long start = System.nanoTime();
        for (int i = 0; i < passes; i++) {
            startTiming(SAMPLER1);
            startTiming(SAMPLER2);
            startTiming(SAMPLER3);
            stopTiming(SAMPLER3);
            startTiming(SAMPLER4);
            startTiming(SAMPLER5);
            startTiming(SAMPLER6);
            stopTiming(SAMPLER6);
            stopTiming(SAMPLER5);
            stopTiming(SAMPLER4);
            stopTiming(SAMPLER2);
            stopTiming(SAMPLER1);
        }
        long timingsCost = (System.nanoTime() - start) / passes / 6;
        reset(SAMPLER1);
        reset(SAMPLER2);
        reset(SAMPLER3);
        reset(SAMPLER4);
        reset(SAMPLER5);
        reset(SAMPLER6);
        return timingsCost;
    }

    private static JSONObject mapAsJSON(ConfigurationSection config, String parentKey) {

        JSONObject object = new JSONObject();
        for (String key : config.getKeys(false)) {
            String fullKey = (parentKey != null ? parentKey + "." + key : key);
            if (fullKey.equals("database") || fullKey.equals("settings.bungeecord-addresses") || TimingsManager.hiddenConfigs.contains(fullKey)) {
                continue;
            }
            final Object val = config.get(key);

            object.put(key, valAsJSON(val, fullKey));
        }
        return object;
    }

    private static Object valAsJSON(Object val, final String parentKey) {
        if (!(val instanceof MemorySection)) {
            if (val instanceof List) {
                Iterable<Object> v = (Iterable<Object>) val;
                return toArrayMapper(v, input -> valAsJSON(input, parentKey));
            } else {
                return val.toString();
            }
        } else {
            return mapAsJSON((ConfigurationSection) val, parentKey);
        }
    }

    public class ExportClass<E, Object> implements Function<E, Object> {

        @Nullable
        @Override
        public Object apply(@Nullable E e) {
            Method export = null;
            try {
                export = TimingHistory.class.getDeclaredMethod("export");
            } catch (NoSuchMethodException e1) {
                e1.printStackTrace();
            }
            export.setAccessible(true);
            try {
                return (Object) export.invoke(e);
            } catch (IllegalAccessException | InvocationTargetException e1) {
                e1.printStackTrace();
            }
            return null;
        }
    }

    @Override
    public void run() {
        out.put("data", toArrayMapper(history, new ExportClass<>()));

        try {
            LocalTimings.getInstance().saveTimingData(JSONValue.toJSONString(out));

            this.listeners.sendMessage("The report has been saved to the timing.txt file.");
        } catch (IOException e) {
            e.printStackTrace();
            this.listeners.sendMessage("Failed to save the timing.txt, check the console for more info");
        }

            /*
            String response = null;
            String timingsURL = null;

            HttpURLConnection con = (HttpURLConnection) new URL("http://timings.aikar.co/post").openConnection();
            con.setDoOutput(true);
            String hostName = "BrokenHost";
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {}
            con.setRequestProperty("User-Agent", "Paper/" + Bukkit.getUnsafe().getTimingsServerName() + "/" + hostName);
            con.setRequestMethod("POST");
            con.setInstanceFollowRedirects(false);

            OutputStream request = new GZIPOutputStream(con.getOutputStream()) {{
                this.def.setLevel(7);
            }};

            request.write(JSONValue.toJSONString(out).getBytes("UTF-8"));
            request.close();

            response = getResponse(con);

            if (con.getResponseCode() != 302) {
                listeners.sendMessage(
                        ChatColor.RED + "Upload Error: " + con.getResponseCode() + ": " + con.getResponseMessage());
                listeners.sendMessage(ChatColor.RED + "Check your logs for more information");
                if (response != null) {
                    Bukkit.getLogger().log(Level.SEVERE, response);
                }
                return;
            }

            timingsURL = con.getHeaderField("Location");
            listeners.sendMessage(ChatColor.GREEN + "View Timings Report: " + timingsURL);

            if (response != null && !response.isEmpty()) {
                Bukkit.getLogger().log(Level.INFO, "Timing Response: " + response);
            }
            */
    }

    private String getResponse(HttpURLConnection con) throws IOException {
        InputStream is = null;
        try {
            is = con.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] b = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(b)) != -1) {
                bos.write(b, 0, bytesRead);
            }
            return bos.toString();

        } catch (IOException ex) {
            listeners.sendMessage(ChatColor.RED + "Error uploading timings, check your logs for more information");
            Bukkit.getLogger().log(Level.WARNING, con.getResponseMessage(), ex);
            return null;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }
}