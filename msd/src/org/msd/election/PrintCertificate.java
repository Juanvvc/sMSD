 package org.msd.election;
public class PrintCertificate {
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		switch (args.length) {
		case 1:
			StationInfo sta = (StationInfo) new CertifiedStationInfo(args[0],".");
			System.out.println("ID (int): "+sta.getId());
			System.out.println("Mobility (int): "+sta.getMobility());
			System.out.println("Battery (int): "+sta.getBattery());
			System.out.println("CPU (int): "+sta.getCPU());
			System.out.println("MSD_bridge (boolean): "+ sta.isMSD_bridge());			
			break;
			
		default:
			System.out.println("Use: java -classpath iaik_jce_full.jar:. org.msd.election.PrintCertificate <cert_file>");
			break;
		}
	}
}
