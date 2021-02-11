package ozeret;
	
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class OzeretMain extends Application {
	
	@Override
	public void start(Stage primaryStage) {
		/* 
		 * Create things in reverse order:
		 * 	SignInPane	-- signs in people, tracks who hasn't signed in yet
		 * 	InitialPane	-- gets name of Ozeret, confirms what time curfew is
		 */		
		
		// create sign in pane and scene
		SignInPane siPane = new SignInPane(primaryStage);
		Scene siScene = new Scene(siPane);
		siScene.getStylesheets().add(OzeretMain.class.getResource("ozeret.css").toExternalForm());
		
		// create initial pane and scene
		Scene initialScene = new Scene(new GridPane());
		@SuppressWarnings("unused") InitialPane initial = new InitialPane(primaryStage, siScene, initialScene);
		initialScene.getStylesheets().add(OzeretMain.class.getResource("ozeret.css").toExternalForm());
		
		// set up stage for viewing with initial scene
		primaryStage.setTitle("Ozeret Sign-In");
		primaryStage.getIcons().add(new Image("file:resources/images/stage_icon.png"));
		primaryStage.setScene(initialScene);
		
		primaryStage.show();
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
