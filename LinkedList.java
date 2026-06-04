public class LinkedList
{
	Reading head;
	Reading tail;

	public static long count = 0;

	public LinkedList()
	{
		head = null;
		tail = null;
	}

	public void add(Reading r)
	{
		if(head == null)
		{
			head = r;
			tail = r;
		} else
		{
			tail.next = r;
			tail = tail.next;
		}
		count++;
	}

	public void removeTemp(double temp)
	{
		Reading curr = head;
		Reading prev = head;

		while (curr != null) // go through whole list
		{
			if(curr.avgtemp == temp)
			{
				System.out.println("Removing: " + removeAfter(prev));
			}
			else
			{
				prev = curr;
			}

			curr = curr.next;
		}

		System.out.println(this);
	}

	public Reading removeAfter(Reading r)
	{
		//given a position in the linked list;
		// get the node after, then cut it from the list

		Reading cutMe = r.next;
		r.next = r.next.next;

		count --;

		return cutMe;
	}

	public Reading findMin()
	{
		Reading min = head;
		Reading curr = head;

		while(curr != null)
		{
			if(curr.avgTemp < min.avgTemp)
			{
				min = curr;
			}

			curr = curr.next;
		}
		return min;
	}
	public Reading findMax()
	{
		Reading max = head;
		Reading curr = head;

		while (curr!= null)
		{
			if (curr.avgTemp > max.avgTemp)
			{
				max = curr;
			}
			curr = curr.next;
		}

		// go through all the readings
		// if max value, mark it

		// at end return that node
		return max;
	}

	public Reading findMaxYear()
	{
		Reading max = head;
		Reading curr = head;

		while (curr != null)
		{
			if (curr.year > max.year)
			{
				max = curr;
			}
			curr = curr.next;
		}

		// go through all the readings
		// if max value, mark it
		// at end return that node
		return max;
	}

	public Reading findMinByCountry(String country)
	{
		Reading curr = head;
		Reading min = null;

		while (curr != null)
		{
			if(curr.country.equalsIgnoreCase(country))
			{
				if (min == null || curr.avgTemp < min.avgTemp)
				{
					min = curr;
				}
			}
			curr = curr.next;
		}
		return min;
	}

	public Reading findMaxByRegion(String region)
	{
		Reading curr = head;
		Reading max = null

		while(curr != null)
		{
			if (curr.region.equalsIgnoreCase(region))
			{
				if (max == null || curr.avgTemp > max.avgTemp)
				{
					max = curr;
				}
			}
			curr = curr.next;
		}
		return max;
	}

	public Reading findMaxByCity(String city)
	{
		Reading curr = head;
		Reading max = null;

		while (curr != null)
		{
			if(max == null || curr.avgTemp > max.avgTemp)
			{
				max = curr;
			}
		}
			curr = curr.next;
	}
	return max;

public void graphCity(String city)
{
	for (Reading curr = head; curr != null; curr = curr.next)
	{
		if(curr.city.equalsIgnoreCase(city))
		{
			int bars = (int)(curr.avgTemp + 30);
			System.out.printf("%d/%d/%d/ | " , curr.month, curr.day, curr.year);

			for (int i = 0; i < bars; i++)
			{
				System.out.print("*");
			}
			System.out.println(" " + curr.avgTemp);
		}
	}
}
}
				
				
