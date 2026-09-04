package com.sw1.umltool.features.project.controller;

import com.sw1.umltool.common.exception.GlobalExceptionHandler;
import com.sw1.umltool.features.project.model.ProjectEntity;
import com.sw1.umltool.features.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerTest {

    private ProjectService projectService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(projectService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validPostReturnsCreated() throws Exception {
        when(projectService.createProject("CRM", "Description", "user-1")).thenReturn(project());

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CRM\",\"description\":\"Description\",\"ownerUserId\":\"user-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CRM"));
    }

    @Test
    void invalidPostReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"ownerUserId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getExistingProjectReturnsOk() throws Exception {
        when(projectService.findById("project-1")).thenReturn(Optional.of(project()));

        mockMvc.perform(get("/api/projects/project-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("project-1"));
    }

    @Test
    void getMissingProjectReturnsNotFound() throws Exception {
        when(projectService.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByOwnerReturnsList() throws Exception {
        when(projectService.findByOwnerUserId("user-1")).thenReturn(List.of(project()));

        mockMvc.perform(get("/api/projects").param("ownerUserId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerUserId").value("user-1"));
    }

    private ProjectEntity project() {
        return ProjectEntity.builder().id("project-1").name("CRM").ownerUserId("user-1").build();
    }
}
