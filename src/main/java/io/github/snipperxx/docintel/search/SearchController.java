package io.github.snipperxx.docintel.search;

import io.github.snipperxx.docintel.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public List<ChunkIndex.ChunkHit> search(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam("q") @NotBlank @Size(max = 1000) String query,
                                            @RequestParam(required = false) Integer topK) {
        return searchService.search(CurrentUser.id(jwt), query, topK);
    }

    @PostMapping("/ask")
    public SearchService.Answer ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest request) {
        return searchService.ask(CurrentUser.id(jwt), request.question(), request.topK());
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question, Integer topK) {
    }
}
