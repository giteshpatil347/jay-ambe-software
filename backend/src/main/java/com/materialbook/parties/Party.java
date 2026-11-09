package com.materialbook.parties;

import com.materialbook.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "parties")
public class Party extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "party_name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 10)
    private PartyType type;

    @Column(length = 20)
    private String mobile;

    @Column(length = 150)
    private String email;

    private String address;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PartyStatus status = PartyStatus.ACTIVE;

    protected Party() {}

    public Party(String code) { this.code = code; }

    public void update(String name, PartyType type, String mobile, String email, String address, String gstNumber,
                       BigDecimal openingBalance) {
        this.name = name;
        this.type = type;
        this.mobile = mobile;
        this.email = email;
        this.address = address;
        this.gstNumber = gstNumber;
        this.openingBalance = openingBalance;
    }

    public void setStatus(PartyStatus status) { this.status = status; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public PartyType getType() { return type; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getGstNumber() { return gstNumber; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public PartyStatus getStatus() { return status; }
}
