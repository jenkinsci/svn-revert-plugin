package jenkins.plugins.svn_revert;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import hudson.EnvVars;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.scm.SubversionSCM;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import com.google.common.collect.Lists;

@SuppressWarnings("rawtypes")
public class SvnReverterTest extends AbstractMockitoTestCase {

    private static final String JOB_NAME = "job-name";
    private static final String LOCAL_REPO = "local" + File.separator;
    private static final String LOCAL_REPO_2 = "local2" + File.separator;
    private static final String REMOTE_REPO = "remote";
    private static final String REMOTE_REPO_2 = "remote2";
    private static final int FIRST_CHANGE = 911;
    private static final int SECOND_CHANGE = FIRST_CHANGE + 1;
    private static final Result NOT_SUCCESS = Result.UNSTABLE;

    private SvnReverter reverter;
    private SvnReverter reverterWithPreviousMessage;
    
    
    @Mock
    private Messenger messenger;
    @Mock
    private AbstractBuild build;
    @Mock
    private AbstractBuild rootBuild;
    @Mock
    private AbstractProject rootProject;
    @Mock
    private AbstractProject project;
    @Mock
    private BuildListener listener;
    @Mock
    private SubversionSCM subversionScm;
    @Mock
    private SvnKitClientFactory svnFactory;
    @Mock
    private EnvVars environmentVariables;
    @Mock
    private SvnKitClient svnKitClient;
    @Mock
    private File moduleDir;
    @Mock
    private File moduleDir2;
    @Mock
    private SVNURL svnUrl;
    @Mock
    private SVNURL svnUrl2;
    @Mock
    private SVNException svnException;
    @Mock
    private ChangedRevisions changedRevisions;
    @Mock
    private ModuleFinder locationFinder;

    private boolean includePreviousMessage;    

    private final IOException ioException = new IOException();

    private final List<Module> modules = Lists.newLinkedList();

    @Before
    public void setup() throws Exception {
        when(build.getRootBuild()).thenReturn(rootBuild);
        when(build.getProject()).thenReturn(project);
        when(project.getRootProject()).thenReturn(rootProject);
        when(rootProject.getName()).thenReturn(JOB_NAME);
        when(svnKitClient.commit(anyString(), any(File.class))).thenReturn(true);
        when(svnKitClient.commit(anyString(), any(File.class), any(File.class))).thenReturn(true);
        when(locationFinder.getModules(subversionScm)).thenReturn(modules);
        reverter = new SvnReverter(build, messenger, svnFactory, locationFinder, changedRevisions, false);
        reverterWithPreviousMessage = new SvnReverter(build, messenger, svnFactory, locationFinder, changedRevisions, true);
    }

    @Test
    public void shouldLogIfNoSvnAuthAvailable() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenScmWithNoAuth();
        reverter.revert(subversionScm,false);
        verify(messenger).informNoSvnAuthProvider();
    }

    @Test
    public void shouldReturnFailedIfNoSvnAuthAvailable() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenScmWithNoAuth();
        assertThat(reverter.revert(subversionScm,false), is(SvnRevertStatus.REVERT_FAILED));
    }

    @Test
    public void shouldLogExceptionIfThrown() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenScmWithAuth();
        when(locationFinder.getModules(subversionScm)).thenThrow(ioException);
        reverter.revert(subversionScm,false);
        verify(messenger).printStackTraceFor(ioException);
    }

    @Test(expected=RuntimeException.class)
    public void shouldNotCatchRuntimeExceptionIfThrown() throws Exception {
        givenScmWithAuth();
        when(build.getEnvironment(listener)).thenThrow(new RuntimeException());
        reverter.revert(subversionScm,false);
    }

    @Test
    public void shouldLogWhenRevertSuccessful() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();

        reverter.revert(subversionScm,false);

        verify(messenger).informReverted(Revisions.create(FIRST_CHANGE), REMOTE_REPO, NOT_SUCCESS.toString());
        verifyNoMoreInteractions(messenger);
    }

    @Test
    public void shouldReturnSuccessWhenRevertSuccessful() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();

        assertThat(reverter.revert(subversionScm,false), is(SvnRevertStatus.REVERT_SUCCESSFUL));
    }

    @Test
    public void shouldReturnNothingRevertedWhenFilesOutOfDate() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();
        when(svnKitClient.commit(anyString(), any(File[].class))).thenReturn(false);

        assertThat(reverter.revert(subversionScm,false), is(SvnRevertStatus.NOTHING_REVERTED));
    }

    @Test
    public void shouldUseConfiguredMessageWhenReverting() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();

        reverter.revert(subversionScm,false);

        verify(svnKitClient).commit(buildCommitMessage(), moduleDir);
    }
    
    @Test
    public void shouldUseConfiguredMessageWhenRevertingWithPreviousMessage() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();

        reverterWithPreviousMessage.revert(subversionScm,true);

        verify(svnKitClient).commit("null " + buildCommitMessage(), moduleDir);
    }    

    @Test
    public void shouldRevertChangedRevision() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();

        reverter.revert(subversionScm,false);

        verify(svnKitClient).reverseMerge(Revisions.create(FIRST_CHANGE), svnUrl, moduleDir);
    }

    @Test
    public void shouldRevertChangedRevisionsInAllModulesWhenSameRevisionsChanged() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMetForTwoModulesInSameRepo();

        reverter.revert(subversionScm,false);

        verify(messenger).informReverted(Revisions.create(FIRST_CHANGE), REMOTE_REPO, NOT_SUCCESS.toString());
        verify(messenger).informReverted(Revisions.create(FIRST_CHANGE), REMOTE_REPO_2, NOT_SUCCESS.toString());
        verify(svnKitClient).reverseMerge(Revisions.create(FIRST_CHANGE), svnUrl, moduleDir);
        verify(svnKitClient).reverseMerge(Revisions.create(FIRST_CHANGE), svnUrl2, moduleDir2);
        verify(svnKitClient).commit(buildCommitMessage(), moduleDir, moduleDir2);
        verifyNoMoreInteractions(svnKitClient);
    }

    @Test
    public void shouldLogRevertFailedWhenCommitFails() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();
        doThrow(svnException).when(svnKitClient).commit(anyString(), any(File.class));

        reverter.revert(subversionScm,false);

        verify(messenger).informNothingRevertedBecauseOf(svnException);
        verifyNoMoreInteractions(messenger);
    }

    @Test
    public void shouldReturnRevertFailedWhenCommitFails() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);    
        givenAllRevertConditionsMet();
        doThrow(svnException).when(svnKitClient).commit(anyString(), any(File.class));

        assertThat(reverter.revert(subversionScm,false), is(SvnRevertStatus.NOTHING_REVERTED));
    }

    @Test
    public void shouldRevertMultipleRevisionsWhenMultipleCommitsSinceLastBuild() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();
        when(changedRevisions.getRevisions()).thenReturn(Revisions.create(FIRST_CHANGE, SECOND_CHANGE));

        reverter.revert(subversionScm,false);

        verify(svnKitClient).reverseMerge(Revisions.create(FIRST_CHANGE, SECOND_CHANGE), svnUrl, moduleDir);
    }
    
    @Test
    public void shouldLogNotRevertedWhenFileIsOutOfDate() throws Exception {
        when(build.getResult()).thenReturn(NOT_SUCCESS);
        givenAllRevertConditionsMet();
        when(svnKitClient.commit(anyString(), any(File[].class))).thenReturn(false);

        reverter.revert(subversionScm,false);

        verify(messenger).informFilesToRevertOutOfDate();
        verifyNoMoreInteractions(messenger);
    }

    private void givenAllRevertConditionsMetForTwoModulesInSameRepo() throws Exception,
            IOException, InterruptedException {
        when(build.getResult()).thenReturn(NOT_SUCCESS);            
        givenAllRevertConditionsMet();
        givenModuleLocations(moduleDir2, svnUrl2, REMOTE_REPO_2, LOCAL_REPO_2);
        when(changedRevisions.getRevisions()).thenReturn(Revisions.create(FIRST_CHANGE));
    }

    private void givenRepositoryWithoutChanges() throws Exception {
        givenScmWithAuth();
        givenEnvironmentVariables();
        givenModuleLocations(moduleDir, svnUrl, REMOTE_REPO, LOCAL_REPO);
    }

    private void givenAllRevertConditionsMet() throws Exception, IOException, InterruptedException {
        givenRepositoryWithoutChanges();
        givenChangesInFirstRepository();
    }

    private void givenChangesInFirstRepository() {
        when(changedRevisions.getRevisions()).thenReturn(Revisions.create(FIRST_CHANGE));
    }

    private void givenScmWithAuth() throws Exception {
        when(svnFactory.create(rootProject, subversionScm)).thenReturn(svnKitClient);
    }

    private void givenEnvironmentVariables() throws Exception {
        when(build.getEnvironment(listener)).thenReturn(environmentVariables);
    }

    private void givenModuleLocations(final File moduleDir, final SVNURL svnUrl,
            final String remoteLocation, final String localLocation) throws Exception {
        final Module module = mock(Module.class);
        modules.add(module);
        when(module.getModuleRoot(build)).thenReturn(moduleDir);
        when(module.getSvnUrl()).thenReturn(svnUrl);
        when(module.getURL()).thenReturn(remoteLocation);
    }

    private void givenScmWithNoAuth() throws Exception {
        when(svnFactory.create(Matchers.<AbstractProject>any(), Matchers.<SubversionSCM>any()))
        .thenThrow(new NoSvnAuthException());
    }

    private String buildCommitMessage() {
        return String.format(SvnReverter.REVERT_MESSAGE.replace("(s)", ""),
                Revisions.create(FIRST_CHANGE).getAllInOrderAsString(),
                JOB_NAME, build.getResult().toString());
    }

}
