package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	private Lock lock;

	private Condition listeners;
	private Condition speakers;
	private Condition isDone;
	
	boolean full;
	
	int word;

	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		lock = new Lock();
        listeners = new Condition(lock);
        speakers = new Condition(lock);
        isDone = new Condition(lock);
        full = false;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param	word	the integer to transfer.
	 */
	public void speak(int word) {
		lock.acquire();
		
		while(full){
			speakers.sleep();
		}
		this.word = word;
		
		listeners.wake();
		lock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return
	 * the <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return	the integer transferred.
	 */    
	public int listen() {
		lock.acquire();
		
		while(full){
			listeners.sleep();
		}
		int result = word;
		full = false;
		speakers.wake();
		
		lock.release();
		return result;
	}
}
