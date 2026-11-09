package com.materialbook.parties;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<Party, Long>, JpaSpecificationExecutor<Party> {
    boolean existsByGstNumber(String gstNumber);

    boolean existsByGstNumberAndIdNot(String gstNumber, Long id);

    /** True once the party has any material transaction or payment (opening balance becomes immutable). */
    @Query(value = "select exists(select 1 from material_transactions where party_id = :id) "
            + "or exists(select 1 from party_payments where party_id = :id)", nativeQuery = true)
    boolean hasTransactions(@Param("id") Long id);

    @Query(value = "select nextval('party_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
