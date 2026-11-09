package com.materialbook.material;

import com.materialbook.inventory.MovementType;
import com.materialbook.parties.PartyType;
import java.util.Set;

/** Direction of a material transaction with everything that differs between IN and OUT. */
enum TransactionType {
    IN("MIN", MovementType.IN, Set.of(PartyType.SUPPLIER, PartyType.BOTH)),
    OUT("MOUT", MovementType.OUT, Set.of(PartyType.CLIENT, PartyType.BOTH));

    final String numberPrefix;
    final MovementType movement;
    final Set<PartyType> allowedPartyTypes;

    TransactionType(String numberPrefix, MovementType movement, Set<PartyType> allowedPartyTypes) {
        this.numberPrefix = numberPrefix;
        this.movement = movement;
        this.allowedPartyTypes = allowedPartyTypes;
    }

    /** Audit module name and permission prefix, e.g. MATERIAL_IN. */
    String module() {
        return "MATERIAL_" + name();
    }
}
