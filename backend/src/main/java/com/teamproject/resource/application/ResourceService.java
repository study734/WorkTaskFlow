package com.teamproject.resource.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.resource.application.dto.ResourceDtos.*;
import com.teamproject.resource.domain.GroupResource;
import com.teamproject.resource.domain.GroupResourceRepository;
import com.teamproject.resource.storage.FileStorage;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ResourceService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "txt", "csv", "docx", "xlsx", "pptx", "zip");
    private final GroupAuthorization authorization;
    private final GroupResourceRepository resources;
    private final TaskRepository tasks;
    private final FileStorage storage;
    public ResourceService(GroupAuthorization authorization, GroupResourceRepository resources,
            TaskRepository tasks, FileStorage storage) {
        this.authorization = authorization; this.resources = resources; this.tasks = tasks; this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> groupResources(Long userId, Long groupId) {
        GroupMember viewer = authorization.requireActiveMember(groupId, userId);
        return resources.findAllByGroupIdAndTaskIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(groupId)
                .stream().map(value -> response(value, viewer)).toList();
    }
    @Transactional(readOnly = true)
    public List<ResourceResponse> taskResources(Long userId, Long taskId) {
        Task task = task(taskId);
        GroupMember viewer = authorization.requireActiveMember(task.getGroup().getId(), userId);
        return resources.findAllByTaskIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(taskId)
                .stream().map(value -> response(value, viewer)).toList();
    }
    @Transactional
    public ResourceResponse createLink(Long userId, Long groupId, CreateLinkRequest request) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        Task task = taskInGroup(request.taskId(), groupId);
        String url = safeExternalUrl(request.url());
        return response(resources.save(GroupResource.link(member.getGroup(), task, member,
                request.title().trim(), url)), member);
    }
    @Transactional
    public ResourceResponse upload(Long userId, Long groupId, Long taskId, String title, MultipartFile file) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        Task task = taskInGroup(taskId, groupId);
        byte[] bytes = validate(file);
        String checksum = sha256(bytes);
        if (resources.existsByGroupIdAndTaskIdAndChecksumSha256AndDeletedAtIsNull(groupId, taskId, checksum)) {
            throw new ApplicationException("RESOURCE_DUPLICATE", HttpStatus.CONFLICT, "같은 파일이 이미 등록되어 있습니다.");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        String key = "groups/" + groupId + "/" + (task == null ? "resources" : "tasks/" + task.getId())
                + "/" + UUID.randomUUID() + "." + extension;
        String contentType = contentType(extension);
        storage.put(key, bytes, contentType);
        try {
            GroupResource saved = resources.save(GroupResource.file(member.getGroup(), task, member,
                    title == null || title.isBlank() ? filename : title.trim(), key, filename,
                    contentType, bytes.length, checksum));
            return response(saved, member);
        } catch (RuntimeException exception) {
            storage.delete(key);
            throw exception;
        }
    }
    @Transactional(readOnly = true)
    public Download download(Long userId, Long resourceId) {
        GroupResource value = resource(resourceId);
        authorization.requireActiveMember(value.getGroup().getId(), userId);
        if (value.getResourceType() != GroupResource.Type.FILE) {
            throw new ApplicationException("RESOURCE_NOT_FILE", HttpStatus.BAD_REQUEST, "다운로드할 파일이 아닙니다.");
        }
        FileStorage.StoredFile file = storage.get(value.getStorageKey());
        return new Download(file.content(), value.getContentType(), value.getOriginalFilename());
    }
    @Transactional
    public void delete(Long userId, Long resourceId) {
        GroupResource value = resource(resourceId);
        GroupMember member = authorization.requireActiveMember(value.getGroup().getId(), userId);
        if (!value.getCreatedBy().getId().equals(member.getId()) && member.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("RESOURCE_DELETE_FORBIDDEN", HttpStatus.FORBIDDEN, "자료를 삭제할 권한이 없습니다.");
        }
        value.delete();
        if (value.getStorageKey() != null) afterCommit(() -> storage.delete(value.getStorageKey()));
    }
    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid("20MB 이하의 파일을 선택해 주세요.");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        if (!EXTENSIONS.contains(extension)) throw invalid("허용되지 않는 파일 형식입니다.");
        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(extension, bytes)) throw invalid("파일 확장자와 실제 형식이 일치하지 않습니다.");
            return bytes;
        } catch (java.io.IOException exception) { throw invalid("파일을 읽을 수 없습니다."); }
    }
    private boolean matchesSignature(String extension, byte[] bytes) {
        if (Set.of("txt", "csv").contains(extension)) {
            for (byte value : bytes) if (value == 0) return false;
            return true;
        }
        if ("pdf".equals(extension)) return starts(bytes, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        if (Set.of("docx", "xlsx", "pptx", "zip").contains(extension)) return starts(bytes, new byte[]{0x50, 0x4b});
        if ("png".equals(extension)) return starts(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        if (Set.of("jpg", "jpeg").contains(extension)) return starts(bytes, new byte[]{(byte) 0xff, (byte) 0xd8});
        if ("gif".equals(extension)) return starts(bytes, "GIF8".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return false;
    }
    private boolean starts(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) if (value[index] != prefix[index]) return false;
        return true;
    }
    private String safeExternalUrl(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
            boolean supported = host.equals("github.com") || host.endsWith(".github.com")
                    || host.equals("notion.so") || host.endsWith(".notion.so") || host.endsWith(".notion.site")
                    || host.equals("drive.google.com") || host.equals("docs.google.com")
                    || host.endsWith(".atlassian.net");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !supported || uri.getUserInfo() != null) throw invalid("지원하는 HTTPS 외부 링크를 입력해 주세요.");
            return uri.toASCIIString();
        } catch (RuntimeException exception) {
            if (exception instanceof ApplicationException applicationException) throw applicationException;
            throw invalid("올바른 외부 링크를 입력해 주세요.");
        }
    }
    private String safeFilename(String raw) {
        String value = raw == null ? "file" : raw.replace("\\", "/");
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_").trim();
        if (value.isBlank() || value.length() > 255) throw invalid("올바른 파일 이름이 아닙니다.");
        return value;
    }
    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private String contentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "txt" -> "text/plain;charset=UTF-8";
            case "csv" -> "text/csv;charset=UTF-8";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/zip";
        };
    }
    private String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private Task taskInGroup(Long taskId, Long groupId) {
        if (taskId == null) return null;
        Task task = task(taskId);
        if (!task.getGroup().getId().equals(groupId)) throw new ApplicationException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다.");
        return task;
    }
    private Task task(Long id) {
        return tasks.findById(id).orElseThrow(() -> new ApplicationException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
    }
    private GroupResource resource(Long id) {
        return resources.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
    }
    private ResourceResponse response(GroupResource value, GroupMember viewer) {
        return new ResourceResponse(value.getId(), value.getGroup().getId(),
                value.getTask() == null ? null : value.getTask().getId(), value.getResourceType().name(),
                value.getTitle(), value.getExternalUrl(), value.getOriginalFilename(), value.getContentType(),
                value.getSizeBytes(), value.getCreatedBy().getId(), value.getCreatedBy().getUser().getNickname(),
                value.getCreatedAt(), value.getCreatedBy().getId().equals(viewer.getId()) || viewer.getRole() == GroupMember.Role.LEADER);
    }
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
    private ApplicationException invalid(String message) {
        return new ApplicationException("RESOURCE_FILE_INVALID", HttpStatus.BAD_REQUEST, message);
    }
    public record Download(byte[] content, String contentType, String filename) {}
}
