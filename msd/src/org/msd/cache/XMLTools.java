/*
 * XMLTools.java
 *
 * Created on December, 3, 2004, 13:03
 */

package org.msd.cache;

import org.w3c.dom.*;
import org.msd.cache.Cache;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.io.InputStream;
import javax.swing.tree.*;

/**
 * This class provides methos to manage XML code.
 * Storing, transforming and reading methods are included.
 * @author juanvvc
 * @date $Date: 2005-06-17 13:46:15 $
 * @version $Revision: 1.4 $
 */
public class XMLTools {
    /**
     * Save a DOM structure into a stream.
     * @param node The node to save in the stream
     * @param out The output stream to save the node
     * @throws java.lang.Exception After any error during saving
     */
    public static void saveDOMToStream(Node node, OutputStream out) throws Exception{
        // take the transformer
        TransformerFactory transFac=TransformerFactory.newInstance();
        Transformer trans=transFac.newTransformer();
        // transform and save the DOM
        trans.transform(new DOMSource(node),new StreamResult(out));
    }
    /**
     * Save a DOM structure into a file.
     * @param node The node to be saved in a file
     * @param filename The name of the this method is going to save the node
     * @throws java.lang.Exception If the node can not be saved in the file
     */
    public static void saveDOMToFile(Node node, String filename) throws Exception{
        saveDOMToStream(node,new java.io.FileOutputStream(new java.io.File(filename)));
    }

    /**
     * Save the DOM structure in a tree.
     * Show in the tree the attribute 'name'.
     * This method is no longer useful since the service browser shows the information using
     * HTML throught an XSLT transformation.
     * @return A DefaultMutableTreeNode to show in a JTree
     * @param node The XML node to be shown in a tree
     * @throws Exception if the node can not be converted to a tree.
     */
    public static DefaultMutableTreeNode saveDOMToTree(Node node) throws Exception{
        // look for the type of the node
        switch(node.getNodeType()){
            case Node.ELEMENT_NODE:
                org.w3c.dom.Element element=(org.w3c.dom.Element)node;

                // create a node for the element
                String name=element.getAttribute(Cache.NAME_NAME);
                // if it hasn't name attribute, paste the node name
                if(name==null || name.equals("")) name=element.getNodeName();
                // paste the ID
                String id=element.getAttribute(Cache.ID_NAME);
                if(id!=null && !id.equals("")) name=name+"("+id+")";
                // If it has a value, paste to the name
//                String valor=element.getNodeValue();                      //@@1.5
//                if(valor!=null && !valor.equals("")) name=name+"="+valor; //@@1.5

                // Create the node
                DefaultMutableTreeNode root=new DefaultMutableTreeNode(name);
                // call this method for every child of this node
                NodeList nodes=element.getChildNodes();
                for(int i=0; i<nodes.getLength(); i++){
                    root.add(saveDOMToTree(nodes.item(i)));
                }
                return root;
            case Node.TEXT_NODE:                                            //@@1.4
                // Create the text                                          //@@1.4
                return new DefaultMutableTreeNode(node.getNodeValue());     //@@1.4
            case Node.DOCUMENT_NODE:
                // Call with the only child of the document
                return saveDOMToTree(node.getFirstChild());
            default:
                throw new Exception("I don't know what to do with node type "+node.getNodeType());
        }
    }

    /**
     * Transforms the XML with XSLT.
     * @param xml An input stream containing the original XML code
     * @param xslt An input stream containing the XSLP code
     * @param out An output stream to write the transformed code
     * @throws java.lang.Exception If the XML code can not be transformed with the XSLT supplied
     */
    public static void transformXML(InputStream xml,InputStream xslt,OutputStream out) throws Exception{
        TransformerFactory tf=TransformerFactory.newInstance();
        Transformer t=tf.newTransformer(new StreamSource(xslt));
        t.transform(new StreamSource(xml),new StreamResult(out));
    }

    /**
     * Save a whole stream in a byte array. This method blocks until the input stream
     * is closed.
     * 
     * Although it has nothing to do with XML, actually this class is just for
     * tools. Besides, in this system likely the stream will has got a XML code...
     * 
     * Code from java.sun.com answering abut how load a resource inside a Jar from
     * an applet.
     * @param input The input stream to read the data
     * @return A byte array containing the data read from the input stream.
     * @throws java.io.IOException After any exception reading the input stream
     */
    public static byte[] toByteArray(java.io.InputStream input) throws java.io.IOException{
        int status = 0;
        final int blockSize = 4096;
        int totalBytesRead = 0;
        int blockCount = 1;
        byte[] dynamicBuffer = new byte[blockSize*blockCount];
        final byte[] buffer = new byte[blockSize];

        boolean endOfStream = false;
        while (!endOfStream) {
            int bytesRead = 0;
            if (input.available() != 0){
                // data is waiting so read as
                //much as is available
                status = input.read(buffer);
                endOfStream = (status == -1);
                if (!endOfStream) bytesRead = status;
            } else {
                // no data waiting so use the
                //one character read to block until
                // data is available or the end of the input stream is reached
                status = input.read();
                endOfStream = (status == -1);
                buffer[0] = (byte)status;
                if (!endOfStream) bytesRead = 1;
            }

            if (!endOfStream) {
                if (totalBytesRead+bytesRead > blockSize*blockCount) {
                    // expand the size of the buffer
                    blockCount++;
                    final byte[] newBuffer = new byte[blockSize*blockCount];
                    System.arraycopy(dynamicBuffer, 0, newBuffer, 0, totalBytesRead);
                    dynamicBuffer = newBuffer;
                }
                System.arraycopy(buffer, 0,dynamicBuffer, totalBytesRead, bytesRead);
                totalBytesRead += bytesRead;
            }
        } //end of while(!endOfStream)

        // make a copy of the array of the exact length
        final byte[] result = new byte[totalBytesRead];
        if (totalBytesRead != 0)
            System.arraycopy(dynamicBuffer, 0, result, 0, totalBytesRead);

        return result;
    }
}
