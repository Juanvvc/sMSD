package org.msd.election;

import java.util.Enumeration;
import java.util.Vector;
import org.msd.proxy.*;
import org.msd.comm.*;

import org.apache.log4j.Logger; //@@l

/** A class to manage an election in the network.
 * @todo Read the parameters from a configuration file. */
public class MSDMasterElection implements TimeListener{
    private final static Logger logger=Logger.getLogger(MSDMasterElection.class);

    /** The timeout of the election.  */
    static int timeout=10;
    /** The neighbours of this msd */
    private Vector neigh_info;
    /** The information of this MSD */
    private StationInfo st_info;
    /** Wether or not the election has started */
    private boolean electionPhase;
    /** Wether or not we are NOT the leader */
    private boolean imNotMSD;
    /** The network we are electiong for */
    private NetworkManager net;
    /** The object of the msd */
    private MSDManager msd;
    /** Wether or not the timeout has started */
    private boolean timeoutStarted=false;

    /** @param msd The object of the MSD.
     * @param net The network
     * @param cert The certificate of the MSD
     */
    public MSDMasterElection(MSDManager msd,NetworkManager net,byte[] cert){
        this.msd=msd;
        this.net=net;
        this.st_info=(StationInfo)new CertifiedStationInfo(cert,".");
        initialize();
    }

    /** Initialize variables to "not electing" */
    private void initialize(){
        if(neigh_info==null){
            neigh_info=new Vector();
        } else{
            neigh_info.clear();
        }
        electionPhase=false;
        imNotMSD=false;
        timeoutStarted=false;
    }

    /** Starts an election.
     * The MSD has send its own certificate to the network before entering
     * this method. Once finished, the MSD leader of the network will receive
     * an event in his MSDManager.iAmTheLeader() method. The rest of the
     * MSDs will not be aware of the finishing of the election.
     */
    public void startElection(){
        if(!electionPhase){
            logger.info("Starting election"); //@@l
            electionPhase=true;
            if(!timeoutStarted){
                TimeManager.getTimeManager().register(this,timeout);
                timeoutStarted=true;
                imNotMSD=false;
            }

            int weight=weightOfStation(st_info);
            int weight2;
            StationInfo sta=new StationInfo(0,0,0,0,false);

            for(Enumeration e=neigh_info.elements();e.hasMoreElements();){
                sta=(StationInfo)e.nextElement();
                weight2=weightOfStation(sta);
                if(weight2>weight){
                    logger.debug("The potencial leader is "+sta.getId()); //@@l
                    imNotMSD=true;
                    break;
                }
            }
        }
    }

    /** Interrupts the election, not informing to anyone. */
    public void interruptElection(){
        logger.info("The election is interrupted"); //@@l
        initialize();
    }

    /** Calculates the weight of a station.
     * @param sta The information of the station
     * @return The weight of the station
     */
    public int weightOfStation(StationInfo sta){
        Double weight;
        double mobility_factor=.9;
        double battery_factor=.5;
        double cpu_factor=.3;
        double bridge_factor=.2;

        // Normalizo los valores
        double suma=mobility_factor+battery_factor+cpu_factor+bridge_factor;
        mobility_factor=mobility_factor/suma;
        battery_factor=battery_factor/suma;
        cpu_factor=cpu_factor/suma;
        if(sta.isMSD_bridge()){
            bridge_factor=(bridge_factor/suma)*100;
        } else{
            bridge_factor=0;
        }

        weight=new Double(mobility_factor*(100-sta.getMobility())+
                          battery_factor*sta.getBattery()+cpu_factor*sta.getCPU()+
                          bridge_factor);
        return weight.intValue();
    }

    /** Gets a new certificate from the network.
     * We are saving certificates even if we are not in the election phase:
     * someone can start an election before us and it will send its certificate.
     *
     * @param cert The certificate of the remote MSD participating in the
     * election.
     */
    public void newCertificate(byte[] cert){
        logger.debug("New certificate"); //@@l
        // TODO Auto-generated method stub
        if(neigh_info.size()==0){
            imNotMSD=false;
            if(!timeoutStarted){
                timeoutStarted=true;
                TimeManager.getTimeManager().register(this,timeout);
            }

        }
        if(!imNotMSD){
            StationInfo si=(StationInfo)new CertifiedStationInfo(cert,".");
            if(electionPhase){
                if(weightOfStation(si)>weightOfStation(st_info)){
                    logger.debug("The potencial leader is "+si.getId()); //@@l
                    imNotMSD=true;
                } else{
                    logger.debug("I am better"); //@@l
                }
            } else{
                neigh_info.add(si);
            }
        }
    }

    /** Gets a signal from the TimeManager: the election is over.
     * If the current MSD has been elected, informs. Otherwise, does nothing.
     * @param type ignored.
     * @param data ignored
     * @return false
     */
    public boolean signal(int type,Object data){
        if(electionPhase){
            logger.info("The election is over"); //@@l
            if(!imNotMSD){
                msd.iAmTheLeader(net);
            }
            initialize();
        }
        return false;
    }
}
