package no.uis.hello;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Hello extends Remote {

	public String greeter(String name, HelloBack client) throws RemoteException;

}
