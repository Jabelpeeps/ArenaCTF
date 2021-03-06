package mc.alk.ctf;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import mc.alk.arena.executors.CustomCommandExecutor;
import mc.alk.arena.executors.MCCommand;
import mc.alk.arena.serializers.ArenaSerializer;
import mc.alk.arena.util.MessageUtil;

public class CTFExecutor extends CustomCommandExecutor {

	@MCCommand( cmds = {"addFlag"}, admin = true )
	public static void addFlag( Player sender, CTFArena arena, Integer index ) {
	    
		if (index < 1 || index > 100) {
			MessageUtil.sendMessage( sender, "&2index must be between [1-100]!" );
			return;
		}

		arena.addFlag(index -1, sender.getLocation());
		ArenaSerializer.saveArenas( CTF.getSelf() );
		
		MessageUtil.sendMessage( sender, "&2Team &6" + index + "&2 flag added!" );
	}

	@MCCommand( cmds = {"clearFlags"}, admin = true )
	public static void clearFlags( CommandSender sender, CTFArena arena ) {
	    
		arena.clearFlags();
		MessageUtil.sendMessage( sender, "&2Flags cleared for &6" + arena.getName() );
	}
}
