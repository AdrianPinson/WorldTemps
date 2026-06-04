import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class WorldTemps
{
	public static void main(String[] args)
	{

		String filename = "city_temperature.csv";

		LinkedList ll = new LinkedList();

		loadFile(ll, filename);

		Scanner input = new Scanner(System.in);
		int choice;

		do
		{
			System.out.println("\n--- City Temperature Menu ---");
			System.out.println("1. Find overall MIN temperature");
			System.out.println("2. Find overall MAX temperature");
			System.out.println("3. Find MIN by country");
			System.out.println("4. Find MAX by region");
			System.out.println("5. Find MAX by city");
			System.out.println("6. Graph temperatures for a city");
			System.out.println("0. Exit");
			System.out.println("Choice: ");

			choice = input.nextInt();
			input.nextLine();

			switch (choice)
			{
			case 1:
				System.out.println(ll.findMin());
				break;
			case 2:
				System.out.println(ll.findMax());
			case 3:
				System.out.println("Enter country: ");
				String country = input.nextLine();
				System.out.println(ll.findMinByCountry(country));
				break;
			case 4:
				System.out.print("Enter region: ");
				String region = input.nextLine();
				System.out.println(ll.findMaxByRegion(region));
				break;
			case 5:
				System.out.println("Enter city: ");
				String city = input.nextLine();
				System.out.println(ll.findMaxByCity(city));
				break;
			case 6:
				System.out.print("Enter city: ");
				String graphCity = input.nextLine();
				ll.graphCity(graphCity);
				break;
			case 0:
				System.out.println("Goodbye!");
				break;
			default:
				System.out.println("Invalid option.");
			}
		}while (choice != 0);
		input.close();
	}

private static void loadFile(LinkedList ll, String filename)
{
	try
	{
		FileReader fr = new FileReader(filename);
		BufferedReader br = new BufferedReader(fr);

		br.readLine(); //ignore headeer line

		String line; //one line of data from file
		String splitBy = ",";

		while((line = br.readLine()) != null)
		{
			String [] data = line.split(splitBy);

			String region = data[0];
			String country = data[1];
			String state = data[2];
			String city = data [3];
			byte month = Byte.parseByte(data[4]);
			byte day = Byte.parseByte(data[5]);
			short year = Short.parseShort(data[6]);
			double avgTemp = Double.parseDouble(data[7]);

			Reading r = new Reading(region, country, state, city, month, day, year, avgTemp);
			ll.add(r);
		}

		br.close();

	}
	catch(IOException e)
	{
		System.out.println("File error: " + filename);
	}
}
			
			
		
