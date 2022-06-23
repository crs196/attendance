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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
import javafx.geometry.Point2D;
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
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class GatePane extends GridPane {

	private Stage stage;

	private LocalDateTime nightOutCurfew, extendedNightOffCurfew, dayOffDay1Curfew, dayOffDay2Curfew, rolloverTime;
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
	private int left, returned, stillOut, visitors;
	// used to track what row to write the next staff member on
	private int staffRowNum;
	
	// used to track whether the "sign in/out" should also write to the excel file
	private boolean autosave;

	public GatePane(Stage s, Ini set) {
		super();
		
		// no people have left or returned just yet
		left = 0;
		returned = 0;
		stillOut = 0;
		visitors = 0;

		// get config settings
		settings = set;
		
		autosave = settings.get("settings", "autosave", Boolean.class);
		
		// set stage
		stage = s;
		
		// set instance variables
		nightOutCurfew = curfewTime(settings.get("times", "nightOutCurfew"));
		extendedNightOffCurfew = curfewTime(settings.get("times", "extendedNightOffCurfew"));
		rolloverTime = curfewTime(settings.get("times", "rolloverTime"));
		// set both day off curfews
		dayOffDay1Curfew = curfewTime(settings.get("times", "dayOffCurfew"));
		// if the day off curfew is today or tomorrow
		if(dayOffDay1Curfew.toLocalDate().isEqual(LocalDate.now()) || dayOffDay1Curfew.plusDays(1).toLocalDate().isEqual(LocalDate.now())) {
			dayOffDay2Curfew = dayOffDay1Curfew; // it's actually the curfew for people on the 2nd day of their day off
			dayOffDay1Curfew = dayOffDay1Curfew.plusDays(1); // and the curfew for people on the 1st day of their day off is the next day
		} else { // if the day off curfew is after tomorrow, it's the curfew for the people on the 1st day of their day off
			dayOffDay2Curfew = dayOffDay1Curfew.minusDays(1); // and the curfew for people on the 2nd day of their day off is the day before
		}
		
		attendanceFile = new File(settings.get("filePaths", "attendanceFilePath"));
		
		// get info text
		infoText = "";
		getInfoFileContents();
		
		//initialize staff list
		staffList = new HashMap<String, StaffMember>();

		// create local workbook from attendanceFile, only continue if workbook creation is acceptable
		try (FileInputStream afis = new FileInputStream(attendanceFile)) {
			workbook = new XSSFWorkbook(afis);
		} catch (EmptyFileException | IOException e) {			
			Alert fileNotAccessible = new Alert(AlertType.ERROR, "Unable to access \"" + attendanceFile.getName()
					+ "\"\nPlease choose a different file.");
			fileNotAccessible.setTitle("Attendance File Not Accessible");
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());
			fileNotAccessible.initOwner(stage);
			fileNotAccessible.showAndWait();
			
			Platform.exit();
		}
		
		// set columns in attendanceSheet
		bunkCol = 0;	// column A: bunk/position
		nameCol = 1;	// column B: name
		idCol = 2;		// column C: ID
		
		timeOutCol = 3;	// column D: time out
		timeInCol = 4;	// column E: time in
		
		// set columns in keySheet
		keyBunkCol = 0;	// column A: bunk/position
		keyNameCol = 1;	// column B: name
		keyIDCol = 2;	// column C: ID
		
		ontimeCol = 3;	// column D: on time
		lateCol = 4;	// column E: late
		absentCol = 5;	// column F: absent
		
		keySheet = workbook.getSheet("Key"); // get key sheet
		if (keySheet == null) keySheet = workbook.getSheetAt(0); // if there is no sheet named "Key", the key sheet is the first sheet
		
		attendanceSheet = workbook.getSheet(LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))); // get sheet with today's date
		if (attendanceSheet == null) { // no sheet with today's date exists
			int templateIndex = workbook.getSheetIndex("Daily Attendance Template"); // get index for template sheet
			// create copy of template with today's date as the name
			if (templateIndex != -1) // sheet named daily attendance template exists
				attendanceSheet = workbook.cloneSheet(templateIndex, LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy")));
			else // the template is the last sheet in the workbook
				attendanceSheet = workbook.cloneSheet(workbook.getNumberOfSheets() - 1, LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy")));
			
			// sets the initial value of staffRowNum to be the first row
			//  that doesn't have a staff member already written into it
			while (attendanceSheet.getRow(staffRowNum + 1) != null && attendanceSheet.getRow(++staffRowNum).getCell(idCol) != null);
			
			// read from yesterday's sheet if it exists and start off today's sheet with that data
			readFromYesterdaySheet();
		} else { // sheet with today's date already exists
			// so read the data written to it
			readFromTodaySheet();
			
			// sets the initial value of staffRowNum to be the first row
			//  that doesn't have a staff member already written into it
			while (attendanceSheet.getRow(staffRowNum + 1) != null && attendanceSheet.getRow(++staffRowNum).getCell(idCol) != null);
		}
		
		workbook.setSheetOrder(attendanceSheet.getSheetName(), 1); // move today's sheet to second position in workbook (key sheet is first)
		
		getMasterStaffList(); // get list of staff from keySheet
		
		initializeAttendanceSheet(); // get locations of columns from header row
		
		setup();
	}
	
	// takes the string entered as curfew time and converts it to the date and time of curfew
	private LocalDateTime curfewTime(String curfewString) {
		
		if (curfewString == null)
			return null;
		
		int hour = 0, minute = 0; // variables to hold the input hour and minute
		
		// a regex and matcher that matches 12-hr time with optional leading zero, optional separator
		//  mandatory meridem indicators (but optionally separated, case-insensitive, and with optional m/M)
		Pattern twelveHrTime = Pattern.compile("^(1[0-2]|0?[1-9]):?([0-5]\\d)?\\s*([AaPp])[Mm]?$");
		Matcher twelveHrMatcher = twelveHrTime.matcher(curfewString);
		
		// a regex and matcher that matches 24-hr time with optional leading zero and optional separator
		Pattern twentyFourHrTime = Pattern.compile("^(2[0-3]|1\\d|0?\\d):?([0-5]\\d)?$");
		Matcher twentyFourHrMatcher = twentyFourHrTime.matcher(curfewString);
		
		if (twelveHrMatcher.find()) { // if the curfew string matches 12-hr time
			
			hour = Integer.parseInt(twelveHrMatcher.group(1)); // set hour variable to input hour
			if (twelveHrMatcher.group(3).toLowerCase().equals("p") && hour != 12)
				hour += 12; // if the time is PM, add 12 to convert to 24-hour time
			else if (twelveHrMatcher.group(3).toLowerCase().equals("a") && hour == 12)
				hour -= 12;
			
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
			invalidCurfewTime.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());
			invalidCurfewTime.showAndWait();
			
			Platform.exit();
			
		}
		
		LocalTime curfew = LocalTime.of(hour, minute);
		
		// if curfew is after the current time, curfew is today
		if (curfew.isAfter(LocalTime.now()))
			return LocalDateTime.of(LocalDate.now(), curfew); // return LocalDateTime object with today's date and entered time
		else // otherwise, curfew is tomorrow (read: after midnight)
			return LocalDateTime.of(LocalDate.now().plusDays(1), curfew); // return LocalDateTime object with tomorrow's date and entered time
	}

	// reads information from file to display if the info button is clicked
	private void getInfoFileContents() {
			
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
			fileNotAccessible.getDialogPane().getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());
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
	
	// reads from the sheet with yesterday's date to start off today's sheet with anyone who's still pending from there
	private void readFromYesterdaySheet() {
		
		// get yesterday's sheet (if it exists)
		XSSFSheet yesterday = workbook.getSheet(LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("MM-dd-yyyy")));
		
		// only proceed if the sheet exists
		if (yesterday != null) {
			// read through the staff members in the sheet and if any are still pending
			//  (staff member who hasn't signed in, visitor who hasn't signed out)
			//  add their info to the sheet and to the staff list
			for (int i = yesterday.getFirstRowNum() + 1; i < yesterday.getLastRowNum() + 1; i++) {
				String bunk, name, id;
				boolean visitor = false, pending = false;
				int keyRow = 0;
				
				// only get cell values if there's a staff member written there
				if (yesterday.getRow(i) != null && yesterday.getRow(i).getCell(nameCol) != null) {
		
					if (yesterday.getRow(i).getCell(timeOutCol) != null) { // if time out column exists
						if(yesterday.getRow(i).getCell(timeOutCol).getStringCellValue().equalsIgnoreCase("visitor")) {
							visitor = true; // it's a visitor
							pending = true; // they're still on-camp
						} else if (yesterday.getRow(i).getCell(timeInCol) != null) {
							// if time in col exists and is "Day \nOff", "Night \nOut", or "Extended \nNight Off"
							String timeOut = yesterday.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase();
							pending = timeOut.equals("day \noff") || timeOut.equals("extended \nnight off") || timeOut.equals("night \nout"); // they're still off camp
						}
					}
				}
				
				// create a new staff member for today's sheet if this person is pending
				if (pending) {
					
					// get name, bunk, and ID (check whether ID is a string or a number)
					bunk = yesterday.getRow(i).getCell(bunkCol).getStringCellValue();
					name = yesterday.getRow(i).getCell(nameCol).getStringCellValue();
					id = yesterday.getRow(i).getCell(idCol).getCellType() == CellType.STRING 
							? yesterday.getRow(i).getCell(idCol).getStringCellValue()
									: yesterday.getRow(i).getCell(idCol).getNumericCellValue() + "";
					
					// get keyRow of staff member by searching through keyRow for ID
					for (int j = keySheet.getFirstRowNum() + 1; j < keySheet.getLastRowNum() + 1; j++) {
						if (keySheet.getRow(j) != null && keySheet.getRow(j).getCell(keyIDCol) != null) {
							if ((keySheet.getRow(j).getCell(keyIDCol).getCellType() == CellType.STRING && keySheet.getRow(j).getCell(keyIDCol).getStringCellValue().equals(id))
									|| (keySheet.getRow(j).getCell(keyIDCol).getCellType() == CellType.NUMERIC && ((int)keySheet.getRow(j).getCell(keyIDCol).getNumericCellValue() + "").equals(id))) {
								keyRow = j;	
							}
						}
					}
					
					// create staff member and add them to the hashmap
					// get what curfew type the person used when siging out
					CurfewType ct;
					if (visitor) {
						ct = CurfewType.VISITOR;
					} else {
						switch (yesterday.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase()) {
							case "night \nout":
								ct = CurfewType.NORMAL;
								break;
							case "extended \nnight off":
								ct = CurfewType.NIGHT_OFF;
								break;
							case "day \noff":
								ct = CurfewType.DAY_OFF_DAY_2;
								break;
							default:
								ct = CurfewType.NONE;
								break;
						}
					}
					// if person is visitor, isn't out. if person isn't visitor, is out
					// if person is visitor, is in. if person isn't visitor, is out
					StaffMember sm = new StaffMember(bunk, name, id, !visitor, visitor, ct, keyRow, i);
					staffList.put(id, sm);
					
					// write this person to today's attendance sheet in order to track them today
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
					
					// set time out column to either visitor or the time staff member signed out yesterday
					// set time in column to either time visitor signed in yesterday or curfew (and style)
					String timeOut = "", timeIn = "";
					if (visitor) {
						timeOut = "Visitor";
						timeIn = "Yesterday (" + yesterday.getRow(i).getCell(timeInCol).getStringCellValue() + ")"; // get time visitor signed in
					} else {
						timeOut = "Yesterday (" + yesterday.getRow(i).getCell(timeOutCol).getStringCellValue() + ")"; // get time staff member signed out
						timeIn = yesterday.getRow(i).getCell(timeInCol).getStringCellValue(); // get curfew staff member used
					}
					// set cell values
					newRow.createCell(timeOutCol).setCellValue(timeOut);
					newRow.createCell(timeInCol).setCellValue(timeIn);
					// set cell styles
					if (visitor) 
						newRow.getCell(timeOutCol).setCellStyle(absent);
					else
						newRow.getCell(timeInCol).setCellStyle(absent);
					
					
					// increment counts of people who have left camp and are still out of camp
					if (!visitor) {
						left++;
						stillOut++;
					} else {
						visitors++;
					}
					// write all summary stats to sheet
					attendanceSheet.getRow(5).getCell(8).setCellValue(left);
					attendanceSheet.getRow(6).getCell(8).setCellValue(returned);
					attendanceSheet.getRow(7).getCell(8).setCellValue(stillOut);
					attendanceSheet.getRow(9).getCell(8).setCellValue(visitors);
				}
			}
		}
	}
	
	// reads from the sheet with today's date to make sure that the program uses the data written there
	private void readFromTodaySheet() {
		// start by getting the summary statistic values
		left     = (int) attendanceSheet.getRow(5).getCell(8).getNumericCellValue();
		returned = (int) attendanceSheet.getRow(6).getCell(8).getNumericCellValue();
		stillOut = (int) attendanceSheet.getRow(7).getCell(8).getNumericCellValue();
		visitors = (int) attendanceSheet.getRow(9).getCell(8).getNumericCellValue();
		
		// then, read through the staff members and create any written there with the same data
		for (int i = attendanceSheet.getFirstRowNum() + 1; i < attendanceSheet.getLastRowNum() + 1; i++) {
			String bunk, name, id;
			boolean out = false, in = false;
			int keyRow = 0;
			
			// only get cell values if there's a staff member written there
			if (attendanceSheet.getRow(i) != null && attendanceSheet.getRow(i).getCell(nameCol) != null) {
				
				// get name, bunk, and ID (check whether ID is a string or a number)
				bunk = attendanceSheet.getRow(i).getCell(bunkCol).getStringCellValue();
				name = attendanceSheet.getRow(i).getCell(nameCol).getStringCellValue();
				id = attendanceSheet.getRow(i).getCell(idCol).getCellType() == CellType.STRING 
						? attendanceSheet.getRow(i).getCell(idCol).getStringCellValue()
								: attendanceSheet.getRow(i).getCell(idCol).getNumericCellValue() + "";
						
				if (attendanceSheet.getRow(i).getCell(timeOutCol) != null) { // if time out column exists
					if(attendanceSheet.getRow(i).getCell(timeOutCol).getStringCellValue().equalsIgnoreCase("visitor")) { // if it's visitor
						in = true; // they're a visitor and have signed in
					} else { // if it isn't visitor
						out = true; // the staff member signed out
						
						// check to see if staff member has signed in:
						//  time in column exists and is a time
						if (attendanceSheet.getRow(i).getCell(timeInCol) != null) {
							Pattern time = Pattern.compile("^(1[0-2]|0?[1-9]):([0-5]\\d) [AP]M$");
							in = time.matcher(attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue()).find();
						}
					}
				}
				
				// get keyRow of staff member by searching through keyRow for ID
				for (int j = keySheet.getFirstRowNum() + 1; j < keySheet.getLastRowNum() + 1; j++) {
					if (keySheet.getRow(j) != null && keySheet.getRow(j).getCell(keyIDCol) != null) {
						if ((keySheet.getRow(j).getCell(keyIDCol).getCellType() == CellType.STRING && keySheet.getRow(j).getCell(keyIDCol).getStringCellValue().equals(id))
								|| (keySheet.getRow(j).getCell(keyIDCol).getCellType() == CellType.NUMERIC && (keySheet.getRow(j).getCell(keyIDCol).getNumericCellValue() + "").equals(id))) {
							keyRow = j;	
						}	
					}
				}
				
				// create staff member and add them to the hashmap
				CurfewType ct;
				if (attendanceSheet.getRow(i).getCell(timeOutCol).getStringCellValue().equalsIgnoreCase("visitor")) {
					ct = CurfewType.VISITOR;
				} else {
					switch (attendanceSheet.getRow(i).getCell(timeInCol).getStringCellValue().toLowerCase()) {
						case "night \nout":
							ct = CurfewType.NORMAL;
							break;
						case "extended \nnight off":
							ct = CurfewType.NIGHT_OFF;
							break;
						case "day \noff":
							// check whether this is the first or second day of a day off by the cell color
							XSSFColor cellColor = attendanceSheet.getRow(i).getCell(timeInCol).getCellStyle().getFillForegroundXSSFColor();
							XSSFColor absentColor = new XSSFColor(Color.decode(settings.get("sheetFormat", "absentColor", String.class)) , new DefaultIndexedColorMap());
							if (cellColor.equals(absentColor)) {
								ct = CurfewType.DAY_OFF_DAY_2;
							} else {
								ct = CurfewType.DAY_OFF_DAY_1;
							}
							break;
						default:
							ct = CurfewType.NONE;
							break;
					}
				}
				staffList.put(id, new StaffMember(bunk, name, id, out, in, ct, keyRow, i));
			}
		}
	}
	
	// reads from the "key" sheet on the spreadsheet to get a list of every possible staff member
	//  that could sign out/in in this session and their information
	private void getMasterStaffList() {
		
		// then, loop through the sheet and collect all the data
		for (int i = keySheet.getFirstRowNum() + 1; i < keySheet.getLastRowNum() + 1; i++) {
			String bunk, name, id;
			
			// only get cell values if there are cell values
			if (keySheet.getRow(i) != null) {
			
				// get name, bunk, and ID (check whether ID is a string or a number)
				bunk = keySheet.getRow(i).getCell(keyBunkCol) != null ? keySheet.getRow(i).getCell(keyBunkCol).getStringCellValue() : "";
				name = keySheet.getRow(i).getCell(keyNameCol) != null ? keySheet.getRow(i).getCell(keyNameCol).getStringCellValue() : "";
				id   = keySheet.getRow(i).getCell(keyIDCol) != null   ? (keySheet.getRow(i).getCell(keyIDCol).getCellType() == CellType.STRING 
																			? keySheet.getRow(i).getCell(keyIDCol).getStringCellValue()
																				: (int)keySheet.getRow(i).getCell(keyIDCol).getNumericCellValue() + "") : "";
				
				// put staff member into the hashmap unless it already exists
				if (staffList.get(id) == null) staffList.put(id, new StaffMember(bunk, name, id, false, false, CurfewType.NONE, i));
			}
		}
	}
	
	// initializes column trackers, writes curfew times to respective cells
	private void initializeAttendanceSheet() {
		
		// write night out curfew time to row 2 in today's column if different than what's already there
		if (attendanceSheet.getRow(1).getCell(8) == null)
			attendanceSheet.getRow(1).createCell(8).setCellValue(nightOutCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(attendanceSheet.getRow(1).getCell(8).getStringCellValue().equals(nightOutCurfew.format(DateTimeFormatter.ofPattern("h:mm a")))))
			attendanceSheet.getRow(1).getCell(8).setCellValue(nightOutCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		
		// write extended night off curfew time to row 3 in today's column if different than what's already there
		if (attendanceSheet.getRow(2).getCell(8) == null)
			attendanceSheet.getRow(2).createCell(8).setCellValue(extendedNightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(attendanceSheet.getRow(2).getCell(8).getStringCellValue().equals(extendedNightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")))))
			attendanceSheet.getRow(2).getCell(8).setCellValue(extendedNightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		
		// write day off curfew time to row 4 in today's column if different than what's already there
		if (attendanceSheet.getRow(3).getCell(8) == null)
			attendanceSheet.getRow(3).createCell(8).setCellValue(dayOffDay1Curfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(attendanceSheet.getRow(3).getCell(8).getStringCellValue().equals(dayOffDay1Curfew.format(DateTimeFormatter.ofPattern("h:mm a")))))
			attendanceSheet.getRow(3).getCell(8).setCellValue(dayOffDay1Curfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		
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
		Label title = new Label("Sign In/Out");
		title.setId("header");
		GatePane.setHalignment(title, HPos.CENTER);
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
		Label normalCurfewLabel = new Label("Night Out Curfew:");
		Label normalCurfewTimeLabel = new Label();
		normalCurfewTimeLabel.setText(nightOutCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		HBox normalCurfewBox = new HBox(this.getHgap());
		normalCurfewLabel.setMinWidth(USE_PREF_SIZE);
		normalCurfewTimeLabel.setMinWidth(USE_PREF_SIZE);
		normalCurfewBox.setAlignment(Pos.CENTER);
		normalCurfewBox.getChildren().addAll(normalCurfewLabel, normalCurfewTimeLabel);
		
		// extended night off
		Label nightOffCurfewLabel = new Label("Extended Night Off Curfew: ");
		Label nightOffCurfewTimeLabel = new Label();
		nightOffCurfewTimeLabel.setText(extendedNightOffCurfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		HBox nightOffCurfewBox = new HBox(this.getHgap());
		nightOffCurfewLabel.setMinWidth(USE_PREF_SIZE);
		nightOffCurfewTimeLabel.setMinWidth(USE_PREF_SIZE);
		nightOffCurfewBox.setAlignment(Pos.CENTER);
		nightOffCurfewBox.getChildren().addAll(nightOffCurfewLabel, nightOffCurfewTimeLabel);
		
		// day off
		Label dayOffCurfewLabel = new Label("Day Off Curfew: ");
		Label dayOffCurfewTimeLabel = new Label();
		dayOffCurfewTimeLabel.setText(dayOffDay1Curfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		HBox dayOffCurfewBox = new HBox(this.getHgap());
		dayOffCurfewLabel.setMinWidth(USE_PREF_SIZE);
		dayOffCurfewTimeLabel.setMinWidth(USE_PREF_SIZE);
		dayOffCurfewBox.setAlignment(Pos.CENTER);
		dayOffCurfewBox.getChildren().addAll(dayOffCurfewLabel, dayOffCurfewTimeLabel);
		
		
		// add clocks to VBox to hold them
		VBox clockBox = new VBox(this.getVgap() * 0.5);
		clockBox.getChildren().addAll(currentTimeBox, normalCurfewBox, nightOffCurfewBox, dayOffCurfewBox);
		
		// buttons
		
		VBox listButtons = new VBox(this.getVgap() * 1.5);
		
		// button to show list of signed-out staff
		Button offCampList = new Button("Show Off-Camp Staff");
		offCampList.setMinWidth(USE_PREF_SIZE);
		HBox.setHgrow(offCampList, Priority.ALWAYS);
		offCampList.setMaxWidth(Double.MAX_VALUE);
		offCampList.setId("green");
		
		// button to show list of staff who've not yet signed out
		Button onCampList = new Button("Show On-Camp Staff");
		onCampList.setMinWidth(USE_PREF_SIZE);
		HBox.setHgrow(onCampList, Priority.ALWAYS);
		onCampList.setMaxWidth(Double.MAX_VALUE);
		onCampList.setId("green");
		
		listButtons.getChildren().addAll(onCampList, offCampList);

		VBox leftCol = new VBox(this.getVgap());
		leftCol.getChildren().addAll(clockBox, listButtons);
		this.add(leftCol, 0, 1, 1, 2);

		/* right column (sign-in box, confirmation area) */

		// sign-in instructions, entry point, and confirm button
		Label scanLabel = new Label("Enter a name or ID and select curfew:");
		scanLabel.setMinWidth(USE_PREF_SIZE);
		
		TextField idField = new TextField();
		HBox.setHgrow(idField, Priority.ALWAYS);
		
		Button signIn = new Button("Sign In/Out");
		signIn.setDefaultButton(true);
		HBox.setHgrow(signIn, Priority.ALWAYS);
		signIn.setMaxWidth(Double.MAX_VALUE);
		signIn.setId("green");
		HBox signInBox = new HBox(this.getHgap());
		signInBox.getChildren().addAll(idField, signIn);
		
		// curfew selection radio buttons
		ToggleGroup curfewTimeSelection = new ToggleGroup();
		RadioButton normal = new RadioButton("Night \nOut");
		RadioButton nightOff = new RadioButton("Extended \nNight Off");
		RadioButton dayOff = new RadioButton("Day \nOff");
		RadioButton visitor = new RadioButton("Visitor");
		normal.setToggleGroup(curfewTimeSelection);
		nightOff.setToggleGroup(curfewTimeSelection);
		dayOff.setToggleGroup(curfewTimeSelection);
		visitor.setToggleGroup(curfewTimeSelection);
		
		HBox timeSelectionBox = new HBox(this.getHgap());
		timeSelectionBox.setAlignment(Pos.CENTER_RIGHT);
		timeSelectionBox.getChildren().addAll(normal, nightOff, dayOff, visitor);
		
		VBox idBox = new VBox(this.getVgap());
		idBox.getChildren().addAll(scanLabel, signInBox, timeSelectionBox);

		// confirmation area
		TextArea confirmation = new TextArea();
		confirmation.setEditable(false);
		confirmation.setWrapText(true);
		confirmation.setPrefWidth(scanLabel.getWidth());
		confirmation.setPrefRowCount(3);
		idBox.getChildren().add(confirmation);
		this.add(idBox, 1, 1);
		
		Button save = new Button("Save");
		Button saveAndRestart = new Button("Save and Restart");
		Button saveAndExit = new Button("Save and Exit");

		save.setMinWidth(USE_PREF_SIZE);
		saveAndRestart.setMinWidth(USE_PREF_SIZE);
		saveAndExit.setMinWidth(USE_PREF_SIZE);

		HBox.setHgrow(save, Priority.ALWAYS);
		HBox.setHgrow(saveAndRestart, Priority.ALWAYS);
		HBox.setHgrow(saveAndExit, Priority.ALWAYS);

		save.setMaxWidth(Double.MAX_VALUE);
		saveAndRestart.setMaxWidth(Double.MAX_VALUE);
		saveAndExit.setMaxWidth(Double.MAX_VALUE);
		
		GridPane infoSpacing = new GridPane();
		HBox.setHgrow(infoSpacing, Priority.ALWAYS);
		
		// add information button (will pop up credits and instructions)
		Button info = new Button("i");
		info.getStyleClass().add("info");
		info.setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
		
		HBox saveAndInfoBox = new HBox(this.getHgap());
		saveAndInfoBox.getChildren().addAll(saveAndExit, saveAndRestart, save, infoSpacing, info);
		this.add(saveAndInfoBox, 0, 2, 2, 1);
		
		// stage and scene for offCampList
		Stage offCampStage = new Stage();
		Scene offCampScene = new Scene(new Label("Something's gone wrong"));
		
		// stage and scene for onCampList
		Stage onCampStage = new Stage();
		Scene onCampScene = new Scene(new Label("Something's gone wrong"));
		
		// set info button behavior (show credits, brief explanation of what to do)
		info.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Alert infoDialog = new Alert(AlertType.NONE, infoText, ButtonType.CLOSE);
				infoDialog.setTitle("Credits and Instructions — Sign-in");
				infoDialog.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());
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
				if (curfewTimeSelection.getSelectedToggle() == null) { // if no curfew type radio button was selected
					// alert the user of such
					Alert noCurfewSelected = new Alert(AlertType.WARNING, "No curfew type selected. Please select a curfew type to proceed.");
					noCurfewSelected.setTitle("Curfew Type Not Selected");
					noCurfewSelected.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());
					noCurfewSelected.initOwner(stage);
					noCurfewSelected.showAndWait();
					return; // don't proceed with signing staff member in/out
				}
				
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
							// check if staff member is visitor or not and send them to the proper handling method
							if (entered.getCurfewType() == CurfewType.VISITOR) {
								signVisitorIn(entered); // they're a visitor so sign them in
								entered.unSignOut(); // un-sign the visitor out (they're back in camp)
								confirmation.setText("Visitor " + entered.getName() + " signed in again");
							} else {
								left--;
								returned--;
								decrementOnTimeCount(entered); // the staff member is leaving again so they're no longer on time
								signOutAndWriteCurfew(entered); // they're a staff member so sign them out
								entered.unSignIn(); // un-sign the staff member in (they're out of camp again)
								confirmation.setText(entered.getName() + " signed out again");
							}
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
						
						// if either additional list is showing, update it
						if (offCampStage.isShowing())
							offCampList.fire();
						if (onCampStage.isShowing())
							onCampList.fire();

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
				switch (sm.getCurfewType()) {
					case NORMAL:
						curfewUsed = nightOutCurfew;
						break;
					case NIGHT_OFF:
						curfewUsed = extendedNightOffCurfew;
						break;
					case DAY_OFF_DAY_1:
						curfewUsed = dayOffDay1Curfew;
						break;
					case DAY_OFF_DAY_2:
						curfewUsed = dayOffDay2Curfew;
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
				// write all summary stats to sheet
				attendanceSheet.getRow(5).getCell(8).setCellValue(left);
				attendanceSheet.getRow(6).getCell(8).setCellValue(returned);
				attendanceSheet.getRow(7).getCell(8).setCellValue(stillOut);
				attendanceSheet.getRow(9).getCell(8).setCellValue(visitors);
			}
			
			// given a visitor who needs to sign out
			//  updates today's sheet to write the time that the visitor left camp
			public void signVisitorOut(StaffMember sm) {
				
				// set timeOutCol of this staff member to current time
				attendanceSheet.getRow(sm.getTodayRow()).getCell(timeOutCol).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")) + " (visitor)");
				// clear styling of this cell
				attendanceSheet.getRow(sm.getTodayRow()).getCell(timeOutCol).setCellStyle(workbook.createCellStyle());
				
				// decrement number of visitors on camp
				visitors--;
				// write all summary stats to sheet
				attendanceSheet.getRow(5).getCell(8).setCellValue(left);
				attendanceSheet.getRow(6).getCell(8).setCellValue(returned);
				attendanceSheet.getRow(7).getCell(8).setCellValue(stillOut);
				attendanceSheet.getRow(9).getCell(8).setCellValue(visitors);
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
				sm.setCurfewType(CurfewType.VISITOR); // set that this staff member is a visitor
				
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
				newRow.createCell(timeInCol).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")));
				
				// increment number of visitors on camp
				visitors++;
				// write all summary stats to sheet
				attendanceSheet.getRow(5).getCell(8).setCellValue(left);
				attendanceSheet.getRow(6).getCell(8).setCellValue(returned);
				attendanceSheet.getRow(7).getCell(8).setCellValue(stillOut);
				attendanceSheet.getRow(9).getCell(8).setCellValue(visitors);
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
				// set the curfew type of the staff member unless it's already set
				if (sm.getCurfewType() == CurfewType.NONE) {
					switch (((RadioButton)curfewTimeSelection.getSelectedToggle()).getText().toLowerCase()) {
						case "night \nout":
							sm.setCurfewType(CurfewType.NORMAL);
							break;
						case "extended \nnight off":
							sm.setCurfewType(CurfewType.NIGHT_OFF);
							break;
						case "day \noff":
							sm.setCurfewType(CurfewType.DAY_OFF_DAY_1);
							break;
					}
				}
				
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
				newRow.createCell(timeInCol).setCellValue(sm.getCurfewType().writeByType());
				newRow.getCell(timeInCol).setCellStyle(sm.getCurfewType() == CurfewType.DAY_OFF_DAY_1 ? dayOffStyle : absent);
				
				// increment counts of people who have left camp and are still out of camp
				left++;
				stillOut++;
				// write all summary stats to sheet
				attendanceSheet.getRow(5).getCell(8).setCellValue(left);
				attendanceSheet.getRow(6).getCell(8).setCellValue(returned);
				attendanceSheet.getRow(7).getCell(8).setCellValue(stillOut);
				attendanceSheet.getRow(9).getCell(8).setCellValue(visitors);
			}
			
			// given a staff member, decrements the number in their on-time column
			public void decrementOnTimeCount(StaffMember sm) {
				int onTimeCount = 0;
				if (keySheet.getRow(sm.getKeyRow()).getCell(ontimeCol) != null
						&& (onTimeCount = (int) keySheet.getRow(sm.getKeyRow()).getCell(ontimeCol).getNumericCellValue()) > 0) {
					keySheet.getRow(sm.getKeyRow()).getCell(ontimeCol).setCellValue(onTimeCount - 1);
				}
			}
		});



		// event handlers for left column buttons

		// pulls up list of staff members who have yet to sign in in this session
		offCampList.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {

				// if there are no staff left unaccounted, print a message saying so and leave this handle method
				if (noUnaccountedStaff()) {
					if (!offCampStage.isShowing())
						confirmation.setText("There's currently no one off-camp to sign in");
					else
						offCampStage.close();
					return;
				}
				
				// if we get here, there are still unaccounted-for staff, so find and list them
				
				GridPane offCampPane = new GridPane();
				// set up grid layout and sizing
				offCampPane.setHgap(15);
				offCampPane.setVgap(20);
				offCampPane.setAlignment(Pos.CENTER);
				offCampPane.setPadding(new Insets(20));
				ColumnConstraints column1 = new ColumnConstraints();
				column1.setPercentWidth(50);
				ColumnConstraints column2 = new ColumnConstraints();
				column2.setPercentWidth(50);
				ColumnConstraints column3 = new ColumnConstraints();
				column3.setPercentWidth(50);
				offCampPane.getColumnConstraints().addAll(column1, column2, column3);

				ScrollPane scrollPane = new ScrollPane(offCampPane);
				scrollPane.setMinWidth(stage.getWidth() * 0.75);
				scrollPane.setMaxHeight(stage.getHeight());

				List<String> listBunks = countBunks();
				int nextRow = 0, nextCol = 0;

				for (int i = 0; i < listBunks.size(); i++) {
					if (!bunkEmpty(listBunks.get(i))) {
						offCampPane.add(getStaffFromBunk(listBunks.get(i)), nextRow, nextCol);

						if (++nextRow > 2) {
							nextCol++;
							nextRow = 0;
						}
					}
				}


				// set up scene
				offCampScene.setRoot(scrollPane);
				offCampScene.getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());

				// only need to do these things if the stage isn't currently on screen
				if (!offCampStage.isShowing()) {
					// set up stage
					offCampStage.setScene(offCampScene);
					offCampStage.setMinWidth(scrollPane.getMinWidth());
					offCampStage.setMaxHeight(scrollPane.getMaxHeight());
					offCampStage.setTitle("Off-Camp Staff");
					offCampStage.getIcons().add(new Image(settings.get("filePaths", "iconPath", String.class)));
					offCampStage.centerOnScreen();
					offCampStage.show();
				} else {
					offCampStage.toFront();
				}
			}
			
			// returns whether or not there are still unaccounted-for staff
			// in order to be "accounted for," the staff member/visitor must fall under one of the following categories:
			// - never signed out and never signed in
			// - signed out and signed back in
			// - signed out for a day off and not signed back in
			public boolean noUnaccountedStaff() {
				
				// if no staff have been added yet, there are also no unaccounted staff
				if (staffRowNum == attendanceSheet.getFirstRowNum() + 1) return true;

				for (StaffMember sm : staffList.values())
					if (!sm.isSignedOut() && sm.isSignedIn()) // a visitor who's signed in but not out
						return false;
					else if (sm.isSignedOut() && !sm.isSignedIn()) // staff member's signed out but not in. only ok if on a day off
						// is on a day off if their time in column says so
						if (!attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).getStringCellValue().equalsIgnoreCase("day off"))
							return false;

				return true;
			}

			// counts the number of unique bunks in workbook and returns the list of unique bunks
			public List<String> countBunks() {

				List<String> uniqueBunks = new ArrayList<String>();

				// loop through all rows of the sheet, starting at the first row (so ignoring the header rows)
				//  and look at the "bunk" column to count how many unique bunks there are
				for (StaffMember sm : staffList.values()) {
					// if the current bunk is new, add it to the list of unique bunks
					if (!uniqueBunks.contains(sm.getBunk()))
						uniqueBunks.add(sm.getBunk());
				}

				return uniqueBunks;
			}

			// given a bunk name, returns whether or not there are any unaccounted staff remaining
			//  in that bunk
			public boolean bunkEmpty(String bunk) {

				for (StaffMember sm : staffList.values()) {
					if (sm.getBunk().equals(bunk)) {
						// if staff member is unaccounted-for, return false
						if (!sm.isSignedOut() && sm.isSignedIn()) { // a visitor who's signed in but not out
							return false;
						} else if (sm.isSignedOut() && !sm.isSignedIn()) {// staff member's signed out but not in. only ok if on a day off
							// is on a day off if their time in column says so
							if (!attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).getStringCellValue().equalsIgnoreCase("day off")) {
								return false;
							}
						}
					}
				}

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
				for (StaffMember sm : staffList.values()) {

					if (sm.getBunk().equals(bunk)) {

						if ((!sm.isSignedOut() && sm.isSignedIn()) 
								|| ((sm.isSignedOut() && !sm.isSignedIn()) 
										&& (!attendanceSheet.getRow(sm.getTodayRow()).getCell(timeInCol).getStringCellValue().equalsIgnoreCase("day off")))) {

							Button staffMember = new Button(sm.getName());
							staffMember.setId("list-button");
							staffMember.setMinWidth(USE_PREF_SIZE);
							HBox staffNameBox = new HBox(15);
							staffNameBox.setAlignment(Pos.CENTER);
							staffNameBox.getChildren().add(staffMember);
							bunkBox.getChildren().add(staffNameBox);

							// when a staff member is clicked, open a popup window to allow user to sign them in normally
							staffMember.setOnAction(new EventHandler<ActionEvent>() {
								
								@Override
								public void handle(ActionEvent event) {
									// create popup and button, add button to popup
									Popup popup = new Popup();
									popup.setAutoHide(true);
									Button popupSignIn = new Button("Sign In");
									VBox popupButtons = new VBox(8, popupSignIn);
									popupButtons.setPadding(new Insets(4, 8, 8, 8));
									popup.getContent().add(popupButtons);
									
									// locate popup on screen
									Point2D point = staffMember.localToScreen(0, 0); // get location of button in screen space
									popup.show(offCampStage);
									popup.setY(point.getY() + staffMember.getHeight()); // y location is just below name
									popup.setX(point.getX()  + (staffMember.getWidth() - popup.getWidth()) / 2); // center popup below name
									
									// set button action
									popupSignIn.setOnAction(new EventHandler<ActionEvent>() {

										@Override
										public void handle(ActionEvent arg0) {
											
											// sign staff member in by writing the staff member's name into the entry box and firing the sign-in button	
											idField.setText(staffMember.getText());
											signIn.fire();
											popup.hide(); // hide the button
		
											// refresh both additional lists
											if (onCampStage.isShowing())
												onCampList.fire();
											offCampList.fire();
											
										}	
									});
								}
							});
						}
					}

				}

				bunkBox.getChildren().addAll(new HBox(), new HBox()); // empty HBoxes for spacing

				return bunkBox;
			}
		});
		
		// pulls up list of staff members who have yet to sign out in this session
		onCampList.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {

				// if there are no staff left unaccounted, print a message saying so and leave this handle method
				if (allStaffOut()) {
					if (!onCampStage.isShowing())
						confirmation.setText("There's currently no one on-camp to sign out");
					else	
						onCampStage.close();
					return;
				}
				
				// if we get here, there are still staff who've not signed out, so find and list them
				
				GridPane onCampPane = new GridPane();
				// set up grid layout and sizing
				onCampPane.setHgap(15);
				onCampPane.setVgap(20);
				onCampPane.setAlignment(Pos.CENTER);
				onCampPane.setPadding(new Insets(20));
				ColumnConstraints column1 = new ColumnConstraints();
				column1.setPercentWidth(50);
				ColumnConstraints column2 = new ColumnConstraints();
				column2.setPercentWidth(50);
				ColumnConstraints column3 = new ColumnConstraints();
				column3.setPercentWidth(50);
				onCampPane.getColumnConstraints().addAll(column1, column2, column3);

				ScrollPane scrollPane = new ScrollPane(onCampPane);
				scrollPane.setMinWidth(stage.getWidth() * 0.75);
				scrollPane.setMaxHeight(stage.getHeight());

				List<String> listBunks = countBunks();
				int nextRow = 0, nextCol = 0;

				for (int i = 0; i < listBunks.size(); i++) {
					if (!bunkNotEmpty(listBunks.get(i))) {
						onCampPane.add(getStaffFromBunk(listBunks.get(i)), nextRow, nextCol);

						if (++nextRow > 2) {
							nextCol++;
							nextRow = 0;
						}
					}
				}


				// set up scene
				onCampScene.setRoot(scrollPane);
				onCampScene.getStylesheets().add(Attendance.class.getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());

				// only need to do these things if the stage isn't currently on screen
				if (!onCampStage.isShowing()) {
					// set up stage
					onCampStage.setScene(onCampScene);
					onCampStage.setMinWidth(scrollPane.getMinWidth());
					onCampStage.setMaxHeight(scrollPane.getMaxHeight());
					onCampStage.setTitle("On-Camp Staff");
					onCampStage.getIcons().add(new Image(settings.get("filePaths", "iconPath", String.class)));
					onCampStage.centerOnScreen();
					onCampStage.show();
				} else {
					onCampStage.toFront();
				}
			}
			
			// returns whether or not all staff members have signed out
			// in order to be "signed out," the person must meet one of the following characteristics
			// - not signed out
			// - signed in and not signed out (visitor)
			public boolean allStaffOut() {

				for (StaffMember sm : staffList.values())
					if (!sm.isSignedOut() || (!sm.isSignedOut() && sm.isSignedIn()))
						return false;

				return true;
			}

			// counts the number of unique bunks in workbook and returns the list of unique bunks
			public List<String> countBunks() {

				List<String> uniqueBunks = new ArrayList<String>();

				// loop through all rows of the sheet, starting at the first row (so ignoring the header rows)
				//  and look at the "bunk" column to count how many unique bunks there are
				for (StaffMember sm : staffList.values()) {
					// if the current bunk is new, add it to the list of unique bunks
					if (!uniqueBunks.contains(sm.getBunk()))
						uniqueBunks.add(sm.getBunk());
				}

				return uniqueBunks;
			}

			// given a bunk name, returns whether or not there are any staff remaining to sign out
			//  in that bunk
			public boolean bunkNotEmpty(String bunk) {

				for (StaffMember sm : staffList.values())
					if (sm.getBunk().equals(bunk))
						// if staff member is on camp (not signed out), return false
						if (!sm.isSignedOut() || sm.isSignedIn())
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

				// runs through all staff members and adds unaccounted staff in this bunk to the VBox
				for (StaffMember sm : staffList.values()) {

					if (sm.getBunk().equals(bunk)) {

						if (!sm.isSignedOut() || sm.isSignedIn()) {

							Button staffMember = new Button(sm.getName());
							staffMember.setId("list-button");
							staffMember.setMinWidth(USE_PREF_SIZE);
							HBox staffNameBox = new HBox(15);
							staffNameBox.setAlignment(Pos.CENTER);
							staffNameBox.getChildren().add(staffMember);
							bunkBox.getChildren().add(staffNameBox);

							// when a staff member is clicked, open a popup window to allow user to sign them out
							staffMember.setOnAction(new EventHandler<ActionEvent>() {

								@Override
								public void handle(ActionEvent event) {
									// create popup and button, add button to popup
									Popup popup = new Popup();
									popup.setAutoHide(true);
									
									Button popupLeaving = new Button("Night Out");
									Button popupNightOff = new Button("Extended Night Off");
									Button popupDayOff = new Button("Day Off");
									Button popupVisitor = new Button("Visitor");
									popupLeaving.setMaxWidth(Double.MAX_VALUE);
									popupNightOff.setMaxWidth(Double.MAX_VALUE);
									popupDayOff.setMaxWidth(Double.MAX_VALUE);
									popupVisitor.setMaxWidth(Double.MAX_VALUE);
									
									VBox popupButtons = new VBox(8, popupLeaving, popupNightOff, popupDayOff, popupVisitor);
									popupButtons.setAlignment(Pos.CENTER);
									popupButtons.setPadding(new Insets(4, 8, 8, 8));
									popup.getContent().addAll(popupButtons);
									
									// locate popup on screen
									Point2D point = staffMember.localToScreen(0, 0); // get location of button in screen space
									popup.show(onCampStage);
									popup.setY(point.getY() + staffMember.getHeight()); // y location is just below name
									popup.setX(point.getX()  + (staffMember.getWidth() - popup.getWidth()) / 2); // center popup below name
									
									// handler class to minimize repeated code for all four buttons
									class ButtonHandler implements EventHandler<ActionEvent> {

										RadioButton buttonToToggle;
										
										public ButtonHandler(RadioButton bTT) {
											buttonToToggle = bTT;
										}
										
										@Override
										public void handle(ActionEvent arg0) {
											
											// get currently selected button
											RadioButton selected = (RadioButton) curfewTimeSelection.getSelectedToggle();
											
											// sign staff member in by writing the staff member's name into the entry box and firing the sign-in button	
											idField.setText(staffMember.getText());
											buttonToToggle.setSelected(true); // select the radio button for the relevant curfew
											signIn.fire();
											if (selected != null) 
												selected.setSelected(true); // reselect the previously selected curfew selection radio button
											else
												buttonToToggle.setSelected(false); // if no button to reselect, just deselect
											popup.hide(); // hide the popup list
		
											// refresh both additional lists
											if (offCampStage.isShowing())
												offCampList.fire();
											onCampList.fire();
											
										}
										
									}
									
									// set button event handlers
									popupLeaving.setOnAction(new ButtonHandler(normal)); // select normal curfew
									popupNightOff.setOnAction(new ButtonHandler(nightOff)); // select extended night off curfew
									popupDayOff.setOnAction(new ButtonHandler(dayOff)); // select day off curfew
									popupVisitor.setOnAction(new ButtonHandler(visitor)); // select visitor
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
		
		// this event handler is used for the "save and restart" and "save and exit" buttons as well as the auto-rollover
		//  in order to minimize duplicate code
		class EndSaveHandler implements EventHandler<ActionEvent> {

			private boolean exit, alertPopup;
			
			public EndSaveHandler(boolean e, boolean ap) {
				exit = e;
				alertPopup = ap;
			}
			
			@Override
			public void handle(ActionEvent event) {
				
				// if the popup should be shown and there are still unaccounted staff, confirm that user still wants to exit
				if (alertPopup && !noUnaccountedStaff()) {
					Alert saveAndExitConf = new Alert(AlertType.CONFIRMATION, "There are still staff members that haven't signed in.\nAre you sure you want to proceed?");
					saveAndExitConf.setHeaderText("Save Confirmation");
					saveAndExitConf.setTitle("Save Confirmation");
					saveAndExitConf.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());
					saveAndExitConf.initOwner(saveAndExit.getScene().getWindow());
					saveAndExitConf.showAndWait();
					
					if (saveAndExitConf.getResult() != ButtonType.OK)
						return; // user does not want to save and exit
				}
				
				// all staff are accounted for or user wants to mark unaccounted-for staff as absent
				markUnaccAbsent();
				
				// write data to attendanceFile
				save.fire();
				
				// close all windows
				offCampStage.close();
				onCampStage.close();
				// if we should exit
				if (exit) {
					Platform.exit(); //exit
					System.exit(0);
				} else { // otherwise restart
					stage.close();
					Attendance.createScene(stage);
				}
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
		}
		
		// marks unaccounted-for staff members as absent, writes to file, restarts program
		saveAndRestart.setOnAction(new EndSaveHandler(false, true)); // restart, show popup
		
		// marks unaccounted-for staff members as absent, writes to file, closes program
		saveAndExit.setOnAction(new EndSaveHandler(true, true)); // exit, show popup
		
		// schedule the program to restart at the specified time
		if (rolloverTime != null) {
			new Timer().schedule(
					new TimerTask() {
						@Override
						public void run() {
							Platform.runLater(() -> {
								new EndSaveHandler(false, false).handle(new ActionEvent());; // restart, don't show popup
							});
						}
					}, Date.from(rolloverTime.atZone(ZoneId.systemDefault()).toInstant()));
		}

		/* change stage close behavior */
		stage.setOnCloseRequest(new EventHandler<WindowEvent>() {

			@Override
			public void handle(WindowEvent event) {
				event.consume(); // consume window-close event

				Alert alert = new Alert(AlertType.CONFIRMATION, "Are you sure you want to exit and lose all unsaved data?\n"
						+ "Click \"OK\" to exit and \"Cancel\" to return to sign-in.");
				alert.setTitle("Exit Confirmation");
				alert.getDialogPane().getStylesheets().add(getClass().getResource(settings.get("filePaths", "cssPath", String.class)).toExternalForm());
				alert.initOwner(stage);
				alert.showAndWait();

				if (alert.getResult().getButtonData() == ButtonData.OK_DONE) {
					save.fire();
					Platform.exit();
					System.exit(0);
				}
			}
		});
	}
}