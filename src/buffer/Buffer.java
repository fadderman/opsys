package buffer;

public interface Buffer<E> {
  public void insert(E item);
  public E remove();
}
