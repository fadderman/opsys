package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	static BoatGrader bg;

	private static Lock lock;

	private static Condition2 condition;


	private static int childrenOnOahu;
	private static int childrenOnMolokai;
	private static int adultsOnOahu;

	private static int childrenOnBoat;

	private static int lastReportedChildrenOnOahu;
	private static int lastReportedChildrenOnMolokai;
	private static int lastReportedAdultsOnOahu;

	private static Location boatLocation;

	private static boolean finished;

	private static boolean start;

	private enum Location {OAHU, MOLOKAI, OCEAN};


	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		//  	begin(1, 2, b);

		//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		//  	begin(3, 3, b);
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		lock = new Lock();
		condition = new Condition2(lock);
		childrenOnOahu = 0;
		childrenOnMolokai = 0;
		adultsOnOahu = 0;
		lastReportedChildrenOnOahu = 0;
		lastReportedChildrenOnMolokai = 0;
		lastReportedAdultsOnOahu = 0;
		childrenOnBoat = 0;
		boatLocation = Location.OAHU;
		finished = false;
		start = false;


		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		lock.acquire();

		for(int x = 0; x < adults; x++) {
			Runnable r = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Adult Boat Thread " + (x+1));
			t.fork();
			condition.wake();
			condition.sleep();
		}

		for(int x = 0; x < children; ++x) {
			Runnable r = new Runnable() {
				public void run() {
					ChildItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Child Boat Thread " + (x+1));
			t.fork();
			condition.wake();
			condition.sleep();
		}

		start = true;
		while(!finished) {
			condition.wake();
			condition.sleep();
		}

		condition.wakeAll();
	}

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	 */
	static void AdultItinerary()
	{
		lock.acquire();

		Location currentLocation = Location.OAHU;
		adultsOnOahu++;

		while(!start){
			condition.wake();
			condition.sleep();
		}


		while(!finished) {
			if(currentLocation == Location.OAHU && boatLocation == Location.OAHU && lastReportedChildrenOnMolokai > 0 && childrenOnBoat == 0){
				adultsOnOahu--;

				int childrenSeen = childrenOnOahu;

				bg.AdultRowToMolokai();
				boatLocation = Location.MOLOKAI;
				currentLocation = Location.MOLOKAI;

				lastReportedChildrenOnOahu = childrenSeen;

				condition.wake();
				condition.sleep();
			}else {
				if(currentLocation == Location.MOLOKAI && lastReportedAdultsOnOahu == 0 && lastReportedChildrenOnOahu == 0 && boatLocation == Location.MOLOKAI) {
					finished = true;
				}
				condition.wake();
				condition.sleep();

			}
			lock.release();
		}
	}

	static void ChildItinerary()
	{
		lock.acquire();

		Location currentLocation = Location.OAHU;
		childrenOnOahu++;

		while(!start) {
			condition.wake();
			condition.sleep();
		}

		while(!finished) {

			if(currentLocation == Location.OAHU && boatLocation == Location.OAHU && (childrenOnOahu > 1 || childrenOnBoat == 1)) {
				if(childrenOnBoat == 0) {
					childrenOnOahu--;
					childrenOnBoat++;

					while(boatLocation == Location.OAHU){
						condition.wake();
						condition.sleep();
					}

					bg.ChildRideToMolokai();
					boatLocation = Location.MOLOKAI;
					currentLocation = Location.MOLOKAI;

					childrenOnBoat--;
					childrenOnMolokai++;

				}
				else if(childrenOnBoat == 1){
					childrenOnOahu--;
					childrenOnBoat++;

					int adultsSeen = adultsOnOahu;
					int childrenSeen = childrenOnOahu;

					bg.ChildRideToMolokai();
					boatLocation = Location.MOLOKAI;
					currentLocation = Location.MOLOKAI;

					childrenOnBoat--;
					childrenOnMolokai++;

					lastReportedChildrenOnOahu = childrenSeen;
					lastReportedAdultsOnOahu = adultsSeen;

				}

			}else if(currentLocation == Location.MOLOKAI && boatLocation == Location.MOLOKAI && (lastReportedChildrenOnOahu > 0 || lastReportedAdultsOnOahu > 0) && childrenOnBoat == 0){
				childrenOnMolokai--;
				childrenOnBoat++;

				int childrenSeen = childrenOnMolokai;

				bg.ChildRowToOahu();
				boatLocation = Location.OAHU;
				currentLocation = Location.OAHU;

				childrenOnBoat--;
				childrenOnOahu++;

				lastReportedChildrenOnMolokai = childrenSeen;

				condition.wake();
				condition.sleep();
			}else if(currentLocation == Location.OAHU && boatLocation == Location.OAHU && adultsOnOahu == 0 && childrenOnOahu == 1 && childrenOnBoat == 0) {
				childrenOnOahu--;
				childrenOnBoat++;

				int adultsSeen = adultsOnOahu;
				int childrenSeen = childrenOnOahu;

				bg.ChildRowToMolokai();
				boatLocation = Location.MOLOKAI;
				currentLocation = Location.MOLOKAI;

				childrenOnBoat--;
				childrenOnMolokai++;

				lastReportedChildrenOnOahu = childrenSeen;
				lastReportedAdultsOnOahu = adultsSeen;

				condition.wake();
				condition.sleep();
			}else{
				if(currentLocation == Location.MOLOKAI && lastReportedAdultsOnOahu == 0 && lastReportedChildrenOnOahu == 0 && boatLocation == Location.MOLOKAI) {
					finished = true;
				}
				condition.wake();
				condition.sleep();
			}
		}
		lock.release();
	}

	static void SampleItinerary()
	{
		// Please note that this isn't a valid solution (you can't fit
				// all of them on the boat). Please also note that you may not
				// have a single thread calculate a solution and then just play
				// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
