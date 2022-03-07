package attendance;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.EmptyFileException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ini4j.Ini;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class SignInPane extends GridPane {

	private Stage stage;

	private String name;
	private LocalDateTime curfew;
	private File attendanceFile;
	private Ini settings;

	private XSSFWorkbook workbook;
	private XSSFSheet sheet;
	
	private BufferedReader infoReader;
	private String infoText;

	// used to track which column holds each piece of information
	private int bunkCol, nameCol, idCol, ontimeCol, lateCol, absentCol, todayCol;

	public SignInPane(Stage s, Ini set) {
		super();
		
		// get config settings
		settings = set;
		
		// set stage
		stage = s;
		
		// set instance variables
		name = settings.get("paneSettings", "opName");
		curfew = curfewTime(settings.get("curfewTimes", "normalCurfew")); // TODO: add other two curfew times
		attendanceFile = new File(settings.get("paneSettings", "attendanceFilePath"));
		
		
		// create local workbook from attendanceFile, only continue if workbook creation is acceptable
		try (FileInputStream afis = new FileInputStream(attendanceFile)) {
			workbook = new XSSFWorkbook(afis);
			
			sheet = workbook.getSheetAt(0);
			
			readHeaderRow();
			setup();
		} catch (EmptyFileException | IOException e) {			
			Alert fileNotAccessible = new Alert(AlertType.ERROR, "Unable to access \"" + attendanceFile.getName()
					+ "\"\nPlease choose a different file.");
			fileNotAccessible.setTitle("Attendance File Not Accessible");
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
			fileNotAccessible.initOwner(stage);
			fileNotAccessible.showAndWait();
			
			Platform.exit();
		}
		
		// get info text
		infoText = "";
		getFileContents();
	}
	
	// takes the string entered as curfew time and converts it to the date and time of curfew
	private LocalDateTime curfewTime(String curfewString) {
		
		int hour = 0, minute = 0; // variables to hold the input hour and minute
		
		// a regex and matcher that matches 12-hr time with optional leading zero, optional separator
		//  mandatory meridem indicators (but optionally separated, case-insenstive, and with optional m/M)
		Pattern twelveHrTime = Pattern.compile("^(1[0-2]|0?[1-9]):?([0-5]\\d)\\s*([AaPp])[Mm]?$");
		Matcher twelveHrMatcher = twelveHrTime.matcher(curfewString);
		
		// a regex and matcher that matches 24-hr time with optional leading zero and optional separator
		Pattern twentyFourHrTime = Pattern.compile("^(2[0-3]|1\\d|0?\\d):?([0-5]\\d)$");
		Matcher twentyFourHrMatcher = twentyFourHrTime.matcher(curfewString);
		
		if (twelveHrMatcher.find()) { // if the curfew string matches 12-hr time
			
			hour = Integer.parseInt(twelveHrMatcher.group(1)); // set hour variable to input hour
			if (twelveHrMatcher.group(3).toLowerCase().equals("p"))
				hour += 12; // if the time is PM, add 12 to convert to 24-hour time
			
			minute = Integer.parseInt(twelveHrMatcher.group(2)); // set minute variable to input minute
			
		} else if (twentyFourHrMatcher.find()){ // else if the curfew string matches 24-hr time
			
			hour = Integer.parseInt(twentyFourHrMatcher.group(1)); // set hour variable to input hour
			minute = Integer.parseInt(twentyFourHrMatcher.group(2)); // set minute variable to input minute
		} else { // if time is invalid, alert user
			Alert invalidCurfewTime = new Alert(AlertType.ERROR, "Curfew time was not input in a known format. Please fix this in \"config.ini\"\n"
					+ "Valid formats are:\n- 24 hour time with or without leading zero, with or without a time separator;\n"
					+ "- 12 hour time with or without a leading zero, with or without a time separator, with or without a space between the minutes and the meridiem, "
					+ "with or without the \"M\" in the meridiem");
			invalidCurfewTime.setTitle("Invalid Time Format");
			invalidCurfewTime.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
			invalidCurfewTime.showAndWait();
			
			Platform.exit();
		}
		
		LocalTime curfew = LocalTime.of(hour, minute);
		
		// if curfew is after the current time, curfew is today
		// TODO: this may need to change to accommodate for days off? I don't think so, but I'm flagging this just in case
		if (curfew.isAfter(LocalTime.now()))
			return LocalDateTime.of(LocalDate.now(), curfew); // return LocalDateTime object with today's date and entered time
		else // otherwise, curfew is tomorrow (read: after midnight)
			return LocalDateTime.of(LocalDate.now().plusDays(1), curfew); // return LocalDateTime object with tomorrow's date and entered time
	}
	
	// reads information from file to display if the info button is clicked
	private void getFileContents() {
		
		// get location of the info file for this pane
		String infoPath = settings.get("paneSettings", "infoPath", String.class);
		String infoFileParent = infoPath.substring(0, infoPath.lastIndexOf("/")); // get the parent name
		String infoFileName = infoPath.split("/")[infoPath.split("/").length - 1]; // get the file name
		
		// set up reader to read from file
		try {
			infoReader = new BufferedReader(new FileReader(infoPath));
		} catch (FileNotFoundException e) {		
			Alert fileNotAccessible = new Alert(AlertType.ERROR, "Unable to access \"" + infoFileName + "\" file.\nPlease create this file in the " + infoFileParent + " directory.");
			fileNotAccessible.setTitle("Info File Not Accessible");
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
			fileNotAccessible.showAndWait();
			
			Platform.exit();
		}
		
		
		// read from file into infoText
		try {
			String temp = "";
			
			while((temp = infoReader.readLine()) != null) {
				infoText += temp + "\n";
			}
			
		} catch (IOException e) {
			infoText = "Something went wrong while reading this text.\nCheck the \"" + infoFileName + "\" file to see if there are errors in it.";
		}
	}

	// called when InitialPane moves to this scene
	// TODO: add this functionality to constructor
	@Deprecated
	public void setPrevVars(String opName, LocalDateTime c, File af) {
		name = opName;
		curfew = c;
		attendanceFile = af;
		
		
		// create local workbook from attendanceFile, only continue if workbook creation is acceptable
		try (FileInputStream afis = new FileInputStream(attendanceFile)) {
			workbook = new XSSFWorkbook(afis);
			
			sheet = workbook.getSheetAt(0);
			
			readHeaderRow();
			setup();
		} catch (EmptyFileException | IOException e) {			
			Alert fileNotAccessible = new Alert(AlertType.ERROR, "Unable to access \"" + attendanceFile.getName()
					+ "\"\nPlease choose a different file.");
			fileNotAccessible.setTitle("Attendance File Not Accessible");
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
			fileNotAccessible.initOwner(stage);
			fileNotAccessible.showAndWait();
			
			Platform.exit();
		}
	}

	// reads header row of workbook to initialize column trackers,
	//  adds column for today to the end of the sheet and puts in name to row 2
	// TODO: consider refactoring this method in conjunction with a set of settings in `config.ini` that specify whether columns exist and where they are
	private void readHeaderRow() {

		XSSFRow headerRow = sheet.getRow(0);
		boolean bunkExists = false, nameExists = false, idExists = false,
				ontimeExists = false, lateExists = false, absentExists = false,
				todayExists = false;
		boolean curfewToday = (LocalDate.now().compareTo(curfew.toLocalDate()) == 0);

		// run through all cells in header row to assign column trackers
		for (int i = headerRow.getFirstCellNum(); i < headerRow.getLastCellNum(); i++) {

			if ((headerRow.getCell(i) != null) && (headerRow.getCell(i).getCellType() == CellType.STRING)) {

				// check to see if current cell has any of these contents
				switch (headerRow.getCell(i).getStringCellValue().toLowerCase()) {
				case "bunk":
					bunkCol = i;
					bunkExists = true;
					break;
				case "name":
					nameCol = i;
					nameExists = true;
					break;
				case "id":
					idCol = i;
					idExists = true;
					break;
				case "on time":
					ontimeCol = i;
					ontimeExists = true;
					break;
				case "late":
					lateCol = i;
					lateExists = true;
					break;
				case "absent":
					absentCol = i;
					absentExists = true;
					break;					
				default:
					break;
				}

				if (curfewToday) {
					if (headerRow.getCell(i).getStringCellValue().equals(curfew.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")))){
						todayCol = i;
						todayExists = true;
					}
				} else {
					if (headerRow.getCell(i).getStringCellValue().equals(curfew.minusDays(1).format(DateTimeFormatter.ofPattern("MM/dd/yyyy")))){
						todayCol = i;
						todayExists = true;
					}
				}
			}
		}
		
		// if the bunk and/or name columns don't exist, this spreadsheet is invalid, so don't continue
		if (!bunkExists || !nameExists) {
			Alert fileNotAccessible = new Alert(AlertType.ERROR, "The chosen file \"" + attendanceFile.getName() + "\" is formatted incorrecly.\n"
					+ "Please choose a different file.");
			fileNotAccessible.setTitle("Attendance File Not Formatted Correctly");
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
			fileNotAccessible.initOwner(stage);
			fileNotAccessible.showAndWait();
			
			Platform.exit();
		}

		// if there is no column labeled "ID", set the ID column to be the name column
		if (!idExists)
			idCol = nameCol;
		// if any of the summary statistic columns don't exist, set the respective tracker to -1 to signify that
		if (!ontimeExists)
			ontimeCol = -1;
		if (!lateExists)
			lateCol = -1;
		if (!absentExists)
			absentCol = -1;
		// if there was no column for today's attendance, add one
		if (!todayExists) {
			// add new column for today's sign-in
			todayCol = headerRow.getLastCellNum();
			headerRow.createCell(todayCol);

			if (curfew.toLocalDate().compareTo(LocalDate.now()) == 0) // if curfew is today
				headerRow.getCell(todayCol).setCellValue(curfew.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
			else // curfew is tomorrow
				headerRow.getCell(todayCol).setCellValue(curfew.minusDays(1).format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
		}

		// write name of operator to row 2 in today's column if different than what's already there
		if (sheet.getRow(1).getCell(todayCol) == null)
			sheet.getRow(1).createCell(todayCol).setCellValue(name);
		else if (!(sheet.getRow(1).getCell(todayCol).getStringCellValue().equals(name)))
			sheet.getRow(1).getCell(todayCol).setCellValue(name);
		
		// write curfew time to row 3 in today's column if different than what's already there
		if (sheet.getRow(2).getCell(todayCol) == null)
			sheet.getRow(2).createCell(todayCol).setCellValue(curfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(sheet.getRow(2).getCell(todayCol).getStringCellValue().equals(curfew.format(DateTimeFormatter.ofPattern("h:mm a")))))
			sheet.getRow(2).getCell(todayCol).setCellValue(curfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		
		sheet.autoSizeColumn(todayCol); // resize column to fit

	}

	// sets up layout and functionality of SignInPane
	private void setup() {

		// set up grid layout and sizing
		this.setAlignment(Pos.CENTER);
		this.setHgap(15);
		this.setVgap(20);
		this.setPadding(new Insets(30));

		// header
		Label title = new Label("Sign In");
		title.setId("header");
		SignInPane.setHalignment(title, HPos.CENTER);
		this.add(title, 0, 0, 3, 1);


		/* left column (clock + time to curfew, view unaccounted for, save, exit) */

		// clock
		Clock currentTime = new Clock();
		Label clockLabel = new Label("Current Time:");
		HBox currentTimeBox = new HBox(this.getHgap());
		currentTime.setMinWidth(USE_PREF_SIZE);
		clockLabel.setMinWidth(USE_PREF_SIZE);
		currentTimeBox.setAlignment(Pos.CENTER);
		currentTimeBox.getChildren().addAll(clockLabel, currentTime);

		// curfew time
		Label curfewLabel = new Label("Curfew:");
		Label curfewTimeLabel = new Label();
		curfewTimeLabel.setText(curfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		HBox curfewBox = new HBox(this.getHgap());
		curfewLabel.setMinWidth(USE_PREF_SIZE);
		curfewTimeLabel.setMinWidth(USE_PREF_SIZE);
		curfewBox.setAlignment(Pos.CENTER);
		curfewBox.getChildren().addAll(curfewLabel, curfewTimeLabel);

		// time to curfew
		CountdownTimer timeToCurfew = new CountdownTimer(curfew);
		Label countdownLabel = new Label("Time until curfew:");
		HBox countdownBox = new HBox(this.getHgap());
		timeToCurfew.setMinWidth(USE_PREF_SIZE);
		countdownLabel.setMinWidth(USE_PREF_SIZE);
		countdownBox.setAlignment(Pos.CENTER);
		countdownBox.getChildren().addAll(countdownLabel, timeToCurfew);

		// add clocks to VBox to hold them
		VBox clockBox = new VBox(this.getVgap() * 0.5);
		clockBox.getChildren().addAll(currentTimeBox, curfewBox, countdownBox);

		VBox listButtons = new VBox(this.getVgap() * 0.5);
		Button viewUnaccounted = new Button("View Unaccounted-for Staff Members");
		Button save = new Button("Save");
		Button saveAndExit = new Button("Save and Exit");

		viewUnaccounted.setMinWidth(USE_PREF_SIZE);
		save.setMinWidth(USE_PREF_SIZE);
		saveAndExit.setMinWidth(USE_PREF_SIZE);

		HBox.setHgrow(viewUnaccounted, Priority.ALWAYS);
		HBox.setHgrow(save, Priority.ALWAYS);
		HBox.setHgrow(saveAndExit, Priority.ALWAYS);

		viewUnaccounted.setMaxWidth(Double.MAX_VALUE);
		save.setMaxWidth(Double.MAX_VALUE);
		saveAndExit.setMaxWidth(Double.MAX_VALUE);
		
		listButtons.getChildren().addAll(new HBox(viewUnaccounted), new HBox(), new HBox(save), new HBox(saveAndExit)); // empty HBox for spacing

		VBox leftColumn = new VBox(this.getVgap());
		leftColumn.getChildren().addAll(clockBox, listButtons);
		this.add(leftColumn, 0, 1, 1, 2);		

		/* right column (sign-in box, confirmation area) */

		// sign-in instructions, entry point, and confirm button
		Label scanLabel = new Label("Please enter a staff member's ID");
		scanLabel.setMinWidth(USE_PREF_SIZE);
		
		TextField idField = new TextField();
		
		Button signIn = new Button("Sign In");
		signIn.setDefaultButton(true);
		HBox.setHgrow(signIn, Priority.ALWAYS);
		signIn.setMaxWidth(Double.MAX_VALUE);
		HBox signInBox = new HBox(this.getHgap());
		signInBox.getChildren().addAll(idField, signIn);
		
		VBox idBox = new VBox(this.getVgap());
		idBox.getChildren().addAll(scanLabel, signInBox);

		// confirmation area
		TextArea confirmation = new TextArea();
		confirmation.setEditable(false);
		confirmation.setWrapText(true);
		confirmation.setPrefWidth(scanLabel.getWidth());
		confirmation.setPrefRowCount(3);
		idBox.getChildren().add(confirmation);
		this.add(idBox, 1, 1);
		
		// add information button (will pop up credits and instructions)
		// infoBox and infoAlign are to right-align the info button
		Button info = new Button("i");
		info.getStyleClass().add("info");
		info.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);

		GridPane infoAlign = new GridPane();
		HBox.setHgrow(infoAlign, Priority.ALWAYS);
		
		HBox infoBox = new HBox();
		infoBox.getChildren().addAll(infoAlign, info);
		this.add(infoBox, 1, 2);
		
		// stage and scene for viewUnaccounted
		Stage extraStage = new Stage();
		Scene unaccScene = new Scene(new Label("Something's gone wrong"));
		
		// set info button behavior (show credits, brief explanation of what to do)
		info.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Alert infoDialog = new Alert(AlertType.NONE, infoText, ButtonType.CLOSE);
				infoDialog.setTitle("Credits and Instructions — Sign-in");
				infoDialog.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
				infoDialog.initOwner(info.getScene().getWindow());
				infoDialog.initModality(Modality.NONE);
				infoDialog.setResizable(true);
				infoDialog.getDialogPane().setPrefWidth(stage.getWidth());
				infoDialog.show();
			}
					
		});

		// set sign-in button behavior
		signIn.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {

				// save staff ID and clear idField text
				String staffID = idField.getText();
				idField.clear();

				// only search if an ID was actually entered
				if (!staffID.isEmpty()) {
					
					boolean idFound = false; // staff member has not yet been found
					for (int i = sheet.getFirstRowNum() + 3; i < sheet.getLastRowNum() + 1; i++) {

						String currentID = "";
						
						// first check idCol for matches
						
						if((sheet.getRow(i) != null) && sheet.getRow(i).getCell(idCol).getCellType() == CellType.NUMERIC)
							currentID = (int) sheet.getRow(i).getCell(idCol).getNumericCellValue() + "";
						else if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(idCol).getCellType() == CellType.STRING)
							currentID = sheet.getRow(i).getCell(idCol).getStringCellValue();
						
						// if the current row's ID matches the one inputted, the staff member was found
						if (currentID.equals(staffID)) {

							idFound = true;
							LocalDateTime now = LocalDateTime.now(); // save current time in case close to curfew

							// if today's attendance column does not exist or is empty, the staff member is unaccounted for
							if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(todayCol) == null) {

								sheet.getRow(i).createCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")));
								signInStatus(i, now);
								confirmation.setText(sheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
								break; // search is done

							} else if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK) {

								sheet.getRow(i).getCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")));
								signInStatus(i, now);
								confirmation.setText(sheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
								break; // search is done

							} else { // if cell exists and is not blank, staff member has already signed in today
								confirmation.setText(sheet.getRow(i).getCell(nameCol).getStringCellValue() + " has already signed in");
								break; // search is done
							}
						}
						
						// then, if nameCol is different than idCol, check nameCol for matches
						if (nameCol != idCol) {
							
							if((sheet.getRow(i) != null) && sheet.getRow(i).getCell(nameCol).getCellType() == CellType.NUMERIC)
								currentID = (int) sheet.getRow(i).getCell(nameCol).getNumericCellValue() + "";
							else if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(nameCol).getCellType() == CellType.STRING)
								currentID = sheet.getRow(i).getCell(nameCol).getStringCellValue();
							
							// if the current row's ID matches the one inputted, the staff member was found
							if (currentID.equals(staffID)) {

								idFound = true;
								LocalDateTime now = LocalDateTime.now(); // save current time in case close to curfew

								// if today's attendance column does not exist or is empty, the staff member is unaccounted for
								if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(todayCol) == null) {

									sheet.getRow(i).createCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")));
									signInStatus(i, now);
									confirmation.setText(sheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
									break; // search is done

								} else if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK) {

									sheet.getRow(i).getCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")));
									signInStatus(i, now);
									confirmation.setText(sheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
									break; // search is done

								} else { // if cell exists and is not blank, staff member has already signed in today
									confirmation.setText(sheet.getRow(i).getCell(nameCol).getStringCellValue() + " has already signed in");
									break; // search is done
								}
							}
						}
					}
					
					if (!idFound && !staffID.isEmpty())
						confirmation.setText("Staff member " + staffID + " not found");
				} else
					confirmation.setText("No ID entered");
				
				if (extraStage.isShowing())
					viewUnaccounted.fire();
			}

			// given a staff member (via row number) and sign-in time, 
			//  increments the proper summary statistic column (if it exists)
			//  and colors the staff member's "today cell" as either green or yellow, depending on sign-in time
			public void signInStatus(int rowNum, LocalDateTime signInTime) {

				// create cell styles for on time and late
				XSSFCellStyle onTime = workbook.createCellStyle();
				java.awt.Color onTimeColor = Color.decode(settings.get("sheetFormat", "onTimeColor", String.class));
				onTime.setFillForegroundColor(new XSSFColor(onTimeColor, new DefaultIndexedColorMap()));
				onTime.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				
				XSSFCellStyle late = workbook.createCellStyle();
				java.awt.Color lateColor = Color.decode(settings.get("sheetFormat", "lateColor", String.class));
				late.setFillForegroundColor(new XSSFColor(lateColor, new DefaultIndexedColorMap()));
				late.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				
				// staff member is on time
				if (curfew.compareTo(signInTime) > 0) {
					// set todayCol to onTime style
					sheet.getRow(rowNum).getCell(todayCol).setCellStyle(onTime);
					
					if (ontimeCol != -1) {// there is an "on time" column
						if ((sheet.getRow(rowNum) != null) && sheet.getRow(rowNum).getCell(ontimeCol) != null) // the cell exists
							sheet.getRow(rowNum).getCell(ontimeCol).setCellValue(sheet.getRow(rowNum).getCell(ontimeCol).getNumericCellValue() + 1);
						else // the cell does not exist
							sheet.getRow(rowNum).createCell(ontimeCol).setCellValue(1);
					}
				} else { // staff member is late
					// set todayCol to late style
					sheet.getRow(rowNum).getCell(todayCol).setCellStyle(late);
					
					if (lateCol != -1) {// there is an "on time" column
						if ((sheet.getRow(rowNum) != null) && sheet.getRow(rowNum).getCell(lateCol) != null) // the cell exists
							sheet.getRow(rowNum).getCell(lateCol).setCellValue(sheet.getRow(rowNum).getCell(lateCol).getNumericCellValue() + 1);
						else // the cell does not exist
							sheet.getRow(rowNum).createCell(lateCol).setCellValue(1);
					}
				}
			}
		});



		// event handlers for left column buttons

		// pulls up list of staff members who have yet to sign in in this session
		viewUnaccounted.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {

				// if there are no staff left unaccounted, print a message saying so and leave this handle method
				if (noUnaccountedStaff()) {
					confirmation.setText("All staff members have signed in");
					extraStage.close();
					return;
				}
				
				// if we get here, there are still unaccounted-for staff, so find and list them
				
				GridPane unaccPane = new GridPane();
				// set up grid layout and sizing
				unaccPane.setHgap(15);
				unaccPane.setVgap(20);
				unaccPane.setAlignment(Pos.CENTER);
				unaccPane.setPadding(new Insets(20));
				ColumnConstraints column1 = new ColumnConstraints();
				column1.setPercentWidth(50);
				ColumnConstraints column2 = new ColumnConstraints();
				column2.setPercentWidth(50);
				ColumnConstraints column3 = new ColumnConstraints();
				column3.setPercentWidth(50);
				unaccPane.getColumnConstraints().addAll(column1, column2, column3);

				ScrollPane scrollPane = new ScrollPane(unaccPane);
				scrollPane.setMinWidth(stage.getWidth() * 0.75);
				scrollPane.setMaxHeight(stage.getHeight());

				List<String> listBunks = countBunks();
				int nextRow = 0, nextCol = 0;

				for (int i = 0; i < listBunks.size(); i++) {
					if (!bunkEmpty(listBunks.get(i))) {
						unaccPane.add(getStaffFromBunk(listBunks.get(i)), nextRow, nextCol);

						if (++nextRow > 2) {
							nextCol++;
							nextRow = 0;
						}
					}
				}


				// set up scene
				unaccScene.setRoot(scrollPane);
				unaccScene.getStylesheets().add(Attendance.class.getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());

				// only need to do these things if the stage isn't currently on screen
				if (!extraStage.isShowing()) {
					// set up stage
					extraStage.setScene(unaccScene);
					extraStage.setMinWidth(scrollPane.getMinWidth());
					extraStage.setMaxHeight(scrollPane.getMaxHeight());
					extraStage.setTitle("Unaccounted-for Staff");
					extraStage.getIcons().add(new Image(settings.get("stageSettings", "iconPath", String.class)));
					extraStage.centerOnScreen();
					extraStage.show();
				}
			}
			
			// returns whether or not there are still unaccounted-for staff
			public boolean noUnaccountedStaff() {

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member
				for (int i = sheet.getFirstRowNum() + 3; i <= sheet.getLastRowNum(); i++)
					// if today's attendance column does not exist or is empty, the staff member is unaccounted for
					if ((sheet.getRow(i) != null) && (sheet.getRow(i).getCell(todayCol) == null || 
						 								sheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK))
						return false;

				return true;
			}

			// counts the number of unique bunks in workbook and returns the list of unique bunks
			public List<String> countBunks() {

				List<String> uniqueBunks = new ArrayList<String>();

				// loop through all rows of the sheet, starting at the third row (so ignoring the two header rows)
				//  and look at the "bunk" column to count how many unique bunks there are
				for (int i = sheet.getFirstRowNum() + 3; i <= sheet.getLastRowNum(); i++) {

					// if the current bunk is new, add it to the list of unique bunks
					if ((sheet.getRow(i) != null) && !uniqueBunks.contains(sheet.getRow(i).getCell(bunkCol).getStringCellValue()))
						uniqueBunks.add(sheet.getRow(i).getCell(bunkCol).getStringCellValue());
				}

				return uniqueBunks;
			}

			// given a bunk name, returns whether or not there are any unaccounted staff remaining
			//  in that bunk
			public boolean bunkEmpty(String bunk) {

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member in this bunk
				for (int i = sheet.getFirstRowNum() + 3; i <= sheet.getLastRowNum(); i++)
					if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(bunkCol).getStringCellValue().equals(bunk))
						// if today's attendance column does not exist or is empty, the staff member is unaccounted for
						if ((sheet.getRow(i) != null) && (sheet.getRow(i).getCell(todayCol) == null || 
						sheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK))
							return false;

				return true;
			}

			// given a bunk name, gets the names of all unaccounted staff in that bunk
			//  and creates a VBox with the name of the bunk and each staff member in it
			public VBox getStaffFromBunk(String bunk) {

				VBox bunkBox = new VBox(10);

				Label bunkName = new Label(bunk);
				bunkName.setId("bunk-label");
				bunkName.setMinWidth(USE_PREF_SIZE);
				HBox bunkNameBox = new HBox(15);
				bunkNameBox.setAlignment(Pos.CENTER);
				bunkNameBox.getChildren().add(bunkName);
				bunkBox.getChildren().addAll(bunkNameBox, new HBox()); // empty HBox for spacing

				// runs through all rows of the spreadsheet and adds unaccounted staff in this bunk to the VBox
				for (int i = sheet.getFirstRowNum() + 3; i <= sheet.getLastRowNum(); i++) {

					if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(bunkCol).getStringCellValue().equals(bunk)) {

						// if today's attendance column does not exist or is empty, the staff member is unaccounted for
						if ((sheet.getRow(i) != null) && (sheet.getRow(i).getCell(todayCol) == null || 
								sheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK)) {

							Button staffMember = new Button(sheet.getRow(i).getCell(nameCol).getStringCellValue());
							staffMember.setId("list-button");
							staffMember.setMinWidth(USE_PREF_SIZE);
							HBox staffNameBox = new HBox(15);
							staffNameBox.setAlignment(Pos.CENTER);
							staffNameBox.getChildren().add(staffMember);
							bunkBox.getChildren().add(staffNameBox);

							// when a staff member is clicked, open a popup window to allow user to
							//  mark them as on shmira or a day off
							XSSFRow staffRow = sheet.getRow(i); // stores current row for use in event handler
							staffMember.setOnAction(new EventHandler<ActionEvent>() {

								@Override
								public void handle(ActionEvent event) {
									Alert options = new Alert(AlertType.NONE, "Sign this staff member in:",
											new ButtonType("Sign In", ButtonData.OTHER),
											new ButtonType("Shmira", ButtonData.OTHER),
											new ButtonType("Day Off", ButtonData.OTHER),
											ButtonType.CANCEL);
									options.setTitle("Manual Sign-In");
									options.setHeaderText(staffMember.getText());
									options.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
									options.initOwner(staffMember.getScene().getWindow());
									options.showAndWait();
									
									// create cell style to be used when signing staff member in for day off or shmira
									XSSFCellStyle onTime = workbook.createCellStyle();
									java.awt.Color onTimeColor = Color.decode(settings.get("sheetFormat", "onTimeColor", String.class));
									onTime.setFillForegroundColor(new XSSFColor(onTimeColor, new DefaultIndexedColorMap()));
									onTime.setFillPattern(FillPatternType.SOLID_FOREGROUND);

									if (options.getResult().getText().equals("Shmira")) {
										// staff member should be signed in as on shmira

										// set today's attendance column to show that the staff member is on shmira
										if (staffRow.getCell(todayCol) == null)
											staffRow.createCell(todayCol).setCellValue("Shmira");
										else
											staffRow.getCell(todayCol).setCellValue("Shmira");

										// update "on time" column (if it exists)
										if (ontimeCol != -1) { // there is an "on time" column
											if (staffRow.getCell(ontimeCol) != null) // the cell exists
												staffRow.getCell(ontimeCol).setCellValue(staffRow.getCell(ontimeCol).getNumericCellValue() + 1);
											else // the cell does not exist
												staffRow.createCell(ontimeCol).setCellValue(1);
										}
										
										// recolor cell background
										staffRow.getCell(todayCol).setCellStyle(onTime);
										
										// print a confirmation
										confirmation.setText(staffMember.getText() + " signed in as on shmira");

									} else if (options.getResult().getText().equals("Day Off")) {
										// staff member should be signed in as on day off

										// set today's attendance column to show that the staff member is on a day off
										if (staffRow.getCell(todayCol) == null)
											staffRow.createCell(todayCol).setCellValue("Day Off");
										else
											staffRow.getCell(todayCol).setCellValue("Day Off");

										// update "on time" column (if it exists)
										if (ontimeCol != -1) {// there is an "on time" column
											if (staffRow.getCell(ontimeCol) != null) // the cell exists
												staffRow.getCell(ontimeCol).setCellValue(staffRow.getCell(ontimeCol).getNumericCellValue() + 1);
											else // the cell does not exist
												staffRow.createCell(ontimeCol).setCellValue(1);
										}
										
										// recolor cell background
										staffRow.getCell(todayCol).setCellStyle(onTime);
										
										// print a confirmation
										confirmation.setText(staffMember.getText() + " signed in as on a day off");
									} else if (options.getResult().getText().equals("Sign In")) {
										// staff member should be signed in normally
										// do this by writing the staff member's name into the entry box and firing the sign-in button
										
										idField.setText(staffMember.getText());
										signIn.fire();
									}

									// refresh list of unaccounted staff members
									viewUnaccounted.fire();
								}
							});
						}
					}

				}

				bunkBox.getChildren().addAll(new HBox(), new HBox()); // empty HBoxes for spacing

				return bunkBox;
			}
		});

		// writes currently entered data back to input spreadsheet
		save.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				
				// write data to attendanceFile
				try (FileOutputStream afos = new FileOutputStream(attendanceFile)) {
					workbook.write(afos);
				} catch (IOException e) {
					confirmation.setText("Unable to write to \"" + attendanceFile.getName() + "\"");
				} 
				
				// print confirmation
				confirmation.setText("Data saved to \"" + attendanceFile.getName() + "\"");
			}
		});

		// pulls up list of staff members who have yet to sign in in this session
		//  with option to mark them as on day off
		saveAndExit.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				
				// see if there are any staff that haven't signed in yet
				boolean allBunksEmpty = noUnaccountedStaff();
				
				// if there are still unaccounted staff, confirm that user still wants to exit
				if (!allBunksEmpty) {
					Alert saveAndExitConf = new Alert(AlertType.CONFIRMATION, "There are still staff members that haven't signed in.\nAre you sure you want to exit?");
					saveAndExitConf.setHeaderText("Save and Exit Confirmation");
					saveAndExitConf.setTitle("Save and Exit Confirmation");
					saveAndExitConf.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
					saveAndExitConf.initOwner(saveAndExit.getScene().getWindow());
					saveAndExitConf.showAndWait();
					
					if (saveAndExitConf.getResult() != ButtonType.OK)
						return; // user does not want to save and exit
				}
				
				// all staff are accounted for or user wants to mark unaccounted-for staff as absent
				markUnaccAbsent();
				
				// write data to attendanceFile
				try (FileOutputStream afos = new FileOutputStream(attendanceFile)) {
					workbook.write(afos);
				} catch (IOException e) {
					confirmation.setText("Unable to write to \"" + attendanceFile.getName() + "\"");
				} 
				
				// clear clock text so that if run again they're still readable
				currentTime.stopClock();
				clockLabel.setText("");
				curfewLabel.setText("");
				curfewTimeLabel.setText("");
				timeToCurfew.stopClock();
				countdownLabel.setText("");
				
				// close unaccounted-for staff window and change scene back to setup scene
				extraStage.close();
				Platform.exit();
			}

			// returns whether or not there are still unaccounted-for staff
			public boolean noUnaccountedStaff() {

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member
				for (int i = sheet.getFirstRowNum() + 3; i <= sheet.getLastRowNum(); i++)
					// if today's attendance column does not exist or is empty, the staff member is unaccounted for
					if ((sheet.getRow(i) != null) && (sheet.getRow(i).getCell(todayCol) == null || 
					       sheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK))
						return false;

				return true;
			}
			
			// marks all staff that did not sign in as absent, and increments their "absent" column, if it exists
			public void markUnaccAbsent() {
				
				boolean absent;
				
				// create cell style for absent cells
				XSSFCellStyle absentStyle = workbook.createCellStyle();
				java.awt.Color absentColor = Color.decode(settings.get("sheetFormat", "absentColor", String.class));
				absentStyle.setFillForegroundColor(new XSSFColor(absentColor, new DefaultIndexedColorMap()));
				absentStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				
				for (int i = sheet.getFirstRowNum() + 3; i < sheet.getLastRowNum() + 1; i++) {
					
					absent = false;
					
					// if today's attendance column does not exist or is empty, the staff member is unaccounted for
					if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(todayCol) == null) {
						sheet.getRow(i).createCell(todayCol).setCellValue("Absent");
						absent = true;
					} else if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK) {
						sheet.getRow(i).getCell(todayCol).setCellValue("Absent");
						absent = true;
					}
					
					// set cell background to red if necessary,
					//  increment "absent" column if it exists
					if (absent) {
						sheet.getRow(i).getCell(todayCol).setCellStyle(absentStyle);
						
						if (absentCol != -1) {
							if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(absentCol) != null) // the cell exists
								sheet.getRow(i).getCell(absentCol).setCellValue(sheet.getRow(i).getCell(absentCol).getNumericCellValue() + 1);
							else // the cell does not exist
								sheet.getRow(i).createCell(absentCol).setCellValue(1);
						}
					}
				}
			}
		});

		/* change stage close behavior */
		stage.setOnCloseRequest(new EventHandler<WindowEvent>() {

			@Override
			public void handle(WindowEvent event) {
				event.consume(); // consume window-close event

				Alert alert = new Alert(AlertType.CONFIRMATION, 
						"Are you sure you want to exit?\nAll unsaved data will be lost.",
						new ButtonType("No, Return to Sign-In", ButtonData.CANCEL_CLOSE),
						new ButtonType("Yes, Exit", ButtonData.OK_DONE));
				alert.setTitle("Exit Confirmation");
				alert.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("stageSettings", "cssFile", String.class)).toExternalForm());
				alert.initOwner(stage);
				alert.showAndWait();

				if (alert.getResult().getButtonData() == ButtonData.OK_DONE)
					Platform.exit();

			}
		});
	}

}

class Clock extends Label {

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

class CountdownTimer extends Label {

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
