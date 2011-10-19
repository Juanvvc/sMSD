package org.msd;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * This is an example of a service. It does not
 * use the MSD library at all. It works as a virtual printer, accepting
 * remote connections and showing the image on the screen. The service must
 * be described using any of the location protocols.
 */
public class ImageClient3 extends Frame implements Runnable{
    /**
     * The server socket to listen to remote connections
     */
    private ServerSocket s=null;
    /**
     * The canvas to show the image
     */
    private ImageViewer image=null;
    /**
     * Creates a new client.
     * @param p The port to listen to remote connections.
     * @throws java.lang.Exception If the server couldn't start
     */
    public ImageClient3(int p) throws Exception{
        super("Virtual Printer "+p);
        s=new ServerSocket(p);
        Thread t=new Thread(this);
        t.start();
        
        image=new ImageViewer();
        add(image,BorderLayout.CENTER);
        Button b=new Button("Exit");
        b.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                System.exit(0);
            }
        });
        add(b,BorderLayout.SOUTH);
        setVisible(true);        
    }

    /**
     * The main method to run as static application
     * @param args The first one os the port to listen to connections.
     * If not provided, use 15153
     */
    public static void main(String[] args){
        int p=15153;
        try{
            p=Integer.parseInt(args[0]);
        }catch(Exception e){
            
        }
        try{
            new ImageClient3(p);
        }catch(Exception ex){
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Waits for remote connections, read the stream and show the image on the screen.
     */
    public void run(){
        while(true){
            try{
                Socket so=s.accept();
                byte[] b=toByteArray(so.getInputStream());
     
                // construct the image
                Image im=Toolkit.getDefaultToolkit().createImage(b);
                // show it
                image.setImage(im);
                image.repaint();
            }catch(Exception e){
                
            }
        }
    }

    /** This class shows an image. */
    private class ImageViewer extends Canvas{
        private Image image=null;
        public ImageViewer(){
            setSize(new Dimension(160,160));
        }

        private void setImage(Image im){
            image=im;
        }
        private Image getImage(){
            return image;
        }

        public void update(Graphics g){
            paint(g);
        }

        public void paint(Graphics g){
            if(image!=null){
                g.drawImage(image,0,0,this);
            }
        }
    }
    
    /**
     * Converts an InputStream into an array of bytes. This code is from Sun webpage.
     * @param input The input stream to be read
     * @return The byte array of the stream
     * @throws java.io.IOException If the input stream couldn't be converted
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

