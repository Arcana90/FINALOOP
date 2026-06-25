package backend.app;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppSettingsManager {
    private static AppSettingsManager instance;
    private final SettingsRepository repository = new SettingsRepository();

    private String timeFormat = "24h";
    private String dateFormat = "YYYY-MM-DD";
    private int autoLogoutMinutes = 30;

    private Timeline inactivityTimeline;
    private Runnable logoutRoutine;

    // A list to hold anyone listening for setting changes
    private final List<Runnable> settingsListeners = new ArrayList<>();

    private AppSettingsManager() {
        refreshSettings();
    }

    public static synchronized AppSettingsManager getInstance() {
        if (instance == null) {
            instance = new AppSettingsManager();
        }
        return instance;
    }

    // Method to register a listener
    public void addSettingsChangedListener(Runnable listener) {
        settingsListeners.add(listener);
    }

    // Load or refresh data directly from DB cache
    public void refreshSettings() {
        // 🌟 FIXED: Query the correct AuthSessionManager to get the active user
        String currentUser = backend.auth.AuthSessionManager.getInstance().getCurrentUsername();

        // Safety check: If no user is logged in yet, don't attempt to load DB settings
        if (currentUser == null || currentUser.isEmpty()) {
            return;
        }

        Map<String, String> settings = repository.loadSettings(currentUser);

        this.timeFormat = settings.getOrDefault("time_format", "24h");
        this.dateFormat = settings.getOrDefault("date_format", "YYYY-MM-DD");
        try {
            this.autoLogoutMinutes = Integer.parseInt(settings.getOrDefault("auto_logout_minutes", "30"));
        } catch (NumberFormatException e) {
            this.autoLogoutMinutes = 30;
        }

        // If a window is currently active, reset the timer to apply the new duration limit immediately
        if (inactivityTimeline != null) {
            resetTimer();
        }

        // Broadcast the update to all listeners on the UI thread
        for (Runnable listener : settingsListeners) {
            Platform.runLater(listener);
        }
    }

    // --- GLOBAL FORMATTING HELPERS ---
    public DateTimeFormatter getDateFormatter() {
        switch (dateFormat) {
            case "DD/MM/YYYY": return DateTimeFormatter.ofPattern("dd/MM/yyyy");
            case "MM/DD/YYYY": return DateTimeFormatter.ofPattern("MM/dd/yyyy");
            default: return DateTimeFormatter.ofPattern("yyyy-MM-dd");
        }
    }

    public DateTimeFormatter getTimeFormatter() {
        return timeFormat.equals("12h") ? DateTimeFormatter.ofPattern("hh:mm a") : DateTimeFormatter.ofPattern("HH:mm");
    }

    public String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        String pattern = dateFormat.replace("YYYY", "yyyy").replace("DD", "dd") + " " + (timeFormat.equals("12h") ? "hh:mm a" : "HH:mm");
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    public String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(getDateFormatter());
    }

    public String formatTime(LocalTime time) {
        if (time == null) return "";
        return time.format(getTimeFormatter());
    }

    // --- GLOBAL AUTO-LOGOUT MECHANISM ---
    public void registerInactivityTracker(Scene scene, Runnable onLogoutAction) {
        this.logoutRoutine = onLogoutAction;

        if (inactivityTimeline != null) {
            inactivityTimeline.stop();
        }

        // Configure dynamic trigger countdown duration
        inactivityTimeline = new Timeline(new KeyFrame(Duration.minutes(autoLogoutMinutes), event -> {
            System.out.println("User inactivity limit reached. Initiating auto-logout.");
            if (logoutRoutine != null) {
                logoutRoutine.run();
            }
        }));
        inactivityTimeline.setCycleCount(1);
        inactivityTimeline.play();

        // Listen for ANY user interactions on this Window/Scene context
        scene.addEventFilter(MouseEvent.ANY, event -> resetTimer());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> resetTimer());
    }

    private void resetTimer() {
        if (inactivityTimeline != null) {
            inactivityTimeline.stop();
            inactivityTimeline.playFromStart();
        }
    }

    public void stopTracker() {
        if (inactivityTimeline != null) {
            inactivityTimeline.stop();
        }
    }

    // --- STRING PARSING HELPERS ---
    public String formatDateString(String dbDate) {
        if (dbDate == null || dbDate.isEmpty() || dbDate.equalsIgnoreCase("null")) return "";
        try {
            // Strip out time if the DB accidentally includes it in a date field
            if (dbDate.contains(" ")) dbDate = dbDate.split(" ")[0];
            return formatDate(LocalDate.parse(dbDate));
        } catch (Exception e) {
            return dbDate; // Fallback to raw string if parsing fails
        }
    }

    public String formatTimeString(String dbTime) {
        if (dbTime == null || dbTime.isEmpty() || dbTime.equalsIgnoreCase("null") || dbTime.equals("-")) {
            return "N/A";
        }
        try {
            // 1. If it's a full timestamp ("2026-06-12 11:36:52"), extract the time component
            if (dbTime.contains(" ")) {
                String[] parts = dbTime.split(" ");
                dbTime = parts[parts.length - 1];
            }

            // 2. Truncate high-precision database sub-seconds/nanoseconds (".618933")
            if (dbTime.contains(".")) {
                dbTime = dbTime.split("\\.")[0];
            }

            // Now dbTime is a clean "HH:mm:ss" or "HH:mm" string
            return formatTime(LocalTime.parse(dbTime));
        } catch (Exception e) {
            // Fallback to raw string gracefully if parsing fails
            return dbTime;
        }
    }

    // Returns the auto-logout time in minutes
    public int getAutoLogoutTimer() {
        return this.autoLogoutMinutes;
    }
}