package local.mmm.residencechunk.service;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ResidenceHook {

    private final Method getResidenceManagerMethod;
    private final Class<?> cuboidAreaClass;
    private final Method checkAreaCollisionMethod;
    private final Method addResidenceMethod;
    private final Method getByNameMethod;
    private final Method setTpLocMethod;
    private final Method replaceAreaMethod;
    private final Method removeResidenceMethod;

    public ResidenceHook(Plugin residencePlugin) {
        try {
            ClassLoader classLoader = residencePlugin.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("com.bekvon.bukkit.residence.api.ResidenceApi", true, classLoader);
            Class<?> managerClass = Class.forName("com.bekvon.bukkit.residence.protection.ResidenceManager", true, classLoader);
            this.cuboidAreaClass = Class.forName("com.bekvon.bukkit.residence.protection.CuboidArea", true, classLoader);
            Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.protection.ClaimedResidence", true, classLoader);

            this.getResidenceManagerMethod = apiClass.getMethod("getResidenceManager");
            this.checkAreaCollisionMethod = managerClass.getMethod("checkAreaCollision", cuboidAreaClass, residenceClass, UUID.class);
            this.addResidenceMethod = managerClass.getMethod("addResidence", Player.class, String.class, UUID.class, String.class, Location.class, Location.class, boolean.class);
            this.getByNameMethod = managerClass.getMethod("getByName", String.class);
            this.setTpLocMethod = residenceClass.getMethod("setTpLoc", Player.class, boolean.class);
            this.replaceAreaMethod = residenceClass.getMethod("replaceArea", Player.class, cuboidAreaClass, String.class, boolean.class);
            this.removeResidenceMethod = managerClass.getMethod("removeResidence", Player.class, residenceClass, boolean.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize Residence reflection bridge", exception);
        }
    }

    public Object createArea(Location low, Location high) {
        try {
            return cuboidAreaClass.getConstructor(Location.class, Location.class).newInstance(low, high);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Residence CuboidArea", exception);
        }
    }

    public Object getResidenceManager() {
        try {
            return getResidenceManagerMethod.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to get Residence manager", exception);
        }
    }

    public String checkAreaCollision(Object manager, Object area, Object residence, UUID ownerUuid) {
        try {
            return (String) checkAreaCollisionMethod.invoke(manager, area, residence, ownerUuid);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to check Residence collision", exception);
        }
    }

    public boolean addResidence(Object manager, Player player, String ownerName, UUID ownerUuid, String name, Location low, Location high, boolean admin) {
        try {
            return (boolean) addResidenceMethod.invoke(manager, player, ownerName, ownerUuid, name, low, high, admin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to add Residence", exception);
        }
    }

    public Object getByName(Object manager, String name) {
        try {
            return getByNameMethod.invoke(manager, name);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to get Residence by name", exception);
        }
    }

    public void setTeleportLocation(Object residence, Player player, boolean admin) {
        try {
            setTpLocMethod.invoke(residence, player, admin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set Residence teleport location", exception);
        }
    }

    public boolean replaceArea(Object residence, Player player, Object area, String areaName, boolean admin) {
        try {
            return (boolean) replaceAreaMethod.invoke(residence, player, area, areaName, admin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to replace Residence area", exception);
        }
    }

    public void removeResidence(Object manager, Player player, Object residence, boolean resadmin) {
        try {
            removeResidenceMethod.invoke(manager, player, residence, resadmin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove Residence", exception);
        }
    }
}
