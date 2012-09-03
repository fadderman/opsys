package buffer;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BoundedBuffer<E> implements Buffer<E> {

  private static final int BUF_SIZE = 5;
  private static final Random RND = new Random();
  private int count = 0; // number of items in buffer
  private int in = 0;    // points to next free position
  private int out = 0;   // points to next full position
  @SuppressWarnings("unchecked")
  private E[] buffer = (E[]) new Object[BUF_SIZE];

  // Producers call this method
  @Override public synchronized void insert(E item) {
    while (count == BUF_SIZE)
      ; // do nothing -- no free space
    buffer[in] = item;
    in = (in+1) % BUF_SIZE;
    ++count;
  }

  // Consumers call this method
  @Override public  E remove() {
    E item;
    while (count == 0)
      ; // do nothing -- nothing to consume
    item = buffer[out];
    out = (out+1) % BUF_SIZE;
    --count;
    return item;
  }

  public static void main(String[] args) {
    final Buffer<String> buf = new BoundedBuffer<String>();
    ExecutorService es = Executors.newFixedThreadPool(2);
    es.execute(new Runnable() {
      // producer thread
      public void run() {
        while (true) {
          buf.insert(String.valueOf(RND.nextInt()));
        }
      }
    });
    es.execute(new Runnable() {
      // consumer thread
      public void run() {
        while (true) {
          String item = buf.remove();
          System.out.println("New item is: " + item);
        }
      }
    });
  }
}
