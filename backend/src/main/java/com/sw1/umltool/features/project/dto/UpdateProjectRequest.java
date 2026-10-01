package com.sw1.umltool.features.project.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProjectRequest {
    private String name;
    private String description;
    @JsonIgnore private boolean nameProvided;
    @JsonIgnore private boolean descriptionProvided;

    @JsonProperty("name")
    public void setName(String name) { this.name = name; this.nameProvided = true; }

    @JsonProperty("description")
    public void setDescription(String description) { this.description = description; this.descriptionProvided = true; }
}
