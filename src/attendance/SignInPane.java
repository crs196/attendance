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
	
	private List<StaffMember> staffList;
	
	private BufferedReader infoReader;
	private String infoText;

	// used to track which column holds each piece of information
	private int keyBunkCol, keyNameCol, keyIDCol, ontimeCol, lateCol, absentCol;
	private int bunkCol, nameCol, idCol, timeOutCol, timeInCol;
	@Deprecated
	private int todayCol;
	
	// used to track how many people have left and returned
	private int left, returned;
	//used to track what row to write the next staff member on
	private int staffRowNum;
	
	private boolean autosave;

	public SignInPane(Stage s, Ini set) {
		super();
		
		// no people have left or returned just yet
		left = 0;
		returned = 0;
		staffRowNum = 1; // start with writing to row 1
		
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
		staffList = new ArrayList<StaffMember>();
		
		// create local workbook from attendanceFile, only continue if workbook creation is acceptable
		try (FileInputStream afis = new FileInputStream(attendanceFile)) {
			workbook = new XSSFWorkbook(afis);
			
			attendanceSheet = workbook.getSheet(LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))); // get sheet with today's date
			if (attendanceSheet == null) { // no sheet with today's date exists
				int templateIndex = workbook.getSheetIndex("Daily Attendance Template"); // get index for template sheet
				// create copy of template with today's date as the name
				attendanceSheet = workbook.cloneSheet(templateIndex, LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy")));
			}
			workbook.setSheetOrder(attendanceSheet.getSheetName(), 1); // move today's sheet to almost beginning of workbook (key sheet is first)
			
			keySheet = workbook.getSheet("Key");
			
			getMasterStaffList(); // get list of staff from keySheet
			
			initializeAttendanceSheet(); // get locations of columns from header row
			
			setup();
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
	}
	
	// reads information from file to display if the info button is clicked
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
						
				staffList.add(new StaffMember(bunk, name, id, ontime, late, absent, false, false)); // TODO: set boolean args to proper values
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
		ToggleGroup curfewTimeSelection = new ToggleGroup();
		RadioButton normal = new RadioButton("Leaving Camp");
		RadioButton nightOff = new RadioButton("Night Off");
		RadioButton dayOff = new RadioButton("Day Off");
		RadioButton visitor = new RadioButton("Visitor"); // TODO: make this button functional
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

			@Override
			public void handle(ActionEvent event) {

				// save staff ID and clear idField text
				String staffID = idField.getText();
				idField.clear();
				
				// figure out which curfew is being used				
				LocalDateTime curfewUsed = null;
				
				if (normal.isSelected())
					curfewUsed = leavingCampCurfew;
				else if (nightOff.isSelected())
					curfewUsed = nightOffCurfew;
				else if (dayOff.isSelected())
					curfewUsed = dayOffCurfew;

				// only search if an ID was actually entered
				if (!staffID.isEmpty()) {
					
					boolean idFound = false; // staff member has not yet been found
					for (int i = attendanceSheet.getFirstRowNum() + 1; i < attendanceSheet.getLastRowNum() + 1; i++) {

						String currentID = "";
						
						// first check idCol for matches
						
						if((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(idCol) != null
								&& attendanceSheet.getRow(i).getCell(idCol).getCellType() == CellType.NUMERIC)
							currentID = (int) attendanceSheet.getRow(i).getCell(idCol).getNumericCellValue() + "";
						else if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(idCol) != null
								&& attendanceSheet.getRow(i).getCell(idCol).getCellType() == CellType.STRING)
							currentID = attendanceSheet.getRow(i).getCell(idCol).getStringCellValue();
						
						// if the current row's ID matches the one inputted, the staff member was found
						if (currentID.equals(staffID)) {

							idFound = true;
							LocalDateTime now = LocalDateTime.now(); // save current time in case close to curfew

							// if today's attendance column does not exist or is empty, the staff member is unaccounted for
							if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(timeInCol) == null) {

								attendanceSheet.getRow(i).createCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")) + " ("
										+ curfewUsed.format(DateTimeFormatter.ofPattern("h:mm a")) + ")");
								signInStatus(i, now, curfewUsed);
								confirmation.setText(attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
								break; // search is done

							} else if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK) {

								attendanceSheet.getRow(i).getCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")) + " ("
										+ curfewUsed.format(DateTimeFormatter.ofPattern("h:mm a")) + ")");
								signInStatus(i, now, curfewUsed);
								confirmation.setText(attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
								break; // search is done

							} else { // if cell exists and is not blank, staff member has already signed in today
								confirmation.setText(attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue() + " has already signed in");
								break; // search is done
							}
						}
						
						// then, if nameCol is different than idCol, check nameCol for matches
						if (nameCol != idCol) {
							
							if((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(nameCol) != null
									&& attendanceSheet.getRow(i).getCell(nameCol).getCellType() == CellType.NUMERIC)
								currentID = (int) attendanceSheet.getRow(i).getCell(nameCol).getNumericCellValue() + "";
							else if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(nameCol) != null
									&& attendanceSheet.getRow(i).getCell(nameCol).getCellType() == CellType.STRING)
								currentID = attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue();
							
							// if the current row's ID matches the one inputted, the staff member was found
							if (currentID.equals(staffID)) {

								idFound = true;
								LocalDateTime now = LocalDateTime.now(); // save current time in case close to curfew

								// if today's attendance column does not exist or is empty, the staff member is unaccounted for
								if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(todayCol) == null) {

									attendanceSheet.getRow(i).createCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")) + " ("
											+ curfewUsed.format(DateTimeFormatter.ofPattern("h:mm a")) + ")");
									signInStatus(i, now, curfewUsed);
									confirmation.setText(attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
									break; // search is done

								} else if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK) {

									attendanceSheet.getRow(i).getCell(todayCol).setCellValue(now.format(DateTimeFormatter.ofPattern("h:mm a")) + " ("
											+ curfewUsed.format(DateTimeFormatter.ofPattern("h:mm a")) + ")");
									signInStatus(i, now, curfewUsed);
									confirmation.setText(attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue() + " signed in");
									break; // search is done

								} else { // if cell exists and is not blank, staff member has already signed in today
									confirmation.setText(attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue() + " has already signed in");
									break; // search is done
								}
							}
						}
					}
					
					// the id wasn't found, so it's not someone who's signing back in
					// check if it's someone in the key sheet who's signing out
					if (!idFound && !staffID.isEmpty()) {
						
						boolean signedOut = false;
						
						for (StaffMember sm : staffList) {
							if (!signedOut && (staffID.equals(sm.getName()) || staffID.equals(sm.getID()))) { // if we've found the staff member
								// add them to the sheet so that we can later sign them back in
								
								// create new row at the bottom of the spreadsheet
								// TODO: consider sorting spreadsheet by bunk? low priority
								XSSFRow newRow = attendanceSheet.createRow(staffRowNum++);
								newRow.createCell(bunkCol).setCellValue(sm.getBunk()); // set the bunk
								newRow.createCell(nameCol).setCellValue(sm.getName()); // set the name
								newRow.createCell(idCol).setCellValue(sm.getID()); // set the ID
								
								// set time out column to current time
								newRow.createCell(timeOutCol).setCellValue(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
								// set time in column to read the method that the staff member left by (e.g. day off)
								newRow.createCell(timeInCol).setCellValue(((RadioButton)curfewTimeSelection.getSelectedToggle()).getText());
								
								// set cell styles for cell border, day off, other
								XSSFCellStyle dayOff = workbook.createCellStyle();
								java.awt.Color dayOffColor = Color.decode(settings.get("sheetFormat", "excusedColor", String.class));
								dayOff.setFillForegroundColor(new XSSFColor(dayOffColor, new DefaultIndexedColorMap()));
								dayOff.setFillPattern(FillPatternType.SOLID_FOREGROUND);
								
								XSSFCellStyle absent = workbook.createCellStyle();
								java.awt.Color absentColor = Color.decode(settings.get("sheetFormat", "absentColor", String.class));
								absent.setFillForegroundColor(new XSSFColor(absentColor, new DefaultIndexedColorMap()));
								absent.setFillPattern(FillPatternType.SOLID_FOREGROUND);
								
								XSSFCellStyle rightBorder = workbook.createCellStyle();
								rightBorder.setBorderRight(BorderStyle.THIN);
								
								newRow.getCell(idCol).setCellStyle(rightBorder);
								
								if (((RadioButton)curfewTimeSelection.getSelectedToggle()).getText().toLowerCase().equals("day off"))
									newRow.getCell(timeInCol).setCellStyle(dayOff);
								else
									newRow.getCell(timeInCol).setCellStyle(absent);
								
								left++; // increment the number of people who've signed out
								attendanceSheet.getRow(5).getCell(8).setCellValue(left);
								
								confirmation.setText(sm.getName() + " signed out");
								signedOut = true;
							}
						}
						
						if (!signedOut) {
							 confirmation.setText("Staff member " + staffID + " not found");
						}
					} 
					
				} else {
					confirmation.setText("No ID entered");
				}
				
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
				
				if (extraStage.isShowing())
					signedOutList.fire(); // TODO: there may be a better way to update the signed-out list other than clicking the button again
			}

			// given a staff member (via row number) and sign-in time, 
			//  increments the proper summary statistic column (if it exists)
			//  and colors the staff member's "today cell" as either onTimeColor or lateColor, depending on sign-in time
			public void signInStatus(int rowNum, LocalDateTime signInTime, LocalDateTime curfewUsed) {
				
				returned++; // increment the number of people who've signed in
				attendanceSheet.getRow(6).getCell(8).setCellValue(returned);

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
				if (curfewUsed.compareTo(signInTime) > 0) {
					// set todayCol to onTime style
					attendanceSheet.getRow(rowNum).getCell(todayCol).setCellStyle(onTime);
					
					if (ontimeCol != -1) {// there is an "on time" column
						if ((attendanceSheet.getRow(rowNum) != null) && attendanceSheet.getRow(rowNum).getCell(ontimeCol) != null) // the cell exists
							attendanceSheet.getRow(rowNum).getCell(ontimeCol).setCellValue(attendanceSheet.getRow(rowNum).getCell(ontimeCol).getNumericCellValue() + 1);
						else // the cell does not exist
							attendanceSheet.getRow(rowNum).createCell(ontimeCol).setCellValue(1);
					}
				} else { // staff member is late
					// set todayCol to late style
					attendanceSheet.getRow(rowNum).getCell(todayCol).setCellStyle(late);
					
					if (lateCol != -1) {// there is an "on time" column
						if ((attendanceSheet.getRow(rowNum) != null) && attendanceSheet.getRow(rowNum).getCell(lateCol) != null) // the cell exists
							attendanceSheet.getRow(rowNum).getCell(lateCol).setCellValue(attendanceSheet.getRow(rowNum).getCell(lateCol).getNumericCellValue() + 1);
						else // the cell does not exist
							attendanceSheet.getRow(rowNum).createCell(lateCol).setCellValue(1);
					}
				}
			}
		});



		// event handlers for left column buttons

		// pulls up list of staff members who have yet to sign in in this session
		signedOutList.setOnAction(new EventHandler<ActionEvent>() {

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

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member
				for (int i = attendanceSheet.getFirstRowNum() + 1; i <= attendanceSheet.getLastRowNum(); i++)
					// if today's attendance column does not exist or is empty, the staff member is unaccounted for
					if ((attendanceSheet.getRow(i) != null) && (attendanceSheet.getRow(i).getCell(todayCol) == null || 
						 								attendanceSheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK))
						return false;

				return true;
			}

			// counts the number of unique bunks in workbook and returns the list of unique bunks
			public List<String> countBunks() {

				List<String> uniqueBunks = new ArrayList<String>();

				// loop through all rows of the sheet, starting at the fifth row (so ignoring the header rows)
				//  and look at the "bunk" column to count how many unique bunks there are
				for (int i = attendanceSheet.getFirstRowNum() + 1; i <= attendanceSheet.getLastRowNum(); i++) {

					// if the current bunk is new, add it to the list of unique bunks
					if ((attendanceSheet.getRow(i) != null) && !uniqueBunks.contains(attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue()))
						uniqueBunks.add(attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue());
				}

				return uniqueBunks;
			}

			// given a bunk name, returns whether or not there are any unaccounted staff remaining
			//  in that bunk
			public boolean bunkEmpty(String bunk) {

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member in this bunk
				for (int i = attendanceSheet.getFirstRowNum() + 1; i <= attendanceSheet.getLastRowNum(); i++)
					if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue().equals(bunk))
						// if today's attendance column does not exist or is empty, the staff member is unaccounted for
						if ((attendanceSheet.getRow(i) != null) && (attendanceSheet.getRow(i).getCell(todayCol) == null || 
						attendanceSheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK))
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
				for (int i = attendanceSheet.getFirstRowNum() + 1; i <= attendanceSheet.getLastRowNum(); i++) {

					if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue().equals(bunk)) {

						// if today's attendance column does not exist or is empty, the staff member is unaccounted for
						if ((attendanceSheet.getRow(i) != null) && (attendanceSheet.getRow(i).getCell(todayCol) == null || 
								attendanceSheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK)) {

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
										// TODO: clicking this button will use the curfew selected by the radio buttons. is this expected behavior?
										
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
				
				// see if there are any staff that haven't signed in yet
				boolean allBunksEmpty = noUnaccountedStaff();
				
				// if there are still unaccounted staff, confirm that user still wants to exit
				if (!allBunksEmpty) {
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
			public boolean noUnaccountedStaff() {

				// runs through all rows of the spreadsheet and returns false if there is an
				//  unaccounted staff member
				for (int i = attendanceSheet.getFirstRowNum() + 1; i <= attendanceSheet.getLastRowNum(); i++)
					// if today's attendance column does not exist or is empty, the staff member is unaccounted for
					if ((attendanceSheet.getRow(i) != null) && (attendanceSheet.getRow(i).getCell(todayCol) == null || 
					       attendanceSheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK))
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
				
				for (int i = attendanceSheet.getFirstRowNum() + 1; i < attendanceSheet.getLastRowNum() + 1; i++) {
					
					absent = false;
					
					// if today's attendance column does not exist or is empty, the staff member is unaccounted for
					if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(todayCol) == null) {
						attendanceSheet.getRow(i).createCell(todayCol).setCellValue("Absent");
						absent = true;
					} else if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(todayCol).getCellType() == CellType.BLANK) {
						attendanceSheet.getRow(i).getCell(todayCol).setCellValue("Absent");
						absent = true;
					}
					
					// set cell background to red if necessary,
					//  increment "absent" column if it exists
					if (absent) {
						attendanceSheet.getRow(i).getCell(todayCol).setCellStyle(absentStyle);
						
						if (absentCol != -1) {
							if ((attendanceSheet.getRow(i) != null) && attendanceSheet.getRow(i).getCell(absentCol) != null) // the cell exists
								attendanceSheet.getRow(i).getCell(absentCol).setCellValue(attendanceSheet.getRow(i).getCell(absentCol).getNumericCellValue() + 1);
							else // the cell does not exist
								attendanceSheet.getRow(i).createCell(absentCol).setCellValue(1);
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