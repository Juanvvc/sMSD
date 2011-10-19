package org.msd.proxy;

import java.util.Hashtable;

/** This file is a default configuration respurce bundle for an MSD.
 * With this class you will create a basic MSD with ethernet an bluetooth
 * interfaces reading the initial cache from conf/cache.xml
 */
public class DefaultResourceBundle extends java.util.ListResourceBundle{
    private static Hashtable properties=null;

    static{
        properties=new Hashtable();
        properties.put("Loj4J","log4j.conf"); //Log4j conf file
        properties.put("LocalFile",""); // local file to store the cache
        properties.put("GUI","false"); // gui interface?
        properties.put("Cache",""); // initial cache file
        properties.put("CacheXML","<cache idcache=\"1000\"><service name=\"MSD\"><network name=\"ethernet\"><attr name=\"url\"></attr><attr name=\"port\">15150</attr></network><network name=\"bluetooth\"/></service></cache>"); // the initial cache
        properties.put("MSDConf","org.msd.proxy.DefaultResourceBundle"); // conf for MSD (this class)
        properties.put("MSD.Algorithm","shared"); // algortithm of cache sharing
        properties.put("MSD.IAmHereTime","10"); //time in seconds between I_AM_HERE messages.
        properties.put("MSD.IAmHereFactor","3"); // consider an MSD down if miss this number of I_AM_HERE messages
        properties.put("MSD.UUID","0000111A00001000800000805F9B34FB"); //UUID for Bluetooth MSD
        properties.put("MSD.ethernet.MulticastURL","239.255.255.254"); // IP of the multicast group or broadcast
        properties.put("MSD.ethernet.MulticastPort","15150"); // port of the multicast group
        properties.put("MSD.wifi.MulticastURL","192.168.1.255"); // IP2 of the multicast group or broadcast
        properties.put("MSD.wifi.MulticastPort","15151"); // port of the multicast group
        properties.put("MSD.ethernet.proxies","slp"); // Protocols for Internet
        properties.put("MSD.wifi.proxies","slp"); // Protocols for Internet2
        properties.put("MSD.bluetooth.proxies","sdp"); // Protocols for Bluetooth
        properties.put("MSD.proxy.slp","org.msd.proxy.SLPManager"); // class for SLP
        properties.put("MSD.proxy.slp.res",""); // resource for SLP (none)
        properties.put("MSD.proxy.slp.time","600"); // interval for searching (seconds)
        properties.put("MSD.proxy.sdp","org.msd.proxy.SDPManager");
        properties.put("MSD.proxy.sdp.res","");
        properties.put("MSD.proxy.sdp.time","300");
    };

    public Object[][] getContents(){
        Object o[][]=new Object[properties.size()][2];
        Object v[]=properties.keySet().toArray();
        for(int i=0;i<v.length;i++){
            o[i][0]=v[i];
            o[i][1]=properties.get(v[i]);
        }
        return o;
    }

    public static void setValue(String key,String value){
        properties.put(key,value);
    }
}
