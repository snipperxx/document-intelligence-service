package io.github.snipperxx.docintel.document;

import io.github.snipperxx.docintel.auth.CurrentUser;
import io.github.snipperxx.docintel.common.BadRequestException;
import io.github.snipperxx.docintel.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Stores the document and returns 202: fact extraction runs asynchronously, poll the
     * document until its status is EXTRACTED or FAILED.
     */
    @PostMapping(path = "/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentService.DocumentView> upload(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable long projectId,
                                                               @RequestPart("file") MultipartFile file,
                                                               @RequestParam(required = false) String title)
            throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty");
        }
        DocumentService.DocumentView view = documentService.upload(CurrentUser.id(jwt), projectId, title,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return ResponseEntity.accepted()
                .location(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/documents/{id}").buildAndExpand(view.id()).toUri())
                .body(view);
    }

    @GetMapping("/projects/{projectId}/documents")
    public PageResponse<DocumentService.DocumentView> list(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable long projectId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return documentService.list(CurrentUser.id(jwt), projectId, page, size);
    }

    @GetMapping("/documents/{documentId}")
    public DocumentService.DocumentView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        return documentService.get(CurrentUser.id(jwt), documentId);
    }

    @GetMapping(path = "/documents/{documentId}/content", produces = MediaType.TEXT_PLAIN_VALUE)
    public String content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        return documentService.content(CurrentUser.id(jwt), documentId);
    }

    @GetMapping("/documents/{documentId}/facts")
    public List<DocumentService.FactView> facts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        return documentService.facts(CurrentUser.id(jwt), documentId);
    }

    @PostMapping("/documents/{documentId}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentService.DocumentView reprocess(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        return documentService.reprocess(CurrentUser.id(jwt), documentId);
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        documentService.delete(CurrentUser.id(jwt), documentId);
    }
}
