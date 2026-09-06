package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import com.sw1.umltool.features.importexport.dto.XmiImportPreviewResponse;
import com.sw1.umltool.features.importexport.parser.XmiParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class XmiImportService {
    private static final Logger log = LoggerFactory.getLogger(XmiImportService.class);
    private final XmiParser parser;
    private final CanonicalModelValidator validator;
    private final DiagramRepository repository;
    private final DiagramStateSerializer serializer;

    public XmiImportService(XmiParser parser, CanonicalModelValidator validator, DiagramRepository repository,
                            DiagramStateSerializer serializer) {
        this.parser = parser;
        this.validator = validator;
        this.repository = repository;
        this.serializer = serializer;
    }

    public XmiImportPreviewResponse preview(MultipartFile file) {
        return parse(file, "preview", "Diagrama importado");
    }

    @Transactional
    public XmiImportPreviewResponse apply(String diagramId, long baseVersion, MultipartFile file) {
        DiagramEntity entity = repository.findById(diagramId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));
        if (entity.getVersion() != baseVersion) throw new VersionConflictException("El diagrama cambió en otra sesión");
        XmiImportPreviewResponse imported = parse(file, diagramId, entity.getName());
        UmlDiagram canonical = imported.getCanonicalModel();
        canonical.setVersion(baseVersion + 1);
        entity.setCanonicalModelJson(serializer.serializeCanonical(canonical));
        entity.setViewStateJson(serializer.serializeViewState(imported.getViewState()));
        entity.setVersion(baseVersion + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        imported.setCanonicalModel(canonical);
        return imported;
    }

    private XmiImportPreviewResponse parse(MultipartFile file, String diagramId, String name) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("El archivo XMI está vacío");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xmi") && !filename.endsWith(".xml")) {
            throw new IllegalArgumentException("Solo se aceptan archivos .xmi o .xml");
        }
        try {
            XmiImportPreviewResponse result = parser.parse(file.getBytes(), diagramId, name);
            var validation = validator.validate(result.getCanonicalModel());
            if (!validation.isValid()) {
                log.warn("XMI validation failed: classes={}, relations={}, links={}, classIds={}, relationDetails={}, errors={}",
                        result.getCanonicalModel().getClasses().size(),
                        result.getCanonicalModel().getRelations().size(),
                        result.getCanonicalModel().getAssociationClassLinks().size(),
                        result.getCanonicalModel().getClasses().stream().map(umlClass -> umlClass.getId()).toList(),
                        result.getCanonicalModel().getRelations().stream().map(relation -> relation.getId() + " " + relation.getSourceClassId() + "->" + relation.getTargetClassId()
                                + " " + relation.getType() + " " + relation.getSourceMultiplicity() + "/" + relation.getTargetMultiplicity()).toList(),
                        validation.getErrors());
            }
            if (!validation.isValid()) throw new IllegalArgumentException("El modelo importado no es válido");
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el archivo XMI", exception);
        }
    }
}
