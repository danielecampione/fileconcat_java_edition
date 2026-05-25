package it.fileconcat_java_edition;

import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        MergeGUI gui = new MergeGUI(primaryStage);
        gui.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
