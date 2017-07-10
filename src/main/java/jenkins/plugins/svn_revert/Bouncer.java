package jenkins.plugins.svn_revert;

import hudson.Launcher;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.SubversionSCM;

import java.io.IOException;

class Bouncer {

    private static final String REVERT = "revert";

    static boolean throwOutIfUnstable(final AbstractBuild<?, ?> build, final Launcher launcher,
            final Messenger messenger, final SvnReverter svnReverter, final Claimer claimer,
            final ChangeLocator changeLocator, final CommitMessages commitMessages,
            final RevertMailSender mailer, final CommitCountRule commitCountRule, final boolean revertFailed)
                    throws InterruptedException, IOException {

        if (isNotSubversionJob(build)) {
            messenger.informNotSubversionSCM();
            return true;
        }
        if (revertFailed) {
	   if (currentBuildSuccess(build)) {
	       messenger.informBuildStatusSuccess();
	       return true;
	   }
        } else {
	    if (currentBuildNotUnstable(build)) {
		messenger.informBuildStatusNotUnstable();
		return true;
	    }        
        } 
        if (previousBuildNotSuccessful(build)) {
            messenger.informPreviousBuildStatusNotSuccess();
            return true;
        }
        if (commitCountRule.noChangesInBuild()) {
            messenger.informNoChanges();
            return true;
        }
        if (commitCountRule.tooManyChangesInBuild()) {
            messenger.informTooManyChanges();
            return true;
        }
        if (commitMessages.anyMessageContains(REVERT)) {
            messenger.informCommitMessageContains(REVERT);
            return true;
        }
        final SubversionSCM subversionScm = getSubversionScm(build);
        if (changeLocator.changesOutsideWorkspace(subversionScm)) {
            messenger.informChangesOutsideWorkspace();
            return true;
        }

        final SvnRevertStatus revertStatus = svnReverter.revert(subversionScm,commitCountRule.singleCommitOnly());
        if (revertStatus == SvnRevertStatus.REVERT_FAILED) {
            return false;
        }
        if (revertStatus == SvnRevertStatus.REVERT_SUCCESSFUL) {
            claimer.claim(build);
            mailer.sendRevertMail(build);
        }
        return true;
    }

    private static boolean isNotSubversionJob(final AbstractBuild<?, ?> abstractBuild) {
        return !(abstractBuild.getProject().getRootProject().getScm() instanceof SubversionSCM);
    }

    private static boolean currentBuildSuccess(final AbstractBuild<?, ?> abstractBuild) {
        return abstractBuild.getResult() == Result.SUCCESS;
    }    
    
    private static boolean currentBuildNotUnstable(final AbstractBuild<?, ?> abstractBuild) {
        return abstractBuild.getResult() != Result.UNSTABLE;
    }

    private static boolean previousBuildNotSuccessful(final AbstractBuild<?, ?> abstractBuild) {
        return !previousBuildSuccessful(abstractBuild);
    }

    private static SubversionSCM getSubversionScm(final AbstractBuild<?, ?> abstractBuild) {
        return SubversionSCM.class.cast(abstractBuild.getProject().getRootProject().getScm());
    }

    private static boolean previousBuildSuccessful(final AbstractBuild<?, ?> abstractBuild) {
        final Run<?, ?> previousBuild = abstractBuild.getPreviousBuild();
        if (previousBuild != null) {
            if (previousBuild.isBuilding()) {
                return false;
            }
            return previousBuild.getResult() == Result.SUCCESS;
        }
        return false;
    }
}
