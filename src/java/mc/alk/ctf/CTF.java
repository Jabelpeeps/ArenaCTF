package mc.alk.ctf;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import mc.alk.arena.BattleArena;
import mc.alk.arena.controllers.APIRegistrationController;
import mc.alk.arena.controllers.StateController;
import mc.alk.arena.objects.victoryconditions.VictoryType;
import mc.alk.arena.util.Log;


public class CTF extends JavaPlugin{
	static CTF plugin;

	@Override
	public void onEnable(){
		plugin = this;

		saveDefaultConfig();
		loadConfig();
		/// Register our competition
		VictoryType.register(FlagVictory.class, this);
        StateController.register(CTFTransition.class);
        APIRegistrationController.registerCompetition( 
                        this, "CaptureTheFlag", "ctf", BattleArena.createArenaFactory( CTFArena.class), new CTFExecutor());
        Log.info("[" + getName()+ "] v" + getDescription().getVersion()+ " enabled!");
	}

	@Override
	public void onDisable(){
		Log.info("[" + getName() + "] v" + getDescription().getVersion() + " stopping!");
	}

	public static CTF getSelf() {
		return plugin;
	}

	@Override
	public void reloadConfig(){
		super.reloadConfig();
		loadConfig();
	}

	private void loadConfig() {
		FileConfiguration config = getConfig();
		CTFArena.capturesToWin = config.getInt("capturesToWin", 3);
	}
}
