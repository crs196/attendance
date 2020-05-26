package ozeret;
	
import java.io.File;
import java.time.LocalDateTime;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class OzeretMain extends Application {
	
	private static String ozeretName;
	private static LocalDateTime curfew;
	private static File attendanceFile;
	
	@Override
	public void start(Stage primaryStage) {
		/* 
		 * Create things in reverse order:
		 * 	FarewellPane	-- confirms attendance has been written, alerts of any issues
		 * 	SignInPane		-- signs in people, tracks who hasn't signed in yet
		 * 	FileChooserPane -- chooses file with attendance information
		 * 	InitialPane		-- gets name of Ozeret, confirms what time curfew is
		 */
		
		// TODO: remove this GridPane and Scene once all four other scenes are properly created
		GridPane gp = new GridPane();
		gp.add(new Button("Hey"), 0, 0);
		Scene dummy = new Scene(gp, 500, 500);
		
		// create file chooser pane and scene
		SignInPane siPane = new SignInPane(primaryStage, dummy); // TODO: replace dummy Scene with Farewell Scene
		Scene siScene = new Scene(siPane);
		siScene.getStylesheets().add(OzeretMain.class.getResource("ozeret.css").toExternalForm());
		
		// create initial pane and scene
		InitialPane initial = new InitialPane(primaryStage, siScene); 
		Scene initialScene = new Scene(initial);
		initialScene.getStylesheets().add(OzeretMain.class.getResource("ozeret.css").toExternalForm());
		
		// set up stage for viewing with inital scene
		primaryStage.setTitle("Ozeret Sign-In");
		primaryStage.setScene(initialScene);
		primaryStage.show();
	}
	
	public static void main(String[] args) {
		launch(args);
	}

	/* getters and setters */
	
	public static String getOzeretName() {
		return ozeretName;
	}

	public static void setOzeretName(String ozeretName) {
		OzeretMain.ozeretName = ozeretName;
	}

	public static LocalDateTime getCurfew() {
		return curfew;
	}

	public static void setCurfew(LocalDateTime curfew) {
		OzeretMain.curfew = curfew;
	}

	public static File getAttendanceFile() {
		return attendanceFile;
	}

	public static void setAttendanceFile(File attendanceFile) {
		OzeretMain.attendanceFile = attendanceFile;
	}
}
