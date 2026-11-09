package com.sw1.umltool.features.ai.changeproposal.controller;

import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalRequest;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalResponse;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyRequest;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyResponse;
import com.sw1.umltool.features.ai.changeproposal.service.AiChangeProposalService;
import com.sw1.umltool.features.ai.changeproposal.service.AiChangeProposalApplyService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class AiChangeProposalController {
    private final AiChangeProposalService service;
    private final AiChangeProposalApplyService applyService;

    public AiChangeProposalController(AiChangeProposalService service, AiChangeProposalApplyService applyService) {
        this.service = service;
        this.applyService = applyService;
    }

    @PostMapping("/change-proposals")
    public AiChangeProposalResponse propose(@PathVariable String projectId,
                                            @RequestParam String diagramId,
                                            @RequestBody AiChangeProposalRequest request,
                                            Authentication authentication) {
        return service.propose(projectId, authentication.getName(), diagramId, request);
    }

    @PostMapping("/change-proposals/apply")
    public AiChangeProposalApplyResponse apply(@PathVariable String projectId,
                                               @Valid @RequestBody AiChangeProposalApplyRequest request,
                                               Authentication authentication) {
        return applyService.apply(projectId, authentication.getName(), request);
    }
}
