package com.teamproject.group.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
            Long userId, GroupMember.Status status);
    long countByUserIdAndGroupType(Long userId, Group.Type type);
    @EntityGraph(attributePaths = "group")
    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMember.Status status);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupMember> findByIdAndGroupIdAndStatus(Long id, Long groupId, GroupMember.Status status);
    @EntityGraph(attributePaths = "user")
    List<GroupMember> findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(Long groupId, GroupMember.Status status);
    long countByGroupIdAndStatus(Long groupId, GroupMember.Status status);
    @Query("select gm.group.id as groupId, count(gm) as memberCount from GroupMember gm "
            + "where gm.group.id in :groupIds and gm.status = :status group by gm.group.id")
    List<GroupMemberCount> countByGroupIdsAndStatus(@Param("groupIds") Collection<Long> groupIds,
            @Param("status") GroupMember.Status status);
    long countByGroupIdAndRoleAndStatus(Long groupId, GroupMember.Role role, GroupMember.Status status);
    interface GroupMemberCount {
        Long getGroupId();
        long getMemberCount();
    }
}
