package com.materialbook.parties;

import com.materialbook.common.PageResponse;
import com.materialbook.parties.PartyDtos.PartyRequest;
import com.materialbook.parties.PartyDtos.PartyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parties")
public class PartyController {
    private final PartyService parties;

    public PartyController(PartyService parties) {
        this.parties = parties;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PARTY.VIEW')")
    PageResponse<PartyResponse> list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) PartyType type,
                                     @RequestParam(required = false) PartyStatus status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return parties.list(q, type, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTY.VIEW')")
    PartyResponse get(@PathVariable Long id) {
        return parties.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PARTY.CREATE')")
    PartyResponse create(@Valid @RequestBody PartyRequest request) {
        return parties.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTY.UPDATE')")
    PartyResponse update(@PathVariable Long id, @Valid @RequestBody PartyRequest request) {
        return parties.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PARTY.UPDATE')")
    PartyResponse deactivate(@PathVariable Long id) {
        return parties.setStatus(id, PartyStatus.INACTIVE);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PARTY.UPDATE')")
    PartyResponse activate(@PathVariable Long id) {
        return parties.setStatus(id, PartyStatus.ACTIVE);
    }
}
