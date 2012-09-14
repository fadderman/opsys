package no.uis.fasthello;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Hello extends Remote {

  String greetings(String from) throws RemoteException;

}
