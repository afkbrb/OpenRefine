package org.openrefine.wikidata.qa.scrutinizers;

import org.openrefine.wikidata.qa.QAWarning;
import org.openrefine.wikidata.updates.ItemUpdate;
import org.wikidata.wdtk.datamodel.interfaces.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DifferenceWithinRangeScrutinizer extends EditScrutinizer {

    public static final String type = "difference-of-the-properties-is-not-within-the-specified-range";
    public String differenceWithinRangeConstraintQid;
    public String differenceWithinRangeConstraintPid;
    public String minimumValuePid;
    public String maximumValuePid;

    class DifferenceWithinRangeConstraint {
        PropertyIdValue lowerPropertyIdValue;
        QuantityValue minRangeValue, maxRangeValue;

        DifferenceWithinRangeConstraint(Statement statement) {
            List<SnakGroup> specs = statement.getClaim().getQualifiers();
            if (specs != null) {
                List<Value> lowerValueProperty = findValues(specs, differenceWithinRangeConstraintPid);
                List<Value> minValue = findValues(specs, minimumValuePid);
                List<Value> maxValue = findValues(specs, maximumValuePid);
                if (!lowerValueProperty.isEmpty()) {
                    lowerPropertyIdValue = (PropertyIdValue) lowerValueProperty.get(0);
                }
                if (!minValue.isEmpty()) {
                    minRangeValue = (QuantityValue) minValue.get(0);
                }
                if (!maxValue.isEmpty()) {
                    maxRangeValue = (QuantityValue) maxValue.get(0);
                }
            }
        }
    }

    @Override
    public boolean prepareDependencies() {
        if (constraints.getDifferenceWithinRangeConstraint() == null) {
            return false;
        }
        differenceWithinRangeConstraintQid = constraints.getDifferenceWithinRangeConstraint().getQid();
        differenceWithinRangeConstraintPid = constraints.getDifferenceWithinRangeConstraint().getProperty();
        minimumValuePid = constraints.getDifferenceWithinRangeConstraint().getMinimumValue();
        maximumValuePid = constraints.getDifferenceWithinRangeConstraint().getMaximumValue();
        return differenceWithinRangeConstraintQid != null && differenceWithinRangeConstraintPid != null
                && minimumValuePid != null && maximumValuePid != null;
    }

    @Override
    public void scrutinize(ItemUpdate update) {
        Map<PropertyIdValue, Value> propertyIdValueValueMap = new HashMap<>();
        for (Statement statement : update.getAddedStatements()){
            PropertyIdValue pid = statement.getClaim().getMainSnak().getPropertyId();
            Value value = statement.getClaim().getMainSnak().getValue();
            propertyIdValueValueMap.put(pid, value);
        }

        for(PropertyIdValue propertyId : propertyIdValueValueMap.keySet()){
            List<Statement> statementList = _fetcher.getConstraintsByType(propertyId, differenceWithinRangeConstraintQid);
            if (!statementList.isEmpty()){
                DifferenceWithinRangeConstraint constraint = new DifferenceWithinRangeConstraint(statementList.get(0));
                PropertyIdValue lowerPropertyId = constraint.lowerPropertyIdValue;
                QuantityValue minRangeValue = constraint.minRangeValue;
                QuantityValue maxRangeValue = constraint.maxRangeValue;

                if (propertyIdValueValueMap.containsKey(lowerPropertyId)){
                    Value startingValue = propertyIdValueValueMap.get(lowerPropertyId);
                    Value endingValue = propertyIdValueValueMap.get(propertyId);
                    if (startingValue instanceof TimeValue && endingValue instanceof TimeValue){
                        TimeValue lowerDate = (TimeValue)startingValue;
                        TimeValue upperDate = (TimeValue)endingValue;

                        long differenceInYears = upperDate.getYear() - lowerDate.getYear();
                        long differenceInMonths = upperDate.getMonth() - lowerDate.getMonth();
                        long differenceInDays = upperDate.getMonth() - lowerDate.getMonth();

                        if (minRangeValue != null && (differenceInYears < minRangeValue.getNumericValue().longValue()
                                || differenceInYears == 0 && differenceInMonths < 0
                                || differenceInYears == 0 && differenceInMonths == 0 && differenceInDays < 0)){
                            QAWarning issue = new QAWarning(type, propertyId.getId(), QAWarning.Severity.WARNING, 1);
                            issue.setProperty("source_entity", lowerPropertyId);
                            issue.setProperty("target_entity", propertyId);
                            issue.setProperty("min_value", minRangeValue.getNumericValue());
                            if (maxRangeValue != null) {
                                issue.setProperty("max_value", maxRangeValue.getNumericValue());
                            } else {
                                issue.setProperty("max_value", null);
                            }
                            issue.setProperty("example_entity", update.getItemId());
                            addIssue(issue);
                        }

                        if (maxRangeValue != null && differenceInYears > maxRangeValue.getNumericValue().longValue()){
                            QAWarning issue = new QAWarning(type, propertyId.getId(), QAWarning.Severity.WARNING, 1);
                            issue.setProperty("source_entity", lowerPropertyId);
                            issue.setProperty("target_entity", propertyId);
                            if (minRangeValue != null) {
                                issue.setProperty("min_value", minRangeValue.getNumericValue());
                            } else {
                                issue.setProperty("min_value", null);
                            }
                            issue.setProperty("max_value", maxRangeValue.getNumericValue());
                            issue.setProperty("example_entity", update.getItemId());
                            addIssue(issue);
                        }
                    }
                }

            }
        }

    }
}
