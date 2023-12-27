package org.openl.rules.dt;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openl.base.INamedThing;
import org.openl.binding.IBindingContext;
import org.openl.binding.impl.NumericStringComparator;
import org.openl.binding.impl.cast.IOpenCast;
import org.openl.binding.impl.module.ModuleOpenClass;
import org.openl.domain.IDomain;
import org.openl.engine.OpenLManager;
import org.openl.exception.OpenLCompilationException;
import org.openl.message.OpenLMessage;
import org.openl.message.OpenLMessagesUtils;
import org.openl.rules.binding.RuleRowHelper;
import org.openl.rules.calc.SpreadsheetResult;
import org.openl.rules.constants.ConstantOpenField;
import org.openl.rules.convertor.IString2DataConvertor;
import org.openl.rules.convertor.String2DataConvertorFactory;
import org.openl.rules.fuzzy.OpenLFuzzyUtils;
import org.openl.rules.fuzzy.OpenLFuzzyUtils.FuzzyResult;
import org.openl.rules.fuzzy.Token;
import org.openl.rules.helpers.ArraySplitter;
import org.openl.rules.helpers.CharRange;
import org.openl.rules.helpers.DateRange;
import org.openl.rules.helpers.DateRangeParser;
import org.openl.rules.helpers.DoubleRange;
import org.openl.rules.helpers.IntRange;
import org.openl.rules.helpers.StringRange;
import org.openl.rules.helpers.StringRangeParser;
import org.openl.rules.lang.xls.IXlsTableNames;
import org.openl.rules.lang.xls.XlsSheetSourceCodeModule;
import org.openl.rules.lang.xls.XlsWorkbookSourceCodeModule;
import org.openl.rules.lang.xls.binding.DTColumnsDefinition;
import org.openl.rules.lang.xls.binding.ExpressionIdentifier;
import org.openl.rules.lang.xls.binding.XlsDefinitions;
import org.openl.rules.lang.xls.binding.XlsModuleOpenClass;
import org.openl.rules.lang.xls.load.SimpleSheetLoader;
import org.openl.rules.lang.xls.load.SimpleWorkbookLoader;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.lang.xls.types.meta.DecisionTableMetaInfoReader;
import org.openl.rules.lang.xls.types.meta.MetaInfoReader;
import org.openl.rules.table.CompositeGrid;
import org.openl.rules.table.GridRegion;
import org.openl.rules.table.GridTable;
import org.openl.rules.table.ICell;
import org.openl.rules.table.IGrid;
import org.openl.rules.table.IGridTable;
import org.openl.rules.table.ILogicalTable;
import org.openl.rules.table.IWritableGrid;
import org.openl.rules.table.LogicalTableHelper;
import org.openl.rules.table.openl.GridCellSourceCodeModule;
import org.openl.rules.table.xls.XlsSheetGridModel;
import org.openl.source.impl.StringSourceCodeModule;
import org.openl.syntax.exception.SyntaxNodeException;
import org.openl.syntax.exception.SyntaxNodeExceptionUtils;
import org.openl.syntax.impl.ISyntaxConstants;
import org.openl.types.IOpenClass;
import org.openl.types.IOpenField;
import org.openl.types.IOpenMethodHeader;
import org.openl.types.IParameterDeclaration;
import org.openl.types.NullOpenClass;
import org.openl.types.impl.AOpenClass;
import org.openl.types.impl.BelongsToModuleOpenClass;
import org.openl.types.impl.CompositeMethod;
import org.openl.types.java.JavaOpenClass;
import org.openl.util.ClassUtils;
import org.openl.util.IOUtils;

public final class DecisionTableHelper {

    public static final String HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER = "/";
    private static final String RET1_COLUMN_NAME = DecisionTableColumnHeaders.RETURN.getHeaderKey() + "1";
    private static final String CRET1_COLUMN_NAME = DecisionTableColumnHeaders.COLLECT_RETURN.getHeaderKey() + "1";
    private static final List<Class<?>> INT_TYPES = Arrays.asList(byte.class,
        short.class,
        int.class,
        long.class,
        java.lang.Byte.class,
        java.lang.Short.class,
        java.lang.Integer.class,
        java.lang.Long.class,
        java.math.BigInteger.class);
    private static final List<Class<?>> DOUBLE_TYPES = Arrays
        .asList(float.class, double.class, java.lang.Float.class, java.lang.Double.class, java.math.BigDecimal.class);
    private static final List<Class<?>> CHAR_TYPES = Arrays.asList(char.class, java.lang.Character.class);
    private static final List<Class<?>> STRING_TYPES = Arrays.asList(java.lang.String.class);
    private static final List<Class<?>> DATE_TYPES = Collections.singletonList(Date.class);
    private static final List<Class<?>> RANGE_TYPES = Arrays
        .asList(IntRange.class, DoubleRange.class, CharRange.class, StringRange.class, DateRange.class);

    private static final List<Class<?>> IGNORED_CLASSES_FOR_COMPOUND_TYPE = Arrays.asList(null,
        byte.class,
        short.class,
        int.class,
        long.class,
        float.class,
        double.class,
        char.class,
        void.class,
        java.lang.Byte.class,
        java.lang.Short.class,
        java.lang.Integer.class,
        java.lang.Long.class,
        java.lang.Float.class,
        java.lang.Double.class,
        java.lang.Character.class,
        java.lang.String.class,
        java.math.BigInteger.class,
        java.math.BigDecimal.class,
        Date.class,
        IntRange.class,
        DoubleRange.class,
        CharRange.class,
        StringRange.class,
        DateRange.class,
        Object.class,
        Map.class,
        SortedMap.class,
        Set.class,
        SortedSet.class,
        List.class,
        Collections.class,
        ArrayList.class,
        LinkedList.class,
        HashSet.class,
        LinkedHashSet.class,
        HashMap.class,
        TreeSet.class,
        TreeMap.class,
        LinkedHashMap.class);

    private static final String[] EMPTY_STRING_ARRAY = new String[] {};

    private DecisionTableHelper() {
    }

    static boolean isValidConditionHeader(String s) {
        return s != null && s.length() >= 2 && s.charAt(0) == DecisionTableColumnHeaders.CONDITION.getHeaderKey()
            .charAt(0) && s.substring(1).chars().allMatch(Character::isDigit);
    }

    static boolean isValidHConditionHeader(String headerStr) {
        return headerStr != null && headerStr.startsWith(
            DecisionTableColumnHeaders.HORIZONTAL_CONDITION.getHeaderKey()) && headerStr.length() > 2 && headerStr
                .substring(2)
                .chars()
                .allMatch(Character::isDigit);
    }

    static boolean isValidMergedConditionHeader(String headerStr) {
        return headerStr != null && headerStr.startsWith(
            DecisionTableColumnHeaders.MERGED_CONDITION.getHeaderKey()) && headerStr.length() > 2 && headerStr
                .substring(2)
                .chars()
                .allMatch(Character::isDigit);
    }

    static boolean isValidActionHeader(String s) {
        return s != null && s.length() >= 2 && s.charAt(0) == DecisionTableColumnHeaders.ACTION.getHeaderKey()
            .charAt(0) && s.substring(1).chars().allMatch(Character::isDigit);
    }

    static boolean isValidRetHeader(String s) {
        return s != null && s.length() >= 3 && s.startsWith(DecisionTableColumnHeaders.RETURN
            .getHeaderKey()) && (s.length() == 3 || s.substring(3).chars().allMatch(Character::isDigit));
    }

    static boolean isValidKeyHeader(String s) {
        return s != null && s.length() >= 3 && s.startsWith(DecisionTableColumnHeaders.KEY
            .getHeaderKey()) && (s.length() == 3 || s.substring(3).chars().allMatch(Character::isDigit));
    }

    static boolean isValidCRetHeader(String s) {
        return s != null && s.length() >= 4 && s.startsWith(DecisionTableColumnHeaders.COLLECT_RETURN
            .getHeaderKey()) && (s.length() == 4 || s.substring(4).chars().allMatch(Character::isDigit));
    }

    static boolean isValidRuleHeader(String s) {
        return Objects.equals(s, DecisionTableColumnHeaders.RULE.getHeaderKey());
    }

    static boolean isConditionHeader(String s) {
        return isValidConditionHeader(s) || isValidHConditionHeader(s) || isValidMergedConditionHeader(s);
    }

    /**
     * Creates virtual headers for condition and return columns to load simple Decision Table as an usual Decision Table
     *
     * @param decisionTable method description for simple Decision Table.
     * @param originalTable The original body of simple Decision Table.
     * @return prepared usual Decision Table.
     */
    static ILogicalTable preprocessDecisionTableWithoutHeaders(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            XlsModuleOpenClass module,
            IBindingContext bindingContext) throws OpenLCompilationException {
        IWritableGrid virtualGrid = createVirtualGrid();
        boolean isSmartLookupAndResultTitleInFirstRow = isSmartLookupAndResultTitleInFirstRow(tableSyntaxNode,
            originalTable);
        writeVirtualHeaders(tableSyntaxNode,
            decisionTable,
            originalTable,
            virtualGrid,
            isSmartLookupAndResultTitleInFirstRow,
            module,
            new IdentityHashMap<>(),
            bindingContext);
        if (isSmartLookupAndResultTitleInFirstRow) {
            originalTable = cutResultTitleInFirstRow(originalTable);
        }
        // If the new table header size bigger than the size of the old table we
        // use the new table size
        int sizeOfVirtualGridTable = virtualGrid.getMaxColumnIndex(0) < originalTable.getSource()
            .getWidth() ? originalTable.getSource().getWidth() - 1 : virtualGrid.getMaxColumnIndex(0) - 1;
        GridTable virtualGridTable = new GridTable(0,
            0,
            IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1,
            sizeOfVirtualGridTable,
            virtualGrid);

        IGrid grid = new CompositeGrid(new IGridTable[] { virtualGridTable, originalTable.getSource() }, true);
        // If the new table header size bigger than the size of the old table we
        // use the new table size
        int sizeofGrid = virtualGridTable.getWidth() < originalTable.getSource().getWidth() ? originalTable.getSource()
            .getWidth() - 1 : virtualGridTable.getWidth() - 1;

        return LogicalTableHelper.logicalTable(new GridTable(0,
            0,
            originalTable.getSource().getHeight() + IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1,
            sizeofGrid,
            grid));
    }

    private static FuzzyContext buildFuzzyContext(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            int numberOfHConditions,
            IBindingContext bindingContext) {
        final ParameterTokens parameterTokens = buildParameterTokens(decisionTable);
        if (numberOfHConditions == 0) {
            IOpenClass returnType = getCompoundReturnType(tableSyntaxNode, decisionTable, bindingContext);
            if (isCompoundReturnType(returnType)) {
                Map<Token, IOpenField[][]> returnTypeFuzzyTokens = OpenLFuzzyUtils
                    .tokensMapToOpenClassWritableFieldsRecursively(returnType, returnType.getName(), 1);
                Token[] returnTokens = returnTypeFuzzyTokens.keySet().toArray(new Token[] {});
                return new FuzzyContext(parameterTokens, returnTokens, returnTypeFuzzyTokens, returnType);
            }
        }
        return new FuzzyContext(parameterTokens);
    }

    public static boolean isSmartLookupAndResultTitleInFirstRow(TableSyntaxNode tableSyntaxNode,
            ILogicalTable originalTable) {
        if (isSmartLookupTable(tableSyntaxNode) && StringUtils
            .isNotBlank(originalTable.getCell(0, 0).getStringValue())) {
            int firstCellHeight = originalTable.getSource().getCell(0, 0).getHeight();
            int width = originalTable.getSource().getWidth();
            int w = originalTable.getSource().getCell(0, 0).getWidth();
            while (w < width) {
                ICell cell = originalTable.getSource().getCell(w, 0);
                if (cell.getHeight() != firstCellHeight || StringUtils.isNotBlank(cell.getStringValue())) {
                    return false;
                }
                w = w + cell.getWidth();
            }
            if (firstCellHeight < originalTable.getSource().getHeight()) {
                return originalTable.getSource().getCell(0, firstCellHeight).getWidth() != width;
            }
        }
        return false;
    }

    public static ILogicalTable cutResultTitleInFirstRow(ILogicalTable originalTable) {
        return originalTable.getSubtable(0, 1, originalTable.getWidth(), originalTable.getHeight() - 1);
    }

    private static void writeVirtualHeaders(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            boolean isSmartLookupAndResultTitleInFirstRow,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) throws OpenLCompilationException {
        ILogicalTable uncutOriginalTable = null;
        if (isSmartLookupAndResultTitleInFirstRow) {
            uncutOriginalTable = originalTable;
            originalTable = cutResultTitleInFirstRow(originalTable);
        }

        int numberOfHConditions = isLookup(tableSyntaxNode) ? getNumberOfHConditions(originalTable) : 0;
        int firstColumnHeight = originalTable.getSource().getCell(0, 0).getHeight();
        int firstColumnForHCondition = -1;
        WithVerticalTitles withVerticalTitles = WithVerticalTitles.NO;

        if (numberOfHConditions > 0) {
            Pair<Integer, WithVerticalTitles> p = getFirstColumnForHCondition(originalTable,
                numberOfHConditions,
                firstColumnHeight,
                isSmartLookupTable(tableSyntaxNode));
            firstColumnForHCondition = p.getLeft();
            if (firstColumnForHCondition > 0) {
                withVerticalTitles = p.getRight();
            }
        }

        final FuzzyContext fuzzyContext = buildFuzzyContext(tableSyntaxNode,
            decisionTable,
            numberOfHConditions,
            bindingContext);

        final NumberOfColumnsUnderTitleCounter numberOfColumnsUnderTitleCounter = new NumberOfColumnsUnderTitleCounter(
            originalTable,
            firstColumnHeight);

        List<DTHeader> dtHeaders = getDTHeaders(tableSyntaxNode,
            decisionTable,
            originalTable,
            fuzzyContext,
            numberOfColumnsUnderTitleCounter,
            numberOfHConditions,
            firstColumnHeight,
            firstColumnForHCondition,
            withVerticalTitles,
            bindingContext);

        DeclaredDTHeader lookupReturnDtHeader = null;
        if (isSmartLookupAndResultTitleInFirstRow) {
            lookupReturnDtHeader = getLookupReturnDtHeader(tableSyntaxNode,
                decisionTable,
                uncutOriginalTable,
                dtHeaders,
                bindingContext);
            if (lookupReturnDtHeader == null) {
                IGridTable cellTable = uncutOriginalTable.getSource().getSubtable(0, 0, 1, 1);
                GridCellSourceCodeModule sourceCodeModule = new GridCellSourceCodeModule(cellTable, bindingContext);
                SyntaxNodeException error = SyntaxNodeExceptionUtils
                    .createError("Expected external return is not found.", sourceCodeModule);
                bindingContext.addError(error);
            }
        }

        writeRule(decisionTable, originalTable, grid, dtHeaders, bindingContext);

        writeConditions(tableSyntaxNode,
            decisionTable,
            originalTable,
            grid,
            numberOfColumnsUnderTitleCounter,
            dtHeaders,
            firstColumnHeight,
            firstColumnForHCondition,
            withVerticalTitles,
            module,
            cache,
            bindingContext);

        writeUnmatchedColumns(decisionTable, originalTable, dtHeaders, firstColumnHeight, bindingContext);

        writeActions(decisionTable, originalTable, grid, dtHeaders, firstColumnHeight, module, cache, bindingContext);

        writeReturns(tableSyntaxNode,
            decisionTable,
            uncutOriginalTable,
            originalTable,
            grid,
            fuzzyContext,
            dtHeaders,
            lookupReturnDtHeader,
            module,
            cache,
            bindingContext);
    }

    private static DeclaredDTHeader getLookupReturnDtHeader(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            List<DTHeader> dtHeaders,
            IBindingContext bindingContext) {
        int retColumn = getRetColumn(dtHeaders);
        DeclaredDTHeader lookupReturnDtHeader = null;
        final XlsDefinitions definitions = ((XlsModuleOpenClass) decisionTable.getDeclaringClass()).getXlsDefinitions();
        final String title = OpenLFuzzyUtils.toTokenString(originalTable.getCell(0, 0).getStringValue());
        for (DTColumnsDefinition definition : definitions.getDtColumnsDefinitions()) {
            if (definition.isReturn() && definition.getTitles().size() == 1 && Objects
                .equals(definition.getTitles().iterator().next(), title)) {
                MatchedDefinition matchedDefinition = matchByDTColumnDefinition(decisionTable,
                    definition,
                    1,
                    bindingContext);
                if (matchedDefinition != null) {
                    IParameterDeclaration[][] columnParameters = new IParameterDeclaration[1][];
                    columnParameters[0] = definition.getParameters(title).toArray(IParameterDeclaration.EMPTY);
                    if (lookupReturnDtHeader == null) {
                        lookupReturnDtHeader = new DeclaredDTHeader(matchedDefinition.getUsedMethodParameterIndexes(),
                            definition,
                            columnParameters,
                            retColumn,
                            0,
                            1,
                            1,
                            matchedDefinition,
                            true,
                            false);
                    } else {
                        bindingContext.addMessage(OpenLMessagesUtils.newWarnMessage(
                            "Ambiguous matching of column titles to DT return columns. Use more appropriate titles for return columns.",
                            tableSyntaxNode));
                        return lookupReturnDtHeader;
                    }
                }
            }
        }
        return lookupReturnDtHeader;
    }

    private static int getRetColumn(List<DTHeader> dtHeaders) {
        return dtHeaders.stream()
            .filter(e -> e.isCondition() || e.isAction())
            .mapToInt(e -> e.getColumn() + e.getWidth())
            .max()
            .orElse(0);
    }

    private static void resolveConflictsInDeclaredDtHeaders(DecisionTable decisionTable, List<List<DTHeader>> fits) {
        Set<String> usedMethodSignatureIdentifiers = new HashSet<>();
        for (int i = 0; i < decisionTable.getSignature().getNumberOfParameters(); i++) {
            usedMethodSignatureIdentifiers.add(toLowerCase(decisionTable.getSignature().getParameterName(i)));
        }
        for (List<DTHeader> dtHeaders : fits) {
            Map<String, Integer> usedAllParameterIdentifiers = new HashMap<>();
            Set<String> externalParameters = new HashSet<>();
            for (DTHeader dtHeader : dtHeaders) {
                if (dtHeader instanceof DeclaredDTHeader) {
                    DeclaredDTHeader declaredDTHeader = (DeclaredDTHeader) dtHeader;
                    for (int i = 0; i < declaredDTHeader.getColumnParameters().length; i++) {
                        for (int j = 0; j < declaredDTHeader.getColumnParameters()[i].length; j++) {
                            IParameterDeclaration parameterDeclaration = declaredDTHeader.getColumnParameters()[i][j];
                            if (parameterDeclaration != null) {
                                usedAllParameterIdentifiers.merge(parameterDeclaration.getName(), 1, Integer::sum);
                            }
                        }
                    }
                    externalParameters.addAll(
                        declaredDTHeader.getMatchedDefinition().getDtColumnsDefinition().getExternalParameters());
                }
            }
            Map<String, String> renamedParameters = new HashMap<>();
            for (DTHeader dtHeader : dtHeaders) {
                if (dtHeader instanceof DeclaredDTHeader) {
                    DeclaredDTHeader declaredDTHeader = (DeclaredDTHeader) dtHeader;
                    Set<String> usedLocalParameterIdentifiers = new HashSet<>();
                    for (int i = 0; i < declaredDTHeader.getColumnParameters().length; i++) {
                        for (int j = 0; j < declaredDTHeader.getColumnParameters()[i].length; j++) {
                            IParameterDeclaration parameterDeclaration = declaredDTHeader.getColumnParameters()[i][j];
                            if (parameterDeclaration != null) {
                                usedLocalParameterIdentifiers.add(toLowerCase(parameterDeclaration.getName()));
                            }
                        }
                    }
                    for (int i = 0; i < declaredDTHeader.getColumnParameters().length; i++) {
                        for (int j = 0; j < declaredDTHeader.getColumnParameters()[i].length; j++) {
                            IParameterDeclaration parameterDeclaration = declaredDTHeader.getColumnParameters()[i][j];
                            if (parameterDeclaration != null) {
                                String param = parameterDeclaration.getName();
                                String lowerCasedParam = toLowerCase(param);
                                if (usedMethodSignatureIdentifiers.contains(
                                    lowerCasedParam) || usedAllParameterIdentifiers.get(param) > 1 && externalParameters
                                        .contains(param)) {
                                    Integer v = usedAllParameterIdentifiers.get(param);
                                    if (v != null) {
                                        if (v > 1) {
                                            usedAllParameterIdentifiers.put(param, v - 1);
                                        } else {
                                            usedAllParameterIdentifiers.remove(param);
                                        }
                                    }
                                    String newParamName = "_" + param;
                                    String newParamNameLowerCased = toLowerCase(newParamName);
                                    int k = 1;
                                    while (usedMethodSignatureIdentifiers
                                        .contains(newParamNameLowerCased) || usedAllParameterIdentifiers
                                            .containsKey(newParamName) || usedLocalParameterIdentifiers
                                                .contains(newParamNameLowerCased)) {
                                        newParamName = "_" + parameterDeclaration.getName() + "_" + k;
                                        newParamNameLowerCased = toLowerCase(newParamName);
                                        k++;
                                    }
                                    param = newParamName;
                                    usedAllParameterIdentifiers.put(newParamName, 1);
                                }
                                if (!StringUtils.equalsIgnoreCase(parameterDeclaration.getName(), param)) {
                                    declaredDTHeader.getMatchedDefinition()
                                        .renameParameterName(parameterDeclaration.getName(), param);
                                    renamedParameters.put(parameterDeclaration.getName(), param);
                                }
                            }
                        }
                    }
                }
            }
            for (DTHeader dtHeader : dtHeaders) {
                if (dtHeader instanceof DeclaredDTHeader) {
                    DeclaredDTHeader declaredDTHeader = (DeclaredDTHeader) dtHeader;
                    for (String externalParameter : declaredDTHeader.getMatchedDefinition()
                        .getDtColumnsDefinition()
                        .getExternalParameters()) {
                        String renamedParameter = renamedParameters.get(externalParameter);
                        if (renamedParameter != null) {
                            declaredDTHeader.getMatchedDefinition()
                                .renameExternalParameter(externalParameter, renamedParameter);
                        }
                    }
                }
            }
        }
    }

    private static boolean isCompoundReturnType(IOpenClass compoundType) {
        if (IGNORED_CLASSES_FOR_COMPOUND_TYPE.contains(compoundType.getInstanceClass())) {
            return false;
        } else if (compoundType.getConstructor(IOpenClass.EMPTY) == null) {
            return false;
        } else if (ClassUtils.isAssignable(compoundType.getInstanceClass(), SpreadsheetResult.class)) {
            return false;
        } else {
            int count = 0;
            for (IOpenField field : compoundType.getFields()) {
                if (!field.isConst() && !field.isStatic() && field.isWritable()) {
                    count++;
                }
            }
            return count > 0;
        }
    }

    private static boolean isCompoundInputType(IOpenClass type) {
        if (IGNORED_CLASSES_FOR_COMPOUND_TYPE.contains(type.getInstanceClass())) {
            return false;
        }
        int count = 0;
        for (IOpenField field : type.getFields()) {
            if (!field.isConst() && !field.isStatic() && field.isReadable()) {
                count++;
            }
        }
        return count > 0;
    }

    private static void validateCompoundReturnType(IOpenClass compoundType) throws OpenLCompilationException {
        try {
            compoundType.getInstanceClass().getConstructor();
        } catch (ReflectiveOperationException e) {
            throw new OpenLCompilationException(
                String.format("Invalid return type: There is no default constructor found in type '%s'.",
                    compoundType.getDisplayName(0)));
        }
    }

    private static void writeReturnMetaInfo(TableSyntaxNode tableSyntaxNode,
            ICell cell,
            String description,
            String uri) {
        MetaInfoReader metaReader = tableSyntaxNode.getMetaInfoReader();
        if (metaReader instanceof DecisionTableMetaInfoReader) {
            DecisionTableMetaInfoReader metaInfoReader = (DecisionTableMetaInfoReader) metaReader;
            metaInfoReader.addReturn(cell.getTopLeftCellFromRegion().getAbsoluteRow(),
                cell.getTopLeftCellFromRegion().getAbsoluteColumn(),
                description,
                uri);
        }
    }

    private static IOpenClass getCompoundReturnType(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            IBindingContext bindingContext) {
        IOpenClass compoundType;
        if (isCollect(tableSyntaxNode)) {
            if (tableSyntaxNode.getHeader().getCollectParameters().length > 0) {
                compoundType = bindingContext.findType(ISyntaxConstants.THIS_NAMESPACE,
                    tableSyntaxNode.getHeader()
                        .getCollectParameters()[tableSyntaxNode.getHeader().getCollectParameters().length - 1]);
            } else {
                if (decisionTable.getType().isArray()) {
                    compoundType = decisionTable.getType().getComponentClass();
                } else {
                    compoundType = decisionTable.getType();
                }
            }
        } else {
            compoundType = decisionTable.getType();
        }
        return compoundType;
    }

    private static Pair<String, IOpenClass> buildStatementByFieldsChain(IOpenClass type, IOpenField[] fieldsChain) {
        StringBuilder fieldsChainSb = new StringBuilder();
        for (int i = 0; i < fieldsChain.length; i++) {
            IOpenField openField = type.getField(fieldsChain[i].getName(), true);
            fieldsChainSb.append(openField.getName());
            if (i < fieldsChain.length - 1) {
                fieldsChainSb.append(".");
            }
            type = fieldsChain[i].getType();
        }
        return Pair.of(fieldsChainSb.toString(), type);
    }

    private static String getTypeNameForCode(IOpenClass type,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache) {
        IOpenClass g = type;
        int dim = 0;
        while (g.isArray()) {
            g = g.getComponentClass();
            dim++;
        }
        if (g instanceof BelongsToModuleOpenClass) {
            if (!module.isDependencyModule(((BelongsToModuleOpenClass) g).getModule(), cache)) {
                return ((BelongsToModuleOpenClass) g).getExternalRefName() + "[]".repeat(Math.max(0, dim));
            }
        }
        if (NullOpenClass.the.equals(g)) {
            return JavaOpenClass.OBJECT.getName() + "[]".repeat(Math.max(0, dim));
        }
        return type.getName();
    }

    private static void writeReturnWithReturnDtHeader(TableSyntaxNode tableSyntaxNode,
            ILogicalTable uncutOriginalTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            DeclaredDTHeader declaredReturn,
            String header,
            boolean lookupReturnHeader,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) {
        grid.setCellValue(declaredReturn.getColumn(), 0, header);
        grid.setCellValue(declaredReturn.getColumn(), 1, declaredReturn.getStatement());
        DTColumnsDefinition dtColumnsDefinition = declaredReturn.getMatchedDefinition().getDtColumnsDefinition();
        int c = declaredReturn.getColumn();
        while (c < declaredReturn.getColumn() + declaredReturn.getWidthForMerge()) {
            ICell cell = lookupReturnHeader ? uncutOriginalTable.getSource().getCell(0, 0)
                                            : originalTable.getSource().getCell(c, 0);
            String d = cell.getStringValue();
            d = OpenLFuzzyUtils.toTokenString(d);
            for (String title : dtColumnsDefinition.getTitles()) {
                if (lookupReturnHeader || Objects.equals(d, title)) {
                    List<IParameterDeclaration> parameters = dtColumnsDefinition.getParameters(title);
                    List<String> parameterNames = new ArrayList<>();
                    List<IOpenClass> typeOfColumns = new ArrayList<>();
                    int totalColumnsUnder = getTotalColumnsUnder(originalTable, c);
                    for (int paramIndex = 0; paramIndex < parameters.size(); paramIndex++) {
                        IParameterDeclaration param = parameters.get(paramIndex);
                        IOpenClass paramType;
                        if (param != null) {
                            String paramName = declaredReturn.getMatchedDefinition().getParameter(param.getName());
                            parameterNames.add(paramName);
                            String value = getTypeNameForCode(param.getType(),
                                module,
                                cache) + (paramName != null ? " " + paramName : "");
                            grid.setCellValue(c, 2, value);
                            paramType = param.getType();
                        } else {
                            paramType = declaredReturn.getDtColumnsDefinition().getCompositeMethod().getType();
                        }
                        typeOfColumns.add(paramType);
                        if (!lookupReturnHeader) {
                            int h = originalTable.getSource().getCell(c, 0).getHeight();
                            int w1 = originalTable.getSource().getCell(c, h).getWidth();
                            if (paramType != null && paramType.isArray()) {
                                // If we have more columns than parameters use excess columns for array typed parameter
                                int tmpC = c;
                                for (int i = 0; i < totalColumnsUnder - parameters.size(); i++) {
                                    int w2 = originalTable.getSource().getCell(tmpC, h).getWidth();
                                    w1 = w1 + w2;
                                    tmpC = tmpC + w2;
                                }
                            }
                            if (w1 > 1) {
                                grid.addMergedRegion(new GridRegion(2, c, 2, c + w1 - 1));
                            }
                            c = c + w1;
                        } else {
                            c = c + 1;
                        }
                    }
                    if (!bindingContext.isExecutionMode()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Return: ").append(header);
                        if (!StringUtils.isEmpty(declaredReturn.getStatement())) {
                            sb.append("\n")
                                .append("Expression: ")
                                .append(declaredReturn.getStatement().replaceAll("\n", StringUtils.SPACE));

                        }
                        DecisionTableMetaInfoReader.appendParameters(sb,
                            parameterNames.toArray(EMPTY_STRING_ARRAY),
                            typeOfColumns.toArray(IOpenClass.EMPTY));
                        writeReturnMetaInfo(tableSyntaxNode,
                            cell,
                            sb.toString(),
                            declaredReturn.getMatchedDefinition().getDtColumnsDefinition().getUri());
                    }
                    break;
                }
            }
        }

        if (c - declaredReturn.getColumn() > 1) {
            for (int row = 0; row < IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1; row++) {
                grid.addMergedRegion(new GridRegion(row, declaredReturn.getColumn(), row, c - 1));
            }
        }
    }

    private static int getTotalColumnsUnder(ILogicalTable originalTable, int c) {
        int column = c;
        int totalColumnsUnder = 0;
        int maxColumn = c + originalTable.getSource().getCell(column, 0).getWidth();
        while (column < maxColumn) {
            int h = originalTable.getSource().getCell(column, 0).getHeight();
            column = column + originalTable.getSource().getCell(column, h).getWidth();
            totalColumnsUnder++;
        }
        return totalColumnsUnder;
    }

    private static final String FUZZY_RET_VARIABLE_NAME = "$Rn";

    private static IOpenClass writeReturnStatement(IOpenClass type,
            IOpenField[] fieldsChain,
            Set<String> generatedNames,
            Map<String, Map<IOpenField, String>> variables,
            String insertStatement,
            Set<String> variableAssignments,
            StringBuilder sb,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache) {
        if (fieldsChain == null) {
            return type;
        }
        String currentVariable = FUZZY_RET_VARIABLE_NAME;
        Set<String> variablesInChain = new HashSet<>();
        variablesInChain.add(currentVariable);
        for (int j = 0; j < fieldsChain.length; j++) {
            String var;
            type = fieldsChain[j].getType();
            if (j < fieldsChain.length - 1) {
                Map<IOpenField, String> vm = variables.get(currentVariable);
                if (vm == null || vm.get(fieldsChain[j]) == null) {
                    var = RandomStringUtils.random(8, true, false);
                    while (generatedNames.contains(var)) { // Prevent
                        // variable
                        // duplication
                        var = RandomStringUtils.random(8, true, false);
                    }
                    generatedNames.add(var);
                    sb.append(getTypeNameForCode(type, module, cache))
                        .append(" ")
                        .append(var)
                        .append("=new ")
                        .append(getTypeNameForCode(type, module, cache))
                        .append("();");
                    sb.append("int ").append(var).append("_").append("=0;");
                    vm = variables.computeIfAbsent(currentVariable, e -> new HashMap<>());
                    vm.put(fieldsChain[j], var);
                    variableAssignments
                        .add(currentVariable + "." + fieldsChain[j].getName() + "=" + var + "_>0?" + var + ":null;");
                } else {
                    var = vm.get(fieldsChain[j]);
                }
                currentVariable = var;
                variablesInChain.add(currentVariable);
            } else {
                final String localVar = currentVariable + "." + fieldsChain[j].getName();
                sb.append(localVar).append("=").append(insertStatement).append(";");
                if (!variablesInChain.isEmpty()) {
                    sb.append("if(").append(localVar).append("!=null){");
                    for (String cv : variablesInChain) {
                        sb.append(cv).append("_++;");
                    }
                    sb.append('}');
                }
            }
        }
        return type;
    }

    private static void writeInputParametersToReturnMetaInfo(DecisionTable decisionTable,
            String statementInInputParameters,
            String statementInReturn) {
        MetaInfoReader metaReader = decisionTable.getSyntaxNode().getMetaInfoReader();
        if (metaReader instanceof DecisionTableMetaInfoReader) {
            DecisionTableMetaInfoReader metaInfoReader = (DecisionTableMetaInfoReader) metaReader;
            metaInfoReader.addParameterToReturn(statementInInputParameters, statementInReturn);
        }
    }

    private static void writeInputParametersToReturn(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            FuzzyContext fuzzyContext,
            List<DTHeader> dtHeaders,
            Set<String> generatedNames,
            Map<String, Map<IOpenField, String>> variables,
            Set<String> variableAssignments,
            StringBuilder sb,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) {
        List<FuzzyDTHeader> fuzzyReturns = dtHeaders.stream()
            .filter(e -> e instanceof FuzzyDTHeader)
            .map(e -> (FuzzyDTHeader) e)
            .filter(FuzzyDTHeader::isReturn)
            .collect(toList());
        Map<IOpenField[], List<Token>> m = new HashMap<>();
        for (Token token : fuzzyContext.getFuzzyReturnTokens()) {
            IOpenField[][] returnTypeFieldsChains = fuzzyContext.getFieldsChainsForReturnToken(token);
            for (IOpenField[] returnTypeFieldsChain : returnTypeFieldsChains) {
                boolean f = false;
                for (Entry<IOpenField[], List<Token>> entry : m.entrySet()) {
                    if (OpenLFuzzyUtils.isEqualsFieldsChains(entry.getKey(), returnTypeFieldsChain)) {
                        entry.getValue().add(token);
                        f = true;
                        break;
                    }
                }
                if (!f) {
                    List<Token> tokens = new ArrayList<>();
                    tokens.add(token);
                    m.put(returnTypeFieldsChain, tokens);
                }
            }
        }

        Map<Token, List<Pair<IOpenField[], FuzzyResult>>> bestFuzzyResultsMap = new HashMap<>();

        for (Entry<IOpenField[], List<Token>> entry : m.entrySet()) {
            final IOpenField[] fieldsChain = entry.getKey();
            final boolean foundInReturns = fuzzyReturns.stream()
                .anyMatch(e -> OpenLFuzzyUtils.isEqualsFieldsChains(e.getFieldsChain(), fieldsChain));
            if (foundInReturns) {
                continue;
            }
            for (Token token : entry.getValue()) {
                List<FuzzyResult> fuzzyResults = OpenLFuzzyUtils
                    .fuzzyExtract(token.getValue(), fuzzyContext.getParameterTokens().getTokens(), false);
                for (FuzzyResult fuzzyResult : fuzzyResults) {
                    final int paramIndex = fuzzyContext.getParameterTokens().getParameterIndex(fuzzyResult.getToken());
                    final IOpenField[] paramFieldsChain = fuzzyContext.getParameterTokens()
                        .getFieldsChain(fuzzyResult.getToken());
                    List<Pair<IOpenField[], FuzzyResult>> resultList = bestFuzzyResultsMap.get(fuzzyResult.getToken());
                    if (resultList == null) {
                        resultList = bestFuzzyResultsMap.entrySet().stream().filter(e -> {
                            final int eParamIndex = fuzzyContext.getParameterTokens().getParameterIndex(e.getKey());
                            return paramIndex == eParamIndex && OpenLFuzzyUtils.isEqualsFieldsChains(paramFieldsChain,
                                fuzzyContext.getParameterTokens().getFieldsChain(e.getKey()));
                        }).map(Entry::getValue).findFirst().orElse(null);
                        if (resultList == null) {
                            resultList = new ArrayList<>();
                            bestFuzzyResultsMap.put(fuzzyResult.getToken(), resultList);
                        }
                    }
                    if (resultList.isEmpty()) {
                        resultList.add(Pair.of(fieldsChain, fuzzyResult));
                    } else {
                        Pair<IOpenField[], FuzzyResult> existedResult = resultList.iterator().next();
                        int fuzzyResultCompare = fuzzyResult.compareTo(existedResult.getRight());
                        if (fuzzyResultCompare <= 0) {
                            if (fuzzyResultCompare < 0) {
                                resultList.clear();
                            }
                            boolean f = true;
                            for (Pair<IOpenField[], FuzzyResult> pair : resultList) {
                                if (OpenLFuzzyUtils.isEqualsFieldsChains(pair.getKey(), fieldsChain)) {
                                    f = false;
                                    break;
                                }
                            }
                            if (f) {
                                resultList.add(Pair.of(fieldsChain, fuzzyResult));
                            }
                        }
                    }
                }
            }
        }

        Map<String, Set<String>> ambiguousReturnStatementMatching = new HashMap<>();
        for (Entry<Token, List<Pair<IOpenField[], FuzzyResult>>> entry : bestFuzzyResultsMap.entrySet()) {
            Token paramToken = entry.getKey();
            for (Pair<IOpenField[], FuzzyResult> pair : entry.getValue()) {
                final int paramIndex = fuzzyContext.getParameterTokens().getParameterIndex(paramToken);
                IOpenClass type = decisionTable.getSignature().getParameterType(paramIndex);
                final IOpenField[] paramFieldsChain = fuzzyContext.getParameterTokens().getFieldsChain(paramToken);
                final String statement;
                if (paramFieldsChain != null) {
                    Pair<String, IOpenClass> v = buildStatementByFieldsChain(type, paramFieldsChain);
                    statement = decisionTable.getSignature().getParameterName(paramIndex) + "." + v.getKey();
                    type = v.getValue();
                } else {
                    statement = decisionTable.getSignature().getParameterName(paramIndex);
                }
                if (!isCompoundInputType(type)) {
                    IOpenField[] fieldsChain = pair.getKey();
                    Pair<String, IOpenClass> p = buildStatementByFieldsChain(fuzzyContext.getFuzzyReturnType(),
                        fieldsChain);
                    IOpenCast cast = bindingContext.getCast(type, p.getValue());
                    if (cast != null && cast.isImplicit()) {
                        writeReturnStatement(fuzzyContext.getFuzzyReturnType(),
                            fieldsChain,
                            generatedNames,
                            variables,
                            statement,
                            variableAssignments,
                            sb,
                            module,
                            cache);
                        final String statementInReturn = getTypeNameForCode(fuzzyContext.getFuzzyReturnType(),
                            module,
                            cache) + "." + buildStatementByFieldsChain(fuzzyContext.getFuzzyReturnType(), fieldsChain)
                                .getKey();
                        Set<String> matchedStatements = ambiguousReturnStatementMatching
                            .computeIfAbsent(statementInReturn, k -> new HashSet<>());
                        matchedStatements.add(statement);
                        if (!bindingContext.isExecutionMode()) {
                            writeInputParametersToReturnMetaInfo(decisionTable, statement, statementInReturn);
                        }
                    }
                }
            }
        }

        ambiguousReturnStatementMatching.entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .forEach(e -> bindingContext.addMessage(OpenLMessagesUtils.newWarnMessage(
                String.format("More than one input parameter is set to return '%s'.", e.getKey()),
                tableSyntaxNode)));
    }

    private static void writeFuzzyReturns(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            FuzzyContext fuzzyContext,
            List<DTHeader> dtHeaders,
            IOpenClass compoundReturnType,
            String header,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) throws OpenLCompilationException {
        validateCompoundReturnType(compoundReturnType);

        List<FuzzyDTHeader> fuzzyReturns = dtHeaders.stream()
            .filter(e -> e instanceof FuzzyDTHeader && e.isReturn())
            .map(e -> (FuzzyDTHeader) e)
            .filter(e -> e.getFieldsChain() != null)
            .collect(toList());

        Set<String> variableAssignments = new HashSet<>();

        if (fuzzyReturns.isEmpty()) {
            throw new IllegalStateException("DT headers are not found.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getTypeNameForCode(compoundReturnType, module, cache))
            .append(" ")
            .append(FUZZY_RET_VARIABLE_NAME)
            .append(" = new ")
            .append(getTypeNameForCode(compoundReturnType, module, cache))
            .append("();");
        sb.append("int ").append(FUZZY_RET_VARIABLE_NAME).append("_").append(" = 0;");

        Set<String> generatedNames = new HashSet<>();
        while (generatedNames.size() < fuzzyReturns.size()) {
            generatedNames.add(RandomStringUtils.random(8, true, false));
        }
        String[] compoundColumnParamNames = generatedNames.toArray(EMPTY_STRING_ARRAY);
        Map<String, Map<IOpenField, String>> variables = new HashMap<>();

        writeInputParametersToReturn(tableSyntaxNode,
            decisionTable,
            fuzzyContext,
            dtHeaders,
            generatedNames,
            variables,
            variableAssignments,
            sb,
            module,
            cache,
            bindingContext);

        int i = 0;
        for (FuzzyDTHeader fuzzyDTHeader : fuzzyReturns) {
            IOpenClass type = writeReturnStatement(compoundReturnType,
                fuzzyDTHeader.getFieldsChain(),
                generatedNames,
                variables,
                compoundColumnParamNames[i],
                variableAssignments,
                sb,
                module,
                cache);

            grid.setCellValue(fuzzyDTHeader.getColumn(),
                2,
                getTypeNameForCode(type, module, cache) + " " + compoundColumnParamNames[i]);

            if (fuzzyDTHeader.getWidth() > 1) {
                grid.addMergedRegion(new GridRegion(2,
                    fuzzyDTHeader.getColumn(),
                    2,
                    fuzzyDTHeader.getColumn() + fuzzyDTHeader.getWidth() - 1));
            }

            if (!bindingContext.isExecutionMode()) {
                int firstColumnHeight = originalTable.getCell(0, 0).getHeight();
                ICell cell = originalTable.getSource().getCell(fuzzyDTHeader.getColumn(), firstColumnHeight - 1);
                cell = cell.getTopLeftCellFromRegion();
                String statement = buildStatementByFieldsChain(compoundReturnType, fuzzyDTHeader.getFieldsChain())
                    .getKey();
                StringBuilder sb1 = new StringBuilder();
                sb1.append("Return: ").append(header);

                if (!StringUtils.isEmpty(statement)) {
                    sb1.append("\n")
                        .append("Expression: value for return ")
                        .append(compoundReturnType.getDisplayName(INamedThing.SHORT))
                        .append(".")
                        .append(statement);
                }
                DecisionTableMetaInfoReader.appendParameters(sb1, null, new IOpenClass[] { type });

                writeReturnMetaInfo(tableSyntaxNode, cell, sb1.toString(), null);
            }
            i++;
        }
        variableAssignments.forEach(sb::append);
        sb.append(FUZZY_RET_VARIABLE_NAME).append("_ > 0 ? ").append(FUZZY_RET_VARIABLE_NAME).append(" : null;");
        final String expression = sb.toString();
        if (expression.length() > SpreadsheetVersion.EXCEL2007.getMaxTextLength()) {
            throw new IllegalStateException("Generated expression is too long!");
        }
        grid.setCellValue(fuzzyReturns.get(0).getColumn(), 0, header);
        grid.setCellValue(fuzzyReturns.get(0).getColumn(), 1, expression);
        int j = fuzzyReturns.size() - 1;
        if (fuzzyReturns.get(j).getColumn() + fuzzyReturns.get(j).getWidth() - fuzzyReturns.get(0).getColumn() > 1) {
            for (int row = 0; row < IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1; row++) {
                grid.addMergedRegion(new GridRegion(row,
                    fuzzyReturns.get(0).getColumn(),
                    row,
                    fuzzyReturns.get(j).getColumn() + fuzzyReturns.get(j).getWidth() - 1));
            }
        }
    }

    private static void writeSimpleDTReturnHeader(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            SimpleReturnDTHeader simpleReturnDTHeader,
            String header,
            int collectParameterIndex,
            IBindingContext bindingContext) {
        grid.setCellValue(simpleReturnDTHeader.getColumn(), 0, header);

        if (tableSyntaxNode.getHeader().getCollectParameters().length > 0) {
            grid.setCellValue(simpleReturnDTHeader.getColumn(),
                2,
                tableSyntaxNode.getHeader().getCollectParameters()[collectParameterIndex]);
        }

        if (!bindingContext.isExecutionMode()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Return: ").append(header);
            ICell cell = originalTable.getSource().getCell(simpleReturnDTHeader.getColumn(), 0);
            if (!StringUtils.isEmpty(simpleReturnDTHeader.getStatement())) {
                sb.append("\n").append("Expression: ").append(simpleReturnDTHeader.getStatement());
            }
            DecisionTableMetaInfoReader
                .appendParameters(sb, null, new IOpenClass[] { decisionTable.getHeader().getType() });
            writeReturnMetaInfo(tableSyntaxNode, cell, sb.toString(), null);
        }

        if (simpleReturnDTHeader.getWidth() > 1) {
            for (int row = 0; row < IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT; row++) {
                grid.addMergedRegion(new GridRegion(row,
                    simpleReturnDTHeader.getColumn(),
                    row,
                    simpleReturnDTHeader.getColumn() + simpleReturnDTHeader.getWidth() - 1));
            }
        }
    }

    private static void writeReturns(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable uncutOriginalTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            FuzzyContext fuzzyContext,
            List<DTHeader> dtHeaders,
            DeclaredDTHeader lookupReturnDtHeader,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) throws OpenLCompilationException {
        final boolean isCollect = isCollect(tableSyntaxNode);

        if (isLookup(tableSyntaxNode)) {
            if (lookupReturnDtHeader != null) {
                writeReturnWithReturnDtHeader(tableSyntaxNode,
                    uncutOriginalTable,
                    originalTable,
                    grid,
                    lookupReturnDtHeader,
                    isCollect ? CRET1_COLUMN_NAME : RET1_COLUMN_NAME,
                    true,
                    module,
                    cache,
                    bindingContext);
            } else {
                int retColumn = getRetColumn(dtHeaders);
                grid.setCellValue(retColumn, 0, isCollect ? CRET1_COLUMN_NAME : RET1_COLUMN_NAME);
            }
            return;
        }

        if (dtHeaders.stream()
            .filter(DTHeader::isReturn)
            .anyMatch(e -> e.getColumn() + e.getWidth() - 1 >= originalTable.getSource().getWidth())) {
            throw new OpenLCompilationException("Wrong table structure: There is no column for return values.");
        }

        int retNum = 1;
        int cRetNum = 1;
        int i = 0;
        int collectParameterIndex = 0;
        int keyNum = 1;
        boolean skipFuzzyReturns = false;
        for (DTHeader dtHeader : dtHeaders) {
            if (dtHeader.isReturn()) {
                if (dtHeader instanceof DeclaredDTHeader) {
                    writeReturnWithReturnDtHeader(tableSyntaxNode,
                        uncutOriginalTable,
                        originalTable,
                        grid,
                        (DeclaredDTHeader) dtHeader,
                        isCollect ? DecisionTableColumnHeaders.COLLECT_RETURN.getHeaderKey() + cRetNum++
                                  : DecisionTableColumnHeaders.RETURN.getHeaderKey() + retNum++,
                        false,
                        module,
                        cache,
                        bindingContext);
                } else if (dtHeader instanceof SimpleReturnDTHeader || dtHeader instanceof FuzzyDTHeader && ((FuzzyDTHeader) dtHeader)
                    .getFieldsChain() == null) {
                    boolean isKey = false;
                    String header;
                    if (isCollect && tableSyntaxNode.getHeader()
                        .getCollectParameters().length > 1 && i == 0 && org.openl.util.ClassUtils
                            .isAssignable(decisionTable.getType().getInstanceClass(), Map.class)) {
                        header = DecisionTableColumnHeaders.KEY.getHeaderKey() + keyNum++;
                        isKey = true;
                    } else {
                        header = isCollect ? DecisionTableColumnHeaders.COLLECT_RETURN.getHeaderKey() + cRetNum++
                                           : DecisionTableColumnHeaders.RETURN.getHeaderKey() + retNum++;
                    }
                    SimpleReturnDTHeader simpleDTReturnHeader;
                    if (dtHeader instanceof FuzzyDTHeader) {
                        FuzzyDTHeader fuzzyDTHeader = (FuzzyDTHeader) dtHeader;
                        simpleDTReturnHeader = new SimpleReturnDTHeader(fuzzyDTHeader.getStatement(),
                            fuzzyDTHeader.getTitle(),
                            fuzzyDTHeader.getColumn(),
                            0,
                            fuzzyDTHeader.getWidth());
                    } else {
                        simpleDTReturnHeader = (SimpleReturnDTHeader) dtHeader;
                    }
                    writeSimpleDTReturnHeader(tableSyntaxNode,
                        decisionTable,
                        originalTable,
                        grid,
                        simpleDTReturnHeader,
                        header,
                        collectParameterIndex,
                        bindingContext);
                    i++;
                    if (isKey) {
                        collectParameterIndex++;
                    }
                } else if (dtHeader instanceof FuzzyDTHeader && !skipFuzzyReturns) {
                    IOpenClass compoundReturnType = getCompoundReturnType(tableSyntaxNode,
                        decisionTable,
                        bindingContext);

                    writeFuzzyReturns(tableSyntaxNode,
                        decisionTable,
                        originalTable,
                        grid,
                        fuzzyContext,
                        dtHeaders,
                        compoundReturnType,
                        isCollect ? DecisionTableColumnHeaders.COLLECT_RETURN.getHeaderKey() + cRetNum++
                                  : DecisionTableColumnHeaders.RETURN.getHeaderKey() + retNum++,
                        module,
                        cache,
                        bindingContext);
                    skipFuzzyReturns = true;
                }
            }
        }
    }

    private static void writeDeclaredDtHeader(DecisionTable decisionTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            DeclaredDTHeader declaredDtHeader,
            String header,
            int firstColumnHeight,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) {
        int column = declaredDtHeader.getColumn();
        grid.setCellValue(column, 0, header);
        grid.setCellValue(column, 1, declaredDtHeader.getStatement());

        int firstColumn = column;
        int lastParamFirstColumn = firstColumn;

        List<String> parameterNames = new ArrayList<>();
        List<IOpenClass> typeOfColumns = new ArrayList<>();
        for (int j = 0; j < declaredDtHeader.getColumnParameters().length; j++) {
            for (int k = 0; k < declaredDtHeader.getColumnParameters()[j].length; k++) {
                IParameterDeclaration param = declaredDtHeader.getColumnParameters()[j][k];
                if (param != null) {
                    String paramName = declaredDtHeader.getMatchedDefinition().getParameter(param.getName());
                    parameterNames.add(paramName);
                    grid.setCellValue(column,
                        2,
                        getTypeNameForCode(param.getType(),
                            module,
                            cache) + (paramName != null ? " " + paramName : ""));
                    typeOfColumns.add(param.getType());
                } else {
                    parameterNames.add(null);
                    typeOfColumns.add(declaredDtHeader.getDtColumnsDefinition().getCompositeMethod().getType());
                }
                int w1;
                if (declaredDtHeader.isHCondition()) {
                    w1 = 1;
                } else {
                    w1 = originalTable.getSource().getCell(column, firstColumnHeight).getWidth();
                }
                if (w1 > 1) {
                    grid.addMergedRegion(new GridRegion(2, column, 2, column + w1 - 1));
                }
                lastParamFirstColumn = column;
                column = column + w1;
            }
        }

        if (!bindingContext.isExecutionMode()) {
            int column1 = declaredDtHeader.getColumn();
            while (column1 < declaredDtHeader.getColumn() + declaredDtHeader.getWidth()) {
                if (declaredDtHeader.isAction()) {
                    writeMetaInfoForAction(decisionTable,
                        originalTable,
                        column1,
                        declaredDtHeader.getRow(),
                        header,
                        parameterNames.toArray(EMPTY_STRING_ARRAY),
                        declaredDtHeader.getStatement(),
                        typeOfColumns.toArray(IOpenClass.EMPTY),
                        declaredDtHeader.getMatchedDefinition().getDtColumnsDefinition().getUri());
                } else if (declaredDtHeader.isCondition() && !declaredDtHeader.isHCondition()) {
                    writeMetaInfoForVCondition(originalTable,
                        decisionTable,
                        column1,
                        declaredDtHeader.getRow(),
                        header,
                        parameterNames.toArray(EMPTY_STRING_ARRAY),
                        declaredDtHeader.getStatement(),
                        typeOfColumns.toArray(IOpenClass.EMPTY),
                        declaredDtHeader.getMatchedDefinition().getDtColumnsDefinition().getUri());
                }
                column1 = column1 + originalTable.getSource().getCell(column1, declaredDtHeader.getRow()).getWidth();
            }
        }

        if (column < firstColumn + declaredDtHeader.getWidthForMerge()) {
            grid.addMergedRegion(new GridRegion(IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1,
                lastParamFirstColumn,
                IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1,
                firstColumn + declaredDtHeader.getWidthForMerge() - 1));
            column = firstColumn + declaredDtHeader.getWidthForMerge();
        }
        // merge columns
        if (column - firstColumn > 1) {
            for (int row = 0; row < IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1; row++) {
                grid.addMergedRegion(new GridRegion(row, firstColumn, row, column - 1));
            }
        }
    }

    private static void writeRule(DecisionTable decisionTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            List<DTHeader> dtHeaders,
            IBindingContext bindingContext) throws OpenLCompilationException {
        List<DTHeader> rules = dtHeaders.stream()
            .filter(DTHeader::isRule)
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));
        if (!rules.isEmpty()) {
            if (rules.size() > 1) {
                String message = "Wrong table structure: Wrong number of rule numbers columns.";
                throw new OpenLCompilationException(message);
            }
            DTHeader rule = rules.iterator().next();
            if (rule.getColumn() != 0) {
                String message = "Wrong table structure: Wrong rule numbers column index.";
                throw new OpenLCompilationException(message);
            }
            if (rule instanceof FuzzyRulesDTHeader) {
                FuzzyRulesDTHeader fuzzyRulesDTHeader = (FuzzyRulesDTHeader) rule;
                grid.setCellValue(fuzzyRulesDTHeader.getColumn(), 0, DecisionTableColumnHeaders.RULE);
                if (!bindingContext.isExecutionMode()) {
                    writeMetaInfoForRule(decisionTable, originalTable, fuzzyRulesDTHeader.getColumn(), 0);
                }
            }
        }
    }

    private static void writeActions(DecisionTable decisionTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            List<DTHeader> dtHeaders,
            int firstColumnHeight,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) throws OpenLCompilationException {
        List<DTHeader> actions = dtHeaders.stream()
            .filter(DTHeader::isAction)
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));
        int num = 0;
        for (DTHeader action : actions) {
            if (action.getColumn() >= originalTable.getSource().getWidth()) {
                String message = "Wrong table structure: Wrong number of action columns.";
                throw new OpenLCompilationException(message);
            }

            DeclaredDTHeader declaredAction = (DeclaredDTHeader) action;
            String header = (DecisionTableColumnHeaders.ACTION.getHeaderKey() + (num + 1));
            writeDeclaredDtHeader(decisionTable,
                originalTable,
                grid,
                declaredAction,
                header,
                firstColumnHeight,
                module,
                cache,
                bindingContext);
            num++;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static boolean getMinMaxOrder(ILogicalTable originalTable,
            NumberOfColumnsUnderTitleCounter numberOfColumnsUnderTitleCounter,
            int firstColumnHeight,
            int column,
            IOpenClass type) {
        int h = firstColumnHeight;
        int height = originalTable.getSource().getHeight();
        int t1 = 0;
        int t2 = 0;
        IString2DataConvertor<?> string2DataConverter = String2DataConvertorFactory
            .getConvertor(type.getInstanceClass());
        while (h < height) {
            ICell cell1 = originalTable.getSource().getCell(column, h);
            try {
                String s1 = cell1.getStringValue();
                Object o1;
                try {
                    o1 = string2DataConverter.parse(s1, null);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                ICell cell2 = originalTable.getSource()
                    .getCell(column + numberOfColumnsUnderTitleCounter.getWidth(column, 0), h);
                String s2 = cell2.getStringValue();
                Object o2;
                try {
                    o2 = string2DataConverter.parse(s2, null);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                if (JavaOpenClass.STRING.equals(type) && o1 != null && o2 != null) {
                    int res = NumericStringComparator.INSTANCE.compare((String) o1, (String) o2);
                    if (res > 0) {
                        t1++;
                    } else if (res < 0) {
                        t2++;
                    }
                } else if (o1 instanceof Comparable && o2 instanceof Comparable) {
                    int res = ((Comparable) o1).compareTo(o2);
                    if (res > 0) {
                        t1++;
                    } else if (res < 0) {
                        t2++;
                    }
                }
            } finally {
                h = h + cell1.getHeight();
            }
        }
        return t1 <= t2;
    }

    private static final String[] MIN_MAX_ORDER = new String[] { "min", "max" };
    private static final String[] MAX_MIN_ORDER = new String[] { "max", "min" };

    private static void writeUnmatchedColumns(DecisionTable decisionTable,
            ILogicalTable originalTable,
            List<DTHeader> dtHeaders,
            int firstColumnHeight,
            IBindingContext bindingContext) throws OpenLCompilationException {
        List<DTHeader> unmatched = dtHeaders.stream()
            .filter(e -> e instanceof UnmatchedDtHeader)
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));
        for (DTHeader dtHeader : unmatched) {
            int column = dtHeader.getColumn();
            if (column > originalTable.getSource().getWidth()) {
                String message = "Wrong table structure: Columns count is less than parameters count";
                throw new OpenLCompilationException(message);
            }
            if (column == originalTable.getSource().getWidth()) {
                String message = "Wrong table structure: There is no column for return values";
                throw new OpenLCompilationException(message);
            }
            if (!bindingContext.isExecutionMode()) {
                writeMetaInfoForUnmatched(originalTable, decisionTable, column, firstColumnHeight - 1);
            }
            GridCellSourceCodeModule eGridCellSourceCodeModule = new GridCellSourceCodeModule(originalTable.getSource(),
                dtHeader.getColumn(),
                firstColumnHeight - 1,
                bindingContext);
            SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(
                String.format("Smart table has unmatched title '%s'.", eGridCellSourceCodeModule.getCode()),
                eGridCellSourceCodeModule);
            bindingContext.addError(error);
        }
    }

    private static void writeConditions(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            IWritableGrid grid,
            NumberOfColumnsUnderTitleCounter numberOfColumnsUnderTitleCounter,
            List<DTHeader> dtHeaders,
            int firstColumnHeight,
            int firstColumnForHCondition,
            WithVerticalTitles withVerticalTitles,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) throws OpenLCompilationException {

        List<DTHeader> conditions = dtHeaders.stream()
            .filter(e -> !(e instanceof UnmatchedDtHeader))
            .filter(DTHeader::isCondition)
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        int numOfVCondition = 0;
        int numOfHCondition = 0;

        int firstColumnForHConditionsOrReturns = dtHeaders.stream()
            .filter(e -> e.isCondition() && !e.isHCondition() || e.isAction())
            .mapToInt(e -> e.getColumn() + e.getWidth())
            .max()
            .orElse(0);
        boolean isCollect = isCollect(tableSyntaxNode);
        Map<DTHeader, IOpenClass> hConditionTypes = new HashMap<>();
        for (DTHeader condition : conditions) {
            int column = condition.getColumn();
            if (!isLookup(tableSyntaxNode)) {
                if (column > originalTable.getSource().getWidth()) {
                    String message = "Wrong table structure: Columns count is less than parameters count";
                    throw new OpenLCompilationException(message);
                }
                if (column > originalTable.getSource().getWidth()) {
                    String message = "Wrong table structure: There is no column for return values";
                    throw new OpenLCompilationException(message);
                }
            }
            // write headers
            //

            String header;
            if (!condition.isHCondition()) {
                // write vertical condition
                //
                numOfVCondition++;
                if (numOfVCondition == 1 && (conditions.stream()
                    .filter(e -> !e.isHCondition())
                    .count() < 2) && !(isCollect && decisionTable.getType()
                        .isArray() && !decisionTable.getType()
                            .getComponentClass()
                            .isArray()) && !(isCollect && ClassUtils
                                .isAssignable(decisionTable.getType().getInstanceClass(), Collection.class))) {
                    header = (DecisionTableColumnHeaders.MERGED_CONDITION.getHeaderKey() + numOfVCondition);
                } else {
                    header = (DecisionTableColumnHeaders.CONDITION.getHeaderKey() + numOfVCondition);
                }
            } else {
                // write horizontal condition
                //
                numOfHCondition++;
                header = (DecisionTableColumnHeaders.HORIZONTAL_CONDITION.getHeaderKey() + numOfHCondition);
            }

            if (condition instanceof DeclaredDTHeader) {
                writeDeclaredDtHeader(decisionTable,
                    originalTable,
                    grid,
                    (DeclaredDTHeader) condition,
                    header,
                    firstColumnHeight,
                    module,
                    cache,
                    bindingContext);
            } else {
                grid.setCellValue(column, 0, header);
                final int numberOfColumnsUnderTitle = numberOfColumnsUnderTitleCounter.get(column);
                IOpenClass type = getTypeForCondition(decisionTable, condition);
                if (condition instanceof FuzzyDTHeader && numberOfColumnsUnderTitle == 2 && condition
                    .getWidthForMerge() == numberOfColumnsUnderTitleCounter.getWidth(column,
                        0) + numberOfColumnsUnderTitleCounter.getWidth(column, 1) && type
                            .getInstanceClass() != null && (type.getInstanceClass()
                                .isPrimitive() || ClassUtils.isAssignable(type.getInstanceClass(), Comparable.class))) {
                    boolean minMaxOrder = getMinMaxOrder(originalTable,
                        numberOfColumnsUnderTitleCounter,
                        firstColumnHeight,
                        column,
                        type);
                    String statement;
                    String stringOperator = StringUtils.EMPTY;
                    if (JavaOpenClass.STRING.equals(type)) {
                        stringOperator = "string";
                    }
                    if (minMaxOrder) {
                        statement = "min " + stringOperator + "<= " + condition.getStatement() + " && " + condition
                            .getStatement() + " " + stringOperator + "< max";
                    } else {
                        statement = "max " + stringOperator + "> " + condition.getStatement() + " && " + condition
                            .getStatement() + " " + stringOperator + ">= min";
                    }
                    grid.setCellValue(column, 1, statement);
                    grid.setCellValue(column,
                        2,
                        getTypeNameForCode(type, module, cache) + " " + (minMaxOrder ? "min" : "max"));
                    int w1 = numberOfColumnsUnderTitleCounter.getWidth(column, 0);
                    if (w1 > 1) {
                        grid.addMergedRegion(new GridRegion(2, column, 2, column + w1 - 1));
                    }
                    grid.setCellValue(column + w1,
                        2,
                        getTypeNameForCode(type, module, cache) + " " + (minMaxOrder ? "max" : "min"));
                    int w2 = numberOfColumnsUnderTitleCounter.getWidth(column, 1);
                    if (w2 > 1) {
                        grid.addMergedRegion(new GridRegion(2, column + w1, 2, column + w1 + w2 - 1));
                    }
                    if (!condition.isHCondition()) {
                        if (!bindingContext.isExecutionMode()) {
                            writeMetaInfoForVCondition(originalTable,
                                decisionTable,
                                condition.getColumn(),
                                condition.getRow(),
                                header,
                                minMaxOrder ? MIN_MAX_ORDER : MAX_MIN_ORDER,
                                statement,
                                new IOpenClass[] { type, type },
                                null);
                        }
                        if (condition.getWidthForMerge() > 1) {
                            for (int row = 0; row < IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT - 1; row++) {
                                grid.addMergedRegion(
                                    new GridRegion(row, column, row, column + condition.getWidthForMerge() - 1));
                            }
                        }
                    }
                } else {
                    // Set type of condition values(for Ranges and Array)
                    Triple<String[], IOpenClass, String> typeOfValue = getTypeForConditionColumn(decisionTable,
                        originalTable,
                        condition,
                        numOfHCondition,
                        firstColumnForHConditionsOrReturns,
                        firstColumnHeight,
                        numberOfColumnsUnderTitle,
                        module,
                        cache,
                        bindingContext);
                    grid.setCellValue(column, 1, typeOfValue.getRight());
                    grid.setCellValue(column,
                        2,
                        typeOfValue.getLeft().length == 1 ? typeOfValue.getLeft()[0]
                                                          : typeOfValue.getLeft()[0] + " " + typeOfValue.getLeft()[1]);
                    if (condition.isHCondition()) {
                        hConditionTypes.put(condition, typeOfValue.getMiddle());
                    } else {
                        if (!bindingContext.isExecutionMode()) {
                            writeMetaInfoForVCondition(originalTable,
                                decisionTable,
                                condition.getColumn(),
                                condition.getRow(),
                                header,
                                typeOfValue.getLeft().length == 1 ? null : new String[] { typeOfValue.getLeft()[1] },
                                typeOfValue.getRight(),
                                new IOpenClass[] { typeOfValue.getMiddle() },
                                null);
                        }
                        if (condition.getWidth() > 1) {
                            for (int row = 0; row < IDecisionTableConstants.SIMPLE_DT_HEADERS_HEIGHT; row++) {
                                grid.addMergedRegion(
                                    new GridRegion(row, column, row, column + condition.getWidth() - 1));
                            }
                        }
                    }
                }
            }
        }

        if (!bindingContext.isExecutionMode()) {
            writeMetaInfoForHConditions(originalTable,
                decisionTable,
                conditions,
                firstColumnForHCondition,
                withVerticalTitles,
                hConditionTypes);
        }
    }

    private static void writeMetaInfoForVCondition(ILogicalTable originalTable,
            DecisionTable decisionTable,
            int column,
            int row,
            String header,
            String[] parameterNames,
            String conditionStatement,
            IOpenClass[] typeOfColumns,
            String url) {
        Objects.requireNonNull(header);
        MetaInfoReader metaReader = decisionTable.getSyntaxNode().getMetaInfoReader();
        if (metaReader instanceof DecisionTableMetaInfoReader) {
            DecisionTableMetaInfoReader metaInfoReader = (DecisionTableMetaInfoReader) metaReader;
            ICell cell = originalTable.getSource().getCell(column, row);
            cell = cell.getTopLeftCellFromRegion();
            metaInfoReader.addCondition(cell.getAbsoluteRow(),
                cell.getAbsoluteColumn(),
                header,
                parameterNames,
                conditionStatement,
                typeOfColumns,
                url,
                null,
                false);
        }
    }

    private static void writeMetaInfoForUnmatched(ILogicalTable originalTable,
            DecisionTable decisionTable,
            int column,
            int row) {
        MetaInfoReader metaReader = decisionTable.getSyntaxNode().getMetaInfoReader();
        if (metaReader instanceof DecisionTableMetaInfoReader) {
            DecisionTableMetaInfoReader metaInfoReader = (DecisionTableMetaInfoReader) metaReader;
            ICell cell = originalTable.getSource().getCell(column, row);
            cell = cell.getTopLeftCellFromRegion();
            metaInfoReader.addUnmatched(cell.getAbsoluteRow(), cell.getAbsoluteColumn());
        }
    }

    private static void writeMetaInfoForRule(DecisionTable decisionTable,
            ILogicalTable originalTable,
            int column,
            int row) {
        MetaInfoReader metaReader = decisionTable.getSyntaxNode().getMetaInfoReader();
        if (metaReader instanceof DecisionTableMetaInfoReader) {
            DecisionTableMetaInfoReader metaInfoReader = (DecisionTableMetaInfoReader) metaReader;
            ICell cell = originalTable.getSource().getCell(column, row);
            cell = cell.getTopLeftCellFromRegion();
            metaInfoReader.addRule(cell.getAbsoluteRow(), cell.getAbsoluteColumn());
        }
    }

    private static void writeMetaInfoForAction(DecisionTable decisionTable,
            ILogicalTable originalTable,
            int column,
            int row,
            String header,
            String[] parameterNames,
            String conditionStatement,
            IOpenClass[] typeOfColumns,
            String url) {
        Objects.requireNonNull(header);
        MetaInfoReader metaReader = decisionTable.getSyntaxNode().getMetaInfoReader();
        if (metaReader instanceof DecisionTableMetaInfoReader) {
            DecisionTableMetaInfoReader metaInfoReader = (DecisionTableMetaInfoReader) metaReader;
            ICell cell = originalTable.getSource().getCell(column, row);
            cell = cell.getTopLeftCellFromRegion();
            metaInfoReader.addAction(cell.getAbsoluteRow(),
                cell.getAbsoluteColumn(),
                header,
                parameterNames,
                conditionStatement,
                typeOfColumns,
                url,
                null);
        }
    }

    private static void writeMetaInfoForHConditions(ILogicalTable originalTable,
            DecisionTable decisionTable,
            List<DTHeader> conditions,
            int firstColumnForHCondition,
            WithVerticalTitles withVerticalTitles,
            Map<DTHeader, IOpenClass> hConditionTypes) {
        MetaInfoReader metaInfoReader = decisionTable.getSyntaxNode().getMetaInfoReader();
        int j = 0;
        List<DTHeader> hDtHeaders = conditions.stream().filter(DTHeader::isHCondition).collect(toList());
        int minColumn;
        if (!WithVerticalTitles.NO.equals(withVerticalTitles) && firstColumnForHCondition > 0) {
            minColumn = firstColumnForHCondition - originalTable.getSource()
                .getCell(firstColumnForHCondition - 1, 0)
                .getWidth();
            List<DTHeader> vDtHeaders = conditions.stream()
                .filter(e -> e.isCondition() && !e.isHCondition())
                .collect(toList());
            if (!vDtHeaders.isEmpty()) {
                DTHeader lastVCondition = vDtHeaders.get(vDtHeaders.size() - 1);
                if (lastVCondition instanceof DeclaredDTHeader) {
                    DeclaredDTHeader declaredDTHeader = (DeclaredDTHeader) lastVCondition;
                    if (!declaredDTHeader.isVerticalConditionWithMergedTitle()) {
                        minColumn = hDtHeaders.stream().mapToInt(DTHeader::getColumn).min().orElse(0);
                    }
                }
            }
        } else {
            minColumn = hDtHeaders.stream().mapToInt(DTHeader::getColumn).min().orElse(0);
        }
        int numOfCondition = 1;
        for (DTHeader condition : hDtHeaders) {
            int column = minColumn;
            while (column < originalTable.getSource().getWidth()) {
                ICell cell = originalTable.getSource().getCell(column, j);
                cell = cell.getTopLeftCellFromRegion();
                String cellValue = cell.getStringValue();
                if (cellValue != null && metaInfoReader instanceof DecisionTableMetaInfoReader) {
                    IOpenClass type = hConditionTypes.get(condition);
                    if (type == null) {
                        type = getTypeForCondition(decisionTable, condition);
                    }
                    ((DecisionTableMetaInfoReader) metaInfoReader).addCondition(cell.getAbsoluteRow(),
                        cell.getAbsoluteColumn(),
                        (DecisionTableColumnHeaders.HORIZONTAL_CONDITION.getHeaderKey() + numOfCondition),
                        null,
                        condition.getStatement(),
                        new IOpenClass[] { type },
                        condition instanceof DeclaredDTHeader ? ((DeclaredDTHeader) condition).getMatchedDefinition()
                            .getDtColumnsDefinition()
                            .getUri() : null,
                        null,
                        true);
                }
                column = column + cell.getWidth();
            }
            j = j + originalTable.getSource().getCell(originalTable.getSource().getWidth() - 1, j).getHeight();
            numOfCondition++;
        }
    }

    private static String toLowerCase(String x) {
        return x != null ? x.toLowerCase() : null;
    }

    private static MatchedDefinition matchByDTColumnDefinition(DecisionTable decisionTable,
            DTColumnsDefinition definition,
            int numberOfHConditions,
            IBindingContext bindingContext) {
        IOpenMethodHeader header = decisionTable.getHeader();
        boolean mayHaveCompilationErrors = false;
        if (definition.isReturn()) {
            IOpenClass methodReturnType = header.getType();
            if (definition.getCompositeMethod() == null) {
                return null;
            }
            IOpenClass definitionType = definition.getCompositeMethod().getType();
            IOpenCast openCast = bindingContext.getCast(definitionType, methodReturnType);
            if (openCast == null || !openCast.isImplicit()) {
                mayHaveCompilationErrors = true;
            }
        }

        List<ExpressionIdentifier> identifiers = definition.getIdentifiers();

        Map<String, IParameterDeclaration> completeParameters = new HashMap<>();
        for (IParameterDeclaration parameter : definition.getParameters()) {
            if (parameter != null && parameter.getName() != null) {
                completeParameters.put(toLowerCase(parameter.getName()), parameter);
            }
        }

        Set<String> methodParametersUsedInExpression = new HashSet<>();
        Map<String, String> originalMethodParametersUsedInExpression = new HashMap<>();
        for (ExpressionIdentifier identifier : identifiers) {
            if (!completeParameters.containsKey(toLowerCase(identifier.getIdentifier()))) {
                methodParametersUsedInExpression.add(toLowerCase(identifier.getIdentifier()));
                originalMethodParametersUsedInExpression.put(toLowerCase(identifier.getIdentifier()),
                    identifier.getIdentifier());
            }
        }

        Map<String, String> methodParametersToRename = new HashMap<>();
        Set<Integer> usedMethodParameterIndexes = new HashSet<>();
        Iterator<String> itr = methodParametersUsedInExpression.iterator();
        MatchType matchType = MatchType.STRICT;
        Map<String, Integer> paramToIndex = new HashMap<>();
        Set<Integer> usedParamIndexesByField = new HashSet<>();
        while (itr.hasNext()) {
            String param = itr.next();
            boolean found = false;
            for (int i = 0; i < definition.getHeader().getSignature().getNumberOfParameters(); i++) {
                if (param.equalsIgnoreCase(definition.getHeader().getSignature().getParameterName(i))) {
                    paramToIndex.put(param, i);
                    found = true;
                    IOpenClass type = definition.getHeader().getSignature().getParameterType(i);
                    for (int j = 0; j < header.getSignature().getNumberOfParameters(); j++) {
                        if (param.equalsIgnoreCase(header.getSignature().getParameterName(j)) && type
                            .isAssignableFrom(header.getSignature().getParameterType(j))) {
                            usedMethodParameterIndexes.add(j);
                            methodParametersToRename.put(param, header.getSignature().getParameterName(j));
                            break;
                        }
                    }
                    break;
                }
            }
            if (!found) {
                int numberOfCandidates = 0;
                for (int i = 0; i < definition.getHeader().getSignature().getNumberOfParameters(); i++) {
                    IOpenClass paramType = definition.getHeader().getSignature().getParameterType(i);
                    IOpenField field = paramType.getField(param, false);
                    if (field != null) {
                        for (int j = 0; j < header.getSignature().getNumberOfParameters(); j++) {
                            if (paramType.isAssignableFrom(header.getSignature().getParameterType(j))) {
                                usedParamIndexesByField.add(j);
                                numberOfCandidates++;
                            }
                        }
                    }
                }
                if (numberOfCandidates > 1) {
                    mayHaveCompilationErrors = true;
                }
                itr.remove();
            }
        }

        MatchType[] matchTypes = { MatchType.STRICT_CASTED,
                MatchType.METHOD_ARGS_RENAMED,
                MatchType.METHOD_ARGS_RENAMED_CASTED };

        for (MatchType mt : matchTypes) {
            itr = methodParametersUsedInExpression.iterator();
            while (itr.hasNext()) {
                String param = itr.next();
                if (methodParametersToRename.containsKey(param)) {
                    continue;
                }
                int j = paramToIndex.get(param);
                IOpenClass type = definition.getHeader().getSignature().getParameterType(j);
                boolean duplicatedMatch = false;
                for (int i = 0; i < header.getSignature().getNumberOfParameters(); i++) {
                    boolean predicate;
                    IOpenCast openCast = bindingContext.getCast(header.getSignature().getParameterType(i), type);
                    switch (mt) {
                        case METHOD_ARGS_RENAMED_CASTED:
                            predicate = openCast != null && openCast.isImplicit();
                            break;
                        case STRICT_CASTED:
                            predicate = openCast != null && openCast.isImplicit() && param
                                .equalsIgnoreCase(header.getSignature().getParameterName(i));
                            break;
                        case METHOD_ARGS_RENAMED:
                            predicate = type.isAssignableFrom(header.getSignature().getParameterType(i));
                            break;
                        default:
                            throw new IllegalStateException();
                    }

                    if (!usedMethodParameterIndexes.contains(i) && predicate) {
                        if (duplicatedMatch) {
                            return null;
                        }
                        duplicatedMatch = true;
                        matchType = mt;
                        usedMethodParameterIndexes.add(i);
                        String newParam;
                        switch (mt) {
                            case STRICT_CASTED:
                            case METHOD_ARGS_RENAMED_CASTED:
                                String typeName = type.getInstanceClass().getSimpleName();
                                if (bindingContext.findType(ISyntaxConstants.THIS_NAMESPACE, typeName) == null) {
                                    typeName = type.getJavaName();
                                }
                                newParam = "((" + typeName + ")" + header.getSignature().getParameterName(i) + ")";
                                break;
                            case METHOD_ARGS_RENAMED:
                                newParam = header.getSignature().getParameterName(i);
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        methodParametersToRename.put(param, newParam);
                    }
                }
            }
        }

        if (usedMethodParameterIndexes.size() != methodParametersUsedInExpression.size()) {
            if (numberOfHConditions > 0) {
                return null;
            }
            Set<String> u = new HashSet<>();
            for (int i = 0; i < header.getSignature().getNumberOfParameters(); i++) {
                u.add(header.getSignature().getParameterName(i));
            }
            for (int i = 0; i < header.getSignature().getNumberOfParameters(); i++) {
                String lowParamName = toLowerCase(header.getSignature().getParameterName(i));
                if (!usedMethodParameterIndexes.contains(i) && methodParametersUsedInExpression
                    .contains(lowParamName)) {
                    String newParamName = "_" + originalMethodParametersUsedInExpression.get(lowParamName);
                    while (u.contains(newParamName)) {
                        newParamName = "_" + newParamName;
                    }
                    u.add(newParamName);
                    methodParametersToRename.put(lowParamName, newParamName);
                }
            }
            mayHaveCompilationErrors = true;
        }

        final String code = definition.getExpression();

        Set<Integer> usedParamIndexes = new HashSet<>(usedMethodParameterIndexes);
        usedParamIndexes.addAll(usedParamIndexesByField);

        int[] usedMethodParameterIndexesArray = ArrayUtils.toPrimitive(usedParamIndexes.toArray(new Integer[0]));

        switch (matchType) {
            case STRICT:
                return new MatchedDefinition(definition,
                    code,
                    usedMethodParameterIndexesArray,
                    methodParametersToRename,
                    identifiers,
                    MatchType.STRICT,
                    mayHaveCompilationErrors);
            case STRICT_CASTED:
                return new MatchedDefinition(definition,
                    code,
                    usedMethodParameterIndexesArray,
                    methodParametersToRename,
                    identifiers,
                    MatchType.STRICT_CASTED,
                    mayHaveCompilationErrors);
            case METHOD_ARGS_RENAMED:
                return new MatchedDefinition(definition,
                    code,
                    usedMethodParameterIndexesArray,
                    methodParametersToRename,
                    identifiers,
                    MatchType.METHOD_ARGS_RENAMED,
                    mayHaveCompilationErrors);
            case METHOD_ARGS_RENAMED_CASTED:
                return new MatchedDefinition(definition,
                    code,
                    usedMethodParameterIndexesArray,
                    methodParametersToRename,
                    identifiers,
                    MatchType.METHOD_ARGS_RENAMED_CASTED,
                    mayHaveCompilationErrors);
            default:
                return null;
        }
    }

    private static ParameterTokens buildParameterTokens(DecisionTable decisionTable) {
        int numberOfParameters = decisionTable.getSignature().getNumberOfParameters();
        Map<Token, Integer> tokenToParameterIndex = new HashMap<>();
        Map<Token, IOpenField[]> tokenToFieldsChain = new HashMap<>();
        Set<Token> tokens = new HashSet<>();
        Set<Token> tokensToIgnore = new HashSet<>();
        for (int i = 0; i < numberOfParameters; i++) {
            IOpenClass parameterType = decisionTable.getSignature().getParameterType(i);
            if (isCompoundInputType(parameterType) && !parameterType.isArray()) {
                Map<Token, IOpenField[][]> openClassFuzzyTokens = OpenLFuzzyUtils
                    .tokensMapToOpenClassReadableFieldsRecursively(parameterType,
                        decisionTable.getSignature().getParameterName(i),
                        1);
                for (Map.Entry<Token, IOpenField[][]> entry : openClassFuzzyTokens.entrySet()) {
                    if (entry.getValue().length == 1 && !tokensToIgnore.contains(entry.getKey())) {
                        if (!tokens.contains(entry.getKey())) {
                            tokens.add(entry.getKey());
                            tokenToParameterIndex.put(entry.getKey(), i);
                            tokenToFieldsChain.put(entry.getKey(), entry.getValue()[0]);
                        } else {
                            tokens.remove(entry.getKey());
                            tokenToParameterIndex.remove(entry.getKey());
                            tokenToFieldsChain.remove(entry.getKey());
                            tokensToIgnore.add(entry.getKey());
                        }
                    }
                }
            }
        }
        for (int i = 0; i < numberOfParameters; i++) {
            String tokenString = OpenLFuzzyUtils
                .toTokenString(OpenLFuzzyUtils.phoneticFix(decisionTable.getSignature().getParameterName(i)));
            Token token = new Token(tokenString, 0);
            tokenToParameterIndex.put(token, i);
            tokens.add(token);
        }

        return new ParameterTokens(tokens.toArray(new Token[] {}), tokenToParameterIndex, tokenToFieldsChain);
    }

    private static class PredicateToken extends Token {
        boolean isTrue;

        public PredicateToken(String value, int distance, int minMatchedTokens, boolean isTrue) {
            super(value, distance, minMatchedTokens);
            this.isTrue = isTrue;
        }

        public boolean isTrue() {
            return isTrue;
        }
    }

    private static class RuleToken extends Token {
        public RuleToken(String value, int distance, int minMatchedTokens) {
            super(value, distance, minMatchedTokens);
        }
    }

    private static void matchWithFuzzySearchRec(DecisionTable decisionTable,
            ILogicalTable originalTable,
            IGridTable gridTable,
            FuzzyContext fuzzyContext,
            NumberOfColumnsUnderTitleCounter numberOfColumnsUnderTitleCounter,
            int numberOfHConditions,
            List<DTHeader> dtHeaders,
            int firstColumnHeight,
            int w,
            int h,
            List<String> parts,
            int sourceTableColumn,
            int firstColumnForHCondition,
            boolean skipNextColumn,
            WithVerticalTitles withVerticalTitles,
            boolean onlyReturns) {
        int w0 = gridTable.getCell(w, h).getWidth();
        int h0 = gridTable.getCell(w, h).getHeight();
        String d = gridTable.getCell(w, h).getStringValue();
        String mergedPartsTitle;
        if (sourceTableColumn + originalTable.getSource()
            .getCell(sourceTableColumn, 0)
            .getWidth() == firstColumnForHCondition && h == firstColumnHeight - 1 && (WithVerticalTitles.SLASH_IN_TITLE
                .equals(withVerticalTitles) && StringUtils.isNotBlank(
                    d) && d.contains(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER) || WithVerticalTitles.MERGED_COLUMN
                        .equals(withVerticalTitles) || WithVerticalTitles.EMPTY_COLUMN.equals(withVerticalTitles))) {
            if (!onlyReturns) {
                List<String> hTitles = new ArrayList<>(parts);
                String p = d;
                if (WithVerticalTitles.SLASH_IN_TITLE.equals(withVerticalTitles)) {
                    p = d.substring(d.indexOf(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER) + 1).trim();
                }
                hTitles.add(p);
                int horizontal = 0;
                for (String hTitle : hTitles) {
                    String tokenizedTitleString = OpenLFuzzyUtils.toTokenString(hTitle);
                    Token[] tokens = fuzzyContext.getParameterTokens().getTokens();
                    tokens = addTrueFalseTokens(fuzzyContext.getMaxDistance(), tokens);
                    List<FuzzyResult> fuzzyResults = OpenLFuzzyUtils.fuzzyExtract(tokenizedTitleString, tokens, true);
                    addFuzzyDtHeader(decisionTable,
                        fuzzyContext,
                        w,
                        h,
                        hTitle,
                        sourceTableColumn + originalTable.getSource().getCell(sourceTableColumn, 0).getWidth(),
                        1,
                        1,
                        fuzzyResults,
                        dtHeaders,
                        horizontal + 1);
                    horizontal++;
                }
            }
            String p;
            if (WithVerticalTitles.SLASH_IN_TITLE.equals(withVerticalTitles)) {
                p = d.substring(0, d.indexOf(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER)).trim();
            } else {
                return;
            }
            parts.add(p);
            mergedPartsTitle = p;
        } else {
            parts.add(d);
            mergedPartsTitle = String.join(" | ", parts);
        }
        if (h + h0 < firstColumnHeight) {
            int w2 = w;
            while (w2 < w + w0) {
                int w1 = gridTable.getCell(w2, h + h0).getWidth();
                matchWithFuzzySearchRec(decisionTable,
                    originalTable,
                    gridTable,
                    fuzzyContext,
                    numberOfColumnsUnderTitleCounter,
                    numberOfHConditions,
                    dtHeaders,
                    firstColumnHeight,
                    w2,
                    h + h0,
                    parts,
                    sourceTableColumn,
                    firstColumnForHCondition,
                    skipNextColumn,
                    withVerticalTitles,
                    onlyReturns);
                w2 = w2 + w1;
            }
        } else {
            String tokenizedTitleString = OpenLFuzzyUtils.toTokenString(mergedPartsTitle);
            if (fuzzyContext.isFuzzySupportsForReturnType()) {
                List<FuzzyResult> fuzzyResults = OpenLFuzzyUtils
                    .fuzzyExtract(mergedPartsTitle, fuzzyContext.getFuzzyReturnTokens(), true);
                for (FuzzyResult fuzzyResult : fuzzyResults) {
                    IOpenField[][] fieldsChains = fuzzyContext.getFieldsChainsForReturnToken(fuzzyResult.getToken());
                    for (IOpenField[] fieldsChain : fieldsChains) {
                        Objects.requireNonNull(fieldsChain);
                        dtHeaders.add(new FuzzyDTHeader(-1,
                            null,
                            mergedPartsTitle,
                            fieldsChain,
                            sourceTableColumn,
                            sourceTableColumn + w,
                            h,
                            w0,
                            w0,
                            fuzzyResult,
                            true,
                            false));
                    }
                }
            }
            if (!onlyReturns) {
                Token[] tokens = fuzzyContext.getParameterTokens().getTokens();
                if (numberOfColumnsUnderTitleCounter.get(sourceTableColumn) == 1) {
                    if (firstColumnForHCondition < 0 && numberOfHConditions > 0 && Arrays
                        .stream(decisionTable.getSignature().getParameterTypes())
                        .anyMatch(
                            e -> e.getInstanceClass() == Boolean.class || e.getInstanceClass() == boolean.class)) {
                        tokens = ArrayUtils.addAll(tokens,
                            new PredicateToken("is true", fuzzyContext.getMaxDistance() + 1, 2, true),
                            new PredicateToken("is false", fuzzyContext.getMaxDistance() + 1, 2, false));
                    } else {
                        tokens = addTrueFalseTokens(fuzzyContext.getMaxDistance(), tokens);
                    }
                    if (sourceTableColumn == 0) {
                        tokens = ArrayUtils.addAll(tokens, new RuleToken("rule", fuzzyContext.getMaxDistance() + 1, 1));
                    }
                }
                List<FuzzyResult> fuzzyResults = OpenLFuzzyUtils.fuzzyExtract(tokenizedTitleString, tokens, true);
                addFuzzyDtHeader(decisionTable,
                    fuzzyContext,
                    w,
                    h,
                    mergedPartsTitle,
                    sourceTableColumn,
                    skipNextColumn ? w0 + originalTable.getSource().getCell(sourceTableColumn + w0, h).getWidth() : w0,
                    w0,
                    fuzzyResults,
                    dtHeaders,
                    0);
            }
        }
        parts.remove(parts.size() - 1);
    }

    private static Token[] addTrueFalseTokens(int maxDistance, Token[] tokens) {
        return ArrayUtils.addAll(tokens,
            new PredicateToken("is true", maxDistance + 1, 2, true),
            new PredicateToken("is false", maxDistance + 1, 2, false),
            new PredicateToken("true", maxDistance + 1, 1, true),
            new PredicateToken("false", maxDistance + 1, 1, false));
    }

    private static void addFuzzyDtHeader(DecisionTable decisionTable,
            FuzzyContext fuzzyContext,
            int w,
            int h,
            String title,
            int sourceTableColumn,
            int w0,
            int widthForMerge,
            List<FuzzyResult> fuzzyResults,
            List<DTHeader> dtHeaders,
            int horizontal) {
        for (FuzzyResult fuzzyResult : fuzzyResults) {
            Integer paramIndex = fuzzyContext.getParameterTokens().getParameterIndex(fuzzyResult.getToken());
            if (paramIndex != null) {
                IOpenField[] fieldsChain = fuzzyContext.getParameterTokens().getFieldsChain(fuzzyResult.getToken());
                StringBuilder conditionStatement = new StringBuilder(
                    decisionTable.getSignature().getParameterName(paramIndex));
                if (fieldsChain != null) {
                    Pair<String, IOpenClass> c = buildStatementByFieldsChain(
                        decisionTable.getSignature().getParameterType(paramIndex),
                        fieldsChain);
                    String chainStatement = c.getLeft();
                    conditionStatement.append(".");
                    conditionStatement.append(chainStatement);
                }
                dtHeaders.add(new FuzzyDTHeader(paramIndex,
                    conditionStatement.toString(),
                    title,
                    fieldsChain,
                    sourceTableColumn,
                    horizontal > 0 ? sourceTableColumn + horizontal - 1 : sourceTableColumn + w,
                    h,
                    horizontal > 0 ? 1 : w0,
                    horizontal > 0 ? 1 : widthForMerge,
                    fuzzyResult,
                    false,
                    horizontal > 0));
            } else {
                if (fuzzyResult.getToken() instanceof PredicateToken) {
                    PredicateToken predicateToken = (PredicateToken) fuzzyResult.getToken();
                    dtHeaders.add(new FuzzyDTHeader(predicateToken.isTrue() ? "true" : "false",
                        title,
                        new IOpenField[] {},
                        sourceTableColumn,
                        horizontal > 0 ? sourceTableColumn + horizontal - 1 : sourceTableColumn,
                        h,
                        horizontal > 0 ? 1 : w0,
                        horizontal > 0 ? 1 : widthForMerge,
                        fuzzyResult,
                        false,
                        horizontal > 0));
                }
                if (sourceTableColumn == 0 && fuzzyResult.getToken() instanceof RuleToken) {
                    dtHeaders.add(new FuzzyRulesDTHeader(title, sourceTableColumn, h, w0, fuzzyResult));
                }
            }
        }

    }

    private static List<DTHeader> matchWithFuzzySearch(DecisionTable decisionTable,
            ILogicalTable originalTable,
            FuzzyContext fuzzyContext,
            NumberOfColumnsUnderTitleCounter numberOfColumnsUnderTitleCounter,
            int numberOfHConditions,
            int column,
            int lastColumn,
            List<DTHeader> dtHeaders,
            int firstColumnHeight,
            int firstColumnForHCondition,
            WithVerticalTitles withVerticalTitles,
            boolean onlyReturns) {
        if (onlyReturns && !fuzzyContext.isFuzzySupportsForReturnType()) {
            return Collections.emptyList();
        }
        if (numberOfHConditions > 0 && column >= lastColumn) {
            return Collections.emptyList();
        }
        int w = originalTable.getSource().getCell(column, 0).getWidth();
        IGridTable gt = originalTable.getSource().getSubtable(column, 0, w, firstColumnHeight);
        List<DTHeader> newDtHeaders = new ArrayList<>();

        int w0 = column + originalTable.getSource().getCell(column, 0).getWidth();
        boolean skipNextColumn = w0 + originalTable.getSource()
            .getCell(w0, 0)
            .getWidth() == firstColumnForHCondition && (WithVerticalTitles.EMPTY_COLUMN
                .equals(withVerticalTitles) || WithVerticalTitles.MERGED_COLUMN.equals(withVerticalTitles));

        matchWithFuzzySearchRec(decisionTable,
            originalTable,
            gt,
            fuzzyContext,
            numberOfColumnsUnderTitleCounter,
            numberOfHConditions,
            newDtHeaders,
            firstColumnHeight,
            0,
            0,
            new ArrayList<>(),
            column,
            firstColumnForHCondition,
            skipNextColumn,
            withVerticalTitles,
            onlyReturns);
        dtHeaders.addAll(newDtHeaders);
        return Collections.unmodifiableList(newDtHeaders);
    }

    private static boolean isCompatibleHeaders(DTHeader a, DTHeader b) {
        int c1 = a.getColumn();
        int c2 = a.getColumn() + a.getWidth() - 1;
        int d1 = b.getColumn();
        int d2 = b.getColumn() + b.getWidth() - 1;

        if (c1 <= d1 && d1 <= c2 || c1 <= d2 && d2 <= c2 || d1 <= c2 && c2 <= d2 || d1 <= c1 && c1 <= d2) {
            return false;
        }

        if ((a.isRule() && b.isCondition() || a.isCondition() && b.isAction() || a.isAction() && b.isReturn() || a
            .isCondition() && b.isReturn()) && c1 >= d1) {
            return false;
        }
        if ((b.isRule() && a.isCondition() || b.isCondition() && a.isAction() || b.isAction() && a.isReturn() || b
            .isCondition() && a.isReturn()) && d1 >= c1) {
            return false;
        }

        if (a instanceof FuzzyDTHeader && b instanceof FuzzyDTHeader) {
            FuzzyDTHeader a1 = (FuzzyDTHeader) a;
            FuzzyDTHeader b1 = (FuzzyDTHeader) b;
            if (a1.isMethodParameterUsed() && b1.isMethodParameterUsed()) {
                if (a1.isCondition() && b1
                    .isCondition() && a1.getMethodParameterIndex() == b1.getMethodParameterIndex() && Arrays
                        .deepEquals(a1.getFieldsChain(), b1.getFieldsChain())) {
                    return false;
                }
            }

            if (a1.isReturn() && b1.isReturn() && fieldsChainsIsCrossed(a1.getFieldsChain(), b1.getFieldsChain())) {
                return false;
            }

            if (!(a1.isHCondition() && b1.isHCondition() || a1.isCondition() && b1.isCondition() || a1.isAction() && b1
                .isAction() || a1.isReturn() && b1.isReturn()) && a1.getTopColumn() == b1.getTopColumn()) {
                return false;
            }
        }
        if (a instanceof DeclaredDTHeader && b instanceof DeclaredDTHeader) {
            DeclaredDTHeader a1 = (DeclaredDTHeader) a;
            DeclaredDTHeader b1 = (DeclaredDTHeader) b;
            return !a1.getMatchedDefinition()
                .getDtColumnsDefinition()
                .equals(b1.getMatchedDefinition().getDtColumnsDefinition());
        }
        return true;
    }

    private static final int FITS_MAX_LIMIT = 10000;
    private static final int MAX_NUMBER_OF_RETURNS = 3;

    private static boolean bruteForceHeaders(ILogicalTable originalTable,
            int column,
            int lastColumn,
            int firstColumnHeight,
            List<DTHeader> dtHeaders,
            boolean[][] matrix,
            Map<Integer, List<Integer>> columnToIndex,
            int maxColumnIndex,
            List<Integer> usedIndexes,
            List<DTHeader> used,
            Set<Integer> usedParameterIndexes,
            List<List<DTHeader>> fits,
            Set<Integer> failedToFit,
            int numberOfParameters,
            int numberOfHConditions,
            int numberOfReturns,
            int fuzzyReturnsFlag,
            int counter) {
        if (fits.size() > FITS_MAX_LIMIT) {
            return column >= maxColumnIndex;
        }
        List<Integer> indexes = columnToIndex.get(column);
        if (indexes == null || numberOfHConditions == 1 && usedParameterIndexes
            .size() >= numberOfParameters - numberOfHConditions + used.stream()
                .filter(DTHeader::isHCondition)
                .count()) {
            List<DTHeader> fit = new ArrayList<>(used);
            while (!fit.isEmpty() && (fit.get(fit.size() - 1) instanceof UnmatchedDtHeader)) {
                fit.remove(fit.size() - 1);
            }
            if (!fit.isEmpty()) {
                fits.add(Collections.unmodifiableList(fit));
            }
        }
        boolean lastColumnReached = column >= maxColumnIndex;
        if (indexes != null) {
            boolean last = true;
            for (Integer index : indexes) {
                boolean f = true;
                for (Integer usedIndex : usedIndexes) {
                    if (!matrix[index][usedIndex]) {
                        f = false;
                        break;
                    }
                }
                if (f) {
                    DTHeader dtHeader = dtHeaders.get(index);
                    boolean isFuzzyReturn = false;
                    if (dtHeader instanceof FuzzyDTHeader) {
                        FuzzyDTHeader fuzzyDTHeader = (FuzzyDTHeader) dtHeader;
                        if (fuzzyDTHeader.isReturn()) {
                            isFuzzyReturn = true;
                        }
                    }
                    if (isFuzzyReturn && fuzzyReturnsFlag == 2) {
                        continue;
                    }
                    Set<Integer> usedParameterIndexesTo = new HashSet<>(usedParameterIndexes);
                    for (int i : dtHeader.getMethodParameterIndexes()) {
                        usedParameterIndexesTo.add(i);
                    }
                    int numberOfReturns1 = dtHeader.isReturn() && !isFuzzyReturn ? numberOfReturns + 1
                                                                                 : numberOfReturns;
                    int fuzzyReturnsFlag1 = isFuzzyReturn && fuzzyReturnsFlag != 1 ? fuzzyReturnsFlag + 1
                                                                                   : fuzzyReturnsFlag;
                    if (numberOfReturns1 + (fuzzyReturnsFlag1 > 1 ? 1 : 0) <= MAX_NUMBER_OF_RETURNS) {
                        last = false;
                        usedIndexes.add(index);
                        used.add(dtHeaders.get(index));
                        lastColumnReached = lastColumnReached | bruteForceHeaders(originalTable,
                            column + dtHeader.getWidth(),
                            lastColumn,
                            firstColumnHeight,
                            dtHeaders,
                            matrix,
                            columnToIndex,
                            maxColumnIndex,
                            usedIndexes,
                            used,
                            usedParameterIndexesTo,
                            fits,
                            failedToFit,
                            numberOfParameters,
                            numberOfHConditions,
                            numberOfReturns1,
                            fuzzyReturnsFlag1,
                            counter + 1);
                        usedIndexes.remove(usedIndexes.size() - 1);
                        used.remove(used.size() - 1);
                    }
                }
            }
            if (!indexes.isEmpty() && last) {
                failedToFit.addAll(indexes);
            }
        }
        if (!lastColumnReached && (numberOfReturns + (fuzzyReturnsFlag > 1 ? 1 : 0)) == 0) {
            ICell cell = originalTable.getSource().getCell(column, firstColumnHeight - 1);
            if (column + cell.getWidth() <= maxColumnIndex) {
                boolean isHorizontal = column + cell.getWidth() >= lastColumn;
                used.add(new UnmatchedDtHeader(StringUtils.EMPTY,
                    column,
                    firstColumnHeight - 1,
                    cell.getWidth(),
                    isHorizontal));

                lastColumnReached = bruteForceHeaders(originalTable,
                    column + cell.getWidth(),
                    lastColumn,
                    firstColumnHeight,
                    dtHeaders,
                    matrix,
                    columnToIndex,
                    maxColumnIndex,
                    usedIndexes,
                    used,
                    usedParameterIndexes,
                    fits,
                    failedToFit,
                    numberOfParameters,
                    numberOfHConditions,
                    numberOfReturns,
                    fuzzyReturnsFlag,
                    counter + 1);
                used.remove(used.size() - 1);
            }
        }
        return lastColumnReached;
    }

    private static List<List<DTHeader>> filterHeadersByMax(List<List<DTHeader>> fits,
            ToLongFunction<List<DTHeader>> function,
            Predicate<List<DTHeader>> predicate) {
        long max = Long.MIN_VALUE;
        Set<Integer> functionIndexes = new HashSet<>();
        Set<Integer> matchIndexes = new HashSet<>();
        int index = 0;
        for (List<DTHeader> fit : fits) {
            if (predicate.test(fit)) {
                long current = function.applyAsLong(fit);
                if (current > max) {
                    max = current;
                    functionIndexes.clear();
                    functionIndexes.add(index);
                } else if (current == max) {
                    functionIndexes.add(index);
                }
            } else {
                matchIndexes.add(index);
            }
            index++;
        }

        Set<Integer> indexes = new HashSet<>(matchIndexes);
        indexes.addAll(functionIndexes);
        List<List<DTHeader>> newFits = new ArrayList<>();
        for (Integer i : indexes) {
            newFits.add(fits.get(i));
        }
        return newFits;
    }

    private static List<List<DTHeader>> filterHeadersByMin(List<List<DTHeader>> fits,
            ToLongFunction<List<DTHeader>> function,
            Predicate<List<DTHeader>> predicate) {
        long min = Long.MAX_VALUE;
        Set<Integer> functionIndexes = new HashSet<>();
        Set<Integer> matchIndexes = new HashSet<>();
        int index = 0;
        for (List<DTHeader> fit : fits) {
            if (predicate.test(fit)) {
                long current = function.applyAsLong(fit);
                if (current < min) {
                    min = current;
                    functionIndexes.clear();
                    functionIndexes.add(index);
                } else if (current == min) {
                    functionIndexes.add(index);
                }
            } else {
                matchIndexes.add(index);
            }
            index++;
        }
        Set<Integer> indexes = new HashSet<>(matchIndexes);
        indexes.addAll(functionIndexes);
        List<List<DTHeader>> newFits = new ArrayList<>();
        for (Integer i : indexes) {
            newFits.add(fits.get(i));
        }
        return newFits;
    }

    private static List<List<DTHeader>> filterHeadersByMatchType(DecisionTable decisionTable,
            List<List<DTHeader>> fits) {
        resolveConflictsInDeclaredDtHeaders(decisionTable, fits);
        MatchType[] matchTypes = MatchType.values();
        Arrays.sort(matchTypes, Comparator.comparingInt(MatchType::getPriority));
        for (MatchType type : matchTypes) {
            fits = filterHeadersByMax(fits,
                e -> e.stream()
                    .filter(x -> x instanceof DeclaredDTHeader)
                    .map(x -> (DeclaredDTHeader) x)
                    .filter(x -> type.equals(x.getMatchedDefinition().getMatchType()))
                    .mapToLong(x -> x.getMatchedDefinition().getDtColumnsDefinition().getNumberOfTitles())
                    .sum(),
                e -> true);
        }
        return fits;
    }

    private static boolean isLastDtColumnValid(DTHeader dtHeader, int maxColumn, int columnsForReturn) {
        if (dtHeader.isReturn()) {
            return dtHeader.getColumn() + dtHeader.getWidth() == maxColumn;
        }
        if (!dtHeader.isHCondition() && dtHeader.isCondition() || dtHeader.isAction()) {
            return dtHeader.getColumn() + dtHeader.getWidth() < maxColumn - columnsForReturn;
        }
        return true;
    }

    private static List<List<DTHeader>> filterWithWrongStructure(ILogicalTable originalTable,
            List<List<DTHeader>> fits,
            boolean twoColumnsInReturn) {
        int maxColumn = originalTable.getSource().getWidth();
        int w = 0;
        if (maxColumn > 0 && twoColumnsInReturn) {
            w = originalTable.getSource().getCell(maxColumn - 1, 0).getWidth();
            if (maxColumn - w > 0) {
                w = w + originalTable.getSource().getCell(maxColumn - 1 - w, 0).getWidth();
            }
        }
        final int w1 = w;

        return fits.stream()
            .filter(
                e -> e.isEmpty() || isLastDtColumnValid(e.get(e.size() - 1), maxColumn, twoColumnsInReturn ? w1 : 0))
            .collect(toList());
    }

    private static boolean fieldsChainsIsCrossed(IOpenField[] m1, IOpenField[] m2) {
        if (m1 == null && m2 == null) {
            return true;
        }
        if (m1 != null && m2 != null) {
            int i = 0;
            while (i < m1.length && i < m2.length) {
                if (m1[i].equals(m2[i])) {
                    i++;
                } else {
                    break;
                }
            }
            return i == m1.length || i == m2.length;
        }
        return false;
    }

    private static boolean isAmbiguousFits(List<List<DTHeader>> fits, Predicate<DTHeader> predicate) {
        if (fits.size() <= 1) {
            return false;
        }
        DTHeader[] dtHeaders0 = fits.get(0).stream().filter(predicate).toArray(DTHeader[]::new);
        for (int i = 1; i < fits.size(); i++) {
            DTHeader[] dtHeaders1 = fits.get(i).stream().filter(predicate).toArray(DTHeader[]::new);
            if (!Arrays.equals(dtHeaders0, dtHeaders1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersects(int b1, int e1, int b2, int e2) {
        return b2 <= b1 && b1 <= e2 || b2 <= e1 && e1 <= e2 || b1 <= b2 && b2 <= e1 || b1 <= e2 && e2 <= e1;
    }

    private static List<DTHeader> findStrongDtHeaders(ILogicalTable originalTable, List<DTHeader> dtHeaders) {
        // Remove headers that intersect with declared dt header if declared dt header is matched 100%
        boolean[] f = new boolean[dtHeaders.size()];
        Arrays.fill(f, false);
        for (int i = 0; i < dtHeaders.size() - 1; i++) {
            for (int j = i + 1; j < dtHeaders.size(); j++) {
                if (dtHeaders.get(i) instanceof DeclaredDTHeader && dtHeaders.get(j) instanceof DeclaredDTHeader) {
                    DeclaredDTHeader d1 = (DeclaredDTHeader) dtHeaders.get(i);
                    DeclaredDTHeader d2 = (DeclaredDTHeader) dtHeaders.get(j);
                    if (!d1.isHCondition() && !d2.isHCondition()) {
                        if (!(d1.getColumn() == d2.getColumn() && d1.getWidth() == d2.getWidth()) && intersects(
                            d1.getColumn(),
                            d1.getColumn() + d1.getWidth() - 1,
                            d2.getColumn(),
                            d2.getColumn() + d2.getWidth() - 1)) {
                            f[i] = true;
                            f[j] = true;
                        }
                    }
                }
            }
        }
        final int lastColumn = originalTable.getSource().getWidth();
        List<DTHeader> ret = new ArrayList<>();
        for (int i = 0; i < dtHeaders.size(); i++) {
            DTHeader dtHeader = dtHeaders.get(i);
            // Exclude from optimization conditions and actions that matches to the last column, where return is
            // expected.
            if (!dtHeader.isHCondition() && (dtHeader.isCondition() || dtHeader.isAction()) && dtHeader
                .getColumn() + dtHeader.getWidth() >= lastColumn) {
                continue;
            }
            if (dtHeader.isHCondition() || !f[i]) {
                ret.add(dtHeader);
            }
        }
        return ret;
    }

    private static List<List<DTHeader>> fitFuzzyDtHeaders(List<List<DTHeader>> fits) {
        fits = filterHeadersByMax(fits,
            e -> e.stream()
                .filter(x -> x instanceof FuzzyDTHeader)
                .map(x -> (FuzzyDTHeader) x)
                .mapToInt(x -> x.getFuzzyResult().getFoundTokensCount())
                .sum(),
            e -> true);
        fits = filterHeadersByMin(fits,
            e -> e.stream()
                .filter(x -> x instanceof FuzzyDTHeader)
                .map(x -> (FuzzyDTHeader) x)
                .mapToInt(x -> x.getFuzzyResult().getMissedTokensCount())
                .sum(),
            e -> true);
        fits = filterHeadersByMin(fits,
            e -> e.stream()
                .filter(x -> x instanceof FuzzyDTHeader)
                .map(x -> (FuzzyDTHeader) x)
                .mapToInt(x -> x.getFuzzyResult().getToken().getDistance())
                .sum(),
            e -> true);
        fits = filterHeadersByMin(fits,
            e -> e.stream()
                .filter(x -> x instanceof FuzzyDTHeader)
                .map(x -> (FuzzyDTHeader) x)
                .mapToInt(x -> x.getFuzzyResult().getUnmatchedTokensCount())
                .sum(),
            e -> true);
        return fits;
    }

    private static boolean isTheSameFit(List<DTHeader> a, List<DTHeader> b) {
        if (a.size() == b.size()) {
            for (int i = 0; i < a.size(); i++) {
                if (!Objects.equals(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static List<List<DTHeader>> removeDuplicates(List<List<DTHeader>> fits) {
        List<List<DTHeader>> ret = new ArrayList<>();
        for (List<DTHeader> fit : fits) {
            boolean f = false;
            for (List<DTHeader> e : ret) {
                if (isTheSameFit(fit, e)) {
                    f = true;
                    break;
                }
            }
            if (!f) {
                ret.add(fit);
            }
        }

        return ret;
    }

    private static List<DTHeader> fitDtHeaders(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            List<DTHeader> dtHeaders,
            int lastColumn,
            int numberOfHConditions,
            boolean twoColumnsForReturn,
            int firstColumnHeight,
            IBindingContext bindingContext) throws OpenLCompilationException {
        int numberOfParameters = decisionTable.getSignature().getNumberOfParameters();
        boolean[][] matrix = new boolean[dtHeaders.size()][dtHeaders.size()];
        for (int i = 0; i < dtHeaders.size(); i++) {
            for (int j = 0; j < dtHeaders.size(); j++) {
                matrix[i][j] = true;
            }
        }
        Map<Integer, List<Integer>> columnToIndex = new HashMap<>();
        for (int i = 0; i < dtHeaders.size(); i++) {
            List<Integer> indexes = columnToIndex.computeIfAbsent(dtHeaders.get(i).getColumn(), ArrayList::new);
            indexes.add(i);
            for (int j = i; j < dtHeaders.size(); j++) {
                if (i == j || !isCompatibleHeaders(dtHeaders.get(i), dtHeaders.get(j))) {
                    matrix[i][j] = false;
                    matrix[j][i] = false;
                }
            }
        }
        List<List<DTHeader>> fits = new ArrayList<>();
        Set<Integer> failedToFit = new HashSet<>();
        bruteForceHeaders(originalTable,
            0,
            lastColumn,
            firstColumnHeight,
            dtHeaders,
            matrix,
            columnToIndex,
            numberOfHConditions > 0 ? lastColumn + numberOfHConditions : originalTable.getSource().getWidth(),
            new ArrayList<>(),
            new ArrayList<>(),
            new HashSet<>(),
            fits,
            failedToFit,
            numberOfParameters,
            numberOfHConditions,
            0,
            0,
            0);

        if (fits.size() > FITS_MAX_LIMIT) {
            bindingContext.addMessage(OpenLMessagesUtils.newWarnMessage(
                "Ambiguous matching of column titles to DT conditions. Too many options are found.",
                tableSyntaxNode));
        }

        final Predicate<List<DTHeader>> all = e -> true;

        fits = filterHeadersByMax(fits,
            e -> e.stream()
                .map(DTHeader::getMethodParameterIndexes)
                .filter(Objects::nonNull)
                .flatMapToInt(Arrays::stream)
                .distinct()
                .count() <= numberOfParameters - numberOfHConditions + e.stream().filter(DTHeader::isHCondition).count()
                                                                                                                         ? 1
                                                                                                                         : 0,
            all);

        fits = filterWithWrongStructure(originalTable, fits, twoColumnsForReturn);

        // Declared covered columns filter
        fits = filterHeadersByMax(fits,
            e -> e.stream()
                .filter(x -> x instanceof DeclaredDTHeader)
                .mapToLong(
                    x -> ((DeclaredDTHeader) x).getMatchedDefinition().getDtColumnsDefinition().getNumberOfTitles())
                .sum(),
            all);

        fits = filterBasedOnDeclaredDtHeaders(fits);

        if (numberOfHConditions == 0) {
            // Prefer full matches with return headers
            fits = fits.stream().filter(e -> e.stream().anyMatch(DTHeader::isReturn)).collect(toList());
        } else {
            // Lookup table with no returns columns
            fits = fits.stream().filter(e -> e.stream().noneMatch(DTHeader::isReturn)).collect(toList());
        }

        // matches with min returns
        fits = filterHeadersByMin(fits, DecisionTableHelper::countReturns, all);

        fits = filterHeadersByMin(fits,
            e -> e.stream().filter(e1 -> e1 instanceof UnmatchedDtHeader && !e1.isHCondition()).count(),
            all);

        fits = filterHeadersByMin(fits,
            e -> e.stream()
                .filter(x -> x instanceof DeclaredDTHeader)
                .map(x -> (DeclaredDTHeader) x)
                .mapToLong(x -> x.getMatchedDefinition().isMayHaveCompilationErrors() ? 1 : 0)
                .sum(),
            e -> e.stream().anyMatch(x -> x instanceof DeclaredDTHeader));

        fits = filterHeadersByMatchType(decisionTable, fits);

        fits = filterHeadersByMax(fits,
            e -> e.stream().flatMapToInt(c -> Arrays.stream(c.getMethodParameterIndexes())).distinct().count(),
            e -> e.stream().anyMatch(x -> x.isCondition() && x instanceof DeclaredDTHeader));

        fits = filterHeadersByMin(fits,
            e -> e.stream().filter(x -> x instanceof SimpleReturnDTHeader).count(),
            e -> e.stream().anyMatch(DTHeader::isReturn));

        fits = fitFuzzyDtHeaders(fits);

        fits = removeDuplicates(fits);

        if (numberOfHConditions == 0 && fits.isEmpty()) {
            final List<DTHeader> dths = dtHeaders;
            OptionalInt c = failedToFit.stream().mapToInt(e -> dths.get(e).getColumn()).max();
            StringBuilder message = new StringBuilder();
            message.append("Failed to compile a decision table.");
            if (c.isPresent()) {
                int c0 = c.getAsInt();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < firstColumnHeight; i++) {
                    if (i > 0) {
                        sb.append(StringUtils.SPACE);
                        sb.append("|");
                        sb.append(StringUtils.SPACE);
                    }
                    sb.append(originalTable.getSource().getCell(c0, i).getStringValue());
                }
                message.append(StringUtils.SPACE);
                message.append("There is no match for column '").append(sb).append("'.");
            }
            throw new DTUnmatchedCompilationException(message.toString());
        }

        if (!fits.isEmpty()) {
            if (fits.size() > 1) {
                int mCount = 0;
                OpenLMessage warnMessage = null;
                if (isAmbiguousFits(fits, DTHeader::isCondition)) {
                    warnMessage = OpenLMessagesUtils.newWarnMessage(
                        "Ambiguous matching of column titles to DT conditions. Use more appropriate titles for condition columns.",
                        tableSyntaxNode);
                    mCount++;
                }
                if (isAmbiguousFits(fits, DTHeader::isAction)) {
                    warnMessage = OpenLMessagesUtils.newWarnMessage(
                        "Ambiguous matching of column titles to DT action columns. Use more appropriate titles for action columns.",
                        tableSyntaxNode);
                    mCount++;
                }
                if (isAmbiguousFits(fits, DTHeader::isReturn)) {
                    warnMessage = OpenLMessagesUtils.newWarnMessage(
                        "Ambiguous matching of column titles to DT return columns. Use more appropriate titles for return columns.",
                        tableSyntaxNode);
                    mCount++;
                }
                if (mCount == 1) {
                    bindingContext.addMessage(warnMessage);
                } else if (mCount > 0) {
                    bindingContext.addMessage(OpenLMessagesUtils.newWarnMessage(
                        "Ambiguous matching of column titles to DT columns. Use more appropriate titles.",
                        tableSyntaxNode));
                }
            }
            // Select with min returns/actions/conditions
            fits = filterHeadersByMin(fits, e -> e.stream().filter(DTHeader::isReturn).count(), all);
            fits = filterHeadersByMin(fits, e -> e.stream().filter(DTHeader::isAction).count(), all);
            fits = filterHeadersByMin(fits, e -> e.stream().filter(DTHeader::isCondition).count(), all);
            if (fits.stream().anyMatch(e -> e instanceof FuzzyDTHeader)) {
                fits = filterHeadersByMax(fits,
                    e -> e.stream()
                        .filter(e1 -> e1 instanceof FuzzyDTHeader)
                        .mapToLong(
                            e1 -> (long) ((FuzzyDTHeader) e1).getFuzzyResult().getAcceptableSimilarity() * 1000000L)
                        .sum() / e.stream().filter(e1 -> e1 instanceof FuzzyDTHeader).count(),
                    all);
            }
            return fits.get(0);
        }

        return Collections.emptyList();
    }

    private static long countReturns(List<DTHeader> dtHeaders) {
        int countReturns = 0;
        boolean fuzzyReturn = false;
        for (DTHeader dtHeader : dtHeaders) {
            if (dtHeader.isReturn()) {
                // If fieldsChain == null then it is a return for whole return type. It is not a part of fuzzy columns
                // returns.
                if (dtHeader instanceof FuzzyDTHeader && ((FuzzyDTHeader) dtHeader).getFieldsChain() != null) {
                    if (!fuzzyReturn) {
                        countReturns++;
                    }
                    fuzzyReturn = true;
                } else {
                    fuzzyReturn = false;
                    countReturns++;
                }
            }
        }
        return countReturns;
    }

    private static List<List<DTHeader>> filterBasedOnDeclaredDtHeaders(List<List<DTHeader>> fits) {
        List<List<DTHeader>> ret = new ArrayList<>();
        for (List<DTHeader> fit : fits) {
            Set<String> externalParameters = new HashSet<>();
            Map<String, Integer> parameters = new HashMap<>();
            for (DTHeader dtHeader : fit) {
                if (dtHeader instanceof DeclaredDTHeader) {
                    DeclaredDTHeader declaredDTHeader = (DeclaredDTHeader) dtHeader;
                    externalParameters.addAll(declaredDTHeader.getMatchedDefinition()
                        .getDtColumnsDefinition()
                        .getExternalParameters()
                        .stream()
                        .map(DecisionTableHelper::toLowerCase)
                        .collect(Collectors.toSet()));
                    for (IParameterDeclaration parameter : declaredDTHeader.getMatchedDefinition()
                        .getDtColumnsDefinition()
                        .getParameters()) {
                        if (parameter != null && parameter.getName() != null) {
                            parameters.merge(toLowerCase(parameter.getName()), 1, Integer::sum);
                        }
                    }
                }
            }
            boolean f = true;
            for (String externalParameter : externalParameters) {
                if (!parameters.containsKey(toLowerCase(externalParameter))) {
                    f = false;
                    break;
                }
            }
            if (f) {
                ret.add(fit);
            }
        }
        return ret.isEmpty() ? fits : ret;
    }

    private enum WithVerticalTitles {
        NO,
        SLASH_IN_TITLE,
        EMPTY_COLUMN,
        MERGED_COLUMN
    }

    public static Pair<Integer, WithVerticalTitles> getFirstColumnForHCondition(ILogicalTable originalTable,
            int numberOfHConditions,
            int firstColumnHeight,
            boolean isSmartLookup) {
        int w = originalTable.getSource().getWidth();
        int column = 0;
        int ret = -1;
        while (column < w) {
            int rowsCount = calculateRowsCount(originalTable, column, firstColumnHeight);
            if (rowsCount != numberOfHConditions) {
                ret = -1;
            }
            if (rowsCount > 1 && rowsCount == numberOfHConditions && ret < 0) {
                ret = column;
            }
            column = column + originalTable.getSource().getCell(column, 0).getWidth();
        }

        if (isSmartLookup && ret < w - 1) {
            int begin = Math.max(ret, 0);
            int end = begin > 0 ? begin + 1 : originalTable.getSource().getWidth();
            int i = begin;
            while (i < end) {
                String value = originalTable.getSource().getCell(i, firstColumnHeight - 1).getStringValue();
                if (StringUtils.isNotBlank(value) && value.contains(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER)) {
                    String part1 = value.substring(0, value.indexOf(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER));
                    String part2 = value.substring(value.indexOf(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER) + 1);
                    if (StringUtils.isNotBlank(part1) && StringUtils.isNotBlank(part2)) {
                        return Pair.of(i + originalTable.getSource().getCell(i, 0).getWidth(),
                            WithVerticalTitles.SLASH_IN_TITLE);
                    } else if (StringUtils.isBlank(part1) && StringUtils.isNotBlank(part2)) {
                        return Pair.of(i + originalTable.getSource().getCell(i, 0).getWidth(),
                            WithVerticalTitles.EMPTY_COLUMN);
                    } else if (i > 0 && StringUtils.isBlank(part1) && StringUtils.isNotBlank(part2)) {
                        int w1 = originalTable.getSource().getCell(i - 1, firstColumnHeight).getWidth();
                        int w2 = originalTable.getSource().getCell(i - 1, firstColumnHeight - 1).getWidth();
                        int w3 = originalTable.getSource().getCell(i, firstColumnHeight - 1).getWidth();
                        if (w1 == w2 + w3) {
                            return Pair.of(i + originalTable.getSource().getCell(i, 0).getWidth(),
                                WithVerticalTitles.MERGED_COLUMN);
                        }
                    }
                }
                i = i + originalTable.getSource().getCell(i, 0).getWidth();
            }
        }

        return Pair.of(ret, WithVerticalTitles.NO);
    }

    private static boolean columnWithFormulas(ILogicalTable originalTable, int firstColumnHeight, int column) {
        int h = firstColumnHeight;
        int height = originalTable.getSource().getHeight();
        int c = 0;
        int t = 0;
        while (h < height) {
            ICell cell = originalTable.getSource().getCell(column, h);
            String s = cell.getStringValue();
            if (!StringUtils.isEmpty(s != null ? s.trim() : null) && !RuleRowHelper.isFormula(s)) {
                c++;
            }
            t++;
            h = h + cell.getHeight();
        }
        return c <= t / 2 + t % 2;
    }

    private static boolean conflictsWithStrongDtHeader(List<DTHeader> strongDtHeaders,
            WithVerticalTitles withVerticalTitles,
            int firstColumnForHCondition,
            int column,
            int width) {
        if (!WithVerticalTitles.NO.equals(withVerticalTitles) && column + width == firstColumnForHCondition) {
            return false;
        }
        for (DTHeader dtHeader : strongDtHeaders) {
            if (intersects(dtHeader.getColumn(),
                dtHeader.getColumn() + dtHeader.getWidth() - 1,
                column,
                column + width - 1)) {
                return true;
            }
        }
        return false;
    }

    private static List<DTHeader> getDTHeaders(TableSyntaxNode tableSyntaxNode,
            DecisionTable decisionTable,
            ILogicalTable originalTable,
            FuzzyContext fuzzyContext,
            NumberOfColumnsUnderTitleCounter numberOfColumnsUnderTitleCounter,
            int numberOfHConditions,
            int firstColumnHeight,
            int firstColumnForHCondition,
            WithVerticalTitles withVerticalTitles,
            IBindingContext bindingContext) throws OpenLCompilationException {
        boolean isSmart = isSmart(tableSyntaxNode);

        int numberOfParameters = decisionTable.getSignature().getNumberOfParameters();
        boolean twoColumnsForReturn = isTwoColumnsForReturn(tableSyntaxNode, decisionTable);

        final XlsDefinitions xlsDefinitions = ((XlsModuleOpenClass) decisionTable.getDeclaringClass())
            .getXlsDefinitions();

        int lastColumn = originalTable.getSource().getWidth();
        if (numberOfHConditions > 0 && firstColumnForHCondition > 0) {
            lastColumn = firstColumnForHCondition;
        }

        String returnTokenString = fuzzyContext != null && fuzzyContext.isFuzzySupportsForReturnType() ? OpenLFuzzyUtils
            .toTokenString(fuzzyContext.getFuzzyReturnType().getName()) : null;
        List<DTHeader> dtHeaders = new ArrayList<>();
        int i = 0;
        int column = 0;
        if (isSmart) {
            while (column < lastColumn) {
                int w = originalTable.getSource().getCell(column, 0).getWidth();
                matchWithDtColumnsDefinitions(decisionTable,
                    originalTable,
                    column,
                    xlsDefinitions,
                    numberOfColumnsUnderTitleCounter,
                    dtHeaders,
                    firstColumnForHCondition,
                    withVerticalTitles,
                    firstColumnHeight,
                    numberOfHConditions,
                    bindingContext);
                column = column + w;
                i++;
            }
        }
        List<DTHeader> strongDtHeaders = findStrongDtHeaders(originalTable, dtHeaders);
        i = 0;
        column = 0;
        SimpleReturnDTHeader lastSimpleReturnDTHeader = null;
        while (column < lastColumn) {
            int w = originalTable.getSource().getCell(column, 0).getWidth();
            int row = 0;
            if (!conflictsWithStrongDtHeader(strongDtHeaders,
                withVerticalTitles,
                firstColumnForHCondition,
                column,
                w)) {
                if (isSmart) {
                    List<DTHeader> fuzzyHeaders = matchWithFuzzySearch(decisionTable,
                        originalTable,
                        fuzzyContext,
                        numberOfColumnsUnderTitleCounter,
                        numberOfHConditions,
                        column,
                        lastColumn,
                        dtHeaders,
                        firstColumnHeight,
                        firstColumnForHCondition,
                        withVerticalTitles,
                        false);
                    if (numberOfHConditions == 0) {
                        String titleForColumn = getTitleForColumn(originalTable, firstColumnHeight, column);
                        int width = originalTable.getSource().getCell(column, 0).getWidth();
                        lastSimpleReturnDTHeader = new SimpleReturnDTHeader(null, titleForColumn, column, row, width);
                        if (fuzzyContext != null && fuzzyContext.isFuzzySupportsForReturnType()) {
                            List<FuzzyResult> returnTypeFuzzyExtractResult = OpenLFuzzyUtils
                                .fuzzyExtract(titleForColumn, new Token[] { new Token(returnTokenString, -1) }, true);
                            if (!returnTypeFuzzyExtractResult.isEmpty()) {
                                dtHeaders.add(new FuzzyDTHeader(column,
                                    null,
                                    titleForColumn,
                                    null,
                                    column,
                                    column,
                                    row,
                                    width,
                                    width,
                                    returnTypeFuzzyExtractResult.get(0),
                                    true,
                                    false));
                            } else if (fuzzyHeaders.stream()
                                .noneMatch(DTHeader::isReturn) && numberOfColumnsUnderTitleCounter
                                    .get(column) == 1 && (column + w >= lastColumn || columnWithFormulas(originalTable,
                                        firstColumnHeight,
                                        column))) {
                                dtHeaders.add(lastSimpleReturnDTHeader);
                            }
                        } else {
                            dtHeaders.add(lastSimpleReturnDTHeader);
                        }
                    }
                } else {
                    if (numberOfHConditions == 0 && i >= numberOfParameters) {
                        matchWithFuzzySearch(decisionTable,
                            originalTable,
                            fuzzyContext,
                            numberOfColumnsUnderTitleCounter,
                            numberOfHConditions,
                            column,
                            lastColumn,
                            dtHeaders,
                            firstColumnHeight,
                            firstColumnForHCondition,
                            withVerticalTitles,
                            true);
                    }
                    if (i < numberOfParameters - numberOfHConditions) {
                        SimpleDTHeader simpleDTHeader = new SimpleDTHeader(i,
                            decisionTable.getSignature().getParameterName(i),
                            null,
                            column,
                            row,
                            w);
                        dtHeaders.add(simpleDTHeader);
                    } else if (numberOfHConditions == 0) {
                        SimpleReturnDTHeader simpleReturnDTHeader = new SimpleReturnDTHeader(null,
                            null,
                            column,
                            row,
                            w);
                        dtHeaders.add(simpleReturnDTHeader);
                    }
                }
            }
            column = column + w;
            i++;
        }

        if (lastSimpleReturnDTHeader != null && dtHeaders.stream().noneMatch(DTHeader::isReturn)) {
            dtHeaders.add(lastSimpleReturnDTHeader);
        }

        List<DTHeader> fit = fitDtHeaders(tableSyntaxNode,
            decisionTable,
            originalTable,
            dtHeaders,
            lastColumn,
            numberOfHConditions,
            twoColumnsForReturn,
            firstColumnHeight,
            bindingContext);

        if (numberOfHConditions > 0) {
            int maxColumnMatched = fit.stream()
                .filter(e -> e.isCondition() && !e.isHCondition() || e.isAction())
                .mapToInt(e -> e.getColumn() + e.getWidth())
                .max()
                .orElse(0);
            column = originalTable.getSource().getWidth() - 1;
            while (column > maxColumnMatched && calculateRowsCount(originalTable,
                column - 1,
                firstColumnHeight) == numberOfHConditions) {
                column--;
            }

            List<DTHeader> fitHCond = new ArrayList<>(fit);
            for (int c = maxColumnMatched; c < column; c++) {
                int num = numberOfColumnsUnderTitleCounter.get(c);
                int col1 = c;
                for (int j = 0; j < num; j++) {
                    int width = numberOfColumnsUnderTitleCounter.getWidth(c, j);
                    fitHCond.add(new UnmatchedDtHeader(StringUtils.EMPTY, col1, 0, width, false));
                    col1 = col1 + width;
                }
            }

            boolean[] parameterIsUsed = new boolean[numberOfParameters];
            Arrays.fill(parameterIsUsed, false);
            for (DTHeader dtHeader : fit) {
                for (int paramIndex : dtHeader.getMethodParameterIndexes()) {
                    parameterIsUsed[paramIndex] = true;
                }
            }
            int freeParameters = 0;
            for (boolean f : parameterIsUsed) {
                if (!f) {
                    freeParameters++;
                }
            }

            long hConditionsMatched = fit.stream()
                .filter(e -> e.isHCondition() && !(e instanceof UnmatchedDtHeader))
                .count();
            if (freeParameters + hConditionsMatched < numberOfHConditions) {
                SyntaxNodeException error = SyntaxNodeExceptionUtils
                    .createError("No input parameter found for horizontal condition.", tableSyntaxNode);
                bindingContext.addError(error);
                return fitHCond;
            }
            int j = 0;
            int w = 0;
            int c = 0;
            int len = fitHCond.size();
            while (w < numberOfParameters && j < numberOfHConditions - hConditionsMatched) {
                if (!parameterIsUsed[w]) {
                    while (c < len) {
                        DTHeader dth = fitHCond.get(c);
                        if (dth instanceof UnmatchedDtHeader && dth.isHCondition()) {
                            break;
                        }
                        c++;
                    }
                    if (c < len) {
                        fitHCond.set(c,
                            new SimpleDTHeader(w, decisionTable.getSignature().getParameterName(w), column + j, j));
                        c++;
                    } else {
                        fitHCond.add(
                            new SimpleDTHeader(w, decisionTable.getSignature().getParameterName(w), column + j, j));
                    }
                    j++;
                }
                w++;
            }
            return Collections.unmodifiableList(fitHCond);
        } else {
            return fit;
        }

    }

    private static String getTitleForColumn(ILogicalTable originalTable, int firstColumnHeight, int column) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < firstColumnHeight; j++) {
            if (j > 0) {
                sb.append(StringUtils.SPACE);
            }
            sb.append(originalTable.getSource().getCell(column, 0).getStringValue());
        }
        return sb.toString();
    }

    public static int getNumberOfHConditions(ILogicalTable originalTable) {
        return calculateRowsCount(originalTable,
            originalTable.getSource().getWidth() - 1,
            originalTable.getSource().getCell(0, 0).getHeight());
    }

    private static boolean isTwoColumnsForReturn(TableSyntaxNode tableSyntaxNode, DecisionTable decisionTable) {
        return isCollect(tableSyntaxNode) && ClassUtils.isAssignable(decisionTable.getType().getInstanceClass(),
            Map.class);
    }

    private static void matchWithDtColumnsDefinitions(DecisionTable decisionTable,
            ILogicalTable originalTable,
            int column,
            XlsDefinitions definitions,
            NumberOfColumnsUnderTitleCounter numberOfColumnsUnderTitleCounter,
            List<DTHeader> dtHeaders,
            int firstColumnForHCondition,
            WithVerticalTitles withVerticalTitles,
            int firstColumnHeight,
            int numberOfHConditions,
            IBindingContext bindingContext) {
        boolean parseAsHorizontalVerticalTitle = WithVerticalTitles.SLASH_IN_TITLE
            .equals(withVerticalTitles) && column + originalTable.getSource()
                .getCell(column, 0)
                .getWidth() == firstColumnForHCondition;
        int w0 = column + originalTable.getSource().getCell(column, 0).getWidth();
        boolean skipNextColumn = w0 + originalTable.getSource()
            .getCell(w0, 0)
            .getWidth() == firstColumnForHCondition && (WithVerticalTitles.EMPTY_COLUMN
                .equals(withVerticalTitles) || WithVerticalTitles.MERGED_COLUMN.equals(withVerticalTitles));
        if (parseAsHorizontalVerticalTitle || originalTable.getSource()
            .getCell(column, 0)
            .getHeight() == firstColumnHeight) {
            for (DTColumnsDefinition definition : definitions.getDtColumnsDefinitions()) {
                Set<String> titles = new HashSet<>(definition.getTitles());
                Triple<String, String, Integer> extractedTitle = extractTokenizedVerticalTitleString(originalTable,
                    column,
                    firstColumnHeight,
                    parseAsHorizontalVerticalTitle);
                Triple<String, String, Integer> lastExtractedTitle = extractedTitle;
                int i = 0;
                int x = column;
                IParameterDeclaration[][] columnParameters = null;
                int numberOfColumnsUnderTitle = numberOfColumnsUnderTitleCounter.get(x);
                boolean f1 = isMatchedByUnderColumns(definition.getParameters(extractedTitle.getLeft()),
                    numberOfColumnsUnderTitle);
                boolean f2 = !Objects.equals(extractedTitle.getLeft(),
                    extractedTitle.getMiddle()) && isMatchedByUnderColumns(
                        definition.getParameters(extractedTitle.getMiddle()),
                        numberOfColumnsUnderTitle);
                boolean g = false;
                while (!titles
                    .isEmpty() && ((numberOfHConditions > 0 && x < firstColumnForHCondition || x < originalTable
                        .getSource()
                        .getWidth()) && (f1 && titles.contains(
                            extractedTitle.getLeft()) || f2 && titles.contains(extractedTitle.getMiddle())))) {
                    g = false;
                    if (f1) {
                        titles.remove(extractedTitle.getLeft());
                    } else {
                        titles.remove(extractedTitle.getMiddle());
                    }
                    for (String s : definition.getTitles()) {
                        if (f1 && s.equals(extractedTitle.getLeft())) {
                            g = true;
                            if (columnParameters == null) {
                                columnParameters = new IParameterDeclaration[definition.getNumberOfTitles()][];
                            }
                            columnParameters[i] = definition.getParameters(extractedTitle.getLeft())
                                .toArray(IParameterDeclaration.EMPTY);
                            break;
                        }
                        if (f2 && s.equals(extractedTitle.getMiddle())) {
                            if (columnParameters == null) {
                                columnParameters = new IParameterDeclaration[definition.getNumberOfTitles()][];
                            }
                            columnParameters[i] = definition.getParameters(extractedTitle.getMiddle())
                                .toArray(IParameterDeclaration.EMPTY);
                            break;
                        }
                    }
                    i = i + 1;
                    int w = originalTable.getSource().getCell(x, 0).getWidth();
                    x = x + w;
                    lastExtractedTitle = extractedTitle;
                    extractedTitle = extractTokenizedVerticalTitleString(originalTable,
                        x,
                        firstColumnHeight,
                        parseAsHorizontalVerticalTitle);
                    parseAsHorizontalVerticalTitle = WithVerticalTitles.SLASH_IN_TITLE
                        .equals(withVerticalTitles) && column + originalTable.getSource()
                            .getCell(column, 0)
                            .getWidth() == firstColumnForHCondition;
                    numberOfColumnsUnderTitle = numberOfColumnsUnderTitleCounter.get(x);
                    f1 = isMatchedByUnderColumns(definition.getParameters(extractedTitle.getLeft()),
                        numberOfColumnsUnderTitle);
                    f2 = !Objects.equals(extractedTitle.getLeft(),
                        extractedTitle.getMiddle()) && isMatchedByUnderColumns(
                            definition.getParameters(extractedTitle.getMiddle()),
                            numberOfColumnsUnderTitle);
                }
                if (titles.isEmpty()) {
                    MatchedDefinition matchedDefinition = matchByDTColumnDefinition(decisionTable,
                        definition,
                        numberOfHConditions,
                        bindingContext);
                    if (matchedDefinition != null) {
                        DeclaredDTHeader dtHeader = new DeclaredDTHeader(
                            matchedDefinition.getUsedMethodParameterIndexes(),
                            definition,
                            columnParameters,
                            column,
                            lastExtractedTitle.getRight(),
                            x - column + (skipNextColumn ? originalTable.getSource().getCell(x, 0).getWidth() : 0),
                            x - column,
                            matchedDefinition,
                            false,
                            g && parseAsHorizontalVerticalTitle);
                        dtHeaders.add(dtHeader);
                    }
                }
            }
        }
        if (!WithVerticalTitles.NO.equals(withVerticalTitles) && column + originalTable.getSource()
            .getCell(column, 0)
            .getWidth() == firstColumnForHCondition) {
            for (DTColumnsDefinition definition : definitions.getDtColumnsDefinitions()) {
                if (definition.getNumberOfTitles() == 1) {
                    String definitionTitle = definition.getTitles().iterator().next();
                    int h = 0;
                    int x = 0;
                    while (h < firstColumnHeight) {
                        int h0 = originalTable.getSource().getCell(column, h).getHeight();
                        String title = originalTable.getSource().getCell(column, h).getStringValue();
                        if (h + h0 >= firstColumnHeight && WithVerticalTitles.SLASH_IN_TITLE
                            .equals(withVerticalTitles)) {
                            title = title.substring(title.indexOf(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER) + 1).trim();
                        }
                        if (x < numberOfHConditions) {
                            title = OpenLFuzzyUtils.toTokenString(title);
                            if (Objects.equals(title, definitionTitle)) {
                                MatchedDefinition matchedDefinition = matchByDTColumnDefinition(decisionTable,
                                    definition,
                                    numberOfHConditions,
                                    bindingContext);
                                if (matchedDefinition != null) {
                                    IParameterDeclaration[][] columnParameters = new IParameterDeclaration[1][];
                                    columnParameters[0] = definition.getParameters(title)
                                        .toArray(IParameterDeclaration.EMPTY);
                                    DeclaredDTHeader vDtHeader = new DeclaredDTHeader(
                                        matchedDefinition.getUsedMethodParameterIndexes(),
                                        definition,
                                        columnParameters,
                                        column + originalTable.getSource().getCell(column, 0).getWidth() + x,
                                        h,
                                        1,
                                        1,
                                        matchedDefinition,
                                        true,
                                        false);
                                    dtHeaders.add(vDtHeader);
                                    break;
                                }
                            }
                        }
                        h = h + h0;
                        x++;
                    }
                }
            }
        }
    }

    private static Triple<String, String, Integer> extractTokenizedVerticalTitleString(ILogicalTable originalTable,
            int column,
            int firstColumnHeight,
            boolean parseAsHorizontalVerticalTitle) {
        if (parseAsHorizontalVerticalTitle) {
            String title = originalTable.getSource().getCell(column, firstColumnHeight - 1).getStringValue();
            if (StringUtils.isNotBlank(title) && title.contains(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER)) {
                String cutTitle = title.substring(0, title.indexOf(HORIZONTAL_VERTICAL_CONDITIONS_SPLITTER)).trim();
                return Triple.of(OpenLFuzzyUtils.toTokenString(cutTitle),
                    OpenLFuzzyUtils.toTokenString(title),
                    firstColumnHeight - 1);
            }
        }
        String title = originalTable.getSource().getCell(column, 0).getStringValue();
        String tokenizedTitle = OpenLFuzzyUtils.toTokenString(title);
        return Triple.of(tokenizedTitle, tokenizedTitle, 0);
    }

    private static boolean isMatchedByUnderColumns(List<IParameterDeclaration> parameters,
            int numberOfColumnsUnderTitle) {
        boolean isAnyArrayTypePresented = parameters.stream()
            .anyMatch(e -> e != null && e.getType() != null && e.getType().isArray());
        return isAnyArrayTypePresented ? numberOfColumnsUnderTitle >= parameters.size()
                                       : numberOfColumnsUnderTitle == parameters.size();
    }

    private static boolean parsableAs(String[] values, Class<?> componentType, IBindingContext bindingContext) {
        try {
            for (String value : values) {
                String2DataConvertorFactory.parse(componentType, value, bindingContext);
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean parsableAs(String src, Class<?> clazz, IBindingContext bindingContext) {
        try {
            String2DataConvertorFactory.parse(clazz, src, bindingContext);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static int calculateRowsCount(ILogicalTable originalTable, int column, int height) {
        int h = 0;
        int k = 0;
        while (h < height && h < originalTable.getSource().getHeight()) {
            h = h + originalTable.getSource().getCell(column, h).getHeight();
            k++;
        }
        return k;
    }

    private static Triple<String[], IOpenClass, String> buildTripleForTypeForConditionColumn(Class<?> rangeClass,
            DTHeader condition,
            boolean isArray,
            boolean isMoreThanOneColumnIsUsed) {
        int type;
        if (isArray) {
            type = isMoreThanOneColumnIsUsed ? 2 : 1;
        } else {
            type = isMoreThanOneColumnIsUsed ? 1 : 0;
        }
        if (type == 0) {
            return Triple.of(new String[] { rangeClass.getSimpleName() },
                JavaOpenClass.getOpenClass(rangeClass),
                condition.getStatement());
        } else if (type == 1) {
            final String paramName = "_" + condition.getStatement().replaceAll("\\.", "_");
            return Triple.of(new String[] { rangeClass.getSimpleName() + "[]", paramName },
                AOpenClass.getArrayType(JavaOpenClass.getOpenClass(rangeClass), 1),
                "contains(" + paramName + ", " + condition.statement + ")");
        } else {
            final String paramName = "_" + condition.getStatement().replaceAll("\\.", "_");
            return Triple.of(new String[] { rangeClass.getSimpleName() + "[][]", paramName },
                AOpenClass.getArrayType(JavaOpenClass.getOpenClass(rangeClass), 2),
                "contains(" + paramName + ", " + condition.statement + ")");
        }
    }

    private static class CellValue {
        String value;
        ICell cell;

        public CellValue(ICell cell) {
            this.value = cell.getStringValue();
            this.cell = cell;
        }

        public String getValue() {
            return value;
        }

        public ICell getCell() {
            return cell;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            CellValue cellValue = (CellValue) o;
            return Objects.equals(value, cellValue.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    /**
     * Check type of condition values. If condition values are complex(Range, Array) then types of complex values will
     * be returned
     */
    @SuppressWarnings("unchecked")
    private static Triple<String[], IOpenClass, String> getTypeForConditionColumn(DecisionTable decisionTable,
            ILogicalTable originalTable,
            DTHeader condition,
            int indexOfHCondition,
            int firstColumnForHConditionsOrReturns,
            int firstColumnHeight,
            int numberOfColumnsUnderTitle,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache,
            IBindingContext bindingContext) {
        int column = condition.getColumn();

        IOpenClass type = getTypeForCondition(decisionTable, condition);

        IGridTable decisionValues;
        int width;
        int skip;
        int numberOfColumnsForCondition;
        if (condition.isHCondition()) {
            decisionValues = originalTable.getSource().getRow(indexOfHCondition - 1);
            width = decisionValues.getWidth();
            skip = firstColumnForHConditionsOrReturns;
            numberOfColumnsForCondition = 1;
        } else {
            decisionValues = originalTable.getSource().getColumns(column, column + numberOfColumnsUnderTitle - 1);
            width = decisionValues.getHeight();
            skip = firstColumnHeight;
            numberOfColumnsForCondition = numberOfColumnsUnderTitle;
        }

        boolean isAllParsableAsRangeFlag = true;
        boolean isAllLikelyNotRangeFlag = true;
        boolean isAllElementsLikelyNotRangeFlag = true;
        boolean isAllParsableAsSingleFlag = true;
        boolean isAllParsableAsDomainFlag = true;
        boolean isAllParsableAsDomainArrayFlag = true;
        boolean isAllParsableAsArrayFlag = true;
        boolean arraySeparatorFoundFlag = false;

        boolean isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = false;
        boolean zeroStartedNumbersFoundFlag = false;

        boolean isIntType = INT_TYPES.contains(type.getInstanceClass());
        boolean isDoubleType = DOUBLE_TYPES.contains(type.getInstanceClass());
        boolean isCharType = CHAR_TYPES.contains(type.getInstanceClass());
        boolean isDateType = DATE_TYPES.contains(type.getInstanceClass());
        boolean isStringType = STRING_TYPES.contains(type.getInstanceClass());
        boolean isRangeType = RANGE_TYPES.contains(type.getInstanceClass());

        boolean canMadeDecisionAboutSingle = true;

        boolean[][] h = new boolean[width][numberOfColumnsForCondition];
        for (int i = 0; i < width; i++) {
            Arrays.fill(h[i], true);
        }

        boolean isMoreThanOneColumnIsUsed = numberOfColumnsForCondition > 1;

        Map<Integer, LinkedHashSet<CellValue>> valuesMap = new HashMap<>();
        for (int valueNum = skip; valueNum < width; valueNum++) {
            IGridTable cellValues = condition.isHCondition() ? decisionValues.getColumn(valueNum)
                                                             : decisionValues.getRow(valueNum);
            Set<CellValue> values = valuesMap.computeIfAbsent(valueNum, e -> new LinkedHashSet<>());
            for (int cellNum = 0; cellNum < numberOfColumnsForCondition; cellNum++) {
                ICell cell = cellValues.getCell(0, cellNum);
                String value = cellValues.getCell(0, cellNum).getStringValue();
                if (value == null || StringUtils.isEmpty(value)) {
                    values.add(null);
                    h[valueNum][cellNum] = false;
                } else {
                    values.add(new CellValue(cell));
                }
            }
            int cellNum = -1;
            for (CellValue cellValue : values) {
                cellNum++;
                if (cellValue == null) {
                    continue;
                }
                String value = cellValue.getValue();

                if (RuleRowHelper.isFormula(value) && !isRangeType) {
                    try {
                        bindingContext.pushErrors();
                        bindingContext.pushMessages();
                        StringSourceCodeModule expressionCellSourceCodeModule = new StringSourceCodeModule(
                            value.substring(value.indexOf("=")).trim(),
                            null);
                        CompositeMethod compositeMethod = OpenLManager.makeMethodWithUnknownType(
                            bindingContext.getOpenL(),
                            expressionCellSourceCodeModule,
                            RandomStringUtils.random(16, true, false),
                            decisionTable.getSignature(),
                            decisionTable.getDeclaringClass(),
                            bindingContext);
                        IOpenClass cellType = compositeMethod.getType();
                        canMadeDecisionAboutSingle = canMadeDecisionAboutSingle && type.equals(cellType);
                        if (cellType.isArray() && RANGE_TYPES
                            .contains(cellType.getComponentClass().getInstanceClass())) {
                            isAllParsableAsArrayFlag = false;
                            isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                            isAllLikelyNotRangeFlag = false;
                            isAllElementsLikelyNotRangeFlag = false;
                        }
                        if (RANGE_TYPES.contains(cellType.getInstanceClass())) {
                            isAllParsableAsArrayFlag = false;
                            isAllLikelyNotRangeFlag = false;
                            isAllElementsLikelyNotRangeFlag = false;
                        }
                        if (cellType.isArray()) {
                            isAllParsableAsSingleFlag = false;
                            isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                        }

                    } finally {
                        bindingContext.popMessages();
                        bindingContext.popErrors();
                    }
                    h[valueNum][cellNum] = false;
                    continue;
                }

                ConstantOpenField constantOpenField = RuleRowHelper.findConstantField(bindingContext, value);
                if (constantOpenField != null) {
                    if (constantOpenField.getType().isArray() && RANGE_TYPES
                        .contains(constantOpenField.getType().getComponentClass().getInstanceClass())) {
                        isAllParsableAsArrayFlag = false;
                        isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                        isAllLikelyNotRangeFlag = false;
                        isAllElementsLikelyNotRangeFlag = false;
                    }
                    if (RANGE_TYPES.contains(constantOpenField.getType().getInstanceClass())) {
                        isAllParsableAsArrayFlag = false;
                        isAllLikelyNotRangeFlag = false;
                        isAllElementsLikelyNotRangeFlag = false;
                    }
                    if (constantOpenField.getType().isArray()) {
                        isAllParsableAsSingleFlag = false;
                        isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                    }
                    h[valueNum][cellNum] = false;
                    canMadeDecisionAboutSingle = canMadeDecisionAboutSingle && type.equals(constantOpenField.getType());
                    continue;
                }
                if (!arraySeparatorFoundFlag && ArraySplitter.isArray(value)) {
                    arraySeparatorFoundFlag = true;
                }
                try {
                    if ((isIntType || isDoubleType || isCharType) && isAllParsableAsSingleFlag && !parsableAs(value,
                        type.getInstanceClass(),
                        bindingContext)) {
                        isAllParsableAsSingleFlag = false;
                    } else if (isStringType) {
                        if (isAllParsableAsDomainFlag && (type
                            .getDomain() == null || !((IDomain<String>) type.getDomain()).selectObject(value))) {
                            isAllParsableAsDomainFlag = false;
                        }
                        if (isAllParsableAsDomainArrayFlag) {
                            if (type.getDomain() == null) {
                                isAllParsableAsDomainArrayFlag = false;
                            } else {
                                for (String s : ArraySplitter.split(value)) {
                                    if (!((IDomain<String>) type.getDomain()).selectObject(s)) {
                                        isAllParsableAsDomainArrayFlag = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (canMadeDecisionAboutSingle) {
            if ((isIntType || isDoubleType || isCharType) && isAllParsableAsSingleFlag || isStringType && isAllParsableAsDomainFlag) {
                return buildTripleForConditionColumnWithSimpleType(condition,
                    type,
                    false,
                    isMoreThanOneColumnIsUsed,
                    module,
                    cache);
            }

            if (isStringType && isAllParsableAsDomainArrayFlag) {
                return buildTripleForConditionColumnWithSimpleType(condition,
                    type,
                    true,
                    isMoreThanOneColumnIsUsed,
                    module,
                    cache);
            }
        }

        for (int valueNum = skip; valueNum < width; valueNum++) {
            Set<CellValue> values = valuesMap.get(valueNum);
            int cellNum = -1;
            for (CellValue cellValue : values) {
                cellNum++;
                if (cellValue == null || !h[valueNum][cellNum]) {
                    continue;
                }
                String value = cellValue.getValue();
                /* try to create range by values **/
                try {
                    if (isIntType) {
                        if (isAllParsableAsRangeFlag || !isNotParsableAsSingleRangeButParsableAsRangesArrayFlag) {
                            var arrs = ArraySplitter.split(value);
                            var f = parsableAs(arrs, IntRange.class, bindingContext);
                            boolean parsableAsSingleRange = parsableAs(value, IntRange.class, bindingContext);
                            if (!f && !parsableAsSingleRange) {
                                isAllParsableAsRangeFlag = false;
                            }
                            if (f && arrs.length > 1 && !parsableAsSingleRange) {
                                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                            }
                        }
                        if (isAllParsableAsArrayFlag) {
                            var arrs = ArraySplitter.split(value);
                            var g = parsableAs(arrs, type.getInstanceClass(), bindingContext);
                            if (g && !zeroStartedNumbersFoundFlag) { // If array element
                                // starts with 0 and
                                // can be range
                                // and
                                // array for all elements then use Range by default. But if
                                // no zero started elements then default String[]
                                zeroStartedNumbersFoundFlag = Arrays.stream(arrs)
                                    .anyMatch(e -> e != null && e.length() > 1 && e.startsWith("0"));
                            }
                            if (!g) {
                                isAllParsableAsArrayFlag = false;
                            }
                        }
                    } else if (isDoubleType) {
                        if (isAllParsableAsRangeFlag || !isNotParsableAsSingleRangeButParsableAsRangesArrayFlag) {
                            var arrs = ArraySplitter.split(value);
                            var f = parsableAs(arrs, DoubleRange.class, bindingContext);
                            boolean parsableAsSingleRange = parsableAs(value, DoubleRange.class, bindingContext);
                            if (!f && !parsableAsSingleRange) {
                                isAllParsableAsRangeFlag = false;
                            }
                            if (f && arrs.length > 1 && !parsableAsSingleRange) {
                                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                            }
                        }
                        if (isAllParsableAsArrayFlag) {
                            var arrs = ArraySplitter.split(value);
                            var g = parsableAs(arrs, type.getInstanceClass(), bindingContext);
                            if (g && !zeroStartedNumbersFoundFlag) {
                                zeroStartedNumbersFoundFlag = Arrays.stream(arrs)
                                    .anyMatch(e -> e != null && e.length() > 1 && e.startsWith("0"));
                            }
                            if (!g) {
                                isAllParsableAsArrayFlag = false;
                            }
                        }
                    } else if (isCharType) {
                        if (isAllParsableAsRangeFlag || !isNotParsableAsSingleRangeButParsableAsRangesArrayFlag) {
                            var arrs = ArraySplitter.split(value);
                            var f = parsableAs(arrs, CharRange.class, bindingContext);
                            boolean parsableAsSingleRange = parsableAs(value, CharRange.class, bindingContext);
                            if (!f && !parsableAsSingleRange) {
                                isAllParsableAsRangeFlag = false;
                            }
                            if (f && arrs.length > 1 && !parsableAsSingleRange) {
                                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                            }
                        }
                        if (isAllParsableAsArrayFlag) {
                            var arrs = ArraySplitter.split(value);
                            var g = parsableAs(arrs, type.getInstanceClass(), bindingContext);
                            if (!g) {
                                isAllParsableAsArrayFlag = false;
                            }
                        }
                    } else if (isDateType) {
                        Object o = cellValue.getCell().getObjectValue();
                        if (o instanceof Date) {
                            continue;
                        }
                        if (o instanceof String && !parsableAs(value, type.getInstanceClass(), bindingContext)) {
                            isAllParsableAsSingleFlag = false;
                        }
                        String[] arrs = null;
                        if (isAllParsableAsRangeFlag || !isNotParsableAsSingleRangeButParsableAsRangesArrayFlag) {
                            arrs = ArraySplitter.split(value);
                            var f = parsableAs(arrs, DateRange.class, bindingContext);
                            boolean parsableAsSingleRange = parsableAs(value, DateRange.class, bindingContext);
                            if (isAllParsableAsRangeFlag && !f && !parsableAsSingleRange) {
                                isAllParsableAsRangeFlag = false;
                            }
                            if (f && arrs.length > 1 && !parsableAsSingleRange) {
                                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                            }
                        }
                        if (isAllLikelyNotRangeFlag && o instanceof String && DateRangeParser.getInstance()
                            .likelyRangeThanDate(value)) {
                            isAllLikelyNotRangeFlag = false;
                        }
                        if (isAllElementsLikelyNotRangeFlag) {
                            if (arrs == null) {
                                arrs = ArraySplitter.split(value);
                            }
                            for (String v : arrs) {
                                if (DateRangeParser.getInstance().likelyRangeThanDate(v)) {
                                    isAllElementsLikelyNotRangeFlag = false;
                                    break;
                                }
                            }
                        }
                        if (isAllParsableAsArrayFlag) {
                            arrs = ArraySplitter.split(value);
                            var g = parsableAs(arrs, type.getInstanceClass(), bindingContext);
                            if (!g) {
                                isAllParsableAsArrayFlag = false;
                            }
                        }
                    } else if (isStringType) {
                        String[] arrs = null;
                        if (isAllParsableAsRangeFlag || !isNotParsableAsSingleRangeButParsableAsRangesArrayFlag) {
                            arrs = ArraySplitter.split(value);
                            var f = parsableAs(arrs, StringRange.class, bindingContext);
                            if (isAllParsableAsRangeFlag && !f && !parsableAs(value,
                                StringRange.class,
                                bindingContext)) {
                                isAllParsableAsRangeFlag = false;
                            }
                            if (!isNotParsableAsSingleRangeButParsableAsRangesArrayFlag && f && arrs.length > 1) {
                                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag = true;
                            }
                        }
                        if (isAllLikelyNotRangeFlag && StringRangeParser.getInstance().likelyRangeThanString(value)) {
                            isAllLikelyNotRangeFlag = false;
                        }
                        if (isAllElementsLikelyNotRangeFlag) {
                            if (arrs == null) {
                                arrs = ArraySplitter.split(value);
                            }
                            for (String v : arrs) {
                                if (StringRangeParser.getInstance().likelyRangeThanString(v)) {
                                    isAllElementsLikelyNotRangeFlag = false;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (isDateType && isAllParsableAsRangeFlag && ((isNotParsableAsSingleRangeButParsableAsRangesArrayFlag ? !isAllElementsLikelyNotRangeFlag
                                                                                                               : !isAllLikelyNotRangeFlag) || !isAllParsableAsArrayFlag)) {
            return buildTripleForTypeForConditionColumn(DateRange.class,
                condition,
                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag,
                isMoreThanOneColumnIsUsed);
        } else if (isIntType && isAllParsableAsRangeFlag && (!isAllParsableAsArrayFlag || zeroStartedNumbersFoundFlag)) {
            return buildTripleForTypeForConditionColumn(IntRange.class,
                condition,
                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag,
                isMoreThanOneColumnIsUsed);
        } else if (isDoubleType && isAllParsableAsRangeFlag && (!isAllParsableAsArrayFlag || zeroStartedNumbersFoundFlag)) {
            return buildTripleForTypeForConditionColumn(DoubleRange.class,
                condition,
                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag,
                isMoreThanOneColumnIsUsed);
        } else if (isCharType && isAllParsableAsRangeFlag && !isAllParsableAsArrayFlag) {
            return buildTripleForTypeForConditionColumn(CharRange.class,
                condition,
                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag,
                isMoreThanOneColumnIsUsed);
        } else if (isSmart(decisionTable
            .getSyntaxNode()) && isStringType && !isAllParsableAsDomainFlag && isAllParsableAsRangeFlag && ((isNotParsableAsSingleRangeButParsableAsRangesArrayFlag ? !isAllElementsLikelyNotRangeFlag
                                                                                                                                                                    : !isAllLikelyNotRangeFlag) || !isAllParsableAsArrayFlag)) {
            return buildTripleForTypeForConditionColumn(StringRange.class,
                condition,
                isNotParsableAsSingleRangeButParsableAsRangesArrayFlag,
                isMoreThanOneColumnIsUsed);
        }

        if (!type.isArray() && isAllParsableAsArrayFlag && (!isAllParsableAsSingleFlag || arraySeparatorFoundFlag)) {
            return buildTripleForConditionColumnWithSimpleType(condition,
                type,
                true,
                isMoreThanOneColumnIsUsed,
                module,
                cache);
        }

        if (isAllParsableAsSingleFlag) {
            return buildTripleForConditionColumnWithSimpleType(condition,
                type,
                false,
                isMoreThanOneColumnIsUsed,
                module,
                cache);
        }

        if (!type.isArray()) {
            if (isDateType) {
                return buildTripleForTypeForConditionColumn(DateRange.class,
                    condition,
                    true,
                    isMoreThanOneColumnIsUsed);
            } else if (isIntType) {
                return buildTripleForTypeForConditionColumn(IntRange.class, condition, true, isMoreThanOneColumnIsUsed);
            } else if (isDoubleType) {
                return buildTripleForTypeForConditionColumn(DoubleRange.class,
                    condition,
                    true,
                    isMoreThanOneColumnIsUsed);
            } else if (isCharType) {
                return buildTripleForTypeForConditionColumn(CharRange.class,
                    condition,
                    true,
                    isMoreThanOneColumnIsUsed);
            } else if (isStringType && isSmart(decisionTable.getSyntaxNode()) && !isAllParsableAsDomainFlag) {
                return buildTripleForTypeForConditionColumn(StringRange.class,
                    condition,
                    true,
                    isMoreThanOneColumnIsUsed);
            }
            return buildTripleForConditionColumnWithSimpleType(condition,
                type,
                true,
                isMoreThanOneColumnIsUsed,
                module,
                cache);
        } else {
            return buildTripleForConditionColumnWithSimpleType(condition,
                type,
                false,
                isMoreThanOneColumnIsUsed,
                module,
                cache);
        }
    }

    private static Triple<String[], IOpenClass, String> buildTripleForConditionColumnWithSimpleType(DTHeader condition,
            IOpenClass type,
            boolean isArray,
            boolean isMoreThanOneColumnIsUsed,
            XlsModuleOpenClass module,
            IdentityHashMap<ModuleOpenClass, IdentityHashMap<ModuleOpenClass, Boolean>> cache) {
        if (type.isArray() && type.getComponentClass().isArray()) {
            return Triple.of(new String[] { getTypeNameForCode(type, module, cache) }, type, condition.getStatement());
        }
        int v;
        if (isArray) {
            v = isMoreThanOneColumnIsUsed ? 2 : 1;
        } else {
            v = isMoreThanOneColumnIsUsed ? 1 : 0;
        }

        if (v == 0) {
            return Triple.of(new String[] { getTypeNameForCode(type, module, cache) }, type, condition.getStatement());
        } else if (v == 1) {
            return Triple.of(new String[] { getTypeNameForCode(type, module, cache) + "[]" },
                AOpenClass.getArrayType(type, 1),
                condition.getStatement());
        } else {
            return Triple.of(new String[] { getTypeNameForCode(type, module, cache) + "[][]" },
                AOpenClass.getArrayType(type, 2),
                condition.getStatement());
        }
    }

    private static IOpenClass getTypeForCondition(DecisionTable decisionTable, DTHeader condition) {
        if (condition instanceof FuzzyDTHeader) {
            FuzzyDTHeader fuzzyCondition = (FuzzyDTHeader) condition;
            if (fuzzyCondition.isMethodParameterUsed()) {
                if (fuzzyCondition.getFieldsChain() != null) {
                    return fuzzyCondition.getFieldsChain()[fuzzyCondition.getFieldsChain().length - 1].getType();
                }
            } else {
                if (fuzzyCondition.getFuzzyResult().getToken() instanceof PredicateToken) {
                    return JavaOpenClass.getOpenClass(Boolean.class);
                }
            }
        } else if (condition instanceof DeclaredDTHeader) {
            DeclaredDTHeader declaredDTHeader = (DeclaredDTHeader) condition;
            return declaredDTHeader.getDtColumnsDefinition().getCompositeMethod().getType();
        }
        if (condition.isMethodParameterUsed()) {
            return decisionTable.getSignature().getParameterTypes()[condition.getMethodParameterIndex()];
        }
        throw new IllegalStateException();
    }

    /**
     * @deprecated Use plain grid model aka 2d array instead of building memory expensive Excel files.
     */
    @Deprecated
    public static XlsSheetGridModel createVirtualGrid() {
        Workbook workbook = new XSSFWorkbook();
        try {
            final Sheet sheet = workbook.createSheet();
            final StringSourceCodeModule sourceCodeModule = new StringSourceCodeModule("", null);
            final SimpleWorkbookLoader workbookLoader = new SimpleWorkbookLoader(sheet.getWorkbook());
            XlsWorkbookSourceCodeModule mockWorkbookSource = new XlsWorkbookSourceCodeModule(sourceCodeModule,
                workbookLoader);
            XlsSheetSourceCodeModule mockSheetSource = new XlsSheetSourceCodeModule(new SimpleSheetLoader(sheet),
                mockWorkbookSource);

            return new XlsSheetGridModel(mockSheetSource);
        } catch (Exception e) {
            // If exception is thrown, we must close workbook in this method and rethrow exception.
            // If no exception, workbook will be closed later.
            IOUtils.closeQuietly(workbook);
            throw e;
        }
    }

    public static boolean isCollect(TableSyntaxNode tableSyntaxNode) {
        return tableSyntaxNode.getHeader().isCollect();
    }

    public static boolean isSmart(TableSyntaxNode tableSyntaxNode) {
        return isSmartDecisionTable(tableSyntaxNode) || isSmartLookupTable(tableSyntaxNode);
    }

    public static boolean isSimple(TableSyntaxNode tableSyntaxNode) {
        return isSimpleDecisionTable(tableSyntaxNode) || isSimpleLookupTable(tableSyntaxNode);
    }

    public static boolean isLookup(TableSyntaxNode tableSyntaxNode) {
        return isSimpleLookupTable(tableSyntaxNode) || isSmartLookupTable(tableSyntaxNode);
    }

    public static boolean isSmartDecisionTable(TableSyntaxNode tableSyntaxNode) {
        String dtType = tableSyntaxNode.getHeader().getHeaderToken().getIdentifier();
        return IXlsTableNames.SMART_DECISION_TABLE.equals(dtType);
    }

    public static boolean isSimpleDecisionTable(TableSyntaxNode tableSyntaxNode) {
        String dtType = tableSyntaxNode.getHeader().getHeaderToken().getIdentifier();
        return IXlsTableNames.SIMPLE_DECISION_TABLE.equals(dtType);
    }

    public static boolean isSmartLookupTable(TableSyntaxNode tableSyntaxNode) {
        String dtType = tableSyntaxNode.getHeader().getHeaderToken().getIdentifier();
        return IXlsTableNames.SMART_DECISION_LOOKUP.equals(dtType);
    }

    public static boolean isSimpleLookupTable(TableSyntaxNode tableSyntaxNode) {
        String dtType = tableSyntaxNode.getHeader().getHeaderToken().getIdentifier();
        return IXlsTableNames.SIMPLE_DECISION_LOOKUP.equals(dtType);
    }

    public static boolean isRulesTable(TableSyntaxNode tableSyntaxNode) {
        String dtType = tableSyntaxNode.getHeader().getHeaderToken().getIdentifier();
        return IXlsTableNames.DECISION_TABLE.equals(dtType) || IXlsTableNames.DECISION_TABLE2.equals(dtType);
    }

    public static boolean isDecisionTable(TableSyntaxNode tableSyntaxNode) {
        return isRulesTable(tableSyntaxNode) || isSmartDecisionTable(tableSyntaxNode) || isSimpleDecisionTable(
            tableSyntaxNode) || isLookup(
                tableSyntaxNode) || isSmartLookupTable(tableSyntaxNode) || isSimpleLookupTable(tableSyntaxNode);
    }

    static int countHConditionsByHeaders(ILogicalTable table) {
        int width = table.getWidth();
        int cnt = 0;

        for (int i = 0; i < width; i++) {
            String value = table.getColumn(i).getSource().getCell(0, 0).getStringValue();
            if (value != null) {
                value = value.toUpperCase();
                if (isValidHConditionHeader(value)) {
                    ++cnt;
                }
            }
        }
        return cnt;
    }

    static int countVConditionsByHeaders(ILogicalTable table) {
        int width = table.getWidth();
        int cnt = 0;
        for (int i = 0; i < width; i++) {
            String value = table.getColumn(i).getSource().getCell(0, 0).getStringValue();
            if (value != null) {
                value = value.toUpperCase();
                if (isValidConditionHeader(value) || isValidMergedConditionHeader(value)) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    static Pair<Integer, Integer> countAllHeaderTypes(ILogicalTable table) {
        int width = table.getWidth();
        int cnt = 0;
        int nonHeaderCnt = 0;
        for (int i = 0; i < width; i++) {
            String value = table.getColumn(i).getSource().getCell(0, 0).getStringValue();
            if (value != null && !StringUtils.isEmpty(value)) {
                value = value.toUpperCase();
                if (isConditionHeader(value) || isValidRetHeader(value) || isValidCRetHeader(
                    value) || isValidActionHeader(value) || isValidKeyHeader(value) || isValidRuleHeader(value)) {
                    cnt++;
                } else {
                    nonHeaderCnt++;
                }
            }
        }
        return Pair.of(cnt, nonHeaderCnt);
    }

    private static final class ParameterTokens {
        final Token[] tokens;
        final Map<Token, Integer> tokensToParameterIndex;
        final Map<Token, IOpenField[]> tokenToFieldsChain;

        ParameterTokens(Token[] tokens,
                Map<Token, Integer> tokensToParameterIndex,
                Map<Token, IOpenField[]> tokenToFieldsChain) {
            this.tokens = tokens;
            this.tokensToParameterIndex = tokensToParameterIndex;
            this.tokenToFieldsChain = tokenToFieldsChain;
        }

        IOpenField[] getFieldsChain(Token value) {
            return tokenToFieldsChain.get(value);
        }

        Integer getParameterIndex(Token value) {
            return tokensToParameterIndex.get(value);
        }

        public Token[] getTokens() {
            return tokens;
        }
    }

    public static class NumberOfColumnsUnderTitleCounter {
        final ILogicalTable logicalTable;
        final int firstColumnHeight;
        final Map<Integer, List<Integer>> numberOfColumnsMap = new HashMap<>();

        private List<Integer> init(int column) {
            int w = logicalTable.getSource().getCell(column, 0).getWidth();
            int i = 0;
            List<Integer> w1 = new ArrayList<>();
            while (i < w) {
                int w0 = logicalTable.getSource().getCell(column + i, firstColumnHeight).getWidth();
                i = i + w0;
                w1.add(w0);
            }
            return w1;
        }

        public int get(int column) {
            List<Integer> numberOfColumns = numberOfColumnsMap.computeIfAbsent(column, e -> init(column));
            return numberOfColumns.size();
        }

        public int getWidth(int column, int num) {
            List<Integer> numberOfColumns = numberOfColumnsMap.computeIfAbsent(column, e -> init(column));
            return numberOfColumns.get(num);
        }

        public NumberOfColumnsUnderTitleCounter(ILogicalTable logicalTable, int firstColumnHeight) {
            this.logicalTable = logicalTable;
            this.firstColumnHeight = firstColumnHeight;
        }
    }

    private static class FuzzyContext {
        final ParameterTokens parameterTokens;
        Token[] returnTokens = null;
        Map<Token, IOpenField[][]> returnTypeFuzzyTokens = null;
        IOpenClass fuzzyReturnType;
        int maxDistance;

        private FuzzyContext(ParameterTokens parameterTokens) {
            this.parameterTokens = parameterTokens;
        }

        private FuzzyContext(ParameterTokens parameterTokens,
                Token[] returnTokens,
                Map<Token, IOpenField[][]> returnTypeFuzzyTokens,
                IOpenClass returnType) {
            this(parameterTokens);
            this.returnTokens = returnTokens;
            this.returnTypeFuzzyTokens = returnTypeFuzzyTokens;
            this.fuzzyReturnType = returnType;
            this.maxDistance = Arrays.stream(parameterTokens.getTokens()).mapToInt(Token::getDistance).max().orElse(0);
        }

        ParameterTokens getParameterTokens() {
            return parameterTokens;
        }

        Token[] getFuzzyReturnTokens() {
            return returnTokens;
        }

        IOpenField[][] getFieldsChainsForReturnToken(Token token) {
            return returnTypeFuzzyTokens.get(token);
        }

        boolean isFuzzySupportsForReturnType() {
            return returnTypeFuzzyTokens != null && returnTokens != null && fuzzyReturnType != null;
        }

        IOpenClass getFuzzyReturnType() {
            return fuzzyReturnType;
        }

        public int getMaxDistance() {
            return maxDistance;
        }
    }
}
