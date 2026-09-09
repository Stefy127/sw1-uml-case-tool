package com.sw1.umltool.features.project.dto;
import com.sw1.umltool.features.auth.model.UserEntity;
import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import lombok.Builder;
@Builder public record ProjectMemberResponse(String id, String userId, String firstName, String lastName, String email, ProjectMemberRole role) {
    public static ProjectMemberResponse from(ProjectMemberEntity member, UserEntity user) { return builder().id(member.getId()).userId(user.getId()).firstName(user.getFirstName()).lastName(user.getLastName()).email(user.getEmail()).role(member.getRole()).build(); }
}
