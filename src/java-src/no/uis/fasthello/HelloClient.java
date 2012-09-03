package no.uis.fasthello;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class HelloClient {

  public static void main(String[] args) {
    try {
      Registry registry = LocateRegistry.getRegistry("localhost");
      Hello server = (Hello) registry.lookup("Hello");
      System.out.println("Reply from server: " + server.greetings("Tiger"));
    } catch (RemoteException e) {
      e.printStackTrace();
    } catch (NotBoundException e) {
      e.printStackTrace();
    }
  }
}
