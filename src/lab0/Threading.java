package lab0;

public class Threading implements Runnable{
	public int test1 = 0;
	public int state = 0;
	
	public Threading(int x){
		state = x;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	@Override
	public void run() {
		try {
			if (state == 1)
			{
				test1 = 1;
			}
			if (state == 2){
				test1 = 2;
			}
			else state = 0;
		}catch(Exception e){
			System.out.println("the thread failed");
		}
	}

}
