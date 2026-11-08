package com.sw1.umltool.features.ai.analysis.service;

import com.sw1.umltool.features.ai.analysis.dto.AnalysisFinding;
import com.sw1.umltool.features.ai.analysis.model.AnalysisCategory;

import java.util.Locale;

/**
 * Conservative admission policy for model-generated candidates.
 *
 * The model may explain a candidate, but it cannot establish facts that are
 * not represented by the canonical model or an explicitly scoped PROJECT rule.
 */
final class AiAnalysisEvidencePolicy {
    Decision evaluate(AnalysisFinding finding,
                      AiAnalysisContextService.ElementDescriptor descriptor,
                      AiAnalysisContextService.PreparedContext prepared) {
        if (finding == null) return Decision.reject(Rule.INVALID_FINDING);

        if (finding.category() == AnalysisCategory.NAMING) {
            if (descriptor != null && descriptor.kind() == AiAnalysisContextService.ElementKind.ATTRIBUTE
                    && !prepared.attributeNamingConventionExplicit()) {
                return Decision.reject(Rule.PROJECT_RULE_SCOPE);
            }
            if (isLinguisticNumberClaim(finding)
                    && (descriptor == null || descriptor.kind() == AiAnalysisContextService.ElementKind.CLASS)
                    && !prepared.classNamingConventionExplicit()) {
                return Decision.reject(Rule.UNVERIFIED_LINGUISTIC_CLAIM);
            }
            if (isLinguisticNumberClaim(finding)
                    && descriptor != null
                    && descriptor.kind() == AiAnalysisContextService.ElementKind.CLASS) {
                // Singular/plural is not a deterministic UML property. A
                // PROJECT convention does not make the generated claim true.
                return Decision.reject(Rule.UNVERIFIED_LINGUISTIC_CLAIM);
            }
        }

        if (finding.category() == AnalysisCategory.CONSISTENCY) {
            // No deterministic consistency rule currently emits evidence for
            // semantic redundancy or domain duplication.
            return Decision.reject(Rule.UNVERIFIED_SEMANTIC_CLAIM);
        }

        if (finding.category() == AnalysisCategory.RELATIONSHIP
                && descriptor != null
                && descriptor.relationRolesMissing()
                && descriptor.relationMultiplicitiesDefined()
                && mentionsRoleRequirement(finding)) {
            return Decision.reject(Rule.OPTIONAL_RELATIONSHIP_ROLE);
        }

        if (finding.category() == AnalysisCategory.MULTIPLICITY
                && descriptor != null
                && descriptor.kind() == AiAnalysisContextService.ElementKind.RELATION
                && descriptor.relationMultiplicitiesDefined()) {
            return Decision.reject(Rule.CONTRADICTS_CANONICAL_FACT);
        }

        return Decision.accept();
    }

    private boolean isLinguisticNumberClaim(AnalysisFinding finding) {
        String text = text(finding);
        return text.contains("singular") || text.contains("plural") || text.contains("pluralidad");
    }

    private boolean mentionsRoleRequirement(AnalysisFinding finding) {
        String text = text(finding);
        return text.contains("rol") || text.contains("role")
                || text.contains("nombre de la asociaci") || text.contains("nombre de relaci");
    }

    private String text(AnalysisFinding finding) {
        return ((safe(finding.title()) + " " + safe(finding.description()) + " " + safe(finding.suggestion()))
                .toLowerCase(Locale.ROOT));
    }

    private String safe(String value) { return value == null ? "" : value; }

    enum Rule {
        INVALID_FINDING,
        PROJECT_RULE_SCOPE,
        UNVERIFIED_LINGUISTIC_CLAIM,
        UNVERIFIED_SEMANTIC_CLAIM,
        OPTIONAL_RELATIONSHIP_ROLE,
        CONTRADICTS_CANONICAL_FACT
    }

    record Decision(boolean accepted, Rule rule) {
        static Decision accept() { return new Decision(true, null); }
        static Decision reject(Rule rule) { return new Decision(false, rule); }
    }
}
