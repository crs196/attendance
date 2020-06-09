package ozeret;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class SignInPane extends GridPane {

	private Stage stage;

	private String ozeretName;
	private LocalDateTime curfew;
	private File attendanceFile;
	private Scene prevScene;

	private XSSFWorkbook workbook;
	private XSSFSheet sheet;

	// used to track which column holds each piece of information
	private int bunkCol, nameCol, idCol, ontimeCol, lateCol, absentCol, todayCol;

	public SignInPane(Stage s) {
		super();
		stage = s;
	}

	// called when InitialPane moves to this scene
	public void setPrevVars(String ozName, LocalDateTime c, File af, Scene ps) {
		ozeretName = ozName;
		curfew = c;
		attendanceFile = af;
		prevScene = ps;

		// create local workbook from attendanceFile
		try (FileInputStream afis = new FileInputStream(attendanceFile)) {
			workbook = new XSSFWorkbook(afis);
		} catch (IOException e) {
			Alert fileNotFound = new Alert(AlertType.ERROR, "Unable to find " + attendanceFile.getName() + "\nPlease choose a different file");
			fileNotFound.setTitle("Attendance File Not Found");
			fileNotFound.getDialogPane().getStylesheets().add(OzeretMain.class.getResource("ozeret.css").toExternalForm());
			fileNotFound.initOwner(stage);
			fileNotFound.showAndWait();
			
			Platform.exit();
		}

		sheet = workbook.getSheetAt(0);
		
		readHeaderRow();
		setup();
	}

	// reads header row of workbook to initialize column trackers,
	//  adds column for today to the end of the sheet and puts in OzeretName to row 2
	private void readHeaderRow() {

		XSSFRow headerRow = sheet.getRow(0);
		boolean idExists = false, ontimeExists = false, lateExists = false, absentExists = false, todayExists = false;
		boolean curfewToday = curfew.toLocalDate().equals(LocalDate.now());

		// run through all cells in header row to assign column trackers
		for (int i = headerRow.getFirstCellNum(); i < headerRow.getLastCellNum(); i++) {

			if ((headerRow.getCell(i) != null) && (headerRow.getCell(i).getCellType() == CellType.STRING)) {

				// check to see if current cell has any of these contents
				switch (headerRow.getCell(i).getStringCellValue().toLowerCase()) {
				case "bunk":
					bunkCol = i;
					break;
				case "name":
					nameCol = i;
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

				if (curfewToday)
					if (headerRow.getCell(i).getStringCellValue().equals(curfew.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")))){
						todayCol = i;
						todayExists = true;
					}
					else
						if (headerRow.getCell(i).getStringCellValue().equals(curfew.minusDays(1).format(DateTimeFormatter.ofPattern("MM/dd/yyyy")))){
							todayCol = i;
							todayExists = true;
						}
			}
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

			if (curfew.toLocalDate().equals(LocalDate.now())) // if curfew is today
				headerRow.getCell(todayCol).setCellValue(curfew.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
			else // curfew is tomorrow
				headerRow.getCell(todayCol).setCellValue(curfew.minusDays(1).format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
		}

		// write name of person on ozeret to row 2 in today's column if different than what's already there
		if (sheet.getRow(1).getCell(todayCol) == null)
			sheet.getRow(1).createCell(todayCol).setCellValue(ozeretName);
		else if (!(sheet.getRow(1).getCell(todayCol).getStringCellValue().equals(ozeretName)))
			sheet.getRow(1).getCell(todayCol).setCellValue(ozeretName);
		
		// write curfew time to row 3 in today's column if different than what's already there
		if (sheet.getRow(2).getCell(todayCol) == null)
			sheet.getRow(2).createCell(todayCol).setCellValue(curfew.format(DateTimeFormatter.ofPattern("h:mm a")));
		else if (!(sheet.getRow(2).getCell(todayCol).getStringCellValue().equals(ozeretName)))
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
		Label title = new Label("Sign-In");
		title.setId("header");
		SignInPane.setHalignment(title, HPos.CENTER);
		this.add(title, 0, 0, 3, 1);


		/* left column (clock + time to curfew, view unaccounted for, mark shmira/day off) */

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
		Button saveAndReturn = new Button("Save and Return to Setup");

		viewUnaccounted.setMinWidth(USE_PREF_SIZE);
		save.setMinWidth(USE_PREF_SIZE);
		saveAndReturn.setMinWidth(USE_PREF_SIZE);

		HBox.setHgrow(viewUnaccounted, Priority.ALWAYS);
		HBox.setHgrow(save, Priority.ALWAYS);
		HBox.setHgrow(saveAndReturn, Priority.ALWAYS);

		viewUnaccounted.setMaxWidth(Double.MAX_VALUE);
		save.setMaxWidth(Double.MAX_VALUE);
		saveAndReturn.setMaxWidth(Double.MAX_VALUE);

		listButtons.getChildren().addAll(new HBox(viewUnaccounted), new HBox(), new HBox(save), new HBox(saveAndReturn)); // empty HBox for spacing

		VBox leftColumn = new VBox(this.getVgap());
		leftColumn.getChildren().addAll(clockBox, listButtons);
		this.add(leftColumn, 0, 1);		

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
		
		// stage and scene for viewUnaccounted
		Stage extraStage = new Stage();
		Scene unaccScene = new Scene(new Label("Something's gone wrong"));

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
							LocalTime now = LocalTime.now(); // save current time in case close to curfew

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
								LocalTime now = LocalTime.now(); // save current time in case close to curfew

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

			// given a staff member (via row number) and sign-in time, increments the proper summary
			//  statistic column (if it exists)
			public void signInStatus(int rowNum, LocalTime signInTime) {

				// staff member is on time
				if (curfew.toLocalTime().isAfter(signInTime)) {
					if (ontimeCol != -1) {// there is an "on time" column
						if ((sheet.getRow(rowNum) != null) && sheet.getRow(rowNum).getCell(ontimeCol) != null) // the cell exists
							sheet.getRow(rowNum).getCell(ontimeCol).setCellValue(sheet.getRow(rowNum).getCell(ontimeCol).getNumericCellValue() + 1);
						else // the cell does not exist
							sheet.getRow(rowNum).createCell(ontimeCol).setCellValue(1);
					}
				} else { // staff member is late
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
				unaccScene.getStylesheets().add(OzeretMain.class.getResource("ozeret.css").toExternalForm());

				// only need to do these things if the stage isn't currently on screen
				if (!extraStage.isShowing()) {
					// set up stage
					extraStage.setScene(unaccScene);
					extraStage.setMinWidth(scrollPane.getMinWidth());
					extraStage.setMaxHeight(scrollPane.getMaxHeight());
					extraStage.setTitle("Unaccounted-for Staff");
					extraStage.getIcons().add(new Image("file:resources/images/stage_icon.png"));
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
									options.getDialogPane().getStylesheets().add(getClass().getResource("ozeret.css").toExternalForm());
									options.initOwner(staffMember.getScene().getWindow());
									options.showAndWait();

									if (options.getResult().getText().equals("Shmira")) {
										// staff member should be signed in as on shmira

										// set today's attendance column to show that the staff member is on shmira
										if (staffRow.getCell(todayCol) == null)
											staffRow.createCell(todayCol).setCellValue("Shmira");
										else
											staffRow.getCell(todayCol).setCellValue("Shmira");

										// update "on time" column (if it exists)
										if (ontimeCol != -1) {// there is an "on time" column
											if (staffRow.getCell(ontimeCol) != null) // the cell exists
												staffRow.getCell(ontimeCol).setCellValue(staffRow.getCell(ontimeCol).getNumericCellValue() + 1);
											else // the cell does not exist
												staffRow.createCell(ontimeCol).setCellValue(1);
										}
										
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
					confirmation.setText("Unable to write to " + attendanceFile.getName());
				} 
				
				// print confirmation
				confirmation.setText("Data saved to " + attendanceFile.getName());
			}
		});

		// pulls up list of staff members who have yet to sign in in this session
		//  with option to mark them as on day off
		saveAndReturn.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				
				// see if there are any staff that haven't signed in yet
				boolean allBunksEmpty = noUnaccountedStaff();
				
				// if there are still unaccounted staff, confirm that user still wants to return to set-up
				if (!allBunksEmpty) {
					Alert saveAndReturnConf = new Alert(AlertType.CONFIRMATION, "There are still staff members that haven't signed in.\nAre you sure you want to return to setup?");
					saveAndReturnConf.setHeaderText("Save and Return to Setup Confirmation");
					saveAndReturnConf.setTitle("Save and Return to Setup Confirmation");
					saveAndReturnConf.getDialogPane().getStylesheets().add(getClass().getResource("ozeret.css").toExternalForm());
					saveAndReturnConf.initOwner(saveAndReturn.getScene().getWindow());
					saveAndReturnConf.showAndWait();
					
					if (saveAndReturnConf.getResult() != ButtonType.OK)
						return; // user does not want to save and return to setup
				}
				
				// all staff are accounted for or user wants to mark unaccounted-for staff as absent
				markUnaccAbsent();
				
				// write data to attendanceFile
				try (FileOutputStream afos = new FileOutputStream(attendanceFile)) {
					workbook.write(afos);
				} catch (IOException e) {
					confirmation.setText("Unable to write to " + attendanceFile.getName());
				} 
				
				// close unaccounted-for staff window and change scene back to sign-in scene
				extraStage.close();
				stage.setScene(prevScene);
				stage.centerOnScreen();
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
					
					// increment "absent" column, if it exists
					if (absent && absentCol != -1) {
						if ((sheet.getRow(i) != null) && sheet.getRow(i).getCell(absentCol) != null) // the cell exists
							sheet.getRow(i).getCell(absentCol).setCellValue(sheet.getRow(i).getCell(absentCol).getNumericCellValue() + 1);
						else // the cell does not exist
							sheet.getRow(i).createCell(absentCol).setCellValue(1);
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
				alert.getDialogPane().getStylesheets().add(getClass().getResource("ozeret.css").toExternalForm());
				alert.initOwner(stage);
				alert.showAndWait();

				if (alert.getResult().getButtonData() == ButtonData.OK_DONE)
					Platform.exit();

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
					setText((LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) + 1) + " minute");
				else
					setText((LocalDateTime.now().until(finalTime, ChronoUnit.MINUTES) + 1) + " minutes");
			}
		}), new KeyFrame(Duration.seconds(1)));

		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();
	}
}
