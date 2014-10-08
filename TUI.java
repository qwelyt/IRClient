/*
 * This was meant to be a terminal interface.
 * But the package that was to be used didn't compile or work.
 * So, if a package that works is found, it can be implemented.
 */
class TUI implements UI{
	public TUI(IRClient hst){
	}
	public void quit(){}
	public void removeBuffer(Buffer b){}
	public void addBuffer(Buffer b){}
	public void append(String str){}
	public void setSelectedBuffer(int i){}
}
