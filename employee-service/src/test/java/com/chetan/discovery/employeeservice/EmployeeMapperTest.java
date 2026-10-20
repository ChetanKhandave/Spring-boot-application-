package com.chetan.discovery.employeeservice;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the actual generated mapper, including null inputs and independent object identity. */
class EmployeeMapperTest {
    private final EmployeeMapper mapper = Mappers.getMapper(EmployeeMapper.class);
    @Test void mapsBothDirectionsWithoutChangingSource() {
        var model = new EmployeeModel(7L, "Original");
        var entity = mapper.toEntity(model);
        assertEquals(model, mapper.toModel(entity));
        entity.setName("Changed");
        assertEquals("Original", model.name());
        assertEquals("Changed", mapper.toModel(entity).name());
    }
    @Test void handlesNullsAndNewRecords() {
        assertNull(mapper.toModel(null));
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toEntity(new EmployeeModel(null, "New")).getId());
    }
}
