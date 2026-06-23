package local.mmm.residencechunk.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import local.mmm.residencechunk.model.ManagedClaim;

public interface LandDataStore {

    void load();

    void save();

    Collection<ManagedClaim> allClaims();

    ManagedClaim find(String residenceName);

    List<ManagedClaim> findOwnedBy(UUID ownerUuid);

    void put(ManagedClaim claim);

    void remove(String residenceName);
}
