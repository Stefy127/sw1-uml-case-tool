package com.sw1.umltool.features.project.dto;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class AddProjectMemberRequest { @NotBlank @Email private String email; @NotNull private ProjectMemberRole role; }
