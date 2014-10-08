public interface UI{
	public abstract void quit();
	public abstract void removeBuffer(Buffer b);
	public abstract void addBuffer(Buffer b);
	public abstract void append(String str);
	public abstract void setSelectedBuffer(int i);
}
