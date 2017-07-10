package jenkins.plugins.svn_revert;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import com.google.common.collect.Lists;

class ChangedRevisions {

    private final AbstractBuild<?, ?> build;

    ChangedRevisions(final AbstractBuild<?, ?> build) {
        this.build = build;
    }

    Revisions getRevisions() {
        final ChangeLogSet<? extends Entry> cs = build.getChangeSet();
        final List<Integer> revisions = Lists.newArrayList();
        for (final Entry entry : cs) {
            revisions.add(Integer.parseInt(entry.getCommitId(), 10));
        }
        return Revisions.create(revisions);
    }
    
    String getCommitMessages() {
        final ChangeLogSet<? extends Entry> cs = build.getChangeSet();
        final List<String> commitMessages = Lists.newArrayList();
        for (final Entry entry : cs) {
            commitMessages.add(entry.getMsg());
        }
        return StringUtils.join(commitMessages," ");        
    }
    
    
}