package com.teamproject.resource.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GroupResourceRepository extends JpaRepository<GroupResource, Long> {
    List<GroupResource> findAllByGroupIdAndTaskIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long groupId);
    List<GroupResource> findAllByTaskIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long taskId);
    Optional<GroupResource> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByGroupIdAndTaskIdAndChecksumSha256AndDeletedAtIsNull(Long groupId, Long taskId, String checksum);
}
