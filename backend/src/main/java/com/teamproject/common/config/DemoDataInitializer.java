package com.teamproject.common.config;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("!test")
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean demoEnabled;
    private final String demoUsername;

    public DemoDataInitializer(
            UserRepository userRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.enabled:true}") boolean demoEnabled,
            @Value("${app.demo.username:demo_leader}") String demoUsername
    ) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoEnabled = demoEnabled;
        this.demoUsername = demoUsername;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!demoEnabled) {
            return;
        }

        String passwordHash = passwordEncoder.encode("password123!");
        var existingUserOpt = userRepository.findByUsernameIgnoreCase(demoUsername);

        User demoLeader;
        if (existingUserOpt.isPresent()) {
            demoLeader = existingUserOpt.get();
            demoLeader.changePassword(passwordHash);
            demoLeader = userRepository.save(demoLeader);
            log.info("Reset demo leader user '{}' password to 'password123!'", demoUsername);
        } else {
            demoLeader = new User(demoUsername, "demo_leader@totaskflow.local", passwordHash, "데모 팀장", true);
            demoLeader = userRepository.save(demoLeader);
            log.info("Created demo leader user '{}' with password 'password123!'", demoUsername);
        }

        List<GroupMember> memberships = groupMemberRepository.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(demoLeader.getId(), GroupMember.Status.ACTIVE);
        boolean hasPaidGroup = false;

        for (GroupMember member : memberships) {
            Group group = member.getGroup();
            if (group.getType() == Group.Type.TEAM) {
                if (group.getMembershipPlan() != Group.MembershipPlan.PAID) {
                    group.switchTestMembership(Group.MembershipPlan.PAID, LocalDateTime.now());
                    groupRepository.save(group);
                    log.info("Upgraded existing group '{}' (ID: {}) to PAID membership", group.getName(), group.getId());
                }
                hasPaidGroup = true;
            }
        }

        if (!hasPaidGroup) {
            Group teamGroup = Group.team("데모 유료 팀", "데모용 유료 팀 그룹입니다.", "Asia/Seoul", demoLeader);
            teamGroup.switchTestMembership(Group.MembershipPlan.PAID, LocalDateTime.now());
            teamGroup = groupRepository.save(teamGroup);
            groupMemberRepository.save(GroupMember.leader(teamGroup, demoLeader));
            log.info("Created new paid team group '{}' (ID: {}) for demo leader", teamGroup.getName(), teamGroup.getId());
        }
    }
}
