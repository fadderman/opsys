package no.uis.hello;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HelloClient implements HelloBack {

	public HelloClient() throws RemoteException {
		UnicastRemoteObject.exportObject(this, 0);
	}

	public static void main(String[] args) {
		String hostname = args[0];
		hostname = hostname != null && hostname.length() > 0 ? hostname : "localhost";
		System.out.println("Looking for registry on: " + hostname);
		try {
			HelloClient client = new HelloClient();
			Registry registry = LocateRegistry.getRegistry(hostname);
			Hello server = (Hello) registry.lookup("Hello");
			System.out.println("Obtained a remote reference from registry: " + server);
			String greeting = server.greeter("Hein", client);
			System.out.println(greeting);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
	}

	public void ping() throws RemoteException {
		System.out.println("pinged");
	}

}
