package attendance;

public class StaffMember implements Comparable<StaffMember>{
	
	private String bunk, name, ID;
	private int onTime, late, absent;
	private boolean signedOut, signedIn;
	
	public StaffMember(String bk, String nm, String id, int ot, int lte, int abs, boolean out, boolean in) {
		bunk = bk;
		name = nm;
		ID = id;
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
