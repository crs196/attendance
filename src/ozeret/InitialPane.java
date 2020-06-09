package ozeret;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class InitialPane extends GridPane {

	private Stage stage;
	private Scene nextScene, myScene;
	
	public InitialPane(Stage s, Scene ns, Scene ms) {
		super();
		stage = s;
		nextScene = ns;
		myScene = ms;
		
		myScene.setRoot(this);
		
		setup();
	}
	
	// sets up layout and functionality of InitalPane
	private void setup() {
		
		// set up grid layout and sizing
		this.setAlignment(Pos.CENTER);
		this.setHgap(15);
		this.setVgap(20);
		this.setPadding(new Insets(30));
		
		// header
		Label title = new Label("Sign-in Setup");
		title.setId("header");
		this.add(title, 0, 0, 3, 1);
		
		
		// location for person on ozeret to input their name
		Label ozName = new Label("Ozeret Name:");
		this.add(ozName, 0, 1);
		
		TextField ozNameEntry = new TextField();
		this.add(ozNameEntry, 1, 1, 2, 1);
		
		
		// location for person on ozeret to input time of curfew
		Label curfewTime = new Label("Curfew (HH:MM):");
		this.add(curfewTime, 0, 2);
		
		TextField curfewEntry = new TextField();
		this.add(curfewEntry, 1, 2);
		
		// create and add am/pm selector buttons
		ToggleGroup timeGroup = new ToggleGroup();
		RadioButton am = new RadioButton("AM");
		RadioButton pm = new RadioButton("PM");
		am.getStyleClass().add("radiobutton");
		pm.getStyleClass().add("radiobutton");
		am.setToggleGroup(timeGroup);
		pm.setToggleGroup(timeGroup);
		am.setSelected(true);
		
		VBox timeBox = new VBox(this.getVgap());
		timeBox.getChildren().addAll(am, pm);
		this.add(timeBox, 2, 2);
		
		
		// add continue/exit buttons
		Button exit = new Button("Exit");
		exit.setCancelButton(true); // exit button is triggered on ESC keypress
		
		Button advance = new Button("Choose File");
		advance.setDefaultButton(true); // advance button is triggered on ENTER keypress
		
		// make buttons grow to fit entire width of row
		HBox statusBox = new HBox(this.getHgap());
		HBox.setHgrow(exit, Priority.ALWAYS);
		HBox.setHgrow(advance, Priority.ALWAYS);
		exit.setMaxWidth(Double.MAX_VALUE);
		advance.setMaxWidth(Double.MAX_VALUE);
		statusBox.getChildren().addAll(exit, advance);
		this.add(statusBox, 0, 3, 3, 1);
		
		// if the exit ('X') button of the window is pressed, act as if the in-window "Exit" button was pressed
		stage.setOnCloseRequest(new EventHandler<WindowEvent>() {

			@Override
			public void handle(WindowEvent event) {
				event.consume();
				exit.fire();
			}
		});
		
		// set exit button behavior
		exit.setOnAction(new EventHandler<ActionEvent>() {
			
			@Override
			public void handle(ActionEvent event) {
				
				// pop up exit confirmation alert
				Alert exitConfirmation = new Alert(AlertType.CONFIRMATION, "Are you sure you want to exit?\nData entered will be lost.");
				exitConfirmation.setTitle("Exit Confirmation");
				exitConfirmation.getDialogPane().getStylesheets().add(getClass().getResource("ozeret.css").toExternalForm());
				exitConfirmation.initOwner(exit.getScene().getWindow());
				exitConfirmation.showAndWait();
				
				// exit if user confirms exit
				if (exitConfirmation.getResult() == ButtonType.OK)
					Platform.exit();
			}
		});
		
		// set advance button behavior
		advance.setOnAction(new EventHandler<ActionEvent>() {
			
			@Override
			public void handle(ActionEvent event) {
				
				// check to make sure that both fields are properly filled out
				//  (name field is not empty, curfew field matches an hh:mm time regex, time is valid)
				if(!ozNameEntry.getText().equals("") && curfewEntry.getText().matches("\\d{1,2}:\\d\\d") &&
						Integer.parseInt(curfewEntry.getText().split(":")[0]) >= 1 &&	// check hour of time for validity
						Integer.parseInt(curfewEntry.getText().split(":")[0]) <= 12 &&
						Integer.parseInt(curfewEntry.getText().split(":")[1]) >= 0 &&	// check minute of time for validity
						Integer.parseInt(curfewEntry.getText().split(":")[1]) <= 59) {
					
					// open FileChooser for person on ozeret to select which file has the attendance information
					FileChooser fileChooser = new FileChooser();
					fileChooser.setInitialDirectory(new File("."));
					fileChooser.setTitle("Select Attendance File");
					fileChooser.getExtensionFilters().add(new ExtensionFilter("Excel Files", "*.xlsx"));
					File attendanceFile = fileChooser.showOpenDialog(stage);
					
					if (attendanceFile != null) {
						
						// update next scene's SignInPane to have correct curfew, ozeret name, and attendance file
						((SignInPane) nextScene.getRoot()).setPrevVars(ozNameEntry.getText(), curfewTime(), attendanceFile, myScene);
						
						// clear text in fields, in case user returns to this scene
						ozNameEntry.clear();
						curfewEntry.clear();
						
						// change the scene to the next one (will be a scene with SignInPane in it)
						stage.setScene(nextScene);
						stage.centerOnScreen();
					}
					
				} else {
				
					// if fields aren't properly filled out, pop up an alert saying so, and don't advance
					Alert notDone = new Alert(AlertType.WARNING, "You must fill out both fields properly to proceed");
					notDone.setHeaderText("Improper Ozeret Name and/or Curfew Time");
					notDone.getDialogPane().getStylesheets().add(getClass().getResource("ozeret.css").toExternalForm());
					notDone.showAndWait();
				}
			}
			
			// takes the string entered as curfew time and converts it to the date and time of curfew
			private LocalDateTime curfewTime() {
				
				int hour, minute;
				hour = Integer.parseInt(curfewEntry.getText().split(":")[0]);
				minute = Integer.parseInt(curfewEntry.getText().split(":")[1]);
				
				// to convert to 24-hr time properly, change 12 to 0
				if (hour == 12)
					hour = 0;
				
				// to convert to 24-hr time, add 12 to the hour if time is PM.
				if (pm.isSelected())
					hour += 12;
				
				LocalTime curfew = LocalTime.of(hour, minute);
				LocalTime now = LocalTime.now();
				
				// if curfew is after the current time, curfew is today
				if (curfew.isAfter(now))
					return LocalDateTime.of(LocalDate.now(), curfew); // return LocalDateTime object with today's date and entered time
				else // otherwise, curfew is tomorrow (read: after midnight)
					return LocalDateTime.of(LocalDate.now().plusDays(1), curfew); // return LocalDateTime object with tomorrow's date and entered time
			}
		});
	}
	
}
