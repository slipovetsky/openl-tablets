package org.openl.rules.lang.xls.binding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.openl.OpenL;
import org.openl.binding.IBindingContext;
import org.openl.binding.IMemberBoundNode;
import org.openl.engine.OpenLManager;
import org.openl.rules.binding.RulesModuleBindingContext;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.table.IGridTable;
import org.openl.rules.table.openl.GridCellSourceCodeModule;
import org.openl.rules.table.properties.ITableProperties;
import org.openl.rules.table.properties.def.TablePropertyDefinitionUtils;
import org.openl.source.IOpenSourceCodeModule;
import org.openl.source.impl.SubTextSourceCodeModule;
import org.openl.syntax.exception.SyntaxNodeException;
import org.openl.syntax.exception.SyntaxNodeExceptionUtils;
import org.openl.types.IOpenClass;
import org.openl.types.impl.OpenMethodHeader;
import org.openl.util.StringUtils;
import org.openl.util.text.TextInfo;

/**
 * Node binder for executable nodes with check for duplicates.
 *
 * @author PUdalau
 */
public abstract class AExecutableNodeBinder extends AXlsTableBinder {

    @Override
    public IMemberBoundNode preBind(TableSyntaxNode tableSyntaxNode,
            OpenL openl,
            RulesModuleBindingContext bindingContext,
            XlsModuleOpenClass module) throws Exception {

        OpenMethodHeader header = createHeader(tableSyntaxNode, openl, bindingContext);
        header.setDeclaringClass(module);

        checkForDuplicates(tableSyntaxNode, bindingContext, header);

        return createNode(tableSyntaxNode, openl, header, module);
    }

    public IOpenSourceCodeModule createHeaderSource(TableSyntaxNode tableSyntaxNode,
            IBindingContext bindingContext) throws SyntaxNodeException {
        IGridTable table = tableSyntaxNode.getGridTable();
        IOpenSourceCodeModule source = new GridCellSourceCodeModule(table, bindingContext);

        return new SubTextSourceCodeModule(source,
            tableSyntaxNode.getHeader()
                .getHeaderToken()
                .getSourceLocation()
                .getEnd()
                .getAbsolutePosition(new TextInfo(source.getCode())));
    }

    public OpenMethodHeader createHeader(TableSyntaxNode tableSyntaxNode,
            OpenL openl,
            RulesModuleBindingContext bindingContext) throws SyntaxNodeException {
        try {
            bindingContext.setIgnoreCustomSpreadsheetResultCompilation(true);
            IOpenSourceCodeModule headerSource = createHeaderSource(tableSyntaxNode, bindingContext);
            OpenMethodHeader methodHeader = (OpenMethodHeader) OpenLManager
                .makeMethodHeader(openl, headerSource, bindingContext);
            if (methodHeader == null) {
                throw SyntaxNodeExceptionUtils.createError("Invalid method header.", tableSyntaxNode);
            }
            return methodHeader;
        } finally {
            bindingContext.setIgnoreCustomSpreadsheetResultCompilation(false);
        }
    }

    protected abstract IMemberBoundNode createNode(TableSyntaxNode tsn,
            OpenL openl,
            OpenMethodHeader header,
            XlsModuleOpenClass module);

    private void checkForDuplicates(TableSyntaxNode tableSyntaxNode,
            RulesModuleBindingContext bindingContext,
            OpenMethodHeader header) throws DuplicatedTableException {

        String key = makeKey(tableSyntaxNode, header);

        if (!bindingContext.isTableSyntaxNodePresented(key)) {
            bindingContext.registerTableSyntaxNode(key, tableSyntaxNode);
        } else {
            throw new DuplicatedTableException(tableSyntaxNode.getDisplayName(), tableSyntaxNode);
        }
    }

    /**
     * Makes table key.
     *
     * @param tableSyntaxNode table syntax node for key generation.
     * @param header header for executable table syntax node with its signature
     * @return key to check uniqueness of table syntax node(generated by table name, arguments types, dimensional
     *         properties and version)
     */
    private String makeKey(TableSyntaxNode tableSyntaxNode, OpenMethodHeader header) {

        StringBuilder builder = new StringBuilder();
        builder.append(header.getName());

        List<String> names = new ArrayList<>();

        for (IOpenClass parameter : header.getSignature().getParameterTypes()) {
            names.add(parameter.getName());
        }

        builder.append("(").append(String.join(", ", names)).append(")");

        // Dimensional properties and version
        //
        ITableProperties tableProperties = tableSyntaxNode.getTableProperties();
        List<Object> values = new ArrayList<>();

        for (String property : TablePropertyDefinitionUtils.getDimensionalTablePropertiesNames()) {
            values.add(tableProperties.getPropertyValue(property));
        }

        builder.append("[").append(join(values)).append(tableProperties.getVersion()).append("]");

        return builder.toString();
    }

    static String join(Collection<?> collection) {

        // handle null, zero and one elements before building a buffer
        if (collection == null) {
            return null;
        }
        Iterator iterator = collection.iterator();

        if (!iterator.hasNext()) {
            return StringUtils.EMPTY;
        }
        Object first = iterator.next();
        if (!iterator.hasNext()) {
            return Objects.toString(first);
        }

        // two or more elements
        StringBuilder buf = new StringBuilder(256); // Java default is 16, probably
        // too small
        if (first != null) {
            buf.append(first);
        }

        while (iterator.hasNext()) {
            buf.append(", ");
            Object obj = iterator.next();
            if (obj != null) {
                if (obj.getClass().isArray()) {
                    buf.append(Arrays.toString((Object[]) obj));
                } else {
                    buf.append(obj);
                }
            }
        }
        return buf.toString();
    }

}
