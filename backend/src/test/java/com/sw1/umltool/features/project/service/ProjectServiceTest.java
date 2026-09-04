package com.sw1.umltool.features.project.service;

import com.sw1.umltool.features.project.model.ProjectEntity;
import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.repository.ProjectMemberRepository;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectMemberRepository projectMemberRepository;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        service = new ProjectService(projectRepository, projectMemberRepository);
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectMemberRepository.save(any(ProjectMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsProjectAndOwnerMember() {
        ProjectEntity project = service.createProject("CRM", "Description", "user-1");

        assertNotNull(project.getId());
        assertFalse(project.getId().isBlank());
        assertEquals("CRM", project.getName());
        verify(projectRepository).save(project);
        verify(projectMemberRepository).save(org.mockito.ArgumentMatchers.argThat(member ->
                project.getId().equals(member.getProjectId())
                        && "user-1".equals(member.getUserId())
                        && ProjectMemberRole.OWNER.equals(member.getRole())));
    }

    @Test
    void emptyNameFails() {
        assertThrows(IllegalArgumentException.class, () -> service.createProject("", "Description", "user-1"));
    }

    @Test
    void emptyOwnerFails() {
        assertThrows(IllegalArgumentException.class, () -> service.createProject("CRM", "Description", ""));
    }
}
