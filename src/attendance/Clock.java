package attendance;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class Clock extends Label {

	Timeline timeline;
	
	public Clock() {
		bindToTime();
	}

	public void stopClock() {
		timeline.stop();
		setText("");
	}
	
	private void bindToTime() {
		timeline = new Timeline(new KeyFrame(Duration.seconds(0), new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				setText(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
			}
		}), new KeyFrame(Duration.seconds(1)));

		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();
	}
}