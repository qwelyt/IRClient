import java.util.*;

/*
 * We want to keep the application as modular as possible.
 * Therefore, all commands that the user can use are here, instead 
 * of integrated in the GUI. A possible TUI (or different GUI) should
 * be able to use the same commands without code-duplication.
 */
class Commands{
	private IRClient host;
	private UI owner;

	public Commands(IRClient hst, UI wnr){
		host = hst;
		owner = wnr;
	}

	// Processes what the user tries to send.
	// Checks if it's a command or just text.
	// If it's just text, send it. We don't care.
	// If it's a command, act upon it!
	public void process(Buffer buf, int bufIndex, String str){
		if(str.charAt(0) == '/' && str.length()>1){
			// It's a command
			try{
				String[] arr = str.substring(1).split(" ", 2); // Split "/command with stuff" into arr[0]="command", arr[1]="with stuff".
				String command = arr[0].toLowerCase();
				String rest = null;
				if(arr.length>1)
					rest = arr[1];

				if(command.equals("quit"))
					owner.quit();

				else if(command.equals("close")){
					if(bufIndex != 0){
						owner.setSelectedBuffer(bufIndex-1);
						host.removeBuffer(buf);
					}
				}

				else if(command.equals("part")){
					if(bufIndex != 0)
						if(!buf.isServer()){
							((Channel)buf).part();
						}
				}

				else if(command.equals("nick")){
					if(!isEmpty(rest)){
						String[] nick = arr[1].split(" ");
						if(bufIndex == 0){
							host.setNick(nick[0]);
						}
						else
							buf.setNick(nick[0]);
					}
					else{
						if(buf.isServer())
							buf.addText(host.format("ERROR","You need to specify your new nick as well"));
						else
							buf.getServer().addText(host.format("ERROR","You need to specify your new nick as well."));
					}
				}

				else if(command.equals("connect")){
					String[] con = rest.split(" ");
					if(con.length > 1){
						if(isInteger(con[1]))
							host.connect(con[0], Integer.parseInt(con[1]));
						else
							owner.append(host.format("ERROR"," Invalid server port: " + con[1] ));
					}
					else
						host.connect(con[0]);
				}

				else if(command.equals("join")){
					if(bufIndex == 0)
						owner.append(host.format("ERROR", "You need to be on a server to join a channel"));
					else{
						if(!isEmpty(rest)){
							String[] chan = rest.split(" ");
							buf.getServer().join(chan[0]);
						}
						else
							owner.append(host.format("ERROR","JOIN: You need to provide a channel to join"));
					}
				}

				else if(command.equals("query")){
					if(bufIndex == 0)
						owner.append(host.format("ERROR", "You need to be an a server to query someone"));
					else{
						if(!isEmpty(rest)){
							String[] usr = rest.split(" ");
							buf.getServer().join(usr[0]);
						}
						else
							owner.append(host.format("ERROR","QUERY: You need to privide a user to query"));
					}
				}

				else if(command.equals("me")){
					if(bufIndex == 0)
						owner.append(host.format("ERROR", "You cannot send text to this buffer"));
					else
						if(!isEmpty(rest)){
							buf.send('\001'+"ACTION "+rest+'\001');
						}
				}

				else if(command.equals("settings")){
					//owner.append("Settings in IRClient.conf:");
					owner.append(host.getSettings());
				}
				else if(command.equals("save")){
					host.saveConfig();
				}

				else if(command.equals("help")){
					owner.append(host.getHelp());
				}

				else{
					String s = "Command { "+command+" } not found";
					buf.addText(host.format("ERROR", s));
				}
			}catch(Exception e){
				owner.append("ERROR: " + e);
			}
		}
		else{
			// It's a message
			if(bufIndex == 0) // If we try to send to the main-buffer, don't.
				buf.addText(host.format("ERROR","Cannot send text to this buffer."));
			else // Else we send it.
				buf.send(str);
		}
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
