/*
 * Created on Apr 11, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

 package org.msd.election;
 
/**
 * @author juan
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class StationInfo {
	private int id;
	private int mobility;
	private int battery;
	private int cpu;
	private boolean msd_bridge;
	
	
	/**
	 * @param id
	 * @param mobility
	 * @param battery
	 * @param cpu
	 * @param msd_bridge
	 */
	public StationInfo(int id, int mobility, int battery, int cpu,
			boolean msd_bridge) {
		this.id = id;
		this.mobility = mobility;
		this.battery = battery;
		this.cpu = cpu;
		this.msd_bridge = msd_bridge;
	}
	
	/**
	 * 
	 */
	public StationInfo() {
		super();
	}
	/**
	 * @return Returns the id.
	 */
	public int getId() {
		return id;
	}	
	/**
	 * @return Returns the battery.
	 */
	public int getBattery() {
		return battery;
	}
	/**
	 * @return Returns the cpu.
	 */
	public int getCPU() {
		return cpu;
	}
	/**
	 * @return Returns the mobility.
	 */
	public int getMobility() {
		return mobility;
	}
	/**
	 * @return Returns the msd_bridge.
	 */
	public boolean isMSD_bridge() {
		return msd_bridge;
	}
	/**
	 * @param id The id to set.
	 */
	public void setID(int id) {
		this.id = id;
	}
	/**
	 * @param battery The battery to set.
	 */
	public void setBattery(int battery) {
		this.battery = battery;
	}
	/**
	 * @param cpu The cpu to set.
	 */
	public void setCPU(int cpu) {
		this.cpu = cpu;
	}
	/**
	 * @param mobility The mobility to set.
	 */
	public void setMobility(int mobility) {
		this.mobility = mobility;
	}
	/**
	 * @param msd_bridge The msd_bridge to set.
	 */
	public void setMsd_bridge(boolean msd_bridge) {
		this.msd_bridge = msd_bridge;
	}
}
