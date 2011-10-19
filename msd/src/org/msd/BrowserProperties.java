/*
 * BrowserProperties.java
 *
 * Created on December, 29. 2004, 13:02
 */

package org.msd;
import java.util.ResourceBundle;
import org.apache.log4j.*; //@@l

/** Save the initial configuration parameters of the system.
 *
 * Initial parameters are read from the configuration file, then command line
 * overwriting the configured parameters.
 *
 * We name 'parameter' to the ones in command line and 'properties' to the one
 * in configuration file. Actually, they are the same. In command line, the
 * parameters of type boolean are set with + and unset with -. So -gui does NOT
 * start the graphical interface, and +gui does.
 *<table>
 *<tr><th>Parameter</th><th>Property</th><th>Defecto</th><</tr>
 *<tr><td>-log4j file</td><td>Log4J=String</td><td>log4j.conf</td></tr>
 *<tr><td>-msdconf conf</td><td>String</td><td></td><td>conf.msd</td></tr>
 *<tr><td>-cache file</td><td>String</td><td>conf.cache</td></tr>
 *<tr><td>-local file</td><td>LocalFile=String</td><td>null</td></tr>
 *<tr><td>-gui</td><td>GUI=boolean</td><td>true</td></tr>
 *</table>
 *
 * @version version $Revision: 1.8 $ $Date: 2005-09-13 14:26:03 $ */
class BrowserProperties {
    /** Init the properties from command file.
     * @param args Command lne arguments, as passed to a main method
     * @param globalfile Route to load the default configuration, as a
     * path format wished by java.util.ResourceBundle. If null, does
     * not use a defualt configuration. Ensure the command line
     * provided every needed parameter.*/
    public static void initProperties(String args[], String globalfile){
        debug=true; //@@l
        // read properties from a global file
        if(globalfile!=null){
            readProperties(globalfile);
        }
        if(debug) System.out.println("Reading properties from command line");
        //read properties from command line
        try{
            for(int i=0; i<args.length; i++){
                if(args[i].equals("-log4j")){
                    log4jConf=args[i+1];
                    i=i+1;
                } else if(args[i].equals("-local")){
                    localFile=args[i+1]; i++;
                } else if(args[i].equals("-cache")){
                    cache=args[i++];
                } else if(args[i].equals("-msdconf")){
                    msdconf=args[i++];
                } else if(args[i].equals("-file")){
                    readProperties(args[i+1]);
                    i++;
                } else if(args[i].equals("-gui")){
                    gui=false;
                } else if(args[i].equals("+gui")){
                    gui=true;
                } else if(args[i].equals("+conf")){
                    graphicalconf=true;
                } else if(args[i].equals("-conf")){
                    graphicalconf=false;
                }
            }
        } catch(Exception e){
            // If you found any error, get out
            System.err.println("Proxy couldn't start: wrong parameters.");
            System.exit(1);
        }

        // Read the conf from a file
        try{
            PropertyConfigurator.configure(log4jConf); //@@l
        } catch(Throwable e){
            // If you get an exception or error, use the basic conf
            // Some explanation is required: if Log4j doesn't find it's
            // conf file, or doesn't get a proper server for sending the
            // logging to, throws an error, not an exception.
            BasicConfigurator.configure(); //@@l
        }

        // Print the conf, if we have to
        if(debug){
            String cadena="";
            cadena+="Log4J="+log4jConf;
            cadena+="\nLocalFile="+localFile;
            cadena+="\nCache="+cache;
            cadena+="\nMSDConf="+msdconf;
            cadena+="\nGUI="+gui;
            System.out.println(cadena);
        }
    }

    /** Reads properties from a external configuration file.
     * @param file File to read the properties from. Actually it is
     * a path as expected by ResourceBundle: it can be a classname or
     * a special path to a properties file. Read ResourceBundle documentation.
     *
     * Example: "conf.slp" read file "conf/slp.properties". */
    private static void readProperties(String file){
        if(debug) System.out.println("Reading properties from "+file);
        try{
            ResourceBundle res=ResourceBundle.getBundle(file);
            log4jConf=res.getString("Log4J");
            localFile=res.getString("LocalFile");
            gui=Boolean.valueOf(res.getString("GUI")).booleanValue();
            cache=res.getString("Cache");
            cachexml=res.getString("CacheXML");
            msdconf=res.getString("MSDConf");

        } catch(Exception e){
            System.err.println("Error while reading properties: "+e.toString());
        }
    }

    /** Name of Log4J conf file */
    public static String log4jConf="log4j.conf";

    /** Filename to save the cache. If null, do not save. */
    public static String localFile=null;

    /** Wether or not use a graphical interface */
    public static boolean gui=true;

    /** Wether or not debugging this class.
     * Debugging are to the default output: we are in charge of
     * starting Log4J system! */
    public static boolean debug=false;

    /** Gets the initial cache from this file */
    public static String cache="conf.cache";

    /** If cache is empty, load cache from this string */
    public static String cachexml="";

    /** Gets the parameters of the MSDManager from this file */
    public static String msdconf="conf.msd";

    /** Start a graphical configuration of the properties of the system */
    public static boolean graphicalconf=false;
}
