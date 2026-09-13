package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import com.sw1.umltool.features.importexport.dto.ImageImportPreviewResponse;
import com.sw1.umltool.features.importexport.mapper.ImageUmlCanonicalMapper;
import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ImageImportService {
    static final long MAX_SIZE = 10L * 1024 * 1024;
    private final ImageUmlAiService aiService;
    private final ImageUmlCanonicalMapper mapper;
    private final CanonicalModelValidator validator;
    private final DiagramRepository repository;
    private final DiagramStateSerializer serializer;
    private final CollaborationBroadcastService broadcaster;
    private final ProjectAccessService access;

    public ImageImportService(ImageUmlAiService aiService, ImageUmlCanonicalMapper mapper,
                              CanonicalModelValidator validator, DiagramRepository repository,
                              DiagramStateSerializer serializer) {
        this.aiService = aiService; this.mapper = mapper; this.validator = validator;
        this.repository = repository; this.serializer = serializer;
        this.broadcaster = null; this.access = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ImageImportService(ImageUmlAiService aiService, ImageUmlCanonicalMapper mapper, CanonicalModelValidator validator, DiagramRepository repository, DiagramStateSerializer serializer, CollaborationBroadcastService broadcaster, ProjectAccessService access) {
        this.aiService = aiService; this.mapper = mapper; this.validator = validator; this.repository = repository; this.serializer = serializer; this.broadcaster = broadcaster; this.access = access;
    }

    public ImageImportPreviewResponse preview(MultipartFile file) {
        return parse(file, "preview", "Diagrama importado");
    }

    @Transactional
    public ImageImportPreviewResponse apply(String diagramId, long baseVersion, MultipartFile file) {
        return apply(diagramId, baseVersion, file, null);
    }
    @Transactional
    public ImageImportPreviewResponse apply(String diagramId, long baseVersion, MultipartFile file, String userId) {
        DiagramEntity entity = repository.findById(diagramId)
                .orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado"));
        if (entity.getVersion() != baseVersion) throw new VersionConflictException("El diagrama cambió en otra sesión");
        if (userId != null && access != null) access.requireDiagramEditor(diagramId, userId);
        ImageImportPreviewResponse imported = parse(file, diagramId, entity.getName());
        long newVersion = baseVersion + 1;
        imported.getCanonicalModel().setVersion(newVersion);
        entity.setCanonicalModelJson(serializer.serializeCanonical(imported.getCanonicalModel()));
        entity.setViewStateJson(serializer.serializeViewState(imported.getViewState()));
        entity.setVersion(newVersion);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        if (broadcaster != null && userId != null) {
            broadcaster.broadcastSnapshot(diagramId, userId, "IMAGE_IMPORT", imported.getCanonicalModel(), imported.getViewState());
        }
        return imported;
    }

    private ImageImportPreviewResponse parse(MultipartFile file, String diagramId, String diagramName) {
        byte[] bytes = validateAndRead(file);
        var result = mapper.map(aiService.interpret(bytes, safeName(file), detectedMime(bytes)), diagramId, diagramName);
        var validation = validator.validate(result.getCanonicalModel());
        if (!validation.isValid()) throw new IllegalArgumentException("El modelo detectado contiene errores: " + validation.getErrors());
        return result;
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("El archivo de imagen está vacío.");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("El archivo supera el límite de 10 MB.");
        String name = safeName(file).toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")))
            throw new IllegalArgumentException("Formato de imagen no compatible.");
        try {
            byte[] bytes = file.getBytes();
            String mime = detectedMime(bytes);
            String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            if (!declared.isBlank() && !declared.equals(mime)) throw new IllegalArgumentException("El contenido del archivo no coincide con su tipo declarado.");
            if (name.endsWith(".png") && !mime.equals("image/png") || (name.endsWith(".jpg") || name.endsWith(".jpeg")) && !mime.equals("image/jpeg") || name.endsWith(".webp") && !mime.equals("image/webp"))
                throw new IllegalArgumentException("El contenido del archivo no coincide con su extensión.");
            return bytes;
        } catch (IOException exception) { throw new IllegalArgumentException("No se pudo leer la imagen.", exception); }
    }

    private String detectedMime(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && new String(bytes, 0, 4).equals("RIFF") && new String(bytes, 8, 4).equals("WEBP")) return "image/webp";
        throw new IllegalArgumentException("El archivo no contiene una imagen PNG, JPG o WEBP válida.");
    }
    private String safeName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return "diagram";
        name = name.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1);
    }
}
