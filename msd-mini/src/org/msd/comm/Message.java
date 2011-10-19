package org.msd.comm;

import java.util.Hashtable;

/** Message to be sent or received through a network or connection.
 * @version $Revision: 1.7 $ */
public class Message{
    /** Message sent or received */
    private byte[] msg;

    /** The attributes of the message */
    private Hashtable attributes;

    public static final int UNREGISTERED=0;
    public static final int MAIN_REQUEST=1;
    public static final int MAIN_REPLY=2;
    public static final int KEY=3;
    public static final int I_AM_HERE=8;
    public static final int CONN=9;
    public static final int UPDATE=10;
    public static final int ERROR=11;
    public static final int CLOSE=12;
    public static final int USE=13;
    public static final int LEFT=14;
    public static final int CREDENTIAL=15;

    public Message(){
        attributes=new Hashtable();
        this.setHops(3);
    }

    /** Constructor.
     * @param data Data to be send.
     * @param from Identifier of the MSD sending the message.
     * @param to Identifier of the MSD receiving the message. null means
     * multicast.
     * @param type Type of message */
    public Message(byte[] data,String from,String to,int type){
        attributes=new Hashtable();
        this.setHops(3);
        setData(data);
        setIDFrom(from);
        setIDTo(to);
        setType(type);
    }

    /** @return A hashtable with the keys the name of the attributes and
     * the values the value of the atribute.
     */
    public Hashtable getAttributes(){
        return this.attributes;
    }

    /** @param name Name of the attribute to set. The name must be a lower
     * case String without puntuation.
     * @param value Value of the attribute. If null, n set the attribute.
     */
    public void setAttribute(String name, String value){
        if(value==null)
            attributes.remove(name);
        else
            attributes.put(name,value);
    }

    /** @param name The name of the attribute to return
     * @return The value of the attribute, or null if not set.
     */
    public String getAttribute(String name){
        return (String)attributes.get(name);
    }

    public byte[] getData(){
        if(msg==null){
            return new byte[0];
        }
        return msg;
    }

    public void setData(byte[] d){
        msg=d;
    }

    public void setIDFrom(String f){
        setAttribute("from",f);
    }

    public String getIDFrom(){
        return getAttribute("from");
    }

    /** @param t The identifier of the recipient. If the identifier is empty,
     * suppose it's a multicast communication and set IDTo==null */
    public void setIDTo(String t){
        setAttribute("to",t);
    }

    public String getIDTo(){
        return getAttribute("to");
    }

    public void setType(int t){
        setAttribute("type",""+t);
    }

    public int getType(){
        try{
            return Integer.parseInt(getAttribute("type"));
        } catch(Exception e){
            return Message.UNREGISTERED;
        }
    }

    public void setHops(int h){
        setAttribute("hops",""+h);
    }

    public int getHops(){
        try{
            return Integer.parseInt(getAttribute("hops"));            
        } catch(Exception e){
            return -1;
        }
    }

    public String getVirtual(){
        return getAttribute("virtual");
    }

    public void setVirtual(String v){
        setAttribute("virtual",v);
    }

    public boolean getEncode(){
        String e=getAttribute("encoded");
        return e!=null&&e.equals("true");
    }

    public void setEncode(boolean enc){
        setAttribute("encoded",enc?"true":"false");
    }

    /** Set the hash code of the content of the message. The hash code is
     * provided by the security methods of CommManager and can be a complex
     * algorithm as MD5 or a simple check sume.
     *
     * The hashcode must be printable.
     * @param b The hash code of the content of the message. */
    public void setHashCode(byte[] b){
        setAttribute("hash",new String(b));
    }
    /** @return The declared hash code of this message. Note this class does
     * not calculate any hash code, but uses the one provided with setHashCode */
    public byte[] getHashCode(){
        String h=getAttribute("hash");
        if(h==null)
            return new byte[0];
        else
            return h.getBytes();
    }
}

