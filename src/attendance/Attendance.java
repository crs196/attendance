package attendance;
	
import java.io.File;
import java.io.IOException;

import org.ini4j.Ini;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class Attendance extends Application {
	
	@Override
	public void start(Stage primaryStage) {
		
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
		String cssFile = settings.get("stageSettings", "cssFile", String.class);
		String iconPath = settings.get("stageSettings", "iconPath", String.class);
		
		
		/* 
		 * Create things in reverse order:
		 * 	SignInPane	-- signs in people, tracks who hasn't signed in yet
		 * 	InitialPane	-- gets name of operator, confirms what time curfew is
		 */		
		
		// create sign in pane and scene
		SignInPane siPane = new SignInPane(primaryStage, settings);
		Scene siScene = new Scene(siPane);
		siScene.getStylesheets().add(Attendance.class.getResource(cssFile).toExternalForm());
		
		// create initial pane and scene
		Scene initialScene = new Scene(new GridPane());
		@SuppressWarnings("unused") 
		InitialPane initial = new InitialPane(primaryStage, siScene, initialScene, settings);
		initialScene.getStylesheets().add(Attendance.class.getResource(cssFile).toExternalForm());
		
		// set up stage for viewing with initial scene
		primaryStage.setTitle("Attendance");
		primaryStage.getIcons().add(new Image(iconPath));
		primaryStage.setScene(initialScene);
		
		primaryStage.show();
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
