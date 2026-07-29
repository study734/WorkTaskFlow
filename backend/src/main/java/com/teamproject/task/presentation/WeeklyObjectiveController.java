package com.teamproject.task.presentation;

import com.teamproject.task.application.WeeklyObjectiveModule;
import com.teamproject.task.application.WeeklyObjectiveModule.ObjectiveView;
import com.teamproject.task.application.WeeklyObjectiveModule.TaskObjectiveView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class WeeklyObjectiveController {
    private final WeeklyObjectiveModule objectives;

    public WeeklyObjectiveController(WeeklyObjectiveModule objectives) {
        this.objectives = objectives;
    }

    @GetMapping("/groups/{groupId}/weekly-objectives")
    List<ObjectiveView> list(Authentication authentication, @PathVariable Long groupId,
            @RequestParam LocalDate weekStart) {
        return objectives.list(userId(authentication), groupId, weekStart);
    }

    @PostMapping("/groups/{groupId}/weekly-objectives")
    ResponseEntity<ObjectiveView> create(Authentication authentication,
            @PathVariable Long groupId, @Valid @RequestBody CreateObjectiveRequest request) {
        ObjectiveView created = objectives.create(userId(authentication), groupId,
                request.weekStart(), request.title(), request.position());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/weekly-objectives/{objectiveId}")
    ObjectiveView update(Authentication authentication, @PathVariable Long objectiveId,
            @Valid @RequestBody UpdateObjectiveRequest request) {
        return objectives.update(userId(authentication), objectiveId, request.title(),
                request.position(), request.expectedVersion());
    }

    @DeleteMapping("/weekly-objectives/{objectiveId}")
    ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long objectiveId,
            @RequestParam @PositiveOrZero long expectedVersion) {
        objectives.delete(userId(authentication), objectiveId, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tasks/{taskId}/weekly-objective")
    TaskObjectiveView findTaskLink(Authentication authentication, @PathVariable Long taskId,
            @RequestParam LocalDate weekStart) {
        return objectives.findTaskLink(userId(authentication), taskId, weekStart);
    }

    @PutMapping("/tasks/{taskId}/weekly-objective")
    TaskObjectiveView linkTask(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody LinkTaskObjectiveRequest request) {
        return objectives.linkTask(userId(authentication), taskId,
                request.weekStart(), request.objectiveId());
    }

    private Long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    record CreateObjectiveRequest(
            @NotNull LocalDate weekStart,
            @NotBlank @Size(max = 120) String title,
            @Min(1) @Max(3) int position) {}

    record UpdateObjectiveRequest(
            @NotBlank @Size(max = 120) String title,
            @Min(1) @Max(3) int position,
            @PositiveOrZero long expectedVersion) {}

    record LinkTaskObjectiveRequest(@NotNull LocalDate weekStart, Long objectiveId) {}
}
