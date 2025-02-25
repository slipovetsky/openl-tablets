package org.openl.rules.ruleservice.core.interceptors.converters;

import java.lang.reflect.Method;
import java.util.Map;

import org.openl.rules.calc.SpreadsheetResult;
import org.openl.rules.ruleservice.core.interceptors.RulesDeployAware;
import org.openl.rules.ruleservice.core.interceptors.ServiceClassLoaderAware;
import org.openl.rules.variation.VariationsResult;

public class VariationResultSPRToPlainConverterAdvice extends AbstractSPRToPlainConverterAdvice<VariationsResult<Object>> implements RulesDeployAware, ServiceClassLoaderAware {

    @Override
    @SuppressWarnings("unchecked")
    public VariationsResult<Object> afterReturning(Method interfaceMethod,
            Object result,
            Object... args) throws Exception {
        VariationsResult<SpreadsheetResult> variationsResult = (VariationsResult<SpreadsheetResult>) result;
        VariationsResult<Object> ret = new VariationsResult<>();

        for (Map.Entry<String, SpreadsheetResult> entry : variationsResult.getVariationResults().entrySet()) {
            ret.registerResult(entry.getKey(),
                SpreadsheetResult.convertSpreadsheetResult(entry.getValue(),
                    getConvertToType().getLeft(),
                    getConvertToType().getRight(),
                    getSpreadsheetResultBeanPropertyNamingStrategy()));
        }

        for (Map.Entry<String, String> entry : variationsResult.getVariationFailures().entrySet()) {
            ret.registerFailure(entry.getKey(), entry.getValue());
        }

        return ret;
    }
}