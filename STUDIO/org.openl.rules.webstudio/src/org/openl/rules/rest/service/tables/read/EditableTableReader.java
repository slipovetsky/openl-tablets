package org.openl.rules.rest.service.tables.read;

import java.util.function.Supplier;

import org.openl.rules.rest.model.tables.TableView;
import org.openl.rules.table.IOpenLTable;

public abstract class EditableTableReader<T extends TableView, R extends TableView.Builder<?>> extends TableReader<T, R> {

    public EditableTableReader(Supplier<R> builderCreator) {
        super(builderCreator);
    }

    public abstract boolean supports(IOpenLTable table);
}
