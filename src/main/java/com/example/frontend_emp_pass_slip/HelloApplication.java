package com.example.frontend_emp_pass_slip;

import backend.db.ConnectionPoolManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import backend.app.SessionManager;
import backend.app.AppSettingsManager;
import java.io.IOException;import backend.passslip.SystemJobScheduler;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        initializeDatabase();

        FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("/com/example/frontend_emp_pass_slip/view/Login.fxml")
        );

        Scene scene = new Scene(fxmlLoader.load(), 1280, 768);

        stage.setTitle("Pass Slip System");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.show();
        SystemJobScheduler monitor = new SystemJobScheduler();
        monitor.start247Monitor();

        startAutoLogout(stage);
    }

    private void initializeDatabase() {
        // Hardcoded credentials for zero-setup portability
        String url = "jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require";
        String user = "postgres.eoncagaaagkhzpltkxuu";
        String password = "PS_dataBase_13";

        ConnectionPoolManager.initialize(url, user, password, 3);
        System.out.println("Database pool initialized successfully.");

        // 👇 ADD THIS LINE HERE TO RUN THE SEEDER ON LAUNCH 👇
        backend.auth.AuthenticationService.getInstance().seedAccounts();
    }

    @Override
    public void stop() {
        try {
            ConnectionPoolManager.getInstance().shutdown();
            System.out.println("Database pool shut down.");
        } catch (IllegalStateException e) {
            // Pool not initialized
        }

        SessionManager.getInstance().stopTimer();
    }

    private void startAutoLogout(Stage stage) {
        int minutes = AppSettingsManager.getInstance().getAutoLogoutTimer();

        Runnable doLogout = () -> {
            System.out.println("Auto-logout triggered! Returning to Login.");

            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            HelloApplication.class.getResource("/com/example/frontend_emp_pass_slip/view/Login.fxml")
                    );
                    Scene loginScene = new Scene(loader.load(), 1280, 768);

                    stage.setScene(loginScene);
                    stage.centerOnScreen();

                    SessionManager.getInstance().stopTimer();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        };

        // Pre-configure rules hook constraints without running counter engine
        SessionManager.getInstance().initialize(stage, minutes, doLogout);
    }

    public static void main(String[] args) {
        launch();
    }
}