package org.openrefine.wikidata.qa.scrutinizers;

import org.openrefine.wikidata.qa.QAWarning;
import org.openrefine.wikidata.updates.ItemUpdate;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UseAsQualifierScrutinizer extends EditScrutinizer {

    public static final String type = "values-should-not-be-used-as-qualifier";
    public String oneOfQualifierValuePropertyQid;
    public String property;
    public String itemOfPropertyConstraintPid;

    class UseAsQualifierConstraint {
        final PropertyIdValue allowedQualifierPid;
        final List<Value> itemList;
        UseAsQualifierConstraint(Statement statement) {
            List<SnakGroup> specs = statement.getClaim().getQualifiers();
            PropertyIdValue pid = null;
            this.itemList = new ArrayList<>();
            for(SnakGroup group : specs) {
                for (Snak snak : group.getSnaks()) {
                    if (group.getProperty().getId().equals(property)){
                        pid = (PropertyIdValue) snak.getValue();
                    }
                    if (group.getProperty().getId().equals(itemOfPropertyConstraintPid)){
                        this.itemList.add(snak.getValue());
                    }
                }
            }
            this.allowedQualifierPid = pid;
        }
    }

    @Override
    public boolean prepareDependencies() {
        oneOfQualifierValuePropertyQid = getConstraintsRelatedId("one_of_qualifier_value_property_constraint_qid");
        property = getConstraintsRelatedId("property_pid");
        itemOfPropertyConstraintPid = getConstraintsRelatedId("item_of_property_constraint_pid");
        return oneOfQualifierValuePropertyQid != null && property != null && itemOfPropertyConstraintPid != null;
    }

    @Override
    public void scrutinize(ItemUpdate update) {
        for (Statement statement : update.getAddedStatements()) {
            PropertyIdValue pid = statement.getClaim().getMainSnak().getPropertyId();
            Map<PropertyIdValue, List<Value>> qualifiersMap = new HashMap<>();
            List<SnakGroup> qualifiersList = statement.getClaim().getQualifiers();

            for(SnakGroup qualifier : qualifiersList) {
                PropertyIdValue qualifierPid = Datamodel.makeWikidataPropertyIdValue(qualifier.getProperty().getId());
                List<Value> itemList;
                for (Snak snak : qualifier.getSnaks()) {
                    if (qualifiersMap.containsKey(qualifierPid)){
                        itemList = qualifiersMap.get(qualifierPid);
                    }else {
                        itemList = new ArrayList<>();
                    }
                    itemList.add(snak.getValue());
                    qualifiersMap.put(qualifierPid, itemList);
                }
            }

            List<Statement> constraintDefinitions = _fetcher.getConstraintsByType(pid, oneOfQualifierValuePropertyQid);
            for (Statement constraintStatement : constraintDefinitions) {
                UseAsQualifierConstraint constraint = new UseAsQualifierConstraint(constraintStatement);
                if (qualifiersMap.containsKey(constraint.allowedQualifierPid)) {
                    for (Value value : qualifiersMap.get(constraint.allowedQualifierPid)) {
                        if (!constraint.itemList.contains(value)) {
                            QAWarning issue = new QAWarning(type, pid.getId()+constraint.allowedQualifierPid.getId(), QAWarning.Severity.WARNING, 1);
                            issue.setProperty("statement_entity", pid);
                            issue.setProperty("qualifier_entity", constraint.allowedQualifierPid);
                            issue.setProperty("example_entity", update.getItemId());
                            addIssue(issue);
                        }
                    }
                }
            }

        }
    }

}
