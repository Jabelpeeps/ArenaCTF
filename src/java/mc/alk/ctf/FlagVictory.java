package mc.alk.ctf;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import lombok.Setter;
import mc.alk.arena.competition.Match;
import mc.alk.arena.events.matches.MatchFindCurrentLeaderEvent;
import mc.alk.arena.events.matches.MatchIntervalMessageEvent;
import mc.alk.arena.events.matches.MatchTimeExpiredMessageEvent;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.events.ArenaEventPriority;
import mc.alk.arena.objects.messaging.MatchMessager;
import mc.alk.arena.objects.scoreboard.ArenaObjective;
import mc.alk.arena.objects.scoreboard.ArenaScoreboard;
import mc.alk.arena.objects.scoreboard.SAPIDisplaySlot;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.objects.victoryconditions.VictoryCondition;
import mc.alk.arena.objects.victoryconditions.interfaces.DefinesLeaderRanking;
import mc.alk.arena.util.TimeUtil;

public class FlagVictory extends VictoryCondition implements DefinesLeaderRanking {

	final ArenaObjective scores;
	@Setter Integer capturesToWin;
	MatchMessager mmh;
	@Setter Map<ArenaTeam, Flag> flags;

	public FlagVictory(Match _match) {
		super(_match);
		scores = new ArenaObjective( "FlagCaptures", "Capture the Flag" );
		scores.setDisplayName( ChatColor.GOLD + "Flag Captures" );
		
		ArenaScoreboard scoreboard = _match.getScoreboard();
		scores.setDisplaySlot( SAPIDisplaySlot.SIDEBAR );
		scoreboard.addObjective( scores );

		/// set all points to 0 so they display in Scoreboard
		resetScores();
	}

	public String getScoreString() {
		Map<String,String> map = new HashMap<>();
		map.put( "{prefix}", match.getParams().getPrefix() );
		
		String teamstr = mmh.getMessage( "CaptureTheFlag.teamscore" );
		String separator = mmh.getMessage( "CaptureTheFlag.teamscore_separator" );
		StringBuilder sb = new StringBuilder();
		
		List<ArenaTeam> teams = match.getTeams();
		if (teams == null)
			return "";
		boolean first = true;
		
        for ( ArenaTeam team : teams ) {
            
            if (!first) sb.append(separator);
            
            Flag f = flags.get(team);
            Map<String, String> map2 = new HashMap<>();
            
            map2.put( "{team}", team.getDisplayName() );
            map2.put( "{captures}", scores.getPoints(team) + "" );
            map2.put( "{maxcaptures}", capturesToWin + "" );
            String holder;
            
            if ( f.isHome() ) {
                holder = mmh.getMessage( "CaptureTheFlag.flaghome" );
            } 
            else if ( !(f.getEntity() instanceof Player) ) {
                holder = mmh.getMessage( "CaptureTheFlag.flagfloor" );
            } 
            else {
                Player p = (Player) f.getEntity();
                holder = mmh.getMessage("CaptureTheFlag.flagperson") + p.getDisplayName();
            }
            map2.put("{flagholder}", holder);
            String str = mmh.format(teamstr, map2);
            sb.append(str);
            first = false;
        }
		map.put("{teamscores}",sb.toString());
		return mmh.getMessage("CaptureTheFlag.score",map);
	}

	public void resetScores(){
		scores.setAllPoints(match, 1);
		scores.setAllPoints(match, 0);
	}

	public Integer addScore(ArenaTeam team, ArenaPlayer ap) {
		scores.addPoints(ap, 1);
		return scores.addPoints(team, 1);
	}

	@ArenaEventHandler(priority=ArenaEventPriority.HIGHEST)
	public void onMatchFindCurrentLeaderEvent(MatchFindCurrentLeaderEvent event){
		event.setResult(scores.getMatchResult(match));
	}

	@ArenaEventHandler
	public void onMatchIntervalMessage( MatchIntervalMessageEvent event ) {
	    
		Map<String,String> map = new HashMap<>();
		map.put( "{prefix}" , match.getParams().getPrefix() );
		map.put( "{timeleft}" , TimeUtil.convertSecondsToString( event.getTimeRemaining() ) );
		map.put( "{score}" , getScoreString() );
		
		event.setMatchMessage( mmh.getMessage( "CaptureTheFlag.time_remaining", map ) );
	}

	@ArenaEventHandler
	public void onMatchTimeExpiredMessage( MatchTimeExpiredMessageEvent event ) {
		StringBuilder sb = new StringBuilder();
		Map<String,String> map = new HashMap<>();
		match.setMatchResult( scores.getMatchResult(match) );
		map.put( "{prefix}", match.getParams().getPrefix() );
		
		String node;
		switch( match.getMatchResult().getResult() ) {
		case WIN:
			for ( ArenaTeam t : match.getMatchResult().getVictors() ){
				sb.append( t.getDisplayName() ).append(" ");
			}
			node = "CaptureTheFlag.time_expired_win";
			break;
		case TIE:
			for ( ArenaTeam t : match.getMatchResult().getDrawers() ) {
				sb.append( t.getDisplayName() ).append(" ");
			}
			node = "CaptureTheFlag.time_expired_draw";
			break;
		default:
			node = "CaptureTheFlag.time_expired_draw";
			break;
		}
		map.put( "{teams}", sb.toString() );
		event.setMatchMessage( mmh.getMessage( node, map ) );
	}

	public void setMessageHandler( MatchMessager _mmh ) { mmh = _mmh; }
	@Override
	public List<ArenaTeam> getLeaders() { return scores.getLeaders(); }
	@Override
	public TreeMap<?, Collection<ArenaTeam>> getRanks() { return scores.getRanks(); }
}
