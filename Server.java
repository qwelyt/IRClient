import java.net.*;
import javax.net.ssl.*;
import java.io.*;
import java.util.*;

/*
 * Handles the connection to the server.
 * Reads all text that comes from the server
 * and sends all messages the users sends.
 */
class Server implements Buffer, Runnable{
	private Thread t;
	private IRClient host;
	private StringBuilder text;

	private String nick, pass, srvrName;
	private int port;
	private boolean connected=false;

	private Socket s;
	private BufferedReader in;
	private BufferedWriter out;
	private boolean SSL=false;

	private Map<String, Channel> channels;
	private Map<String, ArrayList<String>> channelUsers;

	private volatile boolean runThread;
	private boolean debug = false;

	public Server(IRClient hst, String srvr, int p, String n, String pwd){
		host = hst;
		srvrName = srvr;
		port = p;
		nick = n;
		pass = pwd;

		if(port != 6667) // This is a known wrongfull assumption. You could connect to a different port without SSL, but 6667 is standard non-ssl. So we assume that all other uses SSL.
			SSL=true;

		text = new StringBuilder();

		channels = new TreeMap<String, Channel>(String.CASE_INSENSITIVE_ORDER); // #channel == #ChAnNeL
		channelUsers = new HashMap<String, ArrayList<String>>(); // #channel:{nick1,nick2,nick3}
		
		in = null;
		out = null;

		t = new Thread(this);
		runThread = true;
		t.start();
	}

	// Try to connect to the server as well as read all it's output.
	public void run(){
		// Try to connect to server first
		try{
			if(!SSL) // No SSL
				s = new Socket(srvrName, port);
			else{ // With SSL
				SSLSocketFactory sslsf = (SSLSocketFactory)SSLSocketFactory.getDefault();
				s = (SSLSocket)sslsf.createSocket(srvrName, port);
			}
		}catch(IOException ioe){
			addText(host.format("ERROR","Could not connect to server!"));
			runThread = false;
			return;
		}

		// We should now have connected, set up in- and output streams
		try{
			in = new BufferedReader(new InputStreamReader(s.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
		}catch(IOException ioe){
			addText(host.format("ERROR","Could not get I/O working for server, aborting!"));
			runThread = false;
			try{
				s.close();
			}catch(IOException ioe2){
				addText(host.format("ERROR","Could not close connection upon I/O creation failiure!"));
			}
			return;
		}

		// Connected and I/O successfully created. Strart reading from server
		// First, make sure we actually have a valid connection
		try{
			send("NICK " + nick);
			send("USER " + nick + " * * : " + nick);

			// Check respons from server
			String line = null;
			while((line=in.readLine()) != null){
				addText(line);
				if(line.indexOf("004") >= 0){
					connected=true;
					break; // Logged in and good to go
				}
				else if(line.indexOf("443") >= 0){
					// Nick already taken. Since we are lazy, we just notify the user and disconnect from the server.
					addText(host.format("ERROR","Nickname in use. Disconnecting from server. Change nick before reconnecting!"));
					disconnect();
					return;
				}
			}
		}catch(IOException ioe){
			addText(host.format("ERROR","Could not sign in to server!"));
			disconnect();
		}


		// We are connected, have I/O, and are signed in. Time to actually read the IRC-messages!
		while(runThread){
			try{
				String line = null;
				while((line=in.readLine()) != null){
					if(debug)
						System.out.println(line);
					if(line.substring(0,4).equals("PING")){
						send("PONG " + line.substring(5)); // Respond to ping so we don't get kicked off
					}
					else{
						// It's not a ping, it's a string we need to deal with.
						// The structure fo how they are build differ, so we need to deal
						// with them in different ways.
						
						/*
						 *	String		Type		Eg. Values (, delim)
						 *	0			Prefix		:triton.dsv.su, :User!~user@host.com
						 *	1			Command		PRIVMSG, 404, 443
						 *	2			Param		Depends on the command
						 *	3			Param/Trailing	Depends on the command
						 */
						String[] arr = line.split(" ");

						/* Structure of PRIVMSG
						 * :user!user@host.com PRIVMSG #channel :message
						 *
						 * Result from split:
						 *		arr[0] == :user!user@host.com
						 *		arr[1] == PRIVMSG
						 *		arr[2] == #channel
						 *		arr[3 and beyond] ==:message
						 */
						if(arr[1].equals("PRIVMSG")){
							String[] usr = arr[0].split("!");
							StringBuilder msg = new StringBuilder();
	
							// Since we know that it's a privmsg, we can start looking at arr[3] if it's a ctcp-action or a regular message.
							String prefix="";
							if(arr[3].equals(":" + '\001' + "ACTION")) // The message is a CTCP ACTION
								for(int i=4;i<arr.length;i++){
									msg.append(prefix);
									prefix=" ";
									msg.append(arr[i]);
								}
							else						// It's a regular message
								for(int i=3;i<arr.length;i++){
									msg.append(prefix);
									prefix=" ";
									msg.append(arr[i]);
								}

							String chan = "";
							if(arr[2].substring(0,1).equals("#"))
								chan = arr[2];
							else
								chan = usr[0].substring(1);

							if(channels.containsKey(chan)){
								if(arr[3].equals(":"+'\001'+"ACTION"))
										channels.get(chan).add("ACTION", usr[0].substring(1), msg.toString().replace('\001', ' '));
								else
									channels.get(chan).add("PRIVMSG", usr[0].substring(1), msg.toString().substring(1));
							}
							else{
								host.createBuffer(srvrName, chan, usr[0].substring(1), msg.toString().substring(1));
							}
						}

						/* Structure of JOIN
						 * :user!user@host.com JOIN #channel
						 *
						 * Results from split:
						 *		arr[0] == :user!user@host.com
						 *		arr[1] == JOIN
						 *		arr[2] == #channel
						 */
						else if(arr[1].equals("JOIN")){
							String[] usr = arr[0].split("!");
							if(usr[0].substring(1).equals(nick))
								if(channels.containsKey(arr[2])){
									channels.put(arr[2], channels.remove(arr[2])); // To make sure we get the right name. Ex. If we join #channel, but the real name is #ChAnNeL, we get #ChAnNeL in our list instead of #channel.
									channels.get(arr[2]).setCorrectName(arr[2]);
								}

							if(channels.containsKey(arr[2])){
								//channels.get(arr[2]).append(" --> " + usr[0].substring(1) +"("+usr[1]+ ") has joined " + arr[2]);
								channels.get(arr[2]).add("JOIN", usr[0].substring(1), usr[1]);
								if(channelUsers.containsKey(arr[2]))
									channelUsers.get(arr[2]).add(usr[0].substring(1));
							}
							else
								addText(line);
						}

						/* Structure of PART
						 * :user!user@host.com PART #channel :message
						 *
						 * Results from split:
						 *		arr[0] == :user!user@host.com
						 *		arr[1] == PART
						 *		arr[2] == #channel
						 *		arr[3 and beyond] == :message
						 */
						else if(arr[1].equals("PART")){
							String[] usr = arr[0].split("!");
							if(channels.containsKey(arr[2])){
								StringBuilder msg = new StringBuilder();
								String prefix="";
								for(int i=3;i<arr.length;i++){
									msg.append(prefix);
									prefix=" ";
									msg.append(arr[i]);
								}
								String sMsg;
								if(msg.length() == 0)
									sMsg="";
								else
									sMsg=msg.toString().substring(1);

								channels.get(arr[2]).add("PART", usr[0].substring(1)+"!"+usr[1], sMsg);
								if(channelUsers.containsKey(arr[2])){
									channelUsers.get(arr[2]).remove(usr[0].substring(1));
									channelUsers.get(arr[2]).remove("@"+usr[0].substring(1));
									channelUsers.get(arr[2]).remove("+"+usr[0].substring(1));
								}
							}
							else
								addText(line);
						}

						/* Structure of QUIT
						 * :user!user@host.com QUIT :message
						 *
						 * Results from split:
						 *		arr[0] == :user!user@host.com
						 *		arr[1] == QUIT
						 *		arr[2 and beyond] == :message
						 */
						else if(arr[1].equals("QUIT")){
							String[] usr = arr[0].split("!");
							String usrNick = usr[0].substring(1);
							
							arr[2] = arr[2].substring(1);
							StringBuilder msg = new StringBuilder();
							String prefix="";
							for(int i=3;i<arr.length;i++){
								msg.append(prefix);
								prefix=" ";
								msg.append(arr[i]);
							}
							String sMsg;
							if(msg.length() == 0)
								sMsg="";
							else
								sMsg=msg.toString().substring(1);


							Iterator<String> it = channels.keySet().iterator();
							while(it.hasNext()) {
								String key = (String)it.next();
								if(channelUsers.containsKey(key))
									if(channelUsers.get(key).contains(usrNick)){
										//channels.get(key).append(" <-- "+ usrNick +"("+ usr[1]+ ") has quit ("+ msg +")");
										channels.get(key).add("QUIT", usr[0].substring(1)+"!"+usr[1], sMsg);
										channelUsers.get(key).remove(usrNick); // Remove old nick
									}
							}
						}

						/* Structure of NICK
						 * :oldNick!usr@host.com NICK :newNick
						 *
						 * Resuts from split:
						 *		arr[0] == :oldNick!user@host.com
						 *		arr[1] == NICK
						 *		arr[2] == :newNick
						 */
						else if(arr[1].equals("NICK")){
							String oldN = arr[0].split("!")[0].substring(1); // Get old nick
							String newN = arr[2].substring(1);
							//append(" -- " + oldN + " is now known as " + newN);

							Iterator<String> it = channels.keySet().iterator();
							while(it.hasNext()) {
								String key = (String)it.next();
								if(channelUsers.containsKey(key))
									if(channelUsers.get(key).contains(oldN)){
										//channels.get(key).append(" --- " + oldN + " is now known as " + newN);
										channels.get(key).add("NICK", oldN, newN);
										channelUsers.get(key).remove(oldN); // Remove old nick
										channelUsers.get(key).add(newN); // Add new nick
									}
							}

							if(oldN.equals(nick))
								nick = newN;
						}

						/* Structure of 332 (channel topic)
						 * :name.server.net 332 user #channel :topic
						 *
						 * Results from split:
						 *		arr[0] == :name.server.net
						 *		arr[1] == 332
						 *		arr[2] == user
						 *		arr[3] == #channel
						 *		arr[4 and beyond == :topic
						 */
						else if(arr[1].equals("332")){
							if(channels.containsKey(arr[3])){
								StringBuilder topic = new StringBuilder();
								String prefix="";
								for(int i=3;i<arr.length;i++){
									topic.append(prefix);
									prefix=" ";
									topic.append(arr[i]);
								}
								channels.get(arr[3]).add("332", arr[2], topic.toString().substring(1));
							}
							else
								addText(line);
						}

						/* Structure of 353 (nicks in channel)
						 * :name.server.net 353 user = #channel :user1 user2 user3...
						 * :name.server.net 353 user @ #channel :@user1 user2
						 *
						 * Results from split:
						 *		arr[0] == :name.server.net
						 *		arr[1] == 353
						 *		arr[2] == user
						 *		arr[3] == =/@/+ <-- Your status in channel.
						 *		arr[4] == #channel
						 *		arr[5 and beyond] == :@user1 user2 / :user1 user2
						 */
						else if(arr[1].equals("353")){
							if(channels.containsKey(arr[4])){
								StringBuilder usrs = new StringBuilder();
								arr[5] = arr[5].substring(1); // remove : from the first nick.
								for(int i=5; i<arr.length; i++)
									usrs.append(arr[i] + " ");
								addUsrsInChan(arr[4], usrs.toString().split(" "));
							}
							else
								addText(line);
						}
						/* Structure of 366 (end of 353)
						 * :name.server.net 366 user #channel :End of /NAMES list.
						 *
						 * Results from split:
						 *		arr[0] == :name.server.net
						 *		arr[1] == 366
						 *		arr[2] == user
						 *		arr[3] == #channel
						 *		arr[4 and beyond] == :End of /NAMES list.
						 */
						else if(arr[1].equals("366")){
							if(channels.containsKey(arr[3]))
								channels.get(arr[3]).add("366", arr[3], getUsrsInChan(arr[3]));
							else
								addText(line);
						}

						/* Structure of 404 (Cannot send to channel)
						 * :name.server.net 404 user #channel :Cannot send to channel
						 *
						 * Results from split:
						 *		arr[0] == :name.server.net
						 *		arr[1] == 404
						 *		arr[2] == user
						 *		arr[3] == #channel
						 *		arr[4] == :Cannot
						 *		arr[5] == send
						 *		arr[6] == to
						 *		arr[7] == channel
						 */
						else if(arr[1].equals("404")){
							StringBuilder msg = new StringBuilder();
							String prefix="";
							for(int i=4;i<arr.length;i++){
								msg.append(prefix);
								prefix=" ";
								msg.append(arr[i]);
							}

							if(channels.containsKey(arr[3]))
								channels.get(arr[3]).add("404", arr[2], msg.toString().substring(1));
							else
								addText(line);
						}

						/* Structure of MODE
						 * :user!user@host MODE #channel +o nick
						 * :user!user@host MODE #channel +m
						 *
						 * Results from split:
						 * 		arr[0] == :user!usr@host
						 * 		arr[1] == MODE
						 * 		arr[2] == #channel
						 * 		arr[3] == +o / +m / the set/removed MODE
						 *		arr[4] == nick (if any)
						 *
						 * Possible modes:
						 *  Channels
						 *  +/-
						 * 		o -- Op status
						 * 		p -- Private channel
						 * 		s -- secret channel
						 * 		i -- invite only, channel flag
						 * 		t -- Topic settable by OPs only flag
						 * 		n -- No messages to channel from outside the channel
						 * 		m -- Moderated channel
						 * 		l -- Set user limit on channel
						 * 		b -- Ban mask
						 * 		v -- Voice status
						 * 		k -- Set channel key
						 *
						 *  Users 
						 *  +/-
						 * 		i -- make user invisible
						 * 		s -- marks a user for receipt of server notices
						 * 		w -- user receives wallpos
						 * 		o -- user is op.
						 *
						 *
						 * 	But we really only care for o and v on the user. The rest we see though.
						 */
						else if(arr[1].equals("MODE")){
							String[] usr = arr[0].split("!");
							if(channels.containsKey(arr[2])){
								if(arr.length == 5){ // Something happend to a nick
									if(arr[3].substring(1).equals("o") || arr[3].substring(1).equals("v")){ // A nick got/lost op/voice
										if(arr[3].substring(0,1).equals("+")){ // Given status
											if(arr[3].substring(1).equals("o")) // OP
												changeNickStatus(arr[2], arr[4], true, "o");
											else// Voice
												changeNickStatus(arr[2], arr[4], true, "v");

											channels.get(arr[2]).add("MODE", arr[4]+"!"+usr[0].substring(1), arr[3]);
										}
										else{ // The status must have been taken.
											if(arr[3].substring(1).equals("o"))
												changeNickStatus(arr[2], arr[4], false, "o");
											else
												changeNickStatus(arr[2], arr[4], false, "v");

											channels.get(arr[2]).add("MODE", arr[4]+"!"+usr[0].substring(1), arr[3]);
										}
									}
								}
								else if(arr.length == 4){ // Something happned to a channel
									channels.get(arr[2]).add("MODE", "!"+usr[0].substring(1), arr[3]);
								}
								else{ //Don't know what happned...
									addText(line);
								}
							}
							else
								addText(line);
							
						}

						else{
							addText(line);
						}
					}
				}
			}catch(IOException ioe){
				addText(host.format("ERROR","Could not read/send from/to server!"));
			}
		}
	}


	/* SETTERS AND ADDERS */

	public void addText(String str){
		text.append(str+"\n");
	}

	public void join(String chan){
		if(connected){
			if(channels.containsKey(chan)){
				if(channels.get(chan).isConnected()){
					host.getDisplay().addBuffer(channels.get(chan));
				}
				else{
					channels.get(chan).join();
					host.getDisplay().addBuffer(channels.get(chan));
				}
			}
			else{
				Channel c;
				if(chan.substring(0,1).equals("#")) // Real channel
					c = new Channel(this, chan);
				else								// Or query?
					c = new Channel(this, chan, false);
				channels.put(chan, c);
				host.getDisplay().addBuffer(c);
			}
		}
		else{
			System.out.println("Not connected yet");
		}
	}

	public void setNick(String str){
		// Own nick change is handled in the readout from the server.
		send("NICK " + str);
	}

	public void part(Buffer b){
		if(!b.isServer() && channels.containsKey(b.getName())){
			if(((Channel)b).isChannel())
				send("PART " + b.getName());
			if(channelUsers.containsKey(b.getName()))
				channelUsers.remove(b.getName());
		}
	}

	public void remove(Buffer b){
		if(!b.isServer() && channels.containsKey(b.getName())){
			if(((Channel)b).isChannel() && ((Channel)b).isConnected())
				part(b);
			channels.remove(b.getName());
		}
	}

	public void disconnect(){
		send("QUIT");
		runThread = false;
		connected = false;

		try{
			if(out != null)
				out.close();
			if(in != null)
				in.close();
		}catch(IOException ioe){
			addText(host.format("ERROR","Could not close I/O to server!"));
		}
		try{
			if(s != null)
				s.close();
		}catch(IOException ioe){
			addText(host.format("ERROR","Could not close connection to server!"));
		}

		Iterator<String> it = channels.keySet().iterator();
		while(it.hasNext()){
			String key = (String)it.next();
			Channel c = (Channel)channels.get(key);
			c.add("QUIT", nick, " ! Connection to server closed ! ");
		}

	}

	/* GETTERS */

	public String getText(){
		return text.toString();
	}

	public String getName(){
		return srvrName;
	}
	public String getServerName(){
		return getName();
	}

	public Buffer getBuffer(){
		return this;
	}

	public Server getServer(){
		return this;
	}

	public String toString(){
		return srvrName;
	}

	public IRClient getHost(){
		return host;
	}

	public String getNickList(String channel){
		return getUsrsInChan(channel);
	}
	
	public Channel getChannel(String channel){
		if(channels.containsKey(channel))
			return channels.get(channel);
		else
			return null;
	}

	/* OTHER */

	public void send(String str){
		if(runThread){
			try{
				out.write(str+"\r\n");
				out.flush();
				
				// The text we wanted to send is now sent. Now we just hide stuff like PART, JOIN and PRIVMSG that is sent by us from showing up in the textbuffer.
				if(str.length() > 3){
					if(!str.substring(0,4).equals("PART") && !str.substring(0,4).equals("JOIN") && !str.substring(0,4).equals("NICK")){
						if(str.length() > 6)
							if(!str.substring(0,7).equals("PRIVMSG"))
								addText(str);
						else{
							String[] s = str.split(" ");
							StringBuilder msg = new StringBuilder();
							if(s[2].equals(":"+'\001' + "ACTION")) // The message is a CTCP ACTION
								for(int i=3;i<s.length; i++)
									msg.append(s[i] + " ");
							else						// It's a regular message
								for(int i=2; i<s.length; i++)
									msg.append(s[i] + " ");

							if(channels.containsKey(s[1])){
								if(s[2].equals(":"+'\001' + "ACTION"))
									channels.get(s[1]).add("ACTION", nick, msg.toString().replace('\001', ' '));
								else
									channels.get(s[1]).add("PRIVMSG", nick, msg.toString().substring(1));
							}
						}
					}
				}
				else
					addText(str);
			}catch(IOException ioe){
				addText(" ! Unable to send message : " + str);
			}
		}
	}

	private void addUsrsInChan(String chan, String[] usrs){
		if(channelUsers.containsKey(chan)){
			for(int i=0; i<usrs.length; i++)
				if(!channelUsers.get(chan).contains(usrs[i]))
					channelUsers.get(chan).add(usrs[i]);
		}
		else{
			ArrayList<String> usr = new ArrayList<String>();
			for(int i=0;i<usrs.length;i++)
				usr.add(usrs[i]);
			channelUsers.put(chan, usr);
		}
	}

	// This is a really ugly method.
	// It changes the nick in channelUsers if that nick got or lost @ or + status.
	// However, it only cares for the latest change. Eg
	// 		normal	->	@	== Shows
	// 		normal	->	+	== Shows
	// 		+		->	@	== Shows
	// 		@		->	+ 	== You lose @ and get +
	// 		Have @ and +, @ was added last and is now removed -> normal
	//
	// So it's a really bad way of doing it. But it's something.
	private void changeNickStatus(String chan, String nick, boolean given, String mode){
		// This is buggy. You can only see the latest status change.
		if(debug)
			System.out.println("changeNickStatus called with: " + chan +" "+ nick +" "+ given +" "+ mode);
		if(channelUsers.containsKey(chan)){
			if(channelUsers.get(chan).contains(nick)){
				if(given){
					if(mode.equals("o"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf(nick), "@"+nick);
					else if(mode.equals("v"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf(nick), "+"+nick);
				}
				else{
					if(mode.equals("o"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf(nick), nick);
					else if(mode.equals("v"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf(nick), nick);
				}
			}
			else if(channelUsers.get(chan).contains("@"+nick)){
				if(given){
					if(mode.equals("o"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("@"+nick), "@"+nick);
					else if(mode.equals("v"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("@"+nick), "+"+nick);
				}
				else{
					if(mode.equals("o"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("@"+nick), nick);
					else if(mode.equals("v"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("@"+nick), nick);
				}
			}
			else if(channelUsers.get(chan).contains("+"+nick)){
				if(given){
					if(mode.equals("o"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("+"+nick), "@"+nick);
					else if(mode.equals("v"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("+"+nick), "+"+nick);
				}
				else{
					if(mode.equals("o"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("+"+nick), nick);
					else if(mode.equals("v"))
						channelUsers.get(chan).set(channelUsers.get(chan).indexOf("+"+nick), nick);
				}
			}
		}
	}

	// Get all the users that is in a channel and sort them from most power to least power.
	// Otherwise in descending order (A first, Z last).
	private String getUsrsInChan(String chan){
		if(channelUsers.containsKey(chan)){
			// Got all users in channel
			ArrayList<String> all = channelUsers.get(chan);
			
			// Split them up into OPs, Voiced, and regular users
			ArrayList<String> ops = new ArrayList<String>();
			ArrayList<String> voice = new ArrayList<String>();
			ArrayList<String> normal = new ArrayList<String>();

			for(int i=0; i<all.size(); i++){
				if(all.get(i).substring(0,1).equals("@"))
					ops.add(all.get(i));
				else if(all.get(i).substring(0,1).equals("+"))
					voice.add(all.get(i));
				else
					normal.add(all.get(i));
			}
			// Sort them
			Collections.sort(ops, String.CASE_INSENSITIVE_ORDER);
			Collections.sort(voice, String.CASE_INSENSITIVE_ORDER);
			Collections.sort(normal, String.CASE_INSENSITIVE_ORDER);

			// And then add them to the nick-string. This is the easiest way to sort them as different locals will have different value for the @ and + sign and could place them last in the list. But we want them first as they have the most power.
			StringBuilder sb = new StringBuilder();
			for(String s : ops)
				sb.append(s + " ");
			for(String s : voice)
				sb.append(s + " ");
			for(String s : normal)
				sb.append(s + " ");

			return sb.toString();
		}
		else
			return "";
	}

	public boolean isServer(){
		return true;
	}

	public boolean isConnected(){
		return connected;
	}
}
