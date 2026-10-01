package io.github.snipperxx.docintel.org;

import io.github.snipperxx.docintel.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgService.UnitView createOrganization(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateOrganizationRequest request) {
        return orgService.createOrganization(CurrentUser.id(jwt), request.name());
    }

    @PostMapping("/units/{parentId}/children")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgService.UnitView createChild(@AuthenticationPrincipal Jwt jwt, @PathVariable long parentId,
                                           @Valid @RequestBody CreateUnitRequest request) {
        return orgService.createChild(CurrentUser.id(jwt), parentId, request.name(), request.type());
    }

    @GetMapping("/units/mine")
    public List<OrgService.UnitView> mine(@AuthenticationPrincipal Jwt jwt) {
        return orgService.myUnits(CurrentUser.id(jwt));
    }

    @GetMapping("/units/{unitId}")
    public OrgService.UnitView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long unitId) {
        return orgService.get(CurrentUser.id(jwt), unitId);
    }

    @GetMapping("/units/{unitId}/children")
    public List<OrgService.UnitView> children(@AuthenticationPrincipal Jwt jwt, @PathVariable long unitId) {
        return orgService.children(CurrentUser.id(jwt), unitId);
    }

    @GetMapping("/units/{unitId}/members")
    public List<OrgService.MemberView> members(@AuthenticationPrincipal Jwt jwt, @PathVariable long unitId) {
        return orgService.members(CurrentUser.id(jwt), unitId);
    }

    /**
     * Grants a role, or changes it if the user already has one on this unit.
     */
    @PutMapping("/units/{unitId}/members")
    public OrgService.MemberView grant(@AuthenticationPrincipal Jwt jwt, @PathVariable long unitId,
                                       @Valid @RequestBody GrantRequest request) {
        return orgService.grant(CurrentUser.id(jwt), unitId, request.email(), request.role());
    }

    public record CreateOrganizationRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record CreateUnitRequest(@NotBlank @Size(max = 120) String name, @NotNull OrgUnitType type) {
    }

    public record GrantRequest(@NotBlank @Email String email, @NotNull Role role) {
    }
}
