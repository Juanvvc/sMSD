/* Dialogo.java.  Created on 21 de noviembre de 2004, 20:05 a partir de JavSolDialogo */

package org.msd;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** This class implements a dialog
 *
 *This class makes easier the modal dialogues construction with one or two buttons
 *(ok and cancel) Is thought for be able to notice if is an applet or not, and it
 * could show a Panel or a Dialog, but for the time being is just a dialogue.
 *
 * @author Juan Vera del Campo el 12/Ab/03
 * @date $Date: 2005-06-10 10:48:24 $
 * @version $Revision: 1.4 $ */

public class Dialog implements ActionListener, WindowListener{
//	private boolean pulsado=false;
	private boolean pulsadook=false;
	private JButton ok=null;
	private JButton cancel=null;
	private JDialog dialogo=null;

	/** A single Ok button choice */
	public static final int OKONLY=0;
	/** Two buttons choice: Ok and Cancel */
	public static final int OKCANCEL=1;

	/** String of Ok button */
	public static String okString="Aceptar";
	/** String of cancel button */
	public static String cancelString="Cancelar";

	private Dialog(){}
	/** Constructor
	 *@param title Dialogue title
	 *@param comp JComponent to show
	 *@param op Options (OKONLY [by default] or OKCANCEL) */
	public Dialog(String title, JComponent comp, int op){
		//Make the frame
		JFrame frame=new JFrame(title);
		frame.addWindowListener(this);
		//Make the modal dialogue
		dialogo=new JDialog(frame, true);
		dialogo.setTitle(title);
		dialogo.getContentPane().setLayout(new BorderLayout());
		dialogo.getContentPane().add(comp,BorderLayout.CENTER);
		//Make buttons
		JPanel p=new JPanel();
		ok=new JButton(okString);
		ok.addActionListener(this);
		p.add(ok);
		if(op==OKCANCEL){
			cancel=new JButton(cancelString);
			cancel.addActionListener(this);
			p.add(cancel);
		}
		dialogo.getContentPane().add(p,BorderLayout.SOUTH);
		//finalize centering the window
		dialogo.pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		dialogo.setLocation(screenSize.width/2-dialogo.getSize().width/2,
			screenSize.height/2-dialogo.getSize().height/2);
		dialogo.setVisible(true);
	}

	/** @return Return true when a button that finishes the dialogue has been clicked
	 *
	 *Was thought for a Panel use, is useless like dialogue modal */
//	public boolean finished(){ return true; }

	/** @return Return if the dialogue has been finished by an Ok click */
	public boolean ok(){ return pulsadook; }

	/** Turn out and close */
	public void finalize() throws Throwable{
		dialogo.setVisible(false);
		dialogo.dispose();
		dialogo=null;
                super.finalize();
	}

	/** Event manager.
         * @param e An event from the buttons. */
	public void actionPerformed(ActionEvent e){
		if(e.getSource()==ok) pulsadook=true;
                try{
                    finalize();
                }catch(Throwable t){

                }
	}
	public void windowClosing(WindowEvent e){
            try{
                finalize();
            }catch(Throwable t){

            }
        }
	public void windowActivated(WindowEvent e){}
	public void windowIconified(WindowEvent e){}
	public void windowDeactivated(WindowEvent e){}
	public void windowDeiconified(WindowEvent e){}
	public void windowOpened(WindowEvent e){}
	public void windowClosed(WindowEvent e){}
}
