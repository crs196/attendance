package attendance;

public class StaffMember implements Comparable<StaffMember>{
	
	private String bunk, name, ID;
	private int keyRow, todayRow;
	private boolean signedOut, signedIn;
	private CurfewType curfewType;
	
	public StaffMember(String bk, String nm, String id, boolean out, boolean in, CurfewType ct, int kRow) {
		bunk = bk;
		name = nm;
		ID = id;
		
		keyRow = kRow;
		todayRow = 0;
		
		signedOut = out;
		signedIn = in;
		
		curfewType = ct;
	}
	
	public StaffMember(String bk, String nm, String id, boolean out, boolean in, CurfewType ct, int kRow, int tRow) {
		bunk = bk;
		name = nm;
		ID = id;
		
		keyRow = kRow;
		todayRow = tRow;
		
		signedOut = out;
		signedIn = in;
		
		curfewType = ct;
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
	
	public void unSignOut() {
		signedOut = false;
	}
	
	public boolean isSignedIn() {
		return signedIn;
	}
	
	public void signIn() {
		signedIn = true;
	}
	
	public void unSignIn() {
		signedIn = false;
	}
	
	public CurfewType getCurfewType() {
		return curfewType;
	}
	
	public void setCurfewType(CurfewType ct) {
		curfewType = ct;
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