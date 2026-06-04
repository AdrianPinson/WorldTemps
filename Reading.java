public class Reading {
	String region;
	String country;
	String state;
	String city;
	byte month;
	byte day;
	short year;
	double avgTemp;
	
	Reading next = null;
	
	Reading (String r, String co, String s, String ci, byte m, byte d, short y, double t)
	{
		region = r;
		country = co;
		state = s;
		city = ci;
		month = m;
		day = d; 
		year = y;
		avgTemp = t;
	}

	@Override
	public Stringg toString()
	{
		return String.format("\nAvg Temp: %1.1f\nDate: %d\\%d\\%d\n%s,%s\n", avgTemp,month,day,year,country,city);
	}
}
  
