package ozeret;

import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class SignInPane extends GridPane {

	private Stage stage;
	private Scene nextScene;
	
	public SignInPane(Stage s, Scene ns) {
		super();
		stage = s;
		nextScene = ns;
		
		setup();
	}
	
	private void setup() {
		// TODO: create signinpane
	}
	
}
