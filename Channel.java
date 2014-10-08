/*
 * This is a channel. Every channel has it's
 * own text and name. Since we want to
 * separate each channel from one another to easily
 * see who said what where, each channel is its own
 * channel-object.
 */
class Channel implements Buffer{
	private Server server;
	private String name;
	private boolean isConnected;
	private boolean isChannel;
	private IRClient host;

	private StringBuilder text;

	public Channel(Server srvr, String chan){
		this(srvr.getHost(), srvr, chan, true);
	}
	public Channel(Server srvr, String chan, boolean channel){
		this(srvr.getHost(), srvr, chan, channel);
	}
	public Channel(IRClient hst, Server srvr, String chan){
		this(hst, srvr, chan, true);
	}
	public Channel(IRClient hst, Server srvr, String chan, boolean channel){
		server = srvr;
		name = chan;
		isConnected = true;
		text = new StringBuilder();
		host = hst;
		isChannel=channel;

		if(server != null && name.substring(0,1).equals("#"))
			server.send("JOIN " + name);
	}



	/* SETTERS & ADDERS */

	public void addText(String str){
		text.append(str+"\n");
	}

	public void add(String command, String str, String msg){
		String s = host.format(command, str, msg, name);
		addText(s);
	}

	public void send(String str){
		if(server != null)
			server.send("PRIVMSG " + name + " :"+str); // Server manages adding it to the channel.
		else
			addText(str);
	}

	public void setNick(String str){
		server.setNick(str);
	}

	public void setCorrectName(String str){
		name = str;
	}



	/* GETTERS */

	public String getText(){
		return text.toString();
	}

	public String getName(){
		return name;
	}

	public Buffer getBuffer(){
		return this;
	}

	public String getServerName(){
		return server.getName();
	}
	public Server getServer(){
		return server.getServer();
	}

	public String getNickList(){
		return server.getNickList(name);
	}


	public String toString(){
		return name;
	}



	/* OTHER */

	public void part(){
		if(isConnected && isChannel){
			server.part(this);
			isConnected = false;
		}
	}

	public void close(){
		if(isConnected && isChannel)
			part();
		server.remove(this);
	}

	public void join(){
		server.send("JOIN " + name);
		isConnected = true;
	}

	public boolean isServer(){
		return false;
	}

	public boolean isChannel(){
		return isChannel;
	}

	public boolean isConnected(){
		return isConnected;
	}
}
