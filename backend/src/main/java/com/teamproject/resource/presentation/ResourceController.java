package com.teamproject.resource.presentation;

import com.teamproject.resource.application.ResourceService;
import com.teamproject.resource.application.dto.ResourceDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ResourceController {
    private final ResourceService resources;
    public ResourceController(ResourceService resources) { this.resources = resources; }

    @GetMapping("/groups/{groupId}/resources")
    List<ResourceResponse> groupList(Authentication auth, @PathVariable Long groupId) {
        return resources.groupResources((Long) auth.getPrincipal(), groupId);
    }
    @GetMapping("/tasks/{taskId}/resources")
    List<ResourceResponse> taskList(Authentication auth, @PathVariable Long taskId) {
        return resources.taskResources((Long) auth.getPrincipal(), taskId);
    }
    @PostMapping("/groups/{groupId}/resources/links")
    @ResponseStatus(HttpStatus.CREATED)
    ResourceResponse link(Authentication auth, @PathVariable Long groupId,
            @Valid @RequestBody CreateLinkRequest request) {
        return resources.createLink((Long) auth.getPrincipal(), groupId, request);
    }
    @PostMapping(path = "/groups/{groupId}/resources/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ResourceResponse upload(Authentication auth, @PathVariable Long groupId,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) String title,
            @RequestPart MultipartFile file) {
        return resources.upload((Long) auth.getPrincipal(), groupId, taskId, title, file);
    }
    @GetMapping("/resources/{resourceId}/download")
    ResponseEntity<byte[]> download(Authentication auth, @PathVariable Long resourceId) {
        var value = resources.download((Long) auth.getPrincipal(), resourceId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(value.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(value.content());
    }
    @DeleteMapping("/resources/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication auth, @PathVariable Long resourceId) {
        resources.delete((Long) auth.getPrincipal(), resourceId);
    }
}
