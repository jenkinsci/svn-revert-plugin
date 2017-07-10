package jenkins.plugins.svn_revert;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.SubversionSCM;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;

import com.google.common.collect.Lists;

class SvnReverter {

    static final String REVERT_MESSAGE =
            "Automatically reverted revision(s) %s since Jenkins build %s became %s.";
    private final Messenger messenger;
    private final AbstractBuild<?, ?> build;
    private SvnKitClient svnKitClient;
    private final SvnKitClientFactory svnFactory;
    private final ModuleFinder locationFinder;
    private final ChangedRevisions changedRevisions;
    private final boolean includePreviousMessage;

    SvnReverter(final AbstractBuild<?,?> build, final Messenger messenger,
            final SvnKitClientFactory svnFactory, final ModuleFinder locationFinder,
            final ChangedRevisions changedRevisions, final boolean includePreviousMessage) {
        this.build = build;
        this.messenger = messenger;
        this.svnFactory = svnFactory;
        this.locationFinder = locationFinder;
        this.changedRevisions = changedRevisions;
        this.includePreviousMessage = includePreviousMessage;
    }
    SvnRevertStatus revert(final SubversionSCM subversionScm) {
        return revert(subversionScm,false);
    }
    SvnRevertStatus revert(final SubversionSCM subversionScm,final boolean singleCommitOnly) {
        final AbstractProject<?, ?> rootProject = build.getProject().getRootProject();

        try {
            return revertAndCommit(rootProject, subversionScm, singleCommitOnly);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final NoSvnAuthException e) {
            messenger.informNoSvnAuthProvider();
            return SvnRevertStatus.REVERT_FAILED;
        } catch (final SVNException e) {
            messenger.informNothingRevertedBecauseOf(e);
            return SvnRevertStatus.NOTHING_REVERTED;
        } catch (final Exception e) {
            messenger.printStackTraceFor(e);
            return SvnRevertStatus.REVERT_FAILED;
        }
    }

    private SvnRevertStatus revertAndCommit(final AbstractProject<?, ?> rootProject,
            final SubversionSCM subversionScm, final boolean singleCommitOnly)
    throws NoSvnAuthException, IOException, InterruptedException, SVNException {
        svnKitClient = svnFactory.create(rootProject, subversionScm);

        final List<Module> modules = locationFinder.getModules(subversionScm);
        final Revisions revisions = changedRevisions.getRevisions();
        final List<File> moduleDirs = Lists.newArrayList();
        for (final Module module : modules) {
            final File moduleDir = module.getModuleRoot(build);

            svnKitClient.reverseMerge(revisions, module.getSvnUrl(), moduleDir);

            moduleDirs.add(moduleDir);
        }
        String commitMessage = getRevertMessageFor(revisions, rootProject);
        if (singleCommitOnly && includePreviousMessage) {
                commitMessage =  changedRevisions.getCommitMessages() + " " + commitMessage;
        }
        if (svnKitClient.commit(commitMessage, moduleDirs.toArray(new File[0]))) {
            informReverted(revisions, modules);
        } else {
            messenger.informFilesToRevertOutOfDate();
            return SvnRevertStatus.NOTHING_REVERTED;
        }

        return SvnRevertStatus.REVERT_SUCCESSFUL;
    }

    private String getRevertMessageFor(final Revisions revisions, final AbstractProject<?, ?> rootProject) {
        final String revertMessage = StringHumanizer.pluralize(REVERT_MESSAGE, revisions.count());
        return String.format(revertMessage, revisions.getAllInOrderAsString(), rootProject.getName(), build.getResult().toString());
    }

    private void informReverted(final Revisions revisions, final List<Module> modules) {
        for (final Module module : modules) {
            messenger.informReverted(revisions, module.getURL(),build.getResult().toString());
        }
    }

}
