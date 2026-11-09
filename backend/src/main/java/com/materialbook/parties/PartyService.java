package com.materialbook.parties;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.PageResponse;
import com.materialbook.parties.PartyDtos.PartyRequest;
import com.materialbook.parties.PartyDtos.PartyResponse;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartyService {
    private static final String MODULE = "PARTY";
    private static final int MAX_PAGE_SIZE = 200;

    private final PartyRepository parties;
    private final AuditService audit;

    public PartyService(PartyRepository parties, AuditService audit) {
        this.parties = parties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<PartyResponse> list(String q, PartyType type, PartyStatus status, int page, int size) {
        Specification<Party> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = com.materialbook.common.LikePattern.contains(q);
                where.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("mobile")), like)));
            }
            if (type != null) {
                // a BOTH party is both a supplier and a client, so it matches either filter
                where.add(type == PartyType.BOTH ? cb.equal(root.get("type"), PartyType.BOTH)
                        : root.get("type").in(type, PartyType.BOTH));
            }
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.of(parties.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("name", "id"))),
                PartyService::toResponse);
    }

    @Transactional(readOnly = true)
    public PartyResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public PartyResponse create(PartyRequest request) {
        String gst = request.gstNumber();
        if (gst != null && parties.existsByGstNumber(gst)) {
            throw new ConflictException("GST number already exists: " + gst);
        }
        Party party = new Party(String.format("PTY-%04d", parties.nextCodeNumber()));
        apply(party, request, balance(request));
        party = parties.saveAndFlush(party);
        PartyResponse response = toResponse(party);
        audit.log("PARTY_CREATE", MODULE, party.getId(), null, response);
        return response;
    }

    @Transactional
    public PartyResponse update(Long id, PartyRequest request) {
        Party party = require(id);
        String gst = request.gstNumber();
        if (gst != null && parties.existsByGstNumberAndIdNot(gst, id)) {
            throw new ConflictException("GST number already exists: " + gst);
        }
        BigDecimal opening = balance(request);
        if (opening.compareTo(party.getOpeningBalance()) != 0 && parties.hasTransactions(id)) {
            throw new ConflictException("Opening balance cannot be changed: the party already has transactions or payments");
        }
        PartyResponse before = toResponse(party);
        apply(party, request, opening);
        parties.flush();
        PartyResponse after = toResponse(party);
        audit.log("PARTY_UPDATE", MODULE, id, before, after);
        return after;
    }

    @Transactional
    public PartyResponse setStatus(Long id, PartyStatus status) {
        Party party = require(id);
        PartyResponse before = toResponse(party);
        party.setStatus(status);
        parties.flush();
        PartyResponse after = toResponse(party);
        audit.log(status == PartyStatus.ACTIVE ? "PARTY_ACTIVATE" : "PARTY_DEACTIVATE", MODULE, id, before, after);
        return after;
    }

    private Party require(Long id) {
        return parties.findById(id).orElseThrow(() -> new NotFoundException("Party not found"));
    }

    private static BigDecimal balance(PartyRequest r) { return r.openingBalance() == null ? BigDecimal.ZERO : r.openingBalance(); }

    private static void apply(Party party, PartyRequest r, BigDecimal opening) {
        party.update(r.name().trim(), r.type(), r.mobile(), blankToNull(r.email()), blankToNull(r.address()), r.gstNumber(), opening);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static PartyResponse toResponse(Party p) {
        return new PartyResponse(p.getId(), p.getCode(), p.getName(), p.getType(), p.getMobile(), p.getEmail(),
                p.getAddress(), p.getGstNumber(), p.getOpeningBalance(), p.getStatus());
    }
}
