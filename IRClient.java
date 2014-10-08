import java.util.*;
import java.text.*;
import java.io.*;

/*
 * The starter of the application.
 * This is the actual host of the system. The UI displays it
 * and the server handles it's connection. But This is the core.
 */
public class IRClient implements Runnable{
	private UI display;
	private HashMap<String, Server> servers;
	private HashMap<String, String> settings;
	private ArrayList<String> settingsItems;
	private HashMap<String, String> fixes;
	private Thread t;

	private String defaultNick = System.getProperty("user.name");
	private String defaultPass = null;

	public static void main(String[] args){
		new IRClient(args);
	}

	public IRClient(String[] args){
		servers = new HashMap<String, Server>();
		settings = new HashMap<String, String>();
		settings = new HashMap<String, String>();
		settingsItems = new ArrayList<String>();
		fixes = new HashMap<String, String>();
		t = new Thread(this);
		if(args.length > 0){
			if(args[0].equals("g"))
				display = new GUI(this);
			else if(args[0].equals("c"))
				display = new TUI(this);
			else
				display = new GUI(this);
		}
		else{
			display = new GUI(this);
		}
		readConfig();
	}
	public void run(){}

	// Reads in the entire config so we can act upon it.
	// Do *NOT* apply the settings here. If a user puts that
	// she wants to join a channel on a server she has yet to define, it
	// will not work. But if we read everything first and THEN apply it, it is possible.
	private void readConfig(){
		BufferedReader in = null;
		try{
			in= new BufferedReader(new FileReader("IRClient.conf"));
			if(in != null){
				String line;
				while((line=in.readLine()) != null){
					if(line.contains("=")){
						String[] splt = line.split("=");
						settingsItems.add(splt[0]);
						if(!isEmpty(splt[0])){
							if(splt.length > 1){
								if(splt[0].equals("connect") && settings.containsKey("connect")){
									ArrayList<String> srvrs = new ArrayList<String>(Arrays.asList(settings.get("connect").split(",")));
									if(!srvrs.contains(splt[1]))
										srvrs.add(splt[1]);
									StringBuilder sb = new StringBuilder();
									String prefix="";
									for(String s : srvrs){
									   sb.append(prefix);
									   prefix=",";
									   sb.append(s);
									}
									settings.put("connect", sb.toString());

									System.out.println(srvrs);
								}
								else
									settings.put(splt[0], splt[1]);
							}
							else
								settings.put(splt[0], "");
						}
					}
					else
						settingsItems.add(line);
				}
			}
		}catch(IOException ioe){}
		finally{
			try{
				in.close();
			}catch(Exception e){}
		}
		setFixes();
		applySettings();
	}

	// Applies the settings from the config-file.
	// Set nick first, then servers, then channels.
	private void applySettings(){
		try{
			if(settings.containsKey("nick"))
				if(!settings.get("nick").trim().equals(""))
					defaultNick = settings.get("nick").trim();

			if(settings.containsKey("connect")){ // Checks if we should connect to some server
				String[] srvrs = settings.get("connect").split(","); // Split the string containing the servers
				ArrayList<String> server = new ArrayList<String>(); // check what servers we get. Since we want to be sure that we only get valid servers, we use this instead of the srvrs (which might contain wrongfully formated servers).
				for(int i=0; i<srvrs.length; i++){ // Iterate over teh servers and connect. Check if anyone has a port specified.
					String[] s = srvrs[i].split(":");
					if(s.length == 1){
						connect(s[0]);
						server.add(s[0]); // Add to the servers we have connected to.
					}
					else if(s.length == 2)
						if(isInteger(s[1])){
							connect(s[0], Integer.parseInt(s[1]));
							server.add(s[0]); // We only care about the name.
						}
				}
				for(int i=0; i<server.size(); i++){ // Here we want to make sure we join all the channels we specified for the servers.
					if(settings.containsKey(server.get(i))){ // First, check if the settings contains any of the servers we have specified channels for.
						String[] channels = settings.get(server.get(i)).split(","); // Split the channels up.
						for(int j=0; j<channels.length; j++){ // Start iterating over the channels.
							if(!isEmpty(channels[j])){
								int attempts=0; // To prevent us from going into an infinite loop.
								while(true){ // We use a while-loop so we can actually join. No point in trying to connect to a channel on a server we are not connected on.
									if(servers.get(server.get(i)).isConnected()){ // When connected, join the channel.
										servers.get(server.get(i)).join(channels[j]);
										break;
									}
									else if(attempts > 9){
										display.append(format("ERROR", "Could not join channel { " + channels[j] +" } on server { " + server.get(i) + " } as the server did not connect within 10 seconds."));
										break;
									}
									else{
										try{
											t.sleep(1000);
										}catch(InterruptedException ie){}
									}
									attempts++;
								}
							}
						}
					}

				}
			}
		}catch(NullPointerException npe){}
	}

	public void saveConfig(){
		BufferedWriter out = null;
		try{
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("IRClient.conf"), "utf-8"));
			if(out != null){
				out.write(getSettings());
			}
		}catch(IOException ioe){
			display.append("Could not save settings.");
		}
		finally{
			try{
				out.close();
			}catch(Exception e){}
		}
	}

	/* SETTERS AND ADDERS */

	public void setNick(String newNick){
		defaultNick = newNick;
		settings.put("nick", newNick);
		display.append("Default nick set to: "+ newNick);
	}

	public void createBuffer(String srvr, String channel, String user, String msg){
		if(servers.containsKey(srvr)){
			Server s = servers.get(srvr);
			s.join(channel);
			s.getChannel(channel).add("PRIVMSG", user, msg);
		}
	}

	public void connect(String server){
		connect(server, 6667);
	}
	public void connect(String server, int port){
		Server s;
		if(servers.containsKey(server))
			s = servers.get(server);
		else{
			s = new Server(this, server, port, defaultNick, defaultPass);
			servers.put(server, s);
		}
		display.addBuffer((Buffer)s);
	}


	/* REMOVERS */
	public void removeBuffer(Buffer b){
		if(b.isServer()){
			if(servers.containsKey(b.getName())){
				((Server)b).disconnect();
				servers.remove(b.getName());
			}
		}
		else if(!b.isServer()){
			if(servers.containsKey(b.getServerName())){
				Server s = b.getServer();
				s.remove(b);
			}
		}
		display.removeBuffer(b);
	}


	/* GETTERS */

	public UI getDisplay(){
		return display;
	}

	public String getSettings(){
		StringBuilder ret = new StringBuilder();
		boolean nickSet=false;
		if(settingsItems.size() == 0){
			ret.append("[Nick]\nnick="+defaultNick);
			nickSet=true;
		}
		else
			for(int  i=0; i<settingsItems.size(); i++){
				if(settings.containsKey(settingsItems.get(i))){
					ret.append(settingsItems.get(i) + "=" + settings.get(settingsItems.get(i)) + "\n");
					if(settingsItems.get(i).equals("nick"))
						nickSet=true;
				}
				else
					ret.append(settingsItems.get(i)+"\n");
			}
		if(!nickSet)
			ret.insert(0, "[Nick]\nnick="+defaultNick);
		return ret.toString();
	}

	public String getHelp(){
		BufferedReader in = null;
		StringBuilder sb = new StringBuilder();
		try{
			in= new BufferedReader(new FileReader("IRClient.help"));
			if(in != null){
				String line;
				while((line=in.readLine()) != null){
					sb.append(line + "\n");
				}
			}
		}catch(IOException ioe){}
		finally{
			try{
				in.close();
			}catch(Exception e){}
		}

		return sb.toString();
	}


	/* OTHER */
	// Checks if the config contained the keyword and gives the
	// result of it back.
	private String getFix(String key){
		if(settings.containsKey(key)){
			//System.out.println(settings.get(key));
			return settings.get(key);
		}
		else
			return null;
	}

	// Sets the prefix and postfix for the defined options.
	private void setFixes(){
		if(getFix("nick_prefix") != null)
			fixes.put("nPre", getFix("nick_prefix"));
		else
			fixes.put("nPre", "");
		if(getFix("nick_postfix") != null)
			fixes.put("nPos", getFix("nick_postfix"));
		else
			fixes.put("nPos", "");

		if(getFix("action_prefix") != null)
			fixes.put("aPre", getFix("action_prefix"));
		else
			fixes.put("aPre", "");
		if(getFix("action_postfix") != null)
			fixes.put("aPos", getFix("action_postfix"));
		else
			fixes.put("aPos", "");

		if(getFix("error_prefix") != null)
			fixes.put("ePre", getFix("error_prefix"));
		else
			fixes.put("ePre", "");
		if(getFix("error_postfix") != null)
			fixes.put("ePos", getFix("error_postfix"));
		else
			fixes.put("ePos", "");

		if(getFix("join_prefix") != null)
			fixes.put("jPre", getFix("join_prefix"));
		else
			fixes.put("jPre", "");
		if(getFix("join_postfix") != null)
			fixes.put("jPos", getFix("join_postfix"));
		else
			fixes.put("jPos", "");

		if(getFix("quit_prefix") != null)
			fixes.put("qPre", getFix("quit_prefix"));
		else
			fixes.put("qPre", "");
		if(getFix("quit_postfix") != null)
			fixes.put("qPos", getFix("quit_postfix"));
		else
			fixes.put("qPos", "");

		if(getFix("part_prefix") != null)
			fixes.put("pPre", getFix("part_prefix"));
		else
			fixes.put("pPre", "");
		if(getFix("part_postfix") != null)
			fixes.put("pPos", getFix("part_postfix"));
		else
			fixes.put("pPos", "");

		if(getFix("network_prefix") != null)
			fixes.put("netPre", getFix("network_prefix"));
		else
			fixes.put("netPre", "");
		if(getFix("network_postfix") != null)
			fixes.put("netPos", getFix("network_postfix"));
		else
			fixes.put("netPos", "");


		if(getFix("time_format") != null)
			fixes.put("time_format", getFix("time_format"));
		else
			fixes.put("time_format", "");
	}

	// Helper for those strings that only have a command and a string (typically ERROR)
	public String format(String cmd, String str){
		return format(cmd, "", str, "");
	}

	// Formats the string so it's consistent throughout the application.
	// Every command has it's own way for how it should be displayed, and it's
	// not up to the server or channel to do the formatting. That is done here.
	public String format(String cmd, String str, String msg, String chan){
		//SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		String time;
		if(!isEmpty(fixes.get("time_format"))){
			Date d = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat(fixes.get("time_format"));
			time = sdf.format(d);
		}
		else
			time = "";


		if(cmd.equals("PRIVMSG")){
			// str == nick, msg == msg
			return time + fixes.get("nPre") +str+ fixes.get("nPos") + msg;
		}
		else if(cmd.equals("ACTION")){
			return time + fixes.get("aPre") + str + " " + msg + fixes.get("aPos");
		}
		else if(cmd.equals("JOIN")){
			// str == nick. msg ==host
			return time + fixes.get("jPre") + str +"("+msg+ ") has joined " + chan + fixes.get("jPos");
		}
		else if(cmd.equals("PART")){
			// str = nick!nick@host.org, msg == part-msg
			String[] usr = str.split("!");
			return time + fixes.get("pPre") + usr[0] + "("+usr[1] + ") has left " + chan + " ("+msg+")" + fixes.get("pPos");
		}
		else if(cmd.equals("QUIT")){
			// str = nick!nick@host.com, msg == quit-msg
			String[] usr = str.split("!");
			return time + fixes.get("qPre") + usr[0] +"("+usr[1] + ") has quit ("+msg+")" + fixes.get("qPos");
		}
		else if(cmd.equals("NICK")){
			// str = oldnick, msg == newnick
			return time + fixes.get("netPre") + str + " is now known as " + msg + fixes.get("netPos");
		}
		else if(cmd.equals("332")){ // topic
			// str == nick, msg == topic
			return time + fixes.get("netPre") +"Topic for " + chan + " is: \"" + msg + "\"\n -- Topic set by: " + str + fixes.get("netPos");
		}
		else if(cmd.equals("366")){ // nicks
			// str == channel, msg == nicks
			return time + fixes.get("netPre") +"Nicks " + chan +": ["+msg+"]" + fixes.get("netPos");
		}
		else if(cmd.equals("404")){ // can't send to channel
			// str == channel, msg == msg
			return time + fixes.get("netPre") + chan +": " + msg + fixes.get("netPos");
		}

		else if(cmd.equals("ERROR")){// Some error we generated
			// str == ?, msg == msg
			return time + fixes.get("ePre") + msg + fixes.get("ePos");
		}

		else if(cmd.equals("MODE")){
			// str == getter!setter, msg == mode
			String[] usrs = str.split("!");
			if(!usrs[0].equals(""))
				usrs[0] = " " + usrs[0];
			return time + fixes.get("netPre") + "Mode " + chan + " ["+msg + usrs[0]+"] by " + usrs[1];
		}

		else
			return "Unable to format: " + time + " : " + cmd + " : " + str + " : " + msg + " : " + chan;
	}

	/* Helper to check if a string is an integer */
	private static boolean isInteger(String s){
		try{
			Integer.parseInt(s);
		}catch(NumberFormatException nfe){
			return false;
		}
		return true;
	}
	/* Helper to check if a string is empty or not */
	private static boolean isEmpty(String s){
		if(s != null)
			if(!s.isEmpty())
				if(s.trim().length() > 0)
					if(s.matches(".*\\w.*"))
						return false;
		return true;
	}
}
