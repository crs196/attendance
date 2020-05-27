package ozeret;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SignInPane extends GridPane {

	private Stage stage;
	private Scene nextScene;
	
	public SignInPane(Stage s, Scene ns) {
		super();
		stage = s;
		nextScene = ns;
		
		readFile();
		setup();
	}
	
	// reads in information from selected attendance file and stores it in instance variables
	private void readFile() {
		// TODO: do this
	}
	
	// sets up layout and functionality of SignInPane
	private void setup() {

		// set up grid layout and sizing
		this.setAlignment(Pos.CENTER);
		this.setHgap(15);
		this.setVgap(20);
		this.setPadding(new Insets(30));
		
		// header
		Text title = new Text("Sign-In");
		title.setId("header");
		SignInPane.setHalignment(title, HPos.CENTER);
		this.add(title, 0, 0, 3, 1);
		
		
		/* left column (clock + time to curfew) */
		
		// clock
		Clock currentTime = new Clock();
		Label clockLabel = new Label("Current Time:");
		HBox currentTimeBox = new HBox(this.getHgap());
		currentTimeBox.setAlignment(Pos.CENTER);
		currentTimeBox.getChildren().addAll(clockLabel, currentTime);
		
		// time to curfew
		CountdownTimer timeToCurfew = new CountdownTimer(LocalDateTime.now().plusMinutes(3)); // TODO: delete this line once layout of SignInPane is finalized
		// CountdownTimer timeToCurfew = new CountdownTimer(OzeretMain.getCurfew()); // TODO: uncomment this line once layout of SignInPane is finalized
		Label countdownLabel = new Label("Time until curfew:");
		HBox countdownBox = new HBox(this.getHgap());
		countdownBox.setAlignment(Pos.CENTER);
		countdownBox.getChildren().addAll(countdownLabel, timeToCurfew);
		
		// add clocks to pane
		VBox clockBox = new VBox(this.getVgap());
		clockBox.getChildren().addAll(currentTimeBox, countdownBox);
		this.add(clockBox, 0, 1);
		
		
		/* central column (sign-in box, confirmation area) */
		
		// sign-in instructions and entry point
		Label scanLabel = new Label("Please enter a staff member's ID");
		TextField idField = new TextField();
		VBox idBox = new VBox(this.getVgap());
		idBox.getChildren().addAll(scanLabel, idField);
		this.add(idBox, 1, 1);
		
		// confirmation area
		Label confirmationArea = new Label();
		confirmationArea.setId("confirmation");
		this.add(confirmationArea, 1, 2);
		
		// confirm button
		Button signIn = new Button("Sign In");
		signIn.setDefaultButton(true);
		HBox.setHgrow(signIn, Priority.ALWAYS);
		signIn.setMaxWidth(Double.MAX_VALUE);
		HBox signInButton = new HBox(this.getHgap());
		signInButton.getChildren().add(signIn);
		this.add(signInButton, 1, 3);
		
		signIn.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				// TODO: sign in staff member who's ID is in idField
			}
		});
	}
	
}

class Clock extends Label {
	
	public Clock() {
		bindToTime();
	}
	
	private void bindToTime() {
		Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0), new EventHandler<ActionEvent>() {
			
			@Override
			public void handle(ActionEvent event) {
				setText(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
			}
		}), new KeyFrame(Duration.seconds(1)));
		
		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();
	}
}

class CountdownTimer extends Label {
	
	LocalDateTime finalTime;
	
	public CountdownTimer(LocalDateTime timeToCountTo) {
		finalTime = timeToCountTo;
		bindToTime();
	}
	
	private void bindToTime() {
		Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0), new EventHandler<ActionEvent>() {
			
			@Override
			public void handle(ActionEvent event) {
				if (LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) == 1) 
					setText(LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) + " minute");
				else
					setText(LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) + " minutes");
			}
		}), new KeyFrame(Duration.seconds(1)));
		
		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();
	}
}
