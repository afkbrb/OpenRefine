package org.openrefine.wikidata.manifests.constraints;

public class NoneOfConstraint implements Constraint {

    private String qid;
    private String itemOfPropertyConstraint;

    public String getQid() {
        return qid;
    }

    public String getItemOfPropertyConstraint() {
        return itemOfPropertyConstraint;
    }
}
