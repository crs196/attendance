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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.EmptyFileException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ini4j.Ini;

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
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
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

public class SignInPane extends GridPane {

	private Stage stage;

	private LocalDateTime leavingCampCurfew, nightOffCurfew, dayOffCurfew;
	private File attendanceFile;
	private Ini settings;

	private XSSFWorkbook workbook;
	private XSSFSheet attendanceSheet, keySheet;
	
	private HashMap<String, StaffMember> staffList;
	
	private BufferedReader infoReader;
	private String infoText;

	// used to track which column holds each piece of information
	private int keyBunkCol, keyNameCol, keyIDCol, ontimeCol, lateCol, absentCol;
	private int bunkCol, nameCol, idCol, timeOutCol, timeInCol;
	
	// used to track how many people have left and returned
	private int left, returned, stillOut;
	//used to track what row to write the next staff member on
	private int staffRowNum;
	
	private boolean autosave;

	public SignInPane(Stage s, Ini set) {
		super();
		
		// no people have left or returned just yet
		left = 0;
		returned = 0;
		stillOut = 0;

		// get config settings
		settings = set;
		
		autosave = settings.get("settings", "autosave", Boolean.class);
		
		// set stage
		stage = s;
		
		// set instance variables
		leavingCampCurfew = curfewTime(settings.get("curfewTimes", "leavingCampCurfew"));
		nightOffCurfew = curfewTime(settings.get("curfewTimes", "nightOffCurfew"));
		dayOffCurfew = curfewTime(settings.get("curfewTimes", "dayOffCurfew"));
		
		attendanceFile = new File(settings.get("filePaths", "attendanceFilePath"));
		
		// get info text
		infoText = "";
		getFileContents();
		
		//initialize staff list
		staffList = new HashMap<String, StaffMember>();
		
		// create local workbook from attendanceFile, only continue if workbook creation is acceptable
		try (FileInputStream afis = new FileInputStream(attendanceFile)) {
			workbook = new XSSFWorkbook(afis);
		} catch (EmptyFileException | IOException e) {			
			Alert fileNotAccessible = new Alert(AlertType.ERROR, "Unable to access \"" + attendanceFile.getName()
					+ "\"\nPlease choose a different file.");
			fileNotAccessible.setTitle("Attendance File Not Accessible");
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());
			fileNotAccessible.getDialogPane().lookupButton(ButtonType.OK).setId("red");
			fileNotAccessible.initOwner(stage);
			fileNotAccessible.showAndWait();
			
			Platform.exit();
		}
		
		attendanceSheet = workbook.getSheet(LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))); // get sheet with today's date
		if (attendanceSheet == null) { // no sheet with today's date exists
			int templateIndex = workbook.getSheetIndex("Daily Attendance Template"); // get index for template sheet
			// create copy of template with today's date as the name
			attendanceSheet = workbook.cloneSheet(templateIndex, LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy")));
		} // TODO: if sheet with today's date exists, read from it and load the info saved there
		workbook.setSheetOrder(attendanceSheet.getSheetName(), 1); // move today's sheet to almost beginning of workbook (key sheet is first)
		
		keySheet = workbook.getSheet("Key"); // get key sheet
		
		getMasterStaffList(); // get list of staff from keySheet
		
		initializeAttendanceSheet(); // get locations of columns from header row
		
		System.out.println(staffRowNum);
		// sets the initial value of staffRowNum to be the first row
		//  that doesn't have a staff member already written into it
		while (attendanceSheet.getRow(staffRowNum++).getCell(idCol) != null);
		
		setup();
	}
	
	// reads information from file to display if the info button is clicked
	// TODO: need to update contents of this text file
	private void getFileContents() {
			
		// get location of the info file for this pane
		String infoPath = settings.get("filePaths", "infoPath", String.class);
		String infoFileName = infoPath.split("/")[infoPath.split("/").length - 1]; // get the file name
			
		// set up reader to read from file
		try {
			infoReader = new BufferedReader(new FileReader(infoPath));
		} catch (FileNotFoundException e) {		
			Alert fileNotAccessible = new Alert(AlertType.ERROR, "Unable to access \"" + infoFileName + 
					"\" file.\nPlease create this file in the " + infoPath.substring(0, infoPath.lastIndexOf("/")) + " directory.");
			fileNotAccessible.setTitle("Info File Not Accessible");
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());
			fileNotAccessible.getDialogPane().lookupButton(ButtonType.OK).setId("red");
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
	
	// takes the string entered as curfew time and converts it to the date and time of curfew
	private LocalDateTime curfewTime(String curfewString) {

		int hour = 0, minute = 0; // variables to hold the input hour and minute
		
		// a regex and matcher that matches 12-hr time with optional leading zero, optional separator
		//  mandatory meridem indicators (but optionally separated, case-insenstive, and with optional m/M)
		Pattern twelveHrTime = Pattern.compile("^(1[0-2]|0?[1-9]):?([0-5]\\d)?\\s*([AaPp])[Mm]?$");
		Matcher twelveHrMatcher = twelveHrTime.matcher(curfewString);
		
		// a regex and matcher that matches 24-hr time with optional leading zero and optional separator
		Pattern twentyFourHrTime = Pattern.compile("^(2[0-3]|1\\d|0?\\d):?([0-5]\\d)?$");
		Matcher twentyFourHrMatcher = twentyFourHrTime.matcher(curfewString);
		
		if (twelveHrMatcher.find()) { // if the curfew string matches 12-hr time
			
			hour = Integer.parseInt(twelveHrMatcher.group(1)); // set hour variable to input hour
			if (twelveHrMatcher.group(3).toLowerCase().equals("p"))
				hour += 12; // if the time is PM, add 12 to convert to 24-hour time
			
			// set minute variable to input minute (if only 2 groups, minute is 0)
			minute = twelveHrMatcher.group(2) != null ? Integer.parseInt(twelveHrMatcher.group(2)) : 0;
			
		} else if (twentyFourHrMatcher.find()){ // else if the curfew string matches 24-hr time
			
			hour = Integer.parseInt(twentyFourHrMatcher.group(1)); // set hour variable to input hour
			 // set minute variable to input minute (if only 1 group, minute is 0)
			minute = twentyFourHrMatcher.group(2) != null ? Integer.parseInt(twentyFourHrMatcher.group(2)) : 0;
			
		} else { // if time is invalid, alert user
			
			Alert invalidCurfewTime = new Alert(AlertType.ERROR, "Curfew time was not input in a known format. Please fix this in \"config.ini\"\n"
					+ "Valid formats are:\n- 24 hour time with or without leading zero, with or without a time separator;\n"
					+ "- 12 hour time with or without a leading zero, with or without a time separator, with or without a space between the minutes and the meridiem, "
					+ "with or without the \"M\" in the meridiem");
			invalidCurfewTime.setTitle("Invalid Time Format");
			invalidCurfewTime.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());
			invalidCurfewTime.getDialogPane().lookupButton(ButtonType.OK).setId("red");
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
	
	// reads from the "key" sheet on the spreadsheet to get a list of every possible staff member
	//  that could sign out/in in this session and their information
	private void getMasterStaffList() {
		// set columns
		keyBunkCol = 0;	// column A: bunk/position
		keyNameCol = 1;	// column B: name
		keyIDCol = 2;	// column C: ID
		
		ontimeCol = 3;	// column D: on time
		lateCol = 4;	// column E: late
		absentCol = 5;	// column F: absent
		
		// then, loop through the sheet and collect all the data
		for (int i = keySheet.getFirstRowNum() + 1; i < keySheet.getLastRowNum() + 1; i++) {
			String bunk, name, id;
			int ontime, late, absent;
			
			// only get cell values if there are cell values
			if (keySheet.getRow(i) != null) {
			
				// get name, bunk, and ID (check whether ID is a string or a number)
				bunk = keySheet.getRow(i).getCell(keyBunkCol).getStringCellValue();
				name = keySheet.getRow(i).getCell(keyNameCol).getStringCellValue();
				id = keySheet.getRow(i).getCell(keyIDCol).getCellType() == CellType.STRING 
						? keySheet.getRow(i).getCell(keyIDCol).getStringCellValue()
								: keySheet.getRow(i).getCell(keyIDCol).getNumericCellValue() + "";
						
				// get summary statistics
				ontime = keySheet.getRow(i).getCell(ontimeCol) == null
						? 0 :(int) keySheet.getRow(i).getCell(ontimeCol).getNumericCellValue();
				late = keySheet.getRow(i).getCell(lateCol) == null
						? 0: (int) keySheet.getRow(i).getCell(lateCol).getNumericCellValue();
				absent = keySheet.getRow(i).getCell(absentCol) == null
						? 0 : (int) keySheet.getRow(i).getCell(absentCol).getNumericCellValue();
						
				staffList.put(id, new StaffMember(bunk, name, id, ontime, late, absent, false, false, i)); // TODO: set boolean args to proper values by reading from yesterday's sheet
																											// TODO: also set todayRow in constructor?
			}
		}
	}
	
	// initializes column trackers, writes curfew times to respective cells
	private void initializeAttendanceSheet() {
		// set columns
		bunkCol = 0;	// column A: bunk/position
		nameCol = 1;	// column B: name
		idCol = 2;		// column C: ID
		
		timeOutCol = 3;	// column D: time out
		timeInCol = 4;	// column E: time in
		
		// write leaving camp curfew time to row 2 in today's column if different than what's already there
		if (attendanceSheet.getRow(1).getCell(8) == null)
			attendanceSheet.getRow(1).createCell(8).setCellValue(leavingCampCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(attendanceSheet.getRow(1).getCell(8).getStringCellValue().equals(leavingCampCurfew.format(DateTimeFormatter.ofPattern("h:mm a")))))
			attendanceSheet.getRow(1).getCell(8).setCellValue(leavingCampCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		
		// write night off curfew time to row 3 in today's column if different than what's already there
		if (attendanceSheet.getRow(2).getCell(8) == null)
			attendanceSheet.getRow(2).createCell(8).setCellValue(nightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(attendanceSheet.getRow(2).getCell(8).getStringCellValue().equals(nightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")))))
			attendanceSheet.getRow(2).getCell(8).setCellValue(nightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		
		// write day off curfew time to row 4 in today's column if different than what's already there
		if (attendanceSheet.getRow(3).getCell(8) == null)
			attendanceSheet.getRow(3).createCell(8).setCellValue(dayOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(attendanceSheet.getRow(3).getCell(8).getStringCellValue().equals(dayOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")))))
			attendanceSheet.getRow(3).getCell(8).setCellValue(dayOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		
		attendanceSheet.autoSizeColumn(8); // resize column to fit
	}

	// sets up layout and functionality of SignInPane
	private void setup() {

		// set up grid layout and sizing
		this.setAlignment(Pos.CENTER);
		this.setHgap(15);
		this.setVgap(20);
		this.setPadding(new Insets(30));

		// header
		Label title = new Label("Sign In, Out"); // TODO: replace comma (',') with a slash ('/') if I have a font that has one
		title.setId("header");
		SignInPane.setHalignment(title, HPos.CENTER);
		this.add(title, 0, 0, 2, 1);


		/* left column (clock + time to curfew, view unaccounted for, save, exit) */

		// clock
		Clock currentTime = new Clock();
		Label clockLabel = new Label("Current Time:");
		HBox currentTimeBox = new HBox(this.getHgap());
		currentTime.setMinWidth(USE_PREF_SIZE);
		clockLabel.setMinWidth(USE_PREF_SIZE);
		currentTimeBox.setAlignment(Pos.CENTER);
		currentTimeBox.getChildren().addAll(clockLabel, currentTime);

		
		// curfew times
		
		// normal
		Label normalCurfewLabel = new Label("Leaving Camp Curfew:");
		Label normalCurfewTimeLabel = new Label();
		normalCurfewTimeLabel.setText(leavingCampCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		HBox normalCurfewBox = new HBox(this.getHgap());
		normalCurfewLabel.setMinWidth(USE_PREF_SIZE);
		normalCurfewTimeLabel.setMinWidth(USE_PREF_SIZE);
		normalCurfewBox.setAlignment(Pos.CENTER);
		normalCurfewBox.getChildren().addAll(normalCurfewLabel, normalCurfewTimeLabel);
		
		// night off
		Label nightOffCurfewLabel = new Label("Night off Curfew: ");
		Label nightOffCurfewTimeLabel = new Label();
		nightOffCurfewTimeLabel.setText(nightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		HBox nightOffCurfewBox = new HBox(this.getHgap());
		nightOffCurfewLabel.setMinWidth(USE_PREF_SIZE);
		nightOffCurfewTimeLabel.setMinWidth(USE_PREF_SIZE);
		nightOffCurfewBox.setAlignment(Pos.CENTER);
		nightOffCurfewBox.getChildren().addAll(nightOffCurfewLabel, nightOffCurfewTimeLabel);
		
		// day off
		Label dayOffCurfewLabel = new Label("Day off Curfew: ");
		Label dayOffCurfewTimeLabel = new Label();
		dayOffCurfewTimeLabel.setText(dayOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		HBox dayOffCurfewBox = new HBox(this.getHgap());
		dayOffCurfewLabel.setMinWidth(USE_PREF_SIZE);
		dayOffCurfewTimeLabel.setMinWidth(USE_PREF_SIZE);
		dayOffCurfewBox.setAlignment(Pos.CENTER);
		dayOffCurfewBox.getChildren().addAll(dayOffCurfewLabel, dayOffCurfewTimeLabel);
		
		
		// add clocks to VBox to hold them
		VBox clockBox = new VBox(this.getVgap() * 0.5);
		clockBox.getChildren().addAll(currentTimeBox, normalCurfewBox, nightOffCurfewBox, dayOffCurfewBox);
		
		// buttons
		
		VBox listButtons = new VBox(this.getVgap());
		Button save = new Button("Save");
		Button saveAndExit = new Button("Save and Exit");
		
		save.setId("red");
		saveAndExit.setId("red");

		save.setMinWidth(USE_PREF_SIZE);
		saveAndExit.setMinWidth(USE_PREF_SIZE);

		HBox.setHgrow(save, Priority.ALWAYS);
		HBox.setHgrow(saveAndExit, Priority.ALWAYS);

		save.setMaxWidth(Double.MAX_VALUE);
		saveAndExit.setMaxWidth(Double.MAX_VALUE);
		
		listButtons.getChildren().addAll(new HBox(save), new HBox(saveAndExit));

		VBox leftCol = new VBox(this.getVgap());
		leftCol.getChildren().addAll(clockBox, listButtons);
		this.add(leftCol, 0, 1, 1, 2);

		/* right column (sign-in box, confirmation area) */

		// sign-in instructions, entry point, and confirm button
		Label scanLabel = new Label("Please enter a staff member's ID");
		scanLabel.setMinWidth(USE_PREF_SIZE);
		
		TextField idField = new TextField();
		
		Button signIn = new Button("Sign In/Out");
		signIn.setDefaultButton(true);
		HBox.setHgrow(signIn, Priority.ALWAYS);
		signIn.setMaxWidth(Double.MAX_VALUE);
		signIn.setId("green");
		HBox signInBox = new HBox(this.getHgap());
		signInBox.getChildren().addAll(idField, signIn);
		
		// curfew selection radio buttons
		// TODO: do I need to add some kind of disclaimer that the time selection only matters when signing someone out?
		ToggleGroup curfewTimeSelection = new ToggleGroup();
		RadioButton normal = new RadioButton("Leaving Camp");
		RadioButton nightOff = new RadioButton("Night Off");
		RadioButton dayOff = new RadioButton("Day Off");
		RadioButton visitor = new RadioButton("Visitor");
		normal.getStyleClass().add("radiobutton");
		nightOff.getStyleClass().add("radiobutton");
		dayOff.getStyleClass().add("radiobutton");
		visitor.getStyleClass().add("radiobutton");
		normal.setToggleGroup(curfewTimeSelection);
		nightOff.setToggleGroup(curfewTimeSelection);
		dayOff.setToggleGroup(curfewTimeSelection);
		visitor.setToggleGroup(curfewTimeSelection);
		normal.setSelected(true);
		
		HBox timeSelectionBox1 = new HBox(this.getHgap());
		HBox timeSelectionBox2 = new HBox(this.getHgap());
		timeSelectionBox1.getChildren().addAll(normal, visitor);
		timeSelectionBox2.getChildren().addAll(nightOff, dayOff);
		
		VBox idBox = new VBox(this.getVgap());
		idBox.getChildren().addAll(scanLabel, signInBox, timeSelectionBox1, timeSelectionBox2);

		// confirmation area
		TextArea confirmation = new TextArea();
		confirmation.setEditable(false);
		confirmation.setWrapText(true);
		confirmation.setPrefWidth(scanLabel.getWidth());
		confirmation.setPrefRowCount(3);
		idBox.getChildren().add(confirmation);
		this.add(idBox, 1, 1);
		
		// button to show list of signed-out staff
		Button signedOutList = new Button("Show Signed-Out Staff");
		signedOutList.setMinWidth(USE_PREF_SIZE);
		HBox.setHgrow(signedOutList, Priority.ALWAYS);
		signedOutList.setMaxWidth(Double.MAX_VALUE);
		signedOutList.setId("green");
		
		GridPane infoSpacing = new GridPane();
		HBox.setHgrow(infoSpacing, Priority.ALWAYS);
		
		// add information button (will pop up credits and instructions)
		Button info = new Button("i");
		info.getStyleClass().add("info");
		info.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
		
		HBox signedOutandInfoBox = new HBox();
		signedOutandInfoBox.getChildren().addAll(signedOutList, infoSpacing, info);
		this.add(signedOutandInfoBox, 0, 2, 2, 1);
		
		// stage and scene for viewUnaccounted
		Stage extraStage = new Stage();
		Scene unaccScene = new Scene(new Label("Something's gone wrong"));
		
		// set info button behavior (show credits, brief explanation of what to do)
		info.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Alert infoDialog = new Alert(AlertType.NONE, infoText, ButtonType.CLOSE);
				infoDialog.setTitle("Credits and Instructions — Sign-in");
				infoDialog.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());
				infoDialog.getDialogPane().lookupButton(ButtonType.CLOSE).setId("red");
				infoDialog.initOwner(info.getScene().getWindow());
				infoDialog.initModality(Modality.NONE);
				infoDialog.setResizable(true);
				infoDialog.getDialogPane().setPrefWidth(stage.getWidth());
				infoDialog.show();
			}
					
		});

		// set sign-in button behavior
		signIn.setOnAction(new EventHandler<ActionEvent>() {

			public void handle(ActionEvent event) {
				// save staff ID and clear idField text
				String staffID = idField.getText();
				idField.clear();
				
				// only search if something was actually entered
				if (!staffID.isEmpty()) {
					// check to see if an ID was entered or if it was a name
					StaffMember entered = staffList.get(staffID); // is it an ID?
					if (entered == null) // that ID wasn't found--check if it's a name
						for (String id : staffList.keySet())
							if (staffList.get(id).getName().equalsIgnoreCase(staffID))
								entered = staffList.get(id);
					
					if (entered == null) { // if we still haven't found anything
						// the staff member doesn't exist
						confirmation.setText(staffID + " not found");
					} else {
						if (entered.isSignedIn() && entered.isSignedOut()) { // if already signed in and out
							// no further work required
							confirmation.setText(entered.getName() + " is fully accounted-for"); // TODO: should this language be changed?
						} else if (!entered.isSignedIn() && entered.isSignedOut()) { // if signed out but not in
							// person is coming back to camp and needs to be signed in
							signInAndCheckTime(entered); // update spreadsheet to sign staff member in
							entered.signIn(); // mark them as signed in
							confirmation.setText(entered.getName() + " signed in");
						} else if (entered.isSignedIn() && !entered.isSignedOut()) { // if signed in but not out (thus is a visitor)
							signVisitorOut(entered); // update spreadsheet to sign visitor out
							entered.signOut();
							confirmation.setText("Visitor " + entered.getName() + " signed out");
						} else if (!entered.isSignedIn() && !entered.isSignedOut()) { // neither signed in nor out
							// check if they're a visitor or a staff member
							if (visitor.isSelected()) { // person is a visitor
								signVisitorIn(entered); // update spreadsheet to sign visitor in
								entered.signIn(); // mark them as signed in
								confirmation.setText("Visitor " + entered.getName() + " signed in");
							} else { // person is a staff member
								signOutAndWriteCurfew(entered); // update spreadsheet to sign staff member out
								entered.signOut(); // mark them as signed out
								confirmation.setText(entered.getName() + " signed out");
							}
						}
						
						// save to spreadsheet if autosave is on
						if (autosave) {
							// write data to attendanceFile
							try (FileOutputStream afos = new FileOutputStream(attendanceFile)) {
								workbook.write(afos);
								// resize columns to fit
								for (int i = 0; i < 5; i++) {
									attendanceSheet.autoSizeColumn(i);
									keySheet.autoSizeColumn(i);
								}
								keySheet.autoSizeColumn(5); // keySheet has 1 additional column
							} catch (IOException e) {
								confirmation.setText("Autosave error. Try manually saving.");
							} 
						}
						
						// if signedOutList is showing, update it
						if (extraStage.isShowing())
							signedOutList.fire();

					}
				} else {
					confirmation.setText("No ID entered");
				}
			}
			
			// given a staff member who needs to sign in
			//  updates the staff member's key sheet statistics based on the curfew they had
			//  writes the sign in time to the staff member's time in column for today (and the curfew they had)
			//  and colors the time in column to the correct color based on status
			//  and updates the counts of people currently out of camp
			public void signInAndCheckTime(StaffMember sm) {
				
				// set which curfew they used to sign out
				LocalDateTime curfewUsed = null;
				switch (attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).getStringCellValue().toLowerCase()) {
					case "leaving camp":
						curfewUsed = leavingCampCurfew;
						break;
					case "night off":
						curfewUsed = nightOffCurfew;
						break;
					case "day off":
						curfewUsed = dayOffCurfew;
						break;
					default:
						break;
				}
				
				// set time in column to now
				attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
			
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
				if (curfewUsed.compareTo(LocalDateTime.now()) > 0) {
					// set timeInCol to onTime style
					attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).setCellStyle(onTime);
				
					// increment on time column on key sheet
					if ((keySheet.getRow(sm.getKeyRow()) != null) 
							&& keySheet.getRow(sm.getKeyRow()).getCell(ontimeCol) != null) // the cell exists
						keySheet.getRow(sm.getKeyRow()).getCell(ontimeCol).setCellValue(keySheet.getRow(sm.getKeyRow()).getCell(ontimeCol).getNumericCellValue() + 1);
					else // the cell does not exist
						keySheet.getRow(sm.getKeyRow()).createCell(ontimeCol).setCellValue(1);
				} else { // staff member is late
					// set timeInCol to late style
					attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).setCellStyle(late);
					
					// increment late column on key sheet
					if ((keySheet.getRow(sm.getKeyRow()) != null) 
							&& keySheet.getRow(sm.getKeyRow()).getCell(lateCol) != null) // the cell exists
						keySheet.getRow(sm.getKeyRow()).getCell(lateCol).setCellValue(keySheet.getRow(sm.getKeyRow()).getCell(lateCol).getNumericCellValue() + 1);
					else // the cell does not exist
						keySheet.getRow(sm.getKeyRow()).createCell(lateCol).setCellValue(1);
				}
				
				returned++; // increment the number of people who've signed in
				stillOut--; // decrement the number of people who are still out of camp
				attendanceSheet.getRow(6).getCell(8).setCellValue(returned);
				attendanceSheet.getRow(7).getCell(8).setCellValue(stillOut);
			}
			
			// given a visitor who needs to sign out
			//  updates today's sheet to write the time that the visitor left camp
			public void signVisitorOut(StaffMember sm) {
				
				// set timeOutCol of this staff member to current time
				attendanceSheet.getRow(sm.getTodayRow()).getCell(timeOutCol).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")) + " (visitor)");
				// clear styling of this cell
				attendanceSheet.getRow(sm.getTodayRow()).getCell(timeOutCol).setCellStyle(workbook.createCellStyle());
			}
			
			// given a visitor who needs to sign in
			//  updates today's sheet to write the time that the visitor entered camp
			//  and that they're a visitor
			public void signVisitorIn(StaffMember sm) {
				
				// create new row at the bottom of the spreadsheet
				XSSFRow newRow = null;
				if (staffRowNum > attendanceSheet.getLastRowNum())
					newRow = attendanceSheet.createRow(staffRowNum);
				else
					newRow = attendanceSheet.getRow(staffRowNum);
				
				sm.setTodayRow(staffRowNum++); // set what row this staff member is in and increment counter
				
				// set identity information
				
				newRow.createCell(bunkCol).setCellValue(sm.getBunk());	// set the bunk
				newRow.createCell(nameCol).setCellValue(sm.getName());	// set the name
				newRow.createCell(idCol).setCellValue(sm.getID()); 		// set the ID
				
				// set cell styles for cell border, absent
				XSSFCellStyle absent = workbook.createCellStyle();
				java.awt.Color absentColor = Color.decode(settings.get("sheetFormat", "absentColor", String.class));
				absent.setFillForegroundColor(new XSSFColor(absentColor, new DefaultIndexedColorMap()));
				absent.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				
				XSSFCellStyle rightBorder = workbook.createCellStyle();
				rightBorder.setBorderRight(BorderStyle.THIN);
				
				newRow.getCell(idCol).setCellStyle(rightBorder);
				
				// set time out column to visitor and color with absent color
				newRow.createCell(timeOutCol).setCellValue("Visitor");
				newRow.getCell(timeOutCol).setCellStyle(absent);
				
				// set time in column to current time
				newRow.createCell(timeInCol).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));;
			}
			
			// given a staff member who needs to sign out
			//  updates today's sheet to write the time that the staff member left camp
			//  and writes their curfew to their time in column
			//  and updates the counts of people currently out of camp
			public void signOutAndWriteCurfew(StaffMember sm) {

				// create new row at the bottom of the spreadsheet
				XSSFRow newRow = null;
				if (staffRowNum > attendanceSheet.getLastRowNum())
					newRow = attendanceSheet.createRow(staffRowNum);
				else
					newRow = attendanceSheet.getRow(staffRowNum);
				
				sm.setTodayRow(staffRowNum++); // set what row this staff member is in and increment counter
				
				// set identity information
				
				newRow.createCell(bunkCol).setCellValue(sm.getBunk());	// set the bunk
				newRow.createCell(nameCol).setCellValue(sm.getName());	// set the name
				newRow.createCell(idCol).setCellValue(sm.getID()); 		// set the ID
				
				// set cell styles for cell border, day off, other
				XSSFCellStyle dayOffStyle = workbook.createCellStyle();
				java.awt.Color dayOffColor = Color.decode(settings.get("sheetFormat", "excusedColor", String.class));
				dayOffStyle.setFillForegroundColor(new XSSFColor(dayOffColor, new DefaultIndexedColorMap()));
				dayOffStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				
				XSSFCellStyle absent = workbook.createCellStyle();
				java.awt.Color absentColor = Color.decode(settings.get("sheetFormat", "absentColor", String.class));
				absent.setFillForegroundColor(new XSSFColor(absentColor, new DefaultIndexedColorMap()));
				absent.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				
				XSSFCellStyle rightBorder = workbook.createCellStyle();
				rightBorder.setBorderRight(BorderStyle.THIN);
				
				newRow.getCell(idCol).setCellStyle(rightBorder);
				
				// set time out column to current time
				newRow.createCell(timeOutCol).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
				
				// set time in column to curfew name and color with absent/day off color
				newRow.createCell(timeInCol).setCellValue(((RadioButton)curfewTimeSelection.getSelectedToggle()).getText());
				newRow.getCell(timeInCol).setCellStyle(dayOff.isSelected() ? dayOffStyle : absent);
				
				// increment counts of people who have left camp and are still out of camp
				left++;
				stillOut++;
				attendanceSheet.getRow(5).getCell(8).setCellValue(left);
				attendanceSheet.getRow(7).getCell(8).setCellValue(stillOut);
			}
		});



		// event handlers for left column buttons

		// pulls up list of staff members who have yet to sign in in this session
		// TODO: might be able to refactor this to be more efficient by using the HashMap
		signedOutList.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {

				// if there are no staff left unaccounted, print a message saying so and leave this handle method
				if (noUnaccountedStaff()) {
					confirmation.setText("There's currently no one who needs to sign in");
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
				unaccScene.getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());

				// only need to do these things if the stage isn't currently on screen
				if (!extraStage.isShowing()) {
					// set up stage
					extraStage.setScene(unaccScene);
					extraStage.setMinWidth(scrollPane.getMinWidth());
					extraStage.setMaxHeight(scrollPane.getMaxHeight());
					extraStage.setTitle("Unaccounted-for Staff");
					extraStage.getIcons().add(new Image(settings.get("filePaths", "iconPath", String.class)));
					extraStage.centerOnScreen();
					extraStage.show();
				}
			}
			
			// returns whether or not there are still unaccounted-for staff
			public boolean noUnaccountedStaff() {
				
				// if no staff have been added yet, there are also no unaccounted staff
				if (staffRowNum == attendanceSheet.getFirstRowNum() + 1) return true;

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member
				for (int i = attendanceSheet.getFirstRowNum() + 1; i < staffRowNum; i++)
					// if today's time in column does not exist, is empty, or marks that the staff member is out, the staff member is unaccounted for
					if ((attendanceSheet.getRow(i) != null) && (attendanceSheet.getRow(i).getCell(timeInCol) == null 
					|| attendanceSheet.getRow(i).getCell(timeInCol).getCellType() == CellType.BLANK 
					|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("leaving camp")
					|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("night off")
					|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("day off")))
						return false;

				return true;
			}

			// counts the number of unique bunks in workbook and returns the list of unique bunks
			public List<String> countBunks() {

				List<String> uniqueBunks = new ArrayList<String>();

				// loop through all rows of the sheet, starting at the first row (so ignoring the header rows)
				//  and look at the "bunk" column to count how many unique bunks there are
				for (int i = attendanceSheet.getFirstRowNum() + 1; i < staffRowNum; i++) {

					// if the current bunk is new, add it to the list of unique bunks
					if ((attendanceSheet.getRow(i) != null) 
							&& attendanceSheet.getRow(i).getCell(bunkCol) != null 
							&& !uniqueBunks.contains(attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue()))
						uniqueBunks.add(attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue());
				}

				return uniqueBunks;
			}

			// given a bunk name, returns whether or not there are any unaccounted staff remaining
			//  in that bunk
			public boolean bunkEmpty(String bunk) {

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member in this bunk
				for (int i = attendanceSheet.getFirstRowNum() + 1; i < staffRowNum; i++)
					if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue().equals(bunk))
						// if today's time in column does not exist, is empty, or marks that the staff member is out, the staff member is unaccounted for
						if ((attendanceSheet.getRow(i) != null) && (attendanceSheet.getRow(i).getCell(timeInCol) == null 
							|| attendanceSheet.getRow(i).getCell(timeInCol).getCellType() == CellType.BLANK 
							|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("leaving camp")
							|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("night off")
							|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("day off")))
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
				for (int i = attendanceSheet.getFirstRowNum() + 1; i < staffRowNum; i++) {

					if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue().equals(bunk)) {

						// if today's time in column does not exist, is empty, or marks that the staff member is out, the staff member is unaccounted for
						if ((attendanceSheet.getRow(i) != null) && (attendanceSheet.getRow(i).getCell(timeInCol) == null 
							|| attendanceSheet.getRow(i).getCell(timeInCol).getCellType() == CellType.BLANK 
							|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("leaving camp")
							|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("night off")
							|| attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase().equals("day off"))) {

							Button staffMember = new Button(attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue());
							staffMember.setId("list-button");
							staffMember.setMinWidth(USE_PREF_SIZE);
							HBox staffNameBox = new HBox(15);
							staffNameBox.setAlignment(Pos.CENTER);
							staffNameBox.getChildren().add(staffMember);
							bunkBox.getChildren().add(staffNameBox);

							// when a staff member is clicked, open a popup window to allow user to
							//  mark them as on shmira or a day off
							XSSFRow staffRow = attendanceSheet.getRow(i); // stores current row for use in event handler
							staffMember.setOnAction(new EventHandler<ActionEvent>() {

								@Override
								public void handle(ActionEvent event) {
									Alert options = new Alert(AlertType.NONE, "Sign this staff member in:",
											new ButtonType("Sign In", ButtonData.OTHER),
											// TODO: remove these two buttons (Shmira, Day Off)
											new ButtonType("Shmira", ButtonData.OTHER),
											new ButtonType("Day Off", ButtonData.OTHER),
											ButtonType.CANCEL);
									options.setTitle("Manual Sign-In");
									options.setHeaderText(staffMember.getText());
									options.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());
									options.getDialogPane().lookupButton(ButtonType.CANCEL).setId("red");
									options.initOwner(staffMember.getScene().getWindow());
									options.showAndWait();
									
									// create cell style to be used when signing staff member in for day off or shmira
									XSSFCellStyle onTime = workbook.createCellStyle();
									java.awt.Color onTimeColor = Color.decode(settings.get("sheetFormat", "onTimeColor", String.class));
									onTime.setFillForegroundColor(new XSSFColor(onTimeColor, new DefaultIndexedColorMap()));
									onTime.setFillPattern(FillPatternType.SOLID_FOREGROUND);

									// TODO: remove this section of code when I remove the "shmira" button
									if (options.getResult().getText().equals("Shmira")) {
										// staff member should be signed in as on shmira

										// set time in column to show that the staff member is on shmira
										if (staffRow.getCell(timeInCol) == null)
											staffRow.createCell(timeInCol).setCellValue("Shmira");
										else
											staffRow.getCell(timeInCol).setCellValue("Shmira");

										// update "on time" column (if it exists)
										if (ontimeCol != -1) { // there is an "on time" column
											if (staffRow.getCell(ontimeCol) != null) // the cell exists
												staffRow.getCell(ontimeCol).setCellValue(staffRow.getCell(ontimeCol).getNumericCellValue() + 1);
											else // the cell does not exist
												staffRow.createCell(ontimeCol).setCellValue(1);
										}
										
										// recolor cell background
										staffRow.getCell(timeInCol).setCellStyle(onTime);
										
										// print a confirmation
										confirmation.setText(staffMember.getText() + " signed in as on shmira");
										
										if (autosave) {
											// write data to attendanceFile
											try (FileOutputStream afos = new FileOutputStream(attendanceFile)) {
												workbook.write(afos);
												// resize columns to fit
												for (int i = 0; i < 5; i++) {
													attendanceSheet.autoSizeColumn(i);
													keySheet.autoSizeColumn(i);
												}
												keySheet.autoSizeColumn(5); // keySheet has 1 additional column
											} catch (IOException e) {
												confirmation.setText("Autosave error. Try manually saving.");
											} 
										}
									
									// TODO: remove this section of code when I remove the "day off" button
									} else if (options.getResult().getText().equals("Day Off")) {
										// staff member should be signed in as on day off

										// set time in column to show that the staff member is on a day off
										if (staffRow.getCell(timeInCol) == null)
											staffRow.createCell(timeInCol).setCellValue("Day Off");
										else
											staffRow.getCell(timeInCol).setCellValue("Day Off");

										// update "on time" column (if it exists)
										if (ontimeCol != -1) {// there is an "on time" column
											if (staffRow.getCell(ontimeCol) != null) // the cell exists
												staffRow.getCell(ontimeCol).setCellValue(staffRow.getCell(ontimeCol).getNumericCellValue() + 1);
											else // the cell does not exist
												staffRow.createCell(ontimeCol).setCellValue(1);
										}
										
										// recolor cell background
										staffRow.getCell(timeInCol).setCellStyle(onTime);
										
										// print a confirmation
										confirmation.setText(staffMember.getText() + " signed in as on a day off");
										
										if (autosave) {
											// write data to attendanceFile
											try (FileOutputStream afos = new FileOutputStream(attendanceFile)) {
												workbook.write(afos);
												// resize columns to fit
												for (int i = 0; i < 5; i++) {
													attendanceSheet.autoSizeColumn(i);
													keySheet.autoSizeColumn(i);
												}
												keySheet.autoSizeColumn(5); // keySheet has 1 additional column
											} catch (IOException e) {
												confirmation.setText("Autosave error. Try manually saving.");
											} 
										}
										
									} else if (options.getResult().getText().equals("Sign In")) {
										// staff member should be signed in normally
										// do this by writing the staff member's name into the entry box and firing the sign-in button
										
										idField.setText(staffMember.getText());
										signIn.fire();
									}

									// refresh list of unaccounted staff members
									signedOutList.fire();
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
					// resize columns to fit
					for (int i = 0; i < 5; i++) {
						attendanceSheet.autoSizeColumn(i);
						keySheet.autoSizeColumn(i);
					}
					keySheet.autoSizeColumn(5); // keySheet has 1 additional column
				} catch (IOException e) {
					confirmation.setText("Unable to save to \"" + attendanceFile.getName() + "\"");
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
				
				// if there are still unaccounted staff, confirm that user still wants to exit
				if (!noUnaccountedStaff()) {
					Alert saveAndExitConf = new Alert(AlertType.CONFIRMATION, "There are still staff members that haven't signed in.\nAre you sure you want to exit?");
					saveAndExitConf.setHeaderText("Save and Exit Confirmation");
					saveAndExitConf.setTitle("Save and Exit Confirmation");
					saveAndExitConf.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());
					saveAndExitConf.getDialogPane().lookupButton(ButtonType.CANCEL).setId("red");
					saveAndExitConf.initOwner(saveAndExit.getScene().getWindow());
					saveAndExitConf.showAndWait();
					
					if (saveAndExitConf.getResult() != ButtonType.OK)
						return; // user does not want to save and exit
				}
				
				// all staff are accounted for or user wants to mark unaccounted-for staff as absent
				markUnaccAbsent();
				
				// write data to attendanceFile
				save.fire();
				
				// close unaccounted-for staff window and stage
				extraStage.close();
				Platform.exit();
			}

			// returns whether or not there are still unaccounted-for staff
			// in order to be "accounted for," the staff member/visitor must fall under one of the following categories:
			// - never signed out and never signed in
			// - signed out and signed back in
			// - signed out for a day off and not signed back in
			public boolean noUnaccountedStaff() {
				
				for (StaffMember sm : staffList.values())
					if (!sm.isSignedOut() && sm.isSignedIn()) // a visitor who's signed in but not out
						return false;
					else if (sm.isSignedOut() && !sm.isSignedIn()) // staff member's signed out but not in. only ok if on a day off
						// is on a day off if their time in column says so
						if (!attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).getStringCellValue().equalsIgnoreCase("day off"))
							return false;

				return true;
			}
			
			// marks all staff that did not sign in as absent, and increments their "absent" column, if it exists
			public void markUnaccAbsent() {
				
				boolean absent;
				
				for (StaffMember sm : staffList.values()) {
					
					absent = false;
					
					if (!sm.isSignedOut() && sm.isSignedIn()) // a visitor who's signed in but not out
						absent = true;
					else if (sm.isSignedOut() && !sm.isSignedIn()) // staff member's signed out but not in. only ok if on a day off
						// is on a day off if their time in column says so
						if (!attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).getStringCellValue().equalsIgnoreCase("day off"))
							absent = true;
					
					//  increment "absent" column
					if (absent) {				
						if ((keySheet.getRow(sm.getKeyRow()) != null) 
								&& keySheet.getRow(sm.getKeyRow()).getCell(absentCol) != null) // the cell exists
							keySheet.getRow(sm.getKeyRow()).getCell(absentCol).setCellValue(keySheet.getRow(sm.getKeyRow()).getCell(absentCol).getNumericCellValue() + 1);
						else // the cell does not exist
							keySheet.getRow(sm.getKeyRow()).createCell(absentCol).setCellValue(1);
					}
				}
			}
		});

		/* change stage close behavior */
		stage.setOnCloseRequest(new EventHandler<WindowEvent>() {

			@Override
			public void handle(WindowEvent event) {
				event.consume(); // consume window-close event

				Alert alert = new Alert(AlertType.CONFIRMATION, "Are you sure you want to exit and lose all unsaved data?\n"
						+ "Click \"OK\" to exit and \"Cancel\" to return to sign-in.");
				alert.setTitle("Exit Confirmation");
				alert.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssFile", String.class)).toExternalForm());
				alert.getDialogPane().lookupButton(ButtonType.CANCEL).setId("red");
				alert.initOwner(stage);
				alert.showAndWait();

				if (alert.getResult().getButtonData() == ButtonData.OK_DONE)
					Platform.exit();

			}
		});
	}

}