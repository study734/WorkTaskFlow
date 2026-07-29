package com.teamproject.group.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
            Long userId, GroupMember.Status status);
    long countByUserIdAndGroupType(Long userId, Group.Type type);
    @EntityGraph(attributePaths = "group")
    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMember.Status status);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupMember> findByIdAndGroupIdAndStatus(Long id, Long groupId, GroupMember.Status status);
    List<GroupMember> findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(Long groupId, GroupMember.Status status);
    long countByGroupIdAndRoleAndStatus(Long groupId, GroupMember.Role role, GroupMember.Status status);
}
