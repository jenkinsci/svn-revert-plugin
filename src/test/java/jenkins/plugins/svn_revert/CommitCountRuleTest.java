package jenkins.plugins.svn_revert;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM.EntryImpl;
import org.jvnet.hudson.test.FakeChangeLogSCM.FakeChangeLogSet;
import org.mockito.Mock;

import com.google.common.collect.Lists;

@SuppressWarnings("rawtypes")
public class CommitCountRuleTest extends AbstractMockitoTestCase {

    private CommitCountRule commitCountRule;

    @Mock
    private AbstractBuild build;

    private final ChangeLogSet emptyChangeSet = ChangeLogSet.createEmpty(build);
    private EntryImpl change;
    private LinkedList<EntryImpl> changeList;


    @Before
    public void setUp() throws Exception {
        changeList = Lists.newLinkedList();
        changeList.add(change);
        FakeChangeLogSet changeLogSet = new FakeChangeLogSet(null, changeList);
        when(build.getChangeSet()).thenReturn(changeLogSet);
        //when(build.getChangeSet()).thenReturn(new FakeChangeLogSet(build, changeList));
        givenWillNotRevertMultipleCommits();
    }

    @Test
    public void hasNoChangesWhenChangeSetEmpty() throws Exception {
        when(build.getChangeSet()).thenReturn(emptyChangeSet);
        assertThat(commitCountRule.noChangesInBuild(), is(true));
    }

    @Test
    public void hasChangesWhenChangeSetEmpty() throws Exception {
        assertThat(commitCountRule.noChangesInBuild(), is(false));
    }

    @Test
    public void changesNotTooManyWhenShouldRevertMultiple() throws Exception {
        givenWillRevertMultipleCommits();
        givenMultipleCommitsInChanges();

        assertThat(commitCountRule.tooManyChangesInBuild(), is(false));
    }

    @Test
    public void changesNotTooManyWhenOnlyOne() throws Exception {
        givenWillNotRevertMultipleCommits();

        assertThat(commitCountRule.tooManyChangesInBuild(), is(false));
    }

    @Test
    public void changesTooManyWhenShouldNotRevertMultiple() throws Exception {
        givenWillNotRevertMultipleCommits();
        givenMultipleCommitsInChanges();

        assertThat(commitCountRule.tooManyChangesInBuild(), is(true));
    }

    private void givenWillNotRevertMultipleCommits() {
        commitCountRule = new CommitCountRule(build, false);
    }

    private void givenWillRevertMultipleCommits() {
        commitCountRule = new CommitCountRule(build, true);
    }

    private void givenMultipleCommitsInChanges() {
        changeList.add(change);
    }
}
