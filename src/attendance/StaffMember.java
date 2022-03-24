package attendance;

public class StaffMember implements Comparable<StaffMember>{
	
	private String bunk, name, ID;
	
	public StaffMember(String b, String n, String i) {
		bunk = b;
		name = n;
		ID = i;
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

	@Override
	public int compareTo(StaffMember sm) {
		return ID.compareTo(sm.getID());
	}
	
	@Override
	public String toString() {
		return bunk + ": " + name + " (" + ID + ")";
	}
}
