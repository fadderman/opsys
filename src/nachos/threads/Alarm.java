package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;


/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p><b>Note</b>: Nachos will not function correctly with more than one
	 * alarm.
	 */
	private PriorityQueue<WaitingThread> waitQueue; 


	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		});
		waitQueue = new PriorityQueue<WaitingThread>(); 
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread
	 * that should be run.
	 */
	public void timerInterrupt() {
		KThread.currentThread().yield();
		long currentTime = Machine.timer().getTime();
		boolean intStatus = Machine.interrupt().disable();
		
		while (!waitQueue.isEmpty() && waitQueue.peek().waketime <= currentTime){
			WaitingThread waitingThread = waitQueue.poll();
			KThread thread = waitingThread.thread;
			if (thread != null){
			thread.ready();
			}
		}
		KThread.yield();
		Machine.interrupt().restore(intStatus);
		
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks,
	 * waking it up in the timer interrupt handler. The thread must be
	 * woken up (placed in the scheduler ready set) during the first timer
	 * interrupt where
	 *
	 * <p><blockquote>
	 * (current time) >= (WaitUntil called time)+(x)
	 * </blockquote>
	 *
	 * @param	x	the minimum number of clock ticks to wait.
	 *
	 * @see	nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
		KThread thread = KThread.currentThread();
		WaitingThread waitingThread = new WaitingThread( wakeTime,thread);
		boolean intStatus = Machine.interrupt().disable();
		waitQueue.add(waitingThread);
		thread.sleep();
		Machine.interrupt().restore(intStatus);
	}

	private class WaitingThread implements Comparable<WaitingThread>{
		private KThread thread;
		private long waketime;

		public WaitingThread(long waketime, KThread thread){
			this.waketime = waketime;
			this.thread = thread;
		}

		@Override
		public int compareTo(WaitingThread o) {
			if (this.waketime > o.waketime){
				return 1;
			}
			else{ 
				if (this.waketime < o.waketime){
					return -1;
				}
				else{
					return 0;
				}
			}
		}


	}
}
