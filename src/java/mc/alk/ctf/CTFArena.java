package mc.alk.ctf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import mc.alk.arena.controllers.PlayerController;
import mc.alk.arena.controllers.PlayerStoreController;
import mc.alk.arena.controllers.Scheduler;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchState;
import mc.alk.arena.objects.arenas.Arena;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.messaging.MatchMessenger;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.objects.victoryconditions.VictoryCondition;
import mc.alk.arena.serializers.Persist;
import mc.alk.arena.util.Log;
import mc.alk.arena.util.TeamUtil;

public class CTFArena extends Arena {
    public static final boolean DEBUG = true;

    private static final long FLAG_RESPAWN_TIMER = 20 * 15L;
    private static final long TIME_BETWEN_CAPTURES = 2000;

    /**
     * Save these flag spawns with the rest of the arena information
     */
    @Persist
    final HashMap<Integer,Location> flagSpawns = new HashMap<>();

    /// The following variables should be reinitialized and set up every match
    FlagVictory scores;
    final Map<Integer, Flag> flags = new ConcurrentHashMap<>();
    final Map<ArenaTeam, Flag> teamFlags = new ConcurrentHashMap<>();
    public static int capturesToWin = 3;
    int runcount = 0;
    Integer timerid, compassRespawnId, flagCheckId;
    Map<Flag, Integer> respawnTimers = new HashMap<>();
    final Map<ArenaTeam, Long> lastCapture = new ConcurrentHashMap<>();
    final Set<Material> flagMaterials = new HashSet<>();
    Random rand = new Random();
    MatchMessenger mmh;

    @Override
    public void onOpen(){
        mmh = match.getMatchMessager();
        resetVars();
        match.addVictoryCondition(scores);
    }

    private void resetVars(){
        VictoryCondition vc = match.getVictoryCondition( FlagVictory.class );
        
        scores = ( vc != null ? (FlagVictory) vc 
                              : new FlagVictory( match ) );
        
        scores.setCapturesToWin(capturesToWin);
        scores.setMessageHandler(mmh);
        flags.clear();
        teamFlags.clear();
        cancelTimers();
        respawnTimers.clear();
        lastCapture.clear();
        flagMaterials.clear();
    }

    @Override
    public void onStart(){
        List<ArenaTeam> _teams = getTeams();
        if ( flagSpawns.size() < _teams.size() ) {
            Log.err( "Cancelling CTF as there " + _teams.size() + " teams but only " + flagSpawns.size() + " flags" );
            match.cancelMatch();
            return;
        }
        int i = 0;
        for ( Location loc : flagSpawns.values() ) {
            loc = loc.clone();
            ArenaTeam team = _teams.get(i);
            ItemStack is = TeamUtil.getTeamHead( i++ );
            Flag flag = new Flag( team, is, loc );
            teamFlags.put( team, flag );

            flagMaterials.add( is.getType() );
            spawnFlag(flag);
            
            if (DEBUG) Log.info("Team t = " + team + " flag spawned at:- " + loc.toString() );
        }
        scores.setFlags(teamFlags);

        timerid = Bukkit.getScheduler().scheduleSyncRepeatingTask( CTF.getSelf(), 
                () -> {
                        boolean extraeffects = ( runcount++ % 2 == 0 );
                        for ( Flag flag : flags.values() ) {
                            Location l = flag.getCurrentLocation();
                            l.getWorld().playEffect( l, Effect.MOBSPAWNER_FLAMES, 0 );
                            if ( extraeffects ) {
                                l = l.clone()
                                     .add( rand.nextInt(4) - 2, rand.nextInt(2) - 1, rand.nextInt(4) - 2 );
//                                l.setX( l.getX() + rand.nextInt(4) - 2 );
//                                l.setZ( l.getZ() + rand.nextInt(4) - 2 );
//                                l.setY( l.getY() + rand.nextInt(2) - 1 );
                                l.getWorld().playEffect( l, Effect.MOBSPAWNER_FLAMES, 0 );
                            }
                        }
                }, 20L, 20L );

        flagCheckId = Bukkit.getScheduler().scheduleSyncRepeatingTask( CTF.getSelf(), 
                () -> { 
                        for ( Flag flag : flags.values() ) {
                            if ( flag.isHome() && !flag.isValid() ) {
                                spawnFlag(flag);
                            }
                        }
                }, 0L, 6 * 20L );

        compassRespawnId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                                CTF.getSelf(), () -> updateCompassLocations() , 0L, 5 * 20L );
    }

    private void updateCompassLocations() {
        List<ArenaTeam> _teams = getTeams();
        
        for ( int i = 0; i < _teams.size(); i++ ) {

            Flag f = teamFlags.get( _teams.get( ( i == _teams.size() - 1 ) ? 0 
                                                                           : i + 1 ) );           
            if ( f == null ) continue;
            
            for ( ArenaPlayer ap : _teams.get( i ).getLivingPlayers() ) {
                Player p = ap.getPlayer();
                if ( p != null && p.isOnline() ) {
                    p.setCompassTarget( f.getCurrentLocation() );
                }
            }
        }
    }

    private Item spawnItem( Location l, ItemStack is ) {
        Item item = l.getBlock().getWorld().dropItem( l, is );
        item.setVelocity( new Vector(0,0,0) );
        return item;
    }

    @Override
    public void onFinish(){
        cancelTimers();
        removeFlags();
        resetVars();
    }

    @ArenaEventHandler
    public void onPlayerDropItem( PlayerDropItemEvent event ) {
        if ( event.isCancelled() ) return;

        Flag flag = flags.get( event.getPlayer().getEntityId() );
        if ( flag == null ) return;
        
        Item item = event.getItemDrop();
        
        if ( flag.sameFlag( 
                     item.getItemStack() ) ) 
            playerDroppedFlag( flag, item );
    }

    @ArenaEventHandler
    public void onPlayerPickupItem( PlayerPickupItemEvent event ) {
        int id = event.getItem().getEntityId();
        
        if ( !flags.containsKey( id ) ) return;
        
        Player p = event.getPlayer();
        ArenaTeam t = getTeam(p);
        Flag flag = flags.get( id );

        Map<String,String> cParams = getCaptureParams();
        cParams.put( "{player}", p.getDisplayName() );
        
        if ( flag.team.equals( t ) ) {
            event.setCancelled(true);
            if ( !flag.isHome() ) {  /// Return the flag back to its home location
                playerReturnedFlag( p, flag );
                event.getItem().remove();
                t.sendMessage( mmh.getMessage( "CaptureTheFlag.player_returned_flag", cParams ) );
            }
        } 
        else {
            /// Give the enemy the flag
            playerPickedUpFlag( p, flag );
            ArenaTeam fteam = flag.team;

            for ( ArenaTeam team : getTeams() ) {
                if ( team.equals( t ) )
                    team.sendMessage( mmh.getMessage( "CaptureTheFlag.taken_enemy_flag", cParams ) );
                else if ( team.equals( fteam ) )
                    team.sendMessage( mmh.getMessage( "CaptureTheFlag.taken_your_flag", cParams ) );
            }
        }
    }

    private Map<String, String> getCaptureParams() {
        Map<String,String> _params = new HashMap<>();
        _params.put( "{prefix}", getMatch().getParams().getPrefix() );
        _params.put( "{maxcaptures}", capturesToWin + "" );
        return _params;
    }

    @ArenaEventHandler( needsPlayer = false )
    public void onItemDespawn(ItemDespawnEvent event){
        if ( flags.containsKey( event.getEntity().getEntityId() ) ) {
            event.setCancelled( true );
        }
    }

    @ArenaEventHandler
    public void onPlayerDeath(PlayerDeathEvent event){
        Flag flag = flags.remove( event.getEntity().getEntityId() );
        if ( flag == null ) return;
        
        /// we have a flag holder, drop the flag
        List<ItemStack> items = event.getDrops();
        for ( ItemStack is : items ) {
            if ( flag.sameFlag(is) ) {
                int amt = is.getAmount();
                if ( amt > 1 )
                    is.setAmount( amt - 1 );
                else
                    is.setType( Material.AIR );
                break;
            }
        }
        Location l = event.getEntity().getLocation();
        Item item = l.getBlock().getWorld().dropItemNaturally( l, flag.is );
        playerDroppedFlag( flag, item );
    }

    @ArenaEventHandler
    public void onPlayerMove( PlayerMoveEvent event ) {
        if ( event.isCancelled() ) return;
        
        /// Check to see if they moved a block, or if they are holding a flag
        if (    !flags.containsKey( event.getPlayer().getEntityId() ) 
                ||  !(  event.getFrom().getBlockX() != event.getTo().getBlockX()
                     || event.getFrom().getBlockY() != event.getTo().getBlockY()
                     || event.getFrom().getBlockZ() != event.getTo().getBlockZ() ) ) {
            return;
        }
//        event.getTo().distanceSquared( event.getFrom() );
        if ( getState() != MatchState.INGAME ) return;
        
        ArenaTeam t = getTeam( event.getPlayer() );
        Flag f = teamFlags.get(t);
        if ( !f.isHome() && nearLocation( f.getCurrentLocation(), event.getTo() ) ) {
            Flag capturedFlag = flags.get( event.getPlayer().getEntityId() );
            Long lastc = lastCapture.get(t);
            if (lastc != null && System.currentTimeMillis() - lastc < TIME_BETWEN_CAPTURES) return;
            
            lastCapture.put(t, System.currentTimeMillis());
            
            ArenaPlayer ap = PlayerController.toArenaPlayer( event.getPlayer() );
            event.getPlayer().getInventory().remove( f.is );
 
            if ( !teamScored( t, ap ) ) {
                removeFlag( capturedFlag );
                spawnFlag( capturedFlag );
            }
            String score = scores.getScoreString();
            Map<String,String> _params = getCaptureParams();
            _params.put( "{team}", t.getDisplayName() );
            _params.put( "{score}", score );

            performTransition( CTFTransition.ONFLAGCAPTURE, ap );
            mmh.sendMessage( "CaptureTheFlag.teamscored", _params );
        }
    }


    @ArenaEventHandler
    public void onBlockPlace( BlockPlaceEvent event ) {
        if ( !flags.containsKey( event.getPlayer().getEntityId() ) ) return;

        if ( flagMaterials.contains( event.getBlock().getType() ) )
            event.setCancelled( true );
    }

    private void cancelTimers(){
        if (timerid != null){
            Bukkit.getScheduler().cancelTask(timerid);
            timerid = null;
        }
        if (compassRespawnId != null){
            Bukkit.getScheduler().cancelTask(compassRespawnId);
            compassRespawnId = null;
        }
        if (flagCheckId != null){
            Bukkit.getScheduler().cancelTask(flagCheckId);
            flagCheckId = null;
        }
    }

    private void removeFlags() {
        for ( Flag f : flags.values() ) removeFlag(f);
    }

    private void removeFlag( Flag flag ) {
        if ( flag.getEntity() instanceof Player )
            PlayerStoreController.removeItem( PlayerController.toArenaPlayer((Player) flag.entity), flag.is );
        else
            flag.getEntity().remove();
    }
    
    private void playerReturnedFlag( Player player, Flag flag ) {
        flags.remove( flag.getEntity().getEntityId() );
        spawnFlag(flag);
        performTransition( CTFTransition.ONFLAGRETURN, PlayerController.toArenaPlayer( player ) );
    }

    private void playerPickedUpFlag( Player player, Flag flag ) {
        flags.remove( flag.entity.getEntityId() );
        flag.setEntity( player );
        flag.setHome( false );
        flags.put( player.getEntityId(), flag );
        cancelFlagRespawnTimer( flag );
        performTransition( CTFTransition.ONFLAGPICKUP, PlayerController.toArenaPlayer( player ) );
    }

    private void playerDroppedFlag( Flag flag, Item item ) {
        if ( flag.getEntity() instanceof Player )
            performTransition( CTFTransition.ONFLAGDROP, PlayerController.toArenaPlayer( (Player) flag.getEntity() ) );

        flags.remove( flag.getEntity().getEntityId() );
        flag.setEntity( item );
        flags.put( item.getEntityId(), flag );
        startFlagRespawnTimer( flag );
    }

    private void spawnFlag( Flag flag ) {
        cancelFlagRespawnTimer( flag );
        Entity ent = flag.getEntity();
        
        if ( ent != null && ent instanceof Item ) ent.remove();
        if ( ent != null ) flags.remove( ent.getEntityId() );
        
        Item item = spawnItem( flag.getHomeLocation(), flag.is );
        flag.setEntity( item );
        flag.setHome( true );
        flags.put( item.getEntityId(), flag );
    }

    private void startFlagRespawnTimer( Flag flag) {
        cancelFlagRespawnTimer( flag );
        Integer _timerid = Scheduler.scheduleSynchronousTask( CTF.getSelf(), 
                () -> { spawnFlag( flag );
                        flag.getTeam()
                            .sendMessage( mmh.getMessage( "CaptureTheFlag.returned_flag", getCaptureParams() ) );           
                }, FLAG_RESPAWN_TIMER );
        
        respawnTimers.put( flag, _timerid );
    }

    private void cancelFlagRespawnTimer( Flag flag ) {
        Integer _timerid = respawnTimers.get( flag );
        if ( _timerid != null )
            Bukkit.getScheduler().cancelTask( _timerid );
    }

    private synchronized boolean teamScored( ArenaTeam team, ArenaPlayer player ) {
        if ( scores.addScore(team,player) >= capturesToWin ) {
            setWinner(team);
            return true;
        }
        return false;
    }

    private static boolean nearLocation( Location l1, Location l2 ) {
        return l1.getWorld().getUID().equals( l2.getWorld().getUID() ) 
                && Math.abs( l1.getX() - l2.getX() ) < 2
                && Math.abs( l1.getZ() - l2.getZ() ) < 2 
                && Math.abs( l1.getBlockY() - l2.getBlockY() ) < 3;
    }

    void addFlag( Integer i, Location location ) {
        Location l = location.clone();
        l.setX( location.getBlockX() + 0.5 );
        l.setY( location.getBlockY() + 2 );
        l.setZ( location.getBlockZ() + 0.5 );
        flagSpawns.put(i, l);
    }

    Map<Integer, Location> getFlagLocations() { return flagSpawns; }
    void clearFlags() { flagSpawns.clear(); }
    @Override
    public boolean valid() { return super.valid() && flagSpawns.size() >= 2; }

    @Override
    public List<String> getInvalidReasons() {
        List<String> reasons = new ArrayList<>();
        if (flagSpawns.size() < 2)
            reasons.add( "You need to add at least 2 flags!" );
        reasons.addAll( super.getInvalidReasons() );
        return reasons;
    }
}
