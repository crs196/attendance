package attendance;
	
import java.io.File;
import java.io.IOException;

import org.ini4j.Ini;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class Attendance extends Application {
	
	@Override
	public void start(Stage primaryStage) {
		createScene(primaryStage);
		
	}
	
	public static void createScene(Stage primaryStage) {
		
		// start by obtaining configuration settings from file
		Ini settings = null;
		try {
			settings = new Ini(new File("config.ini"));
		} catch (IOException e) {
			System.out.println("The \"config.ini\" file was not found in the current directory. "
					+ "Please create this file and rerun this program");
			Platform.exit();
		}
				
		// obtain stage settings from config file
		String cssPath = settings.get("filePaths", "cssPath");
		String iconPath = settings.get("filePaths", "iconPath");
		
		// create sign in pane and scene
		GridPane siPane = null;
		String mode = settings.get("settings", "mode").toLowerCase(); // get what mode to run in
		if (mode.equals("gate")) // gate mode: create gate pane
			siPane = new GatePane(primaryStage, settings);
		else if (mode.equals("ozeret")) // ozeret mode: create ozeret pane
			siPane = null; // TODO: create OzeretPane
			
		if (siPane != null) { // if a valid mode was specified
			Scene siScene = new Scene(siPane);
			siScene.getStylesheets().add(Attendance.class.getResource(cssPath).toExternalForm());
			
			// set up stage for viewing
			primaryStage.setTitle("Attendance");
			primaryStage.getIcons().add(new Image(iconPath));
			primaryStage.setScene(siScene);
			
			primaryStage.show();
		} else { // if the mode is invalid, alert the user
			Alert invalidMode = new Alert(AlertType.ERROR, "\"" + mode + "\" is not a valid mode. Please specify a valid mode in \"config.ini\""
					+ "\nValid modes are \"ozeret\" and \"gate\"");
			invalidMode.setTitle("Invalid Time Format");
			invalidMode.getDialogPane().getStylesheets().add(Attendance.class.getResource(cssPath).toExternalForm());
			invalidMode.showAndWait();
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
