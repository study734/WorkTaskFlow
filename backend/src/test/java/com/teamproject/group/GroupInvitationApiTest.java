package com.teamproject.group;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.group.application.GroupService;
import com.teamproject.group.application.dto.GroupDtos.CreateGroupRequest;
import com.teamproject.group.domain.*;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class GroupInvitationApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupService groupService;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired GroupInvitationRepository invitations;
    @Autowired HashService hashes;

    @Test
    void leaderInvitesListsAndCancelsWhilePersonalGroupRejectsInvitations() throws Exception {
        Account owner = account("invite_owner", "invite-owner@example.com");
        long teamId = team(owner.user(), "초대 테스트 팀");

        var created = mvc.perform(post("/api/v1/groups/{groupId}/invitations", teamId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new-member@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.email").value("new-member@example.com"))
                .andReturn();
        long invitationId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(get("/api/v1/groups/{groupId}/invitations", teamId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(invitationId));
        mvc.perform(delete("/api/v1/groups/{groupId}/invitations/{invitationId}", teamId, invitationId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        long personalId = groupService.list(owner.user().getId()).stream()
                .filter(group -> group.type().equals("PERSONAL")).findFirst().orElseThrow().id();
        mvc.perform(post("/api/v1/groups/{groupId}/invitations", personalId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERSONAL_GROUP_RESTRICTED"));
    }

    @Test
    void matchingAccountAcceptsOnceAndCanReadMemberList() throws Exception {
        Account owner = account("accept_owner", "accept-owner@example.com");
        Account invited = account("accept_member", "accept-member@example.com");
        long teamId = team(owner.user(), "수락 테스트 팀");
        String rawToken = "known-invitation-token";
        saveInvitation(teamId, owner.user(), invited.user().getEmail(), rawToken, LocalDateTime.now().plusHours(1));

        mvc.perform(post("/api/v1/group-invitations/{token}/accept", rawToken)
                        .header("Authorization", bearer(invited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.nickname").value("초대 사용자"));
        mvc.perform(get("/api/v1/groups/{groupId}/members", teamId)
                        .header("Authorization", bearer(invited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/v1/group-invitations/{token}/accept", rawToken)
                        .header("Authorization", bearer(invited)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void wrongAccountAndExpiredTokenCannotAccept() throws Exception {
        Account owner = account("secure_owner", "secure-owner@example.com");
        Account invited = account("secure_invited", "secure-invited@example.com");
        Account wrong = account("secure_wrong", "secure-wrong@example.com");
        long teamId = team(owner.user(), "보안 초대 팀");
        saveInvitation(teamId, owner.user(), invited.user().getEmail(), "email-bound-token", LocalDateTime.now().plusHours(1));

        mvc.perform(post("/api/v1/group-invitations/{token}/accept", "email-bound-token")
                        .header("Authorization", bearer(wrong)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVITATION_EMAIL_MISMATCH"));

        saveInvitation(teamId, owner.user(), invited.user().getEmail(), "expired-invitation-token",
                LocalDateTime.now().minusSeconds(1));
        mvc.perform(post("/api/v1/group-invitations/{token}/accept", "expired-invitation-token")
                        .header("Authorization", bearer(invited)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void ordinaryMemberCannotManageInvitations() throws Exception {
        Account owner = account("permission_owner", "permission-owner@example.com");
        Account member = account("permission_member", "permission-member@example.com");
        long teamId = team(owner.user(), "초대 권한 팀");
        members.save(GroupMember.member(groups.findById(teamId).orElseThrow(), member.user()));

        mvc.perform(post("/api/v1/groups/{groupId}/invitations", teamId)
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"another@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
        mvc.perform(post("/api/v1/groups/{groupId}/invite-links", teamId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
    }

    @Test
    void sharedInviteLinkCanAddMultipleMembersAndBeRevoked() throws Exception {
        Account owner = account("link_owner", "link-owner@example.com");
        Account first = account("link_first", "link-first@example.com");
        Account second = account("link_second", "link-second@example.com");
        Account blocked = account("link_blocked", "link-blocked@example.com");
        long teamId = team(owner.user(), "링크 초대 팀");

        var created = mvc.perform(post("/api/v1/groups/{groupId}/invite-links", teamId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.url").isString())
                .andReturn();
        String url = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.url");
        String token = url.substring(url.indexOf("token=") + 6);
        long linkId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(get("/api/v1/groups/{groupId}/invite-links", teamId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(linkId))
                .andExpect(jsonPath("$[0].url").doesNotExist());

        mvc.perform(post("/api/v1/group-invitations/{token}/accept", token)
                        .header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mvc.perform(post("/api/v1/group-invitations/{token}/accept", token)
                        .header("Authorization", bearer(second)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/groups/{groupId}/invite-links/{linkId}", teamId, linkId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/group-invitations/{token}/accept", token)
                        .header("Authorization", bearer(blocked)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void memberCanJoinWithGroupKeyAndKeyIsHiddenFromMembers() throws Exception {
        Account owner = account("key_owner", "key-owner@example.com");
        Account member = account("key_member", "key-member@example.com");
        long teamId = team(owner.user(), "키 참여 팀");
        String joinCode = groupService.rotateJoinCode(owner.user().getId(), teamId).joinCode();

        mvc.perform(post("/api/v1/groups/join")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + joinCode.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teamId))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.joinCode").doesNotExist());
        mvc.perform(post("/api/v1/groups/join")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + joinCode + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_ALREADY_JOINED"));
    }

    @Test
    void freeAndPersonalReportsAreUnlimitedWhileGroupScopeStillRequiresLeader() throws Exception {
        Account owner = account("report_owner", "report-owner@example.com");
        Account member = account("report_member", "report-member@example.com");
        long teamId = team(owner.user(), "무료 리포트 팀");
        members.save(GroupMember.member(groups.findById(teamId).orElseThrow(), member.user()));
        String groupReport = "{\"scope\":\"GROUP\",\"periodType\":\"WEEKLY\"}";

        for (int index = 0; index < 4; index++) {
            mvc.perform(post("/api/v1/groups/{groupId}/reports/access", teamId)
                            .header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(groupReport))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.membershipPlan").value("FREE"))
                    .andExpect(jsonPath("$.remainingThisWeek").doesNotExist());
        }
        mvc.perform(post("/api/v1/groups/{groupId}/reports/access", teamId)
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"MY\",\"periodType\":\"YEARLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingThisWeek").doesNotExist());
        mvc.perform(post("/api/v1/groups/{groupId}/reports/access", teamId)
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(groupReport))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
    }

    private Account account(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "초대 사용자", "password123!", code));
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        String token = sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
        return new Account(user, token);
    }

    private long team(User owner, String name) {
        return groupService.createTeam(owner.getId(), new CreateGroupRequest(name, null, "Asia/Seoul")).id();
    }

    private void saveInvitation(long groupId, User owner, String email, String rawToken, LocalDateTime expiresAt) {
        Group group = groups.findById(groupId).orElseThrow();
        GroupMember inviter = members.findByGroupIdAndUserIdAndStatus(
                groupId, owner.getId(), GroupMember.Status.ACTIVE).orElseThrow();
        invitations.save(new GroupInvitation(group, email, inviter, hashes.sha256(rawToken), expiresAt));
    }

    private String bearer(Account account) { return "Bearer " + account.token(); }
    private record Account(User user, String token) {}
}
