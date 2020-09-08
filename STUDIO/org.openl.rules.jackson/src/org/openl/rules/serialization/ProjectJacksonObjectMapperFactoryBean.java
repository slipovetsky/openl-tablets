package org.openl.rules.serialization;

/*-
 * #%L
 * OpenL - STUDIO - Jackson
 * %%
 * Copyright (C) 2016 - 2020 OpenL Tablets
 * %%
 * See the file LICENSE.txt for copying permission.
 * #L%
 */

import java.text.DateFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.openl.rules.calc.CustomSpreadsheetResultOpenClass;
import org.openl.rules.lang.xls.binding.XlsModuleOpenClass;
import org.openl.rules.lang.xls.types.DatatypeOpenClass;
import org.openl.rules.project.model.RulesDeploy;
import org.openl.rules.project.model.RulesDeployHelper;
import org.openl.rules.serialization.jackson.NonNullMixIn;
import org.openl.types.IOpenClass;
import org.openl.util.ClassUtils;
import org.openl.util.StringUtils;
import org.openl.util.generation.InterfaceTransformer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ProjectJacksonObjectMapperFactoryBean implements JacksonObjectMapperFactory {

    private static final AtomicLong incrementer = new AtomicLong();

    public static final String ROOT_CLASS_NAMES_BINDING = "rootClassNamesBinding";
    public static final String JACKSON_CASE_INSENSITIVE_PROPERTIES = "jackson.caseInsensitiveProperties";
    public static final String JACKSON_DEFAULT_DATE_FORMAT = "jackson.defaultDateFormat";
    public static final String JACKSON_DEFAULT_TYPING_MODE = "jackson.defaultTypingMode";
    public static final String JACKSON_SERIALIZATION_INCLUSION = "jackson.serializationInclusion";
    public static final String JACKSON_FAIL_ON_UNKNOWN_PROPERTIES = "jackson.failOnUnknownProperties";
    public static final String JACKSON_SPREADSHEETRESULT_FIELD_NAME_RESOLVER = "jackson.spreadsheetResultFieldNameResolver";

    private final JacksonObjectMapperFactoryBean delegate = new JacksonObjectMapperFactoryBean();

    private XlsModuleOpenClass xlsModuleOpenClass;

    private RulesDeploy rulesDeploy;

    public RulesDeploy getRulesDeploy() {
        return rulesDeploy;
    }

    public void setRulesDeploy(RulesDeploy rulesDeploy) {
        this.rulesDeploy = rulesDeploy;
    }

    public XlsModuleOpenClass getXlsModuleOpenClass() {
        return xlsModuleOpenClass;
    }

    public void setXlsModuleOpenClass(XlsModuleOpenClass xlsModuleOpenClass) {
        this.xlsModuleOpenClass = xlsModuleOpenClass;
    }

    private DefaultTypingMode toDefaultTypingMode(String defaultTypingMode) {
        if (DefaultTypingMode.DISABLED.name().equalsIgnoreCase(defaultTypingMode.trim())) {
            return DefaultTypingMode.DISABLED;
        } else if (DefaultTypingMode.OBJECT_AND_NON_CONCRETE.name().equalsIgnoreCase(defaultTypingMode.trim())) {
            return DefaultTypingMode.OBJECT_AND_NON_CONCRETE;
        } else if (DefaultTypingMode.EVERYTHING.name().equalsIgnoreCase(defaultTypingMode.trim())) {
            return DefaultTypingMode.EVERYTHING;
        } else if (DefaultTypingMode.NON_CONCRETE_AND_ARRAYS.name().equalsIgnoreCase(defaultTypingMode.trim())) {
            return DefaultTypingMode.NON_CONCRETE_AND_ARRAYS;
        } else if (DefaultTypingMode.JAVA_LANG_OBJECT.name().equalsIgnoreCase(defaultTypingMode.trim())) {
            return DefaultTypingMode.JAVA_LANG_OBJECT;
        } else if (DefaultTypingMode.NON_FINAL.name().equalsIgnoreCase(defaultTypingMode.trim())) {
            return DefaultTypingMode.NON_FINAL;
        }
        throw new ObjectMapperConfigurationParsingException(String.format(
            "Expected JAVA_LANG_OBJECT/OBJECT_AND_NON_CONCRETE/NON_CONCRETE_AND_ARRAYS/NON_FINAL/EVERYTHING/DISABLED value for '%s' in the configuration for service '%s'.",
            JACKSON_DEFAULT_TYPING_MODE,
            getRulesDeploy().getServiceName()));
    }

    protected void applyProjectConfiguration() {
        if (rulesDeploy != null) {
            if (Boolean.TRUE.equals(rulesDeploy.isProvideVariations())) {
                delegate.setSupportVariations(true);
            }
            Map<String, Object> configuration = rulesDeploy.getConfiguration();
            if (configuration != null) {
                processCaseInsensitivePropertiesSetting(configuration.get(JACKSON_CASE_INSENSITIVE_PROPERTIES));
                processFailOnUnknownPropertiesSetting(configuration.get(JACKSON_FAIL_ON_UNKNOWN_PROPERTIES));
                processJacksonDefaultDateFormatSetting(configuration.get(JACKSON_DEFAULT_DATE_FORMAT));
                processJacksonDefaultTypingModeSetting(configuration.get(JACKSON_DEFAULT_TYPING_MODE));
                processJacksonSerializationInclusionSetting(configuration.get(JACKSON_SERIALIZATION_INCLUSION));
            }
        }
        processRootClassNamesBindingSetting(
            rulesDeploy != null && rulesDeploy.getConfiguration() != null
                                                                          ? rulesDeploy.getConfiguration()
                                                                              .get(ROOT_CLASS_NAMES_BINDING)
                                                                          : null);
        processXlsModuleOpenClassRelatedSettings();
    }

    private void processXlsModuleOpenClassRelatedSettings() {
        if (xlsModuleOpenClass != null) {
            Set<Class<?>> rootClassNamesBindingClasses = delegate.getOverrideClasses();
            if (rootClassNamesBindingClasses == null) {
                rootClassNamesBindingClasses = new HashSet<>();
            } else {
                rootClassNamesBindingClasses = new HashSet<>(rootClassNamesBindingClasses);
            }
            for (IOpenClass type : xlsModuleOpenClass.getTypes()) {
                if (type instanceof DatatypeOpenClass) {
                    rootClassNamesBindingClasses.add(type.getInstanceClass());
                }
                if (type instanceof CustomSpreadsheetResultOpenClass) {
                    rootClassNamesBindingClasses.add(((CustomSpreadsheetResultOpenClass) type).getBeanClass());
                }
            }
            if (xlsModuleOpenClass.getSpreadsheetResultOpenClassWithResolvedFieldTypes() != null) {
                rootClassNamesBindingClasses
                    .add(xlsModuleOpenClass.getSpreadsheetResultOpenClassWithResolvedFieldTypes()
                        .toCustomSpreadsheetResultOpenClass()
                        .getBeanClass());
            }
            delegate.setOverrideClasses(rootClassNamesBindingClasses);
        }
    }

    protected void processFailOnUnknownPropertiesSetting(Object failOnUnknownProperties) {
        if (failOnUnknownProperties != null) {
            if (failOnUnknownProperties instanceof Boolean) {
                delegate.setFailOnUnknownProperties((Boolean) failOnUnknownProperties);
            } else if (failOnUnknownProperties instanceof String) {
                delegate.setFailOnUnknownProperties(Boolean.parseBoolean((String) failOnUnknownProperties));
            } else {
                throw new ObjectMapperConfigurationParsingException(
                    String.format("Expected true/false value for '%s' in the configuration for service '%s'.",
                        JACKSON_FAIL_ON_UNKNOWN_PROPERTIES,
                        rulesDeploy.getServiceName()));
            }
        }
    }

    protected void processJacksonSerializationInclusionSetting(Object serializationInclusion) {
        if (serializationInclusion != null) {
            if (serializationInclusion instanceof String) {
                String stringValue = (String) serializationInclusion;
                try {
                    delegate.setSerializationInclusion(JsonInclude.Include.valueOf(stringValue));
                } catch (IllegalArgumentException e) {
                    throw new ObjectMapperConfigurationParsingException(String.format(
                        "Invalid serializationInclusion value is used for '%s' in the configuration for service '%s'.",
                        JACKSON_SERIALIZATION_INCLUSION,
                        rulesDeploy.getServiceName()), e);
                }
            } else {
                throw new ObjectMapperConfigurationParsingException(
                    String.format("Expected string value for '%s' in the configuration for service '%s'.",
                        JACKSON_SERIALIZATION_INCLUSION,
                        rulesDeploy.getServiceName()));
            }
        }
    }

    protected void processRootClassNamesBindingSetting(Object rootClassNamesBinding) {
        if (rootClassNamesBinding instanceof String) {
            Set<String> rootClassNamesBindingClassNames = delegate.getOverrideTypes();
            if (rootClassNamesBindingClassNames == null) {
                rootClassNamesBindingClassNames = new HashSet<>();
            } else {
                rootClassNamesBindingClassNames = new HashSet<>(rootClassNamesBindingClassNames);
            }
            rootClassNamesBindingClassNames
                .addAll(RulesDeployHelper.splitRootClassNamesBindingClasses((String) rootClassNamesBinding));
            delegate.setOverrideTypes(rootClassNamesBindingClassNames);
        } else {
            if (rootClassNamesBinding != null) {
                throw new ObjectMapperConfigurationParsingException(
                    String.format("Expected string value for '%s' in the configuration for service '%s'.",
                        ROOT_CLASS_NAMES_BINDING,
                        rulesDeploy.getServiceName()));
            }
        }
    }

    protected void processCaseInsensitivePropertiesSetting(Object caseInsensitive) {
        if (caseInsensitive != null) {
            if (caseInsensitive instanceof Boolean) {
                delegate.setCaseInsensitiveProperties((Boolean) caseInsensitive);
            } else if (caseInsensitive instanceof String) {
                delegate.setCaseInsensitiveProperties(Boolean.parseBoolean((String) caseInsensitive));
            } else {
                throw new ObjectMapperConfigurationParsingException(
                    String.format("Expected true/false value for '%s' in the configuration for service '%s'.",
                        JACKSON_CASE_INSENSITIVE_PROPERTIES,
                        rulesDeploy.getServiceName()));
            }
        }
    }

    protected void processJacksonDefaultTypingModeSetting(Object defaultTypingMode) {
        if (defaultTypingMode != null) {
            if (defaultTypingMode instanceof DefaultTypingMode) {
                delegate.setDefaultTypingMode((DefaultTypingMode) defaultTypingMode);
            } else if (defaultTypingMode instanceof String) {
                DefaultTypingMode dtm = toDefaultTypingMode((String) defaultTypingMode);
                if (dtm != null) {
                    delegate.setDefaultTypingMode(dtm);
                }
            } else {
                throw new ObjectMapperConfigurationParsingException(
                    String.format("Expected string value for '%s' in the configuration for service '%s'.",
                        JACKSON_DEFAULT_TYPING_MODE,
                        rulesDeploy.getServiceName()));
            }
        }
    }

    protected void processJacksonDefaultDateFormatSetting(Object defaultDateFormat) {
        if (defaultDateFormat != null) {
            if (defaultDateFormat instanceof String) {
                String defaultDateFormatString = (String) defaultDateFormat;
                if (StringUtils.isNotBlank(defaultDateFormatString)) {
                    try {
                        delegate.setDefaultDateFormat(new ExtendedStdDateFormat(defaultDateFormatString));
                    } catch (Exception e) {
                        throw new ObjectMapperConfigurationParsingException(
                            String.format("Invalid date format is used in the configuration for service '%s'.",
                                rulesDeploy.getServiceName()),
                            e);
                    }
                }
            } else {
                throw new ObjectMapperConfigurationParsingException(
                    String.format("Expected string value for '%s' in the configuration for service '%s'.",
                        JACKSON_DEFAULT_DATE_FORMAT,
                        rulesDeploy.getServiceName()));
            }
        }
    }

    public final ObjectMapper createJacksonObjectMapper() throws ClassNotFoundException {
        applyBeforeProjectConfiguration();
        applyProjectConfiguration();
        applyAfterProjectConfiguration();
        return enhanceObjectMapper(delegate.createJacksonObjectMapper());
    }

    protected ObjectMapper enhanceObjectMapper(ObjectMapper objectMapper) {
        if (xlsModuleOpenClass != null) {
            SpreadsheetResultFieldNameResolver spreadsheetResultFieldNameResolver = extractSpreadsheetResultFieldNameResolver();
            for (IOpenClass type : xlsModuleOpenClass.getTypes()) {
                if (type instanceof CustomSpreadsheetResultOpenClass) {
                    CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass = ((CustomSpreadsheetResultOpenClass) type);
                    addMixInAnnotationsToSprBeanClass(objectMapper,
                        customSpreadsheetResultOpenClass,
                        spreadsheetResultFieldNameResolver);
                }
            }
            if (xlsModuleOpenClass.getSpreadsheetResultOpenClassWithResolvedFieldTypes() != null) {
                CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass = xlsModuleOpenClass
                    .getSpreadsheetResultOpenClassWithResolvedFieldTypes()
                    .toCustomSpreadsheetResultOpenClass();
                addMixInAnnotationsToSprBeanClass(objectMapper,
                    customSpreadsheetResultOpenClass,
                    spreadsheetResultFieldNameResolver);
            }
        }
        return objectMapper;
    }

    private SpreadsheetResultFieldNameResolver extractSpreadsheetResultFieldNameResolver() {
        if (rulesDeploy != null) {
            if (rulesDeploy.getConfiguration() != null) {
                Object spreadsheetResultFieldNameResolver = rulesDeploy.getConfiguration()
                    .get(JACKSON_SPREADSHEETRESULT_FIELD_NAME_RESOLVER);
                if (spreadsheetResultFieldNameResolver != null) {
                    if (spreadsheetResultFieldNameResolver instanceof String) {
                        String spreadsheetResultFieldNameResolverClassName = (String) spreadsheetResultFieldNameResolver;
                        try {
                            Class<?> spreadsheetResultFieldNameResolverClass = getClassLoader()
                                .loadClass(spreadsheetResultFieldNameResolverClassName);
                            if (!SpreadsheetResultFieldNameResolver.class
                                .isAssignableFrom(spreadsheetResultFieldNameResolverClass)) {
                                throw new ObjectMapperConfigurationParsingException(String.format(
                                    "Failed to load spreadsheet result field name resolver class '%s' for service '%s'. The class must be an implementation of interface '%s'.",
                                    JACKSON_SPREADSHEETRESULT_FIELD_NAME_RESOLVER,
                                    rulesDeploy.getServiceName(),
                                    SpreadsheetResultFieldNameResolver.class.getTypeName()));
                            }
                            try {
                                return (SpreadsheetResultFieldNameResolver) spreadsheetResultFieldNameResolverClass
                                    .newInstance();
                            } catch (InstantiationException | IllegalAccessException e) {
                                throw new ObjectMapperConfigurationParsingException(String.format(
                                    "Failed to instantiate spreadsheet result field name resolver class '%s' for service '%s'.",
                                    JACKSON_SPREADSHEETRESULT_FIELD_NAME_RESOLVER,
                                    rulesDeploy.getServiceName()), e);
                            }
                        } catch (ClassNotFoundException e) {
                            throw new ObjectMapperConfigurationParsingException(String.format(
                                "Failed to load spreadsheet result field name resolver class '%s' for service '%s'.",
                                JACKSON_SPREADSHEETRESULT_FIELD_NAME_RESOLVER,
                                rulesDeploy.getServiceName()), e);
                        }
                    } else {
                        throw new ObjectMapperConfigurationParsingException(
                            String.format("Expected string value for '%s' in the configuration for service '%s'.",
                                JACKSON_SPREADSHEETRESULT_FIELD_NAME_RESOLVER,
                                rulesDeploy.getServiceName()));
                    }
                }
            }
        }
        return null;
    }

    private void addMixInAnnotationsToSprBeanClass(ObjectMapper objectMapper,
            CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass,
            SpreadsheetResultFieldNameResolver spreadsheetResultFieldNameResolver) {
        Class<?> sprBeanClass = customSpreadsheetResultOpenClass.getBeanClass();
        Class<?> originalMixInClass = objectMapper.findMixInClassFor(sprBeanClass);
        Class<?> mixInClass = enhanceMixInClassForSprBeanClass(
            originalMixInClass != null ? originalMixInClass : NonNullMixIn.class,
            customSpreadsheetResultOpenClass,
            spreadsheetResultFieldNameResolver,
            getClassLoader());
        objectMapper.addMixIn(sprBeanClass, mixInClass);
    }

    private Class<?> enhanceMixInClassForSprBeanClass(Class<?> originalMixInClass,
            CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass,
            SpreadsheetResultFieldNameResolver spreadsheetResultFieldNameResolver,
            ClassLoader classLoader) {
        String className = originalMixInClass.getName() + "$Enhanced$" + incrementer.getAndIncrement();
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            ClassWriter classWriter = new ClassWriter(0);
            ClassVisitor classVisitor = new SpreadsheetResultBeanClassMixInAnnotationsWriter(classWriter,
                className,
                originalMixInClass,
                customSpreadsheetResultOpenClass,
                spreadsheetResultFieldNameResolver);
            InterfaceTransformer transformer = new InterfaceTransformer(originalMixInClass, className, true);
            transformer.accept(classVisitor);
            classWriter.visitEnd();
            try {
                ClassUtils.defineClass(className, classWriter.toByteArray(), classLoader);
                return Class.forName(className, true, classLoader);
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
        }
    }

    protected void applyBeforeProjectConfiguration() {
    }

    protected void applyAfterProjectConfiguration() {
    }

    public boolean isSupportVariations() {
        return delegate.isSupportVariations();
    }

    public void setSupportVariations(boolean supportVariations) {
        delegate.setSupportVariations(supportVariations);
    }

    public DefaultTypingMode getDefaultTypingMode() {
        return delegate.getDefaultTypingMode();
    }

    public void setDefaultTypingMode(DefaultTypingMode defaultTypingMode) {
        delegate.setDefaultTypingMode(defaultTypingMode);
    }

    public void setFailOnUnknownProperties(boolean failOnUnknownProperties) {
        delegate.setFailOnUnknownProperties(failOnUnknownProperties);
    }

    public boolean isFailOnUnknownProperties() {
        return delegate.isFailOnUnknownProperties();
    }

    public Set<String> getOverrideTypes() {
        return delegate.getOverrideTypes();
    }

    public void setOverrideTypesAsString(String overrideTypes) {
        delegate.setOverrideTypes(RulesDeployHelper.splitRootClassNamesBindingClasses(overrideTypes));
    }

    public void setOverrideTypes(Set<String> overrideTypes) {
        delegate.setOverrideTypes(overrideTypes);
    }

    public DateFormat getDefaultDateFormat() {
        return delegate.getDefaultDateFormat();
    }

    public void setDefaultDateFormatAsString(String defaultDateFormat) {
        delegate.setDefaultDateFormat(new ExtendedStdDateFormat(defaultDateFormat));
    }

    public void setDefaultDateFormat(DateFormat defaultDateFormat) {
        delegate.setDefaultDateFormat(defaultDateFormat);
    }

    public JsonInclude.Include getSerializationInclusion() {
        return delegate.getSerializationInclusion();
    }

    public void setSerializationInclusion(JsonInclude.Include serializationInclusion) {
        delegate.setSerializationInclusion(serializationInclusion);
    }

    public boolean isPolymorphicTypeValidation() {
        return delegate.isPolymorphicTypeValidation();
    }

    public void setPolymorphicTypeValidation(boolean polymorphicTypeValidation) {
        delegate.setPolymorphicTypeValidation(polymorphicTypeValidation);
    }

    public ClassLoader getClassLoader() {
        return delegate.getClassLoader();
    }

    public void setClassLoader(ClassLoader classLoader) {
        delegate.setClassLoader(classLoader);
    }

    public Set<Class<?>> getOverrideClasses() {
        return delegate.getOverrideClasses();
    }

    public void setOverrideClasses(Set<Class<?>> overrideClasses) {
        delegate.setOverrideClasses(overrideClasses);
    }

    public boolean isCaseInsensitiveProperties() {
        return delegate.isCaseInsensitiveProperties();
    }

    public void setCaseInsensitiveProperties(boolean caseInsensitiveProperties) {
        delegate.setCaseInsensitiveProperties(caseInsensitiveProperties);
    }

    public ObjectMapperFactory getObjectMapperFactory() {
        return delegate.getObjectMapperFactory();
    }

    public void setObjectMapperFactory(ObjectMapperFactory objectMapperFactory) {
        delegate.setObjectMapperFactory(objectMapperFactory);
    }

    public boolean isGenerateSubtypeAnnotationsForDisabledMode() {
        return delegate.isGenerateSubtypeAnnotationsForDisabledMode();
    }

    public void setGenerateSubtypeAnnotationsForDisabledMode(boolean generateSubtypeAnnotationsForDisabledMode) {
        delegate.setGenerateSubtypeAnnotationsForDisabledMode(generateSubtypeAnnotationsForDisabledMode);
    }

}