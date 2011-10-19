 package org.msd.election;

import iaik.asn1.*;
import java.math.BigInteger;
import iaik.x509.attr.AttributeCertificate;
import iaik.asn1.structures.*;
import java.security.*;
import java.io.*;
import iaik.utils.*;
import java.security.cert.*;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class AttributesManager {
    public AttributesManager() {
        try {
            jbInit();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private InputStream ie = null;
    private AttributeCertificate ac = null;
    private String pathDIR = System.getProperty("user.home");
    private String fileSeparator = System.getProperty("file.separator");


    /**
     *
     * @param pathAC String
     * @throws Exception
     */
    public AttributesManager(String pathAC, String pathWorking) throws Exception{
        init(pathWorking);
        if(pathAC != null)
        {
            ie = new FileInputStream(pathAC);
            ac = new AttributeCertificate(ie);
        }
    }

    /**
     *
     * @param derAC byte[]
     * @throws Exception
     */
    public AttributesManager(byte[] derAC, String pathWorking) throws Exception{
        init(pathWorking);
        ac = new AttributeCertificate(derAC);
    }

    /**
     *
     */
    private void init(String pathWorking)
    {
        if(pathWorking != null)
        {
            pathDIR = null;
            pathDIR = pathWorking;
        }

        ie = null;
        ac = null;
    }

    /**
     *
     * @param oid String
     * @return boolean
     */
    public boolean removeAttribute(String oid)
    {
        if (ac.removeAttribute(new ObjectID(oid)) != null){return true;}
        else{return true;}
    }

    /**
     *
     */
    public void removeAllAttributes()
    {
        ac.removeAllAttributes();
    }

    /**
     *
     * @param oid String
     * @param name String
     * @param shortName String
     * @param objAttribute Object
     * @return boolean
     */
    public boolean putAttribute(String oid, String name, String shortName, Object objAttribute)
    {
            ASN1Object asn1Attr = null;

            String nameObjet = objAttribute.getClass().getName();

            //System.out.println("Put Attribute : nameObject ---> "+nameObjet);

            if(nameObjet.equals("java.lang.Integer"))
            {
                asn1Attr = new INTEGER(((Integer)objAttribute).intValue());
            }
            else if(nameObjet.equals("java.lang.Boolean"))
            {
                asn1Attr = new BOOLEAN(((Boolean)objAttribute).booleanValue());
            }
            else if(nameObjet.equals("java.lang.String"))
            {
                asn1Attr = new OCTET_STRING(((String) objAttribute).getBytes());
            }
            else
            {
                return false;
            }
            ObjectID oidAttr = createObjectID(oid, name, shortName);

            //System.out.println("Put Attribute : oid ---> "+oidAttr.toString());

            ASN1Object[] asn1Obj = new ASN1Object[1];
            asn1Obj[0] = asn1Attr;

            Attribute newAttr = new Attribute(oidAttr,asn1Obj);
            ac.addAttribute(newAttr);

            return true;
    }


    /**
     *
     * @param objId String
     * @param name String
     * @param shortName String
     * @return ObjectID
     */
    private ObjectID createObjectID(String objId, String name, String shortName) {

        ObjectID oid = null;

        if(objId != null)
        {
            if(name != null)
            {
                if (shortName != null)
                {
                    oid = new ObjectID(objId, name, shortName);
                }
                else{oid = new ObjectID(objId, name);}
            }
            else{oid = new ObjectID(objId);};
        }
        else{return null;}

        return oid;
    }


    /**
     *
     * @param objId String
     * @return Object
     */
    public Object getAttribute(String objId, String name, String shortName)
    {
        Object objAttribute = null;
        ObjectID oid = createObjectID(objId, name, shortName);

        Attribute attribute = ac.getAttribute(oid);
        if(attribute != null)
        {
            ASN1Object asn1Obj = (attribute.getValue())[0];
            ASN asn = asn1Obj.getAsnType();

            //System.out.println("ASN type: "+asn.toString());

            if(asn.equals(ASN.INTEGER))
            {
                objAttribute = new Integer(((BigInteger)((INTEGER)asn1Obj).getValue()).intValue());
            }
            else if(asn.equals(ASN.BOOLEAN))
            {
                objAttribute = new Boolean(((Boolean)((BOOLEAN)asn1Obj).getValue()).booleanValue());
            }
            else if(asn.equals(ASN.OCTET_STRING))
            {
                byte[] arrayBytes = (byte[])((OCTET_STRING) asn1Obj).getValue();
                objAttribute = arrayBytes;
            }
            else
            {
                objAttribute = asn1Obj;
            }
            return objAttribute;
        }
        else {return null;}
    }

    /**
     *
     * @return byte[]
     */
    public byte[] getAC(){return ac.toByteArray();}

    /**
     *
     * @param path String
     * @throws FileNotFoundException
     * @throws IOException
     */
    public boolean saveACas(String path) throws FileNotFoundException,
            IOException
    {
        try
        {
            OutputStream os = new FileOutputStream(path);
            KeyAndCertificate autoridadKC = new KeyAndCertificate(pathDIR+fileSeparator+"auxkac");
            AlgorithmID algoritmo = new AlgorithmID("1.2.840.113549.1.1.5","id-sha1-with-rsa-Signature");
            ac.sign(algoritmo,autoridadKC.getPrivateKey());

            ac.writeTo(os);
            return true;
        }catch(CertificateException ce){
		ce.printStackTrace();
	}
        catch(InvalidKeyException ie){
		ie.printStackTrace();
	}
        catch(NoSuchAlgorithmException nsae){
		nsae.printStackTrace();
	}
        return false;
    }

    private void jbInit() throws Exception {
    }

}
