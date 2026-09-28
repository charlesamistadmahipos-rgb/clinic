import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    // One booked appointment (built into this file so no other file is needed)
    static class Appointment {
        String name;
        String id;
        int age;
        String date;
        String time;
        String reason;

        Appointment(String name, String id, int age, String date, String time, String reason) {
            this.name = name;
            this.id = id;
            this.age = age;
            this.date = date;
            this.time = time;
            this.reason = reason;
        }
    }

    static final int PORT = 8080;

   
    static final Path WEB_DIR = Paths.get(".");

    static final List<Appointment> appointments = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/appointments", Main::handleAppointments);
        server.createContext("/", Main::serveWebsite);

        server.start();
        System.out.println("Server running at http://localhost:" + PORT);
    }



    static void serveWebsite(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(exchange, "405 - Method not allowed", 405);
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            sendFile(exchange, "index.html", "text/html; charset=utf-8");
        } else if (path.equals("/style.css")) {
            sendFile(exchange, "style.css", "text/css; charset=utf-8");
        } else if (path.equals("/script.js")) {
            sendFile(exchange, "script.js", "text/javascript; charset=utf-8");
        } else if (path.equals("/hero.svg")) {
            sendFile(exchange, "hero.svg", "image/svg+xml");
        } else {
            sendResponse(exchange, "404 - Page not found", 404);
        }
    }

    static void sendFile(HttpExchange exchange, String fileName, String contentType)
            throws IOException {
        Path file = WEB_DIR.resolve(fileName);

        if (!Files.exists(file)) {
            sendResponse(exchange, "404 - File not found: " + fileName, 404);
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendResponse(HttpExchange exchange, String body, int status)
            throws IOException {
        send(exchange, body, status, "text/plain; charset=utf-8");
    }

    static void sendJson(HttpExchange exchange, String json, int status)
            throws IOException {
        send(exchange, json, status, "application/json; charset=utf-8");
    }

    static void send(HttpExchange exchange, String body, int status, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---------- API ----------

    static void handleAppointments(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (method.equals("GET")) {
            sendJson(exchange, appointmentsToJson(), 200);
        } else if (method.equals("POST")) {
            addAppointment(exchange);
        } else {
            sendJson(exchange, error("Method not allowed"), 405);
        }
    }

    static void addAppointment(HttpExchange exchange) throws IOException {
        String body = new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
        Map<String, String> form = parseForm(body);

        String name = form.getOrDefault("name", "").trim();
        String id = form.getOrDefault("id", "").trim();
        String date = form.getOrDefault("date", "").trim();
        String time = form.getOrDefault("time", "").trim();
        String reason = form.getOrDefault("reason", "").trim();

        if (name.isEmpty() || id.isEmpty() || date.isEmpty() || time.isEmpty() || reason.isEmpty()) {
            sendJson(exchange, error("All fields are required"), 400);
            return;
        }

        int age;
        try {
            age = Integer.parseInt(form.getOrDefault("age", "").trim());
        } catch (NumberFormatException e) {
            sendJson(exchange, error("Age must be a number"), 400);
            return;
        }

        if (age < 0 || age > 120) {
            sendJson(exchange, error("Age must be between 0 and 120"), 400);
            return;
        }

        synchronized (appointments) {
            for (Appointment a : appointments) {
                if (a.date.equals(date) && a.time.equals(time)) {
                    sendJson(exchange, error("That time slot is already booked"), 409);
                    return;
                }
            }
            appointments.add(new Appointment(name, id, age, date, time, reason));
        }

        sendJson(exchange, "{\"message\":\"Appointment added\"}", 201);
    }

    static String appointmentsToJson() {
        StringBuilder sb = new StringBuilder("[");

        synchronized (appointments) {
            for (int i = 0; i < appointments.size(); i++) {
                Appointment a = appointments.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                  .append("\"name\":\"").append(escape(a.name)).append("\",")
                  .append("\"id\":\"").append(escape(a.id)).append("\",")
                  .append("\"age\":").append(a.age).append(",")
                  .append("\"date\":\"").append(escape(a.date)).append("\",")
                  .append("\"time\":\"").append(escape(a.time)).append("\",")
                  .append("\"reason\":\"").append(escape(a.reason)).append("\"")
                  .append("}");
            }
        }

        return sb.append("]").toString();
    }

    static Map<String, String> parseForm(String body) throws IOException {
        Map<String, String> map = new HashMap<>();

        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], "UTF-8");
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
            map.put(key, value);
        }

        return map;
    }

    static String error(String message) {
        return "{\"error\":\"" + escape(message) + "\"}";
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}