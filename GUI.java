import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/*
 * A GUI for the IRClient.
 * Creates a graphical inteface that the user can act through.
 * No menus have been provided.
 */
class GUI extends JFrame implements UI, Runnable{
	private IRClient host;
	private Commands commands;
	private Thread t;

	private JList<Buffer> bufferList;
	private DefaultListModel<Buffer> buffers;
	private Buffer main;

	private JList<String> nickList;
	private DefaultListModel<String> nicks;
	private JScrollPane spN;
	private JPanel panel;

	private JTextField input;
	private JTextArea txt;

	public GUI(IRClient hst){
		host = hst;
		commands = new Commands(host, this);
		t = new Thread(this);

		setTitle("IRClient GUI");

		// { Buffers
		buffers = new DefaultListModel<Buffer>();
		bufferList = new JList<Buffer>(buffers);
		bufferList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		// Buffers }

		// { Set up a main-buffer
		main = new Channel(host, null, "IRClient");
		main.send("Thanks for using IRClient.\nConnect to a server and join a channel to start chatting!\n");
		buffers.add(0,main);
		bufferList.setSelectedValue(main, true);
		// Set up a main-buffer }

		// { Create GUI
		bufferList.addListSelectionListener(new BufferSelection());
		bufferList.setLayoutOrientation(JList.VERTICAL);
		JScrollPane spB = new JScrollPane(bufferList);
		add("West", spB);

		panel = new JPanel();
		panel.setLayout(new BorderLayout());

		txt = new JTextArea();
		txt.setLineWrap(true);
		txt.setWrapStyleWord(true);
		txt.setEditable(false);
		JScrollPane spT = new JScrollPane(txt);
		panel.add(spT, BorderLayout.CENTER);

		input = new JTextField();
		input.addActionListener(new SendText());
		panel.add(input, BorderLayout.PAGE_END);

		nicks = new DefaultListModel<String>();
		nickList = new JList<String>(nicks);
		nickList.setLayoutOrientation(JList.VERTICAL);
		spN = new JScrollPane(nickList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		add("Center", panel);


		setDefaultCloseOperation(EXIT_ON_CLOSE);
		pack();
		setLocationRelativeTo(null);
		setSize(600,400);
		setVisible(true);
		t.start();

		input.requestFocusInWindow();
	}

	// Make sure to update the GUI as the buffers 
	// contents change and the user switches buffer.
	public void run(){
		String oldTxt = txt.getText();
		String oldNicks = "";
		while(true){
			try{
				Buffer b = (Buffer)bufferList.getSelectedValue();
				int bi = bufferList.getSelectedIndex();
				String newTxt = b.getText();
				
				if(!oldTxt.equals(newTxt)){
					oldTxt = newTxt;
					txt.setText(newTxt);
					txt.setCaretPosition(txt.getText().length());
				}
				if(!b.isServer() && bi != 0){
					String newNicks = ((Channel)b).getNickList();
					if(!oldNicks.equals(newNicks)){
						oldNicks = newNicks;
						nicks.clear();
						String[] n = newNicks.split(" ");
						for(int i=0; i<n.length; i++)
							nicks.addElement(n[i]);
						panel.add(spN, BorderLayout.LINE_END);
						getContentPane().revalidate();
					}
				}
				else{
					nicks.clear();
					oldNicks = "";
					panel.remove(spN);
					getContentPane().revalidate();
				}
			}catch(NullPointerException npe){
				System.out.println("Could not get text from buffer.");
			}

			// To use less resources
			try{
				t.sleep(300);
			}catch(InterruptedException ie){}
		}
	}

	public void quit(){
		dispose();
		System.exit(0);
	}

	public void append(String str){
		main.addText(str);
	}

	public void addBuffer(Buffer b){
		if(!buffers.contains(b))
			buffers.addElement(b);
		if(b.isServer())
			setSelectedBuffer(b);
		else
			if(((Channel)b).isChannel())
				setSelectedBuffer(b);
		getContentPane().revalidate();
	}

	public void setSelectedBuffer(Buffer b){
		bufferList.setSelectedValue(b, true);
	}
	public void setSelectedBuffer(int i){
		bufferList.setSelectedIndex(i);
	}

	public void removeBuffer(Buffer b){
		if(buffers.contains(b))
			buffers.removeElement(b);
		getContentPane().revalidate();
	}

	class BufferSelection implements ListSelectionListener{
		public void valueChanged(ListSelectionEvent lse){
			Buffer b = (Buffer)bufferList.getSelectedValue();
			txt.setText(b.getText());
			txt.setCaretPosition(txt.getText().length());
		}
	}

	class SendText implements ActionListener{
		public void actionPerformed(ActionEvent ae){
			if(ae.getSource() == input){
				String str = input.getText();
				if(!str.equals("")){
					Buffer b = (Buffer)bufferList.getSelectedValue();
					int i = bufferList.getSelectedIndex();

					commands.process(b,i,str);
					input.setText("");
				}
			}
		}
	}
}
