/***
 * JAVANAISE Implementation
 * JvnServerImpl class
 * Implementation of a Jvn server
 * Contact: 
 *
 * Authors: MathysC MatveiP
 */

package jvn.server;

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

import jvn.coord.JvnCoordImpl;
import jvn.coord.JvnRemoteCoord;
import jvn.object.JvnObject;
import jvn.object.JvnObjectImpl;
import jvn.object.JvnObjectInvocationHandler;
import jvn.utils.JvnException;

import java.io.*;

public class JvnServerImpl extends UnicastRemoteObject implements JvnLocalServer, JvnRemoteServer {

	/**
	 * Serialization
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * A JVN server is managed as a singleton
	 */
	private static JvnServerImpl js = null;

	/**
	 * The registered coordinator.
	 */
	private transient JvnRemoteCoord coordinator;

	/**
	 * Identification number of the Server
	 */
	private int id = -1;

	/**
	 * Identification name
	 */
	private String name = "";

	/**
	 * Registry used by the project
	 */
	private transient Registry reg;

	/**
	 * Map of object used by this server.
	 */
	private HashMap<Integer, JvnObject> objects = new HashMap<>();

	/**
	 * Default constructor
	 * 
	 * @throws AlreadyBoundException
	 * @throws RemoteException
	 * @throws AccessException
	 * 
	 * @throws JvnException
	 * @throws NotBoundException
	 **/
	private JvnServerImpl() throws RemoteException, AlreadyBoundException, JvnException, NotBoundException {
		super();
		System.setProperty(JvnCoordImpl.PROPERTY, JvnCoordImpl.COORD_HOST);
		this.reg = LocateRegistry.getRegistry(2001);
		boolean cont = true;
		while (cont) {
			try {
				cont = false;
				this.coordinator = (JvnRemoteCoord) reg.lookup(JvnCoordImpl.COORD_NAME);
			} catch (Exception exp){
				cont = true;
			}
		}
		this.id = this.coordinator.jvnGetObjectId();
		this.name = "srv_" + this.id;
		reg.bind(name, this);
	}

	/**
	 * Static method allowing an application to get a reference to
	 * a JVN server instance
	 * 
	 * @throws JvnException
	 **/
	public synchronized static JvnServerImpl jvnGetServer() {
		if (js == null) {
			try {
				js = new JvnServerImpl();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return js;
	}

	@Override
	public synchronized void jvnTerminate() throws JvnException, RemoteException {
		try {
			this.coordinator.jvnTerminate(this);
			this.reg.unbind(this.name);
			UnicastRemoteObject.unexportObject(js, false);
			this.objects.clear();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
	}

	@Override
	public synchronized Object jvnCreateObject(Serializable jos) throws JvnException, RemoteException {
		try {
			int joi = this.coordinator.jvnGetObjectId();
			JvnObjectImpl jo = new JvnObjectImpl(jos, joi, this);

			// use the proxy instead of the real object
			Object proxy = JvnObjectInvocationHandler.newInstance(jo);

			this.objects.put(joi, jo);
			jvnRegisterObject("IRC", jo);
			jo.jvnUnLock();
			return proxy;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public synchronized void jvnRegisterObject(String jon, JvnObject jo) throws JvnException, RemoteException {
		try {
			this.coordinator.jvnRegisterObject(jon, jo, this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public synchronized Object jvnLookupObject(String jon, Serializable newObj) throws JvnException, RemoteException {
		JvnObject object = this.coordinator.jvnLookupObject(jon, this);
		if (object != null) {
			// change server to be current instead of creator
			object.jvnSetServer(this);
			object.resetState();
			this.objects.put(object.jvnGetObjectId(), object);
			return JvnObjectInvocationHandler.newInstance(object);
		} else {
			return jvnCreateObject(newObj);
		}
	}

	@Override
	public synchronized Serializable jvnLockRead(int joi) throws JvnException {
		Serializable serializable = this.objects.get(joi).jvnGetSharedObject();
		try {
			serializable = this.coordinator.jvnLockRead(joi, this);
			this.objects.get(joi).jvnSetSharedObject(serializable);
		} catch (RemoteException e) {
			System.err.println("[JvnServerImpl][jvnLockRead] id: " + joi + " impossible to Lock");
			e.printStackTrace();
		}
		return serializable;
	}

	@Override
	public synchronized Serializable jvnLockWrite(int joi) throws JvnException {
		Serializable serializable = this.objects.get(joi).jvnGetSharedObject();
		try {
			serializable = this.coordinator.jvnLockWrite(joi, this);
			this.objects.get(joi).jvnSetSharedObject(serializable);
		} catch (RemoteException e) {
			System.err.println("[JvnServerImpl][jvnLockWrite] id: " + joi + " impossible to Lock");
			e.printStackTrace();
		}
		return serializable;
	}

	@Override
	public synchronized void jvnInvalidateReader(int joi) throws RemoteException, JvnException {
		this.objects.get(joi).jvnInvalidateReader();
	}

	@Override
	public synchronized Serializable jvnInvalidateWriter(int joi) throws RemoteException, JvnException {
		return this.objects.get(joi).jvnInvalidateWriter();
	}

	@Override
	public synchronized Serializable jvnInvalidateWriterForReader(int joi) throws RemoteException, JvnException {
		return this.objects.get(joi).jvnInvalidateWriterForReader();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((coordinator == null) ? 0 : coordinator.hashCode());
		result = prime * result + id;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		JvnServerImpl other = (JvnServerImpl) obj;
		if (id != other.id)
			return false;
		if (coordinator == null) {
			if (other.coordinator != null)
				return false;
		} else if (!coordinator.equals(other.coordinator))
			return false;
		return true;
	}
}
