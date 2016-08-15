package mc.alk.ctf;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.util.InventoryUtil;
import mc.alk.util.Log;
import mc.alk.util.SerializerUtil;

@RequiredArgsConstructor
public class Flag {
    @Getter final ArenaTeam team; 
    final ItemStack is; /// what type of item is our flag
    @Getter final Location homeLocation; /// our spawn location
    
	static int count = 0;
	final int id = count++; 
	@Getter @Setter Entity entity; /// What is our flag (item or carried by player)
	@Getter @Setter boolean home = true; /// is our flag at home
    static Method isValidMethod;
    static boolean isValid = true;

    /**
     * And a large workaround so that I can be compatible with 1.2.5 (aka Tekkit Servers)
     * who will not have the method Entity#isValid()
     */
    static {
        try {
            final String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            
            if (version.equalsIgnoreCase("craftbukkit")){
                isValidMethod = Entity.class.getMethod("isDead");
                isValid = false;
            } else {
                isValidMethod = Entity.class.getMethod("isValid");
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

	public Location getCurrentLocation() { return entity.getLocation(); }

	public boolean sameFlag(ItemStack is2) {
		return is.getType() == is2.getType() && is.getDurability() == is2.getDurability();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof Flag)) return false;
		return this.hashCode() == other.hashCode();
	}

	@Override
	public int hashCode() { return id;}

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

    public boolean isValid() {
        entity.isValid();
        try {
            return (Boolean) isValidMethod.invoke(entity);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }
}
