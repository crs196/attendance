package attendance;

public enum CurfewType {
	NONE {
		@Override
		String writeByType() {
			return "Curfew Type Selection Error";
		}
	},
	NORMAL {
		@Override
		String writeByType() {
			return "Night \nOff";
		}
	},
	NIGHT_OFF {
		@Override
		String writeByType() {
			return "Extended \nNight Off";
		}
	},
	DAY_OFF_DAY_1 {
		@Override
		String writeByType() {
			return "Day \nOff";
		}
	},
	DAY_OFF_DAY_2 {
		@Override
		String writeByType() {
			return "Day \nOff";
		}
	},
	VISITOR {
		@Override
		String writeByType() {
			return "Visitor";
		}
	},
	BUNK {
		@Override
		String writeByType() {
			return "Not in Bunk";
		}
	};
	
	abstract String writeByType();
}
