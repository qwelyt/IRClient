public interface Buffer{
	public boolean isServer();

	public void send(String str);
	public void addText(String str);
	public void setNick(String str);

	public String getText();
	public String getServerName();
	public String getName();
	public Buffer getBuffer();
	public Server getServer();
}
