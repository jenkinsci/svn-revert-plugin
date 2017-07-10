package jenkins.plugins.svn_revert;

import static hudson.model.Result.SUCCESS;
import static hudson.model.Result.UNSTABLE;
import static hudson.model.Result.FAILURE;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.scm.NullSCM;
import hudson.scm.SubversionSCM;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import jenkins.plugins.svn_revert.JenkinsGlue.SvnRevertDescriptorImpl;

import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNStatus;

import com.google.common.collect.Lists;

@SuppressWarnings({ "rawtypes", "deprecation" })
public class PluginAcceptanceTest extends HudsonTestCase {

    private static final String EMAIL_SENT = "An attempt to send an e-mail to empty list of recipients, ignored.";
    private static final long NO_COMMITS = 1;
    private static final long ONE_COMMIT = 2;
    private static final long TWO_COMMITS = 3;
    private static final String ONE_REVERTED_REVISION =
            String.format(" %s:%s ", NO_COMMITS, ONE_COMMIT);
    private static final String TWO_REVERTED_REVISIONS =
            String.format(" %s:%s ", NO_COMMITS, TWO_COMMITS);
    private static final String MODULE_1 = "module1";
    private static final String MODULE_2 = "module2";
    private static final String MODIFIED_FILE = "modified_file.txt";
    private static final String MODIFIED_FILE_IN_MODULE_1 =
            MODULE_1 + File.separator + MODIFIED_FILE;
    private static final String MODIFIED_FILE_IN_MODULE_2 =
            MODULE_2 + File.separator + MODIFIED_FILE;
    private static final String FOLDER_IN_MODULE_1 = MODULE_1 + File.separator + "folder";
    private static final int LOG_LIMIT = 1000;
    private FreeStyleProject job;
    private String svnUrl;
    private SubversionSCM scm;
    private SubversionSCM rootScm;
    private FreeStyleBuild currentBuild;
    private int jobCounter = 0;
    private SvnRevertDescriptorImpl descriptor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        givenSubversionScmWithOneModule();
        descriptor = hudson.getDescriptorByType(SvnRevertDescriptorImpl.class);
    }

    public void testShouldNotRevertWhenNotSubversionSCM() throws Exception {
        givenJobWithNullScm();

        currentBuild = whenPreviousJobSuccessfulAndCurrentUnstable();

        assertLogContains(Messenger.NOT_SUBVERSION_SCM, currentBuild);
        assertBuildStatus(UNSTABLE, currentBuild);
    }

    public void testShouldNotRevertWhenBuildStatusIsSuccess() throws Exception {
        givenJobWithOneModule();
        givenChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1);

        currentBuild = whenBuilding();

        assertLogContains(Messenger.BUILD_STATUS_NOT_UNSTABLE, currentBuild);
        assertBuildStatus(SUCCESS, currentBuild);
        assertNothingRevertedSince(ONE_COMMIT);
    }

    public void testShouldNotRevertWhenBuildStatusChangesToFailedAndNotRevertFailed() throws Exception {
        givenJobWithOneModule();

        currentBuild = whenPreviousJobSuccessfulAndCurrentFailed();

        assertBuildStatus(FAILURE, currentBuild);
        assertNothingRevertedSince(ONE_COMMIT);
    }    
    
    public void testShouldLogAndRevertWhenBuildStatusChangesToUnstable() throws Exception {
        givenJobWithOneModule();

        currentBuild = whenPreviousJobSuccessfulAndCurrentUnstable();

        assertBuildStatus(UNSTABLE, currentBuild);
        assertFileReverted(MODIFIED_FILE_IN_MODULE_1);
        assertLogContains(svnUrl, currentBuild);
        assertLogContains(ONE_REVERTED_REVISION, currentBuild);
    }
    
    public void testShouldLogAndRevertWhenBuildStatusNotSuccessAndRevertFailed() throws Exception {
        givenJobWithOneModule(true,false);

        currentBuild = whenPreviousJobSuccessfulAndCurrentFailed();

        assertBuildStatus(FAILURE, currentBuild);
        assertFileReverted(MODIFIED_FILE_IN_MODULE_1);
        assertLogContains(svnUrl, currentBuild);
        assertLogContains(ONE_REVERTED_REVISION, currentBuild);
    }    

    public void testCanRevertMultipleModulesInSameRepository() throws Exception {
        givenJobWithTwoModulesInSameRepository();
        givenPreviousBuildSuccessful();
        givenChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1);
        givenNextBuildWillBe(UNSTABLE);

        currentBuild = whenBuilding();

        assertBuildStatus(UNSTABLE, currentBuild);
        assertFileReverted(MODIFIED_FILE_IN_MODULE_1);
        assertLogContains(MODULE_1, currentBuild);
        assertLogContains(MODULE_2, currentBuild);
        assertThatStringContainsTimes(logFor(currentBuild), ONE_REVERTED_REVISION, 2);
    }

    public void testCanRevertMultipleRevisions() throws Exception {
        descriptor.setRevertMultipleCommits(true);
        givenJobWithOneModule();

        currentBuild = whenPreviousJobSuccesfulAndCurrentUnstableWithTwoChanges();

        assertBuildStatus(UNSTABLE, currentBuild);
        assertFileReverted(MODIFIED_FILE_IN_MODULE_1);
        assertLogContains(svnUrl, currentBuild);
        assertLogContains(TWO_REVERTED_REVISIONS, currentBuild);
    }

    public void testWillNotRevertMultipleRevisionsWhenConfiguredNotTo() throws Exception {
        descriptor.setRevertMultipleCommits(false);
        givenJobWithOneModule();

        currentBuild = whenPreviousJobSuccesfulAndCurrentUnstableWithTwoChanges();

        assertBuildStatus(UNSTABLE, currentBuild);
        assertNothingRevertedSince(TWO_COMMITS);
        assertLogContains(svnUrl, currentBuild);
        assertLogContains(Messenger.TOO_MANY_CHANGES, currentBuild);
    }

    public void testWillNotRevertWhenFileHasChangedSinceBuildStarted() throws Exception {
        givenJobWithOneModule();
        givenPreviousBuildSuccessful();
        givenChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1);
        givenNextBuildWillBe(UNSTABLE);

        currentBuild = whenFileChangedDuringBuilding(MODIFIED_FILE_IN_MODULE_1);

        assertBuildStatus(UNSTABLE, currentBuild);
        assertNothingRevertedSince(TWO_COMMITS);
        assertLogNotContains(ONE_REVERTED_REVISION, currentBuild);
    }

    public void testWillNotRevertWhenFolderHasBeenRemovedSinceBuildStarted() throws Exception {
        givenJobWithOneModule();
        givenPreviousBuildSuccessful();
        givenChangesInSubversionIn(FOLDER_IN_MODULE_1 + File.separator + "file.txt");
        givenNextBuildWillBe(UNSTABLE);

        currentBuild = whenFileRemovedDuringBuilding(FOLDER_IN_MODULE_1);

        assertBuildStatus(UNSTABLE, currentBuild);
        assertNothingRevertedSince(TWO_COMMITS);
        assertLogNotContains(ONE_REVERTED_REVISION, currentBuild);
    }

    public void testShouldNotRevertAnythingWhenFileToRevertHasChanged() throws Exception {
        givenJobWithTwoModulesInSameRepository();
        givenPreviousBuildSuccessful();
        givenChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1, MODIFIED_FILE_IN_MODULE_2);
        givenNextBuildWillBe(UNSTABLE);

        currentBuild = whenFileChangedDuringBuilding(MODIFIED_FILE_IN_MODULE_1);

        assertNothingRevertedSince(TWO_COMMITS);
        assertBuildStatus(UNSTABLE, currentBuild);
        assertLogNotContains(ONE_REVERTED_REVISION, currentBuild);
    }

    public void testShouldNotRevertAnythingWhenCommitMessageContainsRevert() throws Exception {
        givenJobWithOneModule();
        givenPreviousBuildSuccessful();
        givenChangesInSubversionWithMessageIn("Reverted something", MODIFIED_FILE_IN_MODULE_1);
        givenNextBuildWillBe(UNSTABLE);

        currentBuild = whenBuilding();

        assertNothingRevertedSince(ONE_COMMIT);
        assertBuildStatus(UNSTABLE, currentBuild);
        assertLogNotContains(ONE_REVERTED_REVISION, currentBuild);
    }

    public void testShouldNotRevertAnythingWhenWorkspaceOnlyContainsPartsOfCommit()
    throws Exception {
        givenJobWithOneModule();
        givenPreviousBuildSuccessful();
        givenChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1, MODIFIED_FILE_IN_MODULE_2);
        givenNextBuildWillBe(UNSTABLE);

        currentBuild = whenBuilding();

        assertNothingRevertedSince(ONE_COMMIT);
        assertBuildStatus(UNSTABLE, currentBuild);
        assertLogNotContains(ONE_REVERTED_REVISION, currentBuild);
    }

    public void testShouldBePossibleToRevertOnSlaveMachine() throws Exception {
        givenJobWithOneModule();

        final Node slave = createSlave("slave-node", null, null);
        job.setAssignedNode(slave);

        currentBuild = whenPreviousJobSuccessfulAndCurrentUnstable();

        assertBuildStatus(UNSTABLE, currentBuild);
        assertFileReverted(MODIFIED_FILE_IN_MODULE_1);
        assertLogContains(svnUrl, currentBuild);
        assertLogContains(ONE_REVERTED_REVISION, currentBuild);
    }

    private void givenSubversionScmWithOneModule() throws Exception {
        final File repo = getRepoWithTwoModules();
        final String repoUrl = "file://" + repo.getPath();
        svnUrl = repoUrl + "/" + MODULE_1;
        scm = new SubversionSCM(svnUrl);
        rootScm = new SubversionSCM(repoUrl);
    }

    private void givenChangesInSubversionIn(final String... files) throws Exception {
        modifyAndCommit(files);
    }

    private void givenChangesInSubversionWithMessageIn(final String commitMessage,
            final String... files) throws Exception {
        modifyAndCommitWithMessage(commitMessage, files);
    }

    private void givenTwoChangesInSubversionIn(final String files) throws Exception {
        modifyAndCommit(files);
        modifyAndCommit(files);
    }

    private void givenFileRemovedInSubversion(final String... files) throws Exception {
        removeAndCommit(files);
    }

    private void givenJobWithTwoModulesInSameRepository() throws Exception, IOException {
        givenJobWithOneModule();
        final File repo = getRepoWithTwoModules();
        svnUrl = "file://" + repo.getPath();
        final String[] svnUrls = new String[]{ svnUrl + "/" + MODULE_1, svnUrl + "/" + MODULE_2 };
        final String[] repoLocations= new String[]{ MODULE_1, MODULE_2 };
        scm = new SubversionSCM(svnUrls, repoLocations, true, null);
        rootScm = new SubversionSCM(svnUrl);
        job.setScm(scm);
    }

    private void givenPreviousBuildSuccessful() throws Exception {
        assertBuildStatusSuccess(scheduleBuild());
    }

    private void givenNextBuildWillBe(final Result result) throws Exception {
        job.getBuildersList().add(new MockBuilder(result));
    }

    private void givenJobWithNullScm() throws Exception {
        job = createFreeStyleProject("no-scm-job");
        job.getPublishersList().add(new JenkinsGlue(false,false));
        job.setAssignedLabel(hudson.getSelfLabel());
        job.setScm(new NullSCM());
    }

    private void givenJobWithOneModule() throws Exception {
        givenJobWithOneModule(false,false);
    }
    private void givenJobWithOneModule(boolean revertFailed, boolean includePreviousMessage) throws Exception {
        job = createFreeStyleProject("subversion-scm-job");
        job.getPublishersList().add(new JenkinsGlue(revertFailed, includePreviousMessage));
        job.setAssignedLabel(hudson.getSelfLabel());
        job.setScm(scm);
    }    

    private FreeStyleBuild whenBuilding() throws Exception {
        final FreeStyleBuild build = scheduleBuild();
        printLogFor(build);
        return build;
    }

    private FreeStyleBuild whenPreviousJobSuccessfulAndCurrentUnstable() throws Exception,
            InterruptedException, ExecutionException {
        givenPreviousBuildSuccessful();
        givenChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1);
        givenNextBuildWillBe(UNSTABLE);
        return whenBuilding();
    }

    private FreeStyleBuild whenPreviousJobSuccessfulAndCurrentFailed() throws Exception,
            InterruptedException, ExecutionException {
        givenPreviousBuildSuccessful();
        givenChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1);
        givenNextBuildWillBe(FAILURE);
        return whenBuilding();
    }    
    
    private FreeStyleBuild whenPreviousJobSuccesfulAndCurrentUnstableWithTwoChanges()
            throws Exception {
        givenPreviousBuildSuccessful();
        givenTwoChangesInSubversionIn(MODIFIED_FILE_IN_MODULE_1);
        givenNextBuildWillBe(UNSTABLE);
        return whenBuilding();
    }

    private FreeStyleBuild whenFileChangedDuringBuilding(final String file) throws Exception, InterruptedException,
            ExecutionException {
        final Semaphore semaphore = blockExecutionOf(job);
        final Future<FreeStyleBuild> future = job.scheduleBuild2(0);
        givenChangesInSubversionIn(file);
        semaphore.release();
        final FreeStyleBuild build = future.get();
        printLogFor(build);
        return build;
    }

    private FreeStyleBuild whenFileRemovedDuringBuilding(final String... files) throws Exception, InterruptedException,
    ExecutionException {
        final Semaphore semaphore = blockExecutionOf(job);
        final Future<FreeStyleBuild> future = job.scheduleBuild2(0);
        semaphore.release();
        givenFileRemovedInSubversion(files);
        final FreeStyleBuild build = future.get();
        printLogFor(build);
        return build;
    }

    private Semaphore blockExecutionOf(final FreeStyleProject job) throws IOException {
        final Semaphore semaphore = new Semaphore(0);
        job.getBuildWrappersList().add(new BuildBlocker(semaphore));
        return semaphore;
    }

    private void assertNothingRevertedSince(final long revisionNumber) throws Exception {
        assertEquals("HEAD revision", revisionNumber, getHeadSvnRevision());
        assertLogNotContains(EMAIL_SENT, currentBuild);
    }

    private void assertFileReverted(final String path)
            throws IOException, InterruptedException, ExecutionException, Exception {

        final FreeStyleBuild build = getIndependentSubversionBuild(getUniqueBuildName("assert-file-reverted"), rootScm);
        final FilePath file = build.getWorkspace().child(path);
        assertFalse("File '" + path + "' is not reverted (because it exists)", file.exists());
        assertLogContains(EMAIL_SENT, currentBuild);
    }

    private void assertThatStringContainsTimes(
            final String log, final String string, final int times) {
        final int actualTimes = log.split(string).length - 1;
        assertThat(actualTimes, is(times));

    }

    /**
     * Repo at revision 1 with structure
     *   module1/
     *           file1
     *   module2/
     *           file2
     */
    private File getRepoWithTwoModules() throws Exception {
        return new CopyExisting(getClass().getResource("repoWithTwoModules.zip")).allocate();
    }

    private String logFor(final FreeStyleBuild build) throws IOException {
        return build.getLog(LOG_LIMIT).toString();
    }

    private void printLogFor(final FreeStyleBuild build) throws IOException {
        final List<String> logLines = build.getLog(LOG_LIMIT);
        System.out.println("Build log: ");
        printLog(logLines);
    }

    private void printLog(final List<String> logLines) {
        for (final String logLine : logLines) {
            System.out.println("   " + logLine);
        }
    }

    private FreeStyleBuild scheduleBuild() throws Exception {
        return job.scheduleBuild2(0).get();
    }

    private void modifyAndCommit(final String... paths) throws Exception {
        modifyAndCommitWithMessage("test changes", paths);
    }

    private void modifyAndCommitWithMessage(final String commitMessage, final String... paths)
            throws  Exception {
        final FreeStyleBuild build = getIndependentSubversionBuild(getUniqueBuildName("modify-and-commit"), rootScm);
        final SVNClientManager svnm = SubversionSCM.createSvnClientManager((AbstractProject) null);

        final List<File> filesToCommit = Lists.newArrayList();
        for (final String path : paths) {
            final FilePath file = build.getWorkspace().child(path);
            if (!file.exists()) {
                final File realFile = new File(file.getRemote());
                final File parent = realFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                    svnm.getWCClient().doAdd(parent, false, false, false,
                            SVNDepth.INFINITY, false, false);
                    filesToCommit.add(parent);
                }
                file.touch(System.currentTimeMillis());
                svnm.getWCClient().doAdd(realFile, false, false, false,
                        SVNDepth.INFINITY, false, false);
            } else {
                file.write("random content", "UTF-8");
            }
            filesToCommit.add(new File(file.getRemote()));
        }

        svnm.getCommitClient().doCommit(filesToCommit.toArray(new File[0]), false,
                commitMessage, null, null, false, false, SVNDepth.EMPTY);
    }

    private void removeAndCommit(final String... paths) throws Exception {
        final FreeStyleBuild build = getIndependentSubversionBuild(getUniqueBuildName("remove-and-commit"), rootScm);
        final SVNClientManager svnm = SubversionSCM.createSvnClientManager((AbstractProject) null);

        final List<File> filesToCommit = Lists.newArrayList();
        for (final String path : paths) {
            final FilePath file = build.getWorkspace().child(path);
            if (file.exists()) {
                file.touch(System.currentTimeMillis());
                svnm.getWCClient().doDelete(new File(file.getRemote()), true, true, false);
            } else {
                throw new IllegalStateException("Can not delete file that does not exist");
            }
            filesToCommit.add(new File(file.getRemote()));
        }

        svnm.getCommitClient().doCommit(filesToCommit.toArray(new File[0]), false,
                "test changes", null, null, false, false, SVNDepth.INFINITY);
    }

    private FreeStyleBuild getIndependentSubversionBuild(final String jobName, final SubversionSCM scm) throws IOException,
            Exception, InterruptedException, ExecutionException {
        final FreeStyleProject forCommit = createFreeStyleProject(jobName);
        forCommit.setScm(scm);
        forCommit.setAssignedLabel(hudson.getSelfLabel());
        final FreeStyleBuild build = assertBuildStatusSuccess(forCommit.scheduleBuild2(0).get());
        return build;
    }

    private long getHeadSvnRevision() throws Exception {
        final SVNClientManager svnm = SubversionSCM.createSvnClientManager((AbstractProject) null);
        final FreeStyleBuild build = getIndependentSubversionBuild(getUniqueBuildName("get-head-revision"), rootScm);
        final File workspace = new File(build.getWorkspace().getRemote());
        final SVNStatus status = svnm.getStatusClient().doStatus(workspace, true);
        return status.getRevision().getNumber();
    }

    private String getUniqueBuildName(final String buildName) {
        return buildName + "-" + jobCounter++;
    }

}
