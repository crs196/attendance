package attendance;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class CountdownTimer extends Label {

	LocalDateTime finalTime;
	Timeline timeline;

	public CountdownTimer(LocalDateTime timeToCountTo) {
		finalTime = timeToCountTo;
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
				if (LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) == 0) 
					setText((LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) + 1) + " minute");
				else
					setText((LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) + 1) + " minutes");
			}
		}), new KeyFrame(Duration.seconds(1)));

		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();
	}
}