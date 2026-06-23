package backend.passslip;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemJobScheduler {

    // Creates a daemon thread (this ensures the timer automatically dies when you close the app)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final MonitoringJdbcRepository repository = new MonitoringJdbcRepository();

    public void start247Monitor() {
        System.out.println("Starting 24/7 Database Monitor...");

        // This runs the validation immediately when the app opens, and then every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                repository.runShiftValidation();
            } catch (Exception e) {
                System.err.println("Error in 24/7 Monitor: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}