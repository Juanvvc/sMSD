 package org.msd.election;
/*
 * Created on Jul 7, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author juan
 *
 */
public class CertifiedStationInfo extends StationInfo {
	AttributesManager am;
	
	/**
	 * @param am
	 */
	
	public CertifiedStationInfo(byte[] cert, String pathWork) {
		
		getAttrFromCert(cert, pathWork);
		try {
			super.setID(getCertId());
			super.setBattery((getCertBattery()));
			super.setCPU(getCertCPU());
			super.setMobility(getCertMobility());
			super.setMsd_bridge(isCertMSDbridge());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public CertifiedStationInfo(String pathAC, String pathWork) {
		getAttrFromCert(pathAC,pathWork);
		try {
			super.setID(getCertId());
			super.setBattery((getCertBattery()));
			super.setCPU(getCertCPU());
			super.setMobility(getCertMobility());
			super.setMsd_bridge(isCertMSDbridge());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void getAttrFromCert(String pathAC, String pathWork) {
		try {
			am = new AttributesManager(pathAC,pathWork);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void getAttrFromCert(byte[] cert, String pathWork) {
		try {
			am = new AttributesManager(cert,pathWork);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public int getCertId() throws Exception {
		Integer attr = (Integer) am.getAttribute("0.0",null,null);
		if(attr == null){
			throw new Exception("Couldn't read attribute");
		}
		return attr.intValue();
	}
	public int getCertMobility() throws Exception {
		Integer attr = (Integer) am.getAttribute("0.1",null,null);
		if(attr == null){
			throw new Exception("Couldn't read attribute");
		}
		return attr.intValue();
	}
	public int getCertBattery() throws Exception {
		Integer attr = (Integer) am.getAttribute("0.2",null,null);
		if(attr == null){
			throw new Exception("Couldn't read attribute");
		}
		return attr.intValue();
	}
	public int getCertCPU() throws Exception {
		Integer attr = (Integer) am.getAttribute("0.3",null,null);
		if(attr == null){
			throw new Exception("Couldn't read attribute");
		}
		return attr.intValue();
	}
	public boolean isCertMSDbridge() throws Exception {
		Boolean attr = (Boolean) am.getAttribute("0.4",null,null);
		if(attr == null){
			throw new Exception("Couldn't read attribute");
		}
		return attr.booleanValue();
	}
}
