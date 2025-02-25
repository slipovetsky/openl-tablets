/* Copyright © 2023 EIS Group and/or one of its affiliates. All rights reserved. Unpublished work under U.S. copyright laws.
CONFIDENTIAL AND TRADE SECRET INFORMATION. No portion of this work may be copied, distributed, modified, or incorporated into any other media without EIS Group prior written consent.*/
package org.openl.rules.rest.model.converters;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.openl.rules.project.abstraction.ProjectStatus;

import java.io.IOException;

/**
 * Custom serializer for {@link ProjectStatus} enum.
 *
 * @author Vladyslav Pikus
 */
public class ProjectStatusSerializer extends JsonSerializer<ProjectStatus> {

    @Override
    public void serialize(ProjectStatus value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == ProjectStatus.VIEWING) {
            gen.writeString("OPENED");
        } else {
            gen.writeString(value.name());
        }
    }
}
