package mc.alk.ctf;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.util.InventoryUtil;
import mc.alk.arena.util.SerializerUtil;

@RequiredArgsConstructor
public class Flag {
    @Getter final ArenaTeam team; 
    final ItemStack is;
    @Getter final Location homeLocation;
    
	static int count = 0;
	final int id = count++; 
	
	@Getter @Setter Entity entity; /// What is our flag (item or carried by player)
	@Getter @Setter boolean home = true; /// is our flag at home

	public Location getCurrentLocation() { return entity.getLocation(); }
    public boolean isValid() { return entity.isValid(); }

	public boolean sameFlag( ItemStack is2 ) {
	    if ( is.getType() != is2.getType() ) return false;
	    if ( is.hasItemMeta() ) 
	        return is.getItemMeta().equals( is2.getItemMeta() );
	    
		return is.getDurability() == is2.getDurability();
	}

	@Override
	public boolean equals( Object other ) {
		if ( this == other ) return true;
		if ( !(other instanceof Flag ) ) return false;
		return id == ((Flag) other).id;
	}

	@Override
	public int hashCode() { return id; }

	@Override
	public String toString(){
		return String.format( "[Flag %d: ent=%s, home=%s, team=%s, is=%s, homeloc=%s]",
                				id,
                                entity == null ? "null" : entity.getType(), 
                                home,
                				team == null ? "null" : team.getId(),
                				is == null ? "null" : InventoryUtil.getItemString(is),
                				homeLocation == null ? "null" : SerializerUtil.getLocString(homeLocation));
	}
}
