package local.mmm.residencechunk.service;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ResidenceHook {

    private final ClassLoader classLoader;

    public ResidenceHook(Plugin residencePlugin) {
        this.classLoader = residencePlugin.getClass().getClassLoader();
    }

    public Object createArea(Location low, Location high) {
        try {
            Class<?> cuboidAreaClass = loadClass("com.bekvon.bukkit.residence.protection.CuboidArea");
            return cuboidAreaClass.getConstructor(Location.class, Location.class).newInstance(low, high);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Residence CuboidArea", exception);
        }
    }

    public Object getResidenceManager() {
        try {
            Class<?> apiClass = loadClass("com.bekvon.bukkit.residence.api.ResidenceApi");
            Method method = apiClass.getMethod("getResidenceManager");
            return method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to get Residence manager", exception);
        }
    }

    public String checkAreaCollision(Object manager, Object area, Object residence, UUID ownerUuid) {
        try {
            Class<?> managerClass = loadClass("com.bekvon.bukkit.residence.protection.ResidenceManager");
            Class<?> areaClass = loadClass("com.bekvon.bukkit.residence.protection.CuboidArea");
            Class<?> residenceClass = loadClass("com.bekvon.bukkit.residence.protection.ClaimedResidence");
            Method method = managerClass.getMethod("checkAreaCollision", areaClass, residenceClass, UUID.class);
            return (String) method.invoke(manager, area, residence, ownerUuid);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to check Residence collision", exception);
        }
    }

    public boolean addResidence(Object manager, Player player, String ownerName, UUID ownerUuid, String name, Location low, Location high, boolean admin) {
        try {
            Class<?> managerClass = loadClass("com.bekvon.bukkit.residence.protection.ResidenceManager");
            Method method = managerClass.getMethod("addResidence", Player.class, String.class, UUID.class, String.class, Location.class, Location.class, boolean.class);
            return (boolean) method.invoke(manager, player, ownerName, ownerUuid, name, low, high, admin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to add Residence", exception);
        }
    }

    public Object getByName(Object manager, String name) {
        try {
            Class<?> managerClass = loadClass("com.bekvon.bukkit.residence.protection.ResidenceManager");
            Method method = managerClass.getMethod("getByName", String.class);
            return method.invoke(manager, name);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to get Residence by name", exception);
        }
    }

    public void setTeleportLocation(Object residence, Player player, boolean admin) {
        try {
            Class<?> residenceClass = loadClass("com.bekvon.bukkit.residence.protection.ClaimedResidence");
            Method method = residenceClass.getMethod("setTpLoc", Player.class, boolean.class);
            method.invoke(residence, player, admin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set Residence teleport location", exception);
        }
    }

    public boolean replaceArea(Object residence, Player player, Object area, String areaName, boolean admin) {
        try {
            Class<?> residenceClass = loadClass("com.bekvon.bukkit.residence.protection.ClaimedResidence");
            Class<?> areaClass = loadClass("com.bekvon.bukkit.residence.protection.CuboidArea");
            Method method = residenceClass.getMethod("replaceArea", Player.class, areaClass, String.class, boolean.class);
            return (boolean) method.invoke(residence, player, area, areaName, admin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to replace Residence area", exception);
        }
    }

    public void removeResidence(Object manager, Player player, Object residence, boolean resadmin) {
        try {
            Class<?> managerClass = loadClass("com.bekvon.bukkit.residence.protection.ResidenceManager");
            Class<?> residenceClass = loadClass("com.bekvon.bukkit.residence.protection.ClaimedResidence");
            Method method = managerClass.getMethod("removeResidence", Player.class, residenceClass, boolean.class);
            method.invoke(manager, player, residence, resadmin);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove Residence", exception);
        }
    }

    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, classLoader);
    }
}
