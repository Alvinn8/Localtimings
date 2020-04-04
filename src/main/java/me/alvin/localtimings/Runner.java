package me.alvin.localtimings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;

public class Runner {
    public static void main(String[] args) {
        try {
            URL url = Runner.class.getResource("/timing.txt");
            if (url == null) {
                System.err.println("Please place a timing.txt file inside the resources folder.");
                return;
            }
            Path file = Paths.get(url.toURI());
            String data = new String(Files.readAllBytes(file));

            HttpURLConnection con = (HttpURLConnection) new URL("http://timings.aikar.co/post").openConnection();

            con.setDoOutput(true);

            // con.setRequestProperty("User-Agent", "Paper/" + Bukkit.getUnsafe().getTimingsServerName() + "/" + hostName);
            con.setRequestProperty("User-Agent", "Paper/unknown/unknown");

            con.setRequestMethod("POST");
            con.setInstanceFollowRedirects(false);

            OutputStream request = new GZIPOutputStream(con.getOutputStream()) {{
                this.def.setLevel(7);
            }};

            try {
                request.write(data.getBytes("UTF-8"));
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                request.close();
            }

            String response = getResponse(con);

            if (con.getResponseCode() != 302) {
                System.err.println("Upload Error: " + con.getResponseCode() + ": " + con.getResponseMessage());
                if (response != null) {
                    System.err.println(response);
                }
                return;
            }

            if (response != null && !response.isEmpty()) {
                System.out.println("Timing Response: " + response);
            }

            String timingsURL = con.getHeaderField("Location");
            System.out.println(("View Timings Report: " + timingsURL));
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private static String getResponse(HttpURLConnection con) throws IOException {
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
            System.out.println(("Error uploading timings, check your logs for more information"));
            System.out.println("[WARN] "+ con.getResponseMessage() + ", "+ ex);
            return null;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }
}
