package jenkins.plugins.svn_revert;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

public class CommitCountRule {

    private final AbstractBuild<?, ?> build;
    private final boolean shouldRevertMultiple;

    public CommitCountRule(final AbstractBuild<?, ?> build, final boolean shouldRevertMultiple) {
        this.build = build;
        this.shouldRevertMultiple = shouldRevertMultiple;
    }

    public boolean noChangesInBuild() {
        return build.getChangeSet().isEmptySet();
    }

    public boolean tooManyChangesInBuild() {
        if (shouldRevertMultiple) {
            return false;
        }
        final ChangeLogSet<? extends Entry> changeSet = build.getChangeSet();
        return changeSet.getItems().length > 1;
    }
    
    public boolean singleCommitOnly() {
        final ChangeLogSet<? extends Entry> changeSet = build.getChangeSet();
        return changeSet.getItems().length == 1;        
    }

}
