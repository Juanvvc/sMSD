 package org.msd.election;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FillCertificate {
	String pathAC;
	String pathWork;
	AttributesManager am;
	StationInfo sta;
	
	
	public FillCertificate(String pathAC, String pathWork, StationInfo sta) {
		// TODO Auto-generated constructor stub
		this.pathAC = pathAC;
		this.pathWork = pathWork;
		try {
			am = new AttributesManager(this.pathAC,this.pathWork);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.sta = sta;
		writeCertificate();
	}


	private void writeCertificate(){
		am.removeAllAttributes();
		am.putAttribute("0.0",null,null,new Integer(sta.getId()));
		am.putAttribute("0.1",null,null,new Integer(sta.getMobility()));
		am.putAttribute("0.2",null,null,new Integer(sta.getBattery()));
		am.putAttribute("0.3",null,null,new Integer(sta.getCPU()));
		am.putAttribute("0.4",null,null,new Boolean(sta.isMSD_bridge()));
		
		try {
			am.saveACas(pathAC);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] argv) {
		switch(argv.length) {
		case 6:
			new FillCertificate(argv[5],".",new StationInfo(Integer.parseInt(argv[0]),Integer.parseInt(argv[1]),Integer.parseInt(argv[2]),Integer.parseInt(argv[3]),Boolean.getBoolean(argv[4])));
			break;
		default:
            System.out.println("Use: java -classpath iaik_jce_full.jar:. org.msd.election.FillCertificate <id> <mobility> <battery> <cpu> <bridge> <cert_name>");
		}
	}
}
