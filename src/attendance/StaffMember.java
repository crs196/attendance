package attendance;

public class StaffMember implements Comparable<StaffMember>{
	
	private String bunk, name, ID;
	private int keyRow, todayRow;
	private boolean signedOut, signedIn;
	
	public StaffMember(String bk, String nm, String id, boolean out, boolean in, int kRow) {
		bunk = bk;
		name = nm;
		ID = id;
		
		keyRow = kRow;
		todayRow = 0;
		
		signedOut = out;
		signedIn = in;
	}
	
	public StaffMember(String bk, String nm, String id, boolean out, boolean in, int kRow, int tRow) {
		bunk = bk;
		name = nm;
		ID = id;
		
		keyRow = kRow;
		todayRow = tRow;
		
		signedOut = out;
		signedIn = in;
	}
	
	public String getBunk() {
		return bunk;
	}
	
	public String getName() {
		return name;
	}
	
	public String getID() {
		return ID;
	}
	
	public int getKeyRow() {
		return keyRow;
	}
	
	public int getTodayRow() {
		return todayRow;
	}
	
	public void setTodayRow(int tr) {
		todayRow = tr;
	}
	
	public boolean isSignedOut() {
		return signedOut;
	}
	
	public void signOut() {
		signedOut = true;
	}
	
	public boolean isSignedIn() {
		return signedIn;
	}
	
	public void signIn() {
		signedIn = true;
	}

	@Override
	public int compareTo(StaffMember sm) {
		return ID.compareTo(sm.getID());
	}
	
	@Override
	public String toString() {
		return bunk + ": " + name + " (" + ID + "), out=" + signedOut + ", in=" + signedIn;
	}
}
