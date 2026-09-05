package com.sw1.umltool.features.diagram.operation;

import com.sw1.umltool.features.diagram.operation.payload.AddAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.AddMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.AddParameterPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeMultiplicityPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeNavigabilityPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeRelationRolesPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeRelationTypePayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateAssociationClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateAssociationClassLinkPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteAssociationClassLinkPayload;
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
import com.sw1.umltool.features.diagram.operation.payload.UpdateClassStylePayload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DiagramOperationPayloadMapper {

    private final ObjectMapper objectMapper;

    public DiagramOperationPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DiagramOperation map(DiagramOperation operation) {
        if (operation == null || operation.getType() == null || operation.getPayload() == null) {
            throw new OperationApplicationException("Operation type and payload are required");
        }
        Class<?> payloadType = switch (operation.getType()) {
            case CREATE_CLASS -> CreateClassPayload.class;
            case DELETE_CLASS -> DeleteClassPayload.class;
            case RENAME_CLASS -> RenameClassPayload.class;
            case SET_CLASS_ABSTRACT -> SetClassAbstractPayload.class;
            case ADD_ATTRIBUTE -> AddAttributePayload.class;
            case UPDATE_ATTRIBUTE -> UpdateAttributePayload.class;
            case REMOVE_ATTRIBUTE -> RemoveAttributePayload.class;
            case ADD_METHOD -> AddMethodPayload.class;
            case UPDATE_METHOD -> UpdateMethodPayload.class;
            case REMOVE_METHOD -> RemoveMethodPayload.class;
            case ADD_PARAMETER -> AddParameterPayload.class;
            case UPDATE_PARAMETER -> UpdateParameterPayload.class;
            case REMOVE_PARAMETER -> RemoveParameterPayload.class;
            case CREATE_RELATION -> CreateRelationPayload.class;
            case DELETE_RELATION -> DeleteRelationPayload.class;
            case CREATE_ASSOCIATION_CLASS -> CreateAssociationClassPayload.class;
            case CREATE_ASSOCIATION_CLASS_LINK -> CreateAssociationClassLinkPayload.class;
            case DELETE_ASSOCIATION_CLASS_LINK -> DeleteAssociationClassLinkPayload.class;
            case CHANGE_RELATION_TYPE -> ChangeRelationTypePayload.class;
            case CHANGE_MULTIPLICITY -> ChangeMultiplicityPayload.class;
            case CHANGE_RELATION_ROLES -> ChangeRelationRolesPayload.class;
            case CHANGE_NAVIGABILITY -> ChangeNavigabilityPayload.class;
            case MOVE_CLASS -> MoveClassPayload.class;
            case RESIZE_CLASS -> ResizeClassPayload.class;
            case UPDATE_CLASS_STYLE -> UpdateClassStylePayload.class;
        };
        try {
            operation.setPayload(objectMapper.convertValue(operation.getPayload(), payloadType));
            return operation;
        } catch (IllegalArgumentException exception) {
            throw new OperationApplicationException("Invalid payload for operation: " + operation.getType());
        }
    }
}
