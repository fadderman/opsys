package no.uis.hello;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface HelloBack extends Remote {

	public void ping() throws RemoteException;
}
