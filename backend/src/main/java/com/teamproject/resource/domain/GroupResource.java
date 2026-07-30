package com.teamproject.resource.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.task.domain.Task;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_resources")
public class GroupResource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_id") private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_member_id")
    private GroupMember createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Type resourceType;
    @Column(nullable = false, length = 120) private String title;
    @Column(length = 1000) private String externalUrl;
    @Column(length = 500) private String storageKey;
    @Column(length = 255) private String originalFilename;
    @Column(length = 120) private String contentType;
    private Long sizeBytes;
    @Column(length = 64, columnDefinition = "char(64)") private String checksumSha256;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    protected GroupResource() {}
    private GroupResource(Group group, Task task, GroupMember member, Type type, String title) {
        this.group = group; this.task = task; this.createdBy = member; this.resourceType = type;
        this.title = title; this.createdAt = LocalDateTime.now();
    }
    public static GroupResource link(Group group, Task task, GroupMember member, String title, String url) {
        GroupResource value = new GroupResource(group, task, member, Type.LINK, title);
        value.externalUrl = url;
        return value;
    }
    public static GroupResource file(Group group, Task task, GroupMember member, String title,
            String key, String filename, String contentType, long size, String checksum) {
        GroupResource value = new GroupResource(group, task, member, Type.FILE, title);
        value.storageKey = key; value.originalFilename = filename; value.contentType = contentType;
        value.sizeBytes = size; value.checksumSha256 = checksum;
        return value;
    }
    public void delete() { this.deletedAt = LocalDateTime.now(); }
    public boolean isDeleted() { return deletedAt != null; }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public Task getTask() { return task; }
    public GroupMember getCreatedBy() { return createdBy; }
    public Type getResourceType() { return resourceType; }
    public String getTitle() { return title; }
    public String getExternalUrl() { return externalUrl; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Type { LINK, FILE }
}
