package com.sw1.umltool.features.generator.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.AssociationClassLink;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.generator.dto.GenerateBackendRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratorServiceTest {
    @Test
    void subclassDtosContainEffectiveInheritedAttributesWithoutDuplicateId() throws Exception {
        UmlClass persona = UmlClass.builder().id("persona").name("Persona")
                .attributes(List.of(UmlAttribute.builder().id("pid").name("id").type("Long").build(),
                        UmlAttribute.builder().id("pname").name("nombre").type("String").build())).build();
        UmlClass alumno = UmlClass.builder().id("alumno").name("Alumno")
                .attributes(List.of(UmlAttribute.builder().id("code").name("codigo").type("String").build())).build();
        UmlClass materia = UmlClass.builder().id("materia").name("Materia").build();
        UmlRelation inheritance = UmlRelation.builder().id("r1").sourceClassId("alumno").targetClassId("persona")
                .type(RelationType.INHERITANCE).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("1", "1")).build();
        UmlRelation association = UmlRelation.builder().id("r2").sourceClassId("alumno").targetClassId("materia")
                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(persona, alumno, materia))
                .relations(List.of(inheritance, association)).build();
        byte[] zip = generate(mockSerializer(diagram), null);

        String request = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/AlumnoRequestDto.java");
        String response = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/AlumnoResponseDto.java");
        String service = entry(zip, "generated-backend/src/main/java/com/generated/app/service/AlumnoService.java");
        assertTrue(request.contains("private String nombre") && request.contains("private String codigo"));
        assertFalse(request.contains("private Long id"));
        assertTrue(response.contains("private Long id") && response.contains("private String nombre")
                && response.contains("private String codigo"));
        assertEquals(1, response.split("private Long id", -1).length - 1);
        assertTrue(service.contains("setNombre(value.getNombre())") && service.contains("setCodigo(value.getCodigo())"));
    }

    @Test
    void requestDtosOnlyExposeOwningSideButResponsesExposeInverseSide() throws Exception {
        UmlClass curso = UmlClass.builder().id("curso").name("Curso").build();
        UmlClass horario = UmlClass.builder().id("horario").name("Horario").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("horario").targetClassId("curso")
                .type(RelationType.COMPOSITION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("1", "1")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(curso, horario))
                .relations(List.of(relation)).build();
        byte[] zip = generate(mockSerializer(diagram), null);

        String cursoRequest = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/CursoRequestDto.java");
        String cursoResponse = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/CursoResponseDto.java");
        String cursoService = entry(zip, "generated-backend/src/main/java/com/generated/app/service/CursoService.java");
        String horarioRequest = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/HorarioRequestDto.java");
        String horarioService = entry(zip, "generated-backend/src/main/java/com/generated/app/service/HorarioService.java");
        assertFalse(cursoRequest.contains("horarioId"));
        assertTrue(cursoResponse.contains("horarioId"));
        assertFalse(cursoService.contains("HorarioRepository") || cursoService.contains("entity.setHorario("));
        assertTrue(horarioRequest.contains("cursoId"));
        assertTrue(horarioService.contains("CursoRepository") && horarioService.contains("setCurso"));
    }

    @Test
    void generatesJoinedRootAndInheritedChildWithoutDuplicateId() throws Exception {
        DiagramStateSerializer serializer = mock(DiagramStateSerializer.class);
        UmlClass persona = UmlClass.builder().id("persona").name("Persona").build();
        UmlClass alumno = UmlClass.builder().id("alumno").name("Alumno").build();
        UmlRelation inheritance = UmlRelation.builder().id("r1").sourceClassId("alumno").targetClassId("persona")
                .type(RelationType.INHERITANCE).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("1", "1")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Prueba").classes(List.of(persona, alumno)).relations(List.of(inheritance)).build();
        when(serializer.deserializeCanonical("canonical")).thenReturn(diagram);

        byte[] zip = generate(serializer, diagram);

        String personaSource = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Persona.java");
        String alumnoSource = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Alumno.java");
        assertTrue(personaSource.contains("@Inheritance(strategy = InheritanceType.JOINED)"));
        assertTrue(alumnoSource.contains("class Alumno extends Persona"));
        assertFalse(alumnoSource.contains("@Inheritance"));
        assertEquals(0, alumnoSource.split("@Id", -1).length - 1);
    }

    @Test
    void simpleEntityAndCrudIncludeJavaBeanAccessorsAndPut() throws Exception {
        UmlClass producto = UmlClass.builder().id("producto").name("Producto").build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(producto)).build();
        byte[] zip = generate(mockSerializer(diagram), null);
        String entity = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Producto.java");
        String controller = entry(zip, "generated-backend/src/main/java/com/generated/app/controller/ProductoController.java");
        assertTrue(entity.contains("public Producto() {}"));
        assertTrue(entity.contains("getId()") && entity.contains("setId(Long id)"));
        assertTrue(controller.contains("@PutMapping(\"/{id}\")"));
        assertTrue(controller.contains("Producto findById"));
        assertFalse(controller.contains("Optional<Producto>"));
        assertFalse(entity.contains("@JsonIgnore"));
    }

    @Test
    void generatedBackendIncludesOpenApiAndPostmanDocumentation() throws Exception {
        UmlClass carrera = UmlClass.builder().id("carrera").name("Carrera").build();
        UmlClass materia = UmlClass.builder().id("materia").name("Materia")
                .attributes(List.of(UmlAttribute.builder().id("materia-nombre").name("nombre").type("String").build(),
                        UmlAttribute.builder().id("materia-creditos").name("creditos").type("Integer").build())).build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("carrera").targetClassId("materia")
                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(carrera, materia))
                .relations(List.of(relation)).build();
        byte[] zip = generate(mockSerializer(diagram), null);

        String pom = entry(zip, "generated-backend/pom.xml");
        String config = entry(zip, "generated-backend/src/main/java/com/generated/app/config/OpenApiConfig.java");
        String cors = entry(zip, "generated-backend/src/main/java/com/generated/app/config/CorsConfig.java");
        String controller = entry(zip, "generated-backend/src/main/java/com/generated/app/controller/MateriaController.java");
        String readme = entry(zip, "generated-backend/README.md");
        String collection = entry(zip, "generated-backend/postman/test.postman_collection.json");
        assertTrue(pom.contains("springdoc-openapi-starter-webmvc-ui") && pom.contains("2.8.9"));
        assertTrue(config.contains("@OpenAPIDefinition") && config.contains("Test API"));
        assertTrue(cors.contains("allowedOriginPatterns(\"http://localhost:*\", \"http://127.0.0.1:*\")")
                && cors.contains("addMapping(\"/**\")")
                && cors.contains("allowedMethods(\"GET\", \"POST\", \"PUT\", \"DELETE\", \"PATCH\", \"OPTIONS\")"));
        assertTrue(controller.contains("@Tag") && controller.contains("@Operation"));
        assertTrue(readme.contains("/swagger-ui.html") && readme.contains("/v3/api-docs")
                && readme.contains("postman/test.postman_collection.json"));
        assertTrue(collection.startsWith("{\"info\"")
                && collection.contains("\"key\":\"baseUrl\"")
                && collection.contains("\"value\":\"http://localhost:8080\""));
        assertTrue(collection.contains("GET all") && collection.contains("GET by ID") && collection.contains("POST")
                && collection.contains("PUT") && collection.contains("DELETE"));
        assertTrue(collection.contains("carreraId") && !collection.contains("materiaId"));
    }

    @Test
    void runtimeSchemaDescribesGenericEntitiesFieldsAndRelations() throws Exception {
        UmlClass persona = UmlClass.builder().id("persona").name("Persona")
                .attributes(List.of(UmlAttribute.builder().id("persona-id").name("id").type("Long").build(),
                        UmlAttribute.builder().id("persona-name").name("nombre").type("String").build())).build();
        UmlClass alumno = UmlClass.builder().id("alumno").name("Alumno")
                .attributes(List.of(UmlAttribute.builder().id("alumno-code").name("codigo").type("String").build())).build();
        UmlClass carrera = UmlClass.builder().id("carrera").name("Carrera")
                .attributes(List.of(UmlAttribute.builder().id("carrera-id").name("id").type("Long").build(),
                        UmlAttribute.builder().id("carrera-name").name("nombre").type("String").build())).build();
        UmlClass materia = UmlClass.builder().id("materia").name("Materia")
                .attributes(List.of(UmlAttribute.builder().id("materia-id").name("id").type("Long").build(),
                        UmlAttribute.builder().id("materia-credits").name("creditos").type("Integer").build())).build();
        UmlClass inscription = UmlClass.builder().id("inscription").name("Inscripcion").build();
        UmlRelation inheritance = UmlRelation.builder().id("inheritance").sourceClassId("alumno").targetClassId("persona")
                .type(RelationType.INHERITANCE).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("1", "1")).build();
        UmlRelation normalRelation = UmlRelation.builder().id("career-subject").sourceClassId("carrera").targetClassId("materia")
                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*")).build();
        UmlRelation associationClassRelation = UmlRelation.builder().id("student-subject").sourceClassId("alumno").targetClassId("materia")
                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*")).build();
        AssociationClassLink link = AssociationClassLink.builder().id("link").relationId("student-subject").classId("inscription").build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test")
                .classes(List.of(persona, alumno, carrera, materia, inscription))
                .relations(List.of(inheritance, normalRelation, associationClassRelation))
                .associationClassLinks(List.of(link)).build();

        byte[] zip = generate(mockSerializer(diagram), null);
        String runtime = entry(zip, "generated-backend/src/main/java/com/generated/app/controller/RuntimeSchemaController.java");

        assertTrue(runtime.contains("@GetMapping(value = \"/runtime-schema\""));
        assertTrue(runtime.contains("\\\"schemaVersion\\\":\\\"1.0\\\""));
        assertTrue(runtime.contains("@Hidden"));
        assertTrue(runtime.contains("\\\"application\\\":\\\"Test\\\""));
        assertTrue(runtime.contains("\\\"displayField\\\":\\\"nombre\\\""));
        assertTrue(runtime.contains("\\\"type\\\":\\\"integer\\\""));
        assertTrue(runtime.contains("\\\"name\\\":\\\"id\\\",\\\"type\\\":\\\"integer\\\",\\\"required\\\":true,\\\"editable\\\":false,\\\"readOnly\\\":true"));
        assertTrue(runtime.contains("\\\"name\\\":\\\"carreraId\\\",\\\"type\\\":\\\"relation\\\""));
        assertTrue(runtime.contains("\\\"targetEntity\\\":\\\"Carrera\\\",\\\"owningSide\\\":true"));
        assertTrue(runtime.contains("\\\"name\\\":\\\"materiasIds\\\",\\\"type\\\":\\\"relation\\\"")
                && runtime.contains("\\\"name\\\":\\\"materiasIds\\\",\\\"type\\\":\\\"relation\\\",\\\"required\\\":false,\\\"editable\\\":false,\\\"readOnly\\\":true,\\\"nullable\\\":true")
                && runtime.contains("\\\"collection\\\":true")
                && runtime.contains("\\\"requestField\\\":null"));
        assertTrue(runtime.contains("\\\"name\\\":\\\"alumnoId\\\",\\\"type\\\":\\\"relation\\\"")
                && runtime.contains("\\\"targetEntity\\\":\\\"Alumno\\\""));
        assertTrue(runtime.contains("\\\"name\\\":\\\"alumnoId\\\",\\\"type\\\":\\\"relation\\\",\\\"required\\\":true,\\\"editable\\\":true,\\\"readOnly\\\":false")
                && runtime.contains("\\\"requestField\\\":\\\"alumnoId\\\""));
        assertTrue(runtime.contains("\\\"name\\\":\\\"materiaId\\\",\\\"type\\\":\\\"relation\\\",\\\"required\\\":true,\\\"editable\\\":true,\\\"readOnly\\\":false,\\\"nullable\\\":false"));
        assertTrue(runtime.contains("\\\"name\\\":\\\"nombre\\\",\\\"type\\\":\\\"string\\\""));
        assertFalse(runtime.contains("\\\"targetEntity\\\":\\\"Dependency\\\""));
    }

    @Test
    void runtimeSchemaUsesFirstStringAttributeAsDisplayField() throws Exception {
        UmlClass aula = UmlClass.builder().id("aula").name("Aula")
                .attributes(List.of(UmlAttribute.builder().id("aula-id").name("id").type("Long").build(),
                        UmlAttribute.builder().id("aula-number").name("numero").type("String").build())).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(aula)).build();

        String runtime = entry(generate(mockSerializer(diagram), null),
                "generated-backend/src/main/java/com/generated/app/controller/RuntimeSchemaController.java");

        assertTrue(runtime.contains("\\\"displayField\\\":\\\"numero\\\""));
    }

    @Test
    void runtimeSchemaMarksInverseFieldsNonRequiredAndOwningRequiredWhenApplicable() throws Exception {
        UmlClass carrera = UmlClass.builder().id("carrera").name("Carrera").build();
        UmlClass aula = UmlClass.builder().id("aula").name("Aula").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("carrera").targetClassId("aula")
                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("1", "1")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test")
                .classes(List.of(carrera, aula)).relations(List.of(relation)).build();

        String runtime = entry(generate(mockSerializer(diagram), null),
                "generated-backend/src/main/java/com/generated/app/controller/RuntimeSchemaController.java");

        assertTrue(runtime.contains("\\\"name\\\":\\\"carreraId\\\",\\\"type\\\":\\\"relation\\\",\\\"required\\\":true,\\\"editable\\\":true,\\\"readOnly\\\":false,\\\"nullable\\\":false"));
        assertTrue(runtime.contains("\\\"name\\\":\\\"aulaId\\\",\\\"type\\\":\\\"relation\\\",\\\"required\\\":false,\\\"editable\\\":false,\\\"readOnly\\\":true,\\\"nullable\\\":true"));
    }

    @ParameterizedTest
    @MethodSource("runtimeCardinalities")
    void runtimeSchemaMatchesJpaRequirednessForTheSameOwningRelation(String sourceLower, String targetUpper,
            boolean expectedRequired) throws Exception {
        UmlClass source = UmlClass.builder().id("source").name("Cliente").build();
        UmlClass target = UmlClass.builder().id("target").name("Mascota").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("source").targetClassId("target")
                .type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity(sourceLower, "1"))
                .targetMultiplicity(new Multiplicity("0", targetUpper)).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(source, target))
                .relations(List.of(relation)).build();

        byte[] zip = generate(mockSerializer(diagram), null);
        String entity = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Mascota.java");
        String runtime = entry(zip, "generated-backend/src/main/java/com/generated/app/controller/RuntimeSchemaController.java");
        String association = "*".equals(targetUpper) ? "@ManyToOne" : "@OneToOne";
        String unique = "*".equals(targetUpper) ? ")" : ", unique = true)";
        String expectedJpa = association + "(optional = " + !expectedRequired + ")\n    @JoinColumn(name=\"cliente_id\", nullable = "
                + !expectedRequired + unique;
        assertTrue(entity.contains(expectedJpa), entity);
        String expectedRuntime = "\\\"name\\\":\\\"clienteId\\\",\\\"type\\\":\\\"relation\\\",\\\"required\\\":"
                + expectedRequired + ",\\\"editable\\\":true,\\\"readOnly\\\":false,\\\"nullable\\\":"
                + !expectedRequired + ",\\\"collection\\\":" + ("*".equals(targetUpper) ? "false" : "false")
                + ",\\\"relation\\\":true";
        assertTrue(runtime.contains(expectedRuntime), runtime);
    }

    private static Stream<Arguments> runtimeCardinalities() {
        return Stream.of(Arguments.of("1", "*", true), Arguments.of("0", "*", false),
                Arguments.of("1", "1", true), Arguments.of("0", "1", false));
    }

    @Test
    void runtimeSchemaNormalizesSupportedScalarTypes() throws Exception {
        UmlClass item = UmlClass.builder().id("item").name("Item")
                .attributes(List.of(
                        UmlAttribute.builder().id("item-id").name("id").type("Long").build(),
                        UmlAttribute.builder().id("amount").name("amount").type("BigDecimal").build(),
                        UmlAttribute.builder().id("active").name("active").type("Boolean").build(),
                        UmlAttribute.builder().id("date").name("date").type("LocalDate").build(),
                        UmlAttribute.builder().id("timestamp").name("timestamp").type("LocalDateTime").build())).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(item)).build();

        String runtime = entry(generate(mockSerializer(diagram), null),
                "generated-backend/src/main/java/com/generated/app/controller/RuntimeSchemaController.java");

        assertTrue(runtime.contains("\\\"type\\\":\\\"decimal\\\""));
        assertTrue(runtime.contains("\\\"type\\\":\\\"boolean\\\""));
        assertTrue(runtime.contains("\\\"type\\\":\\\"date\\\""));
        assertTrue(runtime.contains("\\\"type\\\":\\\"datetime\\\""));
        assertFalse(runtime.contains("\\\"type\\\":\\\"BigDecimal\\\"")
                || runtime.contains("\\\"type\\\":\\\"LocalDate\\\"")
                || runtime.contains("\\\"type\\\":\\\"LocalDateTime\\\""));
    }

    @Test
    void relatedEntitiesGenerateIdBasedDtosAndRepositoryResolution() throws Exception {
        UmlClass carrera = UmlClass.builder().id("carrera").name("Carrera").build();
        UmlClass materia = UmlClass.builder().id("materia").name("Materia").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("carrera").targetClassId("materia").type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity("1", "1")).targetMultiplicity(new Multiplicity("0", "*")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(carrera, materia)).relations(List.of(relation)).build();
        byte[] zip = generate(mockSerializer(diagram), null);
        String request = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/MateriaRequestDto.java");
        String response = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/MateriaResponseDto.java");
        String service = entry(zip, "generated-backend/src/main/java/com/generated/app/service/MateriaService.java");
        String controller = entry(zip, "generated-backend/src/main/java/com/generated/app/controller/MateriaController.java");
        assertTrue(request.contains("carreraId") && response.contains("carreraId"));
        assertFalse(response.contains("Carrera carrera"));
        assertTrue(service.contains("CarreraRepository") && service.contains("findById(value.getCarreraId())"));
        assertFalse(service.contains("new ArrayList<>("));
        assertTrue(service.contains("MateriaResponseDto findById"));
        assertFalse(controller.contains("Optional<MateriaResponseDto>"));
    }

    @Test
    void associationClassSuppressesDirectEndpointRelationInDtosAndServices() throws Exception {
        UmlClass alumno = UmlClass.builder().id("a").name("Alumno").build();
        UmlClass materia = UmlClass.builder().id("m").name("Materia").build();
        UmlClass inscription = UmlClass.builder().id("i").name("Inscripcion").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("m").targetClassId("a").type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity("1", "1")).targetMultiplicity(new Multiplicity("0", "*")).build();
        AssociationClassLink link = AssociationClassLink.builder().id("link").relationId("r1").classId("i").build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(alumno, materia, inscription)).relations(List.of(relation)).associationClassLinks(List.of(link)).build();
        byte[] zip = generate(mockSerializer(diagram), null);
        String alumnoDto = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/AlumnoResponseDto.java");
        String materiaDto = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/MateriaResponseDto.java");
        String alumnoService = entry(zip, "generated-backend/src/main/java/com/generated/app/service/AlumnoService.java");
        String materiaService = entry(zip, "generated-backend/src/main/java/com/generated/app/service/MateriaService.java");
        String associationDto = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/InscripcionRequestDto.java");
        String associationService = entry(zip, "generated-backend/src/main/java/com/generated/app/service/InscripcionService.java");
        String associationEntity = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Inscripcion.java");
        assertFalse(alumnoDto.contains("materiaId") || alumnoDto.contains("materiasIds"));
        assertFalse(materiaDto.contains("alumnoId") || materiaDto.contains("alumnosIds"));
        assertFalse(alumnoService.contains("MateriaRepository") || alumnoService.contains("setMaterias"));
        assertFalse(materiaService.contains("AlumnoRepository") || materiaService.contains("setAlumnos"));
                assertTrue(associationDto.contains("materiaId") && associationDto.contains("alumnoId"));
        assertTrue(associationService.contains("MateriaRepository") && associationService.contains("AlumnoRepository"));
                assertTrue(associationEntity.contains("private Materia materia;") && associationEntity.contains("private Alumno alumno;"));
                assertTrue(associationEntity.contains("@ManyToOne(optional = false)")
                        && associationEntity.contains("@JoinColumn(name=\"materia_id\", nullable = false)"));
                assertTrue(associationEntity.contains("uniqueConstraints = @UniqueConstraint(columnNames = {\"alumno_id\", \"materia_id\"})"));
    }

        @ParameterizedTest
        @MethodSource("cardinalities")
        void relationForeignKeyNullabilityFollowsReferencedMultiplicity(String sourceLower, String targetLower, boolean expectedRequired) throws Exception {
                UmlClass source = UmlClass.builder().id("source").name("Cliente").build();
                UmlClass target = UmlClass.builder().id("target").name("Mascota").build();
                UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("source").targetClassId("target")
                                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity(sourceLower, targetLower.equals("*") ? "1" : "1"))
                                .targetMultiplicity(new Multiplicity(targetLower.equals("*") ? "0" : targetLower, targetLower)).build();
                UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(source, target)).relations(List.of(relation)).build();
                String entity = entry(generate(mockSerializer(diagram), null), "generated-backend/src/main/java/com/generated/app/entity/Mascota.java");
                String association = "*".equals(targetLower) ? "@ManyToOne" : "@OneToOne";
                String unique = "*".equals(targetLower) ? ")" : ", unique = true)";
                assertPairedNullability(entity, association, "cliente_id", expectedRequired, unique);
        }

        private static Stream<Arguments> cardinalities() {
                return Stream.of(Arguments.of("1", "*", true), Arguments.of("0", "*", false),
                                Arguments.of("1", "1", true), Arguments.of("0", "1", false));
        }

        @Test
        void oneToOneForeignKeyIsUniqueAndUsesReferencedMultiplicity() throws Exception {
                UmlClass cita = UmlClass.builder().id("cita").name("Cita").build();
                UmlClass consulta = UmlClass.builder().id("consulta").name("Consulta").build();
                UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("cita").targetClassId("consulta")
                                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                                .targetMultiplicity(new Multiplicity("0", "1")).build();
                UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(cita, consulta)).relations(List.of(relation)).build();
                String entity = entry(generate(mockSerializer(diagram), null), "generated-backend/src/main/java/com/generated/app/entity/Consulta.java");
                assertPairedNullability(entity, "@OneToOne", "cita_id", true, ", unique = true)");
        }

        @Test
        void identifiersNormalizeDiacriticsAndSpecialIdAcrossGeneratedSurfaces() throws Exception {
                UmlClass clase = UmlClass.builder().id("clase").name("Ficha").attributes(List.of(
                                UmlAttribute.builder().id("id").name("ID").type("Long").build(),
                                UmlAttribute.builder().id("description").name("Descripción").type("String").build(),
                                UmlAttribute.builder().id("address").name("Dirección").type("String").build())).build();
                UmlClass owner = UmlClass.builder().id("owner").name("Owner").build();
                UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("owner").targetClassId("clase")
                                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                                .targetMultiplicity(new Multiplicity("0", "*" )).build();
                UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(clase, owner)).relations(List.of(relation)).build();
                byte[] zip = generate(mockSerializer(diagram), null);
                String entity = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Ficha.java");
                String dto = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/FichaResponseDto.java");
                String runtime = entry(zip, "generated-backend/src/main/java/com/generated/app/controller/RuntimeSchemaController.java");
                assertTrue(entity.contains("private Long id;") && entity.contains("getId()") && entity.contains("setId(Long id)"));
                assertTrue(entity.contains("private String descripcion;") && entity.contains("private String direccion;"));
                assertTrue(dto.contains("private String descripcion;") && dto.contains("getDescripcion()"));
                assertTrue(runtime.contains("\\\"idField\\\":\\\"id\\\"") && runtime.contains("\\\"name\\\":\\\"descripcion\\\""));
                assertFalse(entity.contains("descripciN"));
        }

    @Test
    void dtoImportsAllJavaTypesUsedByRequestAndResponse() throws Exception {
        UmlClass registro = UmlClass.builder().id("registro").name("Registro").attributes(List.of(
                UmlAttribute.builder().id("fecha").name("fechaNacimiento").type("LocalDate").build(),
                UmlAttribute.builder().id("hora").name("fechaHora").type("LocalDateTime").build(),
                UmlAttribute.builder().id("precio").name("precio").type("BigDecimal").build())).build();
        UmlClass owner = UmlClass.builder().id("owner").name("Owner").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("owner").targetClassId("registro")
                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*" )).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(owner, registro)).relations(List.of(relation)).build();
        byte[] zip = generate(mockSerializer(diagram), null);

        String request = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/RegistroRequestDto.java");
        String response = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/RegistroResponseDto.java");
        for (String dto : List.of(request, response)) {
            assertTrue(dto.contains("import java.time.LocalDate;"), dto);
            assertTrue(dto.contains("import java.time.LocalDateTime;"), dto);
            assertTrue(dto.contains("import java.math.BigDecimal;"), dto);
        }
    }

    @Test
    void dtoDoesNotImportUnusedDateTypes() throws Exception {
        UmlClass simple = UmlClass.builder().id("simple").name("Simple").attributes(List.of(
                UmlAttribute.builder().id("name").name("name").type("String").build(),
                UmlAttribute.builder().id("count").name("count").type("Integer").build())).build();
        UmlClass owner = UmlClass.builder().id("owner").name("Owner").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("owner").targetClassId("simple")
                .type(RelationType.ASSOCIATION).sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*" )).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(owner, simple)).relations(List.of(relation)).build();
        String request = entry(generate(mockSerializer(diagram), null), "generated-backend/src/main/java/com/generated/app/dto/SimpleRequestDto.java");

        assertFalse(request.contains("import java.time."));
        assertFalse(request.contains("import java.math.BigDecimal;"));
        assertFalse(request.contains("import java.util.List;"));
        assertFalse(request.contains("import java.util.ArrayList;"));
    }

        private void assertPairedNullability(String entity, String association, String column, boolean required, String suffix) {
                String expected = association + "(optional = " + !required + ")\n    @JoinColumn(name=\"" + column
                        + "\", nullable = " + !required + suffix;
                assertTrue(entity.contains(expected), entity);
        }

    @Test
    void aggregationGeneratesOnlyPartForeignKey() throws Exception {
        UmlClass carrera = UmlClass.builder().id("carrera").name("Carrera").build();
        UmlClass materia = UmlClass.builder().id("materia").name("Materia").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("carrera").targetClassId("materia").type(RelationType.AGGREGATION)
                .sourceMultiplicity(new Multiplicity("1", "1")).targetMultiplicity(new Multiplicity("1", "1")).build();
        byte[] zip = generate(mockSerializer(UmlDiagram.builder().id("d1").name("Test").classes(List.of(carrera, materia)).relations(List.of(relation)).build()), null);
        String whole = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Carrera.java");
        String part = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Materia.java");
        String wholeRequest = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/CarreraRequestDto.java");
        String wholeResponse = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/CarreraResponseDto.java");
        String partRequest = entry(zip, "generated-backend/src/main/java/com/generated/app/dto/MateriaRequestDto.java");
        assertFalse(whole.contains("@JoinColumn"));
        assertTrue(whole.contains("mappedBy = \"carrera\""));
        assertEquals(1, part.split("@JoinColumn", -1).length - 1);
        assertFalse(whole.contains("orphanRemoval"));
        assertFalse(wholeRequest.contains("materiaId"));
        assertTrue(wholeResponse.contains("materiaId"));
        assertTrue(partRequest.contains("carreraId"));
    }

    @Test
    void compositionKeepsCascadeOnWholeAndForeignKeyOnPart() throws Exception {
        UmlClass curso = UmlClass.builder().id("curso").name("Curso").build();
        UmlClass horario = UmlClass.builder().id("horario").name("Horario").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("horario").targetClassId("curso").type(RelationType.COMPOSITION)
                .sourceMultiplicity(new Multiplicity("1", "1")).targetMultiplicity(new Multiplicity("1", "1")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(curso, horario)).relations(List.of(relation)).build();
        byte[] zip = generate(mockSerializer(diagram), null);
        String whole = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Curso.java");
        String part = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Horario.java");
        assertTrue(whole.contains("cascade = CascadeType.ALL") && whole.contains("orphanRemoval = true"));
        assertFalse(whole.contains("@JoinColumn"));
        assertEquals(1, part.split("@JoinColumn", -1).length - 1);
        assertTrue(part.contains("@OneToOne(optional = false)\n    @JoinColumn(name=\"curso_id\", nullable = false, unique = true)"));
        assertTrue(whole.contains("@JsonIgnore"));
    }

    @Test
    void compositionOneToManyKeepsForeignKeyOnPartCollection() throws Exception {
        UmlClass curso = UmlClass.builder().id("curso").name("Curso").build();
        UmlClass horario = UmlClass.builder().id("horario").name("Horario").build();
        UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("curso").targetClassId("horario").type(RelationType.COMPOSITION)
                .sourceMultiplicity(new Multiplicity("1", "1")).targetMultiplicity(new Multiplicity("0", "*")).build();
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(curso, horario)).relations(List.of(relation)).build();
        byte[] zip = generate(mockSerializer(diagram), null);
        String whole = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Curso.java");
        String part = entry(zip, "generated-backend/src/main/java/com/generated/app/entity/Horario.java");
        assertTrue(whole.contains("@OneToMany(mappedBy = \"curso\", cascade = CascadeType.ALL, orphanRemoval = true)"));
        assertFalse(whole.contains("@JoinColumn"));
        assertTrue(part.contains("@ManyToOne"));
                assertTrue(part.contains("@ManyToOne(optional = false)\n    @JoinColumn(name=\"curso_id\", nullable = false)"));
        assertEquals(1, part.split("@JoinColumn", -1).length - 1);
    }

        @Test
        void optionalCompositionForeignKeyKeepsOptionalAndNullableInSync() throws Exception {
                UmlClass curso = UmlClass.builder().id("curso").name("Curso").build();
                UmlClass horario = UmlClass.builder().id("horario").name("Horario").build();
                UmlRelation relation = UmlRelation.builder().id("r1").sourceClassId("horario").targetClassId("curso")
                                .type(RelationType.COMPOSITION).sourceMultiplicity(new Multiplicity("0", "1"))
                                .targetMultiplicity(new Multiplicity("0", "1")).build();
                UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test").classes(List.of(curso, horario)).relations(List.of(relation)).build();
                String part = entry(generate(mockSerializer(diagram), null), "generated-backend/src/main/java/com/generated/app/entity/Horario.java");
                assertTrue(part.contains("@OneToOne(optional = true)\n    @JoinColumn(name=\"curso_id\", nullable = true, unique = true)"));
        }

    private DiagramStateSerializer mockSerializer(UmlDiagram diagram) {
        DiagramStateSerializer serializer = mock(DiagramStateSerializer.class);
        when(serializer.deserializeCanonical("canonical")).thenReturn(diagram);
        return serializer;
    }

    private byte[] generate(DiagramStateSerializer serializer, UmlDiagram ignored) {
        return new GeneratorService(serializer, new GeneratorValidator(new com.sw1.umltool.features.diagram.validation.CanonicalModelValidator()))
                .generate(DiagramEntity.builder().id("d1").name("Test").canonicalModelJson("canonical").build(), new GenerateBackendRequest(null, null, null, null));
    }

    private String entry(byte[] zip, String expected) throws Exception {
        String suffix = expected.substring(expected.indexOf('/') + 1);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (entry.getName().equals(suffix) || entry.getName().endsWith("/" + suffix)) return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        fail("ZIP entry not found: " + expected);
        return "";
    }
}
