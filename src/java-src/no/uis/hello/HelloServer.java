package no.uis.hello;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HelloServer implements Hello {

	private static final String GREETINGS = ", hello to your world!";
	private static final int registryPort = 1099;

	private HelloServer() throws RemoteException {
		UnicastRemoteObject.exportObject(this, 0);
	}

	public String greeter(String name, HelloBack client) throws RemoteException {
		System.out.println("Received client request from: " + name);
		client.ping();
		return name + GREETINGS;
	}

	public static void main(String[] args) {
		try {
			HelloServer server = new HelloServer();
			Registry registry = LocateRegistry.createRegistry(registryPort);
			registry.rebind("Hello", server);
			System.out.println("HelloServer started. Awaiting client requests...");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

}
