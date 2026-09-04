package com.sw1.umltool.features.diagram.operation;

import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.operation.payload.AddAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.AddMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.AddParameterPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeMultiplicityPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeNavigabilityPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeRelationRolesPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeRelationTypePayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.MoveClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.RemoveAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.RemoveMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.RemoveParameterPayload;
import com.sw1.umltool.features.diagram.operation.payload.RenameClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.ResizeClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.SetClassAbstractPayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateParameterPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiagramOperationTest {

    @Test
    void buildsRenameClassOperation() {
        RenameClassPayload payload = RenameClassPayload.builder()
                .classId("class-1")
                .name("Customer")
                .build();

        DiagramOperation operation = DiagramOperation.builder()
                .operationId("operation-1")
                .diagramId("diagram-1")
                .userId("user-1")
                .baseVersion(3)
                .type(DiagramOperationType.RENAME_CLASS)
                .payload(payload)
                .build();

        assertEquals(DiagramOperationType.RENAME_CLASS, operation.getType());
        assertEquals(payload, operation.getPayload());
    }

    @Test
    void buildsCreateRelationOperation() {
        UmlRelation relation = UmlRelation.builder()
                .id("relation-1")
                .sourceClassId("class-1")
                .targetClassId("class-2")
                .type(RelationType.ASSOCIATION)
                .build();

        CreateRelationPayload payload = CreateRelationPayload.builder()
                .relation(relation)
                .build();

        DiagramOperation operation = DiagramOperation.builder()
                .type(DiagramOperationType.CREATE_RELATION)
                .payload(payload)
                .build();

        assertEquals(DiagramOperationType.CREATE_RELATION, operation.getType());
        assertEquals(relation, ((CreateRelationPayload) operation.getPayload()).getRelation());
    }

    @Test
    void buildsMainPayloads() {
        UmlAttribute attribute = UmlAttribute.builder().id("attribute-1").name("id").build();
        UmlMethod method = UmlMethod.builder().id("method-1").name("find").build();
        UmlParameter parameter = UmlParameter.builder().id("parameter-1").name("id").build();
        UmlRelation relation = UmlRelation.builder().id("relation-1").build();

        assertEquals("Customer", CreateClassPayload.builder().classId("class-1").name("Customer")
                .isAbstract(false).x(10).y(20).width(200).height(100).build().getName());
        assertEquals("class-1", DeleteClassPayload.builder().classId("class-1").build().getClassId());
        assertEquals("Customer", RenameClassPayload.builder().classId("class-1").name("Customer").build().getName());
        assertEquals("class-1", SetClassAbstractPayload.builder().classId("class-1").isAbstract(true).build().getClassId());
        assertEquals(attribute, AddAttributePayload.builder().classId("class-1").attribute(attribute).build().getAttribute());
        assertNull(UpdateAttributePayload.builder().classId("class-1").attributeId("attribute-1").build().getName());
        assertEquals("attribute-1", RemoveAttributePayload.builder().classId("class-1").attributeId("attribute-1").build().getAttributeId());
        assertEquals(method, AddMethodPayload.builder().classId("class-1").method(method).build().getMethod());
        assertNull(UpdateMethodPayload.builder().classId("class-1").methodId("method-1").build().getName());
        assertEquals("method-1", RemoveMethodPayload.builder().classId("class-1").methodId("method-1").build().getMethodId());
        assertEquals(parameter, AddParameterPayload.builder().classId("class-1").methodId("method-1").parameter(parameter).build().getParameter());
        assertEquals("parameter-1", UpdateParameterPayload.builder().parameterId("parameter-1").build().getParameterId());
        assertEquals("parameter-1", RemoveParameterPayload.builder().parameterId("parameter-1").build().getParameterId());
        assertEquals(relation, CreateRelationPayload.builder().relation(relation).build().getRelation());
        assertEquals("relation-1", DeleteRelationPayload.builder().relationId("relation-1").build().getRelationId());
        assertEquals(RelationType.COMPOSITION, ChangeRelationTypePayload.builder().relationId("relation-1")
                .type(RelationType.COMPOSITION).build().getType());
        assertEquals("relation-1", ChangeMultiplicityPayload.builder().relationId("relation-1").build().getRelationId());
        assertEquals("source", ChangeRelationRolesPayload.builder().sourceRole("source").build().getSourceRole());
        assertEquals(Boolean.TRUE, ChangeNavigabilityPayload.builder().sourceNavigable(true).build().getSourceNavigable());
        assertEquals(10, MoveClassPayload.builder().classId("class-1").x(10).y(20).build().getX());
        assertEquals(200, ResizeClassPayload.builder().classId("class-1").width(200).height(100).build().getWidth());
    }
}
