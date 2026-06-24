package com.example.frontend_emp_pass_slip;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Launcher extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/example/frontend_emp_pass_slip/view/Login.fxml")
        );
        Scene scene = new Scene(fxmlLoader.load(), 1280, 768);

        stage.setTitle("Pass Slip System");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}
