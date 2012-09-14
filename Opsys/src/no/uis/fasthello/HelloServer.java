package no.uis.fasthello;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HelloServer implements Hello {

  private static final int REG_PORT = 1099;

  private HelloServer() throws RemoteException {
    UnicastRemoteObject.exportObject(this, 0);
  }

  public String greetings(String from) throws RemoteException {
    return "Hello there, " + from;
  }

  public static void main(String[] args) {
    try {
      HelloServer server = new HelloServer();
      Registry registry = LocateRegistry.createRegistry(REG_PORT);
      registry.rebind("Hello", server);
      System.out.println("HelloServer started. Awaiting client requests...");
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }
}
