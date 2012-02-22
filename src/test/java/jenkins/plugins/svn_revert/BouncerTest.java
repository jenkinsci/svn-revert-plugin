package jenkins.plugins.svn_revert;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import hudson.Launcher;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;
import hudson.scm.SubversionSCM;

import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM.EntryImpl;
import org.jvnet.hudson.test.FakeChangeLogSCM.FakeChangeLogSet;
import org.mockito.Mock;

import com.google.common.collect.Lists;

@SuppressWarnings("rawtypes")
public class BouncerTest extends AbstractMockitoTestCase {

    private static final Result NOT_SUCCESS = Result.UNSTABLE;
    private static final Result NOT_UNSTABLE = Result.SUCCESS;
    @Mock
    private AbstractBuild build;
    @Mock
    private AbstractBuild rootBuild;
    @Mock
    private Launcher launcher;
    @Mock
    private FreeStyleBuild previousBuild;
    @Mock
    private SvnReverter reverter;
    @Mock
    private Messenger messenger;
    @Mock
    private Claimer claimer;
    @Mock
    private SubversionSCM subversionScm;
    @Mock
    private AbstractProject project;
    @Mock
    private AbstractProject rootProject;
    @Mock
    private NullSCM nullScm;
    @Mock
    private RevertMailSender mailer;
    @Mock
    private ChangeLocator changeLocator;

    private final ChangeLogSet emptyChangeSet = ChangeLogSet.createEmpty(build);
    private EntryImpl entry;
    private LinkedList<EntryImpl> entryList;

    @Before
    public void setUp() throws Exception {
        when(build.getRootBuild()).thenReturn(rootBuild);
        when(build.getProject()).thenReturn(project);
        when(project.getRootProject()).thenReturn(rootProject);
        when(build.getPreviousBuild()).thenReturn(previousBuild);
        when(previousBuild.isBuilding()).thenReturn(false);
        when(rootProject.getScm()).thenReturn(subversionScm);
        when(changeLocator.changesOutsideWorkspace(subversionScm)).thenReturn(false);
        givenMayRevert();
    }

    @Test
    public void shouldRevertWhenBuildResultIsUnstableAndPreviousResultIsSuccess() throws Exception {
        throwOutIfUnstable();

        verify(reverter).revert(subversionScm);
    }

    @Test
    public void shouldNotRevertIfPreviousBuildWasNotSuccess() throws Exception {
        when(previousBuild.getResult()).thenReturn(NOT_SUCCESS);

        throwOutIfUnstable();

        verifyNotReverted();
    }

    @Test
    public void shouldNotRevertIfPreviousBuildIsBuilding() throws Exception {
        when(previousBuild.isBuilding()).thenReturn(true);

        throwOutIfUnstable();

        verifyNotReverted();
    }

    @Test
    public void shouldNotRevertWhenBuildResultIsSuccess() throws Exception {
        when(build.getResult()).thenReturn(Result.SUCCESS);

        throwOutIfUnstable();

        verifyNotReverted();
    }

    @Test
    public void shouldNotRevertWhenBuildResultIsFailure() throws Exception {
        when(build.getResult()).thenReturn(Result.FAILURE);

        throwOutIfUnstable();

        verifyNotReverted();
    }

    @Test
    public void shouldNotRevertWhenNoChanges() throws Exception {
        when(build.getChangeSet()).thenReturn(emptyChangeSet);

        throwOutIfUnstable();

        verifyNotReverted();
    }

    @Test
    public void shouldNotRevertIfChangesOutsideWorkspace() throws Exception {
        when(changeLocator.changesOutsideWorkspace(subversionScm)).thenReturn(true);

        throwOutIfUnstable();

        verifyNotReverted();
    }

    @Test
    public void shouldLogIfRepoIsNotSubversion() throws Exception {
        givenNotSubversionScm();
        throwOutIfUnstable();
        verify(messenger).informNotSubversionSCM();
    }

    @Test
    public void shouldLogWhenBuildResultIsNotUnstable() throws Exception {
        when(build.getResult()).thenReturn(NOT_UNSTABLE);

        throwOutIfUnstable();

        verify(messenger).informBuildStatusNotUnstable();
    }

    @Test
    public void shouldLogWhenPreviousBuildResultIsNotSuccess() throws Exception {
        when(previousBuild.getResult()).thenReturn(NOT_SUCCESS);

        throwOutIfUnstable();

        verify(messenger).informPreviousBuildStatusNotSuccess();
    }

    @Test
    public void shouldLogWhenNoChanges() throws Exception {
        when(build.getChangeSet()).thenReturn(emptyChangeSet);

        throwOutIfUnstable();

        verify(messenger).informNoChanges();
    }

    @Test
    public void shouldLogWhenChangesOutsideWorkspace() throws Exception {
        when(changeLocator.changesOutsideWorkspace(subversionScm)).thenReturn(true);

        throwOutIfUnstable();

        verify(messenger).informChangesOutsideWorkspace();
    }

    @Test
    public void shouldReturnTrueIfRepoIsNotSubversion() throws Exception {
        givenNotSubversionScm();
        assertThat(throwOutIfUnstable(), is(true));
    }

    @Test
    public void shouldReturnTrueWhenBuildResultIsNotUnstable() throws Exception {
        when(build.getResult()).thenReturn(NOT_UNSTABLE);

        assertThat(throwOutIfUnstable(), is(true));
    }

    @Test
    public void shouldReturnTrueWhenPreviousBuildResultIsNotSuccess() throws Exception {
        when(previousBuild.getResult()).thenReturn(NOT_SUCCESS);

        assertThat(throwOutIfUnstable(), is(true));
    }

    @Test
    public void shouldFailBuildWhenRevertFails() throws Exception {
        when(reverter.revert(subversionScm)).thenReturn(SvnRevertStatus.REVERT_FAILED);

        assertThat(throwOutIfUnstable(), is(false));
    }

    @Test
    public void shouldNotFailWhenFirstBuildIsUnstable() throws Exception {
        when(build.getPreviousBuild()).thenReturn(null);

        assertThat(throwOutIfUnstable(), is(true));
    }

    @Test
    public void shouldNotFailBuildWhenRevertSucceeds() throws Exception {
        when(reverter.revert(subversionScm)).thenReturn(SvnRevertStatus.REVERT_SUCCESSFUL);

        assertThat(throwOutIfUnstable(), is(true));
    }

    @Test
    public void shouldClaimWhenRevertSucceeds() throws Exception {
        when(reverter.revert(subversionScm)).thenReturn(SvnRevertStatus.REVERT_SUCCESSFUL);

        throwOutIfUnstable();

        verify(claimer).claim(build);
    }

    @Test
    public void shouldSendMailWhenRevertSucceeds() throws Exception {
        when(reverter.revert(subversionScm)).thenReturn(SvnRevertStatus.REVERT_SUCCESSFUL);

        throwOutIfUnstable();

        verify(mailer).sendRevertMail(build);
    }

    @Test
    public void shouldNotClaimOrMailWhenRevertFails() throws Exception {
        when(reverter.revert(subversionScm)).thenReturn(SvnRevertStatus.REVERT_FAILED);

        throwOutIfUnstable();

        verifyZeroInteractions(claimer);
        verifyZeroInteractions(mailer);
    }

    @Test
    public void shouldNotClaimOrMailWhenNothingReverted() throws Exception {
        when(reverter.revert(subversionScm)).thenReturn(SvnRevertStatus.NOTHING_REVERTED);

        throwOutIfUnstable();

        verifyZeroInteractions(claimer);
        verifyZeroInteractions(mailer);
    }

    private boolean throwOutIfUnstable() throws Exception {
        return Bouncer.throwOutIfUnstable(build, launcher, messenger, reverter, claimer, changeLocator, mailer);
    }

    private void givenNotSubversionScm() {
        when(rootProject.getScm()).thenReturn(nullScm);
    }

    private void givenMayRevert() {
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        when(previousBuild.getResult()).thenReturn(Result.SUCCESS);
        entryList = Lists.newLinkedList();
        entryList.add(entry);
        when(build.getChangeSet()).thenReturn(new FakeChangeLogSet(build, entryList));
    }

    private void verifyNotReverted() {
        verify(reverter, never()).revert(subversionScm);
    }

}
