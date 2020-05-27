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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
		currentTime.setMinWidth(USE_PREF_SIZE);
		clockLabel.setMinWidth(USE_PREF_SIZE);
		currentTimeBox.setAlignment(Pos.CENTER);
		currentTimeBox.getChildren().addAll(clockLabel, currentTime);
		
		// time to curfew
		CountdownTimer timeToCurfew = new CountdownTimer(LocalDateTime.now().plusMinutes(3)); // TODO: delete this line once layout of SignInPane is finalized
		// CountdownTimer timeToCurfew = new CountdownTimer(OzeretMain.getCurfew()); // TODO: uncomment this line once layout of SignInPane is finalized
		Label countdownLabel = new Label("Time until curfew:");
		HBox countdownBox = new HBox(this.getHgap());
		timeToCurfew.setMinWidth(USE_PREF_SIZE);
		countdownLabel.setMinWidth(USE_PREF_SIZE);
		countdownBox.setAlignment(Pos.CENTER);
		countdownBox.getChildren().addAll(countdownLabel, timeToCurfew);
		
		// add clocks to pane
		VBox clockBox = new VBox(this.getVgap());
		clockBox.getChildren().addAll(currentTimeBox, countdownBox);
		this.add(clockBox, 0, 1);
		
		
		/* central column (sign-in box, confirmation area) */
		
		// sign-in instructions and entry point
		Label scanLabel = new Label("Please enter a staff member's ID");
		scanLabel.setMinWidth(USE_PREF_SIZE);
		TextField idField = new TextField();
		VBox idBox = new VBox(this.getVgap());
		idBox.getChildren().addAll(scanLabel, idField);
		
		// confirmation area
		TextArea confirmation = new TextArea();
		confirmation.getStyleClass().add("textarea");
		confirmation.setEditable(false);
		confirmation.setWrapText(true);
		confirmation.setPrefWidth(scanLabel.getWidth());
		confirmation.setPrefRowCount(3);
		idBox.getChildren().add(confirmation);
		this.add(idBox, 1, 1);
		
		// confirm button
		Button signIn = new Button("Sign In");
		signIn.setDefaultButton(true);
		HBox.setHgrow(signIn, Priority.ALWAYS);
		signIn.setMaxWidth(Double.MAX_VALUE);
		HBox signInButton = new HBox(this.getHgap());
		signInButton.getChildren().add(signIn);
		this.add(signInButton, 1, 2);
		
		signIn.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				// TODO: sign in staff member who's ID is in idField
				confirmation.setText("Cooper Schwartz signed in at 12:34 PM");
			}
		});
		
		
		/* right column (view unaccounted for, mark shmira/day off) */
		
		VBox listButtons = new VBox(this.getVgap() / 2);
		Button viewUnaccounted = new Button("View Unaccounted-for Staff Members");
		Button markShmira = new Button("Mark Staff on Shmira");
		Button markDayOff = new Button("Mark Staff on Day Off");
		
		viewUnaccounted.setMinWidth(USE_PREF_SIZE);
		markShmira.setMinWidth(USE_PREF_SIZE);
		markDayOff.setMinWidth(USE_PREF_SIZE);
		
		HBox.setHgrow(viewUnaccounted, Priority.ALWAYS);
		HBox.setHgrow(markShmira, Priority.ALWAYS);
		HBox.setHgrow(markDayOff, Priority.ALWAYS);
		
		viewUnaccounted.setMaxWidth(Double.MAX_VALUE);
		markShmira.setMaxWidth(Double.MAX_VALUE);
		markDayOff.setMaxWidth(Double.MAX_VALUE);
		
		HBox vu = new HBox();
		vu.getChildren().add(viewUnaccounted);
		HBox ms = new HBox();
		ms.getChildren().add(markShmira);
		HBox md = new HBox();
		md.getChildren().add(markDayOff);
		
		listButtons.getChildren().addAll(vu, ms, md);
		this.add(listButtons, 2, 1);
		
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
