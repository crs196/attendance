package ozeret;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
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
		
		setup();
	}
	
	private void setup() {

		// set up grid layout and sizing
		this.setAlignment(Pos.CENTER);
		this.setHgap(15);
		this.setVgap(20);
		this.setPadding(new Insets(30));
		
		// header
		Text title = new Text("Sign-In");
		title.setId("header");
		this.add(title, 0, 0, 3, 1);
		
		
		/* left column (clock + time to curfew) */
		
		// clock
		Clock currentTime = new Clock();
		Label clockLabel = new Label("Current Time:");
		HBox clockBox = new HBox(this.getHgap());
		clockBox.setAlignment(Pos.CENTER);
		clockBox.getChildren().addAll(clockLabel, currentTime);
		this.add(clockBox, 0, 1);
		
		// time to curfew
		CountdownTimer timeToCurfew = new CountdownTimer(LocalDateTime.now().plusMinutes(3)); // TODO: delete this line once layout of SignInPane is finalized
		// CountdownTimer timeToCurfew = new CountdownTimer(OzeretMain.getCurfew()); // TODO: uncomment this line once layout of SignInPane is finalized
		Label countdownLabel = new Label("Time until curfew:");
		HBox countdownBox = new HBox(this.getHgap());
		countdownBox.setAlignment(Pos.CENTER);
		countdownBox.getChildren().addAll(countdownLabel, timeToCurfew);
		this.add(countdownBox, 0, 2);
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
				setText(LocalTime.now().format(DateTimeFormatter.ofPattern("h:m a")));
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
