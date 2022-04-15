package attendance;

public class StaffMember implements Comparable<StaffMember>{
	
	private String bunk, name, ID;
	private int onTime, late, absent;
	private int keyRow, todayRow;
	private boolean signedOut, signedIn;
	
	public StaffMember(String bk, String nm, String id, int ot, int lte, int abs, boolean out, boolean in, int kRow) {
		bunk = bk;
		name = nm;
		ID = id;
		
		keyRow = kRow;
		todayRow = 0;
		
		// TODO: these three properties don't actually get used
		onTime = ot;
		late = lte;
		absent = abs;
		
		signedOut = out;
		signedIn = in;
	}
	
	public StaffMember(String bk, String nm, String id, int ot, int lte, int abs, boolean out, boolean in, int kRow, int tRow) {
		bunk = bk;
		name = nm;
		ID = id;
		
		keyRow = kRow;
		todayRow = tRow;
		
		// TODO: these three properties don't actually get used
		onTime = ot;
		late = lte;
		absent = abs;
		
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
	
	public int getOnTime() {
		return onTime;
	}
	
	public int getLate() {
		return late;
	}
	
	public int getAbsent() {
		return absent;
	}
	
	public int[] getSummary() {
		return new int[] {onTime, late, absent};
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
		return bunk + ": " + name + " (" + ID + ")";
	}
}
